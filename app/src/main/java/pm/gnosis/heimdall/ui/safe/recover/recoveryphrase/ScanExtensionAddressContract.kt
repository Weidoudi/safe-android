package pm.gnosis.heimdall.ui.safe.recover.recoveryphrase

import android.arch.lifecycle.ViewModel
import io.reactivex.Single
import pm.gnosis.model.Solidity
import pm.gnosis.svalinn.common.utils.Result

abstract class ScanExtensionAddressContract : ViewModel() {
    abstract fun setup(safeAddress: Solidity.Address)
    abstract fun isBrowserExtensionOwner(extensionAddress: Solidity.Address): Single<Result<Pair<Solidity.Address, Boolean>>>
    abstract fun getSafeAddress(): Solidity.Address
}
