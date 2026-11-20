package com.lulu786.alternate.ui.components

import com.lulu786.alternate.ui.Sym
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.clearAndSetSemantics
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.lulu786.alternate.LocalContainer
import com.lulu786.alternate.ui.theme.AvatarLetterFont
import com.lulu786.alternate.ui.theme.LocalAvatarColors
import androidx.graphics.shapes.RoundedPolygon

/** Append-only: existing contacts index into this list by their stored seed. */
object AvatarShapes {
    val polygons: List<RoundedPolygon> = listOf(
        MaterialShapes.Cookie4Sided, MaterialShapes.Cookie6Sided, MaterialShapes.Cookie7Sided, MaterialShapes.Cookie9Sided,
        MaterialShapes.Cookie12Sided, MaterialShapes.Clover4Leaf, MaterialShapes.Clover8Leaf, MaterialShapes.Sunny,
        MaterialShapes.VerySunny, MaterialShapes.Flower, MaterialShapes.SoftBurst, MaterialShapes.Pentagon,
    )
    val names = listOf("Cookie4Sided", "Cookie6Sided", "Cookie7Sided", "Cookie9Sided", "Cookie12Sided", "Clover4Leaf",
        "Clover8Leaf", "Sunny", "VerySunny", "Flower", "SoftBurst", "Pentagon")
    @Composable fun shapeFor(seed: Int): Shape = polygons[AvatarSpec.shape(seed)].toShape()
}


/**
 * Contact avatar, styled like the M3 list sample: the contact's own shape (pass [expressive]) and a heavy, wide,
 * fully rounded Google Sans Flex initial. A photo replaces colour and pattern but keeps the shape.
 */
@Composable
fun ContactAvatar(
    seed: Int,
    name: String,
    photoPath: String,
    size: Dp,
    modifier: Modifier = Modifier,
    expressive: Boolean = false,
    selected: Boolean = false,
) {
    val shape: Shape = if (expressive) AvatarShapes.shapeFor(seed) else CircleShape
    val colors = LocalAvatarColors.current
    val colorIndex = AvatarSpec.color(seed)
    val container = colors.container[colorIndex]
    val onContainer = colors.onContainer[colorIndex]

    // Selection flips the avatar around its vertical axis, swapping to a check circle halfway.
    val flip = remember { Animatable(if (selected) 180f else 0f) }
    LaunchedEffect(selected) { flip.animateTo(if (selected) 180f else 0f, spring(dampingRatio = 0.6f, stiffness = 400f)) }
    val showCheck = flip.value > 90f

    Box(
        modifier
            .size(size)
            .graphicsLayer { rotationY = flip.value; cameraDistance = 12f * density }
            .clip(if (showCheck) CircleShape else shape)
            .then(if (showCheck) Modifier.background(MaterialTheme.colorScheme.primary) else Modifier.avatarBackground(container, onContainer)),
        contentAlignment = Alignment.Center,
    ) {
        if (showCheck) {
            Icon(
                Sym.Check, null, tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(size * 0.5f).graphicsLayer { rotationY = 180f },
            )
        } else if (photoPath.isNotEmpty()) {
            val file = LocalContainer.current.photos.file(photoPath)
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(file).crossfade(true).size(256).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size),
            )
        } else {
            Text(
                name.trim().let { if (it.isEmpty()) "#" else String(Character.toChars(it.codePointAt(0))).uppercase() },
                color = onContainer,
                fontFamily = AvatarLetterFont,
                fontSize = (size.value * 0.44f).sp, // the wide face fills more width than height
                modifier = Modifier.clearAndSetSemantics { },
            )
        }
    }
}

/** Soft diagonal gradient with a light glow (Samsung style). The stored pattern id is kept for compatibility but no longer drawn. */
private fun Modifier.avatarBackground(container: Color, onContainer: Color): Modifier =
    drawWithCache {
        val s = size.minDimension
        val gradient = Brush.linearGradient(listOf(lerp(container, Color.White, 0.22f), lerp(container, onContainer, 0.16f)), Offset.Zero, Offset(s, s))
        val glow = Brush.radialGradient(listOf(Color.White.copy(alpha = 0.28f), Color.Transparent), Offset(s * 0.3f, s * 0.25f), s * 0.75f)
        onDrawBehind { drawRect(gradient); drawRect(glow) }
    }
