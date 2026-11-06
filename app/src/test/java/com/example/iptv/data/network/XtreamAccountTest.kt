package com.example.iptv.data.network

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class XtreamAccountTest {
    private fun account(auth: String, status: String, expiry: String, created: String) = XtreamParser.parseAccount(
        Json.parseToJsonElement("""{"user_info":{"username":"teste","auth":$auth,"status":"$status","exp_date":$expiry,"created_at":$created}}"""),
        "http://panel/"
    )

    @Test fun xtreamSecondsBecomeMillisecondsForAccountAndPlan() {
        val info = account("1", "Active", "1900000000", "1700000000")
        assertTrue(info.isActive)
        assertEquals(1_900_000_000_000L, info.expiresAtMillis ?: 0L)
        assertEquals(1_700_000_000_000L, info.createdAtMillis ?: 0L)
    }

    @Test fun alreadyConvertedMillisecondsAndUnlimitedPlansAreAccepted() {
        assertEquals(1_900_000_000_000L, account("1", "Active", "1900000000000", "null").expiresAtMillis ?: 0L)
        assertNull(account("1", "Active", "null", "0").expiresAtMillis)
    }

    @Test fun explicitAuthFailureOrExpiredStatusIsNotAnActiveAccount() {
        assertFalse(account("0", "Active", "null", "null").isActive)
        assertFalse(account("1", "Expired", "null", "null").isActive)
    }
}
