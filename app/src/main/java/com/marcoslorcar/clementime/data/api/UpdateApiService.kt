package com.marcoslorcar.clementime.data.api

import kotlinx.serialization.Serializable
import retrofit2.Response
import retrofit2.http.GET

interface UpdateApiService {
    @GET("repos/MarcosLorCar/ClemenTime/releases/latest")
    suspend fun getLatestRelease(): Response<GitHubRelease>
}

@Serializable
data class GitHubRelease(
    val tag_name: String,
    val html_url: String,
    val body: String,
    val assets: List<GitHubAsset>
)

@Serializable
data class GitHubAsset(
    val name: String,
    val browser_download_url: String,
    val size: Long
)
