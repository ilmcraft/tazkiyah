package com.inovixlabs.tazkiyah.ui

import kotlinx.serialization.Serializable

@Serializable data object HomeRoute
@Serializable data object SpeakersRoute
@Serializable data object CategoriesRoute
@Serializable data object LibraryRoute
@Serializable data object SearchRoute
@Serializable data object SettingsRoute
@Serializable data class SpeakerRoute(val id: String)
@Serializable data class CategoryRoute(val id: String)
