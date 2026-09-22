package com.cleanguard.app.data.apps

import android.app.AppOpsManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Process
import android.os.storage.StorageManager
import com.cleanguard.app.domain.model.AppStorageItem
import com.cleanguard.app.domain.model.AppStorageScanResult
import com.cleanguard.app.domain.model.UsageAccessStatus
import com.cleanguard.app.domain.repository.AppStorageRepository
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

class AndroidAppStorageRepository(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AppStorageRepository {
    private val appOpsManager = context.getSystemService(AppOpsManager::class.java)
    private val storageStatsManager = context.getSystemService(StorageStatsManager::class.java)
    private val packageManager = context.packageManager

    override fun usageAccessStatus(): UsageAccessStatus {
        val mode = appOpsManager.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        )
        return if (mode == AppOpsManager.MODE_ALLOWED) {
            UsageAccessStatus.GRANTED
        } else {
            UsageAccessStatus.NOT_GRANTED
        }
    }

    override suspend fun loadLaunchableAppStorage(): Result<AppStorageScanResult> = withContext(ioDispatcher) {
        try {
            check(usageAccessStatus() == UsageAccessStatus.GRANTED) {
                "Usage access is required to read app storage statistics."
            }

            val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            val launchableApps = packageManager.queryIntentActivities(
                launcherIntent,
                PackageManager.MATCH_ALL,
            ).mapNotNull { it.activityInfo?.applicationInfo }
                .distinctBy(ApplicationInfo::packageName)

            var skippedCount = 0
            val items = buildList {
                launchableApps.forEach { applicationInfo ->
                    coroutineContext.ensureActive()
                    try {
                        val stats = storageStatsManager.queryStatsForPackage(
                            StorageManager.UUID_DEFAULT,
                            applicationInfo.packageName,
                            Process.myUserHandle(),
                        )
                        add(
                            AppStorageItem(
                                packageName = applicationInfo.packageName,
                                appName = applicationInfo.loadLabel(packageManager).toString()
                                    .ifBlank { applicationInfo.packageName },
                                appBytes = stats.appBytes.coerceAtLeast(0L),
                                dataBytes = stats.dataBytes.coerceAtLeast(0L),
                                cacheBytes = stats.cacheBytes.coerceAtLeast(0L),
                            ),
                        )
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (_: PackageManager.NameNotFoundException) {
                        skippedCount += 1
                    } catch (_: SecurityException) {
                        skippedCount += 1
                    } catch (_: IOException) {
                        skippedCount += 1
                    } catch (_: IllegalArgumentException) {
                        skippedCount += 1
                    }
                }
            }
            Result.success(
                AppStorageScanResult(
                    items = items.sortedByDescending(AppStorageItem::totalBytes),
                    skippedAppCount = skippedCount,
                ),
            )
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            Result.failure(exception)
        }
    }
}
