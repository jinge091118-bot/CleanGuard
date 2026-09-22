package com.cleanguard.app.data.saf

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.provider.DocumentsContract
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.core.net.toUri
import com.cleanguard.app.domain.model.DownloadAccessState
import com.cleanguard.app.domain.repository.DownloadAccessRepository
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first

private val Context.cleanGuardPreferences by preferencesDataStore(name = "cleanguard_preferences")

class SafDownloadAccessRepository(
    context: Context,
) : DownloadAccessRepository {
    private val appContext = context.applicationContext
    private val contentResolver = appContext.contentResolver
    private val dataStore = appContext.cleanGuardPreferences
    private val _accessState = MutableStateFlow(DownloadAccessState())
    override val accessState: StateFlow<DownloadAccessState> = _accessState
    private var grantedTreeUri: String? = null

    override suspend fun refreshAccess(): DownloadAccessState {
        var savedUriForState: String? = null
        val state = try {
            val savedUri = dataStore.data
                .catch { error ->
                    if (error is IOException) emit(emptyPreferences()) else throw error
                }
                .first()[TREE_URI_KEY]
            savedUriForState = savedUri
            savedUri.toAccessState()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: SecurityException) {
            DownloadAccessState(errorMessage = "已保存的下载目录授权当前不可用。")
        } catch (_: IllegalArgumentException) {
            DownloadAccessState(errorMessage = "已保存的下载目录信息无效。")
        } catch (_: IOException) {
            DownloadAccessState(errorMessage = "暂时无法读取下载目录授权。")
        }
        grantedTreeUri = savedUriForState.takeIf { state.isGranted }
        _accessState.value = state
        return state
    }

    override suspend fun persistAccess(uri: String, resultFlags: Int): Result<Unit> = try {
        val treeUri = uri.toUri()
        require(DocumentsContract.isTreeUri(treeUri)) { "Selected URI is not a document tree" }
        val readFlag = resultFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION
        require(readFlag != 0) { "Document provider did not grant read access" }
        contentResolver.takePersistableUriPermission(treeUri, readFlag)
        dataStore.edit { preferences -> preferences[TREE_URI_KEY] = treeUri.toString() }
        refreshAccess()
        Result.success(Unit)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: SecurityException) {
        _accessState.value = DownloadAccessState(errorMessage = "系统未授予可持续读取权限，请重新选择目录。")
        Result.failure(error)
    } catch (error: IllegalArgumentException) {
        _accessState.value = DownloadAccessState(errorMessage = "所选目录无法用于持续扫描，请重新选择。")
        Result.failure(error)
    } catch (error: IOException) {
        _accessState.value = DownloadAccessState(errorMessage = "保存下载目录授权失败，请重试。")
        Result.failure(error)
    }

    override suspend fun persistedTreeUri(): String? {
        refreshAccess()
        return grantedTreeUri
    }

    private fun String?.toAccessState(): DownloadAccessState {
        if (this == null) return DownloadAccessState()
        val uri = toUri()
        val hasReadAccess = contentResolver.persistedUriPermissions.any { permission ->
            permission.uri == uri && permission.isReadPermission
        }
        if (!hasReadAccess) return DownloadAccessState()
        val directoryName = DocumentsContract.getTreeDocumentId(uri)
            .substringAfterLast(':')
            .ifBlank { "已选目录" }
        return DownloadAccessState(isGranted = true, directoryName = directoryName)
    }

    private companion object {
        val TREE_URI_KEY = stringPreferencesKey("download_tree_uri")
    }
}
