package com.ayushdebbarma.myaiagent;

import android.content.Context;
import android.util.Base64;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import org.json.JSONObject;

/**
 * Remote autonomous repair bridge. Axtor sends only bounded source proposals to a
 * dedicated GitHub Actions workflow. The workflow builds/tests before changing main.
 * The GitHub token is encrypted with Android Keystore and is never logged.
 */
public final class GitHubRepairClient {
    private static final String PREF = "axtor_github_repair";
    private static final String KEY_TOKEN = "token";
    private static final String KEY_REPO = "repo";
    private static final String KEY_WORKFLOW = "workflow";
    private static final String KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "AxtorGitHubRepair";
    private static final String API = "https://api.github.com";
    private GitHubRepairClient() {}

    public static boolean configure(Context context, String token, String repository, String workflow) {
        if (context == null || token == null || token.trim().isEmpty() || repository == null ||
                !repository.matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) return false;
        try {
            context.getSharedPreferences(PREF, 0).edit()
                    .putString(KEY_TOKEN, encrypt(token.trim()))
                    .putString(KEY_REPO, repository.trim())
                    .putString(KEY_WORKFLOW, workflow == null || workflow.trim().isEmpty() ? "axtor-autorepair.yml" : workflow.trim())
                    .apply();
            return true;
        } catch (Exception e) { return false; }
    }

    public static boolean isConfigured(Context context) {
        return token(context) != null && !repo(context).isEmpty();
    }

    public static JSONObject dispatch(Context context, String goal, String targetPath, String replacement) {
        JSONObject out = new JSONObject();
        if (!SelfCodingEngine.isSafeSourcePath(targetPath)) return error(out, "Unsafe target path");
        if (replacement == null || replacement.length() > 48000) return error(out, "Repair body exceeds remote workflow limit");
        String token = token(context);
        if (token == null) return error(out, "GitHub repair is not configured");
        try {
            String repo = repo(context);
            String workflow = workflow(context);
            JSONObject inputs = new JSONObject();
            inputs.put("goal", goal == null ? "" : goal.trim());
            inputs.put("target_path", targetPath.trim());
            inputs.put("replacement", replacement);
            inputs.put("proposal_id", SelfCodingEngine.latest(context).optString("id", "unknown"));
            JSONObject body = new JSONObject().put("ref", "main").put("inputs", inputs);
            JSONObject response = request("POST", "/repos/" + repo + "/actions/workflows/" + URLEncoder.encode(workflow, "UTF-8") + "/dispatches", token, body.toString());
            out.put("status", "dispatched");
            out.put("repository", repo);
            out.put("workflow", workflow);
            if (response.has("workflow_run_id")) out.put("runId", response.optLong("workflow_run_id"));
            if (response.has("html_url")) out.put("url", response.optString("html_url"));
        } catch (Exception e) { error(out, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage()); }
        return out;
    }

    public static JSONObject run(Context context, long runId) {
        JSONObject out = new JSONObject();
        String token = token(context);
        if (token == null || runId <= 0) return error(out, "GitHub repair is not configured");
        try {
            JSONObject r = request("GET", "/repos/" + repo(context) + "/actions/runs/" + runId, token, null);
            out.put("status", r.optString("status", "unknown"));
            out.put("conclusion", r.optString("conclusion", ""));
            out.put("url", r.optString("html_url", ""));
            out.put("runId", runId);
        } catch (Exception e) { error(out, e.getMessage()); }
        return out;
    }

    private static JSONObject request(String method, String path, String token, String body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(API + path).openConnection();
        c.setRequestMethod(method);
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setRequestProperty("Accept", "application/vnd.github+json");
        c.setRequestProperty("Authorization", "Bearer " + token);
        c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
        c.setRequestProperty("User-Agent", "Axtor-Repair-Agent");
        if (body != null) {
            c.setDoOutput(true);
            c.setRequestProperty("Content-Type", "application/json");
            try (OutputStream o = c.getOutputStream()) { o.write(body.getBytes(StandardCharsets.UTF_8)); }
        }
        int code = c.getResponseCode();
        InputStream stream = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
        String text = "";
        if (stream != null) try (InputStream in = stream) {
            byte[] buffer = new byte[8192];
            StringBuilder b = new StringBuilder();
            int n;
            while ((n = in.read(buffer)) != -1) b.append(new String(buffer, 0, n, StandardCharsets.UTF_8));
            text = b.toString();
        }
        if (code < 200 || code >= 300) throw new IOException("GitHub API " + code + (text.isEmpty() ? "" : ": " + text));
        return text.isEmpty() ? new JSONObject() : new JSONObject(text);
    }

    private static JSONObject error(JSONObject o, String message) { try { o.put("status", "error").put("error", message == null ? "unknown error" : message); } catch (Exception ignored) {} return o; }
    private static String repo(Context c) { return c.getSharedPreferences(PREF, 0).getString(KEY_REPO, ""); }
    private static String workflow(Context c) { return c.getSharedPreferences(PREF, 0).getString(KEY_WORKFLOW, "axtor-autorepair.yml"); }
    private static String token(Context c) { try { String x = c.getSharedPreferences(PREF, 0).getString(KEY_TOKEN, null); return x == null ? null : decrypt(x); } catch (Exception e) { return null; } }

    private static SecretKey key() throws Exception {
        KeyStore ks = KeyStore.getInstance(KEYSTORE);
        ks.load(null);
        if (!ks.containsAlias(KEY_ALIAS)) {
            KeyGenerator g = KeyGenerator.getInstance("AES", KEYSTORE);
            g.init(256);
            g.generateKey();
        }
        return ((KeyStore.SecretKeyEntry) ks.getEntry(KEY_ALIAS, null)).getSecretKey();
    }

    private static String encrypt(String value) throws Exception {
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE, key());
        byte[] iv = c.getIV();
        byte[] ct = c.doFinal(value.getBytes(StandardCharsets.UTF_8));
        byte[] all = new byte[iv.length + ct.length];
        System.arraycopy(iv, 0, all, 0, iv.length);
        System.arraycopy(ct, 0, all, iv.length, ct.length);
        return Base64.encodeToString(all, Base64.NO_WRAP);
    }

    private static String decrypt(String value) throws Exception {
        byte[] all = Base64.decode(value, Base64.NO_WRAP);
        if (all.length < 13) throw new IllegalArgumentException("Invalid encrypted token");
        byte[] iv = new byte[12];
        byte[] ct = new byte[all.length - 12];
        System.arraycopy(all, 0, iv, 0, 12);
        System.arraycopy(all, 12, ct, 0, ct.length);
        Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE, key(), new javax.crypto.spec.GCMParameterSpec(128, iv));
        return new String(c.doFinal(ct), StandardCharsets.UTF_8);
    }
}
