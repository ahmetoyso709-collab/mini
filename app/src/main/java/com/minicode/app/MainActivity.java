package com.minicode.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.provider.DocumentsContract;
import android.database.Cursor;
import android.content.ContentResolver;
import android.webkit.MimeTypeMap;
import androidx.documentfile.provider.DocumentFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;

public class MainActivity extends Activity {
    private static final int PICK_TREE = 42;
    private WebView web;
    private DocumentFile root;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().getDecorView().setSystemUiVisibility(0);
        web = new WebView(this);
        web.setBackgroundColor(0xFF1E1E1E);
        web.getSettings().setJavaScriptEnabled(true);
        web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setAllowFileAccess(true);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient());
        web.addJavascriptInterface(new Bridge(), "MiniNative");
        setContentView(web);
        web.loadUrl("file:///android_asset/index.html");
    }

    private void js(String script) { runOnUiThread(() -> web.evaluateJavascript(script, null)); }

    private class Bridge {
        @JavascriptInterface public void pickFolder() {
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
                startActivityForResult(i, PICK_TREE);
            });
        }
        @JavascriptInterface public String getRootName() { return root == null ? "" : root.getName(); }
        @JavascriptInterface public String listFiles(String parentUri) {
            try {
                DocumentFile dir = parentUri == null || parentUri.isEmpty() ? root : DocumentFile.fromTreeUri(MainActivity.this, Uri.parse(parentUri));
                if (dir == null || !dir.isDirectory()) return "[]";
                JSONArray a = new JSONArray();
                for (DocumentFile f : dir.listFiles()) {
                    JSONObject o = new JSONObject();
                    o.put("name", f.getName() == null ? "Dosya" : f.getName());
                    o.put("uri", f.getUri().toString());
                    o.put("directory", f.isDirectory());
                    o.put("size", f.length());
                    a.put(o);
                }
                return a.toString();
            } catch (Exception e) { return "[]"; }
        }
        @JavascriptInterface public String readFile(String uri) {
            try (InputStream in = getContentResolver().openInputStream(Uri.parse(uri)); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                if (in == null) return "";
                byte[] b = new byte[8192]; int n;
                while ((n = in.read(b)) > 0) out.write(b, 0, n);
                return out.toString("UTF-8");
            } catch (Exception e) { return ""; }
        }
        @JavascriptInterface public boolean writeFile(String uri, String content) {
            try (OutputStream out = getContentResolver().openOutputStream(Uri.parse(uri), "wt")) {
                if (out == null) return false;
                out.write(content.getBytes("UTF-8")); out.flush(); return true;
            } catch (Exception e) { return false; }
        }
        @JavascriptInterface public String createFile(String parentUri, String name) {
            try {
                DocumentFile dir = parentUri == null || parentUri.isEmpty() ? root : DocumentFile.fromTreeUri(MainActivity.this, Uri.parse(parentUri));
                if (dir == null) return "";
                String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1).toLowerCase() : "";
                String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
                DocumentFile f = dir.createFile(mime == null ? "text/plain" : mime, name);
                return f == null ? "" : f.getUri().toString();
            } catch (Exception e) { return ""; }
        }
        @JavascriptInterface public String createFolder(String parentUri, String name) {
            try {
                DocumentFile dir = parentUri == null || parentUri.isEmpty() ? root : DocumentFile.fromTreeUri(MainActivity.this, Uri.parse(parentUri));
                if (dir == null) return "";
                DocumentFile f = dir.createDirectory(name);
                return f == null ? "" : f.getUri().toString();
            } catch (Exception e) { return ""; }
        }
        @JavascriptInterface public boolean deleteFile(String uri) {
            try { DocumentFile f = DocumentFile.fromTreeUri(MainActivity.this, Uri.parse(uri)); return f != null && f.delete(); }
            catch (Exception e) { return false; }
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_TREE && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();
            try { getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION); } catch (Exception ignored) {}
            root = DocumentFile.fromTreeUri(this, uri);
            String name = root == null ? "Çalışma alanı" : root.getName();
            try { name = JSONObject.quote(name); } catch (Exception ignored) {}
            js("window.onNativeFolderSelected && window.onNativeFolderSelected(" + name + "," + JSONObject.quote(uri.toString()) + ")");
        }
    }
}
