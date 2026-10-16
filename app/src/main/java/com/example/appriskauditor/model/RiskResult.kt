package com.example.appriskauditor.model

/**
 * Band thresholds for the 0-100 risk score.
 */
enum class RiskBand {
    LOW,      // 0-24
    MEDIUM,   // 25-49
    HIGH,     // 50-74
    CRITICAL  // 75-100
}

/**
 * The output of [com.example.appriskauditor.engine.RiskEngine] for one app.
 *
 * @param score   0-100 (capped)
 * @param band    LOW / MEDIUM / HIGH / CRITICAL
 * @param reasons plain-language list; High/Critical must have ≥3
 */
data class RiskResult(
    val score: Int,
    val band: RiskBand,
    val reasons: List<String>
) {
    companion object {
        fun bandFor(score: Int): RiskBand = when {
            score >= 75 -> RiskBand.CRITICAL
            score >= 50 -> RiskBand.HIGH
            score >= 25 -> RiskBand.MEDIUM
            else        -> RiskBand.LOW
        }
    }
}
