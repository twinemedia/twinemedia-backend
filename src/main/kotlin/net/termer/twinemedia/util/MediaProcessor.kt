package net.termer.twinemedia.util

import io.vertx.core.AsyncResult
import io.vertx.core.Future.failedFuture
import io.vertx.core.Future.succeededFuture
import io.vertx.core.Handler
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.core.executeBlockingAwait
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.bramp.ffmpeg.FFmpeg
import net.bramp.ffmpeg.FFmpegExecutor
import net.bramp.ffmpeg.FFprobe
import net.bramp.ffmpeg.builder.FFmpegBuilder
import net.bramp.ffmpeg.probe.FFmpegProbeResult
import net.bramp.ffmpeg.progress.ProgressListener
import net.termer.twine.ServerManager.vertx
import net.termer.twine.utils.StringFilter.generateString
import net.termer.twinemedia.Module.Companion.config
import net.termer.twinemedia.Module.Companion.logger
import net.termer.twinemedia.model.updateMediaInfo
import net.termer.twinemedia.model.updateMediaProcessError
import sun.misc.BASE64Encoder
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.random.Random

private val ffmpeg = FFmpeg(config.ffmpeg_path)
private val ffprobe = FFprobe(config.ffprobe_path)
private val executor = FFmpegExecutor(ffmpeg, ffprobe)

private val mediaQueue = CopyOnWriteArrayList<MediaProcessorJob>()

/**
 * Starts a media processor instance
 * @since 1.0
 */
fun startMediaProcessor() : Thread {
    return thread(start = true) {
        // Infinite processing loop
        loop@ while(true) {
            // Sleep for 1 second to avoid checking queue too frequently
            Thread.sleep(1000)

            // Check if queue has any jobs
            val queueSize = mediaQueue.size
            if(queueSize > 0) {
                // Get job and remove it from queue
                val job = mediaQueue[0]
                mediaQueue.removeAt(0)

                // Collect job data
                val id = job.id
                val duration = job.duration
                val type = job.type
                val source = job.source
                val out = job.out
                val settings = job.settings

                // Setup ffmpeg job
                val builder = FFmpegBuilder()
                        .setInput(source)
                        .addOutput(out)
                if(type == MediaProcessorJobType.VIDEO) {
                    // Job is for video, set video bitrate as well as audio
                    val videoBitrate = settings.getLong("video_bitrate").coerceIn(-1, 51200)
                    val audioBitrate = settings.getLong("audio_bitrate").coerceIn(-1, 51200)

                    if(videoBitrate > -1)
                        builder.video_bit_rate = videoBitrate*1024
                    if(audioBitrate > -1)
                        builder.audio_bit_rate = audioBitrate*1024

                    // If dimensions are specified, apply them
                    if(settings.containsKey("width") && settings.containsKey("height")) {
                        val width = settings.getInteger("width").coerceIn(-1, 7680).toString()
                        val height = settings.getInteger("height").coerceIn(-1, 4320).toString()

                        // Apply video filter
                        builder.video_filter = "scale=$width:$height"
                    }
                } else if(type == MediaProcessorJobType.AUDIO) {
                    val audioBitrate = settings.getLong("audio_bitrate").coerceIn(-1, 51200)

                    if(audioBitrate > -1)
                        builder.audio_bit_rate = audioBitrate*1024
                }


                // Run job
                try {
                    val ffmpegJob = executor.createJob(builder.done(), ProgressListener { prog ->
                        // Broadcast progress percentage
                        val percent = ((prog.out_time_ns / (duration * TimeUnit.SECONDS.toNanos(1))) * 100).toInt()

                        vertx().eventBus().publish("twinemedia.progress.$id", json {
                            obj(
                                    "status" to "progress",
                                    "percent" to percent
                            )
                        })
                    }).run()
                } catch(e : Exception) {
                    logger.error("Failed to encode media file:")
                    e.printStackTrace()

                    // Broadcast encoding error
                    vertx().eventBus().publish("twinemedia.process.$id", json {
                        obj(
                                "status" to "error",
                                "error" to "Encoding error"
                        )
                    })

                    // Attempt to record processing error
                    try {
                        runBlocking(vertx().dispatcher()) {
                            updateMediaProcessError(id, "Processing failed due to encoding error")
                        }
                    } catch(e : Exception) {
                        logger.error("Failed to update media file's process error field:")
                        e.printStackTrace()

                        // Nothing can be done at this point
                    }

                    // Run callback
                    if(job.callback != null)
                        GlobalScope.launch(vertx().dispatcher()) {
                            job.callback?.handle(failedFuture(e))
                        }

                    // Return to normal loop operation
                    continue@loop
                }

                vertx().eventBus().publish("twinemedia.progress.$id", json {
                    obj("status" to "processing")
                })

                // Fetch info about new file
                val fileSize = File(out).length()

                // Hash file
                val buffer = ByteArray(8192)
                var count : Int
                val digest = MessageDigest.getInstance("SHA-256")
                val bis = BufferedInputStream(FileInputStream(out))
                while (bis.read(buffer).also { count = it } > 0) {
                    digest.update(buffer, 0, count)
                }
                bis.close()
                val hash = BASE64Encoder().encode(digest.digest())

                // Try to generate thumbnail
                var thumbnail : String? = null
                try {
                    val thumbId = generateString(10)

                    // Generate video thumbnail or audio thumbnail
                    runBlocking(vertx().dispatcher()) {
                        if (type == MediaProcessorJobType.VIDEO) {
                            createVideoThumbnail(out, duration, "${config.upload_location}thumbnails/$thumbId.jpg")
                        } else if(type == MediaProcessorJobType.AUDIO) {
                            createImagePreview(out, "${config.upload_location}thumbnails/$thumbId.jpg")
                        }
                    }

                    // Set thumbnail file
                    thumbnail = "$thumbId.jpg"
                } catch(e : Exception) {
                    // Failed to generate thumbnail
                }

                // Probe file for metadata
                val meta = ffprobeToJsonMeta(ffprobe.probe(out))

                try {
                    // Update media info
                    runBlocking(vertx().dispatcher()) {
                        updateMediaInfo(id, false, fileSize, hash, thumbnail, meta)
                    }

                    // Broadcast success
                    vertx().eventBus().publish("twinemedia.process.$id", json {
                        obj("status" to "success")
                    })

                    // Run callback
                    if(job.callback != null)
                        job.callback?.handle(succeededFuture())

                } catch(e : Exception) {
                    logger.error("Failed to update media info after processing:")
                    e.printStackTrace()

                    // Broadcast processing error
                    vertx().eventBus().publish("twinemedia.process.$id", json {
                        obj(
                                "status" to "error",
                                "error" to "Database error"
                        )
                    })

                    // Attempt to record processing error
                    try {
                        runBlocking(vertx().dispatcher()) {
                            updateMediaProcessError(id, "Processing failed due to database error")
                        }
                    } catch(e : Exception) {
                        logger.error("Failed to update media file's process error field:")
                        e.printStackTrace()

                        // Nothing can be done at this point
                    }

                    // Run callback
                    if(job.callback != null)
                        GlobalScope.launch(vertx().dispatcher()) {
                            job.callback?.handle(failedFuture(e))
                        }
                }
            }
        }
    }
}

/**
 * Types of media processing jobs
 * @since 1.0
 */
enum class MediaProcessorJobType {
    VIDEO,
    AUDIO
}

/**
 * Interface to define media processor jobs
 * @since 1.0
 */
interface MediaProcessorJob {
    /**
     * The alphanumeric ID for the media entry this job is for
     * @since 1.0
     */
    val id : String
    /**
     * The path to the source file
     * @since 1.0
     */
    val source : String
    /**
     * The path to write the new file
     * @since 1.0
     */
    val out : String
    /**
     * The duration of the file in seconds
     * @since 1.0
     */
    val duration : Int
    /**
     * The type of job
     * @since 1.0
     */
    val type : MediaProcessorJobType
    /**
     * The settings to use when encoding the file
     * @since 1.0
     */
    val settings : JsonObject
    /**
     * The callback to execute when this job has succeeded or failed
     * @since 1.0
     */
    val callback : Handler<AsyncResult<Unit>>?
}

/**
 * Queues a media processing job.
 * JSON parameters:
 * id: String, the alphanumeric ID for the media entry this job is for
 * source: String, the path to the source file
 * out: String, the path to write the new file
 * duration: Integer, the duration of the file in seconds
 * type: String, the type of job ("video" or "audio")
 * settings: JSON object, the settings to use when encoding the file
 *
 * JSON parameters for the settings object:
 * audio_bitrate: Long, the audio bitrate for the output in kB/s
 * video_bitrate: Long, the video bitrate for the output in kB/s
 * width (optional, for video only): Integer, the width of the output video (use -1 to keep aspect ratio) (max. value is 7680)
 * height (optional, for video only): Integer, the height of the output video (use -1 to keep aspect ratio) (max. value is 4320)
 * @param job The an object implementing MediaProcessorJob containing the job's info
 * @since 1.0
 */
fun queueMediaProcessJob(job : MediaProcessorJob) {
    mediaQueue.add(job)
}

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

/**
 * Creates a JSON object containing most key probe info
 * @param probe The probe result to extract data from
 * @since 1.0
 */
fun ffprobeToJsonMeta(probe : FFmpegProbeResult) : JsonObject {
    val format = probe.format
    val meta = JsonObject()

    // Collect metadata from probe
    meta
            .put("bitrate", format.bit_rate)
            .put("duration", format.duration)
            .put("format_name", format.format_name)
            .put("format_long_name", format.format_long_name)
            .put("stream_count", format.nb_streams)
            .put("start_time", format.start_time)
    val streams = JsonArray()
    for(stream in probe.streams) {
        val strm = JsonObject()
        strm
                .put("codec_name", stream.codec_name)
                .put("codec_long_name", stream.codec_long_name)
                .put("average_framerate", stream.avg_frame_rate.toProperString())
                .put("bitrate", stream.bit_rate)
                .put("channel_count", stream.channels)
                .put("aspect_ratio", stream.display_aspect_ratio)
                .put("duration", stream.duration)
                .put("width", stream.width)
                .put("height", stream.height)
                .put("max_bitrate", stream.max_bit_rate)
                .put("frame_count", stream.nb_frames)
        streams.add(strm)
    }
    meta.put("streams", streams)

    return meta
}