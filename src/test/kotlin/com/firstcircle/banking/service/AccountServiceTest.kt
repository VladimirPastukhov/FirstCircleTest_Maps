package com.firstcircle.banking.service

import com.firstcircle.banking.domain.AccountAlreadyExistsException
import com.firstcircle.banking.domain.AccountNotFoundException
import com.firstcircle.banking.domain.InvalidHolderNameException
import com.firstcircle.banking.domain.Money
import com.firstcircle.banking.domain.of
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AccountServiceTest {

    private val service = AccountService()

    @Test
    fun `opened account has the initial deposit as balance`() {
        service.openAccount("Alice", Money.of("100.00"))

        assertEquals(Money.of("100.00"), service.getBalance("Alice"))
    }

    @Test
    fun `unknown holder is not found`() {
        val e = assertFailsWith<AccountNotFoundException> { service.getBalance("Nobody") }
        assertEquals("Nobody", e.holderName)
    }

    @Test
    fun `blank holder name is rejected`() {
        assertFailsWith<InvalidHolderNameException> { service.openAccount("", Money.of("1")) }
        assertFailsWith<InvalidHolderNameException> { service.openAccount("   ", Money.of("1")) }
    }

    @Test
    fun `names are compared exactly`() {
        service.openAccount("Alice", Money.of("10"))
        service.openAccount("alice", Money.of("20"))

        assertEquals(Money.of("10"), service.getBalance("Alice"))
        assertEquals(Money.of("20"), service.getBalance("alice"))
        assertFailsWith<AccountNotFoundException> { service.getBalance(" Alice") }
    }

    @Test
    fun `second open for the same name is rejected`() {
        service.openAccount("Alice", Money.of("100.00"))

        val e = assertFailsWith<AccountAlreadyExistsException> { service.openAccount("Alice", Money.of("5.00")) }
        assertEquals("Alice", e.holderName)
        assertEquals(Money.of("100.00"), service.getBalance("Alice"))
    }

    @Test
    fun `concurrent opens for the same name create exactly one account`() {
        val threads = 32
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(threads)
        try {
            val results = (1..threads).map { i ->
                pool.submit<Int?> {
                    start.await()
                    try {
                        service.openAccount("Alice", Money.of(i.toString()))
                        i
                    } catch (e: AccountAlreadyExistsException) {
                        null
                    }
                }
            }
            start.countDown()
            val winners = results.mapNotNull { it.get(10, TimeUnit.SECONDS) }

            assertEquals(1, winners.size)
            assertEquals(Money.of(winners.single().toString()), service.getBalance("Alice"))
        } finally {
            pool.shutdownNow()
        }
    }
}
