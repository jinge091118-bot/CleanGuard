package com.cleanguard.app.data.apk

import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.net.toUri
import com.cleanguard.app.domain.apk.ApkArchiveInspector
import com.cleanguard.app.domain.apk.ApkArchiveMetadata
import java.io.File
import java.io.FileNotFoundException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

class AndroidApkArchiveInspector(
    private val context: Context,
    private val contentResolver: ContentResolver = context.contentResolver,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ApkArchiveInspector {
    override suspend fun inspect(contentUri: String): ApkArchiveMetadata? = withContext(ioDispatcher) {
        val temporaryApk = File.createTempFile(TEMP_PREFIX, APK_SUFFIX, context.cacheDir)
        try {
            contentResolver.openInputStream(contentUri.toUri()).use { input ->
                if (input == null) throw FileNotFoundException("Unable to open APK URI")
                temporaryApk.outputStream().buffered().use { output ->
                    val buffer = ByteArray(STREAM_BUFFER_SIZE)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (count > 0) output.write(buffer, 0, count)
                    }
                }
            }
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageArchiveInfo(
                    temporaryApk.absolutePath,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_META_DATA.toLong()),
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageArchiveInfo(
                    temporaryApk.absolutePath,
                    PackageManager.GET_META_DATA,
                )
            } ?: return@withContext null
            val applicationInfo = packageInfo.applicationInfo?.apply {
                sourceDir = temporaryApk.absolutePath
                publicSourceDir = temporaryApk.absolutePath
            }
            ApkArchiveMetadata(
                packageName = packageInfo.packageName,
                versionName = packageInfo.versionName,
                longVersionCode = packageInfo.longVersionCode,
                applicationLabel = applicationInfo?.let {
                    runCatching { context.packageManager.getApplicationLabel(it).toString() }.getOrNull()
                },
            )
        } finally {
            temporaryApk.delete()
        }
    }

    private companion object {
        const val TEMP_PREFIX = "cg-apk-"
        const val APK_SUFFIX = ".apk"
        const val STREAM_BUFFER_SIZE = 64 * 1024
    }
}
