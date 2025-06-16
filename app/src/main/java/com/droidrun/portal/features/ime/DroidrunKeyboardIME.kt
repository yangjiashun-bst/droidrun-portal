package com.droidrun.portal.features.ime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.ExtractedTextRequest
import androidx.compose.ui.platform.ComposeView
import com.droidrun.portal.features.ime.KeyboardReceiver.Companion.IME_CHARS
import com.droidrun.portal.features.ime.KeyboardReceiver.Companion.IME_CLEAR_TEXT
import com.droidrun.portal.features.ime.KeyboardReceiver.Companion.IME_EDITORCODE
import com.droidrun.portal.features.ime.KeyboardReceiver.Companion.IME_KEYCODE
import com.droidrun.portal.features.ime.KeyboardReceiver.Companion.IME_MESSAGE_B64
import com.droidrun.portal.features.ime.KeyboardReceiver.Companion.IME_META_KEYCODE

/**
 * A custom Input Method Service that allows for programmatic text input via broadcast intents.
 * This service listens for a set of internal broadcasts to perform keyboard actions such as
 * committing text, sending key events, and clearing text. It is designed to be a "portal"
 * for other applications and scripts to interact with the Android UI.
 *
 * The keyboard has a minimal UI, as it is primarily intended for programmatic use.
 */
class DroidrunKeyboardIME : InputMethodService() {
    private val TAG = "DroidrunKeyboardIME"
    private var keyboardReceiver: BroadcastReceiver? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "DroidrunKeyboardIME: onCreate() called")

        if (keyboardReceiver == null) {
            keyboardReceiver = initKeyboardReceiver()

            val filter = IntentFilter().apply {
                addAction(IME_CHARS)
                addAction(IME_KEYCODE)
                addAction(IME_META_KEYCODE)
                addAction(IME_EDITORCODE)
                addAction(IME_MESSAGE_B64)
                addAction(IME_CLEAR_TEXT)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(keyboardReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                registerReceiver(keyboardReceiver, filter, Context.RECEIVER_EXPORTED)
            }
            Log.d(TAG, "Broadcast receiver registered in onCreate()")
        }
    }

    private fun initKeyboardReceiver(): KeyboardReceiver {
        return KeyboardReceiver(
            onCommitText = { text -> currentInputConnection?.commitText(text, 1) },
            onSendKeyEvent = { keyEvent -> currentInputConnection?.sendKeyEvent(keyEvent) },
            onPerformEditorAction = { actionId ->
                currentInputConnection?.performEditorAction(
                    actionId
                )
            },
            onDeleteSurroundingText = { before, after ->
                currentInputConnection?.let {
                    val extracted = it.getExtractedText(ExtractedTextRequest(), 0)
                    if (extracted != null) {
                        val beforeLength =
                            it.getTextBeforeCursor(extracted.text.length, 0)?.length ?: 0
                        val afterLength =
                            it.getTextAfterCursor(extracted.text.length, 0)?.length ?: 0
                        it.deleteSurroundingText(beforeLength, afterLength)
                    }
                }
            }
        )
    }

    override fun onCreateInputView(): View {
        return ComposeView(this).apply {
            setContent {
                KeyboardView()
            }
        }
    }

    override fun onStartInput(attribute: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        Log.d(TAG, "onStartInput called - restarting: $restarting")
    }

    override fun onStartInputView(attribute: android.view.inputmethod.EditorInfo?, restarting: Boolean) {
        super.onStartInputView(attribute, restarting)
        Log.d(TAG, "onStartInputView called - keyboard should be visible now")
    }

    override fun onDestroy() {
        Log.d(TAG, "DroidrunKeyboardIME: onDestroy() called")
        if (keyboardReceiver != null) {
            try {
                unregisterReceiver(keyboardReceiver)
                Log.d(TAG, "Broadcast receiver unregistered")
            } catch (e: Exception) {
                Log.e(TAG, "Error unregistering receiver", e)
            }
        }
        super.onDestroy()
    }
} 