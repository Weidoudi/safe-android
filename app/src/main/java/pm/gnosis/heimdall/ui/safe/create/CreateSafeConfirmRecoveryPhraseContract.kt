package pm.gnosis.heimdall.ui.safe.create

import io.reactivex.Single
import pm.gnosis.heimdall.ui.recoveryphrase.ConfirmRecoveryPhraseViewModel
import pm.gnosis.model.Solidity
import pm.gnosis.svalinn.common.utils.Result
import pm.gnosis.svalinn.security.EncryptionManager

abstract class CreateSafeConfirmRecoveryPhraseContract(
    encryptionManager: EncryptionManager
) : ConfirmRecoveryPhraseViewModel(encryptionManager) {
    abstract fun setup(browserExtensionAddress: Solidity.Address)
    abstract fun createSafe(): Single<Result<Solidity.Address>>
}
