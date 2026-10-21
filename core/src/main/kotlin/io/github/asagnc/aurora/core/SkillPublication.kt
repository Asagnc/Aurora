package io.github.asagnc.aurora.core

import kotlinx.coroutines.*

/** The index commit must atomically publish its entries and remove this operation's journal row. */
interface SkillPublicationJournal {
    suspend fun begin(id: String, hashes: List<String>)
    suspend fun pending(): List<Pair<String, List<String>>>
    suspend fun known(hash: String): Boolean
    suspend fun clearOperation(id: String)
}

/** Caller serializes catalog mutations; cancellation is accepted before, not midway through, publication. */
class SkillPublication(private val store: SkillContentStore, private val journal: SkillPublicationJournal) {
    suspend fun publish(id: String, packages: List<PreparedSkill>, commit: suspend () -> Unit) {
        currentCoroutineContext().ensureActive()
        withContext(NonCancellable) {
            journal.begin(id, packages.map { it.manifest.digest }.distinct())
            try {
                packages.forEach(store::publish)
                commit()
            } catch (failure: Throwable) {
                try { recoverOperation(id) } catch (cleanup: Throwable) { failure.addSuppressed(cleanup) }
                throw failure
            }
        }
    }

    suspend fun recover() {
        for ((id, hashes) in journal.pending()) recoverOperation(id, hashes)
    }

    private suspend fun recoverOperation(id: String) {
        val hashes = journal.pending().firstOrNull { it.first == id }?.second ?: return
        recoverOperation(id, hashes)
    }

    private suspend fun recoverOperation(id: String, hashes: List<String>) {
        for (hash in hashes) if (!journal.known(hash)) store.remove(hash)
        // Keep the row if a removal fails, so a later recovery can retry.
        journal.clearOperation(id)
    }
}
