package pm.gnosis.heimdall.ui.recoveryphrase

import android.arch.lifecycle.ViewModel
import io.reactivex.Single
import pm.gnosis.svalinn.common.utils.Result

abstract class ConfirmRecoveryPhraseContract : ViewModel() {
    abstract fun setup(encryptedMnemonic: String): Single<List<String>>
    abstract fun isCorrectSequence(words: List<String>): Single<Result<Boolean>>
    abstract fun getMnemonic(): String
}
