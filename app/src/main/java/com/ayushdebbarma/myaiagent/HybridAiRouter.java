package com.ayushdebbarma.myaiagent;

import android.content.Context;

/** Local-only model router. Axtor never silently falls back to an online AI endpoint. */
public final class HybridAiRouter {
    private HybridAiRouter() {}
    public interface Callback { void onSuccess(String text, double tps); void onError(String message); }

    public static void generate(Context context, String model, String prompt, String system,
                                int maxTokens, Callback cb) {
        if (model == null || model.isEmpty() || !AppCore.hasUsableActiveModel(context)) {
            cb.onError("No usable local GGUF model is loaded");
            return;
        }
        LlamaRuntime.generate(context, model, prompt, system, maxTokens,
                new LlamaRuntime.Callback() {
                    public void onSuccess(String text, double tps) { cb.onSuccess(text, tps); }
                    public void onError(String message) { cb.onError(message); }
                });
    }

    /** Retained as no-op compatibility methods; online AI is intentionally disabled. */
    public static void setEndpoint(Context c, String v) {}
    public static void setApiKey(Context c, String v) {}
    public static void setOnlineModel(Context c, String v) {}
    public static void setOnlineEnabled(Context c, boolean v) {}
    public static boolean onlineEnabled(Context c) { return false; }
    public static boolean isConfigured(Context c) { return false; }
}
