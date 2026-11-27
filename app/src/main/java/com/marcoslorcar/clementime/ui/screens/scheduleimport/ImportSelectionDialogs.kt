@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)
package com.marcoslorcar.clementime.ui.screens.scheduleimport

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.marcoslorcar.clementime.R
import com.marcoslorcar.clementime.data.ClassSlot
import com.marcoslorcar.clementime.data.EntryType
import com.marcoslorcar.clementime.data.Subject
import com.marcoslorcar.clementime.data.SubjectWithSlots
import com.marcoslorcar.clementime.data.importing.model.SelectedSubject
import com.marcoslorcar.clementime.data.importing.parser.JsonScheduleParser
import com.marcoslorcar.clementime.ui.components.ScheduleMiniPreview
import com.marcoslorcar.clementime.ui.screens.scheduleimport.model.ConflictDetail

@Composable
internal fun ImportPreviewDialog(
    previewSubject: SelectedSubject,
    selectedSubjects: Set<SelectedSubject>,
    existingSubjects: List<SubjectWithSlots>,
    onDismiss: () -> Unit
) {
    val cumulativeSlots = remember(previewSubject, selectedSubjects, existingSubjects) {
        val parser = JsonScheduleParser()
        var slotIdCounter = 1000L // Use high IDs for preview slots to avoid conflict if any

        val slotsList = mutableListOf<Pair<Subject, ClassSlot>>()

        // 1. Existing subjects
        existingSubjects.forEach { sWithSlots ->
            sWithSlots.slots.forEach { slot ->
                if (slot.entryType == EntryType.THEORY ||
                    sWithSlots.subject.selectedLabGroup == null ||
                    slot.labGroupName == sWithSlots.subject.selectedLabGroup
                ) {
                    slotsList.add(sWithSlots.subject to slot.copy(id = slotIdCounter++))
                }
            }
        }

        // 2. Selected subjects (from current import session)
        selectedSubjects.filter { it != previewSubject }.forEach { sel ->
            val subj = Subject(
                id = sel.subject.code.hashCode().toLong(),
                code = sel.subject.code,
                name = sel.subject.name,
                color = sel.subject.color ?: Subject.PRESET_COLORS.indices.random(),
                isActive = true
            )
            // Theory slots
            sel.subject.theorySlots.forEach { jsonSlot ->
                slotsList.add(subj to with(parser) { jsonSlot.toClassSlot(subj.id).copy(id = slotIdCounter++) })
            }
            // Lab variants
            sel.subject.labVariants.forEach { (group, variantSlots) ->
                variantSlots.forEach { jsonSlot ->
                    slotsList.add(subj to with(parser) {
                        jsonSlot.toClassSlot(subj.id).copy(
                            id = slotIdCounter++,
                            labGroupName = group,
                            entryType = EntryType.LAB
                        )
                    })
                }
            }
        }

        // 3. Long-pressed subject (Preview)
        val previewSubjObj = Subject(
            id = previewSubject.subject.code.hashCode().toLong(),
            code = previewSubject.subject.code,
            name = previewSubject.subject.name,
            color = previewSubject.subject.color ?: Subject.PRESET_COLORS.indices.random(),
            isActive = true
        )
        previewSubject.subject.theorySlots.forEach { jsonSlot ->
            slotsList.add(previewSubjObj to with(parser) { jsonSlot.toClassSlot(previewSubjObj.id).copy(id = slotIdCounter++) })
        }
        previewSubject.subject.labVariants.forEach { (group, variantSlots) ->
            variantSlots.forEach { jsonSlot ->
                slotsList.add(previewSubjObj to with(parser) {
                    jsonSlot.toClassSlot(previewSubjObj.id).copy(
                        id = slotIdCounter++,
                        labGroupName = group,
                        entryType = EntryType.LAB
                    )
                })
            }
        }

        slotsList
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = stringResource(R.string.import_preview_header),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = previewSubject.subject.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                ScheduleMiniPreview(
                    modifier = Modifier.fillMaxSize(),
                    slots = cumulativeSlots,
                    overlappingSlotIds = emptySet()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        }
    )
}

@Composable
internal fun ConflictDetailsDialog(
    detail: ConflictDetail,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.import_conflict_dialog_title)) },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                if (detail.theoryOverlaps.isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.import_conflict_theory_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.import_conflict_theory_desc),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        detail.theoryOverlaps.forEach { overlap ->
                            Text(
                                text = stringResource(R.string.bullet_point_with_ampersand, overlap.subject1Name, overlap.subject2Name),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(start = 8.dp, top = 4.dp)
                            )
                        }
                    }

                    item {
                        Text(
                            text = stringResource(R.string.import_conflict_theory_visualization),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ScheduleMiniPreview(
                            modifier = Modifier.height(150.dp),
                            slots = detail.theoryOverlappingSlots,
                            overlappingSlotIds = detail.theoryOverlappingSlots.map { it.second.id }.toSet()
                        )
                    }
                }

                if (!detail.hasLabCombinationWithZeroOverlaps) {
                    item {
                        Text(
                            text = stringResource(R.string.import_conflict_lab_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.import_conflict_lab_desc),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }

                    item {
                        Text(
                            text = stringResource(R.string.import_conflict_theory_visualization),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ScheduleMiniPreview(
                            modifier = Modifier.height(150.dp),
                            slots = detail.labOverlappingSlots,
                            overlappingSlotIds = detail.labOverlappingSlotIds
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        }
    )
}
