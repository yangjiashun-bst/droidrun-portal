package com.droidrun.portal

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.inputmethodservice.InputMethodService
import android.os.Build
import android.util.Base64
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection

class DroidrunKeyboardIME : InputMethodService() {
    private val TAG = "DroidrunKeyboardIME"
    private val IME_MESSAGE = "DROIDRUN_INPUT_TEXT"
    private val IME_CHARS = "DROIDRUN_INPUT_CHARS"
    private val IME_KEYCODE = "DROIDRUN_INPUT_CODE"
    private val IME_META_KEYCODE = "DROIDRUN_INPUT_MCODE"
    private val IME_EDITORCODE = "DROIDRUN_EDITOR_CODE"
    private val IME_MESSAGE_B64 = "DROIDRUN_INPUT_B64"
    private val IME_CLEAR_TEXT = "DROIDRUN_CLEAR_TEXT"
    private var mReceiver: BroadcastReceiver? = null

    override fun onCreateInputView(): View {
        val mInputView = layoutInflater.inflate(R.layout.keyboard_view, null)

        if (mReceiver == null) {
            val filter = IntentFilter().apply {
                addAction(IME_MESSAGE)
                addAction(IME_CHARS)
                addAction(IME_KEYCODE)
                addAction(IME_META_KEYCODE)
                addAction(IME_EDITORCODE)
                addAction(IME_MESSAGE_B64)
                addAction(IME_CLEAR_TEXT)
            }
            mReceiver = KeyboardReceiver()
            
            // Handle different API levels for receiver registration
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(mReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                registerReceiver(mReceiver, filter)
            }
        }

        return mInputView
    }

    override fun onDestroy() {
        if (mReceiver != null) {
            unregisterReceiver(mReceiver)
        }
        super.onDestroy()
    }

    inner class KeyboardReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                IME_MESSAGE -> {
                    // Normal text message
                    val msg = intent.getStringExtra("msg")
                    if (msg != null) {
                        val ic = currentInputConnection
                        ic?.commitText(msg, 1)
                    }

                    // Meta codes
                    val metaCodes = intent.getStringExtra("mcode")
                    metaCodes?.let {
                        val mcodes = it.split(",")
                        if (mcodes.size > 1) {
                            val ic = currentInputConnection
                            var i = 0
                            while (i < mcodes.size - 1) {
                                if (ic != null) {
                                    val ke = if (mcodes[i].contains("+")) {
                                        // Check metaState if more than one
                                        val arrCode = mcodes[i].split("+")
                                        KeyEvent(
                                            0, 0,
                                            KeyEvent.ACTION_DOWN,
                                            mcodes[i + 1].toInt(),
                                            0,
                                            arrCode[0].toInt() or arrCode[1].toInt(),
                                            0, 0,
                                            KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE,
                                            InputDevice.SOURCE_KEYBOARD
                                        )
                                    } else {
                                        // Only one metaState
                                        KeyEvent(
                                            0, 0,
                                            KeyEvent.ACTION_DOWN,
                                            mcodes[i + 1].toInt(),
                                            0,
                                            mcodes[i].toInt(),
                                            0, 0,
                                            KeyEvent.FLAG_SOFT_KEYBOARD or KeyEvent.FLAG_KEEP_TOUCH_MODE,
                                            InputDevice.SOURCE_KEYBOARD
                                        )
                                    }
                                    ic.sendKeyEvent(ke)
                                }
                                i += 2
                            }
                        }
                    }
                }

                IME_MESSAGE_B64 -> {
                    val data = intent.getStringExtra("msg")
                    data?.let {
                        try {
                            val b64 = Base64.decode(it, Base64.DEFAULT)
                            val msg = String(b64, Charsets.UTF_8)
                            val ic = currentInputConnection
                            ic?.commitText(msg, 1)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error decoding base64 message", e)
                        }
                    }
                }

                IME_CHARS -> {
                    val chars = intent.getIntArrayExtra("chars")
                    chars?.let {
                        val msg = String(it.map { code -> code.toChar() }.toCharArray())
                        val ic = currentInputConnection
                        ic?.commitText(msg, 1)
                    }
                }

                IME_KEYCODE -> {
                    val code = intent.getIntExtra("code", -1)
                    if (code != -1) {
                        val ic = currentInputConnection
                        ic?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
                        ic?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
                    }
                }

                IME_EDITORCODE -> {
                    val code = intent.getIntExtra("code", -1)
                    if (code != -1) {
                        val ic = currentInputConnection
                        ic?.performEditorAction(code)
                    }
                }

                IME_CLEAR_TEXT -> {
                    val ic = currentInputConnection
                    ic?.let {
                        val extractedText = it.getExtractedText(ExtractedTextRequest(), 0)
                        if (extractedText != null) {
                            val curPos = extractedText.text
                            val beforePos = it.getTextBeforeCursor(curPos.length, 0)
                            val afterPos = it.getTextAfterCursor(curPos.length, 0)
                            it.deleteSurroundingText(beforePos?.length ?: 0, afterPos?.length ?: 0)
                        }
                    }
                }
            }
        }
    }
} 