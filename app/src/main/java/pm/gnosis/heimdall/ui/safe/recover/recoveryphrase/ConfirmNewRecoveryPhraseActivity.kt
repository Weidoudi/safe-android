package pm.gnosis.heimdall.ui.safe.recover.recoveryphrase

import android.content.Context
import android.content.Intent
import android.os.Bundle
import com.jakewharton.rxbinding2.view.clicks
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.plusAssign
import kotlinx.android.synthetic.main.layout_confirm_safe_recovery_phrase.*
import pm.gnosis.heimdall.data.repositories.TransactionData
import pm.gnosis.heimdall.data.repositories.models.SafeTransaction
import pm.gnosis.heimdall.di.components.ViewComponent
import pm.gnosis.heimdall.ui.recoveryphrase.ConfirmRecoveryPhraseActivity
import pm.gnosis.heimdall.ui.transactions.view.review.ReviewTransactionActivity
import pm.gnosis.model.Solidity
import pm.gnosis.svalinn.common.utils.subscribeForResult
import pm.gnosis.svalinn.common.utils.visible
import pm.gnosis.utils.asEthereumAddress
import pm.gnosis.utils.asEthereumAddressString
import pm.gnosis.utils.nullOnThrow
import timber.log.Timber

class ConfirmNewRecoveryPhraseActivity : ConfirmRecoveryPhraseActivity<ConfirmNewRecoveryPhraseContract>() {
    override fun inject(component: ViewComponent) = component.inject(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val safeAddress = nullOnThrow { intent.getStringExtra(EXTRA_SAFE_ADDRESS).asEthereumAddress()!! } ?: run { finish(); return }
        val browserExtension = nullOnThrow { intent.getStringExtra(EXTRA_BROWSER_ADDRESS).asEthereumAddress()!! } ?: run { finish(); return }
        viewModel.setup(safeAddress, browserExtension)
    }

    override fun onStart() {
        super.onStart()
        disposables += layout_confirm_safe_recovery_phrase_finish.clicks()
            .switchMapSingle { _ ->
                viewModel.loadTransaction()
                    .observeOn(AndroidSchedulers.mainThread())
                    .doOnSubscribe { onLoadingTransaction(isLoading = true) }
                    .doAfterTerminate { onLoadingTransaction(isLoading = false) }
            }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeForResult(onNext = { onTransaction(it.first, it.second) }, onError = ::onTransactionError)
    }

    private fun onTransaction(safeAddress: Solidity.Address, safeTransaction: SafeTransaction) {
        startActivity(
            ReviewTransactionActivity.createIntent(
                this,
                safeAddress,
                txData = TransactionData.ReplaceRecoveryPhrase(safeAddress, safeTransaction)
            )
        )
    }

    private fun onTransactionError(throwable: Throwable) {
        Timber.e(throwable)
    }

    private fun onLoadingTransaction(isLoading: Boolean) {
        bottomBarEnabled(!isLoading)
        layout_confirm_safe_recovery_phrase_progress_bar.visible(isLoading)
    }

    companion object {
        private const val EXTRA_SAFE_ADDRESS = "extra.string.safe_address"
        private const val EXTRA_BROWSER_ADDRESS = "extra.string.browser_address"

        fun createIntent(context: Context, safeAddress: Solidity.Address, browserAddress: Solidity.Address, encryptedMnemonic: String) =
            Intent(context, ConfirmNewRecoveryPhraseActivity::class.java).apply {
                putExtra(EXTRA_SAFE_ADDRESS, safeAddress.asEthereumAddressString())
                putExtra(EXTRA_BROWSER_ADDRESS, browserAddress.asEthereumAddressString())
                putExtra(EXTRA_ENCRYPTED_MNEMONIC, encryptedMnemonic)
            }
    }
}
