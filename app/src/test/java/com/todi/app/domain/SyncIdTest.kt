package com.todi.app.domain

import com.todi.app.domain.util.newSyncId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncIdTest {
    @Test
    fun syncId_isDashedFreeAndUuidShaped() {
        val id = newSyncId()

        assertEquals(32, id.length)
        assertTrue("expected lowercase hex, got $id", id.all { it in "0123456789abcdef" })
    }

    @Test
    fun syncId_isUniquePerCall() {
        val ids = List(1_000) { newSyncId() }

        assertEquals(ids.size, ids.toSet().size)
    }
}
