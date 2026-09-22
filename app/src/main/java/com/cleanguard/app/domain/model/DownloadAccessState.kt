package com.cleanguard.app.domain.model

data class DownloadAccessState(
    val isGranted: Boolean = false,
    val directoryName: String? = null,
    val errorMessage: String? = null,
)
