package com.ghalbitnet.meshx2.blockchain

import android.content.Context

/**
 * =========================================================
 * GHALBIT MESH X2
 * BLOCKCHAIN LEDGER
 * =========================================================
 *
 * Ledger utama blockchain lokal.
 *
 * Bertugas untuk:
 *
 * - mengelola block
 * - menyimpan blockchain lokal
 * - membuat genesis block
 * - membuat block baru
 * - mining sederhana
 * - validasi blockchain
 * - menghitung reward
 * - menghitung balance
 * - sinkronisasi database blockchain
 *
 * =========================================================
 * FUTURE ROADMAP
 * =========================================================
 *
 * TODO:
 *
 * - Proof Of Connectivity
 * - Reward mesh node
 * - Smart contract
 * - Validator consensus
 * - AI routing reputation
 * - Distributed transaction
 * - UTXO balance
 * - Hybrid PoW + PoS
 * - Mesh synchronization
 * - Compression sync
 * - Quantum-resistant signature
 *
 * =========================================================
 */

class BlockchainLedger private constructor(
    context: Context
) {

    /**
     * =====================================================
     * DATABASE
     * =====================================================
     */

    private val db =
        BlockDatabase.getInstance(context)

    /**
     * =====================================================
     * DAO
     * =====================================================
     */

    private val dao =
        db.blockDao()

    /**
     * =====================================================
     * SINGLETON
     * =====================================================
     */

    companion object {

        @Volatile
        private var INSTANCE: BlockchainLedger? = null

        fun getInstance(
            context: Context
        ): BlockchainLedger {

            return INSTANCE ?: synchronized(this) {

                val instance =
                    BlockchainLedger(context)

                INSTANCE = instance

                instance
            }
        }
    }

    /**
     * =====================================================
     * ADD BLOCK
     * =====================================================
     */

    suspend fun addBlock(
        block: BlockEntity
    ) {

        dao.insert(block)
    }

    /**
     * =====================================================
     * GET ALL BLOCKS
     * =====================================================
     */

    suspend fun getBlocks(): List<BlockEntity> {

        return dao.getAll()
    }

    /**
     * =====================================================
     * GET LATEST BLOCK
     * =====================================================
     */

    suspend fun getLatestBlock(): BlockEntity? {

        return dao.getLatest()
    }

    /**
     * =====================================================
     * GET BALANCE
     * =====================================================
     *
     * Menghitung reward mining sederhana.
     *
     * =====================================================
     */

    suspend fun getBalance(
        walletAddress: String
    ): Double {

        val blocks =
            dao.getAll()

        var balance = 0.0

        blocks.forEach { block ->

            if (
                block.minerAddress ==
                walletAddress
            ) {

                balance += block.reward
            }
        }

        return balance
    }

    /**
     * =====================================================
     * VALIDATE BLOCKCHAIN
     * =====================================================
     */

    suspend fun isBlockchainValid(): Boolean {

        val blocks =
            dao.getAll()

        if (blocks.isEmpty()) {
            return true
        }

        for (i in 1 until blocks.size) {

            val current =
                blocks[i]

            val previous =
                blocks[i - 1]

            /**
             * Cek previous hash
             */

            if (
                current.previousHash !=
                previous.hash
            ) {

                return false
            }

            /**
             * Recalculate hash
             */

            val rawData =
                "${current.blockNumber}" +
                current.previousHash +
                current.data +
                current.timestamp +
                current.nonce

            val recalculatedHash =
                HashUtil.sha256(rawData)

            /**
             * Hash mismatch
             */

            if (
                current.hash !=
                recalculatedHash
            ) {

                return false
            }
        }

        return true
    }

    /**
     * =====================================================
     * CREATE GENESIS BLOCK
     * =====================================================
     */

    suspend fun createGenesisBlock() {

        val latest =
            dao.getLatest()

        /**
         * Genesis sudah ada
         */

        if (latest != null) {
            return
        }

        val timestamp =
            System.currentTimeMillis()

        val rawData =
            "0" +
            "0" +
            "Genesis Block" +
            timestamp +
            "0"

        val genesisHash =
            HashUtil.sha256(rawData)

        val genesis = BlockEntity(

            blockNumber = 0,

            hash = genesisHash,

            previousHash = "0",

            timestamp = timestamp,

            data = "Genesis Block",

            nonce = 0,

            minerAddress = "GENESIS",

            reward = 0.0
        )

        dao.insert(genesis)
    }

    /**
     * =====================================================
     * GENERATE NEXT BLOCK
     * =====================================================
     *
     * Simple Proof Of Work
     *
     * =====================================================
     */

    suspend fun generateNextBlock(
        data: String,
        minerAddress: String
    ): BlockEntity {

        val latest =
            dao.getLatest()

        val nextNumber =
            (latest?.blockNumber ?: 0) + 1

        val previousHash =
            latest?.hash ?: "0"

        val timestamp =
            System.currentTimeMillis()

        /**
         * SIMPLE MINING
         */

        var nonce = 0L

        var hash: String

        do {

            nonce++

            val rawData =
                "$nextNumber" +
                previousHash +
                data +
                timestamp +
                nonce

            hash =
                HashUtil.sha256(rawData)

        } while (
            !hash.startsWith("000")
        )

        /**
         * Mining reward
         */

        val reward = 1.0

        val block = BlockEntity(

            blockNumber = nextNumber,

            hash = hash,

            previousHash = previousHash,

            timestamp = timestamp,

            data = data,

            nonce = nonce,

            minerAddress = minerAddress,

            reward = reward
        )

        dao.insert(block)

        return block
    }

    /**
     * =====================================================
     * CLEAR BLOCKCHAIN
     * =====================================================
     *
     * DEVELOPMENT ONLY
     *
     * =====================================================
     */

    suspend fun clearBlockchain() {

        /**
         * TODO FUTURE:
         * Tambahkan DAO deleteAll()
         */
    }
}