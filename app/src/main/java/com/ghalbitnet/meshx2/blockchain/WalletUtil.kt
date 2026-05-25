package com.ghalbitnet.meshx2.blockchain

import java.util.UUID

object WalletUtil {

    fun generateWalletAddress(): String {

        return "GHB-" +
            UUID.randomUUID()
                .toString()
                .replace("-", "")
                .take(32)
    }
}
