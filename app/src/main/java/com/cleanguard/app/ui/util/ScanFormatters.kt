package com.cleanguard.app.ui.util

import androidx.annotation.StringRes
import com.cleanguard.app.R
import com.cleanguard.app.domain.model.MediaScanExecutionType

@StringRes
fun MediaScanExecutionType.labelResource(): Int = when (this) {
    MediaScanExecutionType.FULL -> R.string.scan_type_full
    MediaScanExecutionType.INCREMENTAL -> R.string.scan_type_incremental
    MediaScanExecutionType.FULL_RESYNC -> R.string.scan_type_full_resync
    MediaScanExecutionType.API_29_FULL -> R.string.scan_type_api29_full
}
