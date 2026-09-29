// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only
package com.wisso.wizefiles.feature.sync

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SyncthingJsonStoreTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun freshStoreRestoresCommittedStateAndIgnoresIncompleteWrite() {
        val file = File(temporary.root, "state.json")
        SyncthingJsonStore(file).update { it.put("identity", "retained") }
        File(temporary.root, "state.json.pending").writeText("broken")
        assertEquals("retained", SyncthingJsonStore(file).read().getString("identity"))
    }

    @Test fun failedMutationAndCorruptInputNeverEraseExistingState() {
        val file = File(temporary.root, "state.json")
        val store = SyncthingJsonStore(file)
        store.update { it.put("folders", 3) }
        assertThrows(IllegalStateException::class.java) { store.update { error("stop") } }
        assertEquals(3, store.read().getInt("folders"))
        file.writeText("corrupt")
        assertThrows(Exception::class.java) { store.update { it.put("folders", 0) } }
        assertEquals("corrupt", file.readText())
    }

    @Test fun separateCallersCannotLoseEachOthersUpdates() {
        val file = File(temporary.root, "state.json")
        val threads = (0..3).map {
            Thread { repeat(20) {
                SyncthingJsonStore(file).update { json ->
                    json.put("count", json.optInt("count") + 1)
                }
            } }.apply { start() }
        }
        threads.forEach { it.join() }
        assertEquals(80, SyncthingJsonStore(file).read().getInt("count"))
    }
}
