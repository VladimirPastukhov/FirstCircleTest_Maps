package com.firstcircle.banking.service

import com.firstcircle.banking.domain.InsufficientFundsException
import com.firstcircle.banking.domain.InvalidAmountException
import com.firstcircle.banking.domain.Money
import com.firstcircle.banking.domain.of
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WithdrawalTest {

    private val service = AccountService()

    @Test
    fun `withdrawal subtracts from the balance`() {
        service.openAccount("Alice", Money.of("100.00"))

        service.withdraw(UUID.randomUUID(), "Alice", Money.of("30.25"))

        assertEquals(Money.of("69.75"), service.getBalance("Alice"))
    }

    @Test
    fun `withdrawing more than the balance is rejected`() {
        service.openAccount("Alice", Money.of("100.00"))

        val e = assertFailsWith<InsufficientFundsException> {
            service.withdraw(UUID.randomUUID(), "Alice", Money.of("100.01"))
        }
        assertEquals("Alice", e.holderName)
        assertEquals(Money.of("100.00"), service.getBalance("Alice"))
    }

    @Test
    fun `zero withdrawal is rejected`() {
        service.openAccount("Alice", Money.of("100.00"))

        assertFailsWith<InvalidAmountException> { service.withdraw(UUID.randomUUID(), "Alice", Money.ZERO) }
    }

    @Test
    fun `concurrent withdrawals never overdraw`() {
        service.openAccount("Alice", Money.of("50.00"))
        val threads = 100
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(threads)
        try {
            val results = (1..threads).map {
                pool.submit<Boolean> {
                    start.await()
                    try {
                        service.withdraw(UUID.randomUUID(), "Alice", Money.of("1.00"))
                        true
                    } catch (e: InsufficientFundsException) {
                        false
                    }
                }
            }
            start.countDown()
            val succeeded = results.count { it.get(10, TimeUnit.SECONDS) }

            assertEquals(50, succeeded)
            assertEquals(Money.ZERO, service.getBalance("Alice"))
        } finally {
            pool.shutdownNow()
        }
    }
}
