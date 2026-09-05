package com.charmingcolor.shuttersoundzero.ui.pairing

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 시스템 설정 Activity를 전환하지 않고 그 위에 잠깐 표시되는 6자리 페어링 코드 입력창.
 * TYPE_APPLICATION_OVERLAY를 사용하므로 SYSTEM_ALERT_WINDOW 특별 권한이 있을 때만 동작한다.
 */
class PairingCodeOverlay(private val context: Context) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var overlayView: View? = null

    fun show(onSubmit: (String) -> Unit) {
        if (!Settings.canDrawOverlays(context)) return
        mainHandler.post { showInternal(onSubmit) }
    }

    fun dismiss() {
        mainHandler.post { dismissInternal() }
    }

    private fun showInternal(onSubmit: (String) -> Unit) {
        if (!Settings.canDrawOverlays(context)) return
        dismissInternal()

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(16), dp(20), dp(14))
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                cornerRadius = dp(18).toFloat()
                setStroke(dp(1), 0x22000000)
            }
            elevation = dp(10).toFloat()
        }

        root.addView(TextView(context).apply {
            text = "무선 디버깅 페어링"
            textSize = 17f
            setTextColor(Color.BLACK)
            gravity = Gravity.CENTER
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        })

        root.addView(TextView(context).apply {
            text = "화면에 표시된 6자리 코드를 입력하세요"
            textSize = 13f
            setTextColor(0xFF555555.toInt())
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, dp(10))
        })

        val input = EditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            filters = arrayOf(InputFilter.LengthFilter(PAIRING_CODE_LENGTH))
            isSingleLine = true
            gravity = Gravity.CENTER
            textSize = 24f
            hint = "000000"
            setTextColor(Color.BLACK)
            setHintTextColor(0xFFAAAAAA.toInt())
            background = GradientDrawable().apply {
                setColor(0xFFF5F5F5.toInt())
                cornerRadius = dp(10).toFloat()
                setStroke(dp(1), 0x22000000)
            }
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        root.addView(
            input,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        )

        val cancel = TextView(context).apply {
            text = "취소"
            textSize = 14f
            setTextColor(0xFF3F51B5.toInt())
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(12), dp(12), dp(4))
            setOnClickListener { dismiss() }
        }
        root.addView(cancel)

        val params = WindowManager.LayoutParams(
            dp(330),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(72)
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
            title = "ShutterSoundZero pairing input"
        }

        var submitted = false
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit

            override fun afterTextChanged(editable: Editable?) {
                val code = editable?.toString().orEmpty()
                if (!submitted && code.length == PAIRING_CODE_LENGTH && code.all(Char::isDigit)) {
                    submitted = true
                    dismissInternal()
                    onSubmit(code)
                }
            }
        })

        try {
            windowManager.addView(root, params)
            overlayView = root
            input.postDelayed({
                input.requestFocus()
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.showSoftInput(input, InputMethodManager.SHOW_IMPLICIT)
            }, 120)
        } catch (_: Exception) {
            overlayView = null
        }
    }

    private fun dismissInternal() {
        val view = overlayView ?: return
        overlayView = null
        try {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(view.windowToken, 0)
        } catch (_: Exception) {
        }
        try {
            windowManager.removeViewImmediate(view)
        } catch (_: Exception) {
        }
    }

    private fun dp(value: Int): Int = (value * context.resources.displayMetrics.density).toInt()

    companion object {
        private const val PAIRING_CODE_LENGTH = 6
    }
}
