package com.autoride.accepter

import android.app.Activity
import android.content.Intent
import android.widget.Button
import androidx.core.content.ContextCompat

enum class PrimaryTab {
    CORE,
    AUTOMATION,
    PROFILE
}

object PrimaryTabs {
    fun bind(activity: Activity, selected: PrimaryTab) {
        val coreButton = activity.findViewById<Button>(R.id.tabCoreButton)
        val automationButton = activity.findViewById<Button>(R.id.tabAutomationButton)
        val profileButton = activity.findViewById<Button>(R.id.tabProfileButton)

        configureTabButton(activity, coreButton, selected == PrimaryTab.CORE) {
            activity.startActivity(Intent(activity, MainActivity::class.java))
        }
        configureTabButton(activity, automationButton, selected == PrimaryTab.AUTOMATION) {
            activity.startActivity(Intent(activity, AutomationActivity::class.java))
        }
        configureTabButton(activity, profileButton, selected == PrimaryTab.PROFILE) {
            activity.startActivity(Intent(activity, ProfileActivity::class.java))
        }
    }

    private fun configureTabButton(
        activity: Activity,
        button: Button?,
        selected: Boolean,
        onClick: () -> Unit
    ) {
        if (button == null) return

        if (selected) {
            button.setBackgroundResource(R.drawable.bg_glossy_button)
            button.setTextColor(ContextCompat.getColor(activity, R.color.text_primary))
            button.isEnabled = false
            return
        }

        button.setBackgroundResource(R.drawable.bg_glass_outline_button)
        button.setTextColor(ContextCompat.getColor(activity, R.color.text_secondary))
        button.isEnabled = true
        button.setOnClickListener { onClick() }
    }
}
