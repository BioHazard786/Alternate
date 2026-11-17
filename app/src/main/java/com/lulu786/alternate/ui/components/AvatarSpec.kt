package com.lulu786.alternate.ui.components

import android.content.Context
import com.google.android.material.color.MaterialColors

/**
 * Everything about a contact's avatar look comes from one stored number, `contact.avatarSeed`.
 *
 * IMPORTANT: the shape list, [hues] and pattern list are append-only. Reordering or inserting changes
 * how every existing contact looks. A unit test pins the current order.
 */
object AvatarSpec {
    const val SHAPES = 12
    const val COLORS = 10
    const val PATTERNS = 4

    fun shape(seed: Int) = Math.floorMod(seed, SHAPES)
    fun color(seed: Int) = Math.floorMod(seed / SHAPES, COLORS)
    fun pattern(seed: Int) = Math.floorMod(seed / (SHAPES * COLORS), PATTERNS)

    /** Radix step-9 key hues: blue, cyan, teal, green, lime, amber, orange, red, pink, purple. */
    val hues: IntArray = intArrayOf(
        0xFF0090FF.toInt(), 0xFF00A2C7.toInt(), 0xFF12A594.toInt(), 0xFF30A46C.toInt(), 0xFFBDEE63.toInt(),
        0xFFFFC53D.toInt(), 0xFFF76B15.toInt(), 0xFFE5484D.toInt(), 0xFFD6409F.toInt(), 0xFF8E4EC6.toInt(),
    )

    /** Container/on-container colours for palette [index], nudged toward the user's wallpaper colour. */
    fun roles(context: Context, index: Int, light: Boolean): Pair<Int, Int> {
        val harmonized = MaterialColors.harmonizeWithPrimary(context, hues[index])
        val r = MaterialColors.getColorRoles(harmonized, light)
        return r.accentContainer to r.onAccentContainer
    }
}
