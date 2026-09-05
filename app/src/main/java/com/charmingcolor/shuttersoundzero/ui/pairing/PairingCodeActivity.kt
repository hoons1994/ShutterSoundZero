package com.charmingcolor.shuttersoundzero.ui.pairing

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.Toast
import com.charmingcolor.shuttersoundzero.service.PairingForegroundService

/**
 * 알림을 탭했을 때 삼성의 무선 디버깅 화면 위에 작은 코드 입력창만 표시한다.
 * 별도의 오버레이 권한을 사용하지 않고, 사용자의 알림 탭으로 열린 Activity 안에서
 * AlertDialog를 표시한다.
 */
class PairingCodeActivity : Activity() {
    private var dialog: AlertDialog? = null
    private var submitted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showPairingCodeDialog()
    }

    private fun showPairingCodeDialog() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            filters = arrayOf(InputFilter.LengthFilter(PAIRING_CODE_LENGTH))
            gravity = Gravity.CENTER
            textSize = 24f
            isSingleLine = true
            hint = "6자리 코드"
            importantForAutofill = IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        }

        val container = FrameLayout(this).apply {
            setPadding(dp(24), dp(8), dp(24), 0)
            addView(
                input,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }

        val pairingDialog = AlertDialog.Builder(this)
            .setTitle("무선 디버깅 페어링")
            .setMessage("화면에 표시된 6자리 코드를 입력하세요.\n6자리 입력이 끝나면 자동으로 연결합니다.")
            .setView(container)
            .setNegativeButton("취소") { _, _ -> finish() }
            .create()

        dialog = pairingDialog
        pairingDialog.setOnShowListener {
            input.requestFocus()
            pairingDialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)

            input.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

                override fun afterTextChanged(editable: Editable?) {
                    val code = editable?.toString().orEmpty()
                    if (code.length == PAIRING_CODE_LENGTH && code.all(Char::isDigit)) {
                        submitCode(code)
                    }
                }
            })
        }
        pairingDialog.setOnCancelListener { finish() }
        pairingDialog.setOnDismissListener {
            if (!submitted && !isFinishing) finish()
        }
        pairingDialog.show()
    }

    private fun submitCode(code: String) {
        if (submitted) return
        if (code.length != PAIRING_CODE_LENGTH || !code.all(Char::isDigit)) return

        submitted = true
        PairingForegroundService.submitCode(this, code)
        Toast.makeText(this, "페어링 코드를 전송했습니다.", Toast.LENGTH_SHORT).show()

        dialog?.setOnDismissListener(null)
        dialog?.dismiss()
        finish()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        dialog?.setOnDismissListener(null)
        dialog?.dismiss()
        dialog = null
        super.onDestroy()
    }

    companion object {
        private const val PAIRING_CODE_LENGTH = 6
    }
}
