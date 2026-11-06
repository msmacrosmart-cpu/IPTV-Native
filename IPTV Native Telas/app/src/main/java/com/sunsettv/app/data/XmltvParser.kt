package com.sunsettv.app.data

import android.util.Xml
import com.sunsettv.app.domain.EpgProgram
import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.zip.GZIPInputStream

/** Leitor de EPG no formato XMLTV (aceita .xml e .xml.gz). Lê em streaming para economizar memória. */
object XmltvParser {

    data class Result(val programs: Map<String, List<EpgProgram>>, val programCount: Int)

    /** Só guarda programas dentro da janela [windowStart, windowEnd] para manter o cache enxuto. */
    fun parse(file: File, windowStart: Long, windowEnd: Long): Result {
        val withZone = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)
        val noZone = SimpleDateFormat("yyyyMMddHHmmss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        fun time(s: String?): Long? = runCatching {
            val t = s?.trim() ?: return null
            (if (t.length > 14) withZone.parse(t) else noZone.parse(t))?.time
        }.getOrNull()

        val map = HashMap<String, MutableList<EpgProgram>>()
        var count = 0

        openMaybeGzip(file).use { input ->
            val p = Xml.newPullParser()
            p.setInput(input, null)
            var event = p.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && p.name == "programme") {
                    val channel = p.getAttributeValue(null, "channel")
                    val start = time(p.getAttributeValue(null, "start"))
                    val stop = time(p.getAttributeValue(null, "stop"))
                    var title = ""
                    var desc = ""
                    while (true) {
                        event = p.next()
                        if (event == XmlPullParser.END_DOCUMENT) break
                        if (event == XmlPullParser.END_TAG && p.name == "programme") break
                        if (event == XmlPullParser.START_TAG) {
                            when (p.name) {
                                "title" -> { val t = p.nextText().trim(); if (title.isEmpty()) title = t }
                                "desc" -> { val d = p.nextText().trim(); if (desc.isEmpty()) desc = d }
                            }
                        }
                    }
                    if (channel != null && start != null && stop != null && title.isNotEmpty() &&
                        stop > windowStart && start < windowEnd
                    ) {
                        map.getOrPut(channel.lowercase()) { ArrayList() }.add(EpgProgram(title, desc, start, stop))
                        count++
                    }
                }
                event = p.next()
            }
        }
        map.values.forEach { it.sortBy { prog -> prog.startMs } }
        return Result(map, count)
    }

    private fun openMaybeGzip(file: File): InputStream {
        val bis = BufferedInputStream(FileInputStream(file), 64 * 1024)
        bis.mark(2)
        val b1 = bis.read()
        val b2 = bis.read()
        bis.reset()
        return if (b1 == 0x1f && b2 == 0x8b) GZIPInputStream(bis, 64 * 1024) else bis
    }
}
