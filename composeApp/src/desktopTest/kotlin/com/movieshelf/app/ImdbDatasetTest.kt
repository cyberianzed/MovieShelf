package com.movieshelf.app

import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import java.util.zip.GZIPOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ImdbDatasetTest {
    private fun gz(dir: File, lines: List<String>): File = File(dir, "ratings.tsv.gz").also { f ->
        GZIPOutputStream(f.outputStream()).bufferedWriter().use { w -> lines.forEach { w.write(it); w.write("\n") } }
    }

    @Test fun parsesIndexesAndLooksUp() = runTest {
        val dir = Files.createTempDirectory("imdb").toFile()
        // IMDb's file is sorted as text (tt10... before tt2...), so the index must sort numerically itself.
        val rows = listOf("tconst\taverageRating\tnumVotes", "tt10000001\t6.5\t1789", "tt0111161\t9.3\t3112000", "tt38265603\t7.5\t6460") +
            (1..200).map { "tt${5_000_000 + it}\t5.0\t10" }
        val source = gz(dir, rows)
        val data = ImdbDataset(File(dir, "cache"), url = source.toURI().toString())
        val hits = data.lookup(listOf("tt38265603", "tt0111161", "tt10000001", "tt99999999", "bad"))
        assertEquals(7.5 to 6460, hits["tt38265603"])
        assertEquals(9.3 to 3112000, hits["tt0111161"])
        assertEquals(6.5 to 1789, hits["tt10000001"])
        assertNull(hits["tt99999999"])
        assertTrue(File(dir, "cache/imdb_ratings.bin").exists())
        // A second instance (next app start) reads the saved index without downloading.
        source.delete()
        assertEquals(7.5 to 6460, ImdbDataset(File(dir, "cache"), url = source.toURI().toString()).lookup(listOf("tt38265603"))["tt38265603"])
    }

    @Test fun downloadFailureMeansNoRatingsNotACrash() = runTest {
        val dir = Files.createTempDirectory("imdb").toFile()
        val data = ImdbDataset(dir, url = File(dir, "missing.gz").toURI().toString())
        assertTrue(data.lookup(listOf("tt0111161")).isEmpty())
    }

    @Test fun parseLineRejectsJunk() {
        assertNull(ImdbDataset.parseLine("tconst\taverageRating\tnumVotes"))
        assertNull(ImdbDataset.parseLine("nm0000001\t5\t5"))
        assertEquals(7.2 to 5786, ImdbDataset.find(longArrayOf(ImdbDataset.parseLine("tt29959401\t7.2\t5786")!!), "tt29959401"))
    }
}
