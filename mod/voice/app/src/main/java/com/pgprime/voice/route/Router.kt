package com.pgprime.voice.route

import java.text.Normalizer

/** An installed app PG Voice can open, with the names it answers to. */
data class AppEntry(val packageName: String, val label: String, val names: List<String>)

/** What PG Voice does with what it heard. */
sealed interface Action {
    data class OpenApp(val app: AppEntry) : Action
    /** SmartTube's search ("" = just open SmartTube). */
    data class YouTube(val query: String) : Action
    /** PG Prime: Search, or Scout's Ask for a description ("a movie about magic"). */
    data class Prime(val query: String, val ask: Boolean) : Action
    data class Media(val key: MediaKey) : Action
    data object Home : Action
    /** "open X" where no installed app is called X. */
    data class NoApp(val name: String) : Action
}

enum class MediaKey { PLAY, PAUSE, NEXT, PREVIOUS, VOLUME_UP, VOLUME_DOWN, MUTE }

/**
 * Plain rules, French and English, no AI: instant and private. Anything that isn't a command, an app or
 * YouTube goes to PG Prime, where Scout's AI handles descriptions.
 */
object Router {
    private val POLITE = Regex("""^(?:hey |ok |okay |dis |please |s'il (?:te|vous) pla[iî]t |stp )+|(?: please| s'il (?:te|vous) pla[iî]t| stp| merci)+$""")

    private val COMMANDS: List<Pair<Regex, Action>> = listOf(
        Regex("""(?:pause|mets? (?:en )?pause|stop|arr[eê]te)""") to Action.Media(MediaKey.PAUSE),
        Regex("""(?:play|resume|continue|lecture|reprends?|reprendre|relance)""") to Action.Media(MediaKey.PLAY),
        Regex("""(?:next|next episode|skip|suivant|[eé]pisode suivant)""") to Action.Media(MediaKey.NEXT),
        Regex("""(?:previous|pr[eé]c[eé]dent|[eé]pisode pr[eé]c[eé]dent)""") to Action.Media(MediaKey.PREVIOUS),
        Regex("""(?:volume up|louder|turn it up|monte(?:r)? le (?:son|volume)|plus fort|augmente(?:r)? le (?:son|volume))""") to Action.Media(MediaKey.VOLUME_UP),
        Regex("""(?:volume down|quieter|turn it down|baisse(?:r)? le (?:son|volume)|moins fort|diminue(?:r)? le (?:son|volume))""") to Action.Media(MediaKey.VOLUME_DOWN),
        Regex("""(?:mute|unmute|coupe(?:r)? le son|remets? le son|silence)""") to Action.Media(MediaKey.MUTE),
        Regex("""(?:home|go home|home screen|accueil|[eé]cran d'accueil|retour (?:à|a) l'accueil)""") to Action.Home,
    )

    private val OPEN = Regex("""^(?:open|launch|start|go to|ouvre|ouvrir|lance|lancer|d[eé]marre|d[eé]marrer|va sur|aller sur)\s+(.+)$""")
    private val ASK = Regex("""^(?:ask scout|scout|demande (?:à|a) scout|demander (?:à|a) scout)\b[\s,:]*""")
    private val SEARCH_LEAD = Regex(
        """^(?:search(?: for)?|look (?:up|for)|find(?: me)?|show me|play|watch|i want to watch|""" +
            """cherche(?:r)?(?: moi)?|serge|cerge|recherche(?:r)?|trouve(?:r)?(?: moi)?|montre(?: moi)?|mets|lance|regarder|je veux regarder)\b[\s,:]*"""
    )
    private val YOUTUBE_INSIDE = Regex(
        """^(?:(?:une?|des|la|les|a|an|some|the)\s+)?(?:vid[eé]os?\s+)?(?:you ?tube|smart ?tube)\s+(?:vid[eé]os?\s+)?""" +
            """(?:(?:sur|de|du|des|d'|about|of|on|with|avec|for|pour)\s*)?(.+)$"""
    )
    private val VIDEOS = Regex("""^(?:(?:une?|des|les|la|some|the)\s+)?vid[eé]os?\s+(?:de|du|des|d'|sur|about|of|on|with|avec)\s*.+$|^.+\s+vid[eé]os$""")
    private val YOUTUBE_LAST = Regex("""^(.+?)\s+(?:(?:on|in|from|sur|dans)\s+)?(?:you ?tube|smart ?tube)$""")
    /** "… on YouTube", "… sur Arte": the place is the last words. */
    private val PLACE = Regex("""^(.*?)\s+(?:on|in|from|sur|dans)\s+(.+)$""")
    private val DESCRIBES = Regex(
        """^(?:a|an|some|something|any|another|movies?|films?|shows?|series|""" +
            """un|une|des|quelque chose|un truc|s[eé]ries?)\b"""
    )
    /** Asks for a kind of thing anywhere in the phrase: "magic wizardry movies", "un film sur la magie". */
    private val KIND = Regex(
        """\b(?:movies|films|shows|series|s[eé]ries|documentaries|documentaires|cartoons|dessins anim[eé]s|""" +
            """(?:movie|film|show|s[eé]rie) (?:about|with|where|like|for|set|de|sur|avec|o[uù]|comme|pour))\b"""
    )

    fun route(spoken: String, apps: List<AppEntry>, primeName: List<String> = PRIME_NAMES): Action? {
        val said = spoken.lowercase().replace(Regex("""[.,;!?¿¡"“”«»]"""), " ").replace(Regex("""\s+"""), " ").trim()
            .let { POLITE.replace(it, "").trim(' ', ',') }
        if (said.isEmpty()) return null

        COMMANDS.firstOrNull { it.first.matches(said) }?.let { return it.second }

        OPEN.matchEntire(said)?.let { m ->
            val name = m.groupValues[1]
            if (isYouTube(name)) return Action.YouTube("")
            findApp(name, apps)?.let { return Action.OpenApp(it) }
            return Action.NoApp(name)
        }
        if (isYouTube(said)) return Action.YouTube("")
        findApp(said, apps, exactOnly = true)?.let { return Action.OpenApp(it) }

        ASK.find(said)?.let {
            val question = said.substring(it.range.last + 1).trim()
            return if (question.isEmpty()) null else Action.Prime(question, ask = true)
        }

        // "youtube cat videos"
        Regex("""^(?:you ?tube|smart ?tube)\s+(.+)$""").matchEntire(said)?.let {
            return Action.YouTube(clean(it.groupValues[1]))
        }
        // "une vidéo YouTube sur Sandrine Rousseau", "cherche des vidéos YouTube de chats", "a YouTube video about bread"
        YOUTUBE_INSIDE.matchEntire(clean(said))?.let { return Action.YouTube(youTubeWords(it.groupValues[1])) }
        // "vidéo de Sandrine Rousseau YouTube": the "sur" before YouTube is often lost
        YOUTUBE_LAST.matchEntire(said)?.let { return Action.YouTube(youTubeWords(it.groupValues[1])) }
        // "vidéos de Sandrine Rousseau", "cat videos": videos are YouTube's, PG Prime has films and series
        if (VIDEOS.matches(clean(said))) return Action.YouTube(youTubeWords(said))
        PLACE.matchEntire(said)?.let { m ->
            val what = clean(m.groupValues[1])
            val place = m.groupValues[2]
            when {
                isYouTube(place) -> return Action.YouTube(youTubeWords(what))
                primeName.any { normalize(place) == it } -> return prime(what)
                else -> findApp(place, apps, exactOnly = true)?.let { return Action.OpenApp(it) }
            }
        }
        return prime(clean(said))
    }

    private fun prime(query: String): Action? =
        if (query.isEmpty()) null else Action.Prime(query, ask = DESCRIBES.containsMatchIn(query) || KIND.containsMatchIn(query))

    /** YouTube's search wants the subject: "vidéos de chats" -> "chats". */
    fun youTubeWords(text: String): String =
        clean(text).replace(Regex("""^(?:(?:une?|des|la|les|a|an|some|the)\s+)?vid[eé]os?\s+(?:(?:de|du|des|sur|about|of|on|with|avec|d')\s*)?"""), "")
            .trim().ifEmpty { clean(text) }

    private fun clean(text: String): String = SEARCH_LEAD.replace(text.trim(), "").trim(' ', ',', ':')

    private fun isYouTube(text: String): Boolean = normalize(text) in YOUTUBE_NAMES

    /** Best app for a spoken name: same name, else (unless [exactOnly]) a name that starts the same or is one letter off. */
    fun findApp(spoken: String, apps: List<AppEntry>, exactOnly: Boolean = false): AppEntry? {
        val n = APP_WORDS.replace(normalize(spoken), "").trim()
        if (n.isEmpty()) return null
        apps.firstOrNull { app -> app.names.any { it == n } }?.let { return it }
        if (exactOnly) return null
        apps.firstOrNull { app -> app.names.any { it.length >= 4 && (it.startsWith(n) || n.startsWith(it)) } }?.let { return it }
        return apps.firstOrNull { app -> app.names.any { it.length >= 5 && distance(it, n) <= 1 } }
    }

    /** Lower case, no accents, no punctuation, single spaces. */
    fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD).replace(Regex("""\p{M}"""), "")
            .replace(Regex("""[^a-z0-9' ]"""), " ").replace(Regex("""\s+"""), " ").trim()

    private fun distance(a: String, b: String): Int {
        val d = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = d[0]
            d[0] = i
            for (j in 1..b.length) {
                val t = d[j]
                d[j] = minOf(d[j] + 1, d[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = t
            }
        }
        return d[b.length]
    }

    private val APP_WORDS = Regex("""^(?:l'|the |my |app |appli |application |l'appli |l'application |la |le )+""")
    private val YOUTUBE_NAMES = setOf("youtube", "you tube", "smarttube", "smart tube")
    /** PG Prime only. "Nuvio" is the official Nuvio app, a different app (her call, 2026-11-22). */
    val PRIME_NAMES = listOf("pg prime", "pgprime", "prime", "pg", "pj prime", "pg prim", "peggy prime")
    /** The official Nuvio app, with how Google has heard it from her ("Renault vieux", "novio"). */
    val NUVIO_NAMES = listOf("nuvio", "nouvio", "novio", "nuvia", "nuevo", "nubio", "ne vieux", "nu vieux", "renault vieux")
}
