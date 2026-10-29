package com.nail.chesscoach;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.FrameLayout;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String LOCAL_HOST = "appassets.androidplatform.net";
    private final Handler main = new Handler(Looper.getMainLooper());
    private WebView webView;
    private TextView status;
    private ProgressBar progress;
    private Button retry;
    private LinearLayout loadingScreen;
    private volatile boolean loading = false;

    private static class DownloadItem {
        final String name, url;
        DownloadItem(String name, String url) { this.name = name; this.url = url; }
    }

    private final DownloadItem[] downloads = new DownloadItem[] {
        new DownloadItem("chess.js", "https://cdn.jsdelivr.net/npm/chess.js@1.4.0/dist/esm/chess.js"),
        new DownloadItem("stockfish-19-lite-single.js", "https://unpkg.com/stockfish@19.0.1/bin/stockfish-19-lite-single.js"),
        new DownloadItem("stockfish-19-lite-single.wasm", "https://unpkg.com/stockfish@19.0.1/bin/stockfish-19-lite-single.wasm")
    };

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(16, 20, 17));
        getWindow().setNavigationBarColor(Color.rgb(16, 20, 17));
        showLoadingScreen();
        prepareResources();
    }

    private void showLoadingScreen() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(16, 20, 17));
        loadingScreen = new LinearLayout(this);
        loadingScreen.setOrientation(LinearLayout.VERTICAL);
        loadingScreen.setGravity(Gravity.CENTER);
        loadingScreen.setPadding(28, 28, 28, 28);
        TextView title = new TextView(this);
        title.setText("♟ CHESS COACH");
        title.setTextColor(Color.rgb(131, 201, 134));
        title.setTextSize(27);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        loadingScreen.addView(title, new LinearLayout.LayoutParams(-1, -2));
        status = new TextView(this);
        status.setText("Подготовка приложения…");
        status.setTextColor(Color.rgb(238, 245, 239));
        status.setTextSize(15);
        status.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.topMargin = 24;
        loadingScreen.addView(status, sp);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, -2);
        pp.topMargin = 18;
        loadingScreen.addView(progress, pp);
        retry = new Button(this);
        retry.setText("Повторить загрузку");
        retry.setVisibility(View.GONE);
        retry.setOnClickListener(v -> prepareResources());
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-2, -2);
        rp.topMargin = 16;
        loadingScreen.addView(retry, rp);
        TextView note = new TextView(this);
        note.setText("При первом запуске скачиваются шахматная логика и Stockfish. После этого они хранятся на телефоне и работают без интернета.");
        note.setTextColor(Color.rgb(169, 183, 172));
        note.setTextSize(13);
        note.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-1, -2);
        np.topMargin = 20;
        loadingScreen.addView(note, np);
        root.addView(loadingScreen, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
    }

    private void setStatus(String message, int percent) {
        main.post(() -> {
            if (status != null) status.setText(message);
            if (progress != null) progress.setProgress(Math.max(0, Math.min(100, percent)));
        });
    }

    private void prepareResources() {
        if (loading) return;
        loading = true;
        retry.setVisibility(View.GONE);
        new Thread(() -> {
            try {
                File webRoot = new File(getFilesDir(), "www");
                if (!webRoot.exists() && !webRoot.mkdirs()) throw new Exception("Не удалось создать папку ресурсов.");
                copyAssetsRecursively("www", webRoot);
                int total = downloads.length;
                for (int i = 0; i < total; i++) {
                    DownloadItem item = downloads[i];
                    File target = new File(webRoot, item.name);
                    if (target.exists() && target.length() > 0) {
                        setStatus("Ресурс уже сохранён: " + item.name, (i + 1) * 90 / total);
                        continue;
                    }
                    setStatus("Скачивание " + item.name + " (" + (i + 1) + "/" + total + ")…", i * 90 / total);
                    downloadToFile(item.url, target);
                    if (!target.exists() || target.length() < 100) throw new Exception("Скачанный файл повреждён: " + item.name);
                }
                File js = new File(webRoot, "chess.js");
                File sfjs = new File(webRoot, "stockfish-19-lite-single.js");
                File sfwasm = new File(webRoot, "stockfish-19-lite-single.wasm");
                if (!js.isFile() || !sfjs.isFile() || !sfwasm.isFile()) throw new Exception("Не все ресурсы скачаны.");
                setStatus("Готово. Запускаю Chess Coach…", 100);
                main.postDelayed(() -> openApp(webRoot), 250);
            } catch (Exception e) {
                loading = false;
                String message = e.getMessage() == null ? "Неизвестная ошибка" : e.getMessage();
                setStatus("Не удалось подготовить приложение. Подключись к интернету и нажми «Повторить загрузку».\n\n" + message, progress == null ? 0 : progress.getProgress());
                main.post(() -> retry.setVisibility(View.VISIBLE));
            }
        }, "chess-resource-setup").start();
    }

    private void copyAssetsRecursively(String assetPath, File destination) throws Exception {
        String[] children = getAssets().list(assetPath);
        if (children == null || children.length == 0) {
            try (InputStream in = getAssets().open(assetPath); OutputStream out = new FileOutputStream(destination)) {
                copy(in, out);
            }
            return;
        }
        if (!destination.exists() && !destination.mkdirs()) throw new Exception("Не удалось создать " + destination.getName());
        for (String child : children) {
            copyAssetsRecursively(assetPath + "/" + child, new File(destination, child));
        }
    }

    private void downloadToFile(String address, File target) throws Exception {
        File temp = new File(target.getParentFile(), target.getName() + ".part");
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(address).openConnection();
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(60000);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty("User-Agent", "ChessCoachAndroid/1.0");
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) throw new Exception("Сервер вернул HTTP " + code + " для " + target.getName());
            try (InputStream in = connection.getInputStream(); OutputStream out = new FileOutputStream(temp)) {
                copy(in, out);
            }
            if (temp.length() < 100) throw new Exception("Пустой файл: " + target.getName());
            if (target.exists() && !target.delete()) throw new Exception("Не удалось обновить " + target.getName());
            if (!temp.renameTo(target)) throw new Exception("Не удалось сохранить " + target.getName());
        } finally {
            if (connection != null) connection.disconnect();
            if (temp.exists()) temp.delete();
        }
    }

    private void copy(InputStream in, OutputStream out) throws Exception {
        byte[] buffer = new byte[32768];
        int n;
        while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
        out.flush();
    }

    private void openApp(File webRoot) {
        FrameLayout root = new FrameLayout(this);
        webView = new WebView(this);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);
        webView.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return serveLocalResource(request.getUrl().toString(), webRoot);
            }
            @SuppressWarnings("deprecation")
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                return serveLocalResource(url, webRoot);
            }
        });
        webView.setWebChromeClient(new WebChromeClient());
        root.addView(webView, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        webView.loadUrl("https://" + LOCAL_HOST + "/assets/www/index.html");
    }

    private WebResourceResponse serveLocalResource(String urlString, File webRoot) {
        try {
            URL url = new URL(urlString);
            if (!"https".equals(url.getProtocol()) || !LOCAL_HOST.equals(url.getHost())) return null;
            String path = url.getPath();
            String prefix = "/assets/www/";
            if (!path.startsWith(prefix)) return notFound();
            String relative = path.substring(prefix.length());
            if (relative.contains("..") || relative.startsWith("/")) return notFound();
            File file = new File(webRoot, relative);
            if (!file.isFile()) return notFound();
            String mime = mimeType(file.getName());
            return new WebResourceResponse(mime, "UTF-8", new java.io.FileInputStream(file));
        } catch (Exception ignored) { return notFound(); }
    }

    private WebResourceResponse notFound() {
        return new WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", null,
                new java.io.ByteArrayInputStream("Not found".getBytes()));
    }

    private String mimeType(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".html")) return "text/html";
        if (lower.endsWith(".js")) return "application/javascript";
        if (lower.endsWith(".wasm")) return "application/wasm";
        if (lower.endsWith(".css")) return "text/css";
        if (lower.endsWith(".json") || lower.endsWith(".webmanifest")) return "application/json";
        if (lower.endsWith(".svg")) return "image/svg+xml";
        if (lower.endsWith(".png")) return "image/png";
        return "application/octet-stream";
    }

    @Override public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override protected void onDestroy() {
        if (webView != null) { webView.stopLoading(); webView.destroy(); webView = null; }
        super.onDestroy();
    }
}
