package com.github.damontecres.wholphin.services

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.damontecres.wholphin.R
import com.github.damontecres.wholphin.data.ServerRepository
import com.github.damontecres.wholphin.data.model.BaseItem
import com.github.damontecres.wholphin.preferences.AppPreferences
import com.github.damontecres.wholphin.ui.launchIO
import com.github.damontecres.wholphin.ui.showToast
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.exception.InvalidStatusException
import org.jellyfin.sdk.api.client.extensions.libraryApi
import org.jellyfin.sdk.api.client.extensions.userLibraryApi
import org.jellyfin.sdk.model.api.BaseItemKind
import timber.log.Timber
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val metadataManagedItemTypes =
    setOf(BaseItemKind.MOVIE, BaseItemKind.SERIES)

/**
 * Service to manage media such as deletions
 */
@Singleton
class MediaManagementService
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val api: ApiClient,
        private val serverRepository: ServerRepository,
        private val userPreferencesService: UserPreferencesService,
    ) {
        private val _deletedItemFlow =
            MutableSharedFlow<DeletedItem>(
                replay = 1,
                extraBufferCapacity = 0,
                onBufferOverflow = BufferOverflow.DROP_OLDEST,
            )

        /**
         * Listen for recently deleted items. Useful for ViewModels to react and refresh data
         */
        val deletedItemFlow: SharedFlow<DeletedItem> = _deletedItemFlow

        suspend fun canDelete(item: BaseItem): Boolean {
            val appPreferences = userPreferencesService.getCurrent().appPreferences
            return canDelete(item, appPreferences, isCurrentUserAdministrator())
        }

        /**
         * Check if the item can be deleted. This means the app setting is enabled and the user has permission.
         */
        fun canDelete(
            item: BaseItem,
            appPreferences: AppPreferences,
            isAdministrator: Boolean = false,
        ): Boolean {
            val canUseMediaManagement =
                isAdministrator || appPreferences.interfacePreferences.enableMediaManagement
            if (!canUseMediaManagement) {
                return false
            }
            if (item.type == BaseItemKind.RECORDING) {
                if (item.data.canDelete == false) {
                    return false
                }
                return item.canDelete &&
                    serverRepository.currentUserDto.value
                        ?.policy
                        ?.enableLiveTvManagement == true
            }
            if (isAdministrator && item.type in metadataManagedItemTypes) {
                return true
            }
            if (item.data.canDelete == false) {
                return false
            }
            if (item.canDelete) {
                return true
            }
            // Grid rows may omit CanDelete; allow the action and verify again before deleting.
            return item.data.canDelete == null && item.type in metadataManagedItemTypes
        }

        private fun isCurrentUserAdministrator(): Boolean =
            serverRepository.currentUserDto.value?.policy?.isAdministrator == true

        /**
         * Delete the item.
         *
         * This item will be sent through [deletedItemFlow] for other services or view models to react.
         */
        suspend fun deleteItem(item: BaseItem): DeleteResult {
            try {
                syncAccessToken()
                val freshItem = fetchItemForDelete(item.id)
                val appPreferences = userPreferencesService.getCurrent().appPreferences
                if (freshItem.data.canDelete != true) {
                    Timber.w(
                        "Refusing to delete %s (%s): server canDelete=%s",
                        freshItem.id,
                        freshItem.title,
                        freshItem.data.canDelete,
                    )
                    return DeleteResult.Error(
                        IllegalStateException("This item cannot be deleted"),
                    )
                }
                if (!canDelete(freshItem, appPreferences, isCurrentUserAdministrator())) {
                    Timber.w(
                        "Refusing to delete %s (%s): app settings or user policy",
                        freshItem.id,
                        freshItem.title,
                    )
                    return DeleteResult.Error(
                        IllegalStateException("This item cannot be deleted"),
                    )
                }
                Timber.i(
                    "Deleting %s (%s, type=%s, path=%s, server=%s)",
                    freshItem.id,
                    freshItem.title,
                    freshItem.type,
                    freshItem.data.path,
                    serverRepository.currentServer.value?.version,
                )
                invokeDelete(freshItem.id)
                _deletedItemFlow.emit(DeletedItem(freshItem))
                return DeleteResult.Success
            } catch (ex: Exception) {
                Timber.e(
                    ex,
                    "Error deleting %s (%s, type=%s, path=%s, server=%s)",
                    item.id,
                    item.title,
                    item.type,
                    item.data.path,
                    serverRepository.currentServer.value?.version,
                )
                return DeleteResult.Error(ex)
            }
        }

        private fun syncAccessToken() {
            val token = serverRepository.currentUser.value?.accessToken ?: return
            if (api.accessToken != token) {
                Timber.w("Syncing Jellyfin access token before delete")
                api.update(accessToken = token)
            }
        }

        private suspend fun fetchItemForDelete(itemId: UUID): BaseItem {
            val userId = serverRepository.currentUser.value?.id
            val dto =
                if (userId != null) {
                    api.userLibraryApi.getItem(itemId, userId).content
                } else {
                    api.userLibraryApi.getItem(itemId).content
                }
            return BaseItem(dto)
        }

        private suspend fun invokeDelete(itemId: UUID) {
            try {
                api.libraryApi.deleteItem(itemId)
            } catch (ex: InvalidStatusException) {
                if (ex.status == 500) {
                    Timber.w("deleteItem returned 500 for %s, retrying with deleteItems", itemId)
                    api.libraryApi.deleteItems(listOf(itemId))
                } else {
                    throw ex
                }
            }
        }
    }

data class DeletedItem(
    val item: BaseItem,
)

sealed interface DeleteResult {
    data object Success : DeleteResult

    data class Error(
        val ex: Exception,
    ) : DeleteResult
}

/**
 * Convenience function to delete an item and show a Toast based on success or error
 */
fun ViewModel.deleteItem(
    context: Context,
    mediaManagementService: MediaManagementService,
    item: BaseItem,
    onSuccess: () -> Unit = {},
) = viewModelScope.launchIO {
    when (val r = mediaManagementService.deleteItem(item)) {
        is DeleteResult.Error -> {
            val message =
                when {
                    r.ex is IllegalStateException ->
                        context.getString(R.string.delete_not_allowed)

                    r.ex is InvalidStatusException && r.ex.status >= 500 ->
                        context.getString(R.string.delete_server_error)

                    else ->
                        context.getString(
                            R.string.delete_error,
                            r.ex.localizedMessage ?: r.ex.message ?: "Unknown",
                        )
                }
            showToast(context, message)
        }

        DeleteResult.Success -> {
            showToast(context, "Deleted")
            onSuccess.invoke()
        }
    }
}
