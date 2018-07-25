package pm.gnosis.heimdall.ui.safe.recover.recoveryphrase

import io.reactivex.Single
import pm.gnosis.heimdall.data.repositories.GnosisSafeRepository
import pm.gnosis.model.Solidity
import pm.gnosis.svalinn.common.utils.Result
import pm.gnosis.svalinn.common.utils.mapToResult
import javax.inject.Inject

class ScanExtensionAddressViewModel @Inject constructor(
    val safeRepository: GnosisSafeRepository
) : ScanExtensionAddressContract() {
    private lateinit var safeAddress: Solidity.Address

    override fun setup(safeAddress: Solidity.Address) {
        this.safeAddress = safeAddress
    }

    override fun isBrowserExtensionOwner(extensionAddress: Solidity.Address): Single<Result<Pair<Solidity.Address, Boolean>>> =
        safeRepository.loadInfo(safeAddress).firstOrError()
            .map { safeInfo -> extensionAddress to safeInfo.owners.contains(extensionAddress) }
            .mapToResult()

    override fun getSafeAddress() = safeAddress
}
