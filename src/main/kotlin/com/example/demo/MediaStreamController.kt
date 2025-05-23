package com.example.demo

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody


@RestController
@RequestMapping("/api/stream")
class MediaStreamController(val mediaService: MediaService) {

    private val logger = LoggerFactory.getLogger(MediaStreamController::class.java)

    @RequestMapping(
        path = ["/{mediaId}/{fileName}/index.m3u8"],
        method = [RequestMethod.GET],
        produces = [MediaType.APPLICATION_OCTET_STREAM_VALUE]
    )
    fun getIndex(@PathVariable(value = "mediaId", required = true) mediaId: Long,
              @PathVariable(value = "fileName", required = true) fileName: String,
                 request: HttpServletRequest): ResponseEntity<StreamingResponseBody> {
        logger.info("Getting index mediaId: $mediaId, fileName: $fileName")
        try {
            val stream = mediaService.getIndexStream(request, mediaId, fileName)

            val headers = HttpHeaders()
            headers.set(HttpHeaders.CONTENT_TYPE, "application/vnd.apple.mpegurl") // Correct MIME type for audio
            headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=index.m3u8")

            return ResponseEntity(stream, headers, HttpStatus.OK)
        } catch (e: Exception) {
            logger.error("Failed to get index file", e)
            return ResponseEntity.internalServerError().build()
        }
    }

    @RequestMapping(
        path = ["/{mediaId}/{fileName}/{segment}"],
        method = [RequestMethod.GET],
        produces = [MediaType.APPLICATION_OCTET_STREAM_VALUE]
    )
    fun getSegment(@PathVariable(value = "mediaId", required = true) mediaId: Long,
              @PathVariable(value = "fileName", required = true) fileName: String,
              @PathVariable(value = "segment", required = true) segment: String): ResponseEntity<StreamingResponseBody> {
        logger.info("Getting segment: $segment")
        try {
            val stream = mediaService.getSegmentStream(mediaId, fileName, segment)

            val headers = HttpHeaders()
            val content_type = if (segment.endsWith(".ts")) "video/vnd.apple.mpegurl" else "audio/vnd.apple.mpegurl"
            headers.set(HttpHeaders.CONTENT_TYPE, content_type) // Correct MIME type for audio
            headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=$segment")

            return ResponseEntity(stream, headers, HttpStatus.OK)
        } catch (e: Exception) {
            logger.error("Failed to get segment file", e)
            return ResponseEntity.internalServerError().build()
        }
    }
}