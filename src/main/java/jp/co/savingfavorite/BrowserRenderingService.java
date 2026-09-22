package jp.co.savingfavorite;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.CLI;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import java.net.URI;
import java.time.Duration;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** 公開ページのJavaScript描画後HTMLを取得する。認証情報やCAPTCHA回避は行わない。 */
@Service
public class BrowserRenderingService {
    private static final String USER_AGENT = "SavingFavorite/1.0 (+public-product-metadata)";
    private final boolean enabled;
    private final boolean autoInstall;
    private volatile boolean installAttempted;
    private volatile boolean browserReady;

    public BrowserRenderingService(
            @Value("${scraping.browser.enabled:true}") boolean enabled,
            @Value("${scraping.browser.auto-install:true}") boolean autoInstall) {
        this.enabled = enabled;
        this.autoInstall = autoInstall;
    }

    public String fetchRenderedHtml(URI uri) {
        if (!enabled) return "";
        if (!ensureBrowserInstalled()) return "";
        try (Playwright playwright = Playwright.create();
             Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true))) {
            Page page = browser.newPage(new Browser.NewPageOptions().setUserAgent(USER_AGENT));
            page.setDefaultTimeout(Duration.ofSeconds(12).toMillis());
            page.navigate(uri.toString(), new Page.NavigateOptions()
                    .setWaitUntil(com.microsoft.playwright.options.WaitUntilState.DOMCONTENTLOADED)
                    .setTimeout(Duration.ofSeconds(15).toMillis()));
            page.waitForTimeout(1200);
            page.evaluate("window.scrollTo(0, document.body.scrollHeight)");
            page.waitForTimeout(800);
            return page.content();
        } catch (Exception ignored) {
            // Chromium未インストール、タイムアウト、JSエラーなどは通常取得へ戻す。
            return "";
        }
    }

    private synchronized boolean ensureBrowserInstalled() {
        if (browserReady) return true;
        if (installAttempted) return false;
        installAttempted = true;
        try (Playwright playwright = Playwright.create()) {
            Path executable = Path.of(playwright.chromium().executablePath());
            if (Files.isRegularFile(executable)) {
                browserReady = true;
                return true;
            }
        } catch (Exception ignored) {
            // 初回のPlaywright起動に失敗した場合は自動インストールを試す。
        }
        if (!autoInstall) return false;
        try {
            CLI.main(new String[]{"install", "chromium"});
            try (Playwright playwright = Playwright.create()) {
                browserReady = Files.isRegularFile(Path.of(playwright.chromium().executablePath()));
            }
        } catch (Exception ignored) {
            browserReady = false;
        }
        return browserReady;
    }
}
