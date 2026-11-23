package com.majidali.appcloner.ui.common

import android.content.Context
import android.widget.Toast
import androidx.appcompat.app.AlertDialog

/**
 * Small UI helpers shared by every screen.
 *
 * The rule behind them: never crash, never fail silently. When
 * something goes wrong, the user gets one short sentence in plain
 * words and, when there is one, one thing to try next.
 */
object UiHelpers {

    /** Short message at the bottom of the screen. */
    fun toast(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_LONG).show()
    }

    /**
     * Error dialog with a title, a plain-words message, and one
     * optional action button (for example "Copy command").
     */
    fun showError(
        context: Context,
        title: String,
        message: String,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null
    ) {
        val builder = AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
        if (actionLabel != null && onAction != null) {
            builder.setNeutralButton(actionLabel) { _, _ -> onAction() }
        }
        builder.show()
    }

    /** Yes / No question. [onYes] runs only when the user taps Yes. */
    fun askYesNo(
        context: Context,
        title: String,
        message: String,
        onYes: () -> Unit
    ) {
        AlertDialog.Builder(context)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("Yes") { _, _ -> onYes() }
            .setNegativeButton("No", null)
            .show()
    }
}
