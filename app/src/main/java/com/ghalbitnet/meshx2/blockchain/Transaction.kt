package com.ghalbitnet.meshx2.blockchain

/**
 * =========================================================
 * GHALBIT TRANSACTION
 * =========================================================
 *
 * Transaksi blockchain mesh.
 *
 * =========================================================
 * FUTURE ROADMAP
 * =========================================================
 *
 * // TODO FUTURE:
 *
 * Tambahkan:
 *
 * - txHash
 * - signature
 * - gasFee
 * - smartContractCall
 * - nftData
 * - qosReward
 *
 * =========================================================
 */

data class Transaction(

    val type: String,

    val toIp: String,

    val amount: Double
)
