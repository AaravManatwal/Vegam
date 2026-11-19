package com.vegam.engine.smart

/** Link ki haalat, server ke jawab ke hisaab se. Jahan jawab saaf nahi, wahan INCONCLUSIVE (andaza nahi). */
enum class LinkState {
    VALID, INVALID, EXPIRED_EVIDENCE, ACCESS_DENIED, AUTH_REQUIRED, TEMP_UNAVAILABLE,
    TLS_ERROR, UNSUPPORTED, NETWORK_ERROR, INCONCLUSIVE,
    /** Analysis band hai ya abhi nahi hui: sirf URL se jo pata chala wahi hai. */
    NOT_CHECKED,
}

data class RedirectHop(val url: String, val code: Int)

/** Network analysis ka poora nateeja. Sab kuch yahan ek jagah: dialog, security, duplicate sab isi se chalte hain. */
data class LinkReport(
    val inputUrl: String,
    val finalUrl: String,
    val normalizedUrl: String,
    val state: LinkState,
    val stateDetail: String,
    val httpStatus: Int? = null,
    val fileName: String,
    val extension: String?,
    val category: FileCategory,
    val mime: String? = null,
    val contentDisposition: String? = null,
    val server: String? = null,
    val sizeBytes: Long? = null,
    val acceptRanges: Boolean? = null,
    val etag: String? = null,
    val lastModified: String? = null,
    /** Server ne diya hash (Digest / Content-MD5 header). Sirf tab jab server khud de. */
    val contentHash: String? = null,
    val redirects: List<RedirectHop> = emptyList(),
    val methodUsed: String = "none",
    val attempts: Int = 0,
    val looksLikeWebPage: Boolean = false,
    val notes: List<String> = emptyList(),
) {
    val host: String? get() = UrlTools.host(finalUrl)

    companion object {
        /** Bina network ke: sirf URL se naam/type. Analysis OFF ho ya network se pehle dikhane ke liye. */
        fun fromUrlOnly(url: String, state: LinkState = LinkState.NOT_CHECKED, detail: String = "Network analysis nahi hui"): LinkReport {
            val name = FileTypes.resolveName(url, null)
            val ext = FileTypes.extensionOf(name)
            return LinkReport(
                inputUrl = url, finalUrl = url, normalizedUrl = UrlTools.normalize(url), state = state, stateDetail = detail,
                fileName = name, extension = ext, category = FileTypes.categoryOf(ext, null),
            )
        }
    }
}
