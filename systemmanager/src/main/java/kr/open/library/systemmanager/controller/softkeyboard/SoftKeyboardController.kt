package kr.open.library.systemmanager.controller.softkeyboard

import android.content.Context
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kr.open.library.logcat.Logx
import kr.open.library.systemmanager.base.BaseSystemService
import kr.open.library.systemmanager.extenstions.getInputMethodManager

/**
 * Using InputMethodManager
 * Screen Soft Keyboard control class
 * search windowSoftInputMode in https://blog.naver.com/il7942li/222671675950
 */
public open class SoftKeyboardController(context: Context) : BaseSystemService(context,null) {

    public val imm: InputMethodManager by lazy { context.getInputMethodManager() }

    /**
     * can config in manifest
     * android:windowSoftInputMode="SOFT_INPUT_ADJUST_PAN"
     */
    public fun setAdjustPan(window: Window) {
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)
    }

    /**
     * can config in manifest
     * android:windowSoftInputMode=""
     *
     * ex) WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN or WindowManager.LayoutParams.SOFT_INPUT_MASK_STATE or
     */
    public fun setSoftInputMode(window: Window, softInputTypes: Int) {
        window.setSoftInputMode(softInputTypes)
    }

    /**
     * View that can show input text)  EditText, SearchView ... etc.
     * default flag 0, select 0, SHOW_IMPLICIT, SHOW_FORCED(API 33 Deprecated)
     */
    public fun show(v: View, flag: Int = InputMethodManager.SHOW_IMPLICIT): Boolean =
        if (v.requestFocus()) {
            imm.showSoftInput(v, flag)
        } else {
            Logx.e("view requestFocus() is false!!")
            false
        }


    /**
     * View that can show input text delay(ms))  EditText, SearchView ... etc.
     * default flag 0, select 0, SHOW_IMPLICIT, SHOW_FORCED(API 33 Deprecated)
     *
     * @return true if the Runnable was successfully placed in to the
     *         message queue.  Returns false on failure, usually because the
     *         looper processing the message queue is exiting.  Note that a
     *         result of true does not mean the Runnable will be processed --
     *         if the looper is quit before the delivery time of the message
     *         occurs then the message will be dropped.
     */
    public fun showDelay(v: View, delay: Long, flag: Int = InputMethodManager.SHOW_IMPLICIT): Boolean =
        v.postDelayed(Runnable { show(v, flag) }, delay)

    public fun showDelay(
        v: View,
        delay: Long,
        flag: Int = InputMethodManager.SHOW_IMPLICIT,
        coroutineScope: CoroutineScope
    ) {
        coroutineScope.launch {
            delay(delay)
            show(v, flag)
        }
    }

    /**
     * View that can hide input text)  EditText, SearchView ... etc.
     * default flag 0,select flat 0, HIDE_IMPLICIT_ONLY, HIDE_NOT_ALWAYS
     */
    public fun hide(v: View, flag: Int = 0): Boolean = if (v.requestFocus()) {
        imm.hideSoftInputFromWindow(v.windowToken, flag)
    } else {
        Logx.e("view requestFocus() is false!!")
        false
    }


    /**
     * View that can hide input text delay(ms))  EditText, SearchView ... etc.
     * default flag 0,select flat 0, HIDE_IMPLICIT_ONLY, HIDE_NOT_ALWAYS
     *
     * @return true if the Runnable was successfully placed in to the
     *         message queue.  Returns false on failure, usually because the
     *         looper processing the message queue is exiting.  Note that a
     *         result of true does not mean the Runnable will be processed --
     *         if the looper is quit before the delivery time of the message
     *         occurs then the message will be dropped.
     */
    public fun hideDelay(v: View, delay: Long, flag: Int = 0): Boolean =
        v.postDelayed(Runnable {    hide(v, flag)   }, delay)

    public fun hideDelay(v: View, delay: Long, flag: Int = 0, coroutineScope: CoroutineScope) {
        coroutineScope.launch {
            delay(delay)
            hide(v,flag)
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    public fun startStylusHandwriting(v: View) {
        if (v.requestFocus()) { imm.startStylusHandwriting(v) }
        else { Logx.e("[ERROR]view requestFocus() is false!!") }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    public fun startStylusHandwriting(v: View, delay:Long): Boolean =
        v.postDelayed(Runnable { startStylusHandwriting(v) },delay)

}