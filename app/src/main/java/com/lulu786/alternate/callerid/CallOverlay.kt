package com.lulu786.alternate.callerid

import android.annotation.SuppressLint
import android.app.KeyguardManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.os.Build
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import com.lulu786.alternate.R
import com.lulu786.alternate.data.CallerHit
import com.lulu786.alternate.data.PhotoStore
import com.lulu786.alternate.ui.components.AvatarSpec
import com.lulu786.alternate.util.Countries
import com.lulu786.alternate.util.displayName

/** The popup shown over calls. Plain Views on purpose: no Compose startup cost inside a broadcast. */
object CallOverlay {
    private var view: View? = null
    private var params: WindowManager.LayoutParams? = null
    private var collapsed = false

    val isShowing get() = view != null

    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    fun show(context: Context, hit: CallerHit) {
        if (view != null) return
        val themed = DynamicColors.wrapContextIfAvailable(ContextThemeWrapper(context, R.style.Theme_Alternate_Overlay))
        val root = LayoutInflater.from(themed).inflate(R.layout.caller_overlay, null)
        bind(themed, root, hit)

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

        root.findViewById<ImageButton>(R.id.close_btn).setOnClickListener { dismiss(context) }
        root.findViewById<ImageButton>(R.id.collapse_btn).setOnClickListener { if (collapsed) expand(root, wm) else collapse(root, wm) }
        enableDrag(root, wm, lp)

        runCatching { wm.addView(root, lp) }.onSuccess { view = root; params = lp }
    }

    fun dismiss(context: Context) {
        val v = view ?: return
        runCatching { (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(v) }
        view = null; params = null; collapsed = false
    }

    private fun bind(context: Context, root: View, hit: CallerHit) {
        root.findViewById<TextView>(R.id.callerName).text = displayName(hit.prefix, hit.name, hit.suffix)
        root.findViewById<TextView>(R.id.callerSubtitle).apply {
            val s = listOf(hit.appointment, hit.location).filter { it.isNotBlank() }.joinToString(" · ")
            text = s; visibility = if (s.isEmpty()) View.GONE else View.VISIBLE
        }
        val dial = Countries.byCode(hit.countryCode)?.dialCode
        root.findViewById<TextView>(R.id.callerNumber).text =
            "${hit.label} · ${if (dial != null) "+$dial ${hit.phoneNumber}" else "+${hit.fullPhoneNumber}"}"

        val letter = root.findViewById<TextView>(R.id.avatarLetter)
        val photo = root.findViewById<ImageView>(R.id.callerPhoto)
        val light = (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) != Configuration.UI_MODE_NIGHT_YES
        val (bg, fg) = AvatarSpec.roles(AvatarSpec.color(hit.avatarSeed), MaterialColors.getColor(context, androidx.appcompat.R.attr.colorPrimary, 0), light)
        letter.text = hit.name.trim().firstOrNull()?.uppercase() ?: "#"
        letter.setTextColor(fg)
        letter.background.mutate().setTint(bg)

        val bmp = hit.photoPath.takeIf { it.isNotEmpty() }
            ?.let { BitmapFactory.decodeFile(PhotoStore(context).file(it).path) }
        if (bmp != null) { photo.setImageBitmap(bmp); photo.visibility = View.VISIBLE }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun enableDrag(root: View, wm: WindowManager, lp: WindowManager.LayoutParams) {
        var startX = 0; var startY = 0; var touchX = 0f; var touchY = 0f
        root.setOnTouchListener { _, e ->
            if (collapsed) return@setOnTouchListener false
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { startX = lp.x; startY = lp.y; touchX = e.rawX; touchY = e.rawY; true }
                MotionEvent.ACTION_MOVE -> {
                    lp.x = startX + (e.rawX - touchX).toInt(); lp.y = startY + (e.rawY - touchY).toInt()
                    runCatching { wm.updateViewLayout(root, lp) }; true
                }
                else -> false
            }
        }
    }

    private fun collapse(root: View, wm: WindowManager) {
        val lp = params ?: return
        val width = root.resources.displayMetrics.widthPixels
        lp.width = WindowManager.LayoutParams.WRAP_CONTENT
        lp.x = width - COLLAPSED_PX
        lp.gravity = Gravity.TOP or Gravity.START
        root.findViewById<View>(R.id.main_card).alpha = 0.8f
        runCatching { wm.updateViewLayout(root, lp) }
        collapsed = true
        root.setOnClickListener { expand(root, wm) }
    }

    private fun expand(root: View, wm: WindowManager) {
        val lp = params ?: return
        lp.width = WindowManager.LayoutParams.MATCH_PARENT; lp.x = 0; lp.gravity = Gravity.CENTER
        root.findViewById<View>(R.id.main_card).alpha = 1f
        runCatching { wm.updateViewLayout(root, lp) }
        collapsed = false
        root.setOnClickListener(null)
        enableDrag(root, wm, lp)
    }

    private const val COLLAPSED_PX = 60
}
