package pm.gnosis.heimdall.ui.recoveryphrase

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.support.v7.widget.GridLayoutManager
import android.util.DisplayMetrics
import com.jakewharton.rxbinding2.view.clicks
import io.reactivex.Single
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import kotlinx.android.synthetic.main.layout_setup_recovery_phrase.*
import pm.gnosis.heimdall.HeimdallApplication
import pm.gnosis.heimdall.R
import pm.gnosis.heimdall.di.components.DaggerViewComponent
import pm.gnosis.heimdall.di.modules.ViewModule
import pm.gnosis.heimdall.helpers.ToolbarHelper
import pm.gnosis.heimdall.reporting.ScreenId
import pm.gnosis.heimdall.ui.base.BaseActivity
import pm.gnosis.heimdall.utils.scaleBitmapToWidth
import pm.gnosis.utils.words
import timber.log.Timber
import javax.inject.Inject


class V2SetupRecoveryPhraseActivity : BaseActivity() {
    override fun screenId(): ScreenId = ScreenId.CONFIRM_RECOVERY_PHRASE

    private val layoutManager = GridLayoutManager(this, 3, GridLayoutManager.VERTICAL, false)

    @Inject
    lateinit var toolbarHelper: ToolbarHelper

    @Inject
    lateinit var adapter: V2SetupRecoveryPhraseAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        skipSecurityCheck()
        inject()
        setContentView(R.layout.layout_setup_recovery_phrase)

        layout_setup_recovery_phrase_recycler_view.apply {
            setHasFixedSize(true)
            layoutManager = this@V2SetupRecoveryPhraseActivity.layoutManager
            adapter = this@V2SetupRecoveryPhraseActivity.adapter
            addItemDecoration(RecoveryPhraseItemDecoration(this@V2SetupRecoveryPhraseActivity, R.dimen.horizontal_offset, R.dimen.vertical_offset))
        }

        adapter.setWords(RECOVERY_PHRASE.words())
    }

    override fun onStart() {
        super.onStart()
        disposables += Single.fromCallable {
            val displayMetrics = DisplayMetrics()
            windowManager.defaultDisplay.getMetrics(displayMetrics)
            scaleBitmapToWidth(
                bitmap = BitmapFactory.decodeResource(resources, R.drawable.ic_bottom_waves),
                targetWidth = displayMetrics.widthPixels
            )
        }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeBy(onSuccess = { layout_setup_recovery_phrase_waves.setImageBitmap(it) }, onError = Timber::e)

        disposables += toolbarHelper.setupShadow(layout_setup_recovery_phrase_toolbar_shadow, layout_setup_recovery_phrase_scroll_view)

        disposables += layout_setup_recovery_phrase_next.clicks()
            .subscribeBy(onNext = { startActivity(V2ConfirmRecoveryPhraseActivity.createIntent(this, RECOVERY_PHRASE)) }, onError = Timber::e)

        disposables += layout_setup_recovery_phrase_back_button.clicks()
            .subscribeBy(onNext = { onBackPressed() }, onError = Timber::e)
    }

    private fun inject() {
        DaggerViewComponent.builder()
            .applicationComponent(HeimdallApplication[this])
            .viewModule(ViewModule(this))
            .build().inject(this)
    }

    companion object {
        const val RECOVERY_PHRASE = "test ola adeus try again later another room don't know what to"

        fun createIntent(context: Context) = Intent(context, V2SetupRecoveryPhraseActivity::class.java)
    }
}
