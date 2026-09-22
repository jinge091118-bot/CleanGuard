package com.cleanguard.app.domain.model

data class ScanError(
    val itemId: String?,
    val message: String,
    val recoverable: Boolean = true,
)

