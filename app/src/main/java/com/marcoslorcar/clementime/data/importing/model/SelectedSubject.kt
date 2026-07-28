package com.marcoslorcar.clementime.data.importing.model

data class SelectedSubject(
    val subject: JsonSubject,
    val courseGroup: String,
    val remotePath: String? = null
)
