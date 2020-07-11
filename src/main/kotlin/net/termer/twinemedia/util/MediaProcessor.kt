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
import io.vertx.kotlin.coroutines.awaitEvent
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import net.bramp.ffmpeg.FFmpeg
import net.bramp.ffmpeg.FFmpegExecutor
import net.bramp.ffmpeg.FFprobe
import net.bramp.ffmpeg.builder.FFmpegBuilder
import net.bramp.ffmpeg.probe.FFmpegProbeResult
import net.termer.twine.ServerManager.vertx
import net.termer.twine.utils.StringFilter.generateString
import net.termer.twinemedia.Module.Companion.config
import net.termer.twinemedia.exception.MediaNotFoundException
import net.termer.twinemedia.exception.WrongMediaTypeException
import net.termer.twinemedia.model.createMedia
import net.termer.twinemedia.model.fetchMedia
import net.termer.twinemedia.model.updateMediaInfo
import net.termer.twinemedia.model.updateMediaProcessError
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.Base64
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.net.URLConnection
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import kotlin.random.Random

private val ffmpeg = FFmpeg(config.ffmpeg_path)
private val ffprobe = FFprobe(config.ffprobe_path)
private val executor = FFmpegExecutor(ffmpeg, ffprobe)

private val mediaQueue = CopyOnWriteArrayList<MediaProcessorJob>()
private var jobIdIncrementer = 0

private val procLogger : Logger = LoggerFactory.getLogger(MediaProcessorJob::class.java)

// Returns a new unique job ID integer
private fun newJobId() = jobIdIncrementer++

// Fetches a mime for the specified file extension
private fun mimeFor(extension: String) = when(extension) {
    "mp4" -> "video/mp4"
    "m4a" -> "audio/mp4"
    "webm" -> "video/webm"
    "ogv" -> "video/ogg"
    "mkv" -> "video/x-matroska"
    "oga", "ogg" -> "audio/ogg"
    "mp3" -> "audio/mpeg"
    "wav" -> "audio/wav"
    "flac" -> "audio/flac"
    "aac" -> "audio/aac"
    else -> "application/octet-stream"
}

// Acceptable audio file extensions for processing
val audioExtensions = arrayOf(
        "mp3",
        "m4a",
        "flac",
        "wav",
        "aac",
        "oga",
        "ogg"
)
// Acceptable video file extensions for processing
val videoExtensions = arrayOf(
        "mp4",
        "webm",
        "mkv",
        "ogv"
)

/**
 * Starts a media processor instance
 * @since 1.0
 */
fun startMediaProcessor() : Thread {
    val thread = thread(start = false) {
        // Sleep random amount of time to avoid deadlocks with other threads
        Thread.sleep(Random.nextLong(0, 1000))

        // Infinite processing loop
        loop@ while(true) {
            try {
                // Sleep for 1 second to avoid checking queue too frequently
                Thread.sleep(1000)

                // Check if queue has any jobs
                val queueSize = mediaQueue.size
                if(queueSize > 0) {
                    // Get job and remove it from queue
                    val job = mediaQueue[0]
                    mediaQueue.removeAt(0)

                    val jobId = newJobId()

                    procLogger.info("Starting process for new ${if(job.type == MediaProcessorJobType.VIDEO) "VIDEO" else "AUDIO"} job")
                    procLogger.info("Job ID: $jobId")
                    procLogger.info("Source: ${job.source}")
                    procLogger.info("Duration: ${job.duration}")
                    procLogger.info("Output: ${job.out}")

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
                        val videoBitrate = settings.getInteger("video_bitrate").coerceIn(-1, 51200)
                        val audioBitrate = settings.getInteger("audio_bitrate").coerceIn(-1, 51200)

                        if(videoBitrate > -1)
                            builder
                                    .addExtraArgs("-b:v")
                                    .addExtraArgs("${ videoBitrate }k")
                        if(audioBitrate > -1)
                            builder
                                    .addExtraArgs("-b:a")
                                    .addExtraArgs("${ audioBitrate }k")

                        // If dimensions are specified, apply them
                        if(settings.containsKey("width") && settings.containsKey("height")) {
                            var width = settings.getInteger("width").coerceIn(-1, 7680)
                            var height = settings.getInteger("height").coerceIn(-1, 4320)

                            // Add pixel if width or height are not divisible by 2
                            if(width > 0 && width%2 > 0)
                                width++
                            if(height > 0 && height%2 > 0)
                                height++

                            // Apply video filter
                            builder.video_filter = "scale=min\\(${if(width < 0) -2 else width}\\,iw\\):min\\(${if(height < 0) -2 else height}\\,ih\\)"
                        }

                        // If frame rate was specified, set video frame rate
                        if(settings.containsKey("frame_rate")) {
                            var frameRate = settings.getInteger("frame_rate").coerceIn(-1, 512)

                            if(frameRate > -1)
                                builder
                                        .addExtraArgs("-r")
                                        .addExtraArgs(frameRate.toString())
                        }

                    } else if(type == MediaProcessorJobType.AUDIO) {
                        val audioBitrate = settings.getInteger("audio_bitrate").coerceIn(-1, 51200)

                        if(audioBitrate > -1)
                            builder
                                    .addExtraArgs("-b:a")
                                    .addExtraArgs("${ audioBitrate }k")
                    }


                    // Run job
                    try {
                        executor.createJob(builder.done()) { prog ->
                            // Broadcast progress percentage
                            val percent = ((prog.out_time_ns/10e6) / duration).toInt().coerceAtMost(100)

                            vertx().eventBus().publish("twinemedia.process.$id", json {
                                obj(
                                        "status" to "progress",
                                        "percent" to percent
                                )
                            })
                        }.run()
                    } catch (e: Exception) {
                        procLogger.error("Failed to encode media file:")
                        e.printStackTrace()

                        // Delete output file
                        File(out).delete()

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
                        } catch (e: Exception) {
                            procLogger.error("Failed to update media file's process error field:")
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
                    var count: Int
                    val digest = MessageDigest.getInstance("SHA-256")
                    val bis = BufferedInputStream(FileInputStream(out))
                    while (bis.read(buffer).also { count = it } > 0) {
                        digest.update(buffer, 0, count)
                    }
                    bis.close()

                    val hash = String(Base64.getEncoder().encode(digest.digest()))

                    // Try to generate thumbnail
                    var thumbnail: String? = null
                    try {
                        val thumbId = generateString(10)

                        // Generate video thumbnail or audio thumbnail
                        runBlocking(vertx().dispatcher()) {
                            if(type == MediaProcessorJobType.VIDEO) {
                                createVideoThumbnail(out, duration, "${config.upload_location}thumbnails/$thumbId.jpg")
                            } else if(type == MediaProcessorJobType.AUDIO) {
                                createImagePreview(out, "${config.upload_location}thumbnails/$thumbId.jpg")
                            }
                        }

                        // Set thumbnail file
                        thumbnail = "$thumbId.jpg"
                    } catch (e: Exception) {
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

                        procLogger.info("Finished job ID $jobId")

                        // Run callback
                        if(job.callback != null)
                            job.callback?.handle(succeededFuture())

                    } catch (e: Exception) {
                        procLogger.error("Failed to update media info after processing:")
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
                        } catch (e: Exception) {
                            procLogger.error("Failed to update media file's process error field:")
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
            } catch(e : Exception) {
                procLogger.error("Error in processor loop:")
                e.printStackTrace()
            }
        }
    }
    thread.name = "MediaProcessor ${ generateString(5) }"
    thread.start()
    return thread
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
 * Job parameters:
 * id: String, the alphanumeric ID for the media entry this job is for
 * source: String, the path to the source file
 * out: String, the path to write the new file
 * duration: Integer, the duration of the file in seconds
 * type: String, the type of job ("video" or "audio")
 * settings: JSON object, the settings to use when encoding the file
 *
 * JSON parameters for the settings object:
 * frame_rate (for video only): Integer, the frame rate for the output video
 * audio_bitrate: Integer, the audio bitrate for the output in kB/s
 * video_bitrate (for video only): Integer, the video bitrate for the output in kB/s
 * width (optional, for video only): Integer, the width of the output video (use -1 to keep aspect ratio) (max. value is 7680)
 * height (optional, for video only): Integer, the height of the output video (use -1 to keep aspect ratio) (max. value is 4320)
 * @param job The an object implementing MediaProcessorJob containing the job's info
 * @since 1.0
 */
fun queueMediaProcessJob(job : MediaProcessorJob) {
    mediaQueue.add(job)
}

/**
 * Queues a media processing job, and suspends the current coroutine until either the process succeeds or fails.
 * Note: Set the "callback" field to null, because this function will replace it with its own custom callback.
 *
 * Job parameters:
 * id: String, the alphanumeric ID for the media entry this job is for
 * source: String, the path to the source file
 * out: String, the path to write the new file
 * duration: Integer, the duration of the file in seconds
 * type: String, the type of job ("video" or "audio")
 * settings: JSON object, the settings to use when encoding the file
 *
 * JSON parameters for the settings object:
 * frame_rate (for video only): Integer, the frame rate for the output video
 * audio_bitrate: Integer, the audio bitrate for the output in kB/s
 * video_bitrate (for video only): Integer, the video bitrate for the output in kB/s
 * width (optional, for video only): Integer, the width of the output video (use -1 to keep aspect ratio) (max. value is 7680)
 * height (optional, for video only): Integer, the height of the output video (use -1 to keep aspect ratio) (max. value is 4320)
 * @param job The an object implementing MediaProcessorJob containing the job's info
 * @since 1.0
 */
suspend fun queueMediaProcessJobAwait(job : MediaProcessorJob) {
    awaitEvent<AsyncResult<Unit>> {
        // Create new job with custom callback
        mediaQueue.add(object : MediaProcessorJob {
            override val id = job.id
            override val source = job.source
            override val out = job.out
            override val duration = job.duration
            override val type = job.type
            override val settings = job.settings
            override val callback: Handler<AsyncResult<Unit>>? = it
        })
    }
}

/**
 * Queues a media processing job from an existing media file.
 *
 * JSON parameters for the settings object:
 * frame_rate (for video only): Integer, the frame rate for the output video
 * audio_bitrate: Integer, the audio bitrate for the output in kB/s
 * video_bitrate (for video only): Integer, the video bitrate for the output in kB/s
 * width (optional, for video only): Integer, the width of the output video (use -1 to keep aspect ratio) (max. value is 7680)
 * height (optional, for video only): Integer, the height of the output video (use -1 to keep aspect ratio) (max. value is 4320)
 * @param sourceId The alphanumeric ID of the media to process from
 * @param newId The alphanumeric ID to give to the new media entry
 * @param extension The file extension to use (e.g. "mp4")
 * @param creator The ID of the creator of this file
 * @param settings The processing settings to use
 * @param callback The callback to run when the process succeeds or fails
 * @throws MediaNotFoundException If the source media file entry does not exist
 * @throws WrongMediaTypeException If the source media file is not audio or video
 */
suspend fun queueMediaProcessJobFromMedia(sourceId : String, newId : String, extension : String, creator : Int, settings : JsonObject, callback : Handler<AsyncResult<Unit>>?) {
    // Fetch source media
    val sourceRes = fetchMedia(sourceId)

    // Check if it exists
    if(sourceRes != null && sourceRes.rows.size > 0) {
        val source = sourceRes.rows[0]
        val sourcePath = config.upload_location+source.getString("media_file")

        // Check if media type is video or audio
        val mime = source.getString("media_mime")
        if(!mime.startsWith("video/") && !mime.startsWith("audio/"))
            throw WrongMediaTypeException("Media type is $mime, not audio or video")

        // Utility to infer MIME type from filename
        val fileNameMap = URLConnection.getFileNameMap()

        // Probe source
        val sourceProbe = probeFile(sourcePath)

        // Gather some info
        val filename = "$newId.$extension"
        val duration = sourceProbe?.getFormat()?.duration?.toInt()

        // Guess mime
        val outMime = fileNameMap.getContentTypeFor(filename)?: mimeFor(extension)

        // Generate new filename
        val oldFilename = source.getString("media_filename")
        val filenameParts = oldFilename.split(".")
        val newFilename = oldFilename.substring(0, oldFilename.length-(filenameParts[filenameParts.size-1].length))+extension

        // Create new media entry
        createMedia(
                id = newId,
                name = source.getString("media_name"),
                filename = newFilename,
                size = -1,
                mime = outMime,
                file = filename,
                creator = creator,
                hash = "PROCESSING",
                thumbnailFile = null,
                meta = JsonObject(),
                parent = source.getInteger("id"),
                processing = true
        )

        // Queue processing job
        queueMediaProcessJob(object : MediaProcessorJob {
            override val id = newId
            override val source = sourcePath
            override val out = config.upload_location+filename
            override val duration = duration ?: 0
            override val type = if(mime.startsWith("video/")) MediaProcessorJobType.VIDEO else MediaProcessorJobType.AUDIO
            override val settings = settings
            override val callback : Handler<AsyncResult<Unit>>? = callback
        })
    } else {
        throw MediaNotFoundException("Media ID $sourceId does not point to any media entry")
    }
}
/**
 * Queues a media processing job from an existing media file.
 *
 * JSON parameters for the settings object:
 * frame_rate (for video only): Integer, the frame rate for the output video
 * audio_bitrate: Integer, the audio bitrate for the output in kB/s
 * video_bitrate (for video only): Integer, the video bitrate for the output in kB/s
 * width (optional, for video only): Integer, the width of the output video (use -1 to keep aspect ratio) (max. value is 7680)
 * height (optional, for video only): Integer, the height of the output video (use -1 to keep aspect ratio) (max. value is 4320)
 * @param sourceId The alphanumeric ID of the media to process from
 * @param newId The alphanumeric ID to give to the new media entry
 * @param extension The file extension to use (e.g. "mp4")
 * @param creator The ID of the creator of this file
 * @param settings The processing settings to use
 * @throws MediaNotFoundException If the source media file entry does not exist
 * @throws WrongMediaTypeException If the source media file is not audio or video
 */
suspend fun queueMediaProcessJobFromMedia(sourceId : String, newId : String, extension : String, creator : Int, settings : JsonObject) {
    queueMediaProcessJobFromMedia(sourceId, newId, extension, creator, settings, null)
}

/**
 * Queues a media processing job from an existing media file, and suspends the current coroutine until either the process succeeds or fails.
 * JSON parameters for the settings object:
 * frame_rate (for video only): Integer, the frame rate for the output video
 * audio_bitrate: Integer, the audio bitrate for the output in kB/s
 * video_bitrate (for video only): Integer, the video bitrate for the output in kB/s
 * width (optional, for video only): Integer, the width of the output video (use -1 to keep aspect ratio) (max. value is 7680)
 * height (optional, for video only): Integer, the height of the output video (use -1 to keep aspect ratio) (max. value is 4320)
 * @param sourceId The alphanumeric ID of the media to process from
 * @param newId The alphanumeric ID to give to the new media entry
 * @param extension The file extension to use (e.g. "mp4")
 * @param creator The ID of the creator of this file
 * @param settings The processing settings to use
 * @throws MediaNotFoundException If the source media file entry does not exist
 * @throws WrongMediaTypeException If the source media file is not audio or video
 */
suspend fun queueMediaProcessJobFromMediaAwait(sourceId: String, newId: String, extension: String, creator: Int, settings: JsonObject) {
    awaitEvent<AsyncResult<Unit>> {
        GlobalScope.launch(vertx().dispatcher()) {
            try {
                queueMediaProcessJobFromMedia(sourceId, newId, extension, creator, settings, it)
            } catch(e : Exception) {
                it.handle(failedFuture(e))
            }
        }
    }
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