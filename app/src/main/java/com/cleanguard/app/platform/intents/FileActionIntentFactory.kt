package com.cleanguard.app.platform.intents

import android.content.Intent
import android.provider.DocumentsContract
import androidx.core.net.toUri

class FileActionIntentFactory {
    fun createPreview(contentUri: String, mimeType: String?): Intent =
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri.toUri(), mimeType ?: "*/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    fun createShare(contentUri: String, mimeType: String?): Intent =
        Intent(Intent.ACTION_SEND).apply {
            type = mimeType ?: "*/*"
            putExtra(Intent.EXTRA_STREAM, contentUri.toUri())
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    fun createLocate(treeUri: String?): Intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
        addCategory(Intent.CATEGORY_OPENABLE)
        type = "*/*"
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        treeUri?.takeIf { it.isNotBlank() }?.let {
            putExtra(DocumentsContract.EXTRA_INITIAL_URI, it.toUri())
        }
    }
}
