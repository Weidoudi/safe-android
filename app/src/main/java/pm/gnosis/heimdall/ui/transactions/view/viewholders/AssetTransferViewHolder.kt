package pm.gnosis.heimdall.ui.transactions.view.viewholders

import android.arch.lifecycle.Lifecycle
import android.arch.lifecycle.OnLifecycleEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.layout_asset_transfer_info.view.*
import pm.gnosis.heimdall.R
import pm.gnosis.heimdall.data.repositories.TokenRepository
import pm.gnosis.heimdall.data.repositories.TransactionData
import pm.gnosis.heimdall.data.repositories.models.ERC20Token
import pm.gnosis.heimdall.data.repositories.models.SafeTransaction
import pm.gnosis.heimdall.helpers.AddressHelper
import pm.gnosis.heimdall.ui.transactions.builder.AssetTransferTransactionBuilder
import pm.gnosis.heimdall.ui.transactions.view.TransactionInfoViewHolder
import pm.gnosis.model.Solidity
import pm.gnosis.svalinn.common.utils.visible
import pm.gnosis.utils.asDecimalString
import java.math.BigInteger

class AssetTransferViewHolder(
    private val safe: Solidity.Address,
    private val data: TransactionData.AssetTransfer,
    private val addressHelper: AddressHelper,
    private val tokenRepository: TokenRepository,
    private val showExtraInfo: Boolean
) : TransactionInfoViewHolder {

    private val disposables = CompositeDisposable()
    private var view: View? = null

    override fun loadTransaction(): Single<SafeTransaction> =
        Single.fromCallable {
            AssetTransferTransactionBuilder.build(data)
        }.subscribeOn(Schedulers.computation())

    override fun inflate(inflater: LayoutInflater, root: ViewGroup) {
        view = inflater.inflate(R.layout.layout_asset_transfer_info, root, true)
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_START)
    fun start() {
        val view = view ?: return
        view.layout_asset_transfer_info_value.text = "~"
        view.layout_asset_transfer_info_fiat.text = "~ $"

        setupSafeInfo()
        setupToInfo()
        setupTokenInfo()
    }

    private fun setupTokenInfo() {
        disposables += tokenRepository.loadToken(data.token)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeBy(
                onSuccess = {
                    view?.apply {
                        layout_asset_transfer_info_value.text = it.displayString(data.amount)
                        loadSafeTokenBalance(it)
                    }
                },
                onError = {
                    view?.apply {
                        layout_asset_transfer_info_value.text = data.amount.asDecimalString()
                        layout_asset_transfer_info_fiat.text = context.getString(R.string.unknown_token)
                        loadSafeTokenBalance()
                    }
                }
            )
    }

    private fun loadSafeTokenBalance(token: ERC20Token? = null) {
        if (!showExtraInfo) {
            view?.layout_asset_transfer_info_safe_balance?.text = null
            return
        }
        val erc20Token = token ?: ERC20Token(data.token, "", "", 0, "")
        // We build a erc20 token wrapper to request the balance
        disposables += tokenRepository.loadTokenBalances(safe, listOf(erc20Token))
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeBy(onNext = {
                val balance = it.firstOrNull()?.second ?: BigInteger.ZERO
                view?.layout_asset_transfer_info_safe_balance?.text = token?.displayString(balance) ?: balance.asDecimalString()
            }, onError = {
                view?.layout_asset_transfer_info_safe_balance?.text = null
            })
    }

    private fun setupSafeInfo() {
        view?.apply {
            layout_asset_transfer_info_safe_name.visible(false)
            addressHelper.populateAddressInfo(
                layout_asset_transfer_info_safe_address,
                layout_asset_transfer_info_safe_name,
                layout_asset_transfer_info_safe_image,
                safe
            ).forEach { disposables.add(it) }
        }
    }

    private fun setupToInfo() {
        view?.apply {
            layout_asset_transfer_info_to_name.visible(false)
            addressHelper.populateAddressInfo(
                layout_asset_transfer_info_to_address,
                layout_asset_transfer_info_to_name,
                layout_asset_transfer_info_to_image,
                data.receiver
            ).forEach { disposables.add(it) }
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_STOP)
    fun stop() {
        disposables.clear()
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    override fun detach() {
        stop()
        view = null
    }
}
