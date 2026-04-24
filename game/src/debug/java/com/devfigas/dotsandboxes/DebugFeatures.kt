package com.devfigas.dotsandboxes

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import com.devfigas.dotsandboxes.debug.DebugMenuActivity

object DebugFeatures {

    fun setupDebugButton(activity: Activity) {
        val rootView = activity.findViewById<View>(android.R.id.content)
        val contentFrame = (rootView as? FrameLayout) ?: return
        val mainLayout = contentFrame.getChildAt(0) ?: return

        val wrapper = FrameLayout(activity).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        contentFrame.removeView(mainLayout)
        wrapper.addView(mainLayout)

        val density = activity.resources.displayMetrics.density
        val fabSize = (48 * density).toInt()
        val margin = (16 * density).toInt()

        val debugButton = TextView(activity).apply {
            text = "D"
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#99000000"))
                setStroke((1 * density).toInt(), Color.parseColor("#FFFFFF"))
            }
            elevation = 6 * density
            contentDescription = "Debug"
            layoutParams = FrameLayout.LayoutParams(fabSize, fabSize).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                setMargins(margin, margin, margin, margin)
            }
        }
        debugButton.setOnClickListener {
            activity.startActivity(Intent(activity, DebugMenuActivity::class.java))
        }

        wrapper.addView(debugButton)
        contentFrame.addView(wrapper)
    }
}
