package pm.gnosis.heimdall.ui.transactions.view.review

import android.arch.lifecycle.ViewModel
import io.reactivex.Observable
import pm.gnosis.heimdall.data.repositories.TransactionData
import pm.gnosis.heimdall.ui.transactions.view.helpers.SubmitTransactionHelper.Events
import pm.gnosis.heimdall.ui.transactions.view.helpers.SubmitTransactionHelper.ViewUpdate
import pm.gnosis.model.Solidity
import pm.gnosis.svalinn.common.utils.Result

abstract class ReviewTransactionContract : ViewModel() {

    abstract fun setup(safe: Solidity.Address)

    abstract fun observe(events: Events, transactionData: TransactionData): Observable<Result<ViewUpdate>>
}
