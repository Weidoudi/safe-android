package pm.gnosis.heimdall.data.repositories.impls

import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.functions.BiFunction
import io.reactivex.functions.Function3
import io.reactivex.schedulers.Schedulers
import pm.gnosis.ethereum.*
import pm.gnosis.heimdall.BuildConfig
import pm.gnosis.heimdall.GnosisSafePersonalEdition.*
import pm.gnosis.heimdall.Proxy
import pm.gnosis.heimdall.data.db.ApplicationDb
import pm.gnosis.heimdall.data.db.models.*
import pm.gnosis.heimdall.data.repositories.GnosisSafeRepository
import pm.gnosis.heimdall.data.repositories.PushServiceRepository
import pm.gnosis.heimdall.data.repositories.TransactionExecutionRepository
import pm.gnosis.heimdall.data.repositories.models.*
import pm.gnosis.model.Solidity
import pm.gnosis.models.Transaction
import pm.gnosis.models.Wei
import pm.gnosis.svalinn.accounts.base.models.Signature
import pm.gnosis.svalinn.accounts.base.repositories.AccountsRepository
import pm.gnosis.utils.asEthereumAddress
import pm.gnosis.utils.asEthereumAddressString
import pm.gnosis.utils.removeHexPrefix
import java.math.BigInteger
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultGnosisSafeRepository @Inject constructor(
    gnosisAuthenticatorDb: ApplicationDb,
    private val accountsRepository: AccountsRepository,
    private val ethereumRepository: EthereumRepository,
    private val pushServiceRepository: PushServiceRepository
) : GnosisSafeRepository {

    private val safeDao = gnosisAuthenticatorDb.gnosisSafeDao()
    private val descriptionsDao = gnosisAuthenticatorDb.descriptionsDao()

    override fun observeAllSafes() =
        Flowable.combineLatest(
            safeDao.observePendingSafes(),
            safeDao.observeRecoveringSafes(),
            safeDao.observeSafes(),
            Function3 { pendingSafes: List<PendingGnosisSafeDb>, recoveringSafes: List<RecoveringGnosisSafeDb>, safes: List<GnosisSafeDb> ->
                pendingSafes.map { it.fromDb() } + recoveringSafes.map { it.fromDb() } + safes.map { it.fromDb() }
            })
            .subscribeOn(Schedulers.io())!!

    /*
     * Status
     */

    override fun loadInfo(address: Solidity.Address): Observable<SafeInfo> =
        accountsRepository.loadActiveAccount()
            .map {
                SafeInfoRequest(
                    EthBalance(address, 0),
                    EthCall(
                        transaction = Transaction(address = address, data = GetThreshold.encode()),
                        id = 1
                    ),
                    EthCall(
                        transaction = Transaction(address = address, data = GetOwners.encode()),
                        id = 2
                    ),
                    EthCall(
                        transaction = Transaction(
                            address = address,
                            data = IsOwner.encode(it.address)
                        ), id = 3
                    ),
                    EthCall(
                        transaction = Transaction(address = address, data = GetModules.encode()),
                        id = 4
                    )
                )
            }
            .flatMapObservable { bulk -> ethereumRepository.request(bulk) }
            .map {
                val balance = it.balance.result() ?: throw IllegalArgumentException()
                val threshold = GetThreshold.decode(it.threshold.result() ?: throw IllegalArgumentException()).param0.value.toLong()
                val owners = GetOwners.decode(it.owners.result() ?: throw IllegalArgumentException()).param0.items
                val isOwner = IsOwner.decode(it.isOwner.result() ?: throw IllegalArgumentException()).param0.value
                val modules = GetModules.decode(it.modules.result() ?: throw IllegalArgumentException()).param0.items.map { it }
                SafeInfo(address, balance, threshold, owners, isOwner, modules)
            }

    override fun checkSafe(address: Solidity.Address): Observable<Boolean> =
        ethereumRepository.request(EthCall(transaction = Transaction(address = address, data = Proxy.Implementation.encode())))
            .map {
                it.result().let {
                    !it?.removeHexPrefix().isNullOrBlank() &&
                            Proxy.Implementation.decode(it!!).param0 == BuildConfig.SAFE_MASTER_COPY_ADDRESS.asEthereumAddress()
                }
            }

    /*
     * Deployed Safes
     */

    override fun observeSafes() =
        safeDao.observeSafes()
            .map { it.map { it.fromDb() } }
            .subscribeOn(Schedulers.io())!!

    override fun observeSafe(address: Solidity.Address): Flowable<Safe> =
        safeDao.observeSafe(address)
            .subscribeOn(Schedulers.io())
            .map { it.fromDb() }

    override fun loadSafe(address: Solidity.Address): Single<Safe> =
        safeDao.loadSafe(address)
            .subscribeOn(Schedulers.io())
            .map { it.fromDb() }

    override fun addSafe(address: Solidity.Address, name: String?) =
        Completable.fromCallable {
            safeDao.insertSafe(GnosisSafeDb(address, name))
        }.subscribeOn(Schedulers.io())!!

    override fun removeSafe(address: Solidity.Address) =
        Completable.fromCallable {
            safeDao.removeSafe(address)
            // TODO: remove transactions from descriptions DAO
        }.subscribeOn(Schedulers.io())!!

    override fun updateSafe(safe: Safe) =
        Completable.fromCallable {
            safeDao.updateSafe(safe.toDb())
        }.subscribeOn(Schedulers.io())!!

    /*
     * Pending Safes
     */

    override fun observePendingSafe(address: Solidity.Address): Flowable<PendingSafe> =
        safeDao.observePendingSafe(address).map { it.fromDb() }

    override fun loadPendingSafe(address: Solidity.Address): Single<PendingSafe> =
        safeDao.loadPendingSafe(address)
            .subscribeOn(Schedulers.io())
            .map { it.fromDb() }

    override fun addPendingSafe(address: Solidity.Address, transactionHash: BigInteger, name: String?, payment: Wei): Completable =
        Completable.fromAction {
            safeDao.insertPendingSafe(PendingGnosisSafeDb(address, transactionHash, name, ERC20Token.ETHER_TOKEN.address, payment.value))
        }.subscribeOn(Schedulers.io())

    override fun updatePendingSafe(pendingSafe: PendingSafe): Completable =
        Completable.fromCallable {
            safeDao.updatePendingSafe(pendingSafe.toDb())
        }.subscribeOn(Schedulers.io())

    override fun removePendingSafe(address: Solidity.Address) =
        Completable.fromCallable {
            safeDao.removePendingSafe(address)
            // TODO: remove transactions from descriptions DAO
        }.subscribeOn(Schedulers.io())!!

    /*
     * Recovering Safes
     */

    override fun observeRecoveringSafe(address: Solidity.Address): Flowable<RecoveringSafe> =
        safeDao.observeRecoveringSafe(address).map { it.fromDb() }

    override fun loadRecoveringSafe(address: Solidity.Address): Single<RecoveringSafe> =
        safeDao.loadRecoveringSafe(address)
            .subscribeOn(Schedulers.io())
            .map { it.fromDb() }

    override fun addRecoveringSafe(
        safeAddress: Solidity.Address,
        transactionHash: BigInteger?,
        name: String?,
        executeInfo: TransactionExecutionRepository.ExecuteInformation,
        signatures: List<Signature>
    ): Completable =
        Completable.fromAction {
            if (safeAddress != executeInfo.transaction.wrapped.address &&
                BuildConfig.MULTI_SEND_ADDRESS.asEthereumAddress()!! != executeInfo.transaction.wrapped.address
            ) throw IllegalArgumentException("Transaction target needs to be the safe or multi send")
            safeDao.insertRecoveringSafe(
                RecoveringSafe(
                    safeAddress,
                    transactionHash,
                    name,
                    executeInfo.transaction.wrapped.address,
                    executeInfo.transaction.wrapped.data!!,
                    executeInfo.txGas,
                    executeInfo.dataGas,
                    ERC20Token.ETHER_TOKEN.address,
                    executeInfo.gasPrice,
                    executeInfo.transaction.wrapped.nonce!!,
                    executeInfo.transaction.operation,
                    signatures
                ).toDb()
            )
        }.subscribeOn(Schedulers.io())

    override fun updateRecoveringSafe(recoveringSafe: RecoveringSafe): Completable =
        Completable.fromCallable {
            safeDao.updateRecoveringSafe(recoveringSafe.toDb())
        }.subscribeOn(Schedulers.io())

    override fun removeRecoveringSafe(address: Solidity.Address) =
        Completable.fromCallable {
            safeDao.removeRecoveringSafe(address)
            // TODO: remove transactions from descriptions DAO
        }.subscribeOn(Schedulers.io())!!

    /*
     * Safe creation
     */

    override fun pendingSafeToDeployedSafe(pendingSafe: PendingSafe): Completable =
        Completable.fromCallable { safeDao.pendingSafeToDeployedSafe(pendingSafe.address) }
            .andThen(sendSafeCreationPush(pendingSafe.address).onErrorComplete())
            .subscribeOn(Schedulers.io())

    override fun recoveringSafeToDeployedSafe(recoveringSafe: RecoveringSafe): Completable =
        Completable.fromCallable { safeDao.recoveringSafeToDeployedSafe(recoveringSafe.address) }
            .andThen(sendSafeCreationPush(recoveringSafe.address).onErrorComplete())
            .subscribeOn(Schedulers.io())

    override fun sendSafeCreationPush(safeAddress: Solidity.Address): Completable =
        Single.zip(
            accountsRepository.loadActiveAccount().map { it.address },
            loadInfo(safeAddress).firstOrError().map { it.owners },
            BiFunction<Solidity.Address, List<Solidity.Address>, Set<Solidity.Address>> { deviceAddress, owners -> (owners - deviceAddress).toSet() }
        )
            .flatMapCompletable { pushServiceRepository.propagateSafeCreation(safeAddress, it) }
            .subscribeOn(Schedulers.io())

    /*
     * Safe Transactions
     */

    override fun observePendingTransactions(address: Solidity.Address): Flowable<List<TransactionStatus>> =
        descriptionsDao.observePendingTransaction(address)
            .subscribeOn(Schedulers.io())
            .map { it.map { TransactionStatus(it.id, it.timestamp, true) } }

    override fun observeSubmittedTransactions(address: Solidity.Address): Flowable<List<TransactionStatus>> =
        descriptionsDao.observeSubmittedTransaction(address)
            .subscribeOn(Schedulers.io())
            .map { it.map { TransactionStatus(it.id, it.timestamp, false) } }

    private class SafeInfoRequest(
        val balance: EthRequest<Wei>,
        val threshold: EthRequest<String>,
        val owners: EthRequest<String>,
        val isOwner: EthRequest<String>,
        val modules: EthRequest<String>
    ) : BulkRequest(balance, threshold, owners, isOwner, modules)
}
