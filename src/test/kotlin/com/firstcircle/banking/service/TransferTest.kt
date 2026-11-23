package com.firstcircle.banking.service

import com.firstcircle.banking.domain.AccountNotFoundException
import com.firstcircle.banking.domain.InsufficientFundsException
import com.firstcircle.banking.domain.Money
import com.firstcircle.banking.domain.SameAccountTransferException
import com.firstcircle.banking.domain.of
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class TransferTest {

    private val service = AccountService()

    @Test
    fun `transfer moves money between accounts`() {
        service.openAccount("Alice", Money.of("100.00"))
        service.openAccount("Bob", Money.of("20.00"))

        service.transfer(UUID.randomUUID(), "Alice", "Bob", Money.of("30.00"))

        assertEquals(Money.of("70.00"), service.getBalance("Alice"))
        assertEquals(Money.of("50.00"), service.getBalance("Bob"))
    }

    @Test
    fun `transfer larger than the sender's balance changes nothing`() {
        service.openAccount("Alice", Money.of("10.00"))
        service.openAccount("Bob", Money.of("20.00"))

        val e = assertFailsWith<InsufficientFundsException> {
            service.transfer(UUID.randomUUID(), "Alice", "Bob", Money.of("10.01"))
        }
        assertEquals("Alice", e.holderName)
        assertEquals(Money.of("10.00"), service.getBalance("Alice"))
        assertEquals(Money.of("20.00"), service.getBalance("Bob"))
    }

    @Test
    fun `transfer to an unknown receiver changes nothing`() {
        service.openAccount("Alice", Money.of("100.00"))

        val e = assertFailsWith<AccountNotFoundException> {
            service.transfer(UUID.randomUUID(), "Alice", "Nobody", Money.of("10.00"))
        }
        assertEquals("Nobody", e.holderName)
        assertEquals(Money.of("100.00"), service.getBalance("Alice"))
    }

    @Test
    fun `transfer to the same account is rejected`() {
        service.openAccount("Alice", Money.of("100.00"))

        assertFailsWith<SameAccountTransferException> {
            service.transfer(UUID.randomUUID(), "Alice", "Alice", Money.of("10.00"))
        }
        assertEquals(Money.of("100.00"), service.getBalance("Alice"))
    }

    @Test
    fun `same transfer sent twice is applied once`() {
        service.openAccount("Alice", Money.of("100.00"))
        service.openAccount("Bob", Money.of("0"))
        val opId = UUID.randomUUID()

        service.transfer(opId, "Alice", "Bob", Money.of("30.00"))
        service.transfer(opId, "Alice", "Bob", Money.of("30.00"))

        assertEquals(Money.of("70.00"), service.getBalance("Alice"))
        assertEquals(Money.of("30.00"), service.getBalance("Bob"))
    }

    @Test
    fun `opposite transfers at the same time never deadlock`() {
        service.openAccount("Alice", Money.of("1000.00"))
        service.openAccount("Bob", Money.of("1000.00"))
        val threads = 100
        val start = CountDownLatch(1)
        // Daemon threads, so a deadlock fails this test instead of keeping the test JVM alive.
        val pool = Executors.newFixedThreadPool(threads) { r -> Thread(r).apply { isDaemon = true } }
        try {
            val results = (1..threads).map { i ->
                val (from, to) = if (i % 2 == 0) "Alice" to "Bob" else "Bob" to "Alice"
                pool.submit {
                    start.await()
                    service.transfer(UUID.randomUUID(), from, to, Money.of("1.00"))
                }
            }
            start.countDown()
            results.forEach { it.get(10, TimeUnit.SECONDS) }

            assertEquals(Money.of("1000.00"), service.getBalance("Alice"))
            assertEquals(Money.of("1000.00"), service.getBalance("Bob"))
        } finally {
            pool.shutdownNow()
        }
    }
}
