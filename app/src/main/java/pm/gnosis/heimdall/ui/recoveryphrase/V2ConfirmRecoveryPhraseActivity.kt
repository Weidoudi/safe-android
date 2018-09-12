package pm.gnosis.heimdall.ui.recoveryphrase

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v7.widget.GridLayoutManager
import android.widget.TextView
import com.jakewharton.rxbinding2.view.clicks
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.android.synthetic.main.layout_confirm_recovery_phrase.*
import pm.gnosis.heimdall.HeimdallApplication
import pm.gnosis.heimdall.R
import pm.gnosis.heimdall.di.components.DaggerViewComponent
import pm.gnosis.heimdall.di.modules.ViewModule
import pm.gnosis.heimdall.helpers.ToolbarHelper
import pm.gnosis.heimdall.reporting.ScreenId
import pm.gnosis.heimdall.ui.base.BaseActivity
import pm.gnosis.svalinn.common.utils.getColorCompat
import pm.gnosis.svalinn.common.utils.subscribeForResult
import pm.gnosis.utils.words
import timber.log.Timber
import java.security.SecureRandom
import javax.inject.Inject

class V2ConfirmRecoveryPhraseActivity : BaseActivity() {
    override fun screenId() = ScreenId.CONFIRM_RECOVERY_PHRASE

    private val layoutManager = GridLayoutManager(this, 3, GridLayoutManager.VERTICAL, false)

    @Inject
    lateinit var toolbarHelper: ToolbarHelper

    @Inject
    lateinit var adapter: V2ConfirmRecoveryPhraseAdapter

    @Inject
    lateinit var viewModel: ConfirmRecoveryPhraseContract

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        skipSecurityCheck()
        inject()
        setContentView(R.layout.layout_confirm_recovery_phrase)
        viewModel.setRecoveryPhrase(intent.getStringExtra(EXTRA_RECOVERY_PHRASE))

        layout_confirm_recovery_phrase_recycler_view.apply {
            setHasFixedSize(true)
            layoutManager = this@V2ConfirmRecoveryPhraseActivity.layoutManager
            adapter = this@V2ConfirmRecoveryPhraseActivity.adapter
            addItemDecoration(RecoveryPhraseItemDecoration(this@V2ConfirmRecoveryPhraseActivity, R.dimen.horizontal_offset, R.dimen.vertical_offset))
        }

        val words = RECOVERY_PHRASE.words()
        val positionsToFill = getRandomPositions(SELECTABLE_WORDS, words)
        adapter.setWords(words, positionsToFill)

        layout_confirm_recovery_phrase_word_1.text = words[positionsToFill[0]]
        layout_confirm_recovery_phrase_word_2.text = words[positionsToFill[1]]
        layout_confirm_recovery_phrase_word_3.text = words[positionsToFill[2]]
        layout_confirm_recovery_phrase_word_4.text = words[positionsToFill[3]]
    }

    override fun onStart() {
        super.onStart()
        disposables.addAll(
            subscribeWordSelection(layout_confirm_recovery_phrase_word_1),
            subscribeWordSelection(layout_confirm_recovery_phrase_word_2),
            subscribeWordSelection(layout_confirm_recovery_phrase_word_3),
            subscribeWordSelection(layout_confirm_recovery_phrase_word_4)
        )

        disposables += adapter.observeSelectedCount()
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeBy(onNext = { selectedCount ->
                layout_confirm_recovery_phrase_submit.isEnabled = selectedCount == SELECTABLE_WORDS
            }, onError = Timber::e)

        disposables += layout_confirm_recovery_phrase_submit.clicks()
            .switchMapSingle { viewModel.getIncorrectPositions(adapter.getWords()) }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribeForResult(onNext = ::onIncorrectPositions, onError = Timber::e)
    }

    private fun onIncorrectPositions(incorrectPositions: Set<Int>) {
        adapter.setIncorrectPositions(incorrectPositions)
    }

    private fun subscribeWordSelection(wordView: TextView) =
        wordView.clicks()
            .subscribeBy(onNext = {
                adapter.pushWord(wordView.text.toString())
                setWord(isEnabled = false, wordView = wordView)
            }, onError = Timber::e)

    override fun onBackPressed() {
        if (adapter.getSelectedCount() == 0) super.onBackPressed()
        else {
            val poppedWord = adapter.popWord()
            when (poppedWord) {
                layout_confirm_recovery_phrase_word_1.text -> setWord(isEnabled = true, wordView = layout_confirm_recovery_phrase_word_1)
                layout_confirm_recovery_phrase_word_2.text -> setWord(isEnabled = true, wordView = layout_confirm_recovery_phrase_word_2)
                layout_confirm_recovery_phrase_word_3.text -> setWord(isEnabled = true, wordView = layout_confirm_recovery_phrase_word_3)
                layout_confirm_recovery_phrase_word_4.text -> setWord(isEnabled = true, wordView = layout_confirm_recovery_phrase_word_4)
            }
        }
    }

    private fun setWord(isEnabled: Boolean, wordView: TextView) {
        wordView.isClickable = isEnabled
        wordView.setTextColor(getColorCompat(if (isEnabled) R.color.word_recovery_phrase_picker else R.color.word_recovery_phrase_picker_disabled))
    }

    private fun getRandomPositions(n: Int, collection: Collection<*>) =
        (0 until collection.size).shuffled(SecureRandom()).take(n)

    private fun inject() {
        DaggerViewComponent.builder()
            .applicationComponent(HeimdallApplication[this])
            .viewModule(ViewModule(this))
            .build().inject(this)
    }

    companion object {
        private const val EXTRA_RECOVERY_PHRASE = "extra.string.recovery_phrase"
        private const val SELECTABLE_WORDS = 4
        const val RECOVERY_PHRASE = "test ola adeus try again later another room don't know what to"

        fun createIntent(context: Context, recoveryPhrase: String) = Intent(context, V2ConfirmRecoveryPhraseActivity::class.java).apply {
            putExtra(EXTRA_RECOVERY_PHRASE, recoveryPhrase)
        }
    }
}
