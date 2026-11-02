package com.movieshelf.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DefaultWindowExceptionHandlerFactory
import androidx.compose.ui.window.LocalWindowExceptionHandlerFactory
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowExceptionHandler
import androidx.compose.ui.window.WindowExceptionHandlerFactory
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import coil3.ImageLoader
import com.movieshelf.app.resources.Res
import com.movieshelf.app.resources.app_logo
import org.jetbrains.compose.resources.painterResource
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.crossfade
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Path.Companion.toOkioPath
import java.awt.Desktop
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.util.Calendar
import java.util.concurrent.TimeUnit
import java.util.prefs.Preferences

/** Appends crashes to %LOCALAPPDATA%\MovieShelf\crash.log so they can be diagnosed after the window closes. */
private fun logCrash(e: Throwable) {
    try {
        File(DesktopServices.dataDir, "crash.log").appendText("---- ${java.time.LocalDateTime.now()}\n${e.stackTraceToString()}\n")
    } catch (_: Exception) { }
}

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    Thread.setDefaultUncaughtExceptionHandler { _, e -> logCrash(e); e.printStackTrace() }
    SingletonImageLoader.setSafe { context ->
        ImageLoader.Builder(context)
            .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.2).build() }
            .diskCache { DiskCache.Builder().directory(DesktopServices.dataDir.resolve("image-cache").toOkioPath()).maxSizeBytes(300L * 1024 * 1024).build() }
            .crossfade(true)
            .build()
    }
    application {
        val windowState = rememberWindowState(size = DpSize(1440.dp, 900.dp), position = WindowPosition(Alignment.Center))
        CompositionLocalProvider(
            LocalWindowExceptionHandlerFactory provides WindowExceptionHandlerFactory { window ->
                val fallback = DefaultWindowExceptionHandlerFactory.exceptionHandler(window)
                WindowExceptionHandler { e -> logCrash(e); fallback.onException(e) }
            }
        ) {
        Window(
            onCloseRequest = ::exitApplication,
            title = "Padampetti",
            icon = painterResource(Res.drawable.app_logo),
            state = windowState,
            onPreviewKeyEvent = { ev ->
                if (ev.type == KeyEventType.KeyDown && ev.key == Key.F11) {
                    windowState.placement = if (windowState.placement == WindowPlacement.Fullscreen) WindowPlacement.Floating else WindowPlacement.Fullscreen
                    true
                } else false
            }
        ) {
            LaunchedEffect(Unit) { window.minimumSize = java.awt.Dimension(960, 640) }
            MovieShelfApp(DesktopServices)
        }
        }
    }
}

object DesktopServices : PlatformServices {
    override val isTv: Boolean = false
    private val prefs = Preferences.userRoot().node("MovieShelf")

    val dataDir: File by lazy {
        val base = System.getenv("LOCALAPPDATA")?.let(::File) ?: File(System.getProperty("user.home"), ".cache")
        File(base, "MovieShelf").apply { mkdirs() }
    }

    private val nuvioProtocol: Boolean by lazy {
        if (!System.getProperty("os.name").startsWith("Windows")) return@lazy false
        try {
            val process = ProcessBuilder("reg", "query", "HKCR\\nuvio", "/v", "URL Protocol").redirectErrorStream(true).start()
            process.inputStream.readBytes()
            process.waitFor(3, TimeUnit.SECONDS) && process.exitValue() == 0
        } catch (_: Exception) { false }
    }

    /** Nuvio for Windows registers no nuvio:// handler, but Nuvio.exe reads a nuvio:// link from its launch arguments. */
    private val nuvioExe: File? by lazy {
        listOfNotNull(System.getenv("ProgramFiles"), System.getenv("LOCALAPPDATA")?.let { "$it\\Programs" })
            .map { File(it, "Nuvio\\Nuvio.exe") }.firstOrNull { it.exists() }
    }

    override val nuvioAvailable: Boolean get() = nuvioProtocol || nuvioExe != null

    override fun today(): Today = Calendar.getInstance().let { Today(it.get(Calendar.YEAR), it.get(Calendar.MONTH) + 1, it.get(Calendar.DAY_OF_MONTH)) }
    override fun readSetting(key: String): String = prefs.get(key, "")
    override fun saveSetting(key: String, value: String) { prefs.put(key, value); prefs.flush() }
    override fun readFile(name: String): String = File(dataDir, name).takeIf { it.exists() }?.readText() ?: ""
    override fun writeFile(name: String, text: String) {
        val target = File(dataDir, name)
        File(dataDir, "$name.tmp").apply { writeText(text) }.renameTo(target.also { it.delete() })
    }
    override fun encodeQuery(value: String): String = URLEncoder.encode(value, "UTF-8")

    override suspend fun getJson(url: String, bearerToken: String): String = httpJson(url, bearerToken)
    override suspend fun postJson(url: String, body: String): String = httpJson(url, postBody = body)

    override fun openWeb(url: String): Boolean = try {
        if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            Desktop.getDesktop().browse(URI(url)); true
        } else if (System.getProperty("os.name").startsWith("Windows")) {
            ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url).start(); true
        } else false
    } catch (_: Exception) { false }

    override fun openProvider(providerName: String, url: String): Boolean = openWeb(url)

    /**
     * Trailer with sound in a chromeless Microsoft Edge window (built into Windows 11; nothing to download).
     * Uses the normal watch page: YouTube refuses its bare embed player outside a site ("configuration error").
     */
    override fun openTrailer(youtubeKey: String): Boolean {
        val url = "https://www.youtube.com/watch?v=$youtubeKey&autoplay=1"
        val edge = listOfNotNull(System.getenv("ProgramFiles(x86)"), System.getenv("ProgramFiles"))
            .map { File(it, "Microsoft\\Edge\\Application\\msedge.exe") }.firstOrNull { it.exists() }
        if (edge != null) {
            try {
                ProcessBuilder(edge.absolutePath, "--app=$url", "--window-size=1280,760").start()
                return true
            } catch (_: Exception) { }
        }
        return openWeb(url)
    }

    private val imdb by lazy { ImdbDataset(dataDir) }
    override suspend fun imdbRatings(imdbIds: Collection<String>) = imdb.lookup(imdbIds)

    override fun openNuvio(movieId: Int): Boolean {
        val link = "nuvio://tmdb/movie/$movieId"
        if (nuvioProtocol) return openWeb(link)
        val exe = nuvioExe ?: return false
        return try { ProcessBuilder(exe.absolutePath, link).start(); true } catch (_: Exception) { false }
    }
}
