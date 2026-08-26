package com.example.htmlppt;

import android.os.Environment;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

/**
 * 管理 /sdcard/HTML_PPT 下的 settings.json、PPT、themes 目录及文件读写
 */
public class FileManager {
    public static final File BASE = new File(Environment.getExternalStorageDirectory(), "HTML_PPT");
    public static final File PPT_DIR = new File(BASE, "PPT");
    public static final File THEMES_DIR = new File(BASE, "themes");
    public static final File WWW_DIR = new File(BASE, "www");
    public static final File SETTINGS_FILE = new File(BASE, "settings.json");

    public static void ensureDirs() {
        if (!BASE.exists()) BASE.mkdirs();
        if (!PPT_DIR.exists()) PPT_DIR.mkdirs();
        if (!THEMES_DIR.exists()) THEMES_DIR.mkdirs();
        if (!WWW_DIR.exists()) WWW_DIR.mkdirs();
    }

    public static String read(File f) {
        if (f == null || !f.exists()) return "";
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line).append("\n");
        } catch (IOException e) { }
        return sb.toString();
    }

    public static boolean write(File f, String content) {
        try {
            ensureDirs();
            if (f.getParentFile() != null && !f.getParentFile().exists()) f.getParentFile().mkdirs();
            try (OutputStreamWriter ow = new OutputStreamWriter(new FileOutputStream(f), "UTF-8")) {
                ow.write(content);
                return true;
            }
        } catch (IOException e) { return false; }
    }

    public static boolean delete(File f) {
        return f != null && f.exists() && f.delete();
    }

    /* ------------ PPT ------------ */
    public static List<String> listPpt() {
        List<String> list = new ArrayList<>();
        if (PPT_DIR.isDirectory()) {
            File[] files = PPT_DIR.listFiles();
            if (files != null) {
                Arrays.sort(files, Comparator.comparing(File::getName));
                for (File f : files) {
                    if (f.isFile() && f.getName().toLowerCase().endsWith(".txt")) list.add(f.getName());
                }
            }
        }
        return list;
    }

    public static String listPptDetail() {
        JSONArray arr = new JSONArray();
        if (PPT_DIR.isDirectory()) {
            File[] files = PPT_DIR.listFiles();
            if (files != null) {
                Arrays.sort(files, Comparator.comparing(File::getName));
                for (File f : files) {
                    if (f.isFile() && f.getName().toLowerCase().endsWith(".txt")) {
                        JSONObject o = new JSONObject();
                        long mod = f.lastModified();
                        long created = mod;
                        try {
                            java.nio.file.attribute.BasicFileAttributes a = java.nio.file.Files.readAttributes(
                                    f.toPath(), java.nio.file.attribute.BasicFileAttributes.class);
                            created = a.creationTime().toMillis();
                            mod = a.lastModifiedTime().toMillis();
                        } catch (Exception ignored) { }
                        try {
                            o.put("name", f.getName());
                            o.put("created", created);
                            o.put("modified", mod);
                        } catch (Exception ignored) { }
                        arr.put(o);
                    }
                }
            }
        }
        return arr.toString();
    }

    public static File pptFile(String name) {
        if (name == null || name.trim().isEmpty()) return null;
        ensureDirs();
        return new File(PPT_DIR, name);
    }

    public static boolean renamePpt(String oldName, String newName) {
        File o = pptFile(oldName);
        File n = pptFile(newName);
        if (o == null || n == null || !o.exists()) return false;
        if (n.exists()) return false;
        return o.renameTo(n);
    }

    /* ------------ Settings ------------ */
    public static String defaultSettings() {
        JSONObject o = new JSONObject();
        try { o.put("theme", "aurora"); o.put("customThemes", new JSONObject()); } catch (Exception e) { }
        return o.toString();
    }

    public static String readSettings() {
        if (!SETTINGS_FILE.exists()) return defaultSettings();
        String s = read(SETTINGS_FILE);
        if (s.trim().isEmpty()) return defaultSettings();
        return s;
    }

    public static boolean writeSettings(String json) {
        try { json = new JSONObject(json).toString(2); } catch (Exception e) { }
        ensureDirs();
        return write(SETTINGS_FILE, json);
    }

    /* ------------ Themes ------------ */
    public static List<String> listThemes() {
        List<String> list = new ArrayList<>();
        if (THEMES_DIR.isDirectory()) {
            File[] fs = THEMES_DIR.listFiles();
            if (fs != null) {
                Arrays.sort(fs, Comparator.comparing(File::getName));
                for (File f : fs) {
                    String n = f.getName().toLowerCase();
                    if (f.isFile() && (n.endsWith(".json") || n.endsWith(".css"))) list.add(f.getName());
                }
            }
        }
        return list;
    }

    public static File themeFile(String name) {
        ensureDirs();
        return new File(THEMES_DIR, name);
    }
}