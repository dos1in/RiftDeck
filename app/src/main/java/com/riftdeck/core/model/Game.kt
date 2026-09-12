package com.riftdeck.core.model

import androidx.compose.runtime.Immutable

@Immutable
data class Game(
    val id: Long,
    val platformId: Long,
    val title: String,
    val sortTitle: String,
    val romUri: String,
    val fileName: String,
    val fileSize: Long,
    val crc32: String?,
    val sha1: String?,
    val favorite: Boolean,
    val hidden: Boolean,
    val playCount: Int,
    val playTimeSeconds: Long,
    val lastPlayedAt: Long?,
    val coverUri: String?,
    val screenshotUri: String?,
    val videoUri: String?,
    val releaseYear: Int?,
    val developer: String?,
    val genre: String?,
    val coverVersion: String? = null,
    val description: String? = null,
)
