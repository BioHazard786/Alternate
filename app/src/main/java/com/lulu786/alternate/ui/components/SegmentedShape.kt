package com.lulu786.alternate.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val outer = 24.dp
private val inner = 4.dp

private val single = RoundedCornerShape(outer)
private val first = RoundedCornerShape(outer, outer, inner, inner)
private val middle = RoundedCornerShape(inner)
private val last = RoundedCornerShape(inner, inner, outer, outer)

/** Google-Contacts style grouped list: 24dp outer corners, 4dp between rows. */
fun segmentedShape(index: Int, count: Int): RoundedCornerShape = when {
    count <= 1 -> single
    index == 0 -> first
    index == count - 1 -> last
    else -> middle
}
