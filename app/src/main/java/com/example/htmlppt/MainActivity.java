package com.example.htmlppt;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.graphics.Color;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {
    private WebView webView;
    private boolean editing = false;
    private boolean presenting = false;
    private boolean isTool = false;
    private static final String TAG = "HTMLPPT";
    private static final int REQ_STORAGE = 1001;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        FileManager.ensureDirs();
        if (!FileManager.SETTINGS_FILE.exists()) {
            FileManager.writeSettings(FileManager.defaultSettings());
        }
        copyAssetsIfNeeded();
        webView = new WebView(this);
        setupWebView();
        enableEdgeToEdge();
        setContentView(webView);
        checkPermission();
    }

    // 首次启动：把 assets/www 下的界面文件复制到 /sdcard/HTML_PPT/www，便于用户直接编辑更新
    private void copyAssetsIfNeeded() {
        try {
            String[] files = {"index.html", "editor.html", "settings.html", "app.js", "theme.css"};
            java.io.File www = FileManager.WWW_DIR;
            if (!www.exists()) www.mkdirs();
            for (String f : files) {
                java.io.File out = new java.io.File(www, f);
                if (out.exists()) continue;
                try (java.io.InputStream in = getAssets().open("www/" + f);
                     java.io.FileOutputStream os = new java.io.FileOutputStream(out)) {
                    byte[] b = new byte[8192];
                    int n;
                    while ((n = in.read(b)) > 0) os.write(b, 0, n);
                }
            }
            // 启动时把使用说明复制到数据目录根 /sdcard/HTML_PPT/README.md
            java.io.File readme = new java.io.File(FileManager.BASE, "README.md");
            if (!readme.exists()) {
                try (java.io.InputStream in = getAssets().open("www/README.md");
                     java.io.FileOutputStream os = new java.io.FileOutputStream(readme)) {
                    byte[] b = new byte[8192];
                    int n;
                    while ((n = in.read(b)) > 0) os.write(b, 0, n);
                }
            }
        } catch (Exception e) { }
    }

    private void setupWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setAllowFileAccessFromFileURLs(true);
        s.setAllowUniversalAccessFromFileURLs(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        webView.setWebViewClient(new WebViewClient());
        webView.addJavascriptInterface(new JsBridge(this), "Android");
        // 主页：优先加载可写目录 www/index.html（PPT 列表），缺失回退 assets
        String homePath = new java.io.File(FileManager.WWW_DIR, "index.html").getAbsolutePath();
        java.io.File homeIndex = new java.io.File(homePath);
        webView.loadUrl(homeIndex.exists() ? "file://" + homePath : "file:///android_asset/www/index.html");
    }

    private boolean hasManagePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        } else {
            return checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED
                    && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void checkPermission() {
        if (hasManagePermission()) { webView.reload(); return; }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            intent.setData(Uri.parse("package:" + getPackageName()));
            try {
                startActivity(intent);
            } catch (Exception e) {
                Intent i2 = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivity(i2);
            }
        } else {
            requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
        }
    }

    // 沉浸式：让 WebView 内容延伸到状态栏与底部导航栏（小白条）
    private void enableEdgeToEdge() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            View decor = getWindow().getDecorView();
            decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(Color.TRANSPARENT);
            getWindow().setNavigationBarColor(Color.TRANSPARENT);
        }
    }

    public void hideSystemUI() { Log.d(TAG, "hideSystemUI called, presenting=" + presenting + " SDK=" + Build.VERSION.SDK_INT); runOnUiThread(new Ui(this, 2)); }
    private void doHideUI() {
        Log.d(TAG, "doHideUI start SDK=" + Build.VERSION.SDK_INT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN
                    | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
            Log.d(TAG, "doHideUI: flags added");
            WindowInsetsController c = getWindow().getInsetsController();
            Log.d(TAG, "doHideUI: insetsController null=" + (c == null));
            if (c != null) {
                c.hide(WindowInsets.Type.statusBars());
                c.hide(WindowInsets.Type.navigationBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(0xff0f2027));
            try {
                android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
                android.view.WindowInsets wi = getWindow().getDecorView().getRootWindowInsets();
                int sbTop = wi != null ? wi.getInsets(WindowInsets.Type.statusBars()).top : -1;
                Log.d(TAG, "view: webTop=" + webView.getTop() + " webH=" + webView.getHeight() + " screenH=" + dm.heightPixels + " sbTop=" + sbTop);
            } catch (Exception e) { }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }
    public void showSystemUI() { Log.d(TAG, "showSystemUI called, presenting=" + presenting); runOnUiThread(new Ui(this, 3)); }
    private void doShowUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN
                    | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) { c.show(WindowInsets.Type.statusBars()); c.show(WindowInsets.Type.navigationBars()); }
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }
    public void exitApp() { runOnUiThread(new Ui(this, 4)); }
    private void doExit() { finish(); }

    @Override
protected void onResume() { super.onResume(); checkPermission(); }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        Log.d(TAG, "onWindowFocusChanged hasFocus=" + hasFocus + " presenting=" + presenting);
        if (hasFocus) { if (presenting) hideSystemUI(); else showSystemUI(); }
    }

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Log.d(TAG, "onConfigurationChanged orient=" + newConfig.orientation);
        if (newConfig.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE) {
            hideSystemUI();
        } else {
            if (!presenting) showSystemUI();
        }
    }

    public void setEditing(boolean b) { editing = b; }
    public void setPresenting(boolean b) { presenting = b; }
    public void enterTool() { isTool = true; }

    public void goHome() { runOnUiThread(new Ui(this, 1)); }
    private void doGoHome() {
        editing = false; presenting = false; isTool = false;
        String p = new java.io.File(FileManager.WWW_DIR, "index.html").getAbsolutePath();
        webView.loadUrl("file://" + p);
    }

    @Override
    public void onBackPressed() {
        if (presenting) {
            if (webView != null) webView.evaluateJavascript("exitPresent()", null);
            presenting = false;
            return;
        }
        if (editing) {
            if (webView != null) webView.evaluateJavascript("confirmExitEdit()", null);
            return;
        }
        if (isTool) { goHome(); return; }
        // 设置页：系统返回回主页
        String cur = webView != null ? webView.getUrl() : null;
        if (cur != null && cur.endsWith("settings.html")) { goHome(); return; }
        // 关于页：系统返回回设置页
        if (cur != null && cur.endsWith("about.html")) {
            String p = new java.io.File(FileManager.WWW_DIR, "settings.html").getAbsolutePath();
            webView.loadUrl("file://" + p);
            return;
        }
        // 主页：系统返回固定为退出应用（避免误回上一个编辑器）
        exitApp();
    }

    // 具名 Runnable 类：把 UI 操作安全地切回主线程（避免 d8 对匿名内部类/lambda 崩溃）
    static class Ui implements Runnable {
        private final MainActivity a;
        private final int op;
        Ui(MainActivity a, int op) { this.a = a; this.op = op; }
        public void run() {
            switch (op) {
                case 1: a.doGoHome(); break;
                case 2: a.doHideUI(); break;
                case 3: a.doShowUI(); break;
                case 4: a.doExit(); break;
            }
        }
    }
}