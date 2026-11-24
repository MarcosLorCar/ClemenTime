package com.marcoslorcar.clementime.ui.screens.scheduleimport

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.marcoslorcar.clementime.data.EntryType
import com.marcoslorcar.clementime.data.SettingsRepository
import com.marcoslorcar.clementime.data.Subject
import com.marcoslorcar.clementime.data.SubjectWithSlots
import com.marcoslorcar.clementime.data.importing.model.ImportFile
import com.marcoslorcar.clementime.data.importing.model.ImportSourceType
import com.marcoslorcar.clementime.data.importing.model.JsonSubject
import com.marcoslorcar.clementime.data.importing.model.ScheduleJsonSchema
import com.marcoslorcar.clementime.data.importing.model.SelectedSubject
import com.marcoslorcar.clementime.data.importing.parser.JsonScheduleParser
import com.marcoslorcar.clementime.data.importing.repository.ImportRepository
import com.marcoslorcar.clementime.ui.screens.scheduleimport.model.ConflictDetail
import com.marcoslorcar.clementime.ui.screens.scheduleimport.model.ConflictStatus
import com.marcoslorcar.clementime.ui.screens.scheduleimport.model.TheoryOverlap
import com.marcoslorcar.clementime.ui.widget.ScheduleWidgetUtils
import com.marcoslorcar.clementime.utils.ConflictSolver
import com.marcoslorcar.clementime.utils.ScheduleDiffChecker
import com.marcoslorcar.clementime.utils.SemesterUtils
import com.marcoslorcar.clementime.worker.FcmTopicManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

sealed interface ImportUiState {
    object LoadingLibrary : ImportUiState
    data class Library(
        val files: List<ImportFile>,
        val error: String? = null,
        val searchQuery: String = ""
    ) : ImportUiState
    object Parsing : ImportUiState
    data class Selection(
        val schema: ScheduleJsonSchema,
        val selectedSubjects: Set<SelectedSubject>,
        val searchQuery: String = "",
        val selectedFile: ImportFile,
        val conflictStatus: ConflictStatus = ConflictStatus.None,
        val existingSubjects: List<SubjectWithSlots> = emptyList(),
        val onboardingEnabled: Boolean = true,
        val hasSeenConflictTooltip: Boolean = false,
        val hasSeenPreviewTooltip: Boolean = false
    ) : ImportUiState
    object Importing : ImportUiState
    object Success : ImportUiState
    data class Error(val message: String) : ImportUiState
}

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val repository: ImportRepository,
    private val parser: JsonScheduleParser,
    @ApplicationContext private val context: Context? = null,
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ImportUiState>(ImportUiState.LoadingLibrary)
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(
                settingsRepository.onboardingTooltipsEnabledFlow,
                settingsRepository.hasSeenImportConflictTooltipFlow,
                settingsRepository.hasSeenImportPreviewTooltipFlow
            ) { enabled, seenConflict, seenPreview -> Triple(enabled, seenConflict, seenPreview) }.collect { (enabled, seenConflict, seenPreview) ->
                // update{} rather than read-then-write: this collector fires independently of user
                // interaction, so a plain `_uiState.value = current.copy(...)` could publish a
                // snapshot captured before a concurrent edit and silently discard it (it was
                // wiping freshly-computed conflictStatus).
                _uiState.update { current ->
                    if (current is ImportUiState.Selection) {
                        current.copy(
                            onboardingEnabled = enabled,
                            hasSeenConflictTooltip = seenConflict,
                            hasSeenPreviewTooltip = seenPreview
                        )
                    } else {
                        current
                    }
                }
            }
        }
    }

    fun loadLibrary(context: Context) {
        viewModelScope.launch {
            _uiState.value = ImportUiState.LoadingLibrary
            try {
                val localFiles = repository.listAvailableImportFiles(context)
                val rawBaseUrl = try {
                    settingsRepository.githubRepoBaseUrlFlow.first()
                } catch (_: Exception) {
                    SettingsRepository.DEFAULT_GITHUB_REPO_BASE_URL
                }

                val baseUrl = repository.normalizeGitHubUrl(rawBaseUrl)

                val cacheMetadata = repository.getCachedRemoteSchedules(context)
                val cacheDir = repository.getCacheDir(context)

                val activeUniId = runCatching { settingsRepository.selectedUniversityIdFlow.first() }.getOrDefault("")
                val activeFacId = runCatching { settingsRepository.selectedFacultyIdFlow.first() }.getOrDefault("")

                val catalogResult = repository.fetchRemoteCatalog(baseUrl)
                val remoteFiles = catalogResult.fold(
                    onSuccess = { (effectiveBaseUrl, catalog) ->
                        val effectiveFolderUrl = when {
                            effectiveBaseUrl.endsWith("schedules_catalog.json") -> effectiveBaseUrl.substringBeforeLast("/") + "/"
                            effectiveBaseUrl.endsWith("schedules_index.json") -> effectiveBaseUrl.substringBeforeLast("/") + "/"
                            effectiveBaseUrl.endsWith("/") -> effectiveBaseUrl
                            else -> "$effectiveBaseUrl/"
                        }
                        catalog.universities.flatMap { uni ->
                            uni.faculties.flatMap { faculty ->
                                val isLinked = activeFacId.isNotBlank() &&
                                    faculty.id.equals(activeFacId, ignoreCase = true) &&
                                    (activeUniId.isBlank() || uni.id.equals(activeUniId, ignoreCase = true))
                                val termSemesterMap = SemesterUtils.resolveCatalogTermSemesters(faculty.terms)
                                val facultyTermCount = SemesterUtils.resolveFacultySemesterCount(faculty.terms)
                                val facultyTermLabels = SemesterUtils.extractCustomCatalogLabels(faculty.terms)

                                faculty.terms.map { term ->
                                    val scopedId = "${uni.id}_${faculty.id}_${term.id}"
                                    val fullPath = when {
                                        term.path.startsWith("http://") || term.path.startsWith("https://") -> term.path
                                        else -> "$effectiveFolderUrl${term.path.removePrefix("/")}"
                                    }
                                    val cachedEntry = cacheMetadata.find { it.id == scopedId }
                                        ?: cacheMetadata.find {
                                            it.id == term.id &&
                                                (it.facultyId.isNullOrBlank() || it.facultyId.equals(faculty.id, ignoreCase = true))
                                        }
                                    val cacheId = cachedEntry?.id ?: scopedId
                                    val cacheExists = File(cacheDir, "cache_${cacheId}.json").exists()
                                    val isCached = cachedEntry != null &&
                                        cachedEntry.hash == term.hash &&
                                        cacheExists
                                    val isUpdateAvailable = cachedEntry != null &&
                                        term.hash != null &&
                                        cachedEntry.hash != term.hash &&
                                        cacheExists

                                    ImportFile(
                                        id = scopedId,
                                        title = term.name,
                                        fileUri = null,
                                        sourceType = ImportSourceType.REMOTE,
                                        remotePath = fullPath,
                                        description = term.description,
                                        isCached = isCached,
                                        isUpdateAvailable = isUpdateAvailable,
                                        updatedTime = term.updatedTime,
                                        remoteHash = term.hash ?: cachedEntry?.hash,
                                        universityId = uni.id,
                                        universityName = uni.name,
                                        facultyId = faculty.id,
                                        facultyName = faculty.name,
                                        fcmTopicPrefix = faculty.fcmTopicPrefix,
                                        termId = term.id,
                                        isLinkedFaculty = isLinked,
                                        termSemester = termSemesterMap[term.id],
                                        facultyTermCount = facultyTermCount,
                                        facultyTermLabels = facultyTermLabels
                                    )
                                }
                            }
                        }.sortedByDescending { it.isLinkedFaculty }
                    },
                    onFailure = { _ ->
                        cacheMetadata.filter { entry ->
                            File(cacheDir, "cache_${entry.id}.json").exists() ||
                                (!entry.termId.isNullOrBlank() && File(cacheDir, "cache_${entry.termId}.json").exists())
                        }.map { entry ->
                            val isLinked = activeFacId.isNotBlank() &&
                                entry.facultyId.equals(activeFacId, ignoreCase = true) &&
                                (activeUniId.isBlank() || entry.universityId.isNullOrBlank() || entry.universityId.equals(activeUniId, ignoreCase = true))

                            ImportFile(
                                id = entry.id,
                                title = entry.title,
                                fileUri = null,
                                sourceType = ImportSourceType.REMOTE,
                                remotePath = entry.remotePath,
                                description = entry.description,
                                isCached = true,
                                updatedTime = entry.updatedTime,
                                remoteHash = entry.hash,
                                universityId = entry.universityId,
                                universityName = entry.universityName,
                                facultyId = entry.facultyId,
                                facultyName = entry.facultyName,
                                fcmTopicPrefix = entry.fcmTopicPrefix,
                                termId = entry.termId,
                                isLinkedFaculty = isLinked,
                                termSemester = SemesterUtils.parseTermSemester(entry.termId ?: entry.id)
                            )
                        }.sortedByDescending { it.isLinkedFaculty }
                    }
                )

                val combined = remoteFiles + localFiles
                val errorMsg = if (catalogResult.isFailure && remoteFiles.isNotEmpty()) {
                    "Offline: showing cached schedules"
                } else if (catalogResult.isFailure) {
                    "Online library unreachable. Check your connection."
                } else {
                    null
                }

                _uiState.value = ImportUiState.Library(combined, errorMsg)
            } catch (e: Exception) {
                _uiState.value = ImportUiState.Library(emptyList(), "Failed to load library: ${e.localizedMessage}")
            }
        }
    }

    fun loadFile(context: Context, file: ImportFile) {
        viewModelScope.launch {
            _uiState.value = ImportUiState.Parsing
            try {
                val schemaResult: Result<ScheduleJsonSchema> = when (file.sourceType) {
                    ImportSourceType.REMOTE -> {
                        repository.fetchRemoteScheduleSchema(context, file)
                    }
                    ImportSourceType.CUSTOM -> {
                        val jsonString = withContext(Dispatchers.IO) {
                            File(file.fileUri!!).readText()
                        }
                        repository.parseJsonString(jsonString)
                    }
                }

                val normalizedResult = schemaResult.map { parsedSchema ->
                    val targetTermSem = file.termSemester
                        ?: if (file.sourceType == ImportSourceType.REMOTE) {
                            SemesterUtils.parseTermSemester(file.termId ?: file.id)
                        } else {
                            null
                        }
                    if (targetTermSem != null && parsedSchema.semester != null && parsedSchema.semester != targetTermSem) {
                        remapSchemaSemester(parsedSchema, targetTermSem)
                    } else {
                        parsedSchema
                    }
                }

                val schemaSemesters = normalizedResult.getOrNull()?.let { collectSchemaSemesters(it) } ?: setOf(1)
                val existing = repository.getExistingActiveSubjectsForSemesters(schemaSemesters)
                val onboardingEnabled = settingsRepository.onboardingTooltipsEnabledFlow.first()
                val hasSeenConflictTooltip = settingsRepository.hasSeenImportConflictTooltipFlow.first()
                val hasSeenPreviewTooltip = settingsRepository.hasSeenImportPreviewTooltipFlow.first()
                
                normalizedResult.fold(
                    onSuccess = { schema ->
                        _uiState.value = ImportUiState.Selection(
                            schema = schema,
                            selectedSubjects = emptySet(),
                            selectedFile = file,
                            conflictStatus = ConflictStatus.None,
                            existingSubjects = existing,
                            onboardingEnabled = onboardingEnabled,
                            hasSeenConflictTooltip = hasSeenConflictTooltip,
                            hasSeenPreviewTooltip = hasSeenPreviewTooltip
                        )
                    },
                    onFailure = { error ->
                        _uiState.value = ImportUiState.Error("Invalid schedule format: ${error.localizedMessage}")
                    }
                )
            } catch (e: Exception) {
                _uiState.value = ImportUiState.Error("Failed to load schedule: ${e.localizedMessage}")
            }
        }
    }

    fun selectAndSaveNewFile(context: Context, uri: Uri) {
        viewModelScope.launch {
            _uiState.value = ImportUiState.Parsing
            val result = repository.saveJsonFile(context, uri)
            result.fold(
                onSuccess = { importFile ->
                    loadFile(context, importFile)
                },
                onFailure = { error ->
                    _uiState.value = ImportUiState.Error("Failed to save and parse file: ${error.localizedMessage}")
                }
            )
        }
    }

    fun deleteFile(context: Context, file: ImportFile) {
        viewModelScope.launch {
            if (file.sourceType == ImportSourceType.CUSTOM) {
                repository.deleteCustomImportFile(context, file.id)
                loadLibrary(context)
            }
        }
    }

    fun resetToLibrary(context: Context) {
        loadLibrary(context)
    }

    fun toggleSubjectSelection(subject: JsonSubject, groupName: String) {
        val currentState = _uiState.value
        if (currentState is ImportUiState.Selection) {
            val selectedSubject = SelectedSubject(subject, groupName)
            val updatedSelection = currentState.selectedSubjects.toMutableSet()
            if (updatedSelection.contains(selectedSubject)) {
                updatedSelection.remove(selectedSubject)
            } else {
                updatedSelection.add(selectedSubject)
            }
            _uiState.value = currentState.copy(
                selectedSubjects = updatedSelection,
                conflictStatus = calculateConflictStatus(updatedSelection, currentState.existingSubjects)
            )
        }
    }

    fun toggleAllSubjects(targetSubjects: Collection<SelectedSubject>) {
        val currentState = _uiState.value
        if (currentState is ImportUiState.Selection) {
            val updatedSelection = currentState.selectedSubjects.toMutableSet()
            val allSelected = targetSubjects.isNotEmpty() && targetSubjects.all { updatedSelection.contains(it) }
            if (allSelected) {
                updatedSelection.removeAll(targetSubjects.toSet())
            } else {
                updatedSelection.addAll(targetSubjects)
            }
            _uiState.value = currentState.copy(
                selectedSubjects = updatedSelection,
                conflictStatus = calculateConflictStatus(updatedSelection, currentState.existingSubjects)
            )
        }
    }

    fun toggleSectionSubjects(sectionSubjects: Collection<SelectedSubject>) {
        toggleAllSubjects(sectionSubjects)
    }

    fun deselectAll() {
        val currentState = _uiState.value
        if (currentState is ImportUiState.Selection) {
            _uiState.value = currentState.copy(
                selectedSubjects = emptySet(),
                conflictStatus = ConflictStatus.None
            )
        }
    }

    @Suppress("unused")
    fun selectAllSubjects(subjects: Collection<SelectedSubject>? = null) {
        val currentState = _uiState.value
        if (currentState is ImportUiState.Selection) {
            val toSelect = subjects ?: run {
                val fromRoot = currentState.schema.subjects.map { SelectedSubject(it, "General") }
                val fromYears = currentState.schema.years.flatMap { year ->
                    val yearCommon = year.subjects.map { SelectedSubject(it, "${year.name} Common") }
                    val fromGroups = year.groups.flatMap { group ->
                        group.subjects.map { SelectedSubject(it, "${year.name} ${group.name}") }
                    }
                    yearCommon + fromGroups
                }
                fromRoot + fromYears
            }
            val updatedSelection = currentState.selectedSubjects.toMutableSet()
            updatedSelection.addAll(toSelect)
            _uiState.value = currentState.copy(
                selectedSubjects = updatedSelection,
                conflictStatus = calculateConflictStatus(updatedSelection, currentState.existingSubjects)
            )
        }
    }

    private fun calculateConflictStatus(
        selected: Set<SelectedSubject>,
        existing: List<SubjectWithSlots>
    ): ConflictStatus {
        if (selected.isEmpty()) return ConflictStatus.None

        var slotIdCounter = 1L
        val subjectsWithSlots = selected.map { selectedSubject ->
            val jsonSubject = selectedSubject.subject
            val semester = jsonSubject.semester ?: 1
            val subjectId = (jsonSubject.code + semester).hashCode().toLong()
            val subject = Subject(
                id = subjectId,
                code = jsonSubject.code,
                name = jsonSubject.name,
                color = jsonSubject.color ?: Subject.PRESET_COLORS.indices.random(), // Random preset if null
                isActive = true,
                semester = semester
            )
            val theorySlots = jsonSubject.theorySlots.map { 
                with(parser) { it.toClassSlot(subjectId).copy(id = slotIdCounter++) }
            }
            val labSlots = jsonSubject.labVariants.flatMap { (groupName, variantSlots) ->
                variantSlots.map { slot ->
                    with(parser) {
                        slot.toClassSlot(subjectId).copy(
                            id = slotIdCounter++,
                            labGroupName = groupName,
                            entryType = EntryType.LAB
                        )
                    }
                }
            }
            SubjectWithSlots(subject, theorySlots + labSlots)
        }

        val mappedExisting = existing.map { sWithSlots ->
            val activeSlots = sWithSlots.slots.filter { slot ->
                slot.entryType == EntryType.THEORY ||
                sWithSlots.subject.selectedLabGroup == null ||
                slot.labGroupName == sWithSlots.subject.selectedLabGroup
            }.map { slot ->
                slot.copy(id = slotIdCounter++)
            }
            sWithSlots.copy(slots = activeSlots)
        }

        val allSubjectsWithSlots = subjectsWithSlots + mappedExisting

        val solutions = ConflictSolver.findSolutions(allSubjectsWithSlots)
        val optimal = solutions.firstOrNull() ?: return ConflictStatus.None

        val theoryOverlaps = mutableListOf<TheoryOverlap>()
        val theorySlots = allSubjectsWithSlots.flatMap { s -> 
            s.slots.filter { it.entryType == EntryType.THEORY }.map { s.subject to it } 
        }
        
        for (i in theorySlots.indices) {
            for (j in i + 1 until theorySlots.size) {
                val (subj1, slot1) = theorySlots[i]
                val (subj2, slot2) = theorySlots[j]
                if (subj1.semester == subj2.semester && slot1.dayOfWeek == slot2.dayOfWeek && slot1.startTime < slot2.endTime && slot2.startTime < slot1.endTime) {
                    theoryOverlaps.add(
                        TheoryOverlap(
                            subject1Code = subj1.code,
                            subject1Name = subj1.name,
                            subject2Code = subj2.code,
                            subject2Name = subj2.name,
                            slots = listOf(slot1 to slot2)
                        )
                    )
                }
            }
        }

        val hasTheoryConflict = theoryOverlaps.isNotEmpty()
        val hasLabConflict = optimal.overlapsCount > 0

        return if (!hasTheoryConflict && !hasLabConflict) {
            ConflictStatus.Valid
        } else {
            val theoryOverlappingSlots = theoryOverlaps.flatMap { overlap ->
                overlap.slots.flatMap { pair ->
                    val s1 = allSubjectsWithSlots.find { it.subject.code == overlap.subject1Code }!!.subject
                    val s2 = allSubjectsWithSlots.find { it.subject.code == overlap.subject2Code }!!.subject
                    listOf(s1 to pair.first, s2 to pair.second)
                }
            }.distinctBy { it.second.id }

            ConflictStatus.Conflict(
                ConflictDetail(
                    selectedSubjects = selected.toList(),
                    theoryOverlaps = theoryOverlaps,
                    hasLabCombinationWithZeroOverlaps = !hasLabConflict,
                    theoryOverlappingSlots = theoryOverlappingSlots,
                    labOverlappingSlots = if (hasLabConflict) optimal.totalSlots else emptyList(),
                    labOverlappingSlotIds = if (hasLabConflict) optimal.overlappingSlotIds else emptySet()
                )
            )
        }
    }

    fun confirmImport() {
        val currentState = _uiState.value
        if (currentState is ImportUiState.Selection) {
            viewModelScope.launch {
                _uiState.value = ImportUiState.Importing
                try {
                    val importedSubjects = currentState.selectedSubjects.toList()
                    repository.importSubjects(importedSubjects)
                    
                    val currentSemester = settingsRepository.currentSemesterFlow.first()
                    var shouldAutoChangeSemester = false
                    
                    // 1. Check if the schema explicitly defines a semester
                    currentState.schema.semester?.let { schemaSemester ->
                        if (schemaSemester != currentSemester) {
                            settingsRepository.setCurrentSemester(schemaSemester)
                            shouldAutoChangeSemester = true
                        }
                    }
                    
                    // 2. Even if schema doesn't define it, if ALL imported subjects are for a different semester, switch
                    if (!shouldAutoChangeSemester) {
                        val distinctSemesters = importedSubjects.map { it.subject.semester ?: 1 }.distinct()
                        if (distinctSemesters.size == 1 && distinctSemesters.first() != currentSemester) {
                            settingsRepository.setCurrentSemester(distinctSemesters.first())
                            shouldAutoChangeSemester = true
                        }
                    }

                    if (shouldAutoChangeSemester) {
                        val hasManuallyChanged = settingsRepository.hasManuallyChangedSemesterFlow.first()
                        if (!hasManuallyChanged) {
                            settingsRepository.setWasSemesterAutoChanged(true)
                        }
                    }

                    if (currentState.selectedFile.sourceType == ImportSourceType.REMOTE) {
                        val selectedFile = currentState.selectedFile
                        val semesterKey = selectedFile.termId ?: selectedFile.id
                        val inferredTermSemester = selectedFile.termSemester
                            ?: SemesterUtils.parseTermSemester(semesterKey)

                        val targetSemesters = (listOfNotNull(currentState.schema.semester ?: inferredTermSemester) +
                            importedSubjects.mapNotNull { it.subject.semester })
                            .distinct()
                            .ifEmpty { listOf(settingsRepository.currentSemesterFlow.first()) }

                        val maxImportedSemester = targetSemesters.maxOrNull() ?: 1
                        val remoteTermCount = maxOf(
                            selectedFile.facultyTermCount ?: 0,
                            maxImportedSemester,
                            SemesterUtils.inferMinSemestersFromTermId(semesterKey)
                        )
                        if (remoteTermCount >= 1) {
                            settingsRepository.setSemestersPerYear(remoteTermCount)
                        }

                        val prevUniId = settingsRepository.selectedUniversityIdFlow.first()
                        val prevFacId = settingsRepository.selectedFacultyIdFlow.first()

                        if (!selectedFile.facultyId.isNullOrBlank()) {
                            val newUniId = selectedFile.universityId ?: ""
                            val newFacId = selectedFile.facultyId
                            val isSwitchingFaculty = prevFacId.isNotBlank() && (
                                !prevFacId.equals(newFacId, ignoreCase = true) ||
                                    (prevUniId.isNotBlank() && newUniId.isNotBlank() && !prevUniId.equals(newUniId, ignoreCase = true))
                            )
                            if (isSwitchingFaculty) {
                                settingsRepository.resetScheduleHashes()
                            }
                            settingsRepository.setSelectedFaculty(
                                universityId = newUniId,
                                facultyId = newFacId,
                                universityName = selectedFile.universityName,
                                facultyName = selectedFile.facultyName,
                                fcmTopicPrefix = selectedFile.fcmTopicPrefix
                            )
                            settingsRepository.setSelectedFacultyTermLabels(selectedFile.facultyTermLabels)
                            if (context != null) {
                                FcmTopicManager.syncRegistration(context, settingsRepository)
                            }
                        }

                        val remoteHash = selectedFile.remoteHash
                            ?: context?.let { ctx ->
                                repository.getCachedRemoteSchedules(ctx)
                                    .find { it.id == selectedFile.id || (!selectedFile.termId.isNullOrBlank() && it.id == selectedFile.termId) }
                                    ?.hash
                            }

                        for (sem in targetSemesters) {
                            if (!remoteHash.isNullOrBlank()) {
                                settingsRepository.setLastKnownScheduleHash(sem, remoteHash)
                            }
                            if (context != null) {
                                val cachedSlots = repository.getCachedRemoteSlotsById(
                                    context,
                                    selectedFile.id
                                ).ifEmpty {
                                    repository.getCachedRemoteSlotsForSemester(
                                        context = context,
                                        semester = sem,
                                        universityId = selectedFile.universityId,
                                        facultyId = selectedFile.facultyId
                                    )
                                }
                                if (cachedSlots.isNotEmpty()) {
                                    val activeInSem = repository.getExistingActiveSubjects(sem)
                                    val subjectHashes = ScheduleDiffChecker.computeRemoteSubjectHashes(
                                        existingSubjects = activeInSem,
                                        remoteSlots = cachedSlots,
                                        currentSemester = sem
                                    )
                                    settingsRepository.setLastRemoteSubjectHashes(sem, subjectHashes)
                                }
                            }
                        }
                    } else {
                        val maxCustomSemester = (
                            collectSchemaSemesters(currentState.schema) +
                                importedSubjects.mapNotNull { it.subject.semester }
                            ).maxOrNull() ?: 1
                        val configuredSemesters = settingsRepository.semestersPerYearFlow.first()
                        if (maxCustomSemester > configuredSemesters) {
                            settingsRepository.setSemestersPerYear(maxCustomSemester)
                        }
                    }
                    
                    settingsRepository.setHasSeenAddSlotTooltip(true)
                    ScheduleWidgetUtils.updateWidget(context)
                    _uiState.value = ImportUiState.Success
                } catch (e: Exception) {
                    _uiState.value = ImportUiState.Error("Import failed: ${e.localizedMessage}")
                }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        val currentState = _uiState.value
        if (currentState is ImportUiState.Selection) {
            _uiState.value = currentState.copy(searchQuery = query)
        } else if (currentState is ImportUiState.Library) {
            _uiState.value = currentState.copy(searchQuery = query)
        }
    }

    fun markConflictTooltipSeen() {
        viewModelScope.launch {
            settingsRepository.setHasSeenImportConflictTooltip(true)
        }
    }

    fun markPreviewTooltipSeen() {
        viewModelScope.launch {
            settingsRepository.setHasSeenImportPreviewTooltip(true)
        }
    }

    private fun collectSchemaSemesters(schema: ScheduleJsonSchema): Set<Int> {
        val result = mutableSetOf<Int>()
        schema.semester?.let { result.add(it) }
        schema.subjects.mapNotNull { it.semester }.let { result.addAll(it) }
        schema.years.forEach { year ->
            year.subjects.mapNotNull { it.semester }.let { result.addAll(it) }
            year.groups.forEach { group ->
                group.subjects.mapNotNull { it.semester }.let { result.addAll(it) }
            }
        }
        if (result.isEmpty()) result.add(1)
        return result
    }

    private fun remapSchemaSemester(schema: ScheduleJsonSchema, targetSemester: Int): ScheduleJsonSchema {
        return schema.copy(
            semester = targetSemester,
            subjects = schema.subjects.map { it.copy(semester = targetSemester) },
            years = schema.years.map { year ->
                year.copy(
                    subjects = year.subjects.map { it.copy(semester = targetSemester) },
                    groups = year.groups.map { group ->
                        group.copy(subjects = group.subjects.map { it.copy(semester = targetSemester) })
                    }
                )
            }
        )
    }

}