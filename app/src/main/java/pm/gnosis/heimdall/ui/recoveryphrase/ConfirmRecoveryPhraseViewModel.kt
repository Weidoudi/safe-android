package pm.gnosis.heimdall.ui.recoveryphrase

import io.reactivex.Completable
import io.reactivex.Single
import io.reactivex.schedulers.Schedulers
import pm.gnosis.svalinn.common.utils.Result
import pm.gnosis.svalinn.common.utils.mapToResult
import pm.gnosis.svalinn.security.EncryptionManager
import pm.gnosis.utils.utf8String
import pm.gnosis.utils.words

abstract class ConfirmRecoveryPhraseViewModel(
    private val encryptionManager: EncryptionManager
) : ConfirmRecoveryPhraseContract() {
    private lateinit var mnemonic: String

    override fun setup(encryptedMnemonic: String): Single<List<String>> =
        Completable.fromAction {
            val mnemonic = encryptionManager.decrypt(EncryptionManager.CryptoData.fromString(encryptedMnemonic)).utf8String()
            if (mnemonic.words().size != 12) throw IllegalStateException("Invalid mnemonic")
            this.mnemonic = mnemonic
        }
            .andThen(loadWordsToDisplay())
            .subscribeOn(Schedulers.io())

    private fun loadWordsToDisplay(): Single<List<String>> =
        Single.fromCallable {
            mnemonic.words().sorted()
        }.subscribeOn(Schedulers.computation())

    override fun isCorrectSequence(words: List<String>): Single<Result<Boolean>> =
        Single.fromCallable {
            words.joinToString(" ") == mnemonic
        }.subscribeOn(Schedulers.computation()).mapToResult()

    override fun getMnemonic() = mnemonic
}
