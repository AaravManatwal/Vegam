package com.vegam.feature.smart

import com.vegam.engine.Formatters
import com.vegam.engine.smart.Compat
import com.vegam.engine.smart.DuplicateKind
import com.vegam.engine.smart.LinkState
import com.vegam.engine.smart.RiskLevel
import com.vegam.engine.smart.SmartResult

/** Dialog ki ek line: label + value + gambhirta (rang ke liye). */
enum class Tone { OK, WARN, BAD, NEUTRAL }
data class InfoRow(val label: String, val value: String, val tone: Tone = Tone.NEUTRAL)

/** SmartResult -> dialog ki lines. Pure logic: UI ke bina test hota hai. Server ne jo bola wahi likhte hain, andaza nahi. */
object SmartText {
    fun sizeText(bytes: Long?): String = if (bytes == null || bytes <= 0) "Size pata nahi" else Formatters.bytes(bytes)

    fun linkState(s: LinkState): Pair<String, Tone> = when (s) {
        LinkState.VALID -> "Link chal raha hai" to Tone.OK
        LinkState.NOT_CHECKED -> "Jaanch band hai" to Tone.NEUTRAL
        LinkState.INCONCLUSIVE -> "Pakka pata nahi chala" to Tone.WARN
        LinkState.TEMP_UNAVAILABLE -> "Server abhi jawab nahi de raha" to Tone.WARN
        LinkState.NETWORK_ERROR -> "Network dikkat" to Tone.WARN
        LinkState.EXPIRED_EVIDENCE -> "Link ki likhi hui expiry beet chuki hai" to Tone.BAD
        LinkState.ACCESS_DENIED -> "Server ne access mana kiya" to Tone.BAD
        LinkState.AUTH_REQUIRED -> "Login chahiye" to Tone.BAD
        LinkState.INVALID -> "Link galat / file nahi mili" to Tone.BAD
        LinkState.TLS_ERROR -> "Secure connection (HTTPS) mein dikkat" to Tone.BAD
        LinkState.UNSUPPORTED -> "Ye tarah ka link supported nahi" to Tone.BAD
    }

    fun rows(r: SmartResult): List<InfoRow> = buildList {
        val rep = r.report
        val (vText, vTone) = linkState(rep.state)
        add(InfoRow("Link", vText + (rep.httpStatus?.let { " (HTTP $it)" } ?: ""), vTone))
        add(InfoRow("Server", when (rep.acceptRanges) {
            true -> "Resume / multi-connection supported"
            false -> "Resume supported nahi (1 connection)"
            null -> "Resume ka pata nahi"
        }, if (rep.acceptRanges == false) Tone.WARN else Tone.NEUTRAL))
        r.duplicate?.let { d ->
            add(InfoRow("Duplicate", if (d.kind == DuplicateKind.NONE) "Koi duplicate nahi mila" else d.explanation,
                if (d.isDuplicateWarning) Tone.WARN else Tone.NEUTRAL))
        }
        add(InfoRow("Vegam engine", r.compatibility.notes.firstOrNull() ?: when (r.compatibility.level) {
            Compat.OK -> "Poori tarah compatible"
            Compat.LIMITED -> "Limited"
            Compat.NOT_POSSIBLE -> "Download possible nahi"
        }, when (r.compatibility.level) { Compat.OK -> Tone.OK; Compat.LIMITED -> Tone.WARN; Compat.NOT_POSSIBLE -> Tone.BAD }))
        r.security?.let { sec ->
            add(InfoRow("Security", com.vegam.engine.smart.SecurityAssessor.label(sec.level),
                when (sec.level) { RiskLevel.GREEN -> Tone.OK; RiskLevel.YELLOW -> Tone.WARN; RiskLevel.RED -> Tone.BAD }))
        }
    }

    fun warnings(r: SmartResult): List<String> = buildList {
        r.security?.warnings?.forEach { add(it.message) }
        addAll(r.report.notes)
    }.distinct()

    fun advanced(r: SmartResult): List<Pair<String, String>> {
        val rep = r.report
        return buildList {
            add("URL" to rep.inputUrl)
            if (rep.finalUrl != rep.inputUrl) add("Final URL" to rep.finalUrl)
            if (rep.redirects.isNotEmpty()) add("Redirects" to rep.redirects.joinToString(" → ") { "${it.code}" })
            rep.mime?.let { add("MIME" to it) }
            rep.contentDisposition?.let { add("Content-Disposition" to it) }
            rep.server?.let { add("Server" to it) }
            rep.etag?.let { add("ETag" to it) }
            rep.lastModified?.let { add("Last-Modified" to it) }
            rep.contentHash?.let { add("Server hash" to it) }
            add("Jaanch ka tareeka" to "${rep.methodUsed} · ${rep.attempts} koshish")
            if (rep.stateDetail.isNotBlank()) add("Detail" to rep.stateDetail)
        }
    }
}
