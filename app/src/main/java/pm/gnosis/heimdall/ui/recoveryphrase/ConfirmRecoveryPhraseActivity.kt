package pm.gnosis.heimdall.ui.recoveryphrase

import android.os.Bundle
import android.support.v7.widget.GridLayoutManager
import android.view.ViewGroup
import android.widget.TextView
import com.jakewharton.rxbinding2.view.clicks
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.android.synthetic.main.layout_confirm_safe_recovery_phrase.*
import pm.gnosis.heimdall.R
import pm.gnosis.heimdall.helpers.ToolbarHelper
import pm.gnosis.heimdall.reporting.ScreenId
import pm.gnosis.heimdall.ui.base.ViewModelActivity
import pm.gnosis.svalinn.common.utils.getColorCompat
import pm.gnosis.svalinn.common.utils.subscribeForResult
import timber.log.Timber
import javax.inject.Inject


abstract class ConfirmRecoveryPhraseActivity<VM : ConfirmRecoveryPhraseContract> : ViewModelActivity<VM>() {
    override fun screenId() = ScreenId.CONFIRM_RECOVERY_PHRASE

    // TODO Span count
    private val layoutManager = GridLayoutManager(this, 4, GridLayoutManager.VERTICAL, false)

    // Default composite disposable gets cleared on onStop while clicks need to survive the lifetime of the activity
    private var wordClickDisposables = CompositeDisposable()

    @Inject
    lateinit var toolbarHelper: ToolbarHelper

    @Inject
    lateinit var adapter: ConfirmRecoveryPhraseAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val encryptedMnemonic = intent.getStringExtra(EXTRA_ENCRYPTED_MNEMONIC)
        if (encryptedMnemonic == null) {
            finish(); return
        }

        disposables += viewModel.setup(encryptedMnemonic)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeBy(onSuccess = ::onSuccessfulSetup, onError = { finish() })

        layout_confirm_safe_recovery_phrase_selected_words.apply {
            setHasFixedSize(true)
            layoutManager = this@ConfirmRecoveryPhraseActivity.layoutManager
            adapter = this@ConfirmRecoveryPhraseActivity.adapter
        }
    }

    override fun onStart() {
        super.onStart()
        disposables += layout_confirm_safe_recovery_phrase_back.clicks()
            .subscribeBy(onNext = { finish() }, onError = Timber::e)

        disposables += toolbarHelper.setupShadow(
            layout_confirm_safe_recovery_phrase_toolbar_shadow,
            layout_confirm_safe_recovery_phrase_content_scroll
        )
    }

    private fun onIsCorrectSequence(isCorrectSequence: Boolean) {
        bottomBarEnabled(isCorrectSequence)
    }

    private fun onIsCorrectSequenceError(throwable: Throwable) {
        Timber.e(throwable)
        bottomBarEnabled(false)
    }

    protected fun bottomBarEnabled(enable: Boolean) {
        layout_confirm_safe_recovery_phrase_finish.isEnabled = enable
        layout_confirm_safe_recovery_phrase_bottom_bar.setBackgroundColor(getColorCompat(if (enable) R.color.azure else R.color.bluey_grey))
    }

    private fun onSuccessfulSetup(words: List<String>) {
        words.forEachIndexed { index, word ->
            when {
                index < 3 -> addWordEntry(word, layout_confirm_safe_recovery_phrase_first_column)
                index < 6 -> addWordEntry(word, layout_confirm_safe_recovery_phrase_second_column)
                index < 9 -> addWordEntry(word, layout_confirm_safe_recovery_phrase_third_column)
                index < 12 -> addWordEntry(word, layout_confirm_safe_recovery_phrase_fourth_column)
            }
        }

        // When the adapter data changes we check if it has the correct sequence
        wordClickDisposables += adapter.observeWords()
            .doOnNext { bottomBarEnabled(false) }
            .flatMapSingle { viewModel.isCorrectSequence(it) }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeForResult(onNext = ::onIsCorrectSequence, onError = ::onIsCorrectSequenceError)
    }

    private fun addWordEntry(word: String, viewGroup: ViewGroup) =
        (layoutInflater.inflate(R.layout.layout_mnemonic_word_picker, viewGroup, false) as TextView).apply {
            text = word
            wordClickDisposables += clicks().subscribeBy(onNext = {
                adapter.addWord(word)
                layoutManager.scrollToPosition(adapter.itemCount - 1)
            }, onError = Timber::e)
            viewGroup.addView(this)
        }

    override fun layout() = R.layout.layout_confirm_safe_recovery_phrase

    override fun onDestroy() {
        super.onDestroy()
        wordClickDisposables.clear()
    }

    companion object {
        const val EXTRA_ENCRYPTED_MNEMONIC = "extra.string.encrypted_mnemonic"
    }
}
