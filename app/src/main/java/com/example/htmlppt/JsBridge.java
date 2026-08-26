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

    /* ---- 更新 / 网络 ---- */
    @JavascriptInterface
    public String checkRemote(final String url) {
        if (url == null || url.trim().isEmpty()) return "";
        final String[] res = { "" };
        Thread t = new Thread(new Net(url, null, res, 0));
        t.start();
        try { t.join(20000); } catch (Exception e) { }
        return res[0];
    }

    @JavascriptInterface
    public String downloadApply(final String url, final String targetDir) {
        final String[] res = { "err:timeout" };
        Thread t = new Thread(new Net(url, targetDir, res, 1));
        t.start();
        try { t.join(30000); } catch (Exception e) { }
        return res[0];
    }

    private static class Net implements Runnable {
        private final String a, b; private final String[] out; private final int op;
        Net(String a, String b, String[] out, int op) { this.a = a; this.b = b; this.out = out; this.op = op; }
        public void run() {
            if (op == 0) out[0] = doGet(a);
            else if (op == 1) out[0] = doDownload(a, b);
        }
    }

    private static String doGet(String url) {
        try {
            java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            c.setConnectTimeout(15000); c.setReadTimeout(15000);
            c.setRequestMethod("GET");
            c.setRequestProperty("User-Agent", "Mozilla/5.0");
            int code = c.getResponseCode();
            if (code >= 200 && code < 300) {
                java.io.InputStream in = c.getInputStream();
                java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                byte[] b = new byte[8192]; int n;
                while ((n = in.read(b)) > 0) bos.write(b, 0, n);
                return bos.toString("UTF-8");
            }
            return "ERR:http" + code;
        } catch (Exception e) { return "ERR:" + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName()); }
    }

    private static String doDownload(String url, String targetDir) {
        try {
            java.io.File dir = new java.io.File(targetDir);
            if (!dir.exists()) dir.mkdirs();
            java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
            c.setConnectTimeout(20000); c.setReadTimeout(20000);
            c.setRequestMethod("GET");
            c.setRequestProperty("User-Agent", "Mozilla/5.0");
            int code = c.getResponseCode();
            if (!(code >= 200 && code < 300)) return "err:http " + code;
            java.io.InputStream in = c.getInputStream();
            return unzipStream(in, dir);
        } catch (Exception e) { return "err:" + e.getMessage(); }
    }

    @JavascriptInterface
    public String importZip(String zipPath) {
        try {
            java.io.File f = new java.io.File(zipPath);
            if (!f.exists()) return "err:not found " + zipPath;
            java.io.File dir = FileManager.WWW_DIR;
            if (!dir.exists()) dir.mkdirs();
            try (java.io.FileInputStream in = new java.io.FileInputStream(f)) {
                return unzipStream(in, dir);
            }
        } catch (Exception e) { return "err:" + e.getMessage(); }
    }

    private static String unzipStream(java.io.InputStream in, java.io.File dir) throws Exception {
        int count = 0;
        java.util.zip.ZipInputStream zin = new java.util.zip.ZipInputStream(in);
        java.util.zip.ZipEntry entry;
        while ((entry = zin.getNextEntry()) != null) {
            String name = entry.getName();
            if (name == null || name.contains("..")) continue;
            java.io.File out = new java.io.File(dir, name);
            if (entry.isDirectory()) { out.mkdirs(); continue; }
            java.io.File parent = out.getParentFile();
            if (parent != null) parent.mkdirs();
            try (java.io.FileOutputStream os = new java.io.FileOutputStream(out)) {
                byte[] b = new byte[8192]; int n;
                while ((n = zin.read(b)) > 0) os.write(b, 0, n);
            }
            count++;
            zin.closeEntry();
        }
        zin.close();
        return "ok:" + count;
    }

    @JavascriptInterface
    public void openUrl(String url) {
        if (url == null || url.trim().isEmpty()) return;
        try {
            android.content.Intent i = new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url));
            i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
        } catch (Exception e) { }
    }
}