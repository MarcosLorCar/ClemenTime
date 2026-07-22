package com.example.clementime.data

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class AttachedFileItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val fileType: String = "File",
    val uriString: String
)
