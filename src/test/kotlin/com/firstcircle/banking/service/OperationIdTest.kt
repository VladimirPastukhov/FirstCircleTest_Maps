package com.firstcircle.banking.service

import com.firstcircle.banking.domain.AccountNotFoundException
import com.firstcircle.banking.domain.InsufficientFundsException
import com.firstcircle.banking.domain.Money
import com.firstcircle.banking.domain.Operation
import com.firstcircle.banking.domain.OperationIdConflictException
import com.firstcircle.banking.domain.of
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OperationIdTest {

    private val service = AccountService()
    private val opId = UUID.randomUUID()

    @Test
    fun `same deposit sent twice is applied once`() {
        service.openAccount("Alice", Money.of("100.00"))

        service.deposit(opId, "Alice", Money.of("10.00"))
        service.deposit(opId, "Alice", Money.of("10.00"))

        assertEquals(Money.of("110.00"), service.getBalance("Alice"))
    }

    @Test
    fun `same amount written with fewer decimals is the same request`() {
        service.openAccount("Alice", Money.of("0"))

        service.deposit(opId, "Alice", Money.of("10"))
        service.deposit(opId, "Alice", Money.of("10.00"))

        assertEquals(Money.of("10.00"), service.getBalance("Alice"))
    }

    @Test
    fun `same opId with a different amount is a conflict`() {
        service.openAccount("Alice", Money.of("0"))
        service.deposit(opId, "Alice", Money.of("10.00"))

        val e = assertFailsWith<OperationIdConflictException> { service.deposit(opId, "Alice", Money.of("20.00")) }
        assertEquals(opId, e.opId)
        assertEquals(Operation.Deposit("Alice", Money.of("10.00")), e.original)
        assertEquals(Operation.Deposit("Alice", Money.of("20.00")), e.requested)
        assertEquals(Money.of("10.00"), service.getBalance("Alice"))
    }

    @Test
    fun `same opId with a different holder is a conflict`() {
        service.openAccount("Alice", Money.of("0"))
        service.openAccount("Bob", Money.of("0"))
        service.deposit(opId, "Alice", Money.of("10.00"))

        assertFailsWith<OperationIdConflictException> { service.deposit(opId, "Bob", Money.of("10.00")) }
        assertEquals(Money.of("0"), service.getBalance("Bob"))
    }

    @Test
    fun `failed deposit can be retried with the same opId`() {
        assertFailsWith<AccountNotFoundException> { service.deposit(opId, "Alice", Money.of("10.00")) }
        service.openAccount("Alice", Money.of("0"))

        service.deposit(opId, "Alice", Money.of("10.00"))

        assertEquals(Money.of("10.00"), service.getBalance("Alice"))
    }

    @Test
    fun `repeated withdrawal that emptied the account is a no-op`() {
        service.openAccount("Alice", Money.of("100.00"))

        service.withdraw(opId, "Alice", Money.of("100.00"))
        service.withdraw(opId, "Alice", Money.of("100.00"))

        assertEquals(Money.ZERO, service.getBalance("Alice"))
    }

    @Test
    fun `withdrawal rejected for insufficient funds stays rejected on retry`() {
        service.openAccount("Alice", Money.of("10.00"))
        assertFailsWith<InsufficientFundsException> { service.withdraw(opId, "Alice", Money.of("50.00")) }
        service.deposit(UUID.randomUUID(), "Alice", Money.of("100.00"))

        val e = assertFailsWith<InsufficientFundsException> { service.withdraw(opId, "Alice", Money.of("50.00")) }
        assertEquals("Alice", e.holderName)
        assertEquals(Money.of("110.00"), service.getBalance("Alice"))
    }

    @Test
    fun `transfer rejected for insufficient funds stays rejected on retry`() {
        service.openAccount("Alice", Money.of("10.00"))
        service.openAccount("Bob", Money.of("0"))
        assertFailsWith<InsufficientFundsException> { service.transfer(opId, "Alice", "Bob", Money.of("50.00")) }
        service.deposit(UUID.randomUUID(), "Alice", Money.of("100.00"))

        assertFailsWith<InsufficientFundsException> { service.transfer(opId, "Alice", "Bob", Money.of("50.00")) }
        assertEquals(Money.of("110.00"), service.getBalance("Alice"))
        assertEquals(Money.of("0.00"), service.getBalance("Bob"))
    }

    @Test
    fun `different request reusing a rejected opId is a conflict`() {
        service.openAccount("Alice", Money.of("10.00"))
        assertFailsWith<InsufficientFundsException> { service.withdraw(opId, "Alice", Money.of("50.00")) }

        val e = assertFailsWith<OperationIdConflictException> { service.withdraw(opId, "Alice", Money.of("5.00")) }
        assertEquals(Operation.Withdrawal("Alice", Money.of("50.00")), e.original)
        assertEquals(Money.of("10.00"), service.getBalance("Alice"))
    }

    @Test
    fun `withdrawal reusing a deposit's opId is a conflict`() {
        service.openAccount("Alice", Money.of("0"))
        service.deposit(opId, "Alice", Money.of("10.00"))

        assertFailsWith<OperationIdConflictException> { service.withdraw(opId, "Alice", Money.of("10.00")) }
        assertEquals(Money.of("10.00"), service.getBalance("Alice"))
    }

    @Test
    fun `reused opId for an unknown holder is not found`() {
        service.openAccount("Alice", Money.of("0"))
        service.deposit(opId, "Alice", Money.of("10.00"))

        assertFailsWith<AccountNotFoundException> { service.deposit(opId, "Nobody", Money.of("10.00")) }
    }

    @Test
    fun `same deposit sent concurrently is applied once`() {
        service.openAccount("Alice", Money.of("0"))
        val threads = 32
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(threads)
        try {
            val results = (1..threads).map {
                pool.submit {
                    start.await()
                    service.deposit(opId, "Alice", Money.of("10.00"))
                }
            }
            start.countDown()
            results.forEach { it.get(10, TimeUnit.SECONDS) }

            assertEquals(Money.of("10.00"), service.getBalance("Alice"))
        } finally {
            pool.shutdownNow()
        }
    }
}
