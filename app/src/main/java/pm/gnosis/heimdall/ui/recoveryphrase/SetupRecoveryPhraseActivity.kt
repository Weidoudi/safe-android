package pm.gnosis.heimdall.ui.recoveryphrase

import android.os.Bundle
import com.jakewharton.rxbinding2.view.clicks
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.android.synthetic.main.layout_safe_recovery_phrase.*
import pm.gnosis.heimdall.R
import pm.gnosis.heimdall.helpers.ToolbarHelper
import pm.gnosis.heimdall.reporting.ScreenId
import pm.gnosis.heimdall.ui.base.ViewModelActivity
import pm.gnosis.svalinn.common.utils.getColorCompat
import pm.gnosis.svalinn.common.utils.toast
import pm.gnosis.svalinn.common.utils.visible
import pm.gnosis.utils.words
import timber.log.Timber
import javax.inject.Inject

abstract class SetupRecoveryPhraseActivity<VM : SetupRecoveryPhraseContract> : ViewModelActivity<VM>() {
    @Inject
    lateinit var toolbarHelper: ToolbarHelper

    override fun screenId() = ScreenId.DISPLAY_RECOVERY_PHRASE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        disposables += viewModel.generateRecoveryPhrase()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeBy(onSuccess = ::onMnemonic, onError = ::onMnemonicError)
    }

    override fun onStart() {
        super.onStart()
        disposables += toolbarHelper.setupShadow(layout_safe_recovery_phrase_toolbar_shadow, layout_safe_recovery_phrase_content_scroll)

        disposables += layout_safe_recovery_phrase_finish.clicks()
            .flatMapSingle { _ ->
                viewModel.loadEncryptedRecoveryPhrase()
                    .observeOn(AndroidSchedulers.mainThread())
                    .doOnSubscribe { isLoading(true) }
                    .doFinally { isLoading(false) }
            }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeBy(onNext = ::onConfirmedRecoveryPhrase, onError = Timber::e)
    }

    abstract fun onConfirmedRecoveryPhrase(encryptedRecoveryPhrase: String)

    private fun isLoading(isLoading: Boolean) {
        layout_safe_recovery_phrase_progress_bar.visible(isLoading)
        layout_safe_recovery_phrase_finish.isEnabled = !isLoading
        layout_safe_recovery_phrase_bottom_bar.setBackgroundColor(getColorCompat(if (isLoading) R.color.bluey_grey else R.color.azure))
    }

    private fun onMnemonic(mnemonic: String) {
        val words = mnemonic.words()
        if (words.size != 12) toast(R.string.mnemonic_error_invalid) //TODO: finish activity?
        val firstGroupStringBuilder = StringBuilder()
        val secondGroupStringBuilder = StringBuilder()
        val thirdGroupStringBuilder = StringBuilder()
        words.forEachIndexed { index, word ->
            when {
                index < 4 -> firstGroupStringBuilder.append("${index + 1}. $word\n\n")
                index < 8 -> secondGroupStringBuilder.append("${index + 1}. $word\n\n")
                index < 12 -> thirdGroupStringBuilder.append("${index + 1}. $word\n\n")
            }
        }

        layout_safe_recovery_phrase_1_4.text = firstGroupStringBuilder.toString()
        layout_safe_recovery_phrase_5_8.text = secondGroupStringBuilder.toString()
        layout_safe_recovery_phrase_9_12.text = thirdGroupStringBuilder.toString()

    }

    private fun onMnemonicError(throwable: Throwable) {
        Timber.e(throwable)
        toast(R.string.mnemonic_error_invalid) //TODO: finish activity?
    }

    override fun layout() = R.layout.layout_safe_recovery_phrase
}
