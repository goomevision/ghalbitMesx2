package com.ghalbitnet.meshx2.blockchain

/**
 * =========================================================
 * GHALBIT BLOCK
 * =========================================================
 *
 * Representasi block blockchain mesh.
 *
 * =========================================================
 * FUTURE ROADMAP
 * =========================================================
 *
 * // TODO FUTURE:
 *
 * Tambahkan:
 *
 * - merkleRoot
 * - validatorSignature
 * - aiConsensusScore
 * - compressedPayload
 * - smartContractResult
 * - routeProof
 *
 * =========================================================
 */

data class Block(

    val blockNumber: Long,

    val previousHash: String,

    val timestamp: Long,

    val proposerIp: String,

    val proposerPubKey: String,

    val transactionsJson: String,

    val signature: String
)
