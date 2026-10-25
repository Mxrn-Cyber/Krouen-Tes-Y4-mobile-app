package com.kroeungtes.app.data.cloud

import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Waits for a Firebase/Play-services Task inside a coroutine. */
suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnCompleteListener { task ->
        if (!cont.isActive) return@addOnCompleteListener
        val error = task.exception
        if (task.isSuccessful) cont.resume(task.result) else cont.resumeWithException(error ?: IllegalStateException("Task failed"))
    }
}
