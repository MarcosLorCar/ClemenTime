package com.github.marcoslorcar.clementime.data.api

import com.github.marcoslorcar.clementime.data.importing.model.RemoteScheduleSummary
import com.github.marcoslorcar.clementime.data.importing.model.ScheduleJsonSchema
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Url

interface GitHubScheduleApiService {

    @GET
    suspend fun getScheduleIndex(
        @Url url: String
    ): Response<List<RemoteScheduleSummary>>

    @GET
    suspend fun getScheduleSchema(
        @Url url: String
    ): Response<ScheduleJsonSchema>
}
