package com.example.demo

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Configurable
import org.springframework.beans.factory.annotation.Value
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader


data class ProcessedMediaFile(val contentType: String, val index: File, val chunks: List<File>)


class MediaEncoderException(message: String) : Exception(message)

@Configurable
open class MediaTrackEncoderTask(private val sourceFile: File, private val targetPath: File) {

    @Value("\${encoder.ffmpeg-path}")
    var ffmpegPath: String = ""

    private var ffmpegRoot: File? = null

    private val logger = LoggerFactory.getLogger(MediaTrackEncoderTask::class.java)

    open fun execute(): ProcessedMediaFile {
        ffmpegRoot = findFfmpeg()
        if (ffmpegRoot == null) {
            throw MediaEncoderException("FFmpeg not found")
        }

        if (!sourceFile.exists()) {
            throw MediaEncoderException("Source file not found: ${sourceFile.absolutePath}")
        }

        try {
            if (!targetPath.exists()) {
                targetPath.mkdirs()
            }

            val contentTypeOutput = StringBuilder()
            val contentTypeExitCode = runProcess(ffProbeContentTypeCommand(sourceFile), contentTypeOutput)
            if (contentTypeExitCode != 0) {
                throw MediaEncoderException("FFprobe process failed with exit code $contentTypeExitCode")
            }

            val mediaType = contentTypeOutput.toString().trim()
            logger.info("Content type: $mediaType")

            var ffmpegCommand: List<String> = emptyList()
            when (mediaType) {
                "video" -> {
                    ffmpegCommand = ffmpegHlsCommand(sourceFile)
                }
                "audio" -> {
                    ffmpegCommand = ffmpegSegmentCommand(sourceFile)
                }
                else -> {
                    throw MediaEncoderException("Unsupported media type: $mediaType")
                }
            }

            val exitCode = runProcess(ffmpegCommand, null)
            if (exitCode != 0) {
                logger.error("FFmpeg process exited with code $exitCode")
                throw MediaEncoderException("FFmpeg process failed with exit code $exitCode")
            }

            val indexFile = File(targetPath, "index.m3u8")
            if (!indexFile.exists()) {
                throw MediaEncoderException("Target file not found: ${indexFile.absolutePath}")
            }

            val chunkFiles = targetPath.listFiles { _, name -> name.startsWith("segment-") }?.toList() ?: emptyList()
            if (chunkFiles.isEmpty()) {
                throw MediaEncoderException("No chunk files found in target directory")
            }

            var contentType = "application/vnd.apple.mpegurl"
            if (mediaType == "video") {
                contentType = "video/vnd.apple.mpegurl"
            } else if (mediaType == "audio") {
                contentType = "audio/vnd.apple.mpegurl"
            }
            return ProcessedMediaFile(contentType, indexFile, chunkFiles)
        } catch (e: Exception) {
            throw MediaEncoderException("Error processing media file: ${e.message}")
        }
    }

    private fun runProcess(command: List<String>, output: StringBuilder?): Int {
        val processBuilder = ProcessBuilder(command)
        processBuilder.directory(targetPath)
        processBuilder.redirectErrorStream(true)
        val process = processBuilder.start()

        BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
            reader.lines().forEach { line ->
                logger.info(line)
                output?.append(line)?.append("\n")
            }
        }

        return process.waitFor()
    }

    private fun ffProbeContentTypeCommand(source: File): List<String> {
        return listOf(
            ffmpegRoot!!.absolutePath + "/ffprobe",
            "-v", "error",
            "-show_entries", "stream=codec_type",
            "-of", "csv=p=0",
            source.absolutePath
        )
    }

    private fun ffmpegHlsCommand(source: File): List<String> {
        return listOf(
            ffmpegRoot!!.absolutePath + "/ffmpeg",
            "-i", source.absolutePath,
            "-c:v", "h264",
            "-hls_time", "10",
            "-hls_list_size", "0",
            "-hls_segment_filename", "segment-%03d.ts",
            "index.m3u8"
        )
    }

    private fun ffmpegSegmentCommand(source: File): List<String> {
        return listOf(
            ffmpegRoot!!.absolutePath + "/ffmpeg",
            "-i", source.absolutePath,
            "-f", "segment",
            "-ar", "44100",
            "-c:a", "aac",
            "-b:a", "128k",
            "-segment_time", "5",
            "-segment_list", "index.m3u8",
            "-segment_format", "aac", "segment-%03d.aac",
            "-segment_time_delta", "0"
        )
    }

    private fun findFfmpeg(): File? {
        val isWindows = System.getProperty("os.name").lowercase().contains("win")

        val pathsToTest = listOf(
            ffmpegPath,
            System.getenv("FFMPEG_PATH")
        ) + System.getenv("PATH").split(if (isWindows) ";" else ":").map { it.trim() }

        return pathsToTest.firstNotNullOfOrNull {
            val file = File(it, "ffmpeg" + if (isWindows) ".exe" else "")
            if (file.exists()) {
                file.parentFile
            } else {
                null
            }
        }
    }
}