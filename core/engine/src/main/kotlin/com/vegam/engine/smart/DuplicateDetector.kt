package com.vegam.engine.smart

enum class KnownState { ACTIVE, COMPLETED, ENDED }   // ENDED = failed/cancelled

/** Pehle se jaani hui download/ record: DB se ya detection history se banti hai. */
data class KnownDownload(
    val id: Long,
    val normalizedUrl: String,
    val fileName: String,
    val sizeBytes: Long?,
    val etag: String?,
    val lastModified: String?,
    val hash: String?,
    val state: KnownState,
    /** COMPLETED ke liye: file abhi disk par hai? */
    val fileExists: Boolean = true,
)

enum class DuplicateKind {
    NONE,
    ALREADY_DOWNLOADING,
    PREVIOUSLY_DOWNLOADED,
    /** Record hai par file disk se hat chuki: dobara download theek hai. */
    PREVIOUSLY_DOWNLOADED_FILE_MISSING,
    /** Alag link par wahi file: size + (hash ya ETag ya Last-Modified) mile. */
    SAME_FILE_OTHER_LINK,
    /** Naam aur size mile par koi pakka saboot nahi (ETag/hash dono taraf nahi). */
    POSSIBLE_SAME_FILE,
}

data class DuplicateResult(
    val kind: DuplicateKind,
    val match: KnownDownload? = null,
    val explanation: String,
    /** Sirf naam same, file alag (size/ETag/hash alag). Ye duplicate NAHI hai: sirf jaankari. */
    val sameNameDifferentFiles: List<KnownDownload> = emptyList(),
) {
    /** Dialog mein "Download anyway" wali chetavani dikhani hai? */
    val isDuplicateWarning: Boolean
        get() = kind != DuplicateKind.NONE && kind != DuplicateKind.PREVIOUSLY_DOWNLOADED_FILE_MISSING
}

/**
 * Asli duplicate vs sirf same-naam. Naam akela kabhi duplicate nahi banata.
 * Order: same link > same file (size + saboot) > possible (naam + size).
 */
object DuplicateDetector {
    fun detect(report: LinkReport, known: List<KnownDownload>): DuplicateResult {
        val url = report.normalizedUrl
        val size = report.sizeBytes

        // 1) Wahi link
        val sameUrl = known.filter { it.normalizedUrl == url }
        sameUrl.firstOrNull { it.state == KnownState.ACTIVE }?.let {
            return DuplicateResult(DuplicateKind.ALREADY_DOWNLOADING, it, "Ye link abhi download ho raha hai (${it.fileName}).")
        }
        val done = sameUrl.filter { it.state == KnownState.COMPLETED }
        done.firstOrNull { it.fileExists }?.let {
            return DuplicateResult(DuplicateKind.PREVIOUSLY_DOWNLOADED, it, "Ye link pehle download ho chuka hai (${it.fileName}).")
        }
        val missing = done.firstOrNull()

        // 2) Alag link, wahi file
        for (k in known) {
            if (k.normalizedUrl == url || k.state == KnownState.ENDED) continue
            if (k.state == KnownState.COMPLETED && !k.fileExists) continue
            if (size == null || k.sizeBytes == null || k.sizeBytes != size) continue
            val hashEq = report.contentHash != null && k.hash != null && report.contentHash.equals(k.hash, true)
            val etagEq = report.etag != null && k.etag != null && report.etag == k.etag
            val lmEq = report.lastModified != null && k.lastModified != null && report.lastModified == k.lastModified
            val etagDiff = report.etag != null && k.etag != null && report.etag != k.etag
            val hashDiff = report.contentHash != null && k.hash != null && !report.contentHash.equals(k.hash, true)
            if (hashDiff) continue
            if (hashEq || etagEq || (lmEq && !etagDiff)) {
                val how = if (hashEq) "hash" else if (etagEq) "ETag" else "Last-Modified"
                return DuplicateResult(DuplicateKind.SAME_FILE_OTHER_LINK, k, "Alag link par wahi file lagti hai: size aur $how mile (${k.fileName}).")
            }
        }
        for (k in known) {
            if (k.normalizedUrl == url || k.state == KnownState.ENDED) continue
            if (k.state == KnownState.COMPLETED && !k.fileExists) continue
            if (size == null || k.sizeBytes != size) continue
            if (!k.fileName.equals(report.fileName, true)) continue
            val etagDiff = report.etag != null && k.etag != null && report.etag != k.etag
            val hashDiff = report.contentHash != null && k.hash != null && !report.contentHash.equals(k.hash, true)
            if (etagDiff || hashDiff) continue
            return DuplicateResult(DuplicateKind.POSSIBLE_SAME_FILE, k, "Naam aur size wahi hai (${k.fileName}), par pakka saboot nahi: ho sakta hai wahi file ho.")
        }

        val sameName = known.filter {
            it.normalizedUrl != url && it.fileName.equals(report.fileName, true) &&
                !(it.state == KnownState.COMPLETED && !it.fileExists) && it.state != KnownState.ENDED
        }
        if (missing != null) {
            return DuplicateResult(DuplicateKind.PREVIOUSLY_DOWNLOADED_FILE_MISSING, missing, "Ye link pehle download hua tha par file ab device par nahi mili.", sameName)
        }
        return DuplicateResult(
            DuplicateKind.NONE, null,
            if (sameName.isEmpty()) "Koi duplicate nahi mila." else "Isi naam ki ${sameName.size} file hai par size/pehchaan alag hai: alag file hai, duplicate nahi.",
            sameName,
        )
    }
}
