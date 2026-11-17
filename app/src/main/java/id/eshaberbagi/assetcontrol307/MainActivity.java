package id.eshaberbagi.assetcontrol307;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.view.Gravity;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.PermissionRequest;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebResourceResponse;
import android.webkit.SslErrorHandler;
import android.net.http.SslError;
import android.content.ActivityNotFoundException;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.browser.customtabs.CustomTabColorSchemeParams;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

/**
 * Android shell for the existing IT Asset Control 307 Google Apps Script app.
 * All inventory records remain in Google Sheets / Drive; no local data cache.
 */
public class MainActivity extends Activity {
    private static final String APP_URL =
            "https://script.google.com/macros/s/AKfycbyGGoMSfhSTVIfUQJHgeUm4h7WIt8hvhXmHYSW6VbBwTWIZKey4p8KUImCOVTKCBtkU8w/exec";
    private static final int FILE_REQUEST = 300;
    private static final int CAMERA_REQUEST = 301;
    private static final int RED = Color.rgb(214, 25, 53);

    private WebView web;
    private ProgressBar progress;
    private LinearLayout errorPanel;
    private android.webkit.ValueCallback<Uri[]> fileCallback;
    private Uri cameraUri;
    private PermissionRequest cameraPermissionRequest;
    private boolean browserFallbackOpened = false;

    private int dp(float n) {
        return Math.round(n * getResources().getDisplayMetrics().density);
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);

        getWindow().setStatusBarColor(RED);
        getWindow().setNavigationBarColor(Color.BLACK);
        enableImmersive();

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.WHITE);

        web = new WebView(this);
        root.addView(web, new FrameLayout.LayoutParams(-1, -1));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(false);
        progress.setMax(100);
        progress.setProgressTintList(android.content.res.ColorStateList.valueOf(RED));
        root.addView(progress, new FrameLayout.LayoutParams(-1, dp(3)));

        errorPanel = buildErrorPanel();
        errorPanel.setVisibility(View.GONE);
        root.addView(errorPanel, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);

        configureWebView();

        if (state != null) {
            web.restoreState(state);
        } else {
            web.loadUrl(APP_URL);
        }
    }

    private LinearLayout buildErrorPanel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER);
        panel.setPadding(dp(24), dp(24), dp(24), dp(24));
        panel.setBackgroundColor(Color.WHITE);

        TextView title = new TextView(this);
        title.setText("Aplikasi belum bisa dimuat");
        title.setTextSize(20);
        title.setTextColor(Color.rgb(30, 32, 38));
        title.setGravity(Gravity.CENTER);
        panel.addView(title);

        TextView explanation = new TextView(this);
        explanation.setText("Periksa koneksi internet. Jika Google meminta login dan menolaknya di aplikasi, gunakan Chrome.");
        explanation.setTextSize(14);
        explanation.setGravity(Gravity.CENTER);
        explanation.setTextColor(Color.DKGRAY);
        explanation.setPadding(0, dp(12), 0, dp(24));
        panel.addView(explanation);

        Button retry = new Button(this);
        retry.setText("Muat ulang");
        retry.setOnClickListener(v -> {
            errorPanel.setVisibility(View.GONE);
            web.loadUrl(APP_URL);
        });
        panel.addView(retry, new LinearLayout.LayoutParams(-1, dp(52)));

        Button browser = new Button(this);
        browser.setText("Buka lewat Chrome / browser");
        browser.setOnClickListener(v -> openExternally(APP_URL));
        panel.addView(browser, new LinearLayout.LayoutParams(-1, dp(52)));
        return panel;
    }

    private void configureWebView() {
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setLoadsImagesAutomatically(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(false);
        s.setTextZoom(100);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            s.setSafeBrowsingEnabled(true);
        }

        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                String host = uri.getHost();

                if (host != null && host.equalsIgnoreCase("accounts.google.com")
                        && request.isForMainFrame()) {
                    // Google authentication rejects embedded WebViews. Use the actual
                    // Chrome session, where Google sign-in cookies already exist.
                    openInCompatibleBrowser("Login Google memerlukan Chrome");
                    return true;
                }
                if (isGoogleScriptHost(uri)) {
                    return false;
                }
                // All external links are opened by the user's browser, not inside
                // the embedded WebView (which may reject Google login).
                if (request.isForMainFrame()) {
                    if ("https".equalsIgnoreCase(uri.getScheme())) {
                        openExternally(uri.toString());
                    }
                    return true;
                }
                return false;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progress.setVisibility(View.GONE);
                // Some Google sign-in redirects happen without shouldOverrideUrlLoading.
                // They cannot be completed safely within an embedded WebView.
                if (url != null && url.startsWith("https://accounts.google.com/")) {
                    openInCompatibleBrowser("Login Google memerlukan Chrome");
                }
            }

            @Override
            public void onReceivedHttpError(WebView view, WebResourceRequest request,
                                            WebResourceResponse errorResponse) {
                if (request.isForMainFrame() && errorResponse != null &&
                        (errorResponse.getStatusCode() == 401 ||
                         errorResponse.getStatusCode() == 403)) {
                    openInCompatibleBrowser("Akses Google memerlukan sesi browser");
                }
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler,
                                           SslError error) {
                // Never bypass invalid SSL certificates.
                handler.cancel();
                openInCompatibleBrowser("Koneksi Google tidak dapat diverifikasi");
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request,
                                        WebResourceError error) {
                if (request.isForMainFrame()) {
                    progress.setVisibility(View.GONE);
                    openInCompatibleBrowser("Halaman Google tidak dapat dimuat dalam aplikasi");
                }
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int percentage) {
                if (percentage < 100) {
                    progress.setProgress(percentage);
                    progress.setVisibility(View.VISIBLE);
                } else {
                    progress.setVisibility(View.GONE);
                }
            }

            @Override
            public boolean onShowFileChooser(WebView view,
                    android.webkit.ValueCallback<Uri[]> callback,
                    FileChooserParams params) {
                if (fileCallback != null) {
                    fileCallback.onReceiveValue(null);
                }
                fileCallback = callback;
                cameraUri = null;

                try {
                    Intent gallery = params.createIntent();
                    Intent camera = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
                    ArrayList<Intent> extraIntents = new ArrayList<>();

                    File pictures = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
                    if (pictures != null && (pictures.exists() || pictures.mkdirs())) {
                        File photo = File.createTempFile("it307_", ".jpg", pictures);
                        cameraUri = FileProvider.getUriForFile(
                                MainActivity.this,
                                "id.eshaberbagi.assetcontrol307.fileprovider",
                                photo);
                        camera.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri);
                        camera.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                                | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                        camera.setClipData(ClipData.newRawUri("photo", cameraUri));
                        extraIntents.add(camera);
                    }

                    Intent chooser = Intent.createChooser(gallery, "Pilih foto atau pindai barcode");
                    chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS,
                            extraIntents.toArray(new Intent[0]));
                    startActivityForResult(chooser, FILE_REQUEST);
                    return true;
                } catch (Exception e) {
                    fileCallback.onReceiveValue(null);
                    fileCallback = null;
                    Toast.makeText(MainActivity.this, "Gagal membuka kamera / galeri", Toast.LENGTH_LONG).show();
                    return false;
                }
            }

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                runOnUiThread(() -> {
                    if (!isGoogleScriptHost(request.getOrigin())) {
                        request.deny();
                        return;
                    }
                    boolean wantsCamera = false;
                    for (String resource : request.getResources()) {
                        if (PermissionRequest.RESOURCE_VIDEO_CAPTURE.equals(resource)) {
                            wantsCamera = true;
                        }
                    }
                    if (!wantsCamera) {
                        request.deny();
                        return;
                    }
                    if (Build.VERSION.SDK_INT >= 23 &&
                            checkSelfPermission(Manifest.permission.CAMERA)
                                    != PackageManager.PERMISSION_GRANTED) {
                        cameraPermissionRequest = request;
                        requestPermissions(new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST);
                    } else {
                        request.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
                    }
                });
            }
        });

        web.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent,
                                        String contentDisposition, String mimeType,
                                        long length) {
                try {
                    if (!url.startsWith("https://")) {
                        Toast.makeText(MainActivity.this,
                                "Unduhan ini perlu dibuka melalui Chrome", Toast.LENGTH_LONG).show();
                        return;
                    }
                    DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url));
                    String cookies = CookieManager.getInstance().getCookie(url);
                    if (cookies != null) {
                        r.addRequestHeader("Cookie", cookies);
                    }
                    r.addRequestHeader("User-Agent", userAgent);
                    if (mimeType != null) r.setMimeType(mimeType);
                    r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    String name = URLUtil.guessFileName(url, contentDisposition, mimeType);
                    r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
                    ((DownloadManager) getSystemService(DOWNLOAD_SERVICE)).enqueue(r);
                    Toast.makeText(MainActivity.this, "Unduhan dimulai", Toast.LENGTH_SHORT).show();
                } catch (Exception ex) {
                    openExternally(url);
                }
            }
        });
    }

    private boolean isGoogleScriptHost(Uri uri) {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme())) return false;
        String host = uri.getHost();
        if (host == null) return false;
        return host.equals("script.google.com")
                || host.equals("script.googleusercontent.com")
                || host.endsWith(".script.googleusercontent.com");
    }

    private void showError() {
        runOnUiThread(() -> errorPanel.setVisibility(View.VISIBLE));
    }

    /**
     * Fall back automatically to a browser session trusted for Google login.
     * Chrome Custom Tabs share Chrome cookies. They are not fully fullscreen,
     * but Google sign-in and Apps Script's HTML-service iframe work reliably
     * compared with an embedded WebView.
     */
    private void openInCompatibleBrowser(String reason) {
        runOnUiThread(() -> {
            if (browserFallbackOpened || isFinishing()) return;
            browserFallbackOpened = true;
            try {
                CustomTabColorSchemeParams colors =
                    new CustomTabColorSchemeParams.Builder()
                        .setToolbarColor(RED)
                        .setSecondaryToolbarColor(RED)
                        .build();
                CustomTabsIntent tabs = new CustomTabsIntent.Builder()
                    .setDefaultColorSchemeParams(colors)
                    .setShowTitle(false)
                    .setUrlBarHidingEnabled(true)
                    .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
                    .build();
                tabs.launchUrl(this, Uri.parse(APP_URL));
                finish();
            } catch (Exception ex) {
                browserFallbackOpened = false;
                try {
                    openExternally(APP_URL);
                    finish();
                } catch (Exception fallbackException) {
                    showError();
                }
            }
        });
    }

    private void openExternally(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, "Browser tidak ditemukan", Toast.LENGTH_SHORT).show();
        }
    }

    private void enableImmersive() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
    }

    @Override
    public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (focused) enableImmersive();
    }

    @Override
    public void onRequestPermissionsResult(int request, String[] permissions,
                                           int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == CAMERA_REQUEST && cameraPermissionRequest != null) {
            PermissionRequest permission = cameraPermissionRequest;
            cameraPermissionRequest = null;
            if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
                permission.grant(new String[]{PermissionRequest.RESOURCE_VIDEO_CAPTURE});
            } else {
                permission.deny();
            }
        }
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != FILE_REQUEST || fileCallback == null) return;
        Uri[] uris = null;
        if (result == RESULT_OK) {
            if (data != null) {
                uris = WebChromeClient.FileChooserParams.parseResult(result, data);
            }
            if ((uris == null || uris.length == 0) && cameraUri != null) {
                uris = new Uri[]{cameraUri};
            }
        }
        fileCallback.onReceiveValue(uris);
        fileCallback = null;
        cameraUri = null;
    }

    @Override
    public void onBackPressed() {
        if (errorPanel.getVisibility() == View.VISIBLE) {
            errorPanel.setVisibility(View.GONE);
            web.loadUrl(APP_URL);
        } else if (web.canGoBack()) {
            web.goBack();
        } else {
            new AlertDialog.Builder(this)
                    .setTitle("Keluar dari IT Asset Control?")
                    .setMessage("Data yang tersimpan tetap berada di Google Sheets.")
                    .setNegativeButton("Batal", null)
                    .setPositiveButton("Keluar", (d, which) -> finish())
                    .show();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        web.saveState(state);
        super.onSaveInstanceState(state);
    }

    @Override
    protected void onDestroy() {
        web.destroy();
        super.onDestroy();
    }
}
