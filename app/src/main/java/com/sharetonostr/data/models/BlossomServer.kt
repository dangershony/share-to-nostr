package com.sharetonostr.data.models

import kotlinx.serialization.Serializable

@Serializable
data class BlossomServer(
    val url: String,
    val name: String = url
)
