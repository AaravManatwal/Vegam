package com.vegam.engine.smart

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

enum class Severity { INFO, WARN, BLOCK }
enum class RiskLevel { GREEN, YELLOW, RED }

data class Finding(val severity: Severity, val code: String, val message: String)

data class SecurityAssessment(val level: RiskLevel, val findings: List<Finding>) {
    val warnings: List<Finding> get() = findings.filter { it.severity != Severity.INFO }
    val blockReason: String? get() = findings.firstOrNull { it.severity == Severity.BLOCK }?.message
}

/**
 * Download se PEHLE ka chhota, local suraksha-jaanch. Ye malware scanner NAHI hai: koi signature database ya
 * virus engine nahi hai. Sirf URL, server ke headers aur file ke naam ki pehchaani hui gadbad dekhta hai.
 * Kuch na mile to bhi "safe" nahi kehta: sirf "koi jaana-pehchana risk nahi mila".
 * Kuch bhi bahar nahi bheja jaata.
 *
 * RED sirf tab jab dhokha pakka dikhe (document jaisa naam par asal mein executable). Baaki sab YELLOW.
 */
object SecurityAssessor {
    private val DECEPTIVE_DOUBLE = Regex(
        "\\.(pdf|docx?|xlsx?|pptx?|txt|jpe?g|png|gif|webp|mp3|mp4|mkv|avi|mov|zip|rar)\\.(exe|scr|bat|cmd|com|apk|msi|vbs|js|jar|ps1|lnk)$",
        RegexOption.IGNORE_CASE,
    )
    private val IPV4 = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")

    /** Brand -> uske asli domains. Brand naam kisi aur domain mein dikhe to sirf "sanket" (galat alarm ho sakta hai). */
    private val BRANDS: Map<String, List<String>> = mapOf(
        "paypal" to listOf("paypal.com", "paypal.me"),
        "google" to listOf("google.com", "google.co.in", "googleapis.com", "googleusercontent.com", "gstatic.com", "withgoogle.com", "googlevideo.com"),
        "microsoft" to listOf("microsoft.com", "live.com", "office.com", "windows.com", "windows.net", "azureedge.net", "sharepoint.com", "onmicrosoft.com"),
        "apple" to listOf("apple.com", "icloud.com", "mzstatic.com"),
        "amazon" to listOf("amazon.com", "amazon.in", "amazonaws.com", "cloudfront.net"),
        "facebook" to listOf("facebook.com", "fb.com", "fbcdn.net"),
        "instagram" to listOf("instagram.com", "cdninstagram.com"),
        "whatsapp" to listOf("whatsapp.com", "whatsapp.net"),
        "netflix" to listOf("netflix.com", "nflxvideo.net"),
        "paytm" to listOf("paytm.com"),
        "phonepe" to listOf("phonepe.com"),
    )

    fun assess(r: LinkReport): SecurityAssessment {
        val f = mutableListOf<Finding>()
        fun add(s: Severity, code: String, msg: String) { f += Finding(s, code, msg) }

        val url = r.finalUrl.toHttpUrlOrNull()
        val ext = r.extension
        val executable = ext != null && ext in FileTypes.EXECUTABLE_EXT

        if (url != null) {
            if (url.scheme == "http") add(Severity.WARN, "HTTP", "Link surakshit (HTTPS) nahi hai: raaste mein file badli ja sakti hai.")
            val host = url.host
            if (IPV4.matches(host) || ':' in host) add(Severity.WARN, "IP_HOST", "Link kisi website ke naam ki jagah seedha IP address par hai.")
            if (host.split('.').any { it.startsWith("xn--") }) add(Severity.WARN, "IDN_HOST", "Domain mein punycode (xn--) hai: dikhne mein jaane-pehchane naam jaisa nakli domain ho sakta hai.")
            if (url.username.isNotEmpty() || url.password.isNotEmpty()) {
                if ('.' in url.username) {
                    val sev = if (executable) Severity.BLOCK else Severity.WARN
                    add(sev, "USERINFO_TRICK", "Link mein '${url.username}@' jaisa hissa hai jo asli domain chhupane ke liye use hota hai. Asli server: $host")
                } else add(Severity.WARN, "USERINFO", "Link mein username/password likha hai.")
            }
            val port = url.port
            if (port != 80 && port != 443) add(Severity.INFO, "PORT", "Link alag port ($port) par hai.")
            if (host.split('.').size > 5) add(Severity.INFO, "DEEP_SUBDOMAIN", "Domain bahut lamba hai (kai subdomain).")
            val tokens = host.split('.', '-')
            for ((brand, official) in BRANDS) {
                if (brand in tokens && official.none { host == it || host.endsWith(".$it") }) {
                    add(Severity.WARN, "BRAND_LOOKALIKE", "Domain mein '$brand' likha hai par ye $brand ka asli domain nahi hai (sanket hai, galat alarm ho sakta hai).")
                    break
                }
            }
        } else {
            add(Severity.WARN, "BAD_URL", "Link ka format sahi nahi lag raha.")
        }
        if (r.finalUrl.length > 400) add(Severity.INFO, "LONG_URL", "Link bahut lamba hai.")

        // File ka naam / type
        if (DECEPTIVE_DOUBLE.containsMatchIn(r.fileName)) {
            add(Severity.BLOCK, "DOUBLE_EXT", "File ka naam '${r.fileName}' document/media jaisa dikhta hai par asal mein chalne wali (executable) file hai. Ye ek jaani-pehchani chaal hai.")
        } else if (executable) {
            if (ext == "apk" || ext == "xapk" || ext == "apks" || ext == "aab") {
                add(Severity.WARN, "APK", "Ye Android app (.$ext) hai. Sirf bharosemand source se install karo.")
                add(Severity.INFO, "APK_SIG", "APK ka signature download ke baad hi padha ja sakta hai. Install se pehle Android khud bhi jaanchta hai.")
            } else {
                add(Severity.WARN, "EXECUTABLE", "Ye chalne wali file (.$ext) hai. Sirf bharosemand source se kholo.")
            }
        }

        // MIME vs extension
        val mimeCat = FileTypes.mimeCategory(r.mime)
        val extCat = if (ext != null) FileTypes.categoryOf(ext, null) else null
        if (mimeCat != null && extCat != null && extCat != FileCategory.OTHER && mimeCat != extCat) {
            add(Severity.WARN, "MIME_MISMATCH", "Server file ko '${FileTypes.cleanMime(r.mime)}' bata raha hai par naam .$ext hai: dono mel nahi khaate.")
        }
        if (r.looksLikeWebPage && extCat != null && extCat != FileCategory.OTHER) {
            add(Severity.WARN, "HTML_FOR_FILE", "Link file ki jagah ek web page (HTML) de raha hai.")
        }

        // Redirects
        val hops = r.redirects
        if (hops.isNotEmpty()) {
            val startHttps = r.inputUrl.startsWith("https://", true)
            if (startHttps && r.finalUrl.startsWith("http://", true)) add(Severity.WARN, "DOWNGRADE", "Link HTTPS se HTTP par bheja gaya (kam surakshit).")
            val h0 = UrlTools.host(r.inputUrl)
            val h1 = UrlTools.host(r.finalUrl)
            if (h0 != null && h1 != null && h0 != h1) {
                if (executable) add(Severity.WARN, "REDIRECT_HOST", "Link doosre domain ($h1) par bheja gaya.")
                else add(Severity.INFO, "REDIRECT_HOST", "Link doosre domain ($h1) par bheja gaya (CDN mein aam hai).")
            }
        }
        if (r.state == LinkState.TLS_ERROR) add(Severity.WARN, "TLS", "HTTPS certificate theek nahi: ${r.stateDetail}")

        val level = when {
            f.any { it.severity == Severity.BLOCK } -> RiskLevel.RED
            f.any { it.severity == Severity.WARN } -> RiskLevel.YELLOW
            else -> RiskLevel.GREEN
        }
        return SecurityAssessment(level, f)
    }

    fun label(level: RiskLevel): String = when (level) {
        RiskLevel.GREEN -> "Koi jaana-pehchana risk nahi mila (ye guarantee nahi hai)"
        RiskLevel.YELLOW -> "Savdhaani: kuch sanket mile"
        RiskLevel.RED -> "Zyada khatra: download block"
    }
}
