package me.kavishdevar.librepods.bluetooth

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Called on the UI thread. A return to the confirmed value still supersedes an active write. */
class AttWriteDebouncer(private val scope: CoroutineScope, private val debounceMillis: Long = 100) {
    private val jobs = mutableMapOf<ATTHandles, Job>()

    fun submit(handle: ATTHandles, value: ByteArray, state: AttSettingState?,
               write: suspend (ByteArray) -> Unit) {
        if (value.isEmpty()) return
        val desired = value.copyOf()
        val previous = jobs.remove(handle)
        val wasActive = previous?.isActive == true
        previous?.cancel()
        // A cancelled coroutine may still be inside a blocking Bluetooth write. Restore the latest
        // intent after that operation, even when it matches the value confirmed before it started.
        if (!wasActive && state?.phase != SettingPhase.PENDING && state?.confirmed == desired.toList()) return
        jobs[handle] = scope.launch {
            delay(debounceMillis)
            write(desired)
        }
    }

    fun cancelAll() {
        jobs.values.forEach { it.cancel() }
        jobs.clear()
    }
}
