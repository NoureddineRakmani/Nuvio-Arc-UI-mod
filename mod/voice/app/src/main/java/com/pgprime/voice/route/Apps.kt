package com.pgprime.voice.route

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/** The apps on the box that PG Voice can open, and which PG Prime takes requests. */
object Apps {
    /** Real PG Prime first, then the test build. Only one that says it accepts PG Voice requests is used. */
    private val PRIME_PACKAGES = listOf("com.nuvio.tv.test.mod", "com.nuvio.tv.test.dev")
    private const val PRIME_HANDOFF = "pgvoice.handoff"
    const val SMARTTUBE = "org.smarttube.stable"

    /** Extra names people use for some apps (normalised). */
    private val ALIASES = mapOf(
        SMARTTUBE to listOf("youtube", "you tube", "smarttube", "smart tube"),
        "com.nuvio.tv.test.mod" to Router.PRIME_NAMES,
        "com.nuvio.tv" to Router.NUVIO_NAMES,
        "com.nuvio.tv.test" to listOf("nuvio arc", "arc"),
        "fr.tf1.mytf1" to listOf("tf1", "my tf1", "mytf1", "tf un"),
        "tv.arte.plus7" to listOf("arte", "arte tv"),
        "com.orange.owtv.tv" to listOf("orange", "orange tv", "la tv d'orange", "tv d'orange"),
        "ch.protonvpn.android" to listOf("proton", "proton vpn", "vpn"),
        "com.spocky.projengmenu" to listOf("projectivy", "launcher"),
    )

    fun launchable(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val seen = LinkedHashMap<String, AppEntry>()
        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val query = Intent(Intent.ACTION_MAIN).addCategory(category)
            for (info in pm.queryIntentActivities(query, 0)) {
                val pkg = info.activityInfo.packageName
                if (pkg == context.packageName || pkg in seen) continue
                val label = info.loadLabel(pm).toString()
                val names = (listOf(Router.normalize(label)) + ALIASES[pkg].orEmpty()).distinct()
                seen[pkg] = AppEntry(pkg, label, names)
            }
        }
        // Apps with known nicknames first, so "Nuvio" is the official Nuvio app, not Nuvio Arc or PG Prime.
        return seen.values.sortedByDescending { it.packageName in ALIASES }
    }

    fun launchIntent(context: Context, pkg: String): Intent? {
        val pm = context.packageManager
        return (pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg))
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** The PG Prime that accepts searches from PG Voice, or null. */
    fun prime(context: Context): String? = PRIME_PACKAGES.firstOrNull { pkg ->
        runCatching {
            context.packageManager.getApplicationInfo(pkg, PackageManager.GET_META_DATA).metaData?.getBoolean(PRIME_HANDOFF) == true
        }.getOrDefault(false)
    }
}
