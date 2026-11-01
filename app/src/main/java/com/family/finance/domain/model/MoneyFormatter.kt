package com.family.finance.domain.model

/**
 * Display formatting for [Money], kept apart from the value type so arithmetic never
 * depends on presentation. Uses the ₹ symbol and Indian digit grouping (1,23,45,678.90),
 * which is the same in Tamil and English (PRD 7.6).
 */
object MoneyFormatter {

    const val RUPEE_SYMBOL = "₹"

    fun format(money: Money): String {
        val negative = money.isNegative
        // Work in the unsigned domain via Long.MIN_VALUE-safe division.
        val rupees = Math.abs(money.paise / Money.PAISE_PER_RUPEE)
        val paise = Math.abs(money.paise % Money.PAISE_PER_RUPEE)
        val sign = if (negative) "-" else ""
        return "$sign$RUPEE_SYMBOL${groupIndian(rupees)}.${paise.toString().padStart(2, '0')}"
    }

    private fun groupIndian(value: Long): String {
        val digits = value.toString()
        if (digits.length <= 3) return digits
        val lastThree = digits.takeLast(3)
        val rest = digits.dropLast(3)
        val restGrouped = rest.reversed().chunked(2).joinToString(",").reversed()
        return "$restGrouped,$lastThree"
    }
}
