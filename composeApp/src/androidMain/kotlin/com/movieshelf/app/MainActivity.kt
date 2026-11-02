package com.movieshelf.app

import android.app.UiModeManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import android.speech.RecognizerIntent
import androidx.compose.runtime.remember
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.request.crossfade
import okio.Path.Companion.toOkioPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Calendar

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val services = AndroidServices(applicationContext)
        // Voice search: Google's voice input (on a TV it's the Assistant's listener) hands back what was said.
        var heard: ((String) -> Unit)? = null
        val listen = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!text.isNullOrBlank()) heard?.invoke(text)
            heard = null
        }
        services.listen = { onText ->
            heard = onText
            try {
                listen.launch(
                    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH)
                        .putExtra(RecognizerIntent.EXTRA_PROMPT, "Say a movie title")
                )
                true
            } catch (_: ActivityNotFoundException) { heard = null; false }
        }
        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context)
                .diskCache { DiskCache.Builder().directory(context.cacheDir.resolve("image_cache").toOkioPath()).maxSizeBytes(250L * 1024 * 1024).build() }
                .crossfade(true)
                .build()
        }
        setContent {
            val bridge = remember { BackBridge() }
            BackHandler(enabled = bridge.enabled) { bridge.action() }
            MovieShelfApp(services, bridge)
        }
    }
}

private class AndroidServices(private val context: Context) : PlatformServices {
    override val isTv: Boolean =
        (context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager).currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
    private val prefs = context.getSharedPreferences("movie_shelf", Context.MODE_PRIVATE)

    /** Set by MainActivity, which owns the activity-result launcher. */
    var listen: ((String) -> Unit) -> Boolean = { false }

    override val voiceAvailable: Boolean by lazy {
        context.packageManager.queryIntentActivities(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0).isNotEmpty()
    }

    override fun voiceSearch(onText: (String) -> Unit): Boolean = listen(onText)

    override val nuvioAvailable: Boolean by lazy {
        context.packageManager.queryIntentActivities(Intent(Intent.ACTION_VIEW, Uri.parse("nuvio://tmdb/movie/1")), 0).isNotEmpty()
    }

    override fun today(): Today = Calendar.getInstance().let { Today(it.get(Calendar.YEAR), it.get(Calendar.MONTH) + 1, it.get(Calendar.DAY_OF_MONTH)) }
    override fun readSetting(key: String): String = prefs.getString(key, "") ?: ""
    override fun saveSetting(key: String, value: String) { prefs.edit().putString(key, value).apply() }
    override fun readFile(name: String): String = java.io.File(context.filesDir, name).takeIf { it.exists() }?.readText() ?: ""
    override fun writeFile(name: String, text: String) { java.io.File(context.filesDir, name).writeText(text) }
    override fun encodeQuery(value: String): String = URLEncoder.encode(value, "UTF-8")

    override suspend fun getJson(url: String, bearerToken: String): String = httpJson(url, bearerToken)
    override suspend fun postJson(url: String, body: String): String = httpJson(url, postBody = body)

    private fun start(intent: Intent): Boolean = try {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    } catch (_: ActivityNotFoundException) { false } catch (_: SecurityException) { false }

    override fun openWeb(url: String): Boolean = start(Intent(Intent.ACTION_VIEW, Uri.parse(url)))

    /**
     * TVs usually have no browser, so prefer the provider's installed app: first hand it the
     * link (Netflix/Prime/YouTube TV apps accept their own URLs), else just launch the app.
     */
    override fun openProvider(providerName: String, url: String): Boolean {
        netflixTitleId(url)?.let { id -> if (openNetflixTitle(id)) return true }
        val pm = context.packageManager
        val installed = providerPackages(providerName).filter { pkg ->
            try { pm.getPackageInfo(pkg, 0); true } catch (_: PackageManager.NameNotFoundException) { false }
        }
        for (pkg in installed) {
            if (start(Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(pkg))) return true
            val launch = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)
            if (launch != null && start(launch)) return true
        }
        return openWeb(url)
    }

    override val trailerPlayer: TrailerPlayer = AndroidTrailerPlayer
    override fun openTrailer(youtubeKey: String) = openProvider("YouTube", "https://www.youtube.com/watch?v=$youtubeKey")

    private val imdb by lazy { ImdbDataset(context.filesDir) }
    override suspend fun imdbRatings(imdbIds: Collection<String>) = imdb.lookup(imdbIds)

    /**
     * Netflix's TV app (com.netflix.ninja) ignores title links (it just restores its last screen), but it does
     * honour https://www.netflix.com/watch/<id> with extra source=30 and starts playing the movie. Verified on
     * the Lumio Google TV (Netflix 13.2.2), both when Netflix was already running and when it wasn't.
     */
    private fun openNetflixTitle(id: String): Boolean {
        val watch = Uri.parse("https://www.netflix.com/watch/$id")
        val tv = Intent(Intent.ACTION_VIEW, watch).setClassName("com.netflix.ninja", "com.netflix.ninja.MainActivity").putExtra("source", "30")
        if (start(tv)) return true
        // Phones/tablets: the regular Netflix app opens the title page from its web link.
        return start(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.netflix.com/title/$id")).setPackage("com.netflix.mediaclient"))
    }

    override fun openNuvio(movieId: Int): Boolean = start(Intent(Intent.ACTION_VIEW, Uri.parse("nuvio://tmdb/movie/$movieId")))
}

/** Android / Google TV package names for common Indian streaming services (also listed in the manifest <queries>). */
internal fun providerPackages(providerName: String): List<String> {
    val name = providerName.lowercase()
    return when {
        "netflix" in name -> listOf("com.netflix.ninja", "com.netflix.mediaclient")
        "prime video" in name || "amazon video" in name -> listOf("com.amazon.amazonvideo.livingroom", "com.amazon.avod.thirdpartyclient")
        "hotstar" in name -> listOf("in.startv.hotstar", "in.startv.hotstaronly")
        // TizenTube (a YouTube TV client) first, then the official apps.
        "youtube" in name -> listOf("io.gh.reisxd.tizentube.cobalt", "com.google.android.youtube.tv", "com.google.android.youtube")
        "zee5" in name -> listOf("com.graymatrix.did")
        "sony" in name -> listOf("com.sonyliv")
        "sun nxt" in name || "sunnxt" in name -> listOf("com.suntv.sunnxt")
        "manorama" in name -> listOf("com.mmtv.manoramamax.android")
        "apple tv" in name -> listOf("com.apple.atve.androidtv.appletv")
        "jiocinema" in name -> listOf("com.jio.media.ondemand")
        else -> emptyList()
    }
}

/** "https://www.netflix.com/title/81497215" (or /watch/) -> "81497215". */
internal fun netflixTitleId(url: String): String? =
    Regex("""netflix\.com/(?:[a-z]{2}(?:-[a-z]{2})?/)?(?:title|watch)/(\d+)""").find(url)?.groupValues?.get(1)
