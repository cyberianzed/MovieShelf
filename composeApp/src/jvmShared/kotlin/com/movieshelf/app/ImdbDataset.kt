package com.movieshelf.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.util.zip.GZIPInputStream
import kotlin.math.roundToInt

/**
 * IMDb's official ratings file (https://datasets.imdbws.com/title.ratings.tsv.gz, refreshed daily by IMDb,
 * free for personal and non-commercial use). It covers far more new Malayalam films than OMDb.
 *
 * The ~9 MB download is turned into a sorted LongArray (one long per title: id, rating x10, votes) and saved,
 * so later starts load it in well under a second and every lookup is a binary search.
 */
class ImdbDataset(
    private val dir: File,
    private val url: String = "https://datasets.imdbws.com/title.ratings.tsv.gz",
    private val maxAgeMillis: Long = 3 * DAY,
) {
    private val mutex = Mutex()
    private var index: LongArray? = null
    private var lastAttempt = 0L

    /** IMDb rating and vote count for each id that IMDb has rated. Empty if the file can't be loaded. */
    suspend fun lookup(imdbIds: Collection<String>): Map<String, Pair<Double, Int>> = withContext(Dispatchers.IO) {
        val idx = ensureLoaded() ?: return@withContext emptyMap()
        imdbIds.mapNotNull { id -> find(idx, id)?.let { id to it } }.toMap()
    }

    private suspend fun ensureLoaded(): LongArray? = mutex.withLock {
        val bin = File(dir, "imdb_ratings.bin")
        val stale = !bin.exists() || System.currentTimeMillis() - bin.lastModified() > maxAgeMillis
        // Retry a failed download at most once an hour; keep using the old index meanwhile.
        if (stale && System.currentTimeMillis() - lastAttempt > HOUR) {
            lastAttempt = System.currentTimeMillis()
            try { rebuild(bin); index = null } catch (e: CancellationException) { throw e } catch (_: Exception) { }
        }
        if (index == null && bin.exists()) index = try { read(bin) } catch (_: Exception) { null }
        index
    }

    private fun rebuild(bin: File) {
        dir.mkdirs()
        val connection = URL(url).openConnection().apply {
            connectTimeout = 15_000
            readTimeout = 60_000
        }
        if (connection is HttpURLConnection && connection.responseCode !in 200..299) throw HttpStatusException(connection.responseCode)
        var packed = LongArray(2_000_000)
        var count = 0
        GZIPInputStream(connection.getInputStream().buffered(1 shl 16)).bufferedReader().useLines { lines ->
            lines.drop(1).forEach { line ->
                val packedLine = parseLine(line) ?: return@forEach
                if (count == packed.size) packed = packed.copyOf(packed.size * 2)
                packed[count++] = packedLine
            }
        }
        if (count < 100) error("IMDb ratings file looks truncated")
        val sorted = packed.copyOf(count).also { it.sort() }
        val tmp = File(dir, "imdb_ratings.bin.tmp")
        tmp.writeBytes(ByteBuffer.allocate(count * 8).apply { asLongBuffer().put(sorted) }.array())
        bin.delete()
        if (!tmp.renameTo(bin)) error("Could not save IMDb ratings")
    }

    private fun read(bin: File): LongArray {
        val bytes = bin.readBytes()
        return LongArray(bytes.size / 8).also { ByteBuffer.wrap(bytes).asLongBuffer().get(it) }
    }

    companion object {
        private const val HOUR = 60 * 60 * 1000L
        const val DAY = 24 * HOUR

        /** "tt0111161\t9.3\t3000000" -> id << 32 | rating x10 << 24 | votes (capped at 16.7M). */
        fun parseLine(line: String): Long? {
            val t1 = line.indexOf('\t')
            val t2 = line.indexOf('\t', t1 + 1)
            if (t1 < 3 || t2 < 0 || !line.startsWith("tt")) return null
            val id = line.substring(2, t1).toLongOrNull() ?: return null
            val rating = line.substring(t1 + 1, t2).toDoubleOrNull()?.let { (it * 10).roundToInt() } ?: return null
            val votes = line.substring(t2 + 1).trim().toIntOrNull() ?: return null
            return (id shl 32) or (rating.toLong() shl 24) or votes.coerceIn(0, 0xFFFFFF).toLong()
        }

        fun find(index: LongArray, imdbId: String): Pair<Double, Int>? {
            val id = imdbId.removePrefix("tt").toLongOrNull() ?: return null
            var lo = 0
            var hi = index.size - 1
            while (lo <= hi) {
                val mid = (lo + hi) ushr 1
                val midId = index[mid] ushr 32
                when {
                    midId < id -> lo = mid + 1
                    midId > id -> hi = mid - 1
                    else -> {
                        val v = index[mid]
                        return ((v ushr 24) and 0xFF).toInt() / 10.0 to (v and 0xFFFFFF).toInt()
                    }
                }
            }
            return null
        }
    }
}
