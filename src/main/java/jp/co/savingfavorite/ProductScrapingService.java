package jp.co.savingfavorite;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/** 公開HTTPS商品ページのHTML取得だけを担当するサービス。 */
@Service
public class ProductScrapingService {
    private static final Pattern MORE = Pattern.compile("class=\\\"[^\\\"]*js-show-more-ajax[^\\\"]*\\\"[^>]*data-url=\\\"([^\\\"]+)\\\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern TOTAL = Pattern.compile("全\\s*([0-9,]+)\\s*件");
    private static final String USER_AGENT = "SavingFavorite/1.0 (+public-product-metadata)";
    private final Map<String, RobotsRules> robotsCache = new ConcurrentHashMap<>();

    public String fetchHtml(URI uri) throws Exception {
        if (!isAllowedByRobots(uri)) throw new IllegalStateException("このサイトは自動取得を許可していません。");
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(5)).build();
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(8))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml").GET().build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300
                || !response.headers().firstValue("content-type").orElse("").toLowerCase().contains("text/html")) {
            throw new IllegalStateException("公開されている商品ページを取得できませんでした。");
        }
        if (response.body().length() > 1_500_000) throw new IllegalStateException("ページが大きすぎるため取得できません。");
        return response.body();
    }

    /** robots.txt の明示的な拒否だけを尊重する。取得不能時はサイトを壊さないため許可する。 */
    private boolean isAllowedByRobots(URI uri) {
        String hostKey = uri.getScheme().toLowerCase() + "://" + uri.getAuthority().toLowerCase();
        RobotsRules rules = robotsCache.computeIfAbsent(hostKey, key -> loadRobots(uri));
        return rules.allows(uri.getRawPath().isBlank() ? "/" : uri.getRawPath());
    }

    private RobotsRules loadRobots(URI uri) {
        try {
            URI robotsUri = URI.create(uri.getScheme() + "://" + uri.getAuthority() + "/robots.txt");
            HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER)
                    .connectTimeout(Duration.ofSeconds(3)).build();
            HttpRequest request = HttpRequest.newBuilder(robotsUri).timeout(Duration.ofSeconds(5))
                    .header("User-Agent", USER_AGENT).header("Accept", "text/plain").GET().build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() == 404 || response.statusCode() < 200 || response.statusCode() >= 300) return RobotsRules.ALLOW_ALL;
            return RobotsRules.parse(response.body());
        } catch (Exception ignored) {
            return RobotsRules.ALLOW_ALL;
        }
    }

    private record RobotsRules(List<String> disallow, List<String> allow) {
        private static final RobotsRules ALLOW_ALL = new RobotsRules(List.of(), List.of());

        static RobotsRules parse(String body) {
            List<String> disallow = new ArrayList<>();
            List<String> allow = new ArrayList<>();
            boolean applies = false;
            boolean sawAgent = false;
            for (String raw : body.split("\\R")) {
                String line = raw.split("#", 2)[0].trim();
                if (line.isEmpty()) continue;
                int colon = line.indexOf(':');
                if (colon < 0) continue;
                String field = line.substring(0, colon).trim().toLowerCase();
                String value = line.substring(colon + 1).trim();
                if (field.equals("user-agent")) {
                    if (sawAgent) { applies = false; disallow.clear(); allow.clear(); }
                    sawAgent = true;
                    applies = value.equals("*") || value.toLowerCase().contains("savingfavorite");
                } else if (applies && field.equals("disallow") && !value.isEmpty()) disallow.add(value);
                else if (applies && field.equals("allow") && !value.isEmpty()) allow.add(value);
            }
            return new RobotsRules(List.copyOf(disallow), List.copyOf(allow));
        }

        boolean allows(String path) {
            String matchedAllow = allow.stream().filter(path::startsWith).max((a, b) -> Integer.compare(a.length(), b.length())).orElse("");
            String matchedDisallow = disallow.stream().filter(path::startsWith).max((a, b) -> Integer.compare(a.length(), b.length())).orElse("");
            return matchedAllow.length() >= matchedDisallow.length();
        }
    }

    /** 一覧ページの公開「もっと見る」URLを同一ホスト内で順に取得する。 */
    public String fetchListingHtml(URI base, String initial) throws Exception {
        int initialCards = countCards(initial);

        // If a user pastes a later /search page (for example pageNo=11),
        // restart at page 1 so the selector can still choose from the full
        // catalog rather than only the final 12 products.
        if (base.getPath() != null && base.getPath().contains("/search")
                && base.getQuery() != null && base.getQuery().contains("pageNo=")
                && base.getQuery().contains("start=")) {
            String firstQuery = base.getQuery().replaceFirst("pageNo=[0-9]+", "pageNo=1")
                    .replaceFirst("start=[0-9]+", "start=0");
            URI firstPage = URI.create(base.getScheme() + "://" + base.getAuthority() + base.getPath() + "?" + firstQuery);
            if (!firstPage.equals(base)) {
                try {
                    String firstHtml = fetchHtml(firstPage);
                    if (countCards(firstHtml) > 0) return fetchListingHtml(firstPage, firstHtml);
                } catch (Exception ignored) {
                    // Fall back to the page supplied by the user.
                }
            }
        }
        // Some catalog pages expose the complete result set by increasing the
        // page-size query on the public page itself (for example, ?sz=200).
        // Try that first so lazy "show more" grids do not leave products out.
        if (initialCards > 0 && (base.getQuery() == null || !base.getQuery().matches(".*(?:^|&)sz=[0-9]+.*"))) {
            URI expandedBase = URI.create(base + (base.getQuery() == null ? "?sz=200" : "&sz=200"));
            try {
                String expandedHtml = fetchHtml(expandedBase);
                int expandedCards = countCards(expandedHtml);
                Integer total = totalProducts(expandedHtml);
                if (expandedCards > initialCards && (total == null || expandedCards >= total)) return expandedHtml;
            } catch (Exception ignored) {
                // Continue with the store's normal pagination below.
            }
        }
        StringBuilder all = new StringBuilder(initial);
        String current = initial;
        URI currentUri = base;
        // Continue until the endpoint returns no cards. The guard allows
        // catalogs with thousands of products while preventing endless loops.
        for (int page = 0; page < 200; page++) {
            Matcher matcher = MORE.matcher(current);
            URI next;
            if (matcher.find()) {
                String link = matcher.group(1).replace("&amp;", "&");

                // Nijisanji's listing endpoint supports a larger page size. The
                // initial document contains only 12 cards even when the listing
                // has many more products, so request the complete first grid in
                // one response before falling back to the site's "more" URL.
                String expandedLink = link.replace("sz=12", "sz=200")
                        .replace("pageNo=2", "pageNo=1")
                        .replace("start=23", "start=0");
                URI expanded = base.resolve(expandedLink);
                if (isSameSecureHost(base, expanded) && !expanded.equals(base)) {
                    try {
                        String expandedHtml = fetchHtml(expanded);
                        int expandedCards = countCards(expandedHtml);
                        Integer total = totalProducts(expandedHtml);
                        if (expandedCards > initialCards && (total == null || expandedCards >= total)) return expandedHtml;
                    } catch (Exception ignored) {
                        // Some stores do not support a larger page size; use
                        // their normal pagination below in that case.
                    }
                }
                next = base.resolve(link);
            } else if (currentUri.getPath() != null && currentUri.getPath().contains("search")
                    && currentUri.getQuery() != null && currentUri.getQuery().contains("pageNo=")) {
                // If the response itself still exposes a paging endpoint, keep
                // following it. This is a fallback for stores without a bulk
                // page-size parameter.
                String query = currentUri.getQuery();
                Matcher pageMatcher = Pattern.compile("(?:^|&)pageNo=([0-9]+)").matcher(query);
                if (!pageMatcher.find()) break;
                int pageNo = Integer.parseInt(pageMatcher.group(1));
                Matcher startMatcher = Pattern.compile("(?:^|&)start=([0-9]+)").matcher(query);
                int start = startMatcher.find() ? Integer.parseInt(startMatcher.group(1)) : Math.max(0, pageNo * 12 - 1);
                String nextQuery = query.replace("pageNo=" + pageNo, "pageNo=" + (pageNo + 1))
                        .replace("start=" + start, "start=" + (start + 12));
                next = new URI(currentUri.getScheme(), currentUri.getAuthority(), currentUri.getPath(), nextQuery, null);
            } else break;
            if (!isSameSecureHost(base, next)) break;
            String fetched = fetchHtml(next);
            if (fetched.isBlank() || fetched.equals(current) || !fetched.contains("card-container")) break;
            all.append(fetched);
            current = fetched;
            currentUri = next;
        }
        return all.toString();
    }

    private static boolean isSameSecureHost(URI base, URI candidate) {
        return "https".equalsIgnoreCase(candidate.getScheme())
                && base.getHost() != null
                && base.getHost().equalsIgnoreCase(candidate.getHost());
    }

    private static int countCards(String html) {
        int count = 0;
        int offset = 0;
        while ((offset = html.indexOf("card-container", offset)) >= 0) {
            count++;
            offset += "card-container".length();
        }
        return count;
    }

    private static Integer totalProducts(String html) {
        Matcher matcher = TOTAL.matcher(html);
        if (!matcher.find()) return null;
        try {
            return Integer.valueOf(matcher.group(1).replace(",", ""));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
