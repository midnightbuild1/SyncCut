package com.synccut.app.core.model

import java.util.UUID

data class EditSuggestion(
    val id: String = UUID.randomUUID().toString(),
    val sourceMediaId: String,
    val inUs: Long,
    val outUs: Long,
    val score: Float,
    val reason: String,
    val isAccepted: Boolean = true
) {
    val durationUs: Long
        get() = outUs - inUs
}

data class EditHistory(
    val id: Long = 0L,
    val projectId: String,
    val operation: String,
    val serializedBefore: String,
    val serializedAfter: String,
    val createdAt: Long = System.currentTimeMillis()
)

enum class ExportStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    CANCELLED,
    FAILED
}

data class ExportJob(
    val id: String = UUID.randomUUID().toString(),
    val projectId: String,
    val outputUri: String? = null,
    val status: ExportStatus = ExportStatus.PENDING,
    val progress: Float = 0.0f,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
