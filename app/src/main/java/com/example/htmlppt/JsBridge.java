package com.example.htmlppt;

import android.content.Context;
import android.webkit.JavascriptInterface;

import org.json.JSONArray;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import android.util.Base64;

/**
 * 给 WebView 里 JS 调用的原生接口（读写 /sdcard/HTML_PPT 下的文件和设置）
 */
public class JsBridge {
    private final Context ctx;

    public JsBridge(Context ctx) {
        this.ctx = ctx;
    }

    /* ---- PPT ---- */
    @JavascriptInterface
    public String listPpt() {
        JSONArray a = new JSONArray();
        for (String s : FileManager.listPpt()) a.put(s);
        return a.toString();
    }

    @JavascriptInterface
    public String listPptDetail() {
        return FileManager.listPptDetail();
    }

    @JavascriptInterface
    public String readPpt(String name) {
        FileManager.ensureDirs();
        return FileManager.read(FileManager.pptFile(name));
    }

    @JavascriptInterface
    public boolean savePpt(String name, String content) {
        return FileManager.write(FileManager.pptFile(name), content);
    }

    @JavascriptInterface
    public boolean newPpt(String name) {
        if (name == null || name.trim().isEmpty()) return false;
        if (!name.endsWith(".txt")) name = name + ".txt";
        FileManager.ensureDirs();
        File f = FileManager.pptFile(name);
        if (f == null || f.exists()) return false;
        String tpl = "# 新建演示\n## 双击编辑\n\n- 用 --- 分隔每一页\n- 用 # 写标题\n- 支持 ![本地图](file:///storage/emulated/0/HTML_PPT/../图片.png)\n";
        return FileManager.write(f, tpl);
    }

    @JavascriptInterface
    public boolean deletePpt(String name) {
        return FileManager.delete(FileManager.pptFile(name));
    }

    @JavascriptInterface
    public boolean renamePpt(String oldName, String newName) {
        if (!newName.endsWith(".txt")) newName += ".txt";
        return FileManager.renamePpt(oldName, newName);
    }

    /* ---- Settings ---- */
    @JavascriptInterface
    public String readSettings() {
        return FileManager.readSettings();
    }

    @JavascriptInterface
    public boolean saveSettings(String json) {
        return FileManager.writeSettings(json);
    }

    /* ---- Themes ---- */
    @JavascriptInterface
    public String listThemes() {
        JSONArray a = new JSONArray();
        for (String s : FileManager.listThemes()) a.put(s);
        return a.toString();
    }

    @JavascriptInterface
    public String readThemeFile(String name) {
        return FileManager.read(FileManager.themeFile(name));
    }

    @JavascriptInterface
    public boolean saveThemeFile(String name, String content) {
        return FileManager.write(FileManager.themeFile(name), content);
    }

    @JavascriptInterface
    public boolean deleteThemeFile(String name) {
        return FileManager.delete(FileManager.themeFile(name));
    }

    @JavascriptInterface
    public void exitApp() { if (ctx instanceof MainActivity) ((MainActivity) ctx).exitApp(); }

    @JavascriptInterface
    public void hideSystemUI() { if (ctx instanceof MainActivity) ((MainActivity) ctx).hideSystemUI(); }

    @JavascriptInterface
    public void showSystemUI() { if (ctx instanceof MainActivity) ((MainActivity) ctx).showSystemUI(); }

    @JavascriptInterface
    public void setEditing(boolean b) { if (ctx instanceof MainActivity) ((MainActivity) ctx).setEditing(b); }

    @JavascriptInterface
    public void setPresenting(boolean b) { if (ctx instanceof MainActivity) ((MainActivity) ctx).setPresenting(b); }

    @JavascriptInterface
    public void goToHome() { if (ctx instanceof MainActivity) ((MainActivity) ctx).goHome(); }

    @JavascriptInterface
    public void enterTool() { if (ctx instanceof MainActivity) ((MainActivity) ctx).enterTool(); }

    @JavascriptInterface
    public boolean saveExport(String name, String content) {
        if (name == null || name.trim().isEmpty()) return false;
        if (!name.endsWith(".html")) name = name + ".html";
        File dir = new File("/sdcard/HTML_PPT/Download");
        if (!dir.exists()) dir.mkdirs();
        File f = new File(dir, name);
        return FileManager.write(f, content);
    }

    /* ---- 读取本地图片并转 base64（导出内嵌用） ---- */
    @JavascriptInterface
    public String readImageBase64(String path) {
        if (path == null || path.trim().isEmpty()) return "";
        String p = path.trim();
        if (p.startsWith("file://")) p = p.substring("file://".length());
        File f = new File(p);
        if (!f.exists()) {
            try { f = f.getCanonicalFile(); } catch (IOException e) { return ""; }
        }
        if (!f.isFile() || f.length() > 50L * 1024 * 1024) return "";
        try {
            byte[] data = new byte[(int) f.length()];
            int off = 0;
            try (FileInputStream in = new FileInputStream(f)) {
                while (off < data.length && off >= 0) {
                    int r = in.read(data, off, data.length - off);
                    if (r < 0) break;
                    off += r;
                }
            }
            if (off < data.length) data = java.util.Arrays.copyOf(data, off);
            return "data:" + mimeOf(f.getName()) + ";base64," + Base64.encodeToString(data, Base64.NO_WRAP);
        } catch (IOException e) { return ""; }
    }

    private String mimeOf(String name) {
        String n = name.toLowerCase();
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".gif")) return "image/gif";
        if (n.endsWith(".webp")) return "image/webp";
        if (n.endsWith(".bmp")) return "image/bmp";
        if (n.endsWith(".svg")) return "image/svg+xml";
        return "application/octet-stream";
    }

    /* ---- 版本信息 ---- */
    @JavascriptInterface
    public String getVersion() {
        String app = "1.0.0";
        try {
            android.content.pm.PackageInfo pi = ctx.getPackageManager().getPackageInfo(ctx.getPackageName(), 0);
            app = pi.versionName;
        } catch (Exception e) { }
        String hot = "2026.08.26.1";
        return "{\"app\":\"" + app + "\",\"hot\":\"" + hot + "\"}";
    }

    /* ---- 同步内置 HTML：把 assets/www 复制覆盖到 /sdcard/HTML_PPT/www ---- */
    @JavascriptInterface
    public String syncBuiltin() {
        try {
            String[] files = ctx.getAssets().list("www");
            java.io.File www = FileManager.WWW_DIR;
            if (!www.exists()) www.mkdirs();
            int n = 0;
            for (String f : files) {
                java.io.File out = new java.io.File(www, f);
                try (InputStream in = ctx.getAssets().open("www/" + f);
                     java.io.FileOutputStream os = new java.io.FileOutputStream(out)) {
                    byte[] b = new byte[8192];
                    int r;
                    while ((r = in.read(b)) > 0) os.write(b, 0, r);
                    n++;
                } catch (Exception e) { }
            }
            try (InputStream in = ctx.getAssets().open("www/README.md");
                 java.io.FileOutputStream os = new java.io.FileOutputStream(new java.io.File(FileManager.BASE, "README.md"))) {
                byte[] b = new byte[8192];
                int r;
                while ((r = in.read(b)) > 0) os.write(b, 0, r);
            } catch (Exception e) { }
            return "ok:" + n;
        } catch (Exception e) {
            return "err:" + e.getMessage();
        }
    }
}