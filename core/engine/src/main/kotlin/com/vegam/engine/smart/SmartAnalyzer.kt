package com.vegam.engine.smart

/** Settings ke 4 switch jo analysis ke hisse chalate/rokte hain. Baaki switch (clipboard, notification...) app layer ke hain. */
data class SmartToggles(
    val linkAnalysis: Boolean = true,
    val duplicateDetection: Boolean = true,
    val linkValidation: Boolean = true,
    val securityCheck: Boolean = true,
)

data class SmartResult(
    val report: LinkReport,
    /** null = jaancha nahi (setting band). */
    val security: SecurityAssessment?,
    val duplicate: DuplicateResult?,
    val compatibility: Compatibility,
    val decision: Decision,
    val alternative: Alternative?,
    val toggles: SmartToggles,
)

/**
 * Poora pipeline ek jagah: link jaanch -> duplicate -> security -> engine compatibility -> faisla.
 * Koi bhi switch band ho to wahi hissa chhoot jaata hai, baaki chalta rehta hai.
 * Download khud yahan se nahi hota: ye sirf jaankari + faisla (canDownload / extraConfirm) deta hai.
 */
class SmartAnalyzer(private val link: LinkAnalyzer = LinkAnalyzer()) {

    suspend fun run(
        url: String,
        known: List<KnownDownload>,
        toggles: SmartToggles = SmartToggles(),
        onStep: (AnalysisStep) -> Unit = {},
    ): SmartResult {
        var report = if (toggles.linkAnalysis) link.analyze(url, onStep)
        else LinkReport.fromUrlOnly(url, LinkState.NOT_CHECKED, "Smart Link Analysis band hai: sirf link ke naam se jaankari.")

        if (toggles.linkAnalysis && !toggles.linkValidation && report.state != LinkState.UNSUPPORTED && report.state != LinkState.TLS_ERROR) {
            // Metadata (size/type) rakhte hain, par "link sahi/galat" ka faisla nahi dikhate.
            report = report.copy(state = LinkState.NOT_CHECKED, stateDetail = "Link Validation band hai.")
        }
        val security = if (toggles.securityCheck) SecurityAssessor.assess(report) else null
        val dup = if (toggles.duplicateDetection) DuplicateDetector.detect(report, known) else null
        val compat = SmartPolicy.compatibility(report)
        val decision = SmartPolicy.decide(security, compat, report, dup)
        return SmartResult(report, security, dup, compat, decision, SmartPolicy.httpsAlternative(report), toggles)
    }
}
