package com.satnamapps.shapecenterfinder

class DetectionSmoother(
    private val windowSize: Int = 7,
    private val requiredAgreement: Int = 3
) {
    private val recent = ArrayDeque<String?>()
    private var stable: String? = null

    fun stabilizeName(name: String?): String? {
        recent.addLast(name)
        if (recent.size > windowSize) recent.removeFirst()

        val counts = recent.filterNotNull().groupingBy { it }.eachCount()
        val best = counts.maxByOrNull { it.value }
        if (best != null && best.value >= requiredAgreement) {
            stable = best.key
        } else if (recent.count { it == null } > windowSize / 2) {
            stable = null
        }
        return name ?: stable
    }

    fun reset() {
        recent.clear()
        stable = null
    }
}
