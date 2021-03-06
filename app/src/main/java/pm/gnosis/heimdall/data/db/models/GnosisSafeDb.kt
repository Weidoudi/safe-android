package pm.gnosis.heimdall.data.db.models

import android.arch.persistence.room.ColumnInfo
import android.arch.persistence.room.Entity
import android.arch.persistence.room.PrimaryKey
import pm.gnosis.heimdall.data.repositories.TransactionExecutionRepository
import pm.gnosis.heimdall.data.repositories.TransactionExecutionRepository.Operation.Companion.fromInt
import pm.gnosis.heimdall.data.repositories.models.PendingSafe
import pm.gnosis.heimdall.data.repositories.models.RecoveringSafe
import pm.gnosis.heimdall.data.repositories.models.Safe
import pm.gnosis.heimdall.data.repositories.toInt
import pm.gnosis.model.Solidity
import pm.gnosis.models.Wei
import pm.gnosis.svalinn.accounts.base.models.Signature
import java.math.BigInteger

@Entity(tableName = GnosisSafeDb.TABLE_NAME)
data class GnosisSafeDb(
    @PrimaryKey
    @ColumnInfo(name = COL_ADDRESS)
    var address: Solidity.Address,

    @ColumnInfo(name = COL_NAME)
    var name: String?
) {
    companion object {
        const val TABLE_NAME = "gnosis_safes"
        const val COL_ADDRESS = "address"
        const val COL_NAME = "name"
    }
}

fun Safe.toDb() = GnosisSafeDb(address, name)
fun GnosisSafeDb.fromDb() = Safe(address, name)

@Entity(tableName = PendingGnosisSafeDb.TABLE_NAME)
data class PendingGnosisSafeDb(
    @PrimaryKey
    @ColumnInfo(name = COL_ADDRESS)
    var address: Solidity.Address,

    @ColumnInfo(name = COL_TX_HASH)
    var transactionHash: BigInteger,

    @ColumnInfo(name = COL_NAME)
    var name: String?,

    @ColumnInfo(name = COL_PAYMENT_TOKEN)
    var paymentToken: Solidity.Address,

    @ColumnInfo(name = COL_PAYMENT_AMOUNT)
    var paymentAmount: BigInteger,

    @ColumnInfo(name = COL_IS_FUNDED)
    var isFunded: Boolean = false
) {
    companion object {
        const val TABLE_NAME = "gnosis_pending_safes"
        const val COL_ADDRESS = "address"
        const val COL_TX_HASH = "transactionHash"
        const val COL_NAME = "name"
        const val COL_PAYMENT_AMOUNT = "paymentAmount"
        const val COL_PAYMENT_TOKEN = "paymentToken"
        const val COL_IS_FUNDED = "funded"
    }
}

fun PendingSafe.toDb() = PendingGnosisSafeDb(address, hash, name, paymentToken, paymentAmount, isFunded)
fun PendingGnosisSafeDb.fromDb() = PendingSafe(address, transactionHash, name, paymentToken, paymentAmount, isFunded)

@Entity(tableName = RecoveringGnosisSafeDb.TABLE_NAME)
data class RecoveringGnosisSafeDb(

    @PrimaryKey
    @ColumnInfo(name = COL_ADDRESS)
    var address: Solidity.Address,

    @ColumnInfo(name = COL_TX_HASH)
    var transactionHash: BigInteger?,

    @ColumnInfo(name = COL_NAME)
    var name: String?,

    @ColumnInfo(name = COL_RECOVERER)
    var recoverer: Solidity.Address,

    @ColumnInfo(name = COL_DATA)
    var data: String,

    @ColumnInfo(name = COL_NONCE)
    var nonce: BigInteger,

    @ColumnInfo(name = COL_TX_GAS)
    var txGas: BigInteger,

    @ColumnInfo(name = COL_DATA_GAS)
    var dataGas: BigInteger,

    @ColumnInfo(name = COL_GAS_TOKEN)
    var gasToken: Solidity.Address,

    @ColumnInfo(name = COL_GAS_PRICE)
    var gasPrice: BigInteger,

    @ColumnInfo(name = COL_OPERATION)
    var operation: Int,

    @ColumnInfo(name = COL_SIGNATURES)
    var signatures: String
) {
    companion object {
        const val TABLE_NAME = "gnosis_recovering_safes"
        const val COL_ADDRESS = "address"
        const val COL_TX_HASH = "transactionHash"
        const val COL_NAME = "name"
        const val COL_RECOVERER = "recoverer"
        const val COL_DATA = "data"
        const val COL_TX_GAS = "txGas"
        const val COL_DATA_GAS = "dataGas"
        const val COL_GAS_TOKEN = "gasToken"
        const val COL_GAS_PRICE = "gasPrice"
        const val COL_NONCE = "nonce"
        const val COL_OPERATION = "operation"
        const val COL_SIGNATURES = "signatures"
    }
}

fun RecoveringSafe.toDb() =
    RecoveringGnosisSafeDb(
        address,
        transactionHash,
        name,
        recoverer,
        data,
        nonce,
        txGas,
        dataGas,
        gasToken,
        gasPrice,
        operation.toInt(),
        signatures.joinToString(",") { it.toString() })

fun RecoveringGnosisSafeDb.fromDb() =
    RecoveringSafe(
        address,
        transactionHash,
        name,
        recoverer,
        data,
        txGas,
        dataGas,
        gasToken,
        gasPrice,
        nonce,
        fromInt(operation),
        signatures.split(",").map { Signature.from(it.trim()) })
