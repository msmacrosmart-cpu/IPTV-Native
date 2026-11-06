package com.example.iptv.data.epg

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.Instant
import java.util.zip.GZIPOutputStream

class XmltvParserTest {
    @get:Rule val folder = TemporaryFolder()

    private val start = Instant.parse("2025-01-01T12:00:00Z").toEpochMilli()
    private val end = Instant.parse("2025-01-01T18:00:00Z").toEpochMilli()

    @Test fun parsesTimeZonesFiltersWindowAndSortsCaseInsensitiveChannels() {
        val file = xml("""
            <?xml version="1.0" encoding="UTF-8"?>
            <tv>
              <programme channel="News.BR" start="20250101153000 +0000" stop="20250101163000 +0000"><title>Depois</title></programme>
              <programme channel="NEWS.br" start="20250101090000 -0300" stop="20250101100000 -0300"><title>Jornal</title><desc>Notícias</desc></programme>
              <programme channel="News.BR" start="20250101070000 +0000" stop="20250101080000 +0000"><title>Fora da janela</title></programme>
              <programme channel="Other" start="20250101123000 +00:00" stop="20250101130000 +00:00"><title>Filme</title></programme>
              <programme channel="Other" start="invalid" stop="20250101130000 +0000"><title>Inválido</title></programme>
            </tv>
        """.trimIndent())
        val result = XmltvParser.parse(file, start, end)
        assertEquals(3, result.programCount)
        assertEquals(listOf("Jornal", "Depois"), result.programs.getValue("news.br").map { it.title })
        assertEquals("Notícias", result.programs.getValue("news.br").first().description)
        assertEquals(start, result.programs.getValue("news.br").first().startMillis)
        assertEquals(1, result.programs.getValue("other").size)
    }

    @Test fun readsGzipByMagicBytesRatherThanFileExtension() {
        val file = folder.newFile("guide.xml")
        GZIPOutputStream(file.outputStream()).bufferedWriter().use { it.write(
            "<tv><programme channel=\"canal\" start=\"20250101120000 Z\" stop=\"20250101130000 Z\"><title>Ao vivo</title></programme></tv>"
        ) }
        val result = XmltvParser.parse(file, start, end)
        assertEquals("Ao vivo", result.programs.getValue("canal").single().title)
        assertTrue(result.programs.getValue("canal").single().isCurrent(start + 1))
    }

    @Test fun doesNotResolveExternalEntities() {
        val secret = folder.newFile("secret.txt").apply { writeText("SHOULD_NOT_BE_READ") }
        val file = xml("""
            <!DOCTYPE tv [<!ENTITY secret SYSTEM "${secret.toURI()}">]>
            <tv><programme channel="one" start="20250101120000 +0000" stop="20250101130000 +0000">
            <title>Safe &secret;</title></programme></tv>
        """.trimIndent())
        val result = XmltvParser.parse(file, start, end)
        assertFalse(result.programs.values.flatten().any { it.title.contains("SHOULD_NOT_BE_READ") })
    }

    @Test fun cancellationCallbackRunsDuringLargeFeeds() {
        val file = xml(buildString {
            append("<tv>")
            repeat(300) { append("<programme channel=\"c\" start=\"20250101120000\" stop=\"20250101130000\"><title>A</title></programme>") }
            append("</tv>")
        })
        var calls = 0
        XmltvParser.parse(file, start, end) { calls++ }
        assertTrue(calls >= 3)
    }

    private fun xml(content: String): File = folder.newFile().apply { writeText(content) }
}
