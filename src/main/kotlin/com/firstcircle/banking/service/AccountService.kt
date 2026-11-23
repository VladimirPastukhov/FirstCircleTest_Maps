package com.firstcircle.banking.service

import com.firstcircle.banking.domain.Account
import com.firstcircle.banking.domain.AccountAlreadyExistsException
import com.firstcircle.banking.domain.AccountNotFoundException
import com.firstcircle.banking.domain.InsufficientFundsException
import com.firstcircle.banking.domain.InvalidAmountException
import com.firstcircle.banking.domain.InvalidHolderNameException
import com.firstcircle.banking.domain.Money
import com.firstcircle.banking.domain.Operation
import com.firstcircle.banking.domain.OperationIdConflictException
import com.firstcircle.banking.domain.SameAccountTransferException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.withLock

class AccountService {

    private val accounts = ConcurrentHashMap<String, Account>()
    private val appliedOperations = ConcurrentHashMap<UUID, RecordedOperation>()

    fun openAccount(holderName: String, initialDeposit: Money) {
        requireValidHolderName(holderName)
        if (accounts.putIfAbsent(holderName, Account(holderName, initialDeposit)) != null) {
            throw AccountAlreadyExistsException(holderName)
        }
    }

    fun getBalance(holderName: String): Money {
        requireValidHolderName(holderName)
        return findAccount(holderName).balance
    }

    fun deposit(opId: UUID, holderName: String, amount: Money) {
        requireValidHolderName(holderName)
        requirePositive(amount)
        val operation = Operation.Deposit(holderName, amount)
        val account = findAccount(holderName)
        account.lock.withLock {
            if (replayIfRecorded(opId, operation)) return
            record(opId, operation)
            account.balance += amount
        }
    }

    fun withdraw(opId: UUID, holderName: String, amount: Money) {
        requireValidHolderName(holderName)
        requirePositive(amount)
        val operation = Operation.Withdrawal(holderName, amount)
        val account = findAccount(holderName)
        account.lock.withLock {
            if (replayIfRecorded(opId, operation)) return
            if (account.balance < amount) reject(opId, operation, InsufficientFundsException(holderName))
            record(opId, operation)
            account.balance -= amount
        }
    }

    fun transfer(opId: UUID, fromHolderName: String, toHolderName: String, amount: Money) {
        requireValidHolderName(fromHolderName)
        requireValidHolderName(toHolderName)
        requirePositive(amount)
        if (fromHolderName == toHolderName) throw SameAccountTransferException()
        val operation = Operation.Transfer(fromHolderName, toHolderName, amount)
        val from = findAccount(fromHolderName)
        val to = findAccount(toHolderName)
        withBothLocked(from, to) {
            if (replayIfRecorded(opId, operation)) return
            if (from.balance < amount) reject(opId, operation, InsufficientFundsException(fromHolderName))
            record(opId, operation)
            from.balance -= amount
            to.balance += amount
        }
    }

    // Locks in holder-name order whatever the transfer's direction, so two transfers can never
    // each hold one lock while waiting for the other (a deadlock).
    private inline fun withBothLocked(a: Account, b: Account, block: () -> Unit) {
        val (first, second) = if (a.holderName < b.holderName) a to b else b to a
        first.lock.withLock { second.lock.withLock(block) }
    }

    private fun replayIfRecorded(opId: UUID, operation: Operation): Boolean {
        val recorded = appliedOperations[opId] ?: return false
        if (recorded.operation != operation) throw OperationIdConflictException(opId, recorded.operation, operation)
        recorded.rejection?.let { throw it }
        return true
    }

    // Records the rejection so a retry with the same opId fails the same way, even after the balance
    // has grown enough for the operation to succeed.
    // Payment APIs like Stripe usually store the failure and replay it.
    private fun reject(opId: UUID, operation: Operation, rejection: InsufficientFundsException): Nothing {
        record(opId, operation, rejection)
        throw rejection
    }

    // putIfAbsent catches a different request with the same opId that runs at the same time under
    // other account locks.
    private fun record(opId: UUID, operation: Operation, rejection: InsufficientFundsException? = null) {
        val recorded = appliedOperations.putIfAbsent(opId, RecordedOperation(operation, rejection)) ?: return
        throw OperationIdConflictException(opId, recorded.operation, operation)
    }

    private fun findAccount(holderName: String): Account =
        accounts[holderName] ?: throw AccountNotFoundException(holderName)

    private fun requireValidHolderName(holderName: String) {
        if (holderName.isBlank()) throw InvalidHolderNameException()
    }

    private fun requirePositive(amount: Money) {
        if (amount <= Money.ZERO) throw InvalidAmountException("Amount must be greater than zero: $amount")
    }

    // An opId's request and how it ended: applied (rejection is null) or rejected for insufficient funds.
    private class RecordedOperation(val operation: Operation, val rejection: InsufficientFundsException?)
}
