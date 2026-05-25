package com.ghalbitnet.meshx2.token

object RewardEngine {

    private val balances =
        mutableMapOf<String,Double>()

    fun reward(
        node: String,
        amount: Double
    ) {

        val old =
            balances[node] ?: 0.0

        balances[node] =
            old + amount
    }

    fun getBalance(
        node: String
    ): Double {

        return balances[node] ?: 0.0
    }
}