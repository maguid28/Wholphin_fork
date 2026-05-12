package com.github.damontecres.wholphin.ui.detail

import androidx.annotation.StringRes
import com.github.damontecres.wholphin.R

enum class LibraryTopTab(
    val id: String,
    @param:StringRes val titleRes: Int,
) {
    RECOMMENDED("recommended", R.string.recommended),
    LIBRARY("library", R.string.library),
    COLLECTIONS("collections", R.string.collections),
    GENRES("genres", R.string.genres),
    STUDIOS("studios", R.string.studios),
    NETWORKS("networks", R.string.networks),
}

val DefaultMovieLibraryTabs =
    listOf(
        LibraryTopTab.RECOMMENDED,
        LibraryTopTab.LIBRARY,
        LibraryTopTab.COLLECTIONS,
        LibraryTopTab.GENRES,
        LibraryTopTab.STUDIOS,
        LibraryTopTab.NETWORKS,
    )

val DefaultTvLibraryTabs =
    listOf(
        LibraryTopTab.RECOMMENDED,
        LibraryTopTab.LIBRARY,
        LibraryTopTab.GENRES,
        LibraryTopTab.STUDIOS,
        LibraryTopTab.NETWORKS,
    )

fun orderedLibraryTabs(
    defaultTabs: List<LibraryTopTab>,
    savedOrder: List<String>,
): List<LibraryTopTab> {
    if (savedOrder.isEmpty()) return defaultTabs

    val byId = defaultTabs.associateBy { it.id }
    val orderedSaved = savedOrder.mapNotNull(byId::get)
    val missing = defaultTabs.filterNot { tab -> orderedSaved.any { it.id == tab.id } }
    return orderedSaved + missing
}

fun List<LibraryTopTab>.moveTab(
    fromIndex: Int,
    toIndex: Int,
): List<LibraryTopTab> {
    if (fromIndex !in indices || toIndex !in indices || fromIndex == toIndex) return this

    return toMutableList().apply {
        add(toIndex, removeAt(fromIndex))
    }
}
