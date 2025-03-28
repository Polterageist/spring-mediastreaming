package com.example.demo

import io.minio.*
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody
import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.net.URI

class MediaServiceError(message: String, cause: Throwable? = null) : Exception(message, cause)


fun getStreamUri(request: HttpServletRequest, mediaId: Long, fileName: String): URI {
    return URI(
        request.scheme,null, request.serverName, request.serverPort,
        "/api/stream/$mediaId/$fileName", null, null
    )
}

@Service
@Configuration
class MediaService(
    val taskExecutor: ThreadPoolTaskExecutor,
    val mediaRepository: MediaRepository,
    val minioClient: MinioClient,
    val objectStorageConfig: ObjectStorageConfig
) {
    private val logger = LoggerFactory.getLogger(MediaService::class.java)

    fun getMediaList(): List<MediaModel> {
        return mediaRepository.findAll().toList()
    }

    fun createMedia(name: String,
                    video: MultipartFile,
                    audio: List<MultipartFile>): MediaModel {
        logger.info("Creating media: $name")

        var mediaId = mediaRepository.count() + 1
        var workDir: File? = null

        try {
            val videoTrack = MediaTrackModel(
                video.originalFilename!!,
                "video",
                "video"
            )

            val audioTracks = audio.mapIndexed { index, audioTrack ->
                MediaTrackModel(
                    audioTrack.originalFilename!!,
                    "audio-$index",
                    "audio"
                )
            }

            val media = mediaRepository.save(MediaModel(mediaId, name, false, videoTrack, audioTracks))

            workDir = File("${System.getProperty("java.io.tmpdir")}/media-processing/${media.id}")
            if (workDir.exists()) {
                logger.error("Work directory for new media already exists: ${workDir.canonicalPath}")
                workDir.deleteRecursively()
            }
            workDir.mkdirs()

            val tracks = listOf(videoTrack) + audioTracks
            val sourceFiles: List<File> = (listOf(video) + audio).mapIndexed { index, it ->
                File.createTempFile(tracks[index].fileName, ".tmp", workDir).also { file ->
                    file.outputStream().use { output -> it.inputStream.copyTo(output) }
                }
            }

            taskExecutor.execute {
                try {
                    processMedia(media, workDir, tracks, sourceFiles)
                } catch (e: Exception) {
                    logger.error("Error processing media: ${media.id}", e)
                    mediaRepository.deleteById(media.id)
                } finally {
                    workDir.deleteRecursively()
                }
            }

            return media
        } catch (e: Exception) {
            logger.error("Error creating media: $name", e)
            mediaRepository.deleteById(mediaId)
            workDir?.deleteRecursively()

            throw MediaServiceError("Error occurred during creating media: $name", e)
        }
    }

    fun getMedia(mediaId: Long): MediaModel? {
        return mediaRepository.findById(mediaId).get()
    }

    fun deleteMedia(mediaId: Long) {
        logger.info("Deleting media: $mediaId")
        mediaRepository.deleteById(mediaId)
        deleteFromMinio(mediaId)
    }

    fun getIndexStream(request: HttpServletRequest, mediaId: Long, fileName: String): StreamingResponseBody {
        logger.info("Getting index stream for mediaId: $mediaId, fileName: $fileName")
        val objectName = getMediaObjectPrefix(mediaId, fileName) + "index.m3u8"
        val tsPrefix = getStreamUri(request, mediaId, fileName)

        val objectStream = getObjectStream(objectName)

        if (objectStream == null) {
            logger.error("Index file not found: $mediaId/$fileName/index.m3u8")
            throw MediaServiceError("Index file not found: $mediaId/$fileName")
        }

        val reader = BufferedReader(InputStreamReader(objectStream))

        return StreamingResponseBody { outputStream ->
            try {
                reader.forEachLine { line ->
                    if (line.endsWith(".ts"))
                        outputStream.write("$tsPrefix/$line".toByteArray())
                    else
                        outputStream.write(line.toByteArray())
                    outputStream.write(System.lineSeparator().toByteArray())
                }
                outputStream.flush()
            } catch (e: Exception) {
                logger.error("Error streaming media: $mediaId/$fileName", e)
                throw MediaServiceError("Error streaming media: $fileName", e)
            } finally {
                reader.close()
                outputStream.close()
            }
        }
    }

    fun getSegmentStream(mediaId: Long, fileName: String, segment: String): StreamingResponseBody? {
        try {
            val objectName = getMediaObjectPrefix(mediaId, fileName) + segment
            val objectStream = getObjectStream(objectName)

            if (objectStream == null) {
                logger.error("Segment not found: $mediaId/$fileName/$segment")
                throw MediaServiceError("Segment not found: $mediaId/$fileName/$segment")
            }

            return StreamingResponseBody {
                    outputStream ->
                try {
                    val buffer = ByteArray(1024)
                    var bytesRead: Int
                    while (objectStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                    }
                    outputStream.flush()
                } catch (e: Exception) {
                    logger.error("Error streaming media: $mediaId/$fileName/$segment", e)
                } finally {
                    objectStream.close()
                    outputStream.close()
                }
            }
        } catch (e: Exception) {
            logger.error("Error streaming media: $mediaId/$fileName/$segment", e)
            return null
        }
    }

    private fun getObjectStream(objectName: String): InputStream? {
        val files = minioClient.listObjects(
            ListObjectsArgs.builder()
                .bucket(objectStorageConfig.streamBucket)
                .prefix(objectName)
                .build()
        )
        val file = files.firstOrNull() ?: return null

        return minioClient.getObject(
            GetObjectArgs.builder()
                .bucket(objectStorageConfig.streamBucket)
                .`object`(file.get().objectName())
                .build()
        )
    }

    private fun getMediaObjectPrefix(mediaId: Long, fileName: String? = null): String {
        return "media/$mediaId/" + if (fileName != null) "$fileName/" else ""
    }

    private fun cleanFileName(fileName: String): String {
        val file = File(fileName)
        return file.nameWithoutExtension
            .lowercase()
            .trim() + file.extension
    }

    private fun processMedia(
        media: MediaModel,
        workDir: File,
        tracks: List<MediaTrackModel>,
        sourceFiles: List<File>
    ) {
        val processedTracks = sourceFiles.map { file ->
            MediaTrackEncoderTask(file, File("${workDir.absolutePath}/${file.nameWithoutExtension}_processed"))
                .execute()
        }

        processedTracks.forEachIndexed { index, track ->
            uploadToMinio(media.id, tracks[index].fileName, track)
        }

        mediaRepository.save(media.copy(
            ready = true,
        ))

        logger.info("Processed media: ${media.id}")
    }

    private fun uploadToMinio(
        mediaId: Long,
        fileName: String,
        track: ProcessedMediaFile,
    ) {
        val prefix = getMediaObjectPrefix(mediaId, fileName)
        logger.info("Uploading media track: $prefix")

        val files = listOf(track.index) + track.chunks
        files.forEach {
            val objectName = "$prefix${it.name}"
            logger.info("Uploading:  ${objectStorageConfig.streamBucket}:$objectName")
            minioClient.putObject(
                PutObjectArgs.builder()
                    .bucket(objectStorageConfig.streamBucket)
                    .`object`(objectName)
                    .contentType(track.contentType)
                    .stream(it.inputStream(), -1, 10485760)
                    .build()
            )
        }

        logger.info("Uploaded media track: $prefix")
    }

    private fun deleteFromMinio(mediaId: Long, fileName: String? = null) {
        val prefix = getMediaObjectPrefix(mediaId, fileName)
        logger.info("Deleting media objects: $prefix")
        val files = minioClient.listObjects(
            ListObjectsArgs.builder()
                .bucket(objectStorageConfig.streamBucket)
                .prefix(prefix)
                .build()
        )
        files.forEach {
            minioClient.removeObject(
                RemoveObjectArgs.builder()
                    .bucket(objectStorageConfig.streamBucket)
                    .`object`(it.get().objectName())
                    .build()
            )
        }

        logger.info("Deleted media objects: $prefix")
    }
}