package com.firstcircle.banking.service

import com.firstcircle.banking.domain.AccountNotFoundException
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

class DepositTest {

    private val service = AccountService()

    @Test
    fun `deposit adds to the balance`() {
        service.openAccount("Alice", Money.of("100.00"))

        service.deposit(UUID.randomUUID(), "Alice", Money.of("25.50"))

        assertEquals(Money.of("125.50"), service.getBalance("Alice"))
    }

    @Test
    fun `zero deposit is rejected`() {
        service.openAccount("Alice", Money.of("100.00"))

        assertFailsWith<InvalidAmountException> { service.deposit(UUID.randomUUID(), "Alice", Money.of("0")) }
        assertEquals(Money.of("100.00"), service.getBalance("Alice"))
    }

    @Test
    fun `deposit into unknown holder is rejected`() {
        val e = assertFailsWith<AccountNotFoundException> {
            service.deposit(UUID.randomUUID(), "Nobody", Money.of("1"))
        }
        assertEquals("Nobody", e.holderName)
    }

    @Test
    fun `concurrent deposits are all applied`() {
        service.openAccount("Alice", Money.of("0"))
        val threads = 100
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(threads)
        try {
            val results = (1..threads).map {
                pool.submit {
                    start.await()
                    service.deposit(UUID.randomUUID(), "Alice", Money.of("1.00"))
                }
            }
            start.countDown()
            results.forEach { it.get(10, TimeUnit.SECONDS) }

            assertEquals(Money.of("100.00"), service.getBalance("Alice"))
        } finally {
            pool.shutdownNow()
        }
    }
}
