// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.util

sealed interface ActionState<A, R> {
    class Ready<A, R> : ActionState<A, R> {
        override fun equals(other: Any?): Boolean = other is Ready<*, *>

        override fun hashCode(): Int = Ready::class.java.hashCode()
    }

    data class Running<A, R>(val argument: A) : ActionState<A, R>

    data class Success<A, R>(val argument: A, val result: R) : ActionState<A, R>

    data class Error<A, R>(val argument: A, val throwable: Throwable) : ActionState<A, R>
}

val ActionState<*, *>.isReady: Boolean
    get() = this is ActionState.Ready<*, *>

val ActionState<*, *>.isRunning: Boolean
    get() = this is ActionState.Running<*, *>

val ActionState<*, *>.isFinished: Boolean
    get() = this is ActionState.Success<*, *> || this is ActionState.Error<*, *>
