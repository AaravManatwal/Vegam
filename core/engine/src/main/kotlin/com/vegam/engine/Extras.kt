package com.vegam.engine

import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneId

/** SHA-256 of a file (Detail screen ka "checksum"). 64KB chunks, badi file memory mein nahi aati. */
object Checksum {
    fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}

/** Same naam ki do downloads ek doosre ko overwrite na karein: "file.zip" -> "file (1).zip". */
object UniqueName {
    fun pick(name: String, exists: (String) -> Boolean): String {
        if (!exists(name)) return name
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 1
        while (true) {
            val candidate = "$base ($i)$ext"
            if (!exists(candidate)) return candidate
            i++
        }
    }
}

/** Schedule ke time ka hisaab. Zone parameter se aata hai taaki test kisi bhi timezone mein same chale. */
object Schedule {
    /** Aaj `hour:minute` agar abhi se aage hai, warna kal wahi time. */
    fun nextOccurrence(nowMillis: Long, zone: ZoneId, hour: Int, minute: Int): Long {
        val now = Instant.ofEpochMilli(nowMillis).atZone(zone)
        var t = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
        if (!t.isAfter(now)) t = t.plusDays(1)
        return t.toInstant().toEpochMilli()
    }

    fun inHours(nowMillis: Long, hours: Int): Long = nowMillis + hours * 3_600_000L
}

/** Browser se Share karne par text mein se pehla http(s) link nikalo ("Check this https://x.com/f.zip"). */
fun extractUrl(text: String?): String? =
    text?.let { Regex("https?://[^\\s]+").find(it)?.value?.trimEnd('.', ',', ')', ']') }

/** Jin extensions par Vegam "ye file download hai" maanta hai (clipboard suggestion + manifest ke VIEW filters ke saath same list). */
val FILE_EXTENSIONS = setOf("zip", "rar", "7z", "tar", "gz", "apk", "xapk", "apks", "iso", "exe", "msi", "dmg", "mp4", "mkv", "avi", "mov", "webm", "mp3", "flac", "wav", "m4a", "pdf", "epub")

/** Link ka path kisi jaani-pehchani file par khatam hota hai? Query/fragment ignore. Sirf http(s). */
fun isLikelyFileUrl(url: String): Boolean {
    if (!url.startsWith("http://", true) && !url.startsWith("https://", true)) return false
    val path = url.substringBefore('#').substringBefore('?').trimEnd('/')
    val name = path.substringAfterLast('/')
    if ('.' !in name) return false
    return name.substringAfterLast('.').lowercase() in FILE_EXTENSIONS
}

/** Probe ka public roop (Prober internal hai). Add screen ko naam/size/Range-support dikhane ke liye. */
data class FileInfo(
    /** Server ne size nahi bataya to -1 (ya 0): download chalega, par 1 connection se aur % ke bina. */
    val totalBytes: Long,
    val acceptRanges: Boolean,
    val etag: String?,
    val lastModified: String?,
    val contentType: String? = null,
) {
    val sizeKnown: Boolean get() = totalBytes > 0
    /** File ki jagah web page (text/html) mil raha hai? Aksar ye "download page" hota hai, asli file ka link nahi. */
    val looksLikeWebPage: Boolean get() = contentType?.substringBefore(';')?.trim()?.lowercase() in setOf("text/html", "application/xhtml+xml")
}
