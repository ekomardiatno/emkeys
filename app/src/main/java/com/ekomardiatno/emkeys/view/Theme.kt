package com.ekomardiatno.emkeys.view

import android.content.Context
import androidx.core.content.ContextCompat
import com.ekomardiatno.emkeys.R

/**
 * pingwin's dark tokens, resolved once (RESEARCH.md §7.1/§7.2).
 *
 * Dark only in chunk 1 — pingwin ships no light variant, so light mode is new design work
 * and belongs in a later chunk. Values live in `res/values/colors.xml` so there is one
 * source of truth shared with the setup screen.
 */
class Theme(private val context: Context) {
    private fun color(id: Int) = ContextCompat.getColor(context, id)

    /** Deck / inter-key gap — stands in for the ThinkPad's `#1E1D1B` deck. */
    val deck = color(R.color.ink)

    /** Keycap face: the "raised" token, literally. */
    val cap = color(R.color.ink_raise)
    val capPressed = color(R.color.ink_press)

    /** Modifier and navigation caps: the two-tone ThinkPad cap, in pingwin tokens. */
    val capModifier = color(R.color.ink)

    val border = color(R.color.line)
    val borderStrong = color(R.color.line_strong)

    val legend = color(R.color.text)
    val legendMuted = color(R.color.muted)

    /** Amber indicates a physically held modifier or another active toggle. */
    val active = color(R.color.amber)
    val locked = color(R.color.amber_press)
    val onAmber = color(R.color.amber_ink)
}
