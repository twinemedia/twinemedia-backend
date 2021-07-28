package net.termer.twinemedia.util

import io.vertx.core.AsyncResult
import io.vertx.core.Future
import io.vertx.core.Future.failedFuture
import io.vertx.core.Future.succeededFuture
import io.vertx.core.Handler
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.kotlin.core.json.json
import io.vertx.kotlin.core.json.obj
import io.vertx.kotlin.coroutines.await
import io.vertx.kotlin.coroutines.awaitEvent
import io.vertx.kotlin.coroutines.dispatcher
import io.vertx.sqlclient.RowSet
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.runBlocking
import net.bramp.ffmpeg.FFmpeg
import net.bramp.ffmpeg.FFmpegExecutor
import net.bramp.ffmpeg.FFprobe
import net.bramp.ffmpeg.builder.FFmpegBuilder
import net.bramp.ffmpeg.probe.FFmpegProbeResult
import net.termer.twine.ServerManager.vertx
import net.termer.twine.utils.StringFilter.generateString
import net.termer.twinemedia.Module.Companion.config
import net.termer.twinemedia.Module.Companion.mediaSourceManager
import net.termer.twinemedia.db.dataobject.Media
import net.termer.twinemedia.exception.MediaNotFoundException
import net.termer.twinemedia.exception.WrongMediaTypeException
import net.termer.twinemedia.model.*
import net.termer.twinemedia.source.MediaSourceException
import net.termer.twinemedia.source.MediaSourceFileNotFoundException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.Base64
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.lang.RuntimeException
import java.net.URLConnection
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread
import kotlin.random.Random


private val mediaModel = MediaModel()

@DelicateCoroutinesApi
private val ffmpeg = FFmpeg(config.ffmpeg_path)
@DelicateCoroutinesApi
private val ffprobe = FFprobe(config.ffprobe_path)
@DelicateCoroutinesApi
private val executor = FFmpegExecutor(ffmpeg, ffprobe)

private val mediaQueue = CopyOnWriteArrayList<MediaProcessorJob>()
private val processingMedia = CopyOnWriteArrayList<MediaProcessorJob>()
private var jobIdIncrementer = 0

private val procLogger: Logger = LoggerFactory.getLogger(MediaProcessorJob::class.java)

// Currently stored media in <config.processing_location>.
// When a job is finished, it will check if there are any more jobs queued with the same ID, and if not, it will delete the file and the cache entry.
// Key: Media ID
// Value: File path
private val mediaCache = ConcurrentHashMap<String, String>()

// Currently downloading media.
// Key: Media ID
// Value: ArrayList of handlers that will return the path of the finished download, or an error if it failed
private val downloads = ConcurrentHashMap<String, CopyOnWriteArrayList<Handler<AsyncResult<String>>>>()

/**
 * Cancels all queued processing jobs by their parent's ID
 * @param id The parent's ID
 * @since 1.5.0
 */
fun cancelQueuedProcessingJobsByParent(id: String) {
    for(job in mediaQueue)
        if(job.id == id)
            mediaQueue.remove(job)
}

/**
 * Returns all current processing jobs
 * @return All current processing jobs
 * @since 1.5.0
 */
fun currentProcessingJobs() = processingMedia.toTypedArray()

/**
 * Returns all current processing jobs with the specified parent
 * @param id The parent's ID
 * @return All current processing jobs with the specified parent
 * @since 1.5.0
 */
fun currentProcessingJobsByParent(id: String): Array<MediaProcessorJob> {
    val res = ArrayList<MediaProcessorJob>()

    for(job in processingMedia)
        if(job.id == id)
            res.add(job)

    return res.toTypedArray()
}

/**
 * Puts a media entry in the media processor media path
 * @param mediaId The media ID
 * @param path The path to the cached media file on disk
 * @since 1.5.0
 */
fun putMediaCachePath(mediaId: String, path: String) {
    mediaCache[mediaId] = path
}

/**
 * Returns whether the provided media ID's file is currently cached for use by the media processor
 * @return Whether the provided media ID's file is currently cached for use by the media processor
 * @since 1.5.0
 */
fun isMediaCached(id: String) = mediaCache.containsKey(id)

/**
 * Returns the path on disk of the underlying file for the specified media ID.
 * If it is currently cached on the disk, returns the cache URL, otherwise downloads it, caches it, and returns it.
 * @return The file path
 * @throws MediaNotFoundException If the media file does not exist
 * @throws MediaSourceException If an error occurs while downloading the file
 * @since 1.5.0
 */
@DelicateCoroutinesApi
private suspend fun getMediaPath(id: String): String {
    if(mediaCache.containsKey(id)) {
        return mediaCache[id]!!
    } else if(downloads.containsKey(id)) {
        val res = awaitEvent<AsyncResult<String>> {
            downloads[id]!!.add(it)
        }

        if(res.succeeded())
            return res.result()
        else
            throw res.cause()
    } else {
        // Fetch media
        val mediaRes = mediaModel.fetchMedia(id)

        // Check if it exists
        if(mediaRes.rowCount() > 0) {
            val media = mediaRes.first()

            // Create downloads entry
            val hdlrs = CopyOnWriteArrayList<Handler<AsyncResult<String>>>()
            downloads[id] = hdlrs

            try {
                // Fetch source
                val src = mediaSourceManager.getSourceInstanceById(media.source)

                // Download file
                val loc = config.processing_location + media.key
                src!!.downloadFile(media.key, loc)

                // Add cache media cache entry
                mediaCache[id] = loc
                downloads.remove(id)

                // Run handlers
                for(hdlr in hdlrs)
                    hdlr.handle(succeededFuture(loc))

                return loc
            } catch(e: Exception) {
                for(hdlr in hdlrs)
                    hdlr.handle(failedFuture(e))
                throw e
            }
        } else {
            throw MediaNotFoundException("Media ID $id does not exist")
        }
    }
}

// Returns a new unique job ID integer
private fun newJobId() = jobIdIncrementer++

// Utility to infer MIME type from filename
private val fileNameMap = URLConnection.getFileNameMap()

/**
 * Return a mime for the specified file extension
 * @param extension The file extension to use for guessing
 * @return A mime for the specified file extension
 * @since 1.5.0
 */
fun mimeFor(extension: String) = fileNameMap.getContentTypeFor("file.$extension")?: when(extension) {
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
 * @since 1.0.0
 */
@DelicateCoroutinesApi
fun startMediaProcessor(): Thread {
    val thread = thread(start = false) {
        // Sleep random amount of time to avoid deadlocks with other threads
        Thread.sleep(Random.nextLong(0, 1000))

        // Infinite processing loop
        loop@while(true) {
            var curJob: MediaProcessorJob? = null

            try {
                // Sleep for 1 second to avoid checking queue too frequently
                Thread.sleep(1000)

                // Check if queue has any jobs
                val queueSize = mediaQueue.size
                if(queueSize > 0) {
                    // Get job, remove it from queue, and put it in currently processing jobs array
                    val job = mediaQueue[0]
                    curJob = job
                    mediaQueue.removeAt(0)
                    processingMedia.add(job)

                    val jobId = newJobId()

                    procLogger.info("Starting process for new ${job.type.name} job")
                    procLogger.info("Job ID: $jobId")
                    procLogger.info("Media ID: ${job.id}")

                    // Collect job data
                    val id = job.id
                    val type = job.type
                    val extension = job.extension
                    val settings = job.settings
                    var tmpOut: String? = null

                    // Generate output media ID or use provided
                    val outId = job.newId?: generateString(10)

                    // Cleans up job
                    fun cleanUpJob() {
                        // Remove job from currently processing jobs
                        processingMedia.remove(job)

                        // Check if the source media file is in use by any other jobs or will be by queued jobs, and if not, delete the cached version
                        var currentJobRequires = false
                        for(concurrentJob in processingMedia) {
                            if(concurrentJob.id == job.id) {
                                currentJobRequires = true
                                break
                            }
                        }
                        var futureJobRequires = false
                        for(futureJob in mediaQueue) {
                            if(futureJob.id == job.id) {
                                futureJobRequires = true
                                break
                            }
                        }

                        if(!currentJobRequires && !futureJobRequires && tmpOut != null)
                            File(tmpOut!!).delete()
                    }

                    // Store error handler in order to catch other error types with the same code
                    fun handleError(e: Exception) {
                        cleanUpJob()

                        procLogger.error("Failed to encode media file:")
                        e.printStackTrace()

                        // Broadcast encoding error
                        vertx().eventBus().publish("twinemedia.event.media.process", json {
                            obj(
                                    "id" to outId,
                                    "creator" to job.creator,
                                    "status" to "error",
                                    "error" to "Encoding error"
                            )
                        })

                        // Attempt to record processing error
                        try {
                            runBlocking(vertx().dispatcher()) {
                                mediaModel.updateMediaProcessError(outId, "Processing failed due to encoding error")
                            }
                        } catch (e: Exception) {
                            procLogger.error("Failed to update media file's process error field:")
                            e.printStackTrace()

                            // Nothing can be done at this point
                        }

                        // Run callback on context
                        if(job.callback != null) {
                            vertx().runOnContext {
                                job.callback?.handle(failedFuture(e))
                            }
                        }
                    }

                    // Get media
                    val mediaRes: RowSet<Media>
                    runBlocking(vertx().dispatcher()) {
                        mediaRes = mediaModel.fetchMedia(job.id)
                    }
                    // Make sure it exists
                    if(mediaRes.rowCount() < 1) {
                        handleError(MediaSourceFileNotFoundException("Media ID ${job.id} does not exist, so the job cannot continue"))
                        continue@loop
                    }
                    val media = mediaRes.first()

                    // Check if media type is video or audio
                    val mime = media.mime
                    if(!mime.startsWith("video/") && !mime.startsWith("audio/")) {
                        handleError(WrongMediaTypeException("Media type is $mime, not audio or video"))
                        continue@loop
                    }

                    // Guess mime
                    val outMime = mimeFor(extension)

                    // Generate output key
                    var outKey = ((if(media.key.contains("."))
                        media.key.substring(0, media.key.lastIndexOf('.'))
                    else
                        media.key)+"-child-${System.currentTimeMillis()/1000L}.$extension").replace(' ', '_')
                    if(outKey.length > 256)
                        outKey = outKey.substring(outKey.length-256)

                    // Figure out temporary output location
                    tmpOut = config.processing_location+outKey

                    // Generate output filename
                    val oldFilename = media.filename
                    val dotIdx = oldFilename.lastIndexOf('.')
                    val outFilename = (if(dotIdx < 0)
                        oldFilename
                    else
                        oldFilename.substring(0, dotIdx))+".$extension"

                    // Create new media entry if one doesn't already exist
                    try {
                        runBlocking(vertx().dispatcher()) {
                            if(mediaModel.fetchMedia(outId).rowCount() < 1) {
                                mediaModel.createMedia(
                                        id = outId,
                                        name = media.name,
                                        filename = outFilename,
                                        size = -1,
                                        mime = outMime,
                                        key = outKey,
                                        creator = job.creator,
                                        hash = "PROCESSING",
                                        thumbnailFile = null,
                                        meta = JsonObject(),
                                        parent = media.internalId,
                                        processing = true,
                                        sourceId = media.source
                                )
                            }
                        }
                    } catch(e: Exception) {
                        handleError(e)
                        continue@loop
                    }

                    // Get file path on disk
                    val sourcePath: String
                    try {
                        runBlocking(vertx().dispatcher()) {
                            sourcePath = getMediaPath(job.id)
                        }
                    } catch(e: Exception) {
                        handleError(e)
                        continue@loop
                    }

                    // Probe input file for duration
                    val mediaProbe: FFmpegProbeResult
                    try {
                        runBlocking(vertx().dispatcher()) {
                            mediaProbe = probeFile(sourcePath)!!
                        }
                    } catch(e: Exception) {
                        handleError(e)
                        continue@loop
                    }
                    val duration = mediaProbe.getFormat().duration.toInt()

                    // Setup ffmpeg job
                    val builder = FFmpegBuilder()
                            .setInput(sourcePath)
                            .addOutput(tmpOut)
                    if(type == MediaProcessorJobType.VIDEO) {
                        // Job is for video, set video bitrate as well as audio
                        val videoBitrate = settings.getInteger("video_bitrate").coerceIn(-1, 51200)
                        val audioBitrate = settings.getInteger("audio_bitrate").coerceIn(-1, 51200)

                        if(videoBitrate > -1)
                            builder
                                    .addExtraArgs("-b:v")
                                    .addExtraArgs("${videoBitrate}k")
                        if(audioBitrate > -1)
                            builder
                                    .addExtraArgs("-b:a")
                                    .addExtraArgs("${audioBitrate}k")

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
                            val frameRate = settings.getInteger("frame_rate").coerceIn(-1, 512)

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

                    try {
                        // Run job
                        executor.createJob(builder.done()) { prog ->
                            // Broadcast progress percentage
                            val percent = ((prog.out_time_ns/10e6) / duration).toInt().coerceAtMost(100)

                            vertx().eventBus().publish("twinemedia.event.media.process", json {
                                obj(
                                        "id" to outId,
                                        "creator" to job.creator,
                                        "status" to "progress",
                                        "percent" to percent
                                )
                            })
                        }.run()
                    } catch(e: Exception) {
                        handleError(e)
                        continue@loop
                    } catch(e: RuntimeException) {
                        handleError(e)
                        continue@loop
                    }

                    // Fetch info about new file
                    val fileSize = File(tmpOut).length()

                    // Hash file
                    val buffer = ByteArray(8192)
                    var count: Int
                    val digest = MessageDigest.getInstance("SHA-256")
                    val bis = BufferedInputStream(FileInputStream(tmpOut))
                    while(bis.read(buffer).also { count = it } > 0) {
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
                                createVideoThumbnail(tmpOut, duration, "${config.thumbnails_location}$thumbId.jpg")
                            } else if(type == MediaProcessorJobType.AUDIO) {
                                createImagePreview(tmpOut, "${config.thumbnails_location}$thumbId.jpg")
                            }
                        }

                        // Set thumbnail file
                        thumbnail = "$thumbId.jpg"
                    } catch (e: Exception) {
                        // Failed to generate thumbnail
                    }

                    // Probe file for metadata
                    val meta = ffprobeToJsonMeta(ffprobe.probe(tmpOut))

                    try {
                        // Update media info
                        runBlocking(vertx().dispatcher()) {
                            mediaModel.updateMediaInfo(outId, false, fileSize, hash, thumbnail, meta)
                        }

                        // Broadcast success
                        vertx().eventBus().publish("twinemedia.event.media.process", json {
                            obj(
                                    "id" to outId,
                                    "creator" to job.creator,
                                    "status" to "success"
                            )
                        })

                        // Once job is finished and output is dealt with, upload it to the original media's source and delete the temporary file
                        try {
                            runBlocking(vertx().dispatcher()) {
                                val source = mediaSourceManager.getSourceInstanceById(media.source)
                                source!!.uploadFile(tmpOut, outKey)
                            }

                            if(!File(tmpOut).delete())
                                throw IOException("Failed to delete temporary processing file \"$tmpOut\"")
                        } catch(e: Exception) {
                            handleError(e)
                            continue@loop
                        }

                        cleanUpJob()

                        procLogger.info("Finished job ID $jobId")

                        // Run callback on context
                        if(job.callback != null) {
                            vertx().runOnContext {
                                job.callback?.handle(succeededFuture(outId))
                            }
                        }

                    } catch (e: Exception) {
                        procLogger.error("Failed to update media info after processing:")
                        e.printStackTrace()

                        // Broadcast processing error
                        vertx().eventBus().publish("twinemedia.event.media.process", json {
                            obj(
                                    "id" to outId,
                                    "creator" to job.creator,
                                    "status" to "error",
                                    "error" to "Database error"
                            )
                        })

                        // Attempt to record processing error
                        try {
                            runBlocking(vertx().dispatcher()) {
                                mediaModel.updateMediaProcessError(outId, "Processing failed due to database error")
                            }
                        } catch (e: Exception) {
                            procLogger.error("Failed to update media file's process error field:")
                            e.printStackTrace()

                            // Nothing can be done at this point
                        }

                        cleanUpJob()

                        // Run callback on context
                        if(job.callback != null) {
                            vertx().runOnContext {
                                job.callback?.handle(failedFuture(e))
                            }
                        }
                    }
                }
            } catch(e: Exception) {
                procLogger.error("Error in processor loop:")
                e.printStackTrace()
            }

            // If there was a job, it is no longer processing, error or not
            if(curJob != null)
                processingMedia.remove(curJob)
        }
    }
    thread.name = "MediaProcessor ${ generateString(5) }"
    thread.start()
    return thread
}

/**
 * Types of media processing jobs
 * @since 1.0.0
 */
enum class MediaProcessorJobType {
    VIDEO,
    AUDIO
}

/**
 * Interface to define media processor jobs
 * @since 1.0.0
 */
interface MediaProcessorJob {
    /**
     * The alphanumeric ID for the media entry this job will process from
     * @since 1.0.0
     */
    val id: String
    /**
     * The alphanumeric ID for the new media entry that will be created (null to generate it on the fly)
     * @since 1.5.0
     */
    val newId: String?
    /**
     * The job creator's account ID
     * @since 1.5.0
     */
    val creator: Int
    /**
     * The output file's extension, used to determine which format to encode to
     * @since 1.5.0
     */
    val extension: String
    /**
     * The type of job
     * @since 1.0.0
     */
    val type: MediaProcessorJobType
    /**
     * The settings to use when encoding the file
     * @since 1.0.0
     */
    val settings: JsonObject
    /**
     * The callback to execute when this job has succeeded or failed, returning the newly created media ID
     * @since 1.0.0
     */
    val callback: Handler<AsyncResult<String>>?
}

/**
 * Queues a media processing job.
 *
 * JSON parameters for the settings object:
 * frame_rate (for video only): Integer, the frame rate for the output video
 * audio_bitrate: Integer, the audio bitrate for the output in kB/s
 * video_bitrate (for video only): Integer, the video bitrate for the output in kB/s
 * width (optional, for video only): Integer, the width of the output video (use -1 to keep aspect ratio) (max. value is 7680)
 * height (optional, for video only): Integer, the height of the output video (use -1 to keep aspect ratio) (max. value is 4320)
 * @param job The an object implementing MediaProcessorJob containing the job's info
 * @return A future that is fulfilled when the process succeeds (with the newly created media entry's ID) or fails
 * @since 1.5.0
 */
fun queueMediaProcessJob(job: MediaProcessorJob): Future<String> = Future.future { promise ->
    // Create new job with custom callback
    mediaQueue.add(object: MediaProcessorJob {
        override val id = job.id
        override val newId = job.newId
        override val creator = job.creator
        override val extension = job.extension
        override val type = job.type
        override val settings = job.settings
        override val callback = Handler<AsyncResult<String>> {
            try {
                // Run original callback
                job.callback?.handle(it)

                // Fulfill promise
                if(it.succeeded())
                    promise.complete(it.result())
                else
                    promise.fail(it.cause())
            } catch(e: Exception) {
                promise.fail(e)
            }
        }
    })
}

/**
 * Queues a media processing job.
 *
 * JSON parameters for the settings object:
 * frame_rate (for video only): Integer, the frame rate for the output video
 * audio_bitrate: Integer, the audio bitrate for the output in kB/s
 * video_bitrate (for video only): Integer, the video bitrate for the output in kB/s
 * width (optional, for video only): Integer, the width of the output video (use -1 to keep aspect ratio) (max. value is 7680)
 * height (optional, for video only): Integer, the height of the output video (use -1 to keep aspect ratio) (max. value is 4320)
 * @param mediaId The alphanumeric ID of the media to process from
 * @param newId The alphanumeric ID of the new media entry to create (specify null to generate on the fly)
 * @param extension The file extension to use (e.g. "mp4")
 * @param creator The ID of the creator of this file
 * @param settings The processing settings to use
 * @return A future that is fulfilled when the process succeeds (with the newly created media entry's ID) or fails
 * @since 1.5.0
 */
fun queueMediaProcessJob(mediaId: String, newId: String?, extension: String, creator: Int, settings: JsonObject): Future<String> = Future.future { promise ->
    mediaQueue.add(object: MediaProcessorJob {
        override val id = mediaId
        override val newId = newId
        override val creator = creator
        override val extension = extension
        override val type = if(mimeFor(extension).startsWith("video/")) MediaProcessorJobType.VIDEO else MediaProcessorJobType.AUDIO
        override val settings = settings
        override val callback = Handler<AsyncResult<String>> {
            if(it.succeeded())
                promise.complete(it.result())
            else
                promise.fail(it.cause())
        }
    })
}

/**
 * Returns info about a media file
 * @param filePath The file to probe with FFprobe
 * @return The FFmpegProbeResult object for the provided media file
 * @since 1.0.0
 */
@DelicateCoroutinesApi
suspend fun probeFile(filePath: String): FFmpegProbeResult? {
    return vertx().executeBlocking<FFmpegProbeResult> {
        try {
            val probe = ffprobe.probe(filePath)

            if(probe.hasError()) {
                it.fail(Exception(probe.error.string))
            } else {
                it.complete(probe)
            }
        } catch(e: Exception) {
            it.fail(e)
        }
    }.await()
}

/**
 * Creates a thumbnail from a video file
 * @param filePath The video file
 * @param duration The duration of the video in seconds
 * @param outPath The file to output thumbnail file
 * @since 1.0.0
 */
@DelicateCoroutinesApi
suspend fun createVideoThumbnail(filePath: String, duration: Int, outPath: String) {
    vertx().executeBlocking<Unit> {
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
        } catch(e: Exception) {
            it.fail(e)
        }
    }.await()
}

/**
 * Creates a preview image from an image file
 * @param filePath The image file to create the preview from
 * @param outPath The output file for the preview image
 * @since 1.0.0
 */
@DelicateCoroutinesApi
suspend fun createImagePreview(filePath: String, outPath: String) {
    vertx().executeBlocking<Unit> {
        try {
            val builder = FFmpegBuilder()
                    .setInput(filePath)
                    .setAudioFilter("scale=-1:360")
                    .addOutput(outPath)
                    .done()
            executor.createJob(builder).run()
            it.complete()
        } catch(e: Exception) {
            it.fail(e)
        }
    }.await()
}

/**
 * Creates a JSON object containing most key probe info
 * @param probe The probe result to extract data from
 * @since 1.0.0
 */
fun ffprobeToJsonMeta(probe: FFmpegProbeResult): JsonObject {
    val format = probe.format
    val meta = JsonObject()

    // Collect metadata from probe
    meta
            .put("bitrate", format.bit_rate)
            .put("duration", format.duration)
            .put("format_name", format.format_name)
            .put("format_long_name", format.format_long_name)
            .put("stream_count", format.nb_streams)
            .put("program_count", format.nb_programs)
            .put("start_time", format.start_time)
            .put("size", format.size)
            .put("probe_score", format.probe_score)
            .put("tags", JsonObject())

    // Put tags
    if(format.tags != null)
        for ((key, value) in format.tags.entries)
            meta.getJsonObject("tags").put(key.toLowerCase(), value)

    // Put streams
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