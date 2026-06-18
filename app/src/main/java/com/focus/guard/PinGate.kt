package com.focus.guard

import android.content.Context
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import com.focus.guard.Ui.dp
import com.focus.guard.Ui.gradient
import com.focus.guard.Ui.lp
import com.focus.guard.Ui.rounded
import com.focus.guard.Ui.text

/** Shared 4-digit PIN gate (PIN 4377). Returns a lock screen view that calls
 *  [onUnlock] when the correct PIN is entered. Used by Notes + Block sites/words. */
object PinGate {
    const val PIN = "4377"

    fun lockView(ctx: Context, title: String, onUnlock: () -> Unit): View {
        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Ui.BG)
            setPadding(ctx.dp(28), ctx.dp(90), ctx.dp(28), ctx.dp(28))
        }
        col.addView(ctx.text("🔒", 48f, Ui.TEXT).apply { gravity = Gravity.CENTER })
        col.addView(ctx.text(title, 23f, Ui.TEXT, bold = true).apply {
            gravity = Gravity.CENTER; setPadding(0, ctx.dp(12), 0, 0)
        })
        col.addView(ctx.text("Enter PIN to unlock", 14f, Ui.TEXT_DIM).apply {
            gravity = Gravity.CENTER; setPadding(0, ctx.dp(6), 0, ctx.dp(26))
        })
        val pin = EditText(ctx).apply {
            hint = "••••"; gravity = Gravity.CENTER; textSize = 26f
            setTextColor(Ui.TEXT); setHintTextColor(Ui.TEXT_MUTE)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            background = rounded(Ui.SURFACE, ctx.dp(14).toFloat(), Ui.STROKE, ctx.dp(1))
            setPadding(ctx.dp(16), ctx.dp(16), ctx.dp(16), ctx.dp(16))
        }
        col.addView(pin, lp(width = ctx.dp(220)))
        col.addView(ctx.text("Unlock", 16f, Ui.TEXT, bold = true).apply {
            gravity = Gravity.CENTER
            background = gradient(Ui.ACCENT, Ui.ACCENT_2, ctx.dp(16).toFloat())
            setPadding(ctx.dp(40), ctx.dp(15), ctx.dp(40), ctx.dp(15))
            isClickable = true
            setOnClickListener {
                if (pin.text.toString() == PIN) onUnlock()
                else { pin.error = "Wrong PIN"; pin.text.clear() }
            }
        }, lp(width = ViewGroup.LayoutParams.WRAP_CONTENT, topMargin = ctx.dp(20)))

        return ScrollView(ctx).apply { setBackgroundColor(Ui.BG); isFillViewport = true; addView(col) }
    }
}
