package com.cleanguard.app.platform.intents

import android.content.Intent
import android.provider.DocumentsContract
import androidx.core.net.toUri

class DownloadTreeIntentFactory {
    fun create(): Intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
        addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
                Intent.FLAG_GRANT_PREFIX_URI_PERMISSION,
        )
        putExtra(
            DocumentsContract.EXTRA_INITIAL_URI,
            "content://com.android.externalstorage.documents/document/primary%3ADownload".toUri(),
        )
    }
}
