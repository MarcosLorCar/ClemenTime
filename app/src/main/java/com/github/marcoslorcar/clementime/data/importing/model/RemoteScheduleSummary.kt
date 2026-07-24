package com.github.marcoslorcar.clementime.data.importing.model

import kotlinx.serialization.Serializable

/**
 * Representation of an online schedule item available in the remote GitHub repository catalog.
 */
@Serializable
data class RemoteScheduleSummary(
    val id: String,
    val title: String,
    val description: String? = null,
    val path: String,
    val updatedTime: String? = null
)
