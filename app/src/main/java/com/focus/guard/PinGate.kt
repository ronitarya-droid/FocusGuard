package com.focus.guard

import android.app.Activity
import android.content.Context
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.focus.guard.Ui.Radius
import com.focus.guard.Ui.Space
import com.focus.guard.Ui.dp
import com.focus.guard.Ui.dpf
import com.focus.guard.Ui.gradient
import com.focus.guard.Ui.iconChip
import com.focus.guard.Ui.lp
import com.focus.guard.Ui.pressable
import com.focus.guard.Ui.rounded
import com.focus.guard.Ui.text
import com.focus.guard.Ui.tintWash

/**
 * Shared PIN gate (PIN 4377). Returns a polished, centered lock screen view that
 * calls [onUnlock] when the correct PIN is entered. Used by Notes + Block
 * sites/words so both share one secure, on-brand entry surface.
 */
object PinGate {
    const val PIN = "4377"

    fun lockView(ctx: Context, title: String, onUnlock: () -> Unit): View {
        val root = FrameLayout(ctx).apply {
            setBackgroundColor(Ui.BG)
            layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        }

        val col = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(ctx.dp(Space.xxl), ctx.dp(Space.xxl), ctx.dp(Space.xxl), ctx.dp(Space.xxl))
            layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT, Gravity.CENTER)
        }

        col.addView(ctx.iconChip(R.drawable.ic_lock, Ui.ACCENT, tintWash(Ui.ACCENT), 64, Radius.lg))
        col.addView(ctx.text(title, 24f, Ui.TEXT, bold = true).apply {
            gravity = Gravity.CENTER; setPadding(0, ctx.dp(Space.lg), 0, 0)
        })
        col.addView(ctx.text("Enter your PIN to unlock", 14f, Ui.TEXT_DIM).apply {
            gravity = Gravity.CENTER; setPadding(0, ctx.dp(Space.sm), 0, ctx.dp(Space.xxl))
        })

        val pin = EditText(ctx).apply {
            hint = "••••"; gravity = Gravity.CENTER; textSize = 28f
            setTextColor(Ui.TEXT); setHintTextColor(Ui.TEXT_MUTE)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            letterSpacing = 0.5f
            background = rounded(Ui.SURFACE, ctx.dpf(Radius.md), Ui.STROKE, ctx.dp(1))
            setPadding(ctx.dp(16), ctx.dp(18), ctx.dp(16), ctx.dp(18))
        }
        col.addView(pin, lp(width = ctx.dp(220)))

        val unlock = ctx.text("Unlock", 16f, Ui.TEXT, bold = true).apply {
            gravity = Gravity.CENTER
            background = pressable(gradient(Ui.ACCENT, Ui.ACCENT_2, ctx.dpf(Radius.md)))
            setPadding(ctx.dp(44), ctx.dp(15), ctx.dp(44), ctx.dp(15))
            isClickable = true
            setOnClickListener {
                if (pin.text.toString() == PIN) onUnlock()
                else {
                    pin.error = "Wrong PIN"; pin.text.clear()
                    pin.startAnimation(shake(ctx))
                }
            }
        }
        col.addView(unlock, lp(width = WRAP_CONTENT, topMargin = ctx.dp(Space.xl)))

        root.addView(col)
        // Keep the centered content clear of system bars.
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }
        return root
    }

    private fun shake(ctx: Context) = android.view.animation.TranslateAnimation(
        -ctx.dpf(8f), ctx.dpf(8f), 0f, 0f
    ).apply {
        duration = 70; repeatCount = 3
        repeatMode = android.view.animation.Animation.REVERSE
    }
}
