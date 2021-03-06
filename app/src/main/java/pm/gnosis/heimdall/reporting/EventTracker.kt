package pm.gnosis.heimdall.reporting

import android.app.Activity
import io.reactivex.Single


interface EventTracker {
    fun setCurrentScreenId(activity: Activity, id: ScreenId)

    fun submit(event: Event)

    fun loadTrackingIdentifier(): Single<String>
}

sealed class Event {
    class SubmittedTransaction : Event()
    class SignedTransaction : Event()
    data class ScreenView(val id: ScreenId) : Event()
    data class ButtonClick(val id: ButtonId) : Event()
    data class TabSelect(val id: TabId) : Event()
}

enum class ScreenId {
    ACCOUNT,
    ADDRESS_BOOK,
    ADDRESS_BOOK_EDIT_ENTRY,
    ADDRESS_BOOK_ENTRY,
    ADDRESS_BOOK_ENTRY_DETAILS,
    CONFIRM_RECOVERY_PHRASE,
    CONNECT_BROWSER_EXTENSION,
    DEBUG_SETTINGS,
    DISPLAY_RECOVERY_PHRASE,
    FINGERPRINT,
    IDENTIFICATION_FINGERPRINT,
    IDENTIFICATION_KEYBOARD,
    INCOMING_TRANSACTION_REVIEW,
    INPUT_SAFE_ADDRESS,
    INPUT_RECOVERY_PHRASE,
    MANAGE_TOKENS,
    MENU,
    MENU_EXPANDED,
    NEW_SAFE_AWAIT_DEPLOYMENT,
    NEW_SAFE_AWAIT_FUNDS,
    NEW_SAFE_START,
    NO_SAFES,
    PASSWORD,
    PASSWORD_CONFIRM,
    QR_CODE_SCANNER,
    RECOVER_SAFE_AWAIT_FUNDS,
    RECOVER_SAFE_AWAIT_RECOVERY,
    RECOVER_SAFE_REVIEW,
    REPLACE_BROWSER_EXTENSION_RECOVERY_PHRASE,
    REPLACE_BROWSER_EXTENSION_REVIEW,
    SAFE_ASSETS_VIEW,
    SAFE_CONFIRM_REMOVE,
    SAFE_EDIT_NAME,
    SAFE_MAIN,
    SAFE_SETTINGS,
    SAFE_SHARE_ADDRESS,
    SAFE_TRANSACTION_LIST,
    SELECT_SAFE,
    SETTINGS,
    SETTINGS_CHANGE_PASSWORD,
    SETTINGS_ENABLE_FINGERPRINT,
    SPLASH,
    TRANSACTION_DETAILS,
    TRANSACTION_ENTER_DATA,
    TRANSACTION_PICK_TOKEN,
    TRANSACTION_REVIEW,
    UNLOCK,
    UNLOCK_FINGERPRINT,
    UNLOCK_KEYBOARD,
    WELCOME,
    WELCOME_TERMS,

    DIALOG_SHARE_ADDRESS,
}

enum class ButtonId

enum class TabId {
    SAFE_DETAILS_ASSETS,
    SAFE_DETAILS_TRANSACTIONS,
}
