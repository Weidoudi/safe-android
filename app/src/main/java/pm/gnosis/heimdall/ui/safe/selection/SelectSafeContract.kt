package pm.gnosis.heimdall.ui.safe.selection

import android.arch.lifecycle.ViewModel
import android.content.Intent
import io.reactivex.Single
import pm.gnosis.heimdall.data.repositories.models.Safe
import pm.gnosis.heimdall.data.repositories.models.SafeTransaction
import pm.gnosis.model.Solidity
import pm.gnosis.svalinn.common.utils.Result

abstract class SelectSafeContract : ViewModel() {
    abstract fun loadSafes(): Single<List<Safe>>
    abstract fun reviewTransaction(safe: Solidity.Address?, transaction: SafeTransaction): Single<Result<Intent>>
}
