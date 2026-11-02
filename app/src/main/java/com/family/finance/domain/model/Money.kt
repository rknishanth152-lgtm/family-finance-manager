package com.family.finance.domain.model

/**
 * An exact INR amount in paise (1 rupee = 100 paise). Never use Float or Double for money.
 * Arithmetic throws [ArithmeticException] on overflow instead of wrapping silently.
 */
@JvmInline
value class Money(val paise: Long) : Comparable<Money> {

    operator fun plus(other: Money): Money = Money(Math.addExact(paise, other.paise))

    operator fun minus(other: Money): Money = Money(Math.subtractExact(paise, other.paise))

    operator fun unaryMinus(): Money = Money(Math.negateExact(paise))

    val isPositive: Boolean get() = paise > 0
    val isZero: Boolean get() = paise == 0L
    val isNegative: Boolean get() = paise < 0

    /** Within the per-entry limit [MAX_AMOUNT_PAISE] in either direction. */
    val isWithinLimit: Boolean get() = paise in -MAX_AMOUNT_PAISE..MAX_AMOUNT_PAISE

    override fun compareTo(other: Money): Int = paise.compareTo(other.paise)

    companion object {
        const val PAISE_PER_RUPEE = 100L

        /**
         * The largest single amount or opening balance (either sign): Rs 10,000 crore. Far above
         * any family figure, and low enough that hundreds of thousands of capped entries still
         * add up without overflowing a Long. Enforced in the rules, forms, decoding and backups.
         */
        const val MAX_AMOUNT_PAISE = 10_000_000_000_000L

        val ZERO = Money(0)

        fun ofRupees(rupees: Long): Money = Money(Math.multiplyExact(rupees, PAISE_PER_RUPEE))

        private val AMOUNT_PATTERN = Regex("""-?\d+(\.\d+)?""")

        /**
         * Parses user input such as "1,250.50" or "20000".
         *
         * Rounding policy: amounts are never rounded. Input with non-zero digits beyond
         * two decimal places (e.g. "10.005") is rejected and returns null, as does any
         * malformed or out-of-range input.
         */
        fun parse(input: String): Money? {
            val cleaned = input.trim().replace(",", "")
            if (!AMOUNT_PATTERN.matches(cleaned)) return null

            val negative = cleaned.startsWith("-")
            val unsigned = cleaned.removePrefix("-")
            val rupeePart = unsigned.substringBefore('.')
            val fractionPart = unsigned.substringAfter('.', missingDelimiterValue = "").trimEnd('0')
            if (fractionPart.length > 2) return null

            return try {
                val rupees = rupeePart.toLong()
                val fraction = fractionPart.padEnd(2, '0').toLong()
                val paise = Math.addExact(Math.multiplyExact(rupees, PAISE_PER_RUPEE), fraction)
                Money(if (negative) -paise else paise)
            } catch (e: NumberFormatException) {
                null
            } catch (e: ArithmeticException) {
                null
            }
        }
    }
}

fun Iterable<Money>.sum(): Money = fold(Money.ZERO) { acc, m -> acc + m }

inline fun <T> Iterable<T>.sumOfMoney(selector: (T) -> Money): Money =
    fold(Money.ZERO) { acc, item -> acc + selector(item) }
