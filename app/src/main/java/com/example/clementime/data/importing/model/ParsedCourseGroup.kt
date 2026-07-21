package com.example.clementime.data.importing.model

data class ParsedCourseGroup(
    val groupName: String,
    val term: String,
    val subjects: List<ParsedSubject> = emptyList()
)
