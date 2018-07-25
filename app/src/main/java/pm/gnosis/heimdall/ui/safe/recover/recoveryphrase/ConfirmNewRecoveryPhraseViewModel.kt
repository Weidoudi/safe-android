package pm.gnosis.heimdall.ui.safe.recover.recoveryphrase

import io.reactivex.Single
import io.reactivex.functions.BiFunction
import io.reactivex.functions.Function3
import io.reactivex.schedulers.Schedulers
import pm.gnosis.heimdall.data.repositories.GnosisSafeRepository
import pm.gnosis.heimdall.data.repositories.models.SafeInfo
import pm.gnosis.heimdall.data.repositories.models.SafeTransaction
import pm.gnosis.heimdall.ui.safe.helpers.RecoverSafeOwnersHelper
import pm.gnosis.mnemonic.Bip39
import pm.gnosis.model.Solidity
import pm.gnosis.svalinn.accounts.base.repositories.AccountsRepository
import pm.gnosis.svalinn.common.utils.Result
import pm.gnosis.svalinn.common.utils.mapToResult
import pm.gnosis.svalinn.security.EncryptionManager
import javax.inject.Inject

class ConfirmNewRecoveryPhraseViewModel @Inject constructor(
    private val accountsRepository: AccountsRepository,
    private val bip39: Bip39,
    encryptionManager: EncryptionManager,
    private val safeRepository: GnosisSafeRepository,
    private val recoverSafeOwnersHelper: RecoverSafeOwnersHelper
) : ConfirmNewRecoveryPhraseContract(encryptionManager) {
    private lateinit var safeAddress: Solidity.Address
    private lateinit var browserExtensionAddress: Solidity.Address

    override fun setup(safeAddress: Solidity.Address, browserExtensionAddress: Solidity.Address) {
        this.safeAddress = safeAddress
        this.browserExtensionAddress = browserExtensionAddress
    }

    override fun loadTransaction(): Single<Result<Pair<Solidity.Address, SafeTransaction>>> =
        Single.zip(
            safeRepository.loadInfo(safeAddress).firstOrError(),
            accountsRepository.loadActiveAccount().map { it.address },
            getAddressesFromRecoveryPhrase(),
            Function3 { safeInfo: SafeInfo, appAccount: Solidity.Address, recoveryPhraseAddresses: List<Solidity.Address> ->
                RecoverTransactionBundle(
                    safeInfo,
                    listOf(appAccount, browserExtensionAddress),
                    recoveryPhraseAddresses
                )
            }
        )
            .flatMap { bundle ->
                Single.fromCallable {
                    recoverSafeOwnersHelper.buildRecoverTransaction(
                        safeInfo = bundle.safeInfo,
                        addressesToKeep = bundle.addressesToKeep,
                        addressesToAdd = bundle.addressesToAdd
                    )
                }.map { safeAddress to it }
            }
            .subscribeOn(Schedulers.io())
            .mapToResult()

    override fun getSafeAddress(): Solidity.Address = safeAddress

    private data class RecoverTransactionBundle(
        val safeInfo: SafeInfo,
        val addressesToKeep: List<Solidity.Address>,
        val addressesToAdd: List<Solidity.Address>
    )

    private fun getAddressesFromRecoveryPhrase() =
        Single.fromCallable { bip39.mnemonicToSeed(bip39.validateMnemonic(getMnemonic())) }
            .flatMap { seed ->
                Single.zip(
                    accountsRepository.accountFromMnemonicSeed(seed, accountIndex = 0).map { it.first },
                    accountsRepository.accountFromMnemonicSeed(seed, accountIndex = 1).map { it.first },
                    BiFunction { recoveryAccount1: Solidity.Address, recoveryAccount2: Solidity.Address ->
                        listOf(
                            recoveryAccount1,
                            recoveryAccount2
                        )
                    }
                )
            }
}
