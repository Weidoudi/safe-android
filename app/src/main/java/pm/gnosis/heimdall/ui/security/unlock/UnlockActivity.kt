package pm.gnosis.heimdall.ui.security.unlock

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.animation.*
import android.view.inputmethod.EditorInfo
import com.jakewharton.rxbinding2.view.clicks
import com.jakewharton.rxbinding2.widget.textChanges
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.subjects.PublishSubject
import kotlinx.android.synthetic.main.layout_unlock.*
import pm.gnosis.heimdall.HeimdallApplication
import pm.gnosis.heimdall.R
import pm.gnosis.heimdall.common.di.components.DaggerViewComponent
import pm.gnosis.heimdall.common.di.modules.ViewModule
import pm.gnosis.heimdall.common.utils.showKeyboardForView
import pm.gnosis.heimdall.common.utils.subscribeForResult
import pm.gnosis.heimdall.common.utils.vibrate
import pm.gnosis.heimdall.reporting.ScreenId
import pm.gnosis.heimdall.security.FingerprintUnlockFailed
import pm.gnosis.heimdall.security.FingerprintUnlockHelp
import pm.gnosis.heimdall.security.FingerprintUnlockResult
import pm.gnosis.heimdall.security.FingerprintUnlockSuccessful
import pm.gnosis.heimdall.ui.base.BaseActivity
import pm.gnosis.heimdall.utils.errorToast
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Inject

class UnlockActivity : BaseActivity() {
    override fun screenId() = ScreenId.UNLOCK

    @Inject
    lateinit var viewModel: UnlockContract

    private val nextClickSubject = PublishSubject.create<Unit>()

    private var handleRotation = 0f

    private var fingerPrintDisposable: Disposable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        skipSecurityCheck()
        super.onCreate(savedInstanceState)
        inject()
        setContentView(R.layout.layout_unlock)

        if (intent?.getBooleanExtra(EXTRA_CLOSE_APP, false) == true) {
            finish()
        }

        layout_unlock_password_input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                nextClickSubject.onNext(Unit)
            }
            true
        }
    }

    override fun onStart() {
        super.onStart()
        fingerPrintDisposable = encryptionManager.isFingerPrintSet()
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSuccess {
                    layout_unlock_fingerprint.visibility = if (it) View.VISIBLE else View.GONE
                    layout_unlock_password_input.visibility = if (it) View.GONE else View.VISIBLE
                }
                .flatMapObservable {
                    if (it) viewModel.observeFingerprint() else Observable.empty()
                }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeForResult(onNext = ::onFingerprintResult, onError = ::onFingerprintError)

        disposables += viewModel.checkState()
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeForResult(onNext = ::onState, onError = ::onStateCheckError)

        disposables += nextClickSubject
                .flatMap { viewModel.unlock(layout_unlock_password_input.text.toString()) }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribeForResult(onNext = ::onState, onError = ::onUnlockError)

        disposables += layout_unlock_password_input.textChanges()
                .window(200, TimeUnit.MILLISECONDS)
                .flatMapMaybe { it.lastElement() }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe({
                    animateHandle(it.length * 15f, interpolator = DecelerateInterpolator())
                }, Timber::e)

        disposables += layout_unlock_switch_to_password.clicks()
                .subscribeBy(onNext = {
                    fingerPrintDisposable?.dispose()
                    layout_unlock_fingerprint.visibility = View.GONE
                    layout_unlock_password_input.visibility = View.VISIBLE
                    showKeyboardForView(layout_unlock_password_input)
                }, onError = Timber::e)
    }

    private fun onFingerprintResult(result: FingerprintUnlockResult) {
        layout_unlock_error.visibility = View.INVISIBLE
        when (result) {
            is FingerprintUnlockSuccessful -> onState(UnlockContract.State.UNLOCKED)
            is FingerprintUnlockFailed -> {
                vibrate(200)
                animateHandleOnError()
                layout_unlock_error.visibility = View.VISIBLE
                layout_unlock_error.text = getString(R.string.fingerprint_not_recognized)
            }
            is FingerprintUnlockHelp -> {
                result.message?.let {
                    layout_unlock_error.visibility = View.VISIBLE
                    layout_unlock_error.text = it
                }
            }
        }
    }

    private fun onFingerprintError(throwable: Throwable) {
        Timber.e(throwable)
        animateHandleOnError()
        layout_unlock_fingerprint.visibility = View.GONE
        layout_unlock_password_input.visibility = View.VISIBLE
        onStateCheckError(throwable)
    }

    private fun onState(state: UnlockContract.State) {
        when (state) {
            UnlockContract.State.UNINITIALIZED -> startActivity(createIntentToCloseApp(this))
            UnlockContract.State.UNLOCKED -> animateHandle(handleRotation + 360f, { finish() })
            UnlockContract.State.LOCKED -> {
            }
        }
    }

    private fun animateHandle(rotation: Float, endAction: () -> Unit = {}, interpolator: Interpolator = LinearInterpolator()) {
        handleRotation = rotation
        layout_unlock_handle.clearAnimation()
        layout_unlock_handle.animate()
                .rotation(handleRotation)
                .setDuration(300)
                .setInterpolator(interpolator)
                .withEndAction(endAction)
    }

    override fun onStop() {
        layout_unlock_handle.clearAnimation()
        layout_unlock_handle.animate().cancel()
        fingerPrintDisposable?.dispose()
        super.onStop()
    }

    override fun onBackPressed() {
        startActivity(UnlockActivity.createIntentToCloseApp(this))
    }

    private fun onStateCheckError(throwable: Throwable) {
        errorToast(throwable)
    }

    private fun onUnlockError(throwable: Throwable) {
        Timber.e(throwable)
        animateHandleOnError()
        errorToast(throwable)
    }

    private fun animateHandleOnError() {
        val rotationAnimation = RotateAnimation(0f, 20f,
                Animation.RELATIVE_TO_SELF, 0.5f,
                Animation.RELATIVE_TO_SELF, 0.5f)
                .apply {
                    duration = 50
                    repeatCount = 3
                    repeatMode = Animation.REVERSE
                }
        layout_unlock_handle.startAnimation(rotationAnimation)
    }

    private fun inject() {
        DaggerViewComponent.builder()
                .applicationComponent(HeimdallApplication[this].component)
                .viewModule(ViewModule(this))
                .build()
                .inject(this)
    }

    companion object {
        private const val EXTRA_CLOSE_APP = "extra.boolean.close_app"

        fun createIntent(context: Context) = Intent(context, UnlockActivity::class.java)

        private fun createIntentToCloseApp(context: Context): Intent {
            val intent = Intent(context, UnlockActivity::class.java)
            intent.putExtra(EXTRA_CLOSE_APP, true)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            return intent
        }
    }
}