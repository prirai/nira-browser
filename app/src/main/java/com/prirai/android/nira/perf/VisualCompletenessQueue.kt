package com.prirai.android.nira.perf

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A queue for tasks that should run after the app's initial visual completeness.
 * This allows the UI to render quickly while deferring heavy initialization work.
 * 
 * Pattern inspired by Firefox's visual completeness queue.
 */
class VisualCompletenessQueue {
    private val mutex = Mutex()
    private var isReady = false
    private val queuedTasks = mutableListOf<() -> Unit>()
    
    /**
     * Mark the queue as ready and run all queued tasks.
     * Should be called after the first frame is drawn.
     */
    suspend fun ready() {
        val tasksToRun = mutex.withLock {
            if (isReady) {
                return // Already ready, nothing to do
            }
            isReady = true
            queuedTasks.toList().also { queuedTasks.clear() }
        }
        
        // Run tasks outside the lock
        tasksToRun.forEach { task ->
            try {
                task()
            } catch (e: Exception) {
                android.util.Log.e("VisualCompletenessQueue", "Task failed", e)
            }
        }
    }
    
    /**
     * Run a task immediately if the queue is ready, otherwise queue it for later.
     */
    fun runIfReadyOrQueue(task: () -> Unit) {
        CoroutineScope(Dispatchers.Main).launch {
            val shouldRunNow = mutex.withLock {
                if (isReady) {
                    true
                } else {
                    queuedTasks.add(task)
                    false
                }
            }
            
            if (shouldRunNow) {
                try {
                    task()
                } catch (e: Exception) {
                    android.util.Log.e("VisualCompletenessQueue", "Task failed", e)
                }
            }
        }
    }
    
    /**
     * Check if the queue is ready without acquiring the lock.
     * Note: This is a racy read and should only be used for logging/metrics.
     */
    fun isReady(): Boolean = isReady
}
