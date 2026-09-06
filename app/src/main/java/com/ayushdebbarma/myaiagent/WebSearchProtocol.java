package com.ayushdebbarma.myaiagent;

import android.app.SearchManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import java.util.Locale;

/** Deterministic web-search routing for voice commands. */
public final class WebSearchProtocol {
    private WebSearchProtocol() {}

    public static String extractQuery(String command) {
        if (command == null) return null;
        String q = command.trim();
        if (q.isEmpty()) return null;
        String lower = q.toLowerCase(Locale.ROOT);
        String[] prefixes = {
            "search the web for ", "search web for ", "web search for ",
            "search the internet for ", "search internet for ",
            "look up ", "find online ", "google "
        };
        for (String prefix : prefixes) {
            if (lower.startsWith(prefix)) {
                String query = q.substring(prefix.length()).trim();
                return query.isEmpty() ? "" : query;
            }
        }
        if (lower.equals("search the web") || lower.equals("search web")
                || lower.equals("search the internet") || lower.equals("search internet")) {
            return "";
        }
        return null;
    }

    public static String search(Context context, String query) {
        if (query == null) return "Web search failed. Main cause: no search query was detected.";
        if (query.isEmpty()) return "Tell me what you want me to search for.";

        Intent web = new Intent(Intent.ACTION_WEB_SEARCH);
        web.putExtra(SearchManager.QUERY, query);
        web.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            if (web.resolveActivity(context.getPackageManager()) != null) {
                context.startActivity(web);
                return "Searching the web for " + query + ".";
            }
        } catch (ActivityNotFoundException ignored) {}

        try {
            Intent browser = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://www.google.com/search?q=" + Uri.encode(query)));
            browser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (browser.resolveActivity(context.getPackageManager()) != null) {
                context.startActivity(browser);
                return "The system search action is unavailable, so I opened a browser search for " + query + ".";
            }
        } catch (Throwable ignored) {}

        return "Web search failed. Main cause: no compatible search activity or browser is installed.";
    }

    public static String status(Context context) {
        Intent web = new Intent(Intent.ACTION_WEB_SEARCH);
        boolean system = web.resolveActivity(context.getPackageManager()) != null;
        Intent browser = new Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"));
        boolean browserAvailable = browser.resolveActivity(context.getPackageManager()) != null;
        if (system) return "system-search-ready";
        if (browserAvailable) return "browser-fallback-ready";
        return "unavailable";
    }
}
