package com.movieshelf.app

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.ViewGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView

/**
 * Silent background trailer behind the movie page, using YouTube's official embedded player in a WebView.
 *
 * Two TV quirks found on a Realtek-based Google TV (Lumio Vision):
 *  - AV1 is decoded but never displayed (black with sound), so AV1 is hidden from YouTube and it streams VP9.
 *  - A WebView placed *inside* Compose (AndroidView) never shows its video. So the WebView is added to the
 *    window directly, behind the Compose page, which leaves the area over the trailer transparent.
 */
object AndroidTrailerPlayer : TrailerPlayer {
    @Composable
    override fun Player(
        youtubeKey: String, muted: Boolean, fill: Boolean, modifier: Modifier,
        onFailed: () -> Unit, onPlaying: () -> Unit,
    ) {
        val host = LocalView.current
        val failed = rememberUpdatedState(onFailed)
        val playing = rememberUpdatedState(onPlaying)
        val web = remember(youtubeKey) {
            trailerWebView(host.context, youtubeKey, muted, fill, blockedCodecs = listOf("av01"), onPlaying = { playing.value() }) { failed.value() }
        }
        DisposableEffect(web) {
            // The window's content frame, below the Compose content (Compose's own layouts refuse plain views).
            val frame = host.rootView.findViewById<ViewGroup>(android.R.id.content)
            frame.addView(web, 0, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            onDispose {
                frame.removeView(web)
                web.loadUrl("about:blank")
                web.destroy()
            }
        }
        LaunchedEffect(web, muted) {
            web.tag = muted
            web.evaluateJavascript(muteScript(muted), null)
        }
    }
}

/** The configured trailer WebView. */
@SuppressLint("SetJavaScriptEnabled")
internal fun trailerWebView(
    context: android.content.Context, youtubeKey: String, muted: Boolean, fill: Boolean,
    blockedCodecs: List<String> = emptyList(), onPlaying: () -> Unit = {}, onFailed: () -> Unit,
) =
    WebView(context).apply {
        if (blockedCodecs.isNotEmpty() && androidx.webkit.WebViewFeature.isFeatureSupported(androidx.webkit.WebViewFeature.DOCUMENT_START_SCRIPT)) {
            androidx.webkit.WebViewCompat.addDocumentStartJavaScript(this, codecBlockScript(blockedCodecs), setOf("*"))
        }
        // Purely visual: the remote keeps focus on MovieShelf's own buttons.
        isFocusable = false
        isFocusableInTouchMode = false
        setBackgroundColor(0xFF0E1116.toInt()) // MovieShelf's background, shown until the video starts
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.mediaPlaybackRequiresUserGesture = false
        webChromeClient = WebChromeClient()
        webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String?) {
                view.evaluateJavascript(styleScript(fill), null)
                view.evaluateJavascript(QUALITY_SCRIPT, null)
                view.evaluateJavascript(muteScript(tag as? Boolean ?: muted), null)
            }
        }
        tag = muted
        // YouTube requires embedded players in apps to identify themselves; without it the embed fails (error 152/153).
        loadUrl(embedUrl(youtubeKey), mapOf("Referer" to "https://com.movieshelf.app"))
        watchPlayback(this, 0, onPlaying, onFailed)
    }

/**
 * Every 2 s for ~12 s: once the video advances, report [onPlaying]; if YouTube shows an error (e.g. "Video
 * unavailable", embedding disabled) or it never starts, report failure so the page keeps its backdrop.
 */
private fun watchPlayback(view: WebView, round: Int, onPlaying: () -> Unit, onFailed: () -> Unit) {
    view.postDelayed({
        view.evaluateJavascript(
            "(function(){var v=document.querySelector('video');" +
                "if(document.querySelector('.ytp-error'))return 'error';" +
                "if(v&&v.error)return 'error';" +
                "return (v&&v.currentTime>0.3)?'playing':'waiting'})()"
        ) { state ->
            when {
                state.contains("error") -> onFailed()
                state.contains("playing") -> {
                    onPlaying()
                    // Re-assert HD once it plays (YouTube may have started low), and log what it actually uses.
                    view.evaluateJavascript(QUALITY_SCRIPT, null)
                    view.postDelayed({
                        view.evaluateJavascript(
                            "(function(){var p=document.getElementById('movie_player');var v=document.querySelector('video');" +
                                "return (p&&p.getPlaybackQuality?p.getPlaybackQuality():'?')+' '+(v?v.videoWidth+'x'+v.videoHeight:'')})()"
                        ) { q -> android.util.Log.i("MovieShelfTrailer", "quality $q") }
                    }, 5000)
                }
                round >= 6 -> onFailed()
                else -> watchPlayback(view, round + 1, onPlaying, onFailed)
            }
        }
    }, 2000)
}

/**
 * Makes the page report [codecs] (e.g. "av01") as unsupported, so YouTube streams another format.
 * Some TV WebViews decode AV1 in hardware but can't display those frames (black video with sound).
 */
internal fun codecBlockScript(codecs: List<String>): String {
    val list = codecs.joinToString(",") { "'${it.lowercase()}'" }
    return """
(function(){ var b=[$list]; function bad(t){ t=(t||'').toLowerCase(); return b.some(function(x){ return t.indexOf(x)>=0; }); }
 if (window.MediaSource && MediaSource.isTypeSupported) { var o=MediaSource.isTypeSupported.bind(MediaSource); MediaSource.isTypeSupported=function(t){ return bad(t)?false:o(t); }; }
 if (navigator.mediaCapabilities && navigator.mediaCapabilities.decodingInfo) { var d=navigator.mediaCapabilities.decodingInfo.bind(navigator.mediaCapabilities);
   navigator.mediaCapabilities.decodingInfo=function(c){ var t=c&&c.video&&c.video.contentType; if (bad(t)) return Promise.resolve({supported:false,smooth:false,powerEfficient:false}); return d(c); }; }
 var cp=HTMLMediaElement.prototype.canPlayType; HTMLMediaElement.prototype.canPlayType=function(t){ return bad(t)?'':cp.call(this,t); };
})();
""".trimIndent()
}

/**
 * Embedded YouTube starts trailers low and ramps up slowly, so they look soft on big TVs. Ask the player for
 * at least 1080p (up to the best available) via its quality range, retried while the player initialises.
 */
internal const val QUALITY_SCRIPT = """
(function(){ var n=0; (function go(){ var p=document.getElementById('movie_player');
  if (p && p.setPlaybackQualityRange) { try { p.setPlaybackQualityRange('hd1080','highres'); } catch(e){} }
  if (n++ < 20) setTimeout(go, 500); })(); })();
"""

internal fun embedUrl(key: String) =
    "https://www.youtube.com/embed/$key?autoplay=1&mute=1&controls=0&disablekb=1&fs=0&loop=1&playlist=$key" +
        "&playsinline=1&rel=0&modestbranding=1&iv_load_policy=3&enablejsapi=1&vq=hd1080"

/** Unmuting acts on the player page's own <video>; retried briefly because the video element appears late. */
internal fun muteScript(muted: Boolean) = """
(function(){ var n=0; (function go(){ var v=document.querySelector('video');
  if (v) { v.muted=$muted; if (!$muted) { v.volume=1; } v.play && v.play().catch(function(){}); }
  if (!v || n++ < 10) setTimeout(go, 300); })(); })();
""".trimIndent()

/** Hides YouTube's title/overlay chrome and keeps the page transparent; in background mode the video fills like a banner. */
internal fun styleScript(fill: Boolean) = """
(function(){ var s=document.createElement('style'); s.textContent =
 'html,body,#player,.html5-video-player{background:transparent!important;overflow:hidden!important}' +
 '.ytp-chrome-top,.ytp-gradient-top,.ytp-watermark,.ytp-pause-overlay,.ytp-show-cards-title,.ytp-chrome-bottom,.ytp-gradient-bottom,.ytp-spinner{display:none!important}' +
 ${if (fill) "'video{object-fit:cover!important;width:100vw!important;height:100vh!important;left:0!important;top:0!important}'" else "''"};
 document.head.appendChild(s); })();
""".trimIndent()
