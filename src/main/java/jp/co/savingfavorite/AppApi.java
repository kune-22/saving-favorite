package jp.co.savingfavorite;

import java.math.BigDecimal;
import java.net.URI;
import java.net.InetAddress;
import java.security.Principal;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
@Transactional
public class AppApi {
    private static final Pattern META = Pattern.compile("<meta[^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern META_KEY = Pattern.compile("(?:property|name)=\\\"([^\\\"]*)\\\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern META_CONTENT = Pattern.compile("content=\\\"([^\\\"]*)\\\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern JSON_VALUE = Pattern.compile("\\\"(name|image|price|category)\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern JSON_PRICE = Pattern.compile("\\\"price\\\"\\s*:\\s*([0-9]+(?:\\.[0-9]+)?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern H1 = Pattern.compile("<h1[^>]*>([\\s\\S]*?)</h1>", Pattern.CASE_INSENSITIVE);
    private static final Pattern YEN = Pattern.compile("[¥￥]\\s*([0-9][0-9,]*(?:\\.[0-9]+)?)");
    private static final Pattern CARD = Pattern.compile("<div[^>]*class=\\\"[^\\\"]*card-container[^\\\"]*\\\"[\\s\\S]*?<img[^>]+(?:src|data-src|data-lazy-src)=\\\"([^\\\"]+)\\\"[\\s\\S]*?<h3[^>]*class=\\\"[^\\\"]*card-title[^\\\"]*\\\"[^>]*>([\\s\\S]*?)</h3>[\\s\\S]*?<span[^>]*class=\\\"[^\\\"]*card-price[^\\\"]*\\\"[^>]*>([\\s\\S]*?)</span>", Pattern.CASE_INSENSITIVE);
    private static final Pattern GOODS_CARD = Pattern.compile("<a[^>]+class=\\\"[^\\\"]*js-enhanced-ecommerce-image[^\\\"]*\\\"[^>]*>[\\s\\S]*?<img[^>]+(?:src|data-src)=\\\"([^\\\"]+)\\\"[\\s\\S]*?</a>[\\s\\S]*?<a[^>]+class=\\\"[^\\\"]*js-enhanced-ecommerce-goods-name[^\\\"]*\\\"[^>]*>([\\s\\S]*?)</a>[\\s\\S]{0,2500}?class=\\\"[^\\\"]*js-enhanced-ecommerce-goods-price[^\\\"]*\\\"[^>]*>([\\s\\S]*?)</", Pattern.CASE_INSENSITIVE);
    private static final Pattern GOODS_IMAGE = Pattern.compile("<img[^>]+(?:src|data-src|data-lazy-src)=\\\"([^\\\"]*(?:/img/goods/|/goods/)[^\\\"]*)\\\"", Pattern.CASE_INSENSITIVE);
    private static final ObjectMapper JSON = new ObjectMapper();
    private final UserService users;
    private final FavoriteRepository favorites;
    private final FavoriteItemRepository items;
    private final CalendarEventRepository events;
    private final ProductScrapingService productScraping;
    private final BrowserRenderingService browserRendering;
    public AppApi(UserService users, FavoriteRepository favorites, FavoriteItemRepository items, CalendarEventRepository events, ProductScrapingService productScraping, BrowserRenderingService browserRendering) {
        this.users = users; this.favorites = favorites; this.items = items; this.events = events; this.productScraping = productScraping; this.browserRendering = browserRendering;
    }
    public record FavoriteInput(String name, String description, BigDecimal monthlyBudget, String imageUrl, Integer imageSize) {}
    public record ItemInput(Long favoriteId, String name, String category, BigDecimal price, Integer quantity,
            LocalDate purchasedDate, LocalDate membershipJoinedDate, LocalDate deadline, String status, String recurrence, String storeUrl, String imageUrl) {}
    public record EventInput(Long favoriteId, String title, String kind, LocalDate startDate,
            LocalDate endDate, Integer reminderDays, String notes) {}
    public record FavoriteView(Long id, String name, String description, BigDecimal monthlyBudget, String imageUrl, Integer imageSize) {}
    public record ProfileInput(String name, String imageUrl, Integer imageSize, BigDecimal cash) {}
    public record ProfileView(String name, String email, String imageUrl) {}
    public record ItemView(Long id, Long favoriteId, String name, String category, BigDecimal price,
            Integer quantity, LocalDate purchasedDate, LocalDate membershipJoinedDate, LocalDate deadline, String status, String recurrence, String storeUrl, String imageUrl) {}
    public record EventView(Long id, Long favoriteId, String title, String kind, LocalDate startDate,
            LocalDate endDate, int reminderDays, String notes) {}
    public record Snapshot(String name, String email, String imageUrl, Integer imageSize, BigDecimal cash, List<FavoriteView> favorites, List<ItemView> items, List<EventView> events) {}
    public record MetadataInput(String url) {}
    public record ProductMetadata(String name, String imageUrl, BigDecimal price, String category) {}
    public record MetadataView(ProductMetadata product, List<ProductMetadata> products) {}

    @PostMapping("/metadata")
    public MetadataView metadata(@RequestBody MetadataInput input, Principal principal) {
        URI uri;
        try { uri = URI.create(Objects.toString(input.url(), "").trim()); } catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "商品ページのURLを確認してください。"); }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) bad("安全のため、HTTPSの商品ページURLを入力してください。");
        try {
            InetAddress address = InetAddress.getByName(uri.getHost());
            if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress() || address.isSiteLocalAddress()) bad("このURLは取得できません。");
            String html = productScraping.fetchHtml(uri);
            html = productScraping.fetchListingHtml(uri, html);
            List<ProductMetadata> products = extractProducts(html, uri);
            if (products.isEmpty()) {
                String rendered = browserRendering.fetchRenderedHtml(uri);
                if (!rendered.isBlank()) {
                    List<ProductMetadata> renderedProducts = extractProducts(rendered, uri);
                    if (!renderedProducts.isEmpty()) products = renderedProducts;
                }
            }
            if (products.isEmpty()) bad("商品情報を見つけられませんでした。商品ページのURLを確認してください。");
            return new MetadataView(products.get(0), products.size() > 1 ? products : List.of());
        } catch (ResponseStatusException e) { throw e; } catch (Exception e) { throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "商品ページを取得できませんでした。"); }
    }

    private static List<ProductMetadata> extractProducts(String html, URI base) {
        String page = decode(html);
        Document document = Jsoup.parse(page, base.toString());
        // 商品詳細は関連商品カードより優先し、対象商品の価格欄から読む。
        Element detail = document.selectFirst(".product-detail[data-pid]");
        if (detail != null) {
            Element title = detail.selectFirst("h1.heading");
            Element price = detail.selectFirst(".product-price-text .text-price");
            if (title != null && price != null) {
                Map<String, String> values = new HashMap<>();
                values.put("name", title.text());
                Matcher amount = Pattern.compile("[0-9][0-9,]*(?:\\.[0-9]+)?").matcher(price.text());
                if (amount.find()) values.put("price", amount.group().replace(",", ""));
                Element image = document.selectFirst("meta[property=og:image]");
                if (image != null) values.put("image", image.attr("content"));
                List<ProductMetadata> product = new ArrayList<>();
                addProduct(product, values, base);
                if (!product.isEmpty()) return product;
            }
        }
        Map<String,String> meta = new HashMap<>(); Matcher mm = META.matcher(page); while (mm.find()) { Matcher km = META_KEY.matcher(mm.group()); Matcher cm = META_CONTENT.matcher(mm.group()); if (km.find() && cm.find()) meta.put(km.group(1).toLowerCase(Locale.ROOT), decode(cm.group(1))); }
        List<ProductMetadata> result = new ArrayList<>(); Matcher cards = CARD.matcher(page); while (cards.find()) { Map<String,String> values = new HashMap<>(); values.put("name", stripMarkup(cards.group(2))); values.put("image", cards.group(1)); values.put("price", cards.group(3)); addProduct(result, values, base); }
        Matcher goodsCards = GOODS_CARD.matcher(page); while (goodsCards.find()) { Map<String,String> values = new HashMap<>(); values.put("name", stripMarkup(goodsCards.group(2))); values.put("image", goodsCards.group(1)); values.put("price", stripMarkup(goodsCards.group(3))); addProduct(result, values, base); }
        extractShopifyProducts(document, result, base);
        extractItemPropProducts(document, result, base);
        extractJsonLdProducts(document, result, base);
        extractEmbeddedJsonProducts(document, result, base);
        boolean listingPage = !document.select("a[href*='/products/']").isEmpty();
        if (result.isEmpty() && !listingPage) { Map<String,String> values = new HashMap<>(); String title = meta.getOrDefault("og:title", meta.getOrDefault("twitter:title", "")); Matcher h1 = H1.matcher(page); if (title.isBlank() && h1.find()) title = h1.group(1).replaceAll("<[^>]+>", "").trim(); if (!isGenericTitle(title)) { values.put("name", title); String image = meta.getOrDefault("og:image", ""); if (image.isBlank()) { Matcher goodsImage = GOODS_IMAGE.matcher(page); if (goodsImage.find()) image = goodsImage.group(1); } values.put("image", image); String amount = meta.getOrDefault("product:price:amount", ""); if (amount.isBlank()) { Matcher yen = YEN.matcher(page); if (yen.find()) amount = yen.group(1).replace(",", ""); } values.put("price", amount); values.put("category", meta.getOrDefault("product:category", "")); addProduct(result, values, base); } }
        return result;
    }

    private static void extractShopifyProducts(Document document, List<ProductMetadata> result, URI base) {
        for (Element card : document.select(".product-card-wrapper, .card-wrapper.product-card-wrapper")) {
            Element link = card.select("a[href*='/products/']").first();
            Element name = card.select(".card__heading a, .card__heading, [class*=product-title]").first();
            Element image = card.select("img").first();
            Element price = card.select(".price-item--sale, .price-item--regular, .price-item").first();
            if (link == null || name == null) continue;
            Map<String, String> values = new HashMap<>();
            values.put("name", name.text());
            if (image != null) values.put("image", attributeValue(image, "src", "data-src", "data-original"));
            if (price != null) values.put("price", price.text());
            addProduct(result, values, base);
        }
    }

    private static boolean isGenericTitle(String title) {
        String normalized = title == null ? "" : title.trim().toLowerCase(Locale.ROOT);
        return normalized.isBlank() || normalized.equals("default title") || normalized.equals("home")
                || normalized.equals("shopify") || normalized.contains("official store") && !normalized.contains("product");
    }

    /** Microdata/RDFa と link/meta の属性から商品情報を抽出する。 */
    private static void extractItemPropProducts(Document document, List<ProductMetadata> result, URI base) {
        Elements productNodes = document.select("[itemscope][itemtype*=Product], [itemtype*=Product]");
        if (productNodes.isEmpty() && !document.select("[itemprop=name], [itemprop=price], link[itemprop=image]").isEmpty()) productNodes = new Elements(document);
        for (Element product : productNodes) {
            Map<String, String> values = new HashMap<>();
            Element name = product.select("[itemprop=name]").first();
            Element image = product.select("[itemprop=image], link[rel=image_src], link[itemprop=image]").first();
            Element price = product.select("[itemprop=price], [itemprop=lowPrice]").first();
            Element category = product.select("[itemprop=category]").first();
            if (name != null) values.put("name", elementValue(name));
            if (image != null) values.put("image", attributeValue(image, "href", "src", "content"));
            if (price != null) values.put("price", elementValue(price));
            if (category != null) values.put("category", elementValue(category));
            addProduct(result, values, base);
        }

        // 商品画像を <link href="..."> に持つストアにも対応する。
        if (result.isEmpty()) {
            Map<String, String> values = new HashMap<>();
            Element title = document.select("meta[property=og:title], meta[name=twitter:title], link[itemprop=name]").first();
            Element image = document.select("link[rel=image_src], link[itemprop=image], link[rel~=image]").first();
            Element price = document.select("meta[property='product:price:amount'], meta[itemprop=price], link[itemprop=price]").first();
            if (title != null) values.put("name", attributeValue(title, "content", "href"));
            if (image != null) values.put("image", attributeValue(image, "href", "content"));
            if (price != null) values.put("price", attributeValue(price, "content", "href"));
            addProduct(result, values, base);
        }
    }

    /** schema.org Product のJSON-LDを、単体・配列・@graphのいずれでも読む。 */
    private static void extractJsonLdProducts(Document document, List<ProductMetadata> result, URI base) {
        for (Element script : document.select("script[type=application/ld+json]")) {
            try { collectJsonProducts(JSON.readTree(script.data()), result, base); }
            catch (Exception ignored) { /* 壊れたJSON-LDは他の抽出方法へ進む */ }
        }
    }

    /** Next.js等が script[type=application/json] に埋め込む商品情報を読む。 */
    private static void extractEmbeddedJsonProducts(Document document, List<ProductMetadata> result, URI base) {
        for (Element script : document.select("script#__NEXT_DATA__, script[type=application/json]")) {
            try { collectEmbeddedProducts(JSON.readTree(script.data()), result, base); }
            catch (Exception ignored) { /* ページ内の別用途JSONは無視する */ }
        }
    }

    private static void collectEmbeddedProducts(JsonNode node, List<ProductMetadata> result, URI base) {
        if (node == null || node.isNull()) return;
        if (node.isArray()) { node.forEach(item -> collectEmbeddedProducts(item, result, base)); return; }
        if (!node.isObject()) return;

        String name = firstText(node, "itemName", "productName", "name", "title");
        String price = firstText(node, "sellPrice", "price", "priceAmount", "amount");
        String image = firstText(node, "imageUrl", "image", "thumbnailUrl", "thumbnail");
        if (!name.isBlank() && !price.isBlank() && price.matches(".*[0-9].*")) {
            Map<String, String> values = new HashMap<>();
            values.put("name", name);
            values.put("price", price);
            values.put("image", image);
            values.put("category", firstText(node, "category", "categoryName"));
            addProduct(result, values, base);
        }
        node.elements().forEachRemaining(child -> collectEmbeddedProducts(child, result, base));
    }

    private static String firstText(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode value = node.get(name);
            if (value == null || value.isNull()) continue;
            if (value.isArray() && value.size() > 0) value = value.get(0);
            if (value.isValueNode()) {
                String text = value.asText("").trim();
                if (!text.isBlank()) return text;
            }
            if (value.isObject()) {
                String nested = firstText(value, "url", "src", "original", "value");
                if (!nested.isBlank()) return nested;
            }
        }
        return "";
    }

    private static void collectJsonProducts(JsonNode node, List<ProductMetadata> result, URI base) {
        if (node == null || node.isNull()) return;
        if (node.isArray()) { node.forEach(item -> collectJsonProducts(item, result, base)); return; }
        if (!node.isObject()) return;
        JsonNode graph = node.get("@graph");
        if (graph != null) collectJsonProducts(graph, result, base);
        String type = node.path("@type").asText("");
        if (type.equalsIgnoreCase("Product") || type.equalsIgnoreCase("ProductGroup")) {
            Map<String, String> values = new HashMap<>();
            values.put("name", node.path("name").asText(""));
            values.put("category", node.path("category").asText(""));
            JsonNode image = node.get("image");
            if (image != null) values.put("image", image.isArray() && image.size() > 0 ? image.get(0).asText("") : image.asText(""));
            JsonNode offers = node.get("offers");
            if (offers != null && offers.isArray() && offers.size() > 0) offers = offers.get(0);
            if (offers != null) values.put("price", offers.path("price").asText(offers.path("lowPrice").asText("")));
            addProduct(result, values, base);
        }
    }

    private static String elementValue(Element element) {
        return attributeValue(element, "content", "value", "href", "src").isBlank() ? element.text() : attributeValue(element, "content", "value", "href", "src");
    }

    private static String attributeValue(Element element, String... attributes) {
        for (String attribute : attributes) if (element.hasAttr(attribute)) return element.attr(attribute);
        return "";
    }
    private static void addProduct(List<ProductMetadata> result, Map<String,String> values, URI base) {
        String name = values.getOrDefault("name", "").trim(); if (name.isEmpty()) return; BigDecimal price = null; try { if (!values.getOrDefault("price", "").isBlank()) price = new BigDecimal(values.get("price").replaceAll("[^0-9.]", "")); } catch (Exception ignored) {}
        String image = decode(values.getOrDefault("image", "").trim()); if (!image.isBlank()) try { image = base.resolve(image.replace(" ", "%20")).toString(); } catch (Exception ignored) { image = ""; }
        String category = values.getOrDefault("category", "").trim();
        BigDecimal parsedPrice = price;
        if (result.stream().anyMatch(existing -> existing.name().equals(name)
                && (existing.price() == null ? parsedPrice == null
                : parsedPrice != null && parsedPrice.compareTo(existing.price()) == 0))) return;
        result.add(new ProductMetadata(name, image, price, category));
    }
    private static String decode(String value) { return value.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">").replace("&yen;", "¥"); }
    private static String stripMarkup(String value) { return decode(value.replaceAll("<[^>]+>", "").replaceAll("\\s+", " ")).trim(); }

    @GetMapping("/state")
    @Transactional(readOnly = true)
    public Snapshot state(Principal principal) {
        User user = users.findByEmail(principal.getName());
        var owned = favorites.findAllByUserIdOrderByIdAsc(user.getId());
        return new Snapshot(user.getName(), user.getEmail(), user.getImageUrl(), user.getImageSize(), user.getCash(), owned.stream().map(f -> new FavoriteView(f.getId(), f.getName(), f.getDescription(), f.getMonthlyBudget(), f.getImageUrl(), f.getImageSize())).toList(),
            owned.stream().flatMap(f -> f.getItems().stream()).map(i -> new ItemView(i.getId(), i.getFavorite().getId(), i.getName(), i.getCategory(), i.getPrice(), i.getQuantity(), i.getPurchasedDate(), i.getMembershipJoinedDate(), i.getDeadline(), i.getStatus() == null ? "OWNED" : i.getStatus(), i.getRecurrence() == null ? "NONE" : i.getRecurrence(), i.getStoreUrl(), i.getImageUrl())).toList(),
            events.findAllByUserIdOrderByStartDateAsc(user.getId()).stream().map(e -> new EventView(e.getId(), e.getFavorite() == null ? null : e.getFavorite().getId(), e.getTitle(), e.getKind(), e.getStartDate(), e.getEndDate(), e.getReminderDays(), e.getNotes())).toList());
    }
    @PostMapping("/favorites")
    public Map<String, Long> createFavorite(@RequestBody FavoriteInput input, Principal p) {
        Favorite f = new Favorite(text(input.name(), 50, true), text(input.description(), 255, false), null);
        f.setMonthlyBudget(budget(input.monthlyBudget()));
        f.setImageUrl(image(input.imageUrl()));
        f.setImageSize(size(input.imageSize(), 56));
        f.setUser(users.findByEmail(p.getName()));
        return Map.of("id", favorites.save(f).getId());
    }
    @PutMapping("/favorites/{id}")
    public void editFavorite(@PathVariable Long id, @RequestBody FavoriteInput input, Principal p) {
        Favorite f = favorite(id, p); f.setName(text(input.name(), 50, true)); f.setDescription(text(input.description(), 255, false));
        f.setMonthlyBudget(budget(input.monthlyBudget()));
        f.setImageUrl(image(input.imageUrl()));
        f.setImageSize(size(input.imageSize(), 56));
    }
    @DeleteMapping("/favorites/{id}")
    public void deleteFavorite(@PathVariable Long id, Principal p) {
        Favorite f = favorite(id, p);
        events.findAllByUserIdOrderByStartDateAsc(f.getUser().getId()).stream()
            .filter(e -> e.getFavorite() != null && e.getFavorite().getId().equals(id)).forEach(e -> e.setFavorite(null));
        favorites.delete(f);
    }
    @PostMapping("/items")
    public Map<String, Long> createItem(@RequestBody ItemInput input, Principal p) {
        FavoriteItem item = new FavoriteItem(); applyItem(item, input, p); return Map.of("id", items.save(item).getId());
    }
    @PutMapping("/items/{id}")
    public void editItem(@PathVariable Long id, @RequestBody ItemInput input, Principal p) { applyItem(item(id, p), input, p); }
    @DeleteMapping("/items/{id}")
    public void deleteItem(@PathVariable Long id, Principal p) { items.delete(item(id, p)); }
    public record ItemStatusInput(String status) {}

    @PutMapping("/items/{id}/status")
    public void updateItemStatus(@PathVariable Long id, @RequestBody ItemStatusInput input, Principal p) {
        if (!Set.of("PAID", "PLANNED").contains(Objects.toString(input.status(), ""))) bad("購入済または購入予定を選択してください。");
        item(id, p).setStatus(input.status());
    }
    @PostMapping("/events")
    public Map<String, Long> createEvent(@RequestBody EventInput input, Principal p) {
        CalendarEvent event = new CalendarEvent(); event.setUser(users.findByEmail(p.getName()));
        applyEvent(event, input, p); return Map.of("id", events.save(event).getId());
    }
    @PutMapping("/events/{id}")
    public void editEvent(@PathVariable Long id, @RequestBody EventInput input, Principal p) { applyEvent(event(id, p), input, p); }
    @DeleteMapping("/events/{id}")
    public void deleteEvent(@PathVariable Long id, Principal p) { events.delete(event(id, p)); }

    @PutMapping("/profile")
    public void updateProfile(@RequestBody ProfileInput input, Principal p) {
        User user = users.findByEmail(p.getName());
        user.setName(text(input.name(), 50, true));
        user.setImageUrl(image(input.imageUrl()));
        user.setImageSize(size(input.imageSize(), 44));
        user.setCash(money(input.cash()));
    }

    private void applyItem(FavoriteItem i, ItemInput d, Principal p) {
        i.setFavorite(favorite(d.favoriteId(), p));
        i.setName(text(d.name(), 100, true)); String category = text(d.category(), 40, true); i.setCategory(category);
        if (d.price() == null || d.price().signum() < 0 || d.price().compareTo(new BigDecimal("99999999")) > 0 || d.price().scale() > 2) bad("金額は0〜99,999,999円、小数点以下2桁以内で入力してください。");
        if (d.quantity() == null || d.quantity() < 1 || d.quantity() > 9999) bad("数量は1〜9999で入力してください。");
        if (!Set.of("OWNED", "PAID", "PLANNED").contains(Objects.toString(d.status(), ""))) bad("支出の状態を選択してください。");
        if (!Set.of("NONE", "MONTHLY", "YEARLY").contains(Objects.toString(d.recurrence(), ""))) bad("繰り返し設定を確認してください。");
        if (d.purchasedDate() == null) bad("購入日または購入予定日を入力してください。");
        LocalDate purchasedDate = d.purchasedDate();
        LocalDate joinedDate = null;
        if (isMembership(category)) {
            joinedDate = d.membershipJoinedDate() == null ? d.purchasedDate() : d.membershipJoinedDate();
            purchasedDate = joinedDate.plusMonths(1);
            d = new ItemInput(d.favoriteId(), d.name(), category, d.price(), d.quantity(), purchasedDate, joinedDate, d.deadline(), d.status(), "MONTHLY", d.storeUrl(), d.imageUrl());
        }
        if (d.deadline() != null && purchasedDate.isAfter(d.deadline())) bad("購入予定日は購入期限以前にしてください。");
        if (!"NONE".equals(d.recurrence()) && d.deadline() != null) bad("定期支払いでは購入期限を空欄にしてください。");
        i.setPrice(d.price()); i.setQuantity(d.quantity()); i.setPurchasedDate(purchasedDate); i.setMembershipJoinedDate(joinedDate);
        i.setDeadline(d.deadline()); i.setStatus(d.status()); i.setRecurrence(d.recurrence());
        String url = text(d.storeUrl(), 2048, false);
        if (!url.isEmpty()) {
            try { URI u = URI.create(url); if (!"https".equalsIgnoreCase(u.getScheme()) || u.getHost() == null || u.getUserInfo() != null) bad("ストアURLはhttps://で始まるURLを入力してください。"); }
            catch (IllegalArgumentException e) { bad("ストアURLを確認してください。"); }
        }
        i.setStoreUrl(url);
        i.setImageUrl(image(d.imageUrl()));
    }
    private static boolean isMembership(String category) { return category.contains("メンバー") || category.contains("会費"); }
    private void applyEvent(CalendarEvent e, EventInput d, Principal p) {
        e.setFavorite(d.favoriteId() == null ? null : favorite(d.favoriteId(), p));
        e.setTitle(text(d.title(), 100, true)); e.setKind(text(d.kind(), 40, true));
        if (d.startDate() == null || d.endDate() == null || d.endDate().isBefore(d.startDate())) bad("開始日と終了日を確認してください。");
        if (d.reminderDays() == null || d.reminderDays() < 0 || d.reminderDays() > 30) bad("通知日は0〜30日前で設定してください。");
        e.setStartDate(d.startDate()); e.setEndDate(d.endDate()); e.setReminderDays(d.reminderDays()); e.setNotes(text(d.notes(), 1000, false));
    }
    private Favorite favorite(Long id, Principal p) {
        if (id == null) { bad("推しを選択してください。"); }
        Favorite f = favorites.findById(id).orElseThrow(this::missing);
        owner(f.getUser(), p); return f;
    }
    private FavoriteItem item(Long id, Principal p) { FavoriteItem i = items.findById(id).orElseThrow(this::missing); owner(i.getFavorite().getUser(), p); return i; }
    private CalendarEvent event(Long id, Principal p) { CalendarEvent e = events.findById(id).orElseThrow(this::missing); owner(e.getUser(), p); return e; }
    private void owner(User user, Principal p) { if (!user.getEmail().equals(p.getName())) throw missing(); }
    private ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "データが見つかりません。"); }
    private static String text(String s, int max, boolean required) { String value = s == null ? "" : s.trim(); if ((required && value.isEmpty()) || value.length() > max) bad("入力内容を確認してください（最大" + max + "文字）。"); return value; }
    private static void bad(String message) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private static BigDecimal budget(BigDecimal value) {
        if (value != null && (value.signum() < 0 || value.compareTo(new BigDecimal("99999999")) > 0 || value.scale() > 2)) bad("月予算は0〜99,999,999円、小数点以下2桁以内で入力してください。");
        return value;
    }
    private static BigDecimal money(BigDecimal value) {
        if (value == null) return BigDecimal.ZERO;
        if (value.signum() < 0 || value.compareTo(new BigDecimal("999999999")) > 0 || value.scale() > 2) bad("所持金は0〜999,999,999円、小数点以下2桁以内で入力してください。");
        return value;
    }
    private static String image(String value) {
        if (value == null || value.isBlank()) return null;
        String image = value.trim();
        if (image.startsWith("data:image/")) {
            if (!image.matches("^data:image/(png|jpeg|webp|gif);base64,[A-Za-z0-9+/=]+$")) bad("画像はPNG・JPEG・WebP・GIF形式で選択してください。");
            if (image.length() > 2_800_000) bad("画像は2MB以下にしてください。");
            return image;
        }
        try {
            URI uri = URI.create(image);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) bad("画像URLはHTTPS形式で入力してください。");
            return image;
        } catch (IllegalArgumentException e) { bad("画像URLを確認してください。"); return null; }
    }
    private static int size(Integer value, int fallback) {
        int result = value == null ? fallback : value;
        if (result < 24 || result > 160) bad("画像サイズは24〜160pxで設定してください。");
        return result;
    }
    @ExceptionHandler(ResponseStatusException.class)
    public org.springframework.http.ResponseEntity<Map<String, String>> error(ResponseStatusException e) {
        return org.springframework.http.ResponseEntity.status(e.getStatusCode()).body(Map.of("message", Objects.toString(e.getReason(), "処理できませんでした。")));
    }
}
