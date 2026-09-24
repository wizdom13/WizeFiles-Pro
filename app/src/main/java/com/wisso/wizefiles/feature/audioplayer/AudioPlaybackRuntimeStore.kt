package com.wisso.wizefiles.feature.audioplayer

/** In-process handoff used when notification controls reopen the player Activity. */
object AudioPlaybackRuntimeStore {
    @Volatile
    private var activeSessionId: String? = null

    fun setActiveSession(id: String) {
        activeSessionId = id
    }

    fun activeSessionId(): String? = activeSessionId

    fun clear(id: String?) {
        if (id != null && activeSessionId == id) activeSessionId = null
    }
}
