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

        // Shopifyのトップ・コレクションページは ?page=2 形式で一覧を続ける。
        if ((initial.contains("product-card-wrapper") || initial.contains("card__heading"))
                && (base.getPath() == null || !base.getPath().contains("/products/"))) {
            return fetchShopifyPages(base, initial);
        }

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
        StringBuilder all = new StringBuilder(initial);
        String current = initial;
        java.util.Set<URI> visited = new java.util.HashSet<>();
        visited.add(base);
        for (int page = 0; page < 200; page++) {
            org.jsoup.nodes.Element more = org.jsoup.Jsoup.parse(current)
                    .selectFirst(".js-show-more-ajax[data-url]");
            if (more == null || more.attr("data-url").isBlank()) return all.toString();
            URI next = base.resolve(more.attr("data-url"));
            if (!isSameSecureHost(base, next) || !visited.add(next)) {
                throw new IllegalStateException("一覧の続きが取得できませんでした。");
            }
            String fetched = fetchHtml(next);
            if (fetched.equals(current) || countCards(fetched) == 0) {
                throw new IllegalStateException("一覧の続きに商品が見つかりませんでした。");
            }
            all.append(fetched);
            current = fetched;
        }
        throw new IllegalStateException("一覧が取得上限を超えました。条件を絞ってください。");
    }

    private String fetchShopifyPages(URI base, String initial) throws Exception {
        StringBuilder all = new StringBuilder(initial);
        String previous = initial;
        String separator = base.getQuery() == null ? "?" : "&";
        for (int page = 2; page <= 100; page++) {
            URI next = URI.create(base + separator + "page=" + page);
            String fetched;
            try {
                fetched = fetchHtml(next);
            } catch (Exception ignored) {
                break;
            }
            if (fetched.isBlank() || fetched.equals(previous) || !fetched.contains("product-card-wrapper")) break;
            all.append(fetched);
            previous = fetched;
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
