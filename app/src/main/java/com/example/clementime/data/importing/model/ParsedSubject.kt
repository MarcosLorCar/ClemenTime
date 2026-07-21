package com.example.clementime.data.importing.model

data class ParsedSubject(
    val code: String,      // e.g. "FunProg1"
    val fullName: String,  // e.g. "Fundamentos de Programación 1"
    val theorySlots: List<ParsedTimeSlot> = emptyList(),
    val labVariants: Map<String, List<ParsedTimeSlot>> = emptyMap() // Key is lab name like "Lab-A1"
)
