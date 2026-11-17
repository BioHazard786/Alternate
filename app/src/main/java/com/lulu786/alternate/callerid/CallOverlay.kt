package com.lulu786.alternate.callerid

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.os.Build
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import com.lulu786.alternate.R
import com.lulu786.alternate.data.CallerHit
import com.lulu786.alternate.data.PhotoStore
import com.lulu786.alternate.ui.components.AvatarShapes
import com.lulu786.alternate.ui.components.AvatarSpec
import com.lulu786.alternate.util.formatPhone
import com.lulu786.alternate.util.displayName

/** The popup shown over calls. Plain Views on purpose: no Compose startup cost inside a broadcast. */
object CallOverlay {
    private var view: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var collapsed = false
    private var animate = true
    private var busy = false          // a collapse/expand spring is running
    private var pressAnim: SpringAnimation? = null
    private var expandedH = 0

    val isShowing get() = view != null

    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    fun show(context: Context, hit: CallerHit) {
        if (view != null) return
        val themed = DynamicColors.wrapContextIfAvailable(ContextThemeWrapper(context, R.style.Theme_Alternate_Overlay))
        val root = LayoutInflater.from(themed).inflate(R.layout.caller_overlay, null)
        bind(themed, root, hit)
        animate = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) != 0f
        prepareEnter(root)

        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val keyguard = context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        @Suppress("DEPRECATION")
        val type = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            keyguard.isKeyguardLocked -> WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
            else -> WindowManager.LayoutParams.TYPE_PHONE
        }
        @Suppress("DEPRECATION")
        val flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
            WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
            WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT, type, flags, PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.CENTER }

        root.findViewById<View>(R.id.close_btn).setOnClickListener { dismiss(context) }
        root.findViewById<View>(R.id.collapse_btn).setOnClickListener { if (!busy) collapse(root, wm) }
        enableDrag(root, wm, lp)

        runCatching { wm.addView(root, lp) }.onSuccess { view = root; params = lp; root.post { enter(root) } }
    }

    fun dismiss(context: Context) {
        val v = view ?: return
        view = null; params = null; collapsed = false; busy = false
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val remove = { runCatching { wm.removeView(v) } }
        if (!animate) { remove(); return }
        val card = v.findViewById<View>(R.id.main_card)
        val dy = dp(v, 24f)
        spring(0f, 1f, EFFECT_DAMPING, EFFECT_STIFFNESS, onEnd = { remove() }) { p ->
            card.alpha = 1f - p; card.scaleX = 1f - 0.08f * p; card.scaleY = card.scaleX; card.translationY = -dy * p
        }
    }

    private fun bind(context: Context, root: View, hit: CallerHit) {
        root.findViewById<TextView>(R.id.callerName).text = displayName(hit.prefix, hit.name, hit.suffix)
        root.findViewById<TextView>(R.id.callerSubtitle).apply {
            // One per line: a wrapped " · " left a dangling dot at the end of the first line.
            val s = listOf(hit.appointment, hit.location).filter { it.isNotBlank() }.joinToString("\n")
            text = s; visibility = if (s.isEmpty()) View.GONE else View.VISIBLE
        }
        root.findViewById<TextView>(R.id.callerNumber).text = "${hit.label} · ${formatPhone(hit.fullPhoneNumber, hit.countryCode)}"

        val letter = root.findViewById<TextView>(R.id.avatarLetter)
        val photo = root.findViewById<ImageView>(R.id.callerPhoto)
        val light = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES
        val (bg, fg) = AvatarSpec.roles(AvatarSpec.color(hit.avatarSeed), MaterialColors.getColor(context, androidx.appcompat.R.attr.colorPrimary, 0), light)
        letter.text = hit.name.trim().firstOrNull()?.uppercase() ?: "#"
        letter.setTextColor(fg)
        root.findViewById<ShapedAvatarView>(R.id.avatar).setShape(AvatarShapes.polygons[AvatarSpec.shape(hit.avatarSeed)], bg, fg)

        val bmp = hit.photoPath.takeIf { it.isNotEmpty() }
            ?.let { BitmapFactory.decodeFile(PhotoStore(context).file(it).path) }
        if (bmp != null) { photo.setImageBitmap(bmp); photo.visibility = View.VISIBLE }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun enableDrag(root: View, wm: WindowManager, lp: WindowManager.LayoutParams) {
        val card = root.findViewById<View>(R.id.main_card)
        var startX = 0; var startY = 0; var touchX = 0f; var touchY = 0f
        fun press(to: Float) {
            pressAnim?.cancel()
            pressAnim = spring(card.scaleX, to, FAST_DAMPING, FAST_STIFFNESS) { card.scaleX = it; card.scaleY = it }
        }
        root.setOnTouchListener { _, e ->
            if (collapsed || busy) return@setOnTouchListener false
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { startX = lp.x; startY = lp.y; touchX = e.rawX; touchY = e.rawY; press(0.97f); true }
                MotionEvent.ACTION_MOVE -> {
                    lp.x = startX + (e.rawX - touchX).toInt(); lp.y = startY + (e.rawY - touchY).toInt()
                    runCatching { wm.updateViewLayout(root, lp) }; true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> { press(1f); true }
                else -> false
            }
        }
    }

    // --- Material 3 Expressive motion: springs, never fixed-duration tweens ---------------------------------------

    /** Default spatial spring (movement, size). */
    private const val SPATIAL_DAMPING = 0.8f
    private const val SPATIAL_STIFF = 380f
    /** Fast spatial spring: a visible bounce, for small things like the avatar and touch feedback. */
    private const val FAST_DAMPING = 0.6f
    private const val FAST_STIFF = 800f
    /** Effects spring (alpha, colour): no overshoot. */
    private const val EFFECT_DAMPING = 1f
    private const val EFFECT_STIFFNESS = 1600f
    private const val FAST_STIFFNESS = FAST_STIFF

    private fun dp(v: View, n: Float) = n * v.resources.displayMetrics.density

    /** Runs a spring from [from] to [to], reporting each value; snaps straight to the end when animations are off. */
    private fun spring(from: Float, to: Float, damping: Float, stiffness: Float, onEnd: (() -> Unit)? = null, onUpdate: (Float) -> Unit): SpringAnimation {
        val a = SpringAnimation(FloatValueHolder(from)).setSpring(SpringForce(to).setDampingRatio(damping).setStiffness(stiffness))
        a.setMinimumVisibleChange(0.002f)
        a.addUpdateListener { _, v, _ -> onUpdate(v) }
        a.addEndListener { _, canceled, _, _ -> if (!canceled) onEnd?.invoke() }
        a.start()
        if (!animate) a.skipToEnd()
        return a
    }

    private fun prepareEnter(root: View) {
        if (!animate) return
        root.findViewById<View>(R.id.main_card).apply { alpha = 0f; scaleX = 0.9f; scaleY = 0.9f; translationY = -dp(root, 40f) }
        root.findViewById<ShapedAvatarView>(R.id.avatar).apply { progress = 0f; scaleX = 0.6f; scaleY = 0.6f }
        for (id in STAGGER) root.findViewById<View>(id).apply { alpha = 0f; translationY = dp(root, 10f) }
    }

    private fun enter(root: View) {
        if (!animate) return
        val card = root.findViewById<View>(R.id.main_card)
        val dy = dp(root, 40f)
        spring(0f, 1f, SPATIAL_DAMPING, SPATIAL_STIFF) { p -> card.scaleX = 0.9f + 0.1f * p; card.scaleY = card.scaleX; card.translationY = -dy * (1f - p) }
        spring(0f, 1f, EFFECT_DAMPING, EFFECT_STIFFNESS) { card.alpha = it.coerceIn(0f, 1f) }
        // The avatar starts as a circle and springs into the contact's own shape, overshooting on the way.
        val avatar = root.findViewById<ShapedAvatarView>(R.id.avatar)
        root.postDelayed({
            spring(0f, 1f, 0.5f, 350f) { avatar.progress = it }
            spring(0f, 1f, FAST_DAMPING, FAST_STIFF) { p -> avatar.scaleX = 0.6f + 0.4f * p; avatar.scaleY = avatar.scaleX }
        }, 70)
        STAGGER.forEachIndexed { i, id ->
            val v = root.findViewById<View>(id)
            root.postDelayed({
                val rise = dp(root, 10f)
                spring(0f, 1f, SPATIAL_DAMPING, SPATIAL_STIFF) { p -> v.translationY = rise * (1f - p) }
                spring(0f, 1f, EFFECT_DAMPING, EFFECT_STIFFNESS) { v.alpha = it.coerceIn(0f, 1f) }
            }, 90L + 55L * i)
        }
    }

    /** Everything but the avatar: faded out and removed in the collapsed chip. */
    private val FADE = intArrayOf(R.id.details, R.id.actions, R.id.numberChip)

    private val STAGGER = intArrayOf(R.id.callerName, R.id.callerSubtitle, R.id.numberChip, R.id.actions)

    /** Card width/height and the fade of its text and buttons, all driven by one spring value (0 = expanded, 1 = chip). */
    private fun applyCollapse(root: View, p: Float, fullW: Int, chipW: Int, chipH: Int) {
        val card = root.findViewById<View>(R.id.main_card)
        val lp = card.layoutParams
        lp.width = (fullW + (chipW - fullW) * p).toInt()
        lp.height = (expandedH + (chipH - expandedH) * p).toInt()
        card.layoutParams = lp
        val a = (1f - 2.2f * p).coerceIn(0f, 1f)
        for (id in FADE) root.findViewById<View>(id).alpha = a
    }

    private fun collapse(root: View, wm: WindowManager) {
        val lp = params ?: return
        val card = root.findViewById<View>(R.id.main_card)
        val fullW = card.width; val chipW = dp(root, 80f).toInt(); val chipH = chipW
        expandedH = card.height
        busy = true
        spring(0f, 1f, SPATIAL_DAMPING, SPATIAL_STIFF, onEnd = {
            // Settle into a small window docked to the right edge; the chip is already where it will be.
            for (id in FADE) root.findViewById<View>(id).visibility = View.GONE
            card.layoutParams = card.layoutParams.apply { width = ViewGroup.LayoutParams.WRAP_CONTENT; height = ViewGroup.LayoutParams.WRAP_CONTENT }
            lp.width = WindowManager.LayoutParams.WRAP_CONTENT; lp.x = 0; lp.gravity = Gravity.CENTER_VERTICAL or Gravity.END
            runCatching { wm.updateViewLayout(root, lp) }
            collapsed = true; busy = false
            root.setOnClickListener { if (!busy) expand(root, wm) }
        }) { applyCollapse(root, it, fullW, chipW, chipH) }
    }

    private fun expand(root: View, wm: WindowManager) {
        val lp = params ?: return
        val card = root.findViewById<View>(R.id.main_card)
        val chipW = card.width; val chipH = card.height
        val fullW = root.resources.displayMetrics.widthPixels - 2 * (card.layoutParams as ViewGroup.MarginLayoutParams).marginStart
        busy = true
        root.setOnClickListener(null)
        lp.width = WindowManager.LayoutParams.MATCH_PARENT; lp.x = 0; lp.gravity = Gravity.CENTER
        for (id in FADE) root.findViewById<View>(id).apply { visibility = View.VISIBLE; alpha = 0f }
        applyCollapse(root, 1f, fullW, chipW, chipH)
        runCatching { wm.updateViewLayout(root, lp) }
        collapsed = false
        spring(1f, 0f, SPATIAL_DAMPING, SPATIAL_STIFF, onEnd = {
            card.layoutParams = card.layoutParams.apply { width = ViewGroup.LayoutParams.MATCH_PARENT; height = ViewGroup.LayoutParams.WRAP_CONTENT }
            busy = false
        }) { applyCollapse(root, it, fullW, chipW, chipH) }
    }
}
