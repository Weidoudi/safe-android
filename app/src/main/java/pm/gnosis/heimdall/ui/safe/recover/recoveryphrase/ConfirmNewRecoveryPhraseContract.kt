package pm.gnosis.heimdall.ui.safe.recover.recoveryphrase

import io.reactivex.Single
import pm.gnosis.heimdall.data.repositories.models.SafeTransaction
import pm.gnosis.heimdall.ui.recoveryphrase.ConfirmRecoveryPhraseViewModel
import pm.gnosis.model.Solidity
import pm.gnosis.svalinn.common.utils.Result
import pm.gnosis.svalinn.security.EncryptionManager

abstract class ConfirmNewRecoveryPhraseContract(encryptionManager: EncryptionManager) : ConfirmRecoveryPhraseViewModel(encryptionManager) {
    abstract fun setup(safeAddress: Solidity.Address, browserExtensionAddress: Solidity.Address)
    abstract fun loadTransaction(): Single<Result<Pair<Solidity.Address, SafeTransaction>>>
    abstract fun getSafeAddress(): Solidity.Address
}
