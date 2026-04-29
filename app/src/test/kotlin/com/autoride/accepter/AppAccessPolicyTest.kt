package com.autoride.accepter

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppAccessPolicyTest {

    @Test
    fun shouldRejectMissingSupabaseKey() {
        assertFalse(AppAccessPolicy.isSupabaseConfigured("https://example.supabase.co", ""))
    }

    @Test
    fun shouldRejectPlaceholderSupabaseConfig() {
        assertFalse(AppAccessPolicy.isSupabaseConfigured("https://YOUR_PROJECT.supabase.co", "real-key"))
        assertFalse(AppAccessPolicy.isSupabaseConfigured("https://example.supabase.co", "YOUR_KEY"))
    }

    @Test
    fun shouldAllowLocalAccessOnlyForDebugBuilds() {
        assertTrue(AppAccessPolicy.isLocalAccessAllowed(isDebug = true, localAccessMode = true))
        assertFalse(AppAccessPolicy.isLocalAccessAllowed(isDebug = false, localAccessMode = true))
        assertFalse(AppAccessPolicy.isLocalAccessAllowed(isDebug = true, localAccessMode = false))
    }

    @Test
    fun localAccessSnapshotShouldBeActive() {
        val snapshot = AppAccessPolicy.localAccessSnapshot()

        assertTrue(snapshot.hasActiveAccess)
        assertTrue(snapshot.subscriptionExpiresAtMs > System.currentTimeMillis())
    }
}
