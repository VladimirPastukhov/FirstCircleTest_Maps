package com.firstcircle.banking.domain

import java.util.concurrent.locks.ReentrantLock

internal class Account(val holderName: String, initialBalance: Money) {

    val lock = ReentrantLock()

    @Volatile
    var balance: Money = initialBalance
}
