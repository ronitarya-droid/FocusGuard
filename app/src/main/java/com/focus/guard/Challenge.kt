package com.focus.guard

import android.app.AlertDialog
import android.content.Context
import android.text.InputType
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.focus.guard.Ui.dp
import kotlin.random.Random

/**
 * Friction gate for UN-blocking. Blocking is instant; removing something from a
 * blocklist requires the user to type a random code shown on screen. This kills
 * the impulse "flip it off and use it for a second" reflex — you can still undo
 * deliberately, but not reflexively. (Ulysses-pact pattern.)
 */
object Challenge {

    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // no easily-confused chars

    fun toUnblock(context: Context, what: String, onConfirmed: () -> Unit) {
        val code = (1..8).map { ALPHABET[Random.nextInt(ALPHABET.length)] }.joinToString("")

        val pad = context.dp(20)
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, context.dp(8), pad, 0)
        }
        box.addView(TextView(context).apply {
            text = "To unblock $what, type this code:"
            setTextColor(Ui.TEXT_DIM); textSize = 14f
        })
        box.addView(TextView(context).apply {
            text = code
            setTextColor(Ui.ACCENT); textSize = 30f
            gravity = Gravity.CENTER
            letterSpacing = 0.25f
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(0, context.dp(14), 0, context.dp(14))
        })
        val input = EditText(context).apply {
            hint = "Enter code"
            inputType = InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            textSize = 18f
            gravity = Gravity.CENTER
        }
        box.addView(input)

        val dialog = AlertDialog.Builder(context)
            .setTitle("Confirm unblock")
            .setView(box)
            .setPositiveButton("Unblock", null)   // overridden below to block auto-dismiss
            .setNegativeButton("Keep blocked", null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (input.text.toString().trim().equals(code, ignoreCase = true)) {
                    dialog.dismiss()
                    onConfirmed()
                } else {
                    input.error = "Code doesn't match"
                }
            }
        }
        dialog.show()
    }
}
