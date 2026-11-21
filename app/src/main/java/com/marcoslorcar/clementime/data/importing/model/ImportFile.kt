package com.marcoslorcar.clementime.data.importing.model

enum class ImportSourceType {
    REMOTE,
    CUSTOM
}

data class ImportFile(
    val id: String,
    val title: String,
    val fileUri: String? = null,
    val sourceType: ImportSourceType = ImportSourceType.CUSTOM,
    val remotePath: String? = null,
    val description: String? = null,
    val isCached: Boolean = false,
    val isUpdateAvailable: Boolean = false,
    val updatedTime: String? = null,
    val remoteHash: String? = null,
    val universityId: String? = null,
    val universityName: String? = null,
    val facultyId: String? = null,
    val facultyName: String? = null,
    val fcmTopicPrefix: String? = null,
    val termId: String? = null,
    val isLinkedFaculty: Boolean = false
)
