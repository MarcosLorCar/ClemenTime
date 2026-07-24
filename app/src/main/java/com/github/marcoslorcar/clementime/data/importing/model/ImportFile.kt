package com.github.marcoslorcar.clementime.data.importing.model

enum class ImportSourceType {
    BUNDLED,
    REMOTE,
    CUSTOM
}

data class ImportFile(
    val id: String,
    val title: String,
    val isBundled: Boolean = false,
    val fileUri: String? = null,
    val sourceType: ImportSourceType = if (isBundled) ImportSourceType.BUNDLED else ImportSourceType.CUSTOM,
    val remotePath: String? = null,
    val description: String? = null
)
