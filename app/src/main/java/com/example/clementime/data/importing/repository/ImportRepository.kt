package com.example.clementime.data.importing.repository

import android.content.Context
import android.net.Uri
import com.example.clementime.data.EntryType
import com.example.clementime.data.Matter
import com.example.clementime.data.ScheduleDao
import com.example.clementime.data.importing.model.ImportFile
import com.example.clementime.data.importing.model.ScheduleJsonSchema
import com.example.clementime.data.importing.model.SelectedMatter
import com.example.clementime.data.importing.parser.JsonScheduleParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject

class ImportRepository @Inject constructor(
    private val dao: ScheduleDao,
    private val parser: JsonScheduleParser = JsonScheduleParser()
) {

    private fun String.sha256(): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(this.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    fun parseJsonString(jsonContent: String): Result<ScheduleJsonSchema> {
        return parser.parseJson(jsonContent)
    }

    suspend fun listAvailableImportFiles(context: Context): List<ImportFile> = withContext(Dispatchers.IO) {
        val list = mutableListOf<ImportFile>()

        // 1. Check bundled asset
        var bundledHash = ""
        try {
            context.assets.open("primer_cuatrimestre.json").use { stream ->
                val jsonString = stream.bufferedReader().readText()
                bundledHash = jsonString.sha256()
                val schema = parser.parseJson(jsonString).getOrNull()
                val title = schema?.title ?: "Horarios 2026/2027 - 1º Cuatrimestre"
                list.add(ImportFile(id = "bundled", title = title, isBundled = true, fileUri = null))
            }
        } catch (e: Exception) {
            // Asset not found or failed to parse
        }

        // 2. Check local imports directory
        val dir = File(context.filesDir, "imports")
        if (dir.exists()) {
            dir.listFiles { _, name -> name.endsWith(".json") }?.forEach { file ->
                try {
                    val jsonString = file.readText()
                    val fileHash = jsonString.sha256()
                    // Deduplicate against bundled file
                    if (fileHash == bundledHash) {
                        file.delete() // Clean up local duplicate
                        return@forEach
                    }
                    val schema = parser.parseJson(jsonString).getOrNull()
                    val title = schema?.title ?: file.name
                    list.add(ImportFile(id = file.name, title = title, isBundled = false, fileUri = file.absolutePath))
                } catch (e: Exception) {
                    // Skip invalid files
                }
            }
        }

        list
    }

    suspend fun saveJsonFile(context: Context, uri: Uri): Result<ImportFile> = withContext(Dispatchers.IO) {
        try {
            val jsonString = context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader().readText()
            } ?: throw Exception("Could not open file stream")

            val schema = parser.parseJson(jsonString).getOrThrow()
            val title = schema.title ?: "Custom Import"
            val fileHash = jsonString.sha256()

            // Check if it matches bundled file hash
            var bundledHash = ""
            try {
                context.assets.open("primer_cuatrimestre.json").use { stream ->
                    bundledHash = stream.bufferedReader().readText().sha256()
                }
            } catch (e: Exception) {
                // Ignore
            }

            if (fileHash == bundledHash) {
                // It's the bundled file, return reference to bundled representation instead of duplicating
                return@withContext Result.success(ImportFile(id = "bundled", title = title, isBundled = true, fileUri = null))
            }

            val filename = "import_$fileHash.json"
            val dir = File(context.filesDir, "imports")
            if (!dir.exists()) dir.mkdirs()

            val destFile = File(dir, filename)
            destFile.writeText(jsonString)

            Result.success(ImportFile(id = filename, title = title, isBundled = false, fileUri = destFile.absolutePath))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteCustomImportFile(context: Context, filename: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(File(context.filesDir, "imports"), filename)
        if (file.exists() && !filename.contains("/") && !filename.contains("..")) {
            file.delete()
        } else {
            false
        }
    }

    suspend fun importMatters(selectedMatters: List<SelectedMatter>) {
        selectedMatters.forEach { selected ->
            val jsonMatter = selected.matter
            val matter = Matter(
                code = jsonMatter.code,
                name = jsonMatter.name,
                color = jsonMatter.color ?: Matter.PRESET_COLORS.random(),
                courseGroup = selected.courseGroup,
                isActive = true
            )

            val theorySlots = jsonMatter.theorySlots.map {
                with(parser) { it.toClassSlot() }
            }

            val labSlots = jsonMatter.labVariants.flatMap { (groupName, variantSlots) ->
                variantSlots.map { slot ->
                    with(parser) {
                        slot.toClassSlot().copy(
                            labGroupName = groupName,
                            entryType = EntryType.LAB
                        )
                    }
                }
            }

            dao.upsertMatterWithSlots(matter, theorySlots + labSlots)
        }
    }
}