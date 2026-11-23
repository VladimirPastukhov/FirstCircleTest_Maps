package com.firstcircle.banking.domain

import java.util.UUID

sealed class BankingException(message: String) : RuntimeException(message)

class InvalidAmountException(message: String) : BankingException(message)

class InvalidHolderNameException : BankingException("Holder name must not be blank")

class AccountAlreadyExistsException(val holderName: String) :
    BankingException("Holder '$holderName' already has an account")

class AccountNotFoundException(val holderName: String) : BankingException("No account for holder '$holderName'")

class InsufficientFundsException(val holderName: String) :
    BankingException("Holder '$holderName' has insufficient funds")

class SameAccountTransferException : BankingException("Can't transfer from an account to itself")

class OperationIdConflictException(val opId: UUID, val original: Operation, val requested: Operation) :
    BankingException("Operation ID $opId was already used for $original, so it can't be used for $requested")
