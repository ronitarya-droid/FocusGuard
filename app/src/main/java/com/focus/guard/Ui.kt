package com.focus.guard

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

/**
 * A tiny, dependency-free design system for FocusGuard. All views are built in
 * code (the project intentionally avoids XML layouts), but centralised here so
 * both screens share one polished, consistent look: a dark "focus" theme with
 * rounded surfaces, status pills, gradient CTAs and proper spacing rhythm.
 */
object Ui {
    // --- palette ---
    const val BG = 0xFF0B0B0F.toInt()          // app background (near-black)
    const val SURFACE = 0xFF16161D.toInt()      // card surface
    const val SURFACE_HI = 0xFF1E1E27.toInt()   // elevated / inner surface
    const val STROKE = 0xFF262631.toInt()       // hairline border
    const val ACCENT = 0xFFFF7A18.toInt()       // primary orange
    const val ACCENT_2 = 0xFFFF3D81.toInt()     // gradient end (pink)
    const val GREEN = 0xFF34D399.toInt()
    const val RED = 0xFFF87171.toInt()
    const val AMBER = 0xFFFBBF24.toInt()
    const val TEXT = 0xFFF5F5F7.toInt()
    const val TEXT_DIM = 0xFF9AA0A6.toInt()
    const val TEXT_MUTE = 0xFF6B7280.toInt()

    fun Context.dp(v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
    ).toInt()

    fun lp(width: Int = ViewGroup.LayoutParams.MATCH_PARENT,
           height: Int = ViewGroup.LayoutParams.WRAP_CONTENT,
           topMargin: Int = 0) = LinearLayout.LayoutParams(width, height).apply {
        this.topMargin = topMargin
    }

    fun rounded(color: Int, radius: Float, strokeColor: Int? = null, strokeW: Int = 0) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius
            if (strokeColor != null) setStroke(strokeW, strokeColor)
        }

    fun gradient(start: Int, end: Int, radius: Float) =
        GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(start, end)).apply {
            cornerRadius = radius
        }

    // --- text ---
    fun Context.title(t: String) = TextView(this).apply {
        text = t; setTextColor(TEXT); textSize = 13f
        letterSpacing = 0.14f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(TEXT_DIM)
    }

    fun Context.text(t: String, size: Float = 14f, color: Int = TEXT_DIM,
                     bold: Boolean = false) = TextView(this).apply {
        text = t; setTextColor(color); textSize = size
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    // --- card container ---
    fun Context.card(padding: Int = 20): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(SURFACE, dp(20).toFloat(), STROKE, dp(1))
        setPadding(dp(padding), dp(padding), dp(padding), dp(padding))
    }

    // --- status pill: ● ON / ● OFF ---
    fun Context.pill(label: String, on: Boolean): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(SURFACE_HI, dp(14).toFloat())
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        val dot = View(this).apply {
            background = rounded(if (on) GREEN else RED, dp(5).toFloat())
            layoutParams = LinearLayout.LayoutParams(dp(10), dp(10))
        }
        val name = text(label, 14f, TEXT, bold = true).apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setPadding(dp(12), 0, 0, 0)
        }
        val state = text(if (on) "ON" else "OFF", 12.5f, if (on) GREEN else RED, bold = true)
        row.addView(dot); row.addView(name); row.addView(state)
        return row
    }

    // --- buttons ---
    /** Primary gradient CTA. */
    fun Context.primaryButton(label: String, onClick: () -> Unit): TextView =
        buttonBase(label, TEXT, gradient(ACCENT, ACCENT_2, dp(16).toFloat())).apply {
            setOnClickListener { onClick() }
        }

    /** Quiet surface button with an emoji glyph. */
    fun Context.tile(glyph: String, label: String, onClick: () -> Unit): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = ripple(rounded(SURFACE, dp(16).toFloat(), STROKE, dp(1)))
            setPadding(dp(16), dp(16), dp(16), dp(16))
            isClickable = true
            setOnClickListener { onClick() }
        }
        row.addView(TextView(this).apply { text = glyph; textSize = 18f })
        row.addView(text(label, 15f, TEXT, bold = true).apply {
            setPadding(dp(14), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        row.addView(text("›", 22f, TEXT_MUTE))
        return row
    }

    /** Danger button for destructive / lockdown actions. */
    fun Context.dangerButton(label: String, onClick: () -> Unit): TextView =
        buttonBase(label, RED, rounded(0x22F87171, dp(16).toFloat(), 0x55F87171, dp(1))).apply {
            setOnClickListener { onClick() }
        }

    private fun Context.buttonBase(label: String, textColor: Int, bg: android.graphics.drawable.Drawable) =
        TextView(this).apply {
            text = label; setTextColor(textColor); textSize = 15.5f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
            background = ripple(bg)
            setPadding(dp(18), dp(16), dp(18), dp(16))
            isClickable = true
        }

    private fun Context.ripple(content: android.graphics.drawable.Drawable) =
        RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), content, null)
}
