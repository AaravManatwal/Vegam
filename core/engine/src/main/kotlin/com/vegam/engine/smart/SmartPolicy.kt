package com.vegam.engine.smart

enum class Compat { OK, LIMITED, NOT_POSSIBLE }

data class Compatibility(val level: Compat, val notes: List<String>)

/** Agar koi surakshit vikalp hai to: sirf user ki ijazat ke baad try hota hai. */
data class Alternative(val title: String, val explanation: String, val url: String)

data class Decision(
    val canDownload: Boolean,
    /** canDownload=false hone ki wajah. */
    val blockReason: String?,
    /** Khaali = seedha Download. Bhara = Download dabane ke baad ek aur confirmation, in karanon ke saath. */
    val extraConfirm: List<String>,
)

/** Existing VegamEngine ki asli seemayen yahan likhi hain: engine badla nahi gaya, sirf pehle se bata diya jaata hai. */
object SmartPolicy {
    fun compatibility(r: LinkReport): Compatibility {
        val notes = mutableListOf<String>()
        val scheme = r.inputUrl.substringBefore("://", "").lowercase()
        if (scheme != "http" && scheme != "https") {
            return Compatibility(Compat.NOT_POSSIBLE, listOf("Vegam sirf http/https links download karta hai (mila: '${scheme.ifBlank { "unknown" }}')."))
        }
        when (r.state) {
            LinkState.UNSUPPORTED -> return Compatibility(Compat.NOT_POSSIBLE, listOf(r.stateDetail))
            LinkState.TLS_ERROR -> return Compatibility(
                Compat.NOT_POSSIBLE,
                listOf("Server ka HTTPS certificate theek nahi. Vegam kharab certificate ko bypass nahi karta. (${r.stateDetail})"),
            )
            LinkState.AUTH_REQUIRED -> return Compatibility(
                Compat.NOT_POSSIBLE,
                listOf("Server login maang raha hai. Vegam abhi login/cookies nahi bhejta, isliye ye link seedha download nahi ho sakta."),
            )
            else -> Unit
        }
        val reached = r.state == LinkState.VALID
        if (reached && r.sizeBytes == null) {
            notes += "Server file ka size nahi batata: Vegam 1 connection se download karega. % / ETA nahi dikhega aur ruk jaane par zero se shuru hoga."
        }
        if (r.acceptRanges == false) {
            notes += "Server Range support nahi deta: ek hi connection se download hoga, aur ruk jaane par zero se shuru hoga."
        }
        if (r.sizeBytes == null && !reached) {
            notes += "Size pata nahi chala; download shuru hone par engine khud dobara jaanchega."
        }
        if (r.state == LinkState.ACCESS_DENIED || r.state == LinkState.INVALID || r.state == LinkState.EXPIRED_EVIDENCE) {
            notes += "Server ne link par aitraaz kiya hai: download fail hone ka bada chance hai."
        }
        return Compatibility(if (notes.isEmpty()) Compat.OK else Compat.LIMITED, notes)
    }

    /** HTTP link analysis mein fail hua par https roop maujood ho sakta hai: sirf SUJHAV (ijazat ke baad try). */
    fun httpsAlternative(r: LinkReport): Alternative? {
        if (!r.inputUrl.startsWith("http://", true)) return null
        if (r.state == LinkState.VALID || r.state == LinkState.NOT_CHECKED) return null
        if (r.state != LinkState.NETWORK_ERROR && r.state != LinkState.UNSUPPORTED && r.state != LinkState.INCONCLUSIVE) return null
        val https = "https://" + r.inputUrl.substring(7)
        return Alternative("HTTPS version try karein", "Ye HTTP link nahi chala. Wahi link HTTPS par try karein? (Aapki ijazat ke baad hi.)", https)
    }

    fun decide(security: SecurityAssessment?, compat: Compatibility, r: LinkReport, dup: DuplicateResult?): Decision {
        if (security?.level == RiskLevel.RED) {
            return Decision(false, security.blockReason ?: "Zyada khatra mila.", emptyList())
        }
        if (compat.level == Compat.NOT_POSSIBLE) {
            return Decision(false, compat.notes.firstOrNull() ?: "Ye download supported nahi.", emptyList())
        }
        val extra = mutableListOf<String>()
        security?.warnings?.forEach { extra += it.message }
        if (dup?.isDuplicateWarning == true) extra += dup.explanation
        when (r.state) {
            LinkState.INVALID, LinkState.EXPIRED_EVIDENCE, LinkState.ACCESS_DENIED -> extra += r.stateDetail
            else -> Unit
        }
        return Decision(true, null, extra)
    }
}
