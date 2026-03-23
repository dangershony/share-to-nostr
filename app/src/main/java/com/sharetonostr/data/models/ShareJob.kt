package com.sharetonostr.data.models

/**
 * Represents the state of an in-progress share operation.
 */
data class ShareJob(
    val sourceUrl: String,
    val title: String = "",
    val caption: String = "",
    val thumbnailUrl: String? = null,
    val duration: Long = 0,
    val state: ShareState = ShareState.PENDING,
    val progress: Float = 0f,
    val errorMessage: String? = null,
    val blossomUrl: String? = null,
    val noteId: String? = null
)

enum class ShareState {
    PENDING,
    FETCHING_INFO,
    DOWNLOADING,
    UPLOADING,
    SIGNING,
    PUBLISHING,
    COMPLETE,
    ERROR
}
