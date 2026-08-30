# Fast local AI profile

Axtor's Android runtime uses the maintained `llama-android:0.1.1` AAR, which wraps llama.cpp for on-device GGUF inference.

## Production voice/chat prompt

Use this as the default low-latency system prompt:

> You are Axtor, a private offline Android assistant. Answer accurately and directly in one short sentence under 15 words unless detail is requested. Never claim internet access when operating locally. For device actions, state what you did briefly.

For normal chat where a little more detail is useful, raise the output limit rather than making the context window large.

## Speed profile

- Context window: 1024 tokens.
- CPU threads: 2–4, capped to avoid oversubscription on budget phones.
- Output: 16–192 tokens; chat defaults to 128 and voice to 96.
- Sampling: low temperature and smaller top-k for predictable short answers.
- The loaded GGUF model is kept resident during the app process and reused, avoiding a full model reload for every message.
- A single model instance is serialized because the free `llama-android` API documents `LlamaModel` as not thread-safe.
- The current free AAR exposes full completion, not token streaming; therefore Axtor queues completed sentences to Android TTS instead of pretending to stream unavailable tokens.
- GPU/Vulkan offload and Flow token streaming are not enabled by the free 0.1.1 API. If a compatible streaming/GPU runtime is adopted later, it should be integrated behind `LlamaRuntime` rather than changing the UI.

## Model choice

IQ4_NL can be a good size/speed tradeoff when the selected model and runtime support it, but quantization does not by itself guarantee faster model loading or inference. For budget phones, a smaller model is usually the most important speed lever.

## Low-bandwidth web snippets

If web retrieval is added later, keep the fetched text small before sending it to the local model:

```kotlin
data class WebSnippet(val title: String, val url: String, val text: String)

fun compactSnippet(title: String, url: String, raw: String): WebSnippet {
    val text = raw.replace(Regex("\\s+"), " ").trim().take(1200)
    return WebSnippet(title, url, text)
}

fun buildContext(snippets: List<WebSnippet>): String =
    snippets.take(3).joinToString("\\n") {
        "SOURCE: ${it.title}\\n${it.text}"
    }
```

Keep retrieved context bounded; do not inject an entire webpage into a 1024-token local context.

## Important limitation

The Android implementation is CPU/NEON based through the free AAR. It does not use Flash Attention or Vulkan GPU offload because those are not exposed by the current free 0.1.1 API.
