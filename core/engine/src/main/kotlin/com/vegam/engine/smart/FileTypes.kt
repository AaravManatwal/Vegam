package com.vegam.engine.smart

import com.vegam.engine.UrlNames
import java.net.URLDecoder

enum class FileCategory { DOCUMENT, ARCHIVE, AUDIO, VIDEO, IMAGE, APP, OTHER }

/**
 * File ki pehchaan: extension, MIME, Content-Disposition. Koi bandhi hui list nahi:
 * list sirf category (icon/label) ke liye hai; "download hai ya nahi" server ke jawab se tay hota hai.
 */
object FileTypes {
    private val BY_EXT: Map<FileCategory, Set<String>> = mapOf(
        FileCategory.DOCUMENT to setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "rtf", "odt", "ods", "csv", "epub", "md"),
        FileCategory.ARCHIVE to setOf("zip", "rar", "7z", "tar", "gz", "tgz", "bz2", "xz", "iso", "img"),
        FileCategory.AUDIO to setOf("mp3", "wav", "aac", "flac", "m4a", "ogg", "opus", "wma"),
        FileCategory.VIDEO to setOf("mp4", "mkv", "avi", "mov", "webm", "flv", "wmv", "3gp", "m4v", "ts"),
        FileCategory.IMAGE to setOf("jpg", "jpeg", "png", "webp", "gif", "svg", "bmp", "heic", "ico", "tiff"),
        FileCategory.APP to setOf("apk", "xapk", "apks", "aab", "exe", "msi", "dmg", "pkg", "deb", "rpm", "jar"),
    )

    /** Chalane (execute) layak files: security check inhe alag nazar se dekhta hai. */
    val EXECUTABLE_EXT = setOf("apk", "xapk", "apks", "aab", "exe", "msi", "bat", "cmd", "scr", "com", "jar", "vbs", "ps1", "lnk", "sh", "dmg", "pkg", "deb", "rpm", "js")

    fun cleanMime(mime: String?): String? = mime?.substringBefore(';')?.trim()?.lowercase()?.ifBlank { null }

    fun extensionOf(name: String): String? {
        val dot = name.lastIndexOf('.')
        if (dot <= 0 || dot == name.length - 1) return null
        return name.substring(dot + 1).lowercase().takeIf { it.length <= 8 && it.all { c -> c.isLetterOrDigit() } }
    }

    fun categoryOf(ext: String?, mime: String?): FileCategory {
        val byExt = BY_EXT.entries.firstOrNull { ext != null && ext in it.value }?.key
        if (byExt != null) return byExt
        val m = cleanMime(mime) ?: return FileCategory.OTHER
        return when {
            m.startsWith("image/") -> FileCategory.IMAGE
            m.startsWith("video/") -> FileCategory.VIDEO
            m.startsWith("audio/") -> FileCategory.AUDIO
            m == "application/pdf" || m == "text/plain" || m.contains("msword") || m.contains("officedocument") || m.contains("opendocument") -> FileCategory.DOCUMENT
            m.contains("zip") || m.contains("rar") || m.contains("tar") || m.contains("7z") || m.contains("gzip") -> FileCategory.ARCHIVE
            m == "application/vnd.android.package-archive" || m == "application/x-msdownload" -> FileCategory.APP
            else -> FileCategory.OTHER
        }
    }

    /** Category ke liye mime ka akela anumaan (extension se milane ke kaam aata hai). */
    fun mimeCategory(mime: String?): FileCategory? {
        val m = cleanMime(mime) ?: return null
        if (m == "application/octet-stream" || m == "binary/octet-stream") return null   // kuch bhi ho sakta hai
        return categoryOf(null, m).takeIf { it != FileCategory.OTHER }
    }

    fun isAttachment(contentDisposition: String?): Boolean =
        contentDisposition?.trimStart()?.startsWith("attachment", ignoreCase = true) == true

    /** HTML page, file nahi (jab tak server khud attachment na bataye). */
    fun looksLikeWebPage(mime: String?, contentDisposition: String?): Boolean {
        if (isAttachment(contentDisposition)) return false
        val m = cleanMime(mime) ?: return false
        return m == "text/html" || m == "application/xhtml+xml"
    }

    /** Content-Disposition se file ka naam: pehle RFC 5987 (filename*=UTF-8''...), phir filename="..." / filename=... */
    fun parseContentDisposition(header: String?): String? {
        if (header.isNullOrBlank()) return null
        val star = Regex("filename\\*\\s*=\\s*([^';]*)'[^']*'([^;]+)", RegexOption.IGNORE_CASE).find(header)
        if (star != null) {
            val charset = star.groupValues[1].ifBlank { "UTF-8" }
            val raw = star.groupValues[2].trim()
            val decoded = runCatching { URLDecoder.decode(raw.replace("+", "%2B"), charset) }.getOrNull()
            if (!decoded.isNullOrBlank()) return safeName(decoded)
        }
        val quoted = Regex("filename\\s*=\\s*\"([^\"]*)\"", RegexOption.IGNORE_CASE).find(header)?.groupValues?.get(1)
        val plain = Regex("filename\\s*=\\s*([^;\"]+)", RegexOption.IGNORE_CASE).find(header)?.groupValues?.get(1)?.trim()
        val v = (quoted ?: plain)?.takeIf { it.isNotBlank() } ?: return null
        return safeName(v)
    }

    /** Path-traversal ("../../x") aur galat characters hatao: sirf aakhri hissa, saaf. */
    fun safeName(raw: String): String {
        val last = raw.substringAfterLast('/').substringAfterLast('\\')
        val clean = last.replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001F]"), "_").trim().trim('.')
        return clean.ifBlank { "download.bin" }
    }

    fun resolveName(url: String, contentDisposition: String?): String =
        parseContentDisposition(contentDisposition) ?: UrlNames.fileName(url)
}
