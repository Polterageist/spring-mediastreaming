package com.example.demo

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.servlet.mvc.support.RedirectAttributes

@Controller
@RequestMapping("/media/")
class MediaPlayerController(
    val mediaService: MediaService
) {
    private final val logger = LoggerFactory.getLogger(ObjectStorageConfig::class.java)

    @GetMapping
    fun mediaList(model: Model): String {
        logger.info("Getting MediaList")
        model.addAttribute("mediaList", mediaService.getMediaList())
        return "media_index"
    }

    @GetMapping("/{mediaId}")
    fun mediaPlayer(@PathVariable("mediaId") mediaId: Long,
                    request: HttpServletRequest,
                    model: Model,
                    redirectAttributes: RedirectAttributes): String {
        logger.info("Getting MediaPlayer for mediaId: $mediaId")
        try {
            var media = mediaService.getMedia(mediaId) ?: throw MediaServiceError("Media not found")

            if (!media.ready) {
                return "media_processing"
            }

            media = media.copy(
                videoTrack = media.videoTrack!!.copy(
                    streamUrl = getStreamUri(request, media.id, media.videoTrack!!.fileName).toString() + "/index.m3u8"
                ),
                audioTracks = media.audioTracks!!.map {
                    it.copy(
                        streamUrl = getStreamUri(request, media.id, it.fileName).toString() + "/index.m3u8"
                    )
                }
            )

            model.addAttribute("media", media)

            return "media_player"
        } catch (e: Exception) {
            logger.error("Error getting MediaPlayer for mediaId: $mediaId", e)
            redirectAttributes.addFlashAttribute("message", "Error getting MediaPlayer")
            return "redirect:"
        }
    }

    @GetMapping("/upload")
    fun uploadForm(): String {
        logger.info("Getting UploadForm")
        return "media_upload"
    }

    @PostMapping(
        "/",
        consumes = [MediaType.MULTIPART_FORM_DATA_VALUE]
    )
    fun uploadMedia(
        @RequestParam("mediaTitle", required = true) mediaTitle: String,
        @RequestParam("videoFile", required = true) videoFile: MultipartFile,
        @RequestParam("audioFiles", required = true) audioFiles: List<MultipartFile>,
        redirectAttributes: RedirectAttributes
    ): String {
        logger.info("Uploading media: $mediaTitle")
        val media = mediaService.createMedia(mediaTitle, videoFile, audioFiles)

        return "redirect:/media/${media.id}"
    }

    @PostMapping("/{mediaId}/delete")
    fun deleteMedia(
        @PathVariable("mediaId") mediaId: Long,
        redirectAttributes: RedirectAttributes
    ): String {
        logger.info("Deleting media: $mediaId")
        mediaService.deleteMedia(mediaId)
        redirectAttributes.addFlashAttribute("message", "Media deleted")
        return "redirect:/media/"
    }
}