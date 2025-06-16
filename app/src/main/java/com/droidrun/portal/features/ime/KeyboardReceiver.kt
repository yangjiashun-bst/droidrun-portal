package com.droidrun.portal.features.ime

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Base64
import android.util.Log
import android.view.KeyEvent
import android.view.inputmethod.InputConnection

/**
 * A standalone [BroadcastReceiver] that handles various internal intents for the keyboard.
 * It is designed to be instantiated by an [InputMethodService] and processes intents
 * to perform keyboard actions like committing text, sending key events, and clearing text.
 *
 * @param onCommitText A lambda function that takes a [String] and commits it to the current input connection.
 * @param onSendKeyEvent A lambda function that takes a [KeyEvent] and sends it to the current input connection.
 * @param onPerformEditorAction A lambda function that takes an editor action ID and performs it.
 * @param onDeleteSurroundingText A lambda function that deletes text around the cursor.
 */
class KeyboardReceiver(
    private val onCommitText: (String) -> Unit,
    private val onSendKeyEvent: (KeyEvent) -> Unit,
    private val onPerformEditorAction: (Int) -> Unit,
    private val onDeleteSurroundingText: (Int, Int) -> Unit
) : BroadcastReceiver() {

    private val TAG = "DroidrunKeyboardReceiver"

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "Received broadcast with action: ${intent.action}")

        when (intent.action) {
            IME_MESSAGE_B64 -> {
                intent.getStringExtra("msg")?.let {
                    try {
                        val decoded = String(Base64.decode(it, Base64.DEFAULT), Charsets.UTF_8)
                        onCommitText(decoded)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error decoding base64 message", e)
                    }
                }
            }
            IME_CHARS -> {
                intent.getIntArrayExtra("chars")?.let {
                    val msg = String(it.map { code -> code.toChar() }.toCharArray())
                    onCommitText(msg)
                }
            }
            IME_KEYCODE -> {
                val code = intent.getIntExtra("code", -1)
                if (code != -1) {
                    onSendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
                    onSendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
                }
            }
            IME_EDITORCODE -> {
                val code = intent.getIntExtra("code", -1)
                if (code != -1) {
                    onPerformEditorAction(code)
                }
            }
            IME_CLEAR_TEXT -> {
                // This action requires access to the InputConnection, which we don't have directly.
                // The deletion logic is now handled via the passed-in lambda.
                onDeleteSurroundingText(1000, 1000) // A simple implementation; could be more sophisticated.
            }
            IME_META_KEYCODE -> {
                intent.getStringExtra("mcode")?.let {
                    val mcodes = it.split(",")
                    if (mcodes.size > 1) {
                        var i = 0
                        while (i < mcodes.size - 1) {
                            val ke = if (mcodes[i].contains("+")) {
                                val arrCode = mcodes[i].split("+")
                                KeyEvent(
                                    0, 0,
                                    KeyEvent.ACTION_DOWN,
                                    mcodes[i + 1].toInt(),
                                    0,
                                    arrCode[0].toInt() or arrCode[1].toInt(),
                                    0, 0,
                                    KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE,
                                    android.view.InputDevice.SOURCE_KEYBOARD
                                )
                            } else {
                                KeyEvent(
                                    0, 0,
                                    KeyEvent.ACTION_DOWN,
                                    mcodes[i + 1].toInt(),
                                    0,
                                    mcodes[i].toInt(),
                                    0, 0,
                                    KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE,
                                    android.view.InputDevice.SOURCE_KEYBOARD
                                )
                            }
                            onSendKeyEvent(ke)
                            i += 2
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val IME_CHARS = "com.droidrun.portal.DROIDRUN_INPUT_CHARS"
        const val IME_KEYCODE = "com.droidrun.portal.DROIDRUN_INPUT_CODE"
        const val IME_META_KEYCODE = "com.droidrun.portal.DROIDRUN_INPUT_MCODE"
        const val IME_EDITORCODE = "com.droidrun.portal.DROIDRUN_EDITOR_CODE"
        const val IME_MESSAGE_B64 = "com.droidrun.portal.INTERNAL_INPUT_B64"
        const val IME_CLEAR_TEXT = "com.droidrun.portal.DROIDRUN_CLEAR_TEXT"
    }
}