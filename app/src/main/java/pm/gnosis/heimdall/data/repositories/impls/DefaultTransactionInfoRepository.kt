package pm.gnosis.heimdall.data.repositories.impls

import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import pm.gnosis.heimdall.ERC20Contract
import pm.gnosis.heimdall.GnosisSafePersonalEdition
import pm.gnosis.heimdall.data.db.ApplicationDb
import pm.gnosis.heimdall.data.db.models.TransactionDescriptionDb
import pm.gnosis.heimdall.data.repositories.*
import pm.gnosis.heimdall.data.repositories.models.ERC20Token
import pm.gnosis.heimdall.data.repositories.models.SafeTransaction
import pm.gnosis.models.Transaction
import pm.gnosis.models.Wei
import pm.gnosis.utils.isSolidityMethod
import pm.gnosis.utils.removeHexPrefix
import pm.gnosis.utils.removeSolidityMethodPrefix
import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Singleton


@Singleton
class DefaultTransactionInfoRepository @Inject constructor(
    appDb: ApplicationDb
) : TransactionInfoRepository {

    private val descriptionsDao = appDb.descriptionsDao()

    override fun checkRestrictedTransaction(transaction: SafeTransaction): Single<SafeTransaction> =
        Single.fromCallable {
            when {
                transaction.operation == TransactionExecutionRepository.Operation.DELEGATE_CALL ->
                    throw RestrictedTransactionException.DelegateCall
                transaction.wrapped.data?.isSolidityMethod(GnosisSafePersonalEdition.AddOwnerWithThreshold.METHOD_ID) == true ->
                    throw RestrictedTransactionException.ModifyOwners
                transaction.wrapped.data?.isSolidityMethod(GnosisSafePersonalEdition.RemoveOwner.METHOD_ID) == true ->
                    throw RestrictedTransactionException.ModifyOwners
                transaction.wrapped.data?.isSolidityMethod(GnosisSafePersonalEdition.SwapOwner.METHOD_ID) == true ->
                    throw RestrictedTransactionException.ModifyOwners
                transaction.wrapped.data?.isSolidityMethod(GnosisSafePersonalEdition.EnableModule.METHOD_ID) == true ->
                    throw RestrictedTransactionException.ModifyModules
                transaction.wrapped.data?.isSolidityMethod(GnosisSafePersonalEdition.DisableModule.METHOD_ID) == true ->
                    throw RestrictedTransactionException.ModifyModules
                transaction.wrapped.data?.isSolidityMethod(GnosisSafePersonalEdition.ChangeThreshold.METHOD_ID) == true ->
                    throw RestrictedTransactionException.ChangeThreshold
                transaction.wrapped.data?.isSolidityMethod(GnosisSafePersonalEdition.ChangeMasterCopy.METHOD_ID) == true ->
                    throw RestrictedTransactionException.ChangeMasterCopy
            }
            transaction
        }
            .subscribeOn(Schedulers.io())

    override fun parseTransactionData(transaction: SafeTransaction): Single<TransactionData> =
        Single.fromCallable {
            val tx = transaction.wrapped
            val data = tx.data?.removeHexPrefix()
            when {
                data.isNullOrBlank() -> // If we have no data we default to ether transfer
                    TransactionData.AssetTransfer(ERC20Token.ETHER_TOKEN.address, tx.value?.value ?: BigInteger.ZERO, tx.address)
                tx.value?.value ?: BigInteger.ZERO == BigInteger.ZERO && data?.isSolidityMethod(ERC20Contract.Transfer.METHOD_ID) == true -> // There should be no ether transfer with the token transfer
                    parseTokenTransfer(tx)
                else ->
                    TransactionData.Generic(tx.address, tx.value?.value ?: BigInteger.ZERO, tx.data)
            }
        }
            .subscribeOn(Schedulers.io())

    private fun parseTokenTransfer(transaction: Transaction): TransactionData.AssetTransfer {
        val arguments = transaction.data!!.removeSolidityMethodPrefix(ERC20Contract.Transfer.METHOD_ID)
        return ERC20Contract.Transfer.decodeArguments(arguments).let { TransactionData.AssetTransfer(transaction.address, it._value.value, it._to) }
    }

    override fun loadTransactionInfo(id: String): Single<TransactionInfo> =
        descriptionsDao.loadDescription(id)
            .subscribeOn(Schedulers.io())
            .flatMap { info ->
                descriptionsDao.loadStatus(id).map { info to it }
            }
            .flatMap { (info, status) ->
                val transaction = info.toTransaction()
                parseTransactionData(transaction)
                    .map {
                        val gasLimit = info.dataGas + info.txGas + SAFE_TX_BASE_COSTS
                        TransactionInfo(id, status.transactionId, info.safeAddress, it, info.submittedAt, gasLimit, info.gasPrice, info.gasToken)
                    }
            }

    private fun TransactionDescriptionDb.toTransaction(): SafeTransaction =
        SafeTransaction(
            Transaction(to, value = Wei(value), data = data, nonce = nonce),
            TransactionExecutionRepository.Operation.values()[operation.toInt()]
        )

    companion object {
        // These additional costs are hardcoded in the smart contract
        private val SAFE_TX_BASE_COSTS = BigInteger.valueOf(32000)
    }
}
