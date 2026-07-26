package com.marcoslorcar.clementime.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.marcoslorcar.clementime.R
import com.marcoslorcar.clementime.data.AttachedFileItem
import java.io.File

fun resolveFileName(context: Context, uri: Uri): String {
    var name = ""
    if (uri.scheme == "content") {
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    name = it.getString(index)
                }
            }
        }
    }
    if (name.isEmpty()) {
        name = uri.path?.substringAfterLast('/') ?: "file"
    }
    return name
}

fun openFile(context: Context, fileItem: AttachedFileItem) {
    val uri = fileItem.uriString.toUri()
    val intent = Intent(Intent.ACTION_VIEW).apply {
        if (uri.scheme == "content") {
            setDataAndType(uri, context.contentResolver.getType(uri) ?: "*/*")
        } else {
            val file = File(fileItem.uriString)
            if (!file.exists()) {
                Toast.makeText(context, context.getString(R.string.file_not_found_message), Toast.LENGTH_SHORT).show()
                return
            }
            val authority = "${context.packageName}.fileprovider"
            val fileProviderUri = FileProvider.getUriForFile(context, authority, file)
            setDataAndType(fileProviderUri, getMimeType(file))
        }
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(Intent.createChooser(intent, "Open File"))
    } catch (_: Exception) {
        Toast.makeText(context, context.getString(R.string.no_apps_for_file_message), Toast.LENGTH_SHORT).show()
    }
}

fun isImageFile(context: Context, uriString: String): Boolean {
    val uri = uriString.toUri()
    val mimeType = if (uri.scheme == "content") {
        context.contentResolver.getType(uri)
    } else {
        getMimeType(File(uriString))
    }
    return mimeType?.startsWith("image/") == true
}

fun isUriAccessible(context: Context, uriString: String): Boolean {
    val uri = uriString.toUri()
    return try {
        if (uri.scheme == "content") {
            context.contentResolver.query(uri, null, null, null, null)?.use { it.moveToFirst() } ?: false
        } else {
            File(uriString).exists()
        }
    } catch (_: Exception) {
        false
    }
}

fun getMimeType(file: File): String {
    val extension = file.extension.lowercase()
    return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"
}
