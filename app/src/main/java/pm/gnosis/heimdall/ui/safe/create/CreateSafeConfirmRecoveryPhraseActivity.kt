package pm.gnosis.heimdall.ui.safe.create

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import com.jakewharton.rxbinding2.view.clicks
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.plusAssign
import kotlinx.android.synthetic.main.layout_confirm_safe_recovery_phrase.*
import pm.gnosis.heimdall.R
import pm.gnosis.heimdall.di.components.ViewComponent
import pm.gnosis.heimdall.ui.recoveryphrase.ConfirmRecoveryPhraseActivity
import pm.gnosis.heimdall.ui.safe.main.SafeMainActivity
import pm.gnosis.model.Solidity
import pm.gnosis.svalinn.common.utils.subscribeForResult
import pm.gnosis.svalinn.common.utils.toast
import pm.gnosis.utils.asEthereumAddress
import pm.gnosis.utils.asEthereumAddressString
import timber.log.Timber

class CreateSafeConfirmRecoveryPhraseActivity : ConfirmRecoveryPhraseActivity<CreateSafeConfirmRecoveryPhraseContract>() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent.getStringExtra(EXTRA_BROWSER_EXTENSION_ADDRESS).asEthereumAddress()?.let { viewModel.setup(it) }
            ?: run { finish(); return }
    }

    override fun onStart() {
        super.onStart()
        disposables += layout_confirm_safe_recovery_phrase_finish.clicks()
            .flatMapSingle {
                viewModel.createSafe()
                    .doOnSubscribe { _ ->
                        layout_confirm_safe_recovery_phrase_progress_bar.visibility = View.VISIBLE
                        bottomBarEnabled(false)
                    }
            }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeForResult(onNext = ::onSafeCreated, onError = ::onSafeCreationError)
    }

    private fun onSafeCreated(address: Solidity.Address) {
        startActivity(SafeMainActivity.createIntent(this, address))
    }

    private fun onSafeCreationError(throwable: Throwable) {
        Timber.e(throwable)
        layout_confirm_safe_recovery_phrase_progress_bar.visibility = View.INVISIBLE
        bottomBarEnabled(true)
        toast(R.string.unknown_error)
    }

    override fun inject(component: ViewComponent) = component.inject(this)

    companion object {
        private const val EXTRA_BROWSER_EXTENSION_ADDRESS = "extra.string.browser_extension_address"

        fun createIntent(context: Context, encryptedMnemonic: String, chromeExtensionAddress: Solidity.Address) =
            Intent(context, CreateSafeConfirmRecoveryPhraseActivity::class.java).apply {
                putExtra(EXTRA_ENCRYPTED_MNEMONIC, encryptedMnemonic)
                putExtra(EXTRA_BROWSER_EXTENSION_ADDRESS, chromeExtensionAddress.asEthereumAddressString())
            }
    }
}
