package pm.gnosis.heimdall.ui.transactions.details.safe

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.gojuno.koptional.Optional
import com.gojuno.koptional.toOptional
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.android.synthetic.main.layout_review_change_safe_owner.*
import pm.gnosis.heimdall.R
import pm.gnosis.heimdall.common.di.components.ApplicationComponent
import pm.gnosis.heimdall.common.di.components.DaggerViewComponent
import pm.gnosis.heimdall.common.di.modules.ViewModule
import pm.gnosis.heimdall.common.utils.Result
import pm.gnosis.heimdall.common.utils.mapToResult
import pm.gnosis.heimdall.common.utils.visible
import pm.gnosis.heimdall.ui.transactions.details.base.BaseReviewTransactionDetailsFragment
import pm.gnosis.heimdall.ui.transactions.details.safe.ChangeSafeSettingsDetailsContract.Action.*
import pm.gnosis.models.Transaction
import pm.gnosis.models.TransactionParcelable
import pm.gnosis.utils.asEthereumAddressStringOrNull
import pm.gnosis.utils.hexAsBigIntegerOrNull
import timber.log.Timber
import java.math.BigInteger
import javax.inject.Inject


class ReviewChangeDeviceSettingsDetailsFragment : BaseReviewTransactionDetailsFragment() {
    @Inject
    lateinit var subViewModel: ChangeSafeSettingsDetailsContract

    private var transaction: Transaction? = null
    private var safe: BigInteger? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        safe = arguments?.getString(ARG_SAFE)?.hexAsBigIntegerOrNull()
        transaction = arguments?.getParcelable<TransactionParcelable>(ARG_TRANSACTION)?.transaction
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View?
            = inflater.inflate(R.layout.layout_review_change_safe_owner, container, false)

    override fun onStart() {
        super.onStart()

        disposables += subViewModel.loadAction(safe, transaction)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(::setupForm, Timber::e)

        safe?.let {
            disposables += Observable.just(it)
                    .compose(updateSafeInfoTransformer(layout_review_change_safe_address))
                    .subscribeBy(onError = Timber::e)
        }
    }

    private fun setupForm(action: ChangeSafeSettingsDetailsContract.Action) {
        when (action) {
            is RemoveOwner -> {
                layout_review_change_safe_primary_target_label.setText(R.string.old_owner)
                layout_review_change_safe_primary_target_value.text = action.owner.asEthereumAddressStringOrNull()
                layout_review_change_safe_primary_target_icon.setAddress(action.owner)
                layout_review_change_safe_secondary_target_container.visible(false)
                layout_review_change_safe_action.setText(R.string.transaction_description_remove_safe_owner)
            }
            is AddOwner -> {
                layout_review_change_safe_primary_target_label.setText(R.string.new_owner)
                layout_review_change_safe_primary_target_value.text = action.owner.asEthereumAddressStringOrNull()
                layout_review_change_safe_primary_target_icon.setAddress(action.owner)
                layout_review_change_safe_secondary_target_container.visible(false)
                layout_review_change_safe_action.setText(R.string.transaction_description_add_safe_owner)
            }
            is ReplaceOwner -> {
                layout_review_change_safe_primary_target_label.setText(R.string.new_owner)
                layout_review_change_safe_primary_target_value.text = action.newOwner.asEthereumAddressStringOrNull()
                layout_review_change_safe_primary_target_icon.setAddress(action.newOwner)
                layout_review_change_safe_secondary_target_container.visible(true)
                layout_review_change_safe_secondary_target_label.setText(R.string.old_owner)
                layout_review_change_safe_secondary_target_value.text = action.previousOwner.asEthereumAddressStringOrNull()
                layout_review_change_safe_primary_target_icon.setAddress(action.previousOwner)
                layout_review_change_safe_action.setText(R.string.transaction_description_replace_safe_owner)
            }
        }
    }

    override fun observeTransaction(): Observable<Result<Transaction>> {
        return Observable.just(transaction.toOptional())
                .flatMap { it.toNullable()?.let { Observable.just(it) ?: Observable.empty() } }
                .mapToResult()
    }

    override fun observeSafe(): Observable<Optional<BigInteger>> =
            Observable.just(safe.toOptional())

    override fun inject(component: ApplicationComponent) {
        DaggerViewComponent.builder()
                .applicationComponent(component)
                .viewModule(ViewModule(activity!!))
                .build().inject(this)
    }

    companion object {

        private const val ARG_TRANSACTION = "argument.parcelable.transaction"
        private const val ARG_SAFE = "argument.string.safe"

        fun createInstance(transaction: Transaction?, safeAddress: String?) =
                ReviewChangeDeviceSettingsDetailsFragment().apply {
                    arguments = Bundle().apply {
                        putParcelable(ARG_TRANSACTION, transaction?.parcelable())
                        putString(ARG_SAFE, safeAddress)
                    }
                }
    }

}
