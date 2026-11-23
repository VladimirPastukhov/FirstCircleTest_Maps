package com.firstcircle.banking.domain

/**
 * One deposit, withdrawal or transfer requested by a caller.
 *
 * Recorded against its opId once applied. Two requests are the same operation when they are equal:
 * same type, holder names and amount.
 */
sealed interface Operation {

    data class Deposit(val holderName: String, val amount: Money) : Operation

    data class Withdrawal(val holderName: String, val amount: Money) : Operation

    data class Transfer(val fromHolderName: String, val toHolderName: String, val amount: Money) : Operation
}
