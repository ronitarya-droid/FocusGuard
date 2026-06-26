package com.focus.guard

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.animation.AnimationUtils
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * FocusGuard's design system — a small, dependency-free toolkit for building the
 * entire UI in code (the project intentionally ships almost no XML layouts).
 *
 * The look is "premium dark productivity": a near-black canvas, layered rounded
 * surfaces with hairline strokes and soft elevation, a warm orange→pink accent,
 * crisp Lucide-style line icons (never emoji), a strict 4dp spacing rhythm and a
 * consistent type scale. Centralising it here means every screen — dashboard,
 * blockers, study, alarms, onboarding — shares one cohesive, modern surface.
 *
 * Design language: Material 3 Expressive tone (large rounded shapes, confident
 * type hierarchy, tonal surfaces, springy motion) adapted to an always-dark app.
 *
 * Backwards compatibility: all symbols used by existing screens (BG, SURFACE,
 * ACCENT, dp, lp, rounded, gradient, title, text, card, pill, primaryButton,
 * tile, dangerButton) are preserved with compatible signatures.
 */
object Ui {

    // ───────────────────────── palette ─────────────────────────
    // Slightly deeper, cooler base than before for a more premium, OLED-friendly
    // canvas; surfaces step up in luminance so cards read as elevated planes.
    const val BG = 0xFF0A0A0F.toInt()           // app canvas (near-black)
    const val SURFACE = 0xFF15151D.toInt()      // primary card surface
    const val SURFACE_HI = 0xFF1C1C26.toInt()   // elevated / inset surface
    const val SURFACE_LO = 0xFF101017.toInt()   // recessed wells (charts, inputs)
    const val STROKE = 0xFF2A2A36.toInt()        // hairline border
    const val STROKE_SOFT = 0x14FFFFFF           // subtle inner separators

    const val ACCENT = 0xFFFF7A18.toInt()        // primary orange
    const val ACCENT_2 = 0xFFFF3D81.toInt()      // gradient end (pink)
    const val ACCENT_SOFT = 0x1FFF7A18           // accent wash (12%)

    const val GREEN = 0xFF34D399.toInt()
    const val GREEN_SOFT = 0x2334D399
    const val RED = 0xFFF87171.toInt()
    const val RED_SOFT = 0x22F87171
    const val AMBER = 0xFFFBBF24.toInt()
    const val AMBER_SOFT = 0x22FBBF24
    const val BLUE = 0xFF60A5FA.toInt()

    const val TEXT = 0xFFF5F5F7.toInt()
    const val TEXT_DIM = 0xFF9AA0A6.toInt()
    const val TEXT_MUTE = 0xFF6B7280.toInt()

    /** Optional Android-12+ dynamic accent. Resolved lazily per Activity; falls
     *  back to [ACCENT] when wallpaper-based colors aren't available. Keeps the
     *  brand identity while letting the UI feel native to the user's device. */
    fun accentFor(ctx: Context): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return ACCENT
        return try {
            val tv = TypedValue()
            val ok = ctx.theme.resolveAttribute(
                android.R.attr.colorAccent, tv, true
            )
            if (!ok) return ACCENT
            val c = if (tv.resourceId != 0) ctx.getColor(tv.resourceId) else tv.data
            // Only adopt it if it's vivid enough to read as an accent on dark.
            if (isVividOnDark(c)) c else ACCENT
        } catch (_: Throwable) { ACCENT }
    }

    private fun isVividOnDark(c: Int): Boolean {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(c, hsl)
        return hsl[2] in 0.45f..0.85f && hsl[1] >= 0.35f
    }

    // ───────────────────────── spacing / shape scale ─────────────────────────
    // 4dp rhythm. Use these names instead of magic numbers for new code.
    object Space {
        const val xs = 4; const val sm = 8; const val md = 12
        const val lg = 16; const val xl = 20; const val xxl = 28; const val xxxl = 36
    }
    object Radius {
        const val sm = 12f; const val md = 16f; const val lg = 20f
        const val xl = 24f; const val pill = 999f
    }

    fun Context.dp(v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
    ).toInt()

    fun Context.dpf(v: Float): Float = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics
    )

    // ───────────────────────── layout params ─────────────────────────
    fun lp(width: Int = MATCH_PARENT, height: Int = WRAP_CONTENT, topMargin: Int = 0) =
        LinearLayout.LayoutParams(width, height).apply { this.topMargin = topMargin }

    fun row(width: Int = 0, weight: Float = 1f, gravity: Int = Gravity.NO_GRAVITY) =
        LinearLayout.LayoutParams(width, WRAP_CONTENT, weight).apply { this.gravity = gravity }

    // ───────────────────────── drawables ─────────────────────────
    fun rounded(color: Int, radius: Float, strokeColor: Int? = null, strokeW: Int = 0) =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius
            if (strokeColor != null) setStroke(strokeW, strokeColor)
        }

    fun gradient(start: Int, end: Int, radius: Float,
                 orientation: GradientDrawable.Orientation = GradientDrawable.Orientation.LEFT_RIGHT) =
        GradientDrawable(orientation, intArrayOf(start, end)).apply { cornerRadius = radius }

    /** Three-stop diagonal gradient for hero surfaces. */
    fun heroGradient(c1: Int, c2: Int, c3: Int, radius: Float) =
        GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(c1, c2, c3)).apply {
            cornerRadius = radius
        }

    private fun ripple(content: Drawable, rippleColor: Int = 0x33FFFFFF): RippleDrawable =
        RippleDrawable(ColorStateList.valueOf(rippleColor), content, null)

    /** Public ripple helper for screens that build their own pressable surfaces. */
    fun pressable(content: Drawable, rippleColor: Int = 0x26FFFFFF): Drawable =
        ripple(content, rippleColor)

    // ───────────────────────── icons ─────────────────────────
    /** Tinted line icon. [res] is a vector drawable id (R.drawable.ic_*). */
    fun Context.icon(res: Int, tint: Int = TEXT, sizeDp: Int = 22): ImageView =
        ImageView(this).apply {
            setImageResource(res)
            imageTintList = ColorStateList.valueOf(tint)
            layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
            scaleType = ImageView.ScaleType.FIT_CENTER
        }

    /** A rounded, tonal "icon chip" — colored square holding a line icon. The
     *  signature visual element of the redesign (replaces emoji badges). */
    fun Context.iconChip(res: Int, tint: Int = ACCENT, bg: Int = tintWash(tint),
                         sizeDp: Int = 40, radius: Float = Radius.md): FrameLayout {
        val box = FrameLayout(this).apply {
            background = rounded(bg, dpf(radius))
            layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
        }
        val iv = ImageView(this).apply {
            setImageResource(res)
            imageTintList = ColorStateList.valueOf(tint)
            val pad = dp((sizeDp * 0.28f).toInt())
            setPadding(pad, pad, pad, pad)
            layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        box.addView(iv)
        return box
    }

    /** ~13% alpha wash of a color — for tonal icon-chip backgrounds. */
    fun tintWash(color: Int): Int = ColorUtils.setAlphaComponent(color, 0x22)

    // ───────────────────────── typography ─────────────────────────
    private val MEDIUM: Typeface get() = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    private val BOLD: Typeface get() = Typeface.create("sans-serif", Typeface.BOLD)

    /** Section eyebrow label (small, uppercase-feel, tracked, dim). */
    fun Context.title(t: String) = TextView(this).apply {
        text = t; textSize = 12f
        letterSpacing = 0.12f
        typeface = MEDIUM
        setTextColor(TEXT_DIM)
    }

    /** General text. Defaults preserved for compatibility (14sp, dim). */
    fun Context.text(t: String, size: Float = 14f, color: Int = TEXT_DIM,
                     bold: Boolean = false): TextView = TextView(this).apply {
        text = t; setTextColor(color); textSize = size
        setLineSpacing(dpf(2f), 1f)
        if (bold) typeface = MEDIUM
    }

    /** Large screen / hero heading. */
    fun Context.heading(t: String, size: Float = 26f, color: Int = TEXT): TextView =
        TextView(this).apply {
            text = t; setTextColor(color); textSize = size
            typeface = BOLD
            letterSpacing = -0.01f
        }

    /** Big numeric stat (streak, credit). Tabular feel via bold sans. */
    fun Context.stat(t: String, size: Float = 52f, color: Int = TEXT): TextView =
        TextView(this).apply {
            text = t; setTextColor(color); textSize = size
            typeface = BOLD
            letterSpacing = -0.02f
            includeFontPadding = false
        }

    // ───────────────────────── containers ─────────────────────────
    /** Standard elevated card. Soft layered shadow + hairline stroke. */
    fun Context.card(padding: Int = 20, radius: Float = Radius.lg,
                     fill: Int = SURFACE): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = elevatedSurface(fill, dpf(radius))
        setPadding(dp(padding), dp(padding), dp(padding), dp(padding))
        elevation = dpf(6f)
        clipToOutline = false
    }

    /** A surface drawable with a faint top highlight for a sense of light. */
    private fun Context.elevatedSurface(fill: Int, radius: Float): Drawable {
        val base = rounded(fill, radius, STROKE, dp(1))
        val sheen = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(0x0FFFFFFF, 0x00FFFFFF)
        ).apply { cornerRadius = radius }
        return LayerDrawable(arrayOf(base, sheen))
    }

    /** Hairline divider for inside cards. */
    fun Context.divider(topMargin: Int = Space.lg, bottomMargin: Int = Space.md): View =
        View(this).apply {
            setBackgroundColor(STROKE_SOFT)
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, dp(1)).apply {
                this.topMargin = dp(topMargin); this.bottomMargin = dp(bottomMargin)
            }
        }

    // ───────────────────────── status pill ─────────────────────────
    /** A protection-status row: tonal icon dot + label + ON/OFF state chip.
     *  Backwards-compatible 2-arg form; optional icon/subtitle for richer rows. */
    fun Context.pill(label: String, on: Boolean, iconRes: Int? = null,
                     subtitle: String? = null): LinearLayout {
        val stateColor = if (on) GREEN else TEXT_MUTE
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(SURFACE_HI, dpf(Radius.sm))
            setPadding(dp(14), dp(13), dp(14), dp(13))
        }
        if (iconRes != null) {
            row.addView(iconChip(iconRes, stateColor, tintWash(stateColor), 30, Radius.sm))
        } else {
            row.addView(View(this).apply {
                background = rounded(stateColor, dpf(5f))
                layoutParams = LinearLayout.LayoutParams(dp(9), dp(9))
            })
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = row(0, 1f)
            setPadding(dp(12), 0, dp(8), 0)
        }
        col.addView(text(label, 14.5f, TEXT, bold = true))
        if (subtitle != null) col.addView(text(subtitle, 11.5f, TEXT_MUTE).apply {
            setPadding(0, dp(1), 0, 0); maxLines = 1
        })
        row.addView(col)
        row.addView(statePill(on))
        return row
    }

    private fun Context.statePill(on: Boolean): TextView =
        text(if (on) "ON" else "OFF", 11.5f, if (on) GREEN else TEXT_MUTE, bold = true).apply {
            background = rounded(if (on) GREEN_SOFT else 0x18FFFFFF, dpf(Radius.pill))
            setPadding(dp(11), dp(5), dp(11), dp(5))
            letterSpacing = 0.08f
        }

    // ───────────────────────── buttons ─────────────────────────
    /** Primary gradient CTA. Optional leading icon. */
    fun Context.primaryButton(label: String, iconRes: Int? = null,
                              onClick: () -> Unit): View {
        if (iconRes == null) {
            return buttonBase(label, TEXT, gradient(ACCENT, ACCENT_2, dpf(Radius.md))).apply {
                setOnClickListener { debounced(onClick) }
            }
        }
        val btn = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = pressable(gradient(ACCENT, ACCENT_2, dpf(Radius.md)))
            setPadding(dp(18), dp(15), dp(18), dp(15))
            isClickable = true
            elevation = dpf(2f)
            setOnClickListener { debounced(onClick) }
        }
        btn.addView(icon(iconRes, TEXT, 19))
        btn.addView(text(label, 15.5f, TEXT, bold = true).apply { setPadding(dp(10), 0, 0, 0) })
        return btn
    }

    /** Secondary / tonal button (quiet surface, no gradient). */
    fun Context.secondaryButton(label: String, iconRes: Int? = null,
                                onClick: () -> Unit): View {
        val btn = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = pressable(rounded(SURFACE_HI, dpf(Radius.md), STROKE, dp(1)))
            setPadding(dp(18), dp(14), dp(18), dp(14))
            isClickable = true
            setOnClickListener { debounced(onClick) }
        }
        if (iconRes != null) btn.addView(icon(iconRes, TEXT, 18).apply {
            (layoutParams as LinearLayout.LayoutParams).rightMargin = dp(8)
        })
        btn.addView(text(label, 15f, TEXT, bold = true))
        return btn
    }

    /** Navigation/list tile: tonal icon chip + title (+subtitle) + chevron.
     *  Backwards-compatible: old call sites pass an emoji glyph string, which we
     *  now route to [iconForGlyph] so the line-icon set is used automatically. */
    fun Context.tile(glyph: String, label: String, onClick: () -> Unit): LinearLayout =
        tile(iconForGlyph(glyph), label, null, accentForGlyph(glyph), onClick)

    fun Context.tile(iconRes: Int, label: String, subtitle: String? = null,
                     tint: Int = ACCENT, onClick: () -> Unit): LinearLayout {
        val rowV = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = pressable(rounded(SURFACE, dpf(Radius.md), STROKE, dp(1)))
            setPadding(dp(14), dp(14), dp(16), dp(14))
            isClickable = true
            setOnClickListener { debounced(onClick) }
        }
        rowV.addView(iconChip(iconRes, tint, tintWash(tint), 42, Radius.md))
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = row(0, 1f)
            setPadding(dp(14), 0, dp(10), 0)
        }
        col.addView(text(label, 15.5f, TEXT, bold = true))
        if (subtitle != null) col.addView(text(subtitle, 12.5f, TEXT_MUTE).apply {
            setPadding(0, dp(2), 0, 0)
        })
        rowV.addView(col)
        rowV.addView(icon(R.drawable.ic_chevron_right, TEXT_MUTE, 20))
        return rowV
    }

    /** Danger button for destructive / lockdown actions. */
    fun Context.dangerButton(label: String, iconRes: Int? = null,
                             onClick: () -> Unit): View {
        if (iconRes == null) {
            return buttonBase(label, RED, rounded(RED_SOFT, dpf(Radius.md), 0x55F87171, dp(1))).apply {
                setOnClickListener { debounced(onClick) }
            }
        }
        val btn = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = pressable(rounded(RED_SOFT, dpf(Radius.md), 0x55F87171, dp(1)))
            setPadding(dp(18), dp(15), dp(18), dp(15))
            isClickable = true
            setOnClickListener { debounced(onClick) }
        }
        btn.addView(icon(iconRes, RED, 19).apply {
            (layoutParams as LinearLayout.LayoutParams).rightMargin = dp(9)
        })
        btn.addView(text(label, 15.5f, RED, bold = true))
        return btn
    }

    private fun Context.buttonBase(label: String, textColor: Int, bg: Drawable): TextView =
        TextView(this).apply {
            text = label; setTextColor(textColor); textSize = 15.5f
            typeface = MEDIUM
            gravity = Gravity.CENTER
            background = pressable(bg)
            setPadding(dp(18), dp(16), dp(18), dp(16))
            isClickable = true
            elevation = if (textColor == TEXT) dpf(2f) else 0f
        }

    /** Consistently themed toggle. Track/thumb tinted to the accent when on,
     *  muted when off; respects a disabled (locked) state. */
    fun Context.switch(checked: Boolean, enabled: Boolean = true,
                       onChange: ((Boolean) -> Unit)? = null): android.widget.Switch =
        android.widget.Switch(this).apply {
            isChecked = checked
            isEnabled = enabled
            val on = ACCENT; val off = TEXT_MUTE
            thumbTintList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(on, 0xFFCBD0D6.toInt())
            )
            trackTintList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(ColorUtils.setAlphaComponent(on, 0x99), 0x33FFFFFF)
            )
            if (onChange != null) setOnCheckedChangeListener { _, v -> onChange(v) }
        }

    /** Small chip (filter/quick action). */
    fun Context.chip(label: String, accent: Boolean = false, onClick: () -> Unit): TextView =
        text(label, 13.5f, if (accent) TEXT else TEXT_DIM, bold = true).apply {
            background = if (accent) pressable(gradient(ACCENT, ACCENT_2, dpf(Radius.pill)))
                         else pressable(rounded(SURFACE_HI, dpf(Radius.pill), STROKE, dp(1)))
            setPadding(dp(16), dp(9), dp(16), dp(9))
            isClickable = true
            setOnClickListener { debounced(onClick) }
        }

    // ───────────────────────── scaffold (edge-to-edge) ─────────────────────────
    /**
     * Builds a scrollable, edge-to-edge screen with a sticky-feeling top bar and
     * correct system-bar inset padding. Returns the root to pass to setContentView.
     *
     * @param title large screen title shown in the header
     * @param onBack if non-null, shows a back affordance wired to this
     * @param build  populate the vertical content column (already padded)
     */
    fun Activity.scaffold(
        title: String,
        subtitle: String? = null,
        onBack: (() -> Unit)? = null,
        headerIcon: Int? = null,
        build: (LinearLayout) -> Unit
    ): View {
        val rootBg = BG
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(rootBg)
            layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        }

        // Header
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(Space.xl), dp(Space.lg), dp(Space.xl), dp(Space.md))
        }
        if (onBack != null) {
            header.addView(iconButton(R.drawable.ic_arrow_left) { onBack() }.also {
                (it.layoutParams as? LinearLayout.LayoutParams)?.rightMargin = dp(Space.md)
            })
        } else if (headerIcon != null) {
            header.addView(iconChip(headerIcon, ACCENT, tintWash(ACCENT), 40, Radius.md).also {
                (it.layoutParams as LinearLayout.LayoutParams).rightMargin = dp(Space.md)
            })
        }
        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = row(0, 1f)
        }
        titleCol.addView(heading(title, 24f))
        if (subtitle != null) titleCol.addView(text(subtitle, 13f, TEXT_DIM).apply {
            setPadding(0, dp(2), 0, 0)
        })
        header.addView(titleCol)
        outer.addView(header)

        // Scroll body
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(Space.xl), dp(Space.sm), dp(Space.xl), dp(Space.xxxl))
        }
        build(content)

        val scroll = ScrollView(this).apply {
            setBackgroundColor(rootBg)
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
            addView(content)
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f)
        }
        outer.addView(scroll)

        applyInsets(outer, header, content)
        outer.animateInChildren(content)
        return outer
    }

    /**
     * Edge-to-edge frame for screens that supply their own header (e.g. the
     * dashboard's branded header). [header] is pinned, [content] scrolls, system
     * insets are applied, and content children rise in.
     */
    fun Activity.frame(header: View, content: LinearLayout): View {
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
            layoutParams = ViewGroup.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        }
        outer.addView(header)
        val scroll = ScrollView(this).apply {
            setBackgroundColor(BG)
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_NEVER
            isVerticalScrollBarEnabled = false
            addView(content)
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f)
        }
        outer.addView(scroll)
        applyInsets(outer, header, content)
        outer.animateInChildren(content)
        return outer
    }

    /** Apply system-bar insets: top to header, bottom to scroll content. */
    private fun applyInsets(root: View, header: View, content: View) {
        val hpl = header.paddingLeft; val hpt = header.paddingTop
        val hpr = header.paddingRight; val hpb = header.paddingBottom
        val cpl = content.paddingLeft; val cpr = content.paddingRight
        val cpt = content.paddingTop; val cpb = content.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            header.setPadding(hpl, hpt + bars.top, hpr, hpb)
            content.setPadding(cpl, cpt, cpr, cpb + bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(root)
    }

    /** A 40dp circular icon button (used for back / header actions). */
    fun Context.iconButton(iconRes: Int, tint: Int = TEXT, onClick: () -> Unit): View {
        val box = FrameLayout(this).apply {
            background = pressable(rounded(SURFACE, dpf(Radius.pill), STROKE, dp(1)))
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40))
            isClickable = true
            setOnClickListener { debounced(onClick) }
        }
        box.addView(ImageView(this).apply {
            setImageResource(iconRes)
            imageTintList = ColorStateList.valueOf(tint)
            val p = dp(9); setPadding(p, p, p, p)
            layoutParams = FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT)
        })
        return box
    }

    // ───────────────────────── motion ─────────────────────────
    /** Staggered rise-in for a vertical column's direct children. Cheap, runs
     *  once on first layout; gives screens a polished entrance. */
    fun View.animateInChildren(column: LinearLayout, stagger: Long = 38L, max: Int = 9) {
        column.post {
            val n = minOf(column.childCount, max)
            for (i in 0 until n) {
                val child = column.getChildAt(i)
                child.alpha = 0f
                child.translationY = column.context.dpf(14f)
                child.animate()
                    .alpha(1f).translationY(0f)
                    .setStartDelay(i * stagger)
                    .setDuration(360)
                    .setInterpolator(DecelerateInterpolator(1.4f))
                    .start()
            }
        }
    }

    /** A brief press-scale + tap feedback for a single view (e.g. CTA). */
    fun View.pressPop() {
        animate().scaleX(0.97f).scaleY(0.97f).setDuration(90).withEndAction {
            animate().scaleX(1f).scaleY(1f).setDuration(140)
                .setInterpolator(DecelerateInterpolator()).start()
        }.start()
    }

    // Debounce double-taps on actions that start activities / launch dialogs.
    @Volatile private var lastClickAt = 0L
    private fun debounced(action: () -> Unit) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastClickAt < 350L) return
        lastClickAt = now
        action()
    }

    // ───────────────────────── glyph → icon bridge ─────────────────────────
    // Maps the legacy emoji glyphs that existing screens still pass into the new
    // line-icon set, so the whole app upgrades even before each call site does.
    private fun iconForGlyph(glyph: String): Int = when {
        glyph.contains("📵") -> R.drawable.ic_grid
        glyph.contains("🌐") -> R.drawable.ic_globe
        glyph.contains("⛏") -> R.drawable.ic_book
        glyph.contains("📝") -> R.drawable.ic_lock
        glyph.contains("⏰") -> R.drawable.ic_alarm
        glyph.contains("♿") -> R.drawable.ic_accessibility
        glyph.contains("🪟") -> R.drawable.ic_layers
        glyph.contains("🔋") -> R.drawable.ic_battery
        glyph.contains("⬆") -> R.drawable.ic_download
        glyph.contains("🔓") -> R.drawable.ic_refresh
        glyph.contains("🩹") -> R.drawable.ic_heartcrack
        glyph.contains("🔒") -> R.drawable.ic_lock
        glyph.contains("🤖") -> R.drawable.ic_robot
        else -> R.drawable.ic_chevron_right
    }

    private fun accentForGlyph(glyph: String): Int = when {
        glyph.contains("📵") || glyph.contains("🌐") -> ACCENT
        glyph.contains("⛏") || glyph.contains("📝") -> GREEN
        glyph.contains("⏰") -> AMBER
        glyph.contains("🩹") -> RED
        glyph.contains("🔋") || glyph.contains("♿") || glyph.contains("🪟") -> BLUE
        else -> ACCENT
    }
}
