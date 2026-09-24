package com.wisso.wizefiles.feature.filebrowser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserCommandRouterTest {
    @Test fun `router checks availability before dispatch`() {
        val target = FakeTarget(setOf(BrowserCommand.COPY, BrowserCommand.PASTE))
        val router = BrowserCommandRouter()

        assertTrue(router.execute(target, BrowserCommand.COPY))
        assertFalse(router.execute(target, BrowserCommand.DELETE))
        assertTrue(router.execute(target, BrowserCommand.PASTE))
        assertTrue(target.executed == listOf(BrowserCommand.COPY, BrowserCommand.PASTE))
    }

    @Test fun `router safely rejects missing target`() {
        assertFalse(BrowserCommandRouter().execute(null, BrowserCommand.COPY))
    }

    private class FakeTarget(private val available: Set<BrowserCommand>) : BrowserCommandTarget {
        val executed = mutableListOf<BrowserCommand>()
        override fun isBrowserCommandAvailable(command: BrowserCommand) = command in available
        override fun performBrowserCommand(command: BrowserCommand): Boolean {
            executed += command
            return true
        }
    }
}
