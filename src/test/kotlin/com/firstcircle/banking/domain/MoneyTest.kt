package com.firstcircle.banking.domain

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MoneyTest {

    @Test
    fun `amount always has two decimal places`() {
        assertEquals(BigDecimal("10.00"), Money.of("10").amount)
        assertEquals(BigDecimal("10.50"), Money.of("10.500").amount)
    }

    @Test
    fun `more than two decimal places is rejected, not rounded`() {
        assertFailsWith<InvalidAmountException> { Money.of("10.005") }
    }

    @Test
    fun `negative amount is rejected`() {
        assertFailsWith<InvalidAmountException> { Money.of("-0.01") }
    }

    @Test
    fun `equal by value regardless of input scale`() {
        assertEquals(Money.of("10"), Money.of("10.00"))
        assertEquals(Money.of("10").hashCode(), Money.of("10.00").hashCode())
    }

    @Test
    fun `text form has two decimals and no exponent`() {
        assertEquals("1000.00", Money.of("1E+3").toString())
    }

    @Test
    fun `subtracting down to zero is allowed`() {
        assertEquals(Money.of("0.00"), Money.of("10.00") - Money.of("10.00"))
    }

    @Test
    fun `subtracting below zero is rejected`() {
        assertFailsWith<InvalidAmountException> { Money.of("10.00") - Money.of("10.01") }
    }
}
