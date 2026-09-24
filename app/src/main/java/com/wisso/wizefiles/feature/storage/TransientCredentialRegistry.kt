package com.wisso.wizefiles.storage

internal class TransientCredentialRegistry<S, A>(
    private val authorityOf: (S) -> A,
    private val storedServers: () -> Iterable<S>
) {
    private val lock = Any()
    private val temporaryServers = LinkedHashSet<S>()

    fun find(authority: A): S? {
        val temporary = synchronized(lock) {
            temporaryServers.firstOrNull { authorityOf(it) == authority }
        }
        return temporary ?: storedServers().firstOrNull { authorityOf(it) == authority }
    }

    fun add(server: S) {
        synchronized(lock) { temporaryServers += server }
    }

    fun remove(server: S) {
        synchronized(lock) { temporaryServers -= server }
    }
}
