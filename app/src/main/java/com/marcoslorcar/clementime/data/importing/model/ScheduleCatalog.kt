package com.marcoslorcar.clementime.data.importing.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 3-level hierarchical catalog schema (v2):
 * Universities -> Faculties -> Terms.
 */
@Serializable
data class ScheduleCatalog(
    val version: Int = 2,
    val universities: List<UniversityCatalogEntry> = emptyList()
)

@Serializable
data class UniversityCatalogEntry(
    val id: String,
    val name: String,
    val faculties: List<FacultyCatalogEntry> = emptyList()
)

@Serializable
data class FacultyCatalogEntry(
    val id: String,
    val name: String,
    @SerialName("fcm_topic_prefix")
    val fcmTopicPrefix: String? = null,
    @SerialName("subgroups_label")
    val subgroupsLabel: String? = null,
    val terms: List<TermCatalogEntry> = emptyList()
)

@Serializable
data class TermCatalogEntry(
    val id: String,
    val name: String,
    val description: String? = null,
    val path: String,
    val hash: String? = null,
    val updatedTime: String? = null
) {
    /**
     * Converts a catalog term entry into a legacy [RemoteScheduleSummary]
     * for seamless compatibility across downstream viewmodels and diff engines.
     */
    fun toRemoteSummary(): RemoteScheduleSummary = RemoteScheduleSummary(
        id = id,
        title = name,
        description = description,
        path = path,
        hash = hash,
        updatedTime = updatedTime
    )
}
