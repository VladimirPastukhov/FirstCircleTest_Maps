package com.firstcircle.banking.domain

import java.math.BigDecimal

/** Test-only shortcut so tests can write `Money.of("10.50")`. Production code has no text entry point. */
fun Money.Companion.of(value: String): Money = Money.of(BigDecimal(value))
