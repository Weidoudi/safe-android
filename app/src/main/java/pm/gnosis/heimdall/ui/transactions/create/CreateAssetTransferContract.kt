package pm.gnosis.heimdall.ui.transactions.create

import android.arch.lifecycle.ViewModel
import android.content.Intent
import io.reactivex.Observable
import io.reactivex.ObservableTransformer
import pm.gnosis.heimdall.data.repositories.models.ERC20TokenWithBalance
import pm.gnosis.model.Solidity
import pm.gnosis.models.Wei
import pm.gnosis.svalinn.common.utils.Result

abstract class CreateAssetTransferContract : ViewModel() {
    abstract fun processInput(
        safe: Solidity.Address,
        tokenAddress: Solidity.Address,
        reviewEvents: Observable<Unit>
    ): ObservableTransformer<Input, Result<ViewUpdate>>

    data class Input(val amount: String, val address: String)

    sealed class ViewUpdate {
        data class Estimate(val estimate: Wei, val balance: Wei, val canExecute: Boolean) : ViewUpdate()
        object EstimateError : ViewUpdate()
        data class TokenInfo(val value: ERC20TokenWithBalance) : ViewUpdate()
        data class InvalidInput(val amount: Boolean, val address: Boolean) : ViewUpdate()
        data class StartReview(val intent: Intent) : ViewUpdate()
    }
}
