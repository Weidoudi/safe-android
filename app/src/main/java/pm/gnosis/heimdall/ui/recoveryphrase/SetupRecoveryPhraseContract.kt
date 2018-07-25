package pm.gnosis.heimdall.ui.recoveryphrase

import android.arch.lifecycle.ViewModel
import io.reactivex.Single

abstract class SetupRecoveryPhraseContract : ViewModel() {
    abstract fun generateRecoveryPhrase(): Single<String>
    abstract fun loadEncryptedRecoveryPhrase(): Single<String>
}
