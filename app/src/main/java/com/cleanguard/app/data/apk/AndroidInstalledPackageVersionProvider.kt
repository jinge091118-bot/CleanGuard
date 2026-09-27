package com.cleanguard.app.data.apk

import android.content.pm.PackageManager
import android.os.Build
import com.cleanguard.app.domain.apk.InstalledPackageLookup
import com.cleanguard.app.domain.apk.InstalledPackageVersionProvider

class AndroidInstalledPackageVersionProvider(
    private val packageManager: PackageManager,
) : InstalledPackageVersionProvider {
    override fun lookup(packageName: String): InstalledPackageLookup = try {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getPackageInfo(packageName, 0)
        }
        InstalledPackageLookup.Installed(info.longVersionCode)
    } catch (_: PackageManager.NameNotFoundException) {
        // Android 11+ intentionally makes "not installed" indistinguishable from "not visible"
        // for packages outside the app's allowed visibility. Conservatively report UNKNOWN.
        InstalledPackageLookup.Unknown
    } catch (_: SecurityException) {
        InstalledPackageLookup.Unknown
    }
}
