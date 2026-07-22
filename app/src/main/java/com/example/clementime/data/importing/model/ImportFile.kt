package com.example.clementime.data.importing.model

data class ImportFile(
    val id: String,
    val title: String,
    val isBundled: Boolean,
    val fileUri: String? = null
)
