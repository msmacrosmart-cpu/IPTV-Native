package com.example.iptv.data.epg

import com.example.iptv.data.model.EpgEntry
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.IOException
import java.io.StringReader
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.zip.GZIPInputStream
import javax.xml.parsers.SAXParserFactory

/** Streaming XMLTV reader. It retains only the time window used by the guide. */
object XmltvParser {
    data class Result(val programs: Map<String, List<EpgEntry>>, val programCount: Int)

    private const val MAX_PROGRAMS = 250_000
    private val timestamp = Regex("^(\\d{14})(?:\\s*(Z|[+-]\\d{2}:?\\d{2}))?$")

    fun parse(
        file: File,
        windowStart: Long,
        windowEnd: Long,
        checkCancelled: () -> Unit = {}
    ): Result {
        require(windowEnd > windowStart)
        val utc = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US).apply {
            isLenient = false
            timeZone = TimeZone.getTimeZone("UTC")
        }
        fun parseTime(raw: String?): Long? {
            val match = timestamp.matchEntire(raw?.trim().orEmpty()) ?: return null
            val zone = match.groupValues[2].let { if (it.isBlank() || it == "Z") "+0000" else it.replace(":", "") }
            val value = "${match.groupValues[1]} $zone"
            val position = ParsePosition(0)
            val result = utc.parse(value, position)
            return result?.time?.takeIf { position.index == value.length }
        }

        val programs = linkedMapOf<String, MutableList<EpgEntry>>()
        var count = 0
        val handler = object : DefaultHandler() {
            var channel: String? = null
            var start: Long? = null
            var end: Long? = null
            var title = ""
            var description = ""
            var field: String? = null
            val text = StringBuilder()
            var seen = 0

            override fun resolveEntity(publicId: String?, systemId: String?): InputSource =
                InputSource(StringReader(""))

            override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes) {
                val name = qName?.takeIf { it.isNotEmpty() } ?: localName.orEmpty()
                when (name) {
                    "programme" -> {
                        if (++seen % 256 == 0) checkCancelled()
                        channel = attributes.getValue("channel")?.trim()?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }
                        start = parseTime(attributes.getValue("start"))
                        end = parseTime(attributes.getValue("stop"))
                        title = ""
                        description = ""
                    }
                    "title", "desc" -> if (channel != null && field == null) {
                        field = name
                        text.setLength(0)
                    }
                }
            }

            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (field != null && text.length < 16_384) {
                    text.append(ch, start, minOf(length, 16_384 - text.length))
                }
            }

            override fun endElement(uri: String?, localName: String?, qName: String?) {
                val name = qName?.takeIf { it.isNotEmpty() } ?: localName.orEmpty()
                if (name == field) {
                    val value = text.toString().trim()
                    if (name == "title" && title.isEmpty()) title = value
                    if (name == "desc" && description.isEmpty()) description = value
                    field = null
                }
                if (name != "programme") return
                val key = channel
                val from = start
                val until = end
                if (key != null && from != null && until != null && until > from &&
                    until > windowStart && from < windowEnd && title.isNotBlank()
                ) {
                    if (count >= MAX_PROGRAMS) throw IOException("O guia XMLTV excede o limite de programas.")
                    programs.getOrPut(key) { arrayListOf() }.add(
                        EpgEntry("$key:$from", title, description, from, until, isNowPlaying = false)
                    )
                    count++
                }
                channel = null
                field = null
            }
        }

        val factory = SAXParserFactory.newInstance().apply { isNamespaceAware = false }
        // Many XMLTV feeds contain a DOCTYPE. Allow it, but never fetch external entities/DTDs.
        listOf(
            "http://xml.org/sax/features/external-general-entities",
            "http://xml.org/sax/features/external-parameter-entities",
            "http://apache.org/xml/features/nonvalidating/load-external-dtd"
        ).forEach { feature -> runCatching { factory.setFeature(feature, false) } }
        checkCancelled()
        openMaybeGzip(file).use { factory.newSAXParser().parse(it, handler) }
        checkCancelled()
        return Result(programs.mapValues { (_, entries) -> entries.sortedBy { it.startMillis } }, count)
    }

    private fun openMaybeGzip(file: File): InputStream {
        val input = BufferedInputStream(FileInputStream(file), 64 * 1024)
        input.mark(2)
        val first = input.read()
        val second = input.read()
        input.reset()
        return if (first == 0x1f && second == 0x8b) GZIPInputStream(input, 64 * 1024) else input
    }
}
