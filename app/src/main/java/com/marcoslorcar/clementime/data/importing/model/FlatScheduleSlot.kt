package com.marcoslorcar.clementime.data.importing.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class JsonFlatSlot(
    val grupo: String = "",
    val cuatrimestre: String = "1C",
    val dia: String = "",
    @SerialName("hora_inicio") val horaInicio: String = "",
    @SerialName("hora_fin") val horaFin: String = "",
    val asignatura: String = "",
    val tipo: String = "teoría",
    val aula: String = "",
    val profesor: String = "",
    @SerialName("es_laboratorio") val esLaboratorio: Boolean = false,
    @SerialName("grupo_practicas") val grupoPracticas: String = ""
)
