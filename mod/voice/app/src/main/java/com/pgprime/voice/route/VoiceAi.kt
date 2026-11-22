package com.pgprime.voice.route

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.pgprime.voice.speech.TAG
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * AI search: Gemini (her own key, typed on PG Voice's page) fixes what the speech model misheard and picks where
 * a request goes: PG Prime search, Scout, or a YouTube search. Only the transcribed words of searches are sent
 * (never the voice, never commands like "pause" or "ouvre Arte"). Same model and endpoint as Scout in PG Prime (core/scout/ask/AskModels.kt). No key, no network
 * or too slow: the words as heard are used.
 */
object VoiceAi {
    private const val PREFS = "pgvoice"
    private const val KEY = "gemini_key"
    private const val URL_BASE = "https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-lite-latest:generateContent"
    private const val TIMEOUT_MS = 4_000

    /** Tested on her real phrases (2026-11-22): "Video de Sandraine Rouge, YouTube" -> youtube "Sandrine Rousseau". */
    private val SYSTEM = """
        You route voice requests spoken to a TV box, in French or English (often mixed).
        The text comes from an imperfect speech recognizer: fix misheard words and names when the intended ones are clear.
        "PG Prime" is the movie app that searches and Scout go to. "Nuvio" is a different app (open it with target "app").
        Known misrecognitions: "Nuvio" is often heard as "Renault vieux", "ne vieux", "novio";
        "cherche" is often heard as "Serge"; "movies" can be glued to the previous word ("Removies").
        Choose the target:
        - "youtube": the person mentions YouTube (even misheard) or wants online videos, clips, music, tutorials, recipes.
        - "scout": the person describes what they want instead of naming it: a genre, theme, mood or subject followed or preceded
          by movies/films/series/shows ("magic movies", "wizard movies", "films de magie", "movies about…", "something funny").
          Unless it is clearly the exact name of one existing title, words + "movies"/"films"/"séries" mean scout.
        - "search": the person names a precise movie, series or person to find in the movie app. One or two words that
          could be a title or a name ("Dune", "d'une", "June") are always search.
        - "app": the person wants to open one of the installed apps listed in the message (even misheard, e.g. "pour ton VPN"
          = Proton VPN, "Renault vieux" = Nuvio); "query" is then that app's name exactly as listed.
        "query": for youtube, the best YouTube search, keeping the words that describe the content (drop YouTube/cherche/search);
        for search, the corrected title or name; for scout, the request rephrased as a short clear question in the person's
        language, without the app name.
    """.trimIndent()

    private val SCHEMA = JSONObject("""{"type":"object","properties":{"target":{"type":"string","enum":["search","scout","youtube","app"]},"query":{"type":"string"}},"required":["target","query"]}""")

    fun key(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)?.takeIf { it.isNotBlank() }

    fun saveKey(context: Context, key: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            if (key.isNullOrBlank()) remove(KEY) else putString(KEY, key.trim())
        }.apply()
    }

    /**
     * Where [heard] (the whole phrase) should go. [rules] is what the plain rules chose: used when there is no key,
     * no network or Gemini fails, and it wins on things the rules are sure of (the word YouTube, a description).
     * Blocks: call off the main thread.
     */
    fun route(context: Context, heard: String, rules: Action, apps: List<AppEntry>): Action {
        val key = key(context) ?: return rules
        val started = SystemClock.elapsedRealtime()
        val answer = runCatching { JSONObject(ask(key, heard, apps) ?: return@runCatching null) }
            .onFailure { Log.d(TAG, "ai failed: ${it.javaClass.simpleName}") }
            .getOrNull()
        val target = answer?.optString("target").orEmpty()
        val query = answer?.optString("query").orEmpty().trim().take(160)
        Log.d(TAG, "ai: $target \"$query\" in ${SystemClock.elapsedRealtime() - started} ms")
        if (query.isEmpty()) return rules
        return when {
            rules is Action.YouTube -> rules
            // Gemini may send it to YouTube, but YouTube gets her words as heard (no AI search there: her call)
            target == "youtube" -> Action.YouTube(Router.youTubeWords(heard.lowercase().replace(Regex("[.,;!?]"), " ").trim()))
            // A misheard app name ("Renault vieux" -> Nuvio): open it. Not an app it knows: keep what the rules chose.
            // Her nicknames first: "Nuvio" is the official Nuvio app, "Nuvio Arc" and "PG Prime" are others.
            target == "app" -> (Router.findApp(query, apps, exactOnly = true)
                ?: apps.firstOrNull { it.label.equals(query, ignoreCase = true) }
                ?: Router.findApp(query, apps))?.let { Action.OpenApp(it) } ?: rules
            // Scout for a description; a word or two is a title (box log 2026-11-22: "d'une" went to Scout)
            target == "scout" && (query.split(' ').size >= 3 || (rules as? Action.Prime)?.ask == true) || (rules as? Action.Prime)?.ask == true -> Action.Prime(query, ask = true)
            target == "search" || target == "scout" -> Action.Prime(query, ask = false)
            else -> rules
        }
    }

    private fun ask(key: String, heard: String, apps: List<AppEntry>): String? {
        val message = "Request: $heard\nInstalled apps: " + apps.joinToString(", ") { it.label }
        val body = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", SYSTEM))))
            .put("contents", JSONArray().put(JSONObject().put("role", "user")
                .put("parts", JSONArray().put(JSONObject().put("text", message)))))
            .put("generationConfig", JSONObject().put("maxOutputTokens", 100).put("temperature", 0)
                .put("responseMimeType", "application/json").put("responseJsonSchema", SCHEMA))
        val connection = (URL(URL_BASE).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("x-goog-api-key", key)
        }
        try {
            connection.outputStream.use { it.write(body.toString().toByteArray()) }
            if (connection.responseCode !in 200..299) {
                Log.d(TAG, "youtube ai: http ${connection.responseCode}")
                return null
            }
            val reply = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val parts = reply.optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
                ?: return null
            return (0 until parts.length()).joinToString("") { parts.optJSONObject(it)?.optString("text").orEmpty() }
        } finally {
            connection.disconnect()
        }
    }
}
