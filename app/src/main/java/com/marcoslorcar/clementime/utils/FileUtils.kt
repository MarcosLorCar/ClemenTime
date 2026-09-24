package com.marcoslorcar.clementime.utils

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.marcoslorcar.clementime.R
import com.marcoslorcar.clementime.data.AttachedFileItem
import java.io.File
import java.util.Locale
import java.util.UUID

fun resolveFileName(context: Context, uri: Uri): String {
    var name = ""
    if (uri.scheme == "content") {
        val cursor = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        }.getOrNull()
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    name = it.getString(index) ?: ""
                }
            }
        }
    }
    if (name.isEmpty()) {
        name = uri.lastPathSegment?.substringAfterLast('/') ?: "file"
    }
    return name
}

fun copyUriToInternalAttachments(context: Context, sourceUri: Uri, originalName: String): Pair<File, Long>? {
    return runCatching {
        val attachmentsDir = File(context.filesDir, "attachments")
        if (!attachmentsDir.exists()) {
            attachmentsDir.mkdirs()
        }
        val extension = originalName.substringAfterLast('.', "")
        val baseName = originalName.substringBeforeLast('.').ifBlank { "attachment" }
        val sanitizedBase = baseName.replace(Regex("[^a-zA-Z0-9._-]"), "_").take(40)
        val uniqueName = if (extension.isNotBlank()) {
            "${UUID.randomUUID()}_${sanitizedBase}.$extension"
        } else {
            "${UUID.randomUUID()}_$sanitizedBase"
        }
        val destFile = File(attachmentsDir, uniqueName)

        var totalBytes = 0L
        context.contentResolver.openInputStream(sourceUri)?.use { input ->
            destFile.outputStream().use { output ->
                totalBytes = input.copyTo(output)
            }
        } ?: return null

        destFile to totalBytes
    }.getOrNull()
}

fun deleteInternalAttachment(context: Context, fileItem: AttachedFileItem) {
    runCatching {
        val uri = fileItem.uriString.toUri()
        if (uri.scheme == "file") {
            uri.path?.let { File(it).delete() }
        } else if (uri.scheme == "content") {
            // If legacy persistable SAF URI, attempt release
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        } else {
            val file = File(fileItem.uriString)
            if (file.exists()) {
                file.delete()
            }
        }
    }
}

fun deleteOriginalDocument(context: Context, uri: Uri): Boolean {
    return runCatching {
        if (DocumentsContract.isDocumentUri(context, uri)) {
            DocumentsContract.deleteDocument(context.contentResolver, uri)
        } else if (uri.scheme == "content") {
            context.contentResolver.delete(uri, null, null) > 0
        } else if (uri.scheme == "file") {
            uri.path?.let { File(it).delete() } ?: false
        } else {
            File(uri.toString()).delete()
        }
    }.getOrDefault(false)
}

fun openFile(context: Context, fileItem: AttachedFileItem) {
    if (!isUriAccessible(context, fileItem.uriString)) {
        Toast.makeText(context, context.getString(R.string.file_not_found_message), Toast.LENGTH_SHORT).show()
        return
    }

    val uri = fileItem.uriString.toUri()
    val viewUri: Uri
    val mimeType: String

    if (uri.scheme == "file" || uri.scheme.isNullOrEmpty()) {
        val file = if (uri.scheme == "file") File(uri.path ?: "") else File(fileItem.uriString)
        if (!file.exists()) {
            Toast.makeText(context, context.getString(R.string.file_not_found_message), Toast.LENGTH_SHORT).show()
            return
        }
        val authority = "${context.packageName}.fileprovider"
        viewUri = FileProvider.getUriForFile(context, authority, file)
        mimeType = getMimeType(file)
    } else {
        viewUri = uri
        val crType = context.contentResolver.getType(uri)
        mimeType = if (crType.isNullOrBlank() || crType == "*/*" || crType == "application/octet-stream") {
            val ext = fileItem.name.substringAfterLast('.', "")
            MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase()) ?: "*/*"
        } else {
            crType
        }
    }

    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(viewUri, mimeType)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = ClipData.newRawUri("", viewUri)
    }

    try {
        val chooser = Intent.createChooser(intent, null).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(chooser)
    } catch (_: Exception) {
        Toast.makeText(context, context.getString(R.string.no_apps_for_file_message), Toast.LENGTH_SHORT).show()
    }
}

fun isImageFile(context: Context, uriString: String): Boolean {
    val uri = uriString.toUri()
    val mimeType = if (uri.scheme == "content") {
        context.contentResolver.getType(uri)
    } else {
        val file = if (uri.scheme == "file") File(uri.path ?: "") else File(uriString)
        getMimeType(file)
    }
    if (mimeType?.startsWith("image/") == true) return true
    val ext = uriString.substringAfterLast('.', "").lowercase()
    return ext in listOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "svg")
}

fun isUriAccessible(context: Context, uriString: String): Boolean {
    return runCatching {
        val uri = uriString.toUri()
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                it.moveToFirst()
            } ?: false
        } else {
            val file = if (uri.scheme == "file") File(uri.path ?: "") else File(uriString)
            file.exists()
        }
    }.getOrDefault(false)
}

fun getMimeType(file: File): String {
    val extension = file.extension.lowercase()
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"
}

fun getFriendlyFileType(fileName: String, mimeType: String = ""): String {
    val ext = fileName.substringAfterLast('.', "").lowercase()
    return when (ext) {
        "pdf" -> "PDF"
        "doc", "docx", "odt", "rtf" -> "Word"
        "xls", "xlsx", "ods", "csv" -> "Excel"
        "ppt", "pptx", "odp" -> "PowerPoint"
        "jpg", "jpeg", "png", "webp", "gif", "bmp", "svg" -> "Image"
        "zip", "rar", "7z", "tar", "gz" -> "Archive"
        "txt", "md" -> "Text"
        "kt", "java", "c", "cpp", "h", "hpp", "py", "js", "ts", "html", "css", "json", "xml", "sql" -> "Code"
        else -> {
            if (mimeType.isNotBlank() && mimeType != "*/*" && mimeType != "File") {
                mimeType.substringAfterLast('/')
                    .replace("vnd.", "")
                    .replace("x-", "")
                    .uppercase()
                    .take(10)
            } else {
                "File"
            }
        }
    }
}

fun formatFileSize(bytes: Long?): String? {
    if (bytes == null || bytes <= 0L) return null
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> {
            val kb = bytes / 1024.0
            String.format(Locale.US, "%.1f KB", kb)
        }
        else -> {
            val mb = bytes / (1024.0 * 1024.0)
            String.format(Locale.US, "%.1f MB", mb)
        }
    }
}
