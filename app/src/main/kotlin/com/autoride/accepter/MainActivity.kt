package com.autoride.accepter

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.navigation.NavigationView
import java.text.DateFormat
import java.util.Date

class MainActivity : AccessControlledActivity() {
    private lateinit var mainStatusLabel: TextView
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navigationView: NavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawerLayout = findViewById(R.id.mainDrawerLayout)
        navigationView = findViewById(R.id.mainNavigationView)
        mainStatusLabel = findViewById(R.id.mainStatusLabel)

        findViewById<ImageButton>(R.id.openSidebarButton).setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }
        findViewById<Button>(R.id.openProfileFromCoreButton).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
        findViewById<Button>(R.id.openAutomationFromCoreButton).setOnClickListener {
            startActivity(Intent(this, AutomationActivity::class.java))
        }

        PrimaryTabs.bind(this, PrimaryTab.CORE)
        setupSidebar()
    }

    override fun onAccessGranted(snapshot: AppAccessSnapshot) {
        val expiryText = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(snapshot.subscriptionExpiresAtMs))
        mainStatusLabel.text = getString(
            R.string.main_access_active_status,
            authManager.currentUserEmail(),
            expiryText
        )
        bindSidebarHeader(snapshot)
    }

    private fun bindSidebarHeader(snapshot: AppAccessSnapshot) {
        if (navigationView.headerCount == 0) return
        val headerView = navigationView.getHeaderView(0)
        val headerSubtitle = headerView.findViewById<TextView>(R.id.drawerHeaderSubtitle)
        val headerProfileButton = headerView.findViewById<Button>(R.id.drawerProfileButton)

        val expiryText = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
            .format(Date(snapshot.subscriptionExpiresAtMs))
        headerSubtitle.text = getString(R.string.drawer_header_active_until, expiryText)
        headerProfileButton.setOnClickListener {
            drawerLayout.closeDrawer(GravityCompat.START)
            startActivity(Intent(this, ProfileActivity::class.java))
        }
    }

    private fun setupSidebar() {
        navigationView.setNavigationItemSelectedListener { item ->
            drawerLayout.closeDrawer(GravityCompat.START)
            when (item.itemId) {
                R.id.nav_core_dashboard -> true
                R.id.nav_automation -> {
                    startActivity(Intent(this, AutomationActivity::class.java))
                    true
                }
                R.id.nav_profile -> {
                    startActivity(Intent(this, ProfileActivity::class.java))
                    true
                }
                R.id.nav_logout -> {
                    authManager.logout()
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                    true
                }
                else -> false
            }
        }
    }
}
