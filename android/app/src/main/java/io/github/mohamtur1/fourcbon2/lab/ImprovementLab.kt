package io.github.mohamtur1.fourcbon2.lab

/**
 * Minimal Improvement Lab (Division 39).
 *
 * One baseline/candidate report, preserved versions, a fixed evaluator, and
 * approval before promotion. A candidate is promoted only when it beats the
 * baseline and the owner has allowed promotion.
 */
class ImprovementLab {
    data class Report(
        val baselineName: String,
        val candidateName: String,
        val baselineMetric: Double,
        val candidateMetric: Double,
        val promoted: Boolean,
        val reason: String,
    )

    private val versions = linkedMapOf<String, String>()
    private val reports = mutableListOf<Report>()

    fun recordVersion(name: String, content: String) {
        require(name.isNotBlank()) { "Version name is required." }
        require(content.isNotBlank()) { "Version content is required." }
        versions[name] = content
    }

    fun versionContent(name: String): String? = versions[name]

    /** Fixed evaluator: promotion requires a better metric AND explicit approval. */
    fun evaluate(
        baselineName: String,
        candidateName: String,
        baselineMetric: Double,
        candidateMetric: Double,
        promotionAllowed: Boolean,
    ): Report {
        val baselineVersion = versions[baselineName]
        val candidateVersion = versions[candidateName]
        require(baselineVersion != null) { "Baseline version is not preserved." }
        require(candidateVersion != null) { "Candidate version is not preserved." }
        val promoted = candidateMetric > baselineMetric && promotionAllowed
        val reason = when {
            candidateMetric <= baselineMetric -> "Candidate does not beat the baseline."
            !promotionAllowed -> "Candidate beats baseline but promotion was not approved."
            else -> "Candidate promoted after approval."
        }
        val report = Report(baselineName, candidateName, baselineMetric, candidateMetric, promoted, reason)
        reports.add(report)
        return report
    }

    fun allReports(): List<Report> = reports.toList()
}
