package com.pgcore.core.domain.payment.vo

import com.pgcore.core.domain.exception.MoneyException
import com.pgcore.core.exception.BusinessException
import jakarta.persistence.Column
import jakarta.persistence.Embeddable

@Embeddable
class Money(
    @Column(nullable = false)
    val amount: Long,
): Comparable<Money> {
    init {
        if(amount < 0) throw BusinessException(MoneyException.NOT_ALLOW_NEGATIVE_VALUE)
    }

    operator fun plus(other: Money): Money =
        Money(this.amount + other.amount)

    operator fun minus(other: Money): Money {
        val result = this.amount - other.amount

        if(result < 0) throw BusinessException(MoneyException.NOT_ALLOW_NEGATIVE_VALUE)
        return Money(result)
    }

    override operator fun compareTo(other: Money): Int {
        return this.amount.compareTo(other.amount)
    }

    fun isZero(): Boolean = amount == 0L

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Money

        return amount == other.amount
    }

    override fun hashCode(): Int {
        return amount.hashCode()
    }
}
