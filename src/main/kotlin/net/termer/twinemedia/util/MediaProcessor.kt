package net.termer.twinemedia.util

import io.vertx.kotlin.core.executeBlockingAwait
import net.bramp.ffmpeg.FFmpeg
import net.bramp.ffmpeg.FFmpegExecutor
import net.bramp.ffmpeg.FFprobe
import net.bramp.ffmpeg.builder.FFmpegBuilder
import net.bramp.ffmpeg.probe.FFmpegProbeResult
import net.termer.twine.ServerManager.vertx
import net.termer.twinemedia.Module.Companion.config
import kotlin.random.Random

private val ffmpeg = FFmpeg(config.ffmpeg_path)
private val ffprobe = FFprobe(config.ffprobe_path)
private val executor = FFmpegExecutor(ffmpeg, ffprobe)

/**
 * Returns info about a media file
 * @param filePath The file to probe with FFprobe
 * @return The FFmpegProbeResult object for the provided media file
 * @since 1.0
 */
suspend fun probeFile(filePath : String) : FFmpegProbeResult? {
    return vertx().executeBlockingAwait<FFmpegProbeResult> {
        try {
            val probe = ffprobe.probe(filePath)

            if(probe.hasError()) {
                it.fail(Exception(probe.error.string))
            } else {
                it.complete(probe)
            }
        } catch(e : Exception) {
            it.fail(e)
        }
    }
}

/**
 * Creates a thumbnail from a video file
 * @param filePath The video file
 * @param duration The duration of the video in seconds
 * @param outPath The file to output thumbnail file
 * @since 1.0
 */
suspend fun createVideoThumbnail(filePath : String, duration : Int, outPath : String) {
    vertx().executeBlockingAwait<Unit> {
        try {
            // Get thumbnail snapshot time
            val thumbTime = ((duration / 2) + Random.nextInt(-5, 5)).coerceIn(0, duration)

            // Generate thumbnail
            val builder = FFmpegBuilder()
                    .setInput(filePath)
                    .setAudioFilter("scale=-1:360")
                    .addOutput(outPath)
                    .addExtraArgs("-vframes", "1", "-an", "-ss", thumbTime.toString())
                    .done()
            executor.createJob(builder).run()
            it.complete()
        } catch(e : Exception) {
            it.fail(e)
        }
    }
}

/**
 * Creates a preview image from an image file
 * @param filePath The image file to create the preview from
 * @param outPath The output file for the preview image
 * @since 1.0
 */
suspend fun createImagePreview(filePath : String, outPath : String) {
    vertx().executeBlockingAwait<Unit> {
        try {
            val builder = FFmpegBuilder()
                    .setInput(filePath)
                    .setAudioFilter("scale=-1:360")
                    .addOutput(outPath)
                    .done()
            executor.createJob(builder).run()
            it.complete()
        } catch(e : Exception) {
            it.fail(e)
        }
    }
}