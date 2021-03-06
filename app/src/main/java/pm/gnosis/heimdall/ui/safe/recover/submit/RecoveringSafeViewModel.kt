package pm.gnosis.heimdall.ui.safe.recover.submit

import android.content.Context
import io.reactivex.Observable
import io.reactivex.Single
import pm.gnosis.crypto.utils.asEthereumAddressChecksumString
import pm.gnosis.heimdall.data.repositories.GnosisSafeRepository
import pm.gnosis.heimdall.data.repositories.TokenRepository
import pm.gnosis.heimdall.data.repositories.TransactionExecutionRepository
import pm.gnosis.heimdall.data.repositories.models.ERC20Token
import pm.gnosis.heimdall.data.repositories.models.RecoveringSafe
import pm.gnosis.heimdall.data.repositories.models.SafeTransaction
import pm.gnosis.heimdall.di.ApplicationContext
import pm.gnosis.heimdall.ui.exceptions.SimpleLocalizedException
import pm.gnosis.heimdall.ui.safe.pending.SafeCreationFundViewModel
import pm.gnosis.heimdall.utils.emitAndNext
import pm.gnosis.model.Solidity
import pm.gnosis.models.Transaction
import pm.gnosis.models.Wei
import pm.gnosis.svalinn.accounts.base.models.Signature
import pm.gnosis.svalinn.accounts.base.repositories.AccountsRepository
import pm.gnosis.svalinn.common.utils.DataResult
import pm.gnosis.svalinn.common.utils.Result
import pm.gnosis.svalinn.common.utils.mapToResult
import pm.gnosis.utils.addHexPrefix
import pm.gnosis.utils.asTransactionHash
import pm.gnosis.utils.hexAsBigInteger
import pm.gnosis.utils.toHexString
import java.math.BigInteger
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class RecoveringSafeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val accountsRepository: AccountsRepository,
    private val executionRepository: TransactionExecutionRepository,
    private val safeRepository: GnosisSafeRepository,
    private val tokenRepository: TokenRepository
) : RecoveringSafeContract() {

    private val errorHandler = SimpleLocalizedException.networkErrorHandlerBuilder(context).build()

    override fun checkSafeState(address: Solidity.Address): Single<Pair<RecoveringSafe, RecoveryState>> =
        safeRepository.loadRecoveringSafe(address)
            .flatMap { safe ->
                if (safe.transactionHash != null)
                    Single.just(safe to RecoveryState.PENDING)
                else
                    requestExecuteInfo(safe).map {
                        when {
                            safe.nonce != it.transaction.wrapped.nonce ||
                                    it.transaction.wrapped.value ?: Wei.ZERO != Wei.ZERO -> safe to RecoveryState.ERROR
                            it.balance.value < it.gasCosts() -> safe to RecoveryState.CREATED
                            else -> safe to RecoveryState.FUNDED
                        }
                    }
            }
            .onErrorResumeNext { errorHandler.single(it) }

    override fun checkRecoveryStatus(address: Solidity.Address): Single<Solidity.Address> =
        safeRepository.loadRecoveringSafe(address)
            .flatMap { safe ->
                safe.transactionHash?.let {
                    executionRepository.observeTransactionStatus(it)
                        .firstOrError()
                        .flatMap { (success) ->
                            if (success)
                                safeRepository.recoveringSafeToDeployedSafe(safe).andThen(Single.just(safe.address))
                            else throw IllegalStateException() // TODO: proper error here
                        }
                } ?: throw IllegalStateException()
            }
            .onErrorResumeNext { errorHandler.single(it) }


    override fun observeRecoveryInfo(address: Solidity.Address): Observable<Result<RecoveryInfo>> =
        safeRepository.loadRecoveringSafe(address)
            .map { Triple(it.address.asEthereumAddressChecksumString(), it.gasPrice * (it.dataGas + it.txGas), it.gasToken) }
            .emitAndNext(
                emit = { (safeAddress, amount) ->
                    DataResult(
                        RecoveryInfo(
                            safeAddress,
                            null,
                            amount
                        )
                    )
                },
                next = { (safeAddress, amount, gasToken) ->
                    tokenRepository.loadToken(gasToken)
                        .onErrorResumeNext { errorHandler.single(it) }
                        .map { RecoveryInfo(safeAddress, it, amount) }
                        .onErrorResumeNext { errorHandler.single(it) }
                        .toObservable()
                        .mapToResult()
                }
            )

    override fun loadRecoveryExecuteInfo(address: Solidity.Address): Single<TransactionExecutionRepository.ExecuteInformation> =
        safeRepository.loadRecoveringSafe(address)
            .flatMap { safe ->
                val tx = buildSafeTransaction(safe)
                executionRepository.calculateHash(address, tx, safe.gasPrice, safe.txGas, safe.dataGas)
                    .flatMap { hash ->
                        executionRepository.loadSafeExecuteState(address)
                            .map {
                                TransactionExecutionRepository.ExecuteInformation(
                                    hash.toHexString().addHexPrefix(),
                                    tx,
                                    it.sender,
                                    it.requiredConfirmation,
                                    it.owners,
                                    safe.gasPrice,
                                    safe.txGas,
                                    safe.dataGas,
                                    it.balance
                                )
                            }
                    }
            }
            .onErrorResumeNext { errorHandler.single(it) }

    override fun submitRecovery(address: Solidity.Address): Single<Solidity.Address> =
        safeRepository.loadRecoveringSafe(address)
            .flatMap { safe ->
                executionRepository.calculateHash(
                    address,
                    buildSafeTransaction(safe),
                    safe.txGas,
                    safe.dataGas,
                    safe.gasPrice,
                    safe.gasToken
                ).flatMap { txHash ->
                    Single.zip(
                        safe.signatures.map { signature -> accountsRepository.recover(txHash, signature).map { it to signature } }
                    ) {
                        it.associate {
                            @Suppress("UNCHECKED_CAST") // Unchecked cast is necessary because of rx zip implementation
                            it as Pair<Solidity.Address, Signature>
                        }
                    }
                }.map { safe to it }
            }
            .flatMap { (safe, signatures) ->
                executionRepository.submit(address, buildSafeTransaction(safe), signatures, false, safe.txGas, safe.dataGas, safe.gasPrice, false)
                    .flatMap {
                        safeRepository.updateRecoveringSafe(safe.copy(transactionHash = it.hexAsBigInteger())).andThen(Single.just(safe.address))
                    }
            }
            .onErrorResumeNext { errorHandler.single(it) }


    override fun checkRecoveryFunded(address: Solidity.Address): Single<Solidity.Address> =
        safeRepository.loadRecoveringSafe(address)
            .flatMap { safe ->
                val gasCosts = (safe.txGas + safe.dataGas) * safe.gasPrice
                // Create a fake token since only the address is necessary to load the balance
                requestBalance(
                    safe.address,
                    ERC20Token(safe.gasToken, decimals = 0, name = "", symbol = "")
                )
                    .map { if (it < gasCosts) throw SafeCreationFundViewModel.NotEnoughFundsException() }
                    .retryWhen { errors ->
                        errors.delay(BALANCE_REQUEST_INTERVAL_SECONDS, TimeUnit.SECONDS)
                    }
                    .map { safe.address }
            }
            .onErrorResumeNext { errorHandler.single(it) }

    private fun requestBalance(address: Solidity.Address, paymentToken: ERC20Token) =
        tokenRepository.loadTokenBalances(address, listOf(paymentToken)).map { it.first().second ?: BigInteger.ZERO }.firstOrError()

    private fun requestExecuteInfo(safe: RecoveringSafe) =
        executionRepository.loadExecuteInformation(
            safe.address,
            buildSafeTransaction(safe)
        )

    private fun buildSafeTransaction(safe: RecoveringSafe) =
        SafeTransaction(
            Transaction(safe.recoverer, data = safe.data, nonce = safe.nonce),
            safe.operation
        )

    companion object {
        private const val BALANCE_REQUEST_INTERVAL_SECONDS = 10L
    }
}
