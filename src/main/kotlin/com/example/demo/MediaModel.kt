package com.example.demo

import org.springframework.data.annotation.Id
import org.springframework.data.redis.core.RedisHash


@JvmRecord
data class MediaTrackModel(
    val name: String,
    val fileName: String,
    val mediaType: String, // video or audio
    val streamUrl: String? = null,
)

@JvmRecord
@RedisHash
data class MediaModel(
    @Id
    val id: Long,
    val name: String,
    val ready: Boolean,
    val videoTrack: MediaTrackModel? = null,
    val audioTracks: List<MediaTrackModel>? = null,
)