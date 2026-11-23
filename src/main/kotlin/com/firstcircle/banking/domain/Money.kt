package com.firstcircle.banking.domain

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * An amount in the single abstract currency.
 *
 * Always has exactly two decimal places and is never negative. Create it with [Money.of];
 * pass exact decimals such as `BigDecimal("0.10")`, never `BigDecimal(0.1)`.
 */
@JvmInline
value class Money private constructor(val amount: BigDecimal) : Comparable<Money> {

    init {
        check(amount.scale() == SCALE && amount.signum() >= 0) { "Money invariant broken: $amount" }
    }

    operator fun plus(other: Money): Money = of(amount + other.amount)

    operator fun minus(other: Money): Money = of(amount - other.amount)

    override fun compareTo(other: Money): Int = amount.compareTo(other.amount)

    override fun toString(): String = amount.toPlainString()

    companion object {
        private const val SCALE = 2

        /**
         * Returns [amount] as Money with exactly two decimal places.
         *
         * @throws InvalidAmountException if [amount] is negative or needs more than two decimal places.
         */
        fun of(amount: BigDecimal): Money {
            if (amount.signum() < 0) throw InvalidAmountException("Money can't be negative: ${amount.toPlainString()}")
            val scaled = try {
                amount.setScale(SCALE, RoundingMode.UNNECESSARY)
            } catch (e: ArithmeticException) {
                throw InvalidAmountException("Money can't have more than $SCALE decimal places: ${amount.toPlainString()}")
            }
            return Money(scaled)
        }

        val ZERO: Money = of(BigDecimal.ZERO)
    }
}
