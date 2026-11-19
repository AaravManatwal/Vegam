package com.vegam.feature.onboarding

/** Pages ka content data mein: UI sirf dikhata hai. Last page par permission card aata hai. */
data class OnboardingPage(val title: String, val body: String)

val ONBOARDING_PAGES = listOf(
    OnboardingPage("Download at the speed of thought", "Ek file, 16 tak connections. Vegam use tukdon mein baant kar ek saath kheenchta hai."),
    OnboardingPage("Kabhi adhoora nahi", "Net gaya, app band hui, phone restart hua. Download wahin se aage badhegi jahan ruki thi."),
    OnboardingPage("Queue aur schedule", "Raat 2 baje ke liye lagao, Wi-Fi par hi chalao, ya speed limit rakho. Sab tumhare control mein."),
)

/** Button ka text: aakhri page par "Get started", baaki par "Next". */
fun nextLabel(page: Int, total: Int = ONBOARDING_PAGES.size) = if (page >= total - 1) "Get started" else "Next"

/** Skip sirf aakhri page se pehle dikhta hai. */
fun showSkip(page: Int, total: Int = ONBOARDING_PAGES.size) = page < total - 1
