package dajver.com.videotrimmerexample.timmer

import android.content.Context
import android.os.Environment
import com.coremedia.iso.IsoFile
import com.googlecode.mp4parser.FileDataSourceImpl
import com.googlecode.mp4parser.authoring.Track
import com.googlecode.mp4parser.authoring.builder.DefaultMp4Builder
import com.googlecode.mp4parser.authoring.container.mp4.MovieCreator
import com.googlecode.mp4parser.authoring.tracks.AppendTrack
import com.googlecode.mp4parser.authoring.tracks.CroppedTrack
import dajver.com.videotrimmerexample.timmer.interfaces.TrimmerEndWorkListener
import dajver.com.videotrimmerexample.timmer.model.RangesModel
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.*

class VideoTrimmer(private val context: Context, private val srcPath: File, private val callback: TrimmerEndWorkListener) {

    private var videoTimeScale: Long = 0
    private var totalLength: Double = 0.0

    init {
        try {
            val isoFile = IsoFile(srcPath.absolutePath)
            if (isoFile.movieBox == null) {
                callback.onEmptyFileError(Exception("Bad video error"))
            } else {
                videoTimeScale = isoFile.movieBox.movieHeaderBox.timescale
                totalLength = isoFile.movieBox.movieHeaderBox.duration.toDouble() / videoTimeScale
            }
        } catch (e: IOException) {
            e.printStackTrace()
            callback.onTrimError(e)
        }
    }

    fun startTrim(range: RangesModel) {
        try {
            genVideoUsingMp4Parser(range)
        } catch (ex: Exception) {
            callback.onTrimError(ex)
        }
    }

    private fun genVideoUsingMp4Parser(range: RangesModel) {
        try {
            val createdFile: File?
            var actualRange: RangesModel? = null

            val movie = MovieCreator.build(FileDataSourceImpl(srcPath.absolutePath))

            val tracks = movie.tracks
            movie.tracks = LinkedList()

            var startTime1 = range.startTime
            var endTime1 = range.endTime

            var timeCorrected = false
            for (track in tracks) {
                if (track.syncSamples != null && track.syncSamples.isNotEmpty()) {
                    if (timeCorrected) {
                        callback.onTrimError(RuntimeException("The startTime has already been corrected by another track with SyncSample. Not Supported."))
                    }

                    val correctedStartTime = correctTimeToSyncSample(track, startTime1, false)
                    if (correctedStartTime < startTime1 || (startTime1 == 0.0 && range.startTime != 0.0 && correctedStartTime != 0.0)) {
                        startTime1 = correctedStartTime
                    }

                    val correctedEndTime = correctTimeToSyncSample(track, endTime1, true)
                    if (correctedEndTime > endTime1 || (endTime1 == 0.0 && range.endTime != 0.0 && correctedEndTime != 0.0)) {
                        endTime1 = correctedEndTime
                    }

                    timeCorrected = true
                }
            }

            var finalCutPointStartTime = 0.0
            var finalCutPointEndTime = 0.0
            var realCutPointsCalculated = false

            for (track in tracks) {
                var currentSample: Long = 0
                var currentTime = 0.0
                var lastTime = -1.0
                var startSample1: Long = -1
                var endSample1: Long = -1

                track.sampleDurations.forEach {
                    if (currentTime > lastTime && currentTime <= startTime1) {
                        startSample1 = currentSample
                        finalCutPointStartTime = currentTime
                    }
                    if (currentTime > lastTime && currentTime <= endTime1) {
                        endSample1 = currentSample
                        finalCutPointEndTime = currentTime + (it.toDouble() / track.trackMetaData.timescale.toDouble())
                    }
                    lastTime = currentTime
                    currentTime += it.toDouble() / track.trackMetaData.timescale.toDouble()
                    currentSample++
                }

                if (!realCutPointsCalculated) {
                    if (finalCutPointStartTime > 0) {
                        finalCutPointStartTime *= 1000
                    }

                    val newRange = RangesModel(range.name, finalCutPointStartTime, finalCutPointEndTime * 1000)
                    actualRange = newRange
                    realCutPointsCalculated = true
                }

                movie.addTrack(AppendTrack(CroppedTrack(track, startSample1, endSample1)))
            }

            val created = File(context.getExternalFilesDir(Environment.DIRECTORY_DCIM), range.name + ".mp4")
            if (!created.exists())
                created.createNewFile()

             createdFile = created

            val out = DefaultMp4Builder().build(movie)

            val fos = FileOutputStream(created)
            val fc = fos.channel
            out.writeContainer(fc)

            fc.close()
            fos.close()

            callback.onTrimEnds(createdFile, actualRange!!)
        } catch (e: OutOfMemoryError) {
            callback.onTrimError(java.lang.Exception(e.localizedMessage))
        } catch (e1: Throwable) {
            callback.onTrimError(java.lang.Exception(e1.localizedMessage))
        }
    }

    private fun correctTimeToSyncSample(track: Track, cutHere: Double, next: Boolean): Double {
        val timeOfSyncSamples = DoubleArray(track.syncSamples.size)
        var currentSample: Long = 0
        var currentTime = 0.0
        track.sampleDurations.forEach {
            if (Arrays.binarySearch(track.syncSamples, currentSample + 1) >= 0) {
                timeOfSyncSamples[Arrays.binarySearch(track.syncSamples, currentSample + 1)] = currentTime
            }
            currentTime += it.toDouble() / track.trackMetaData.timescale.toDouble()
            currentSample++
        }

        var previous = 0.0
        for (timeOfSyncSample in timeOfSyncSamples) {
            if (timeOfSyncSample > cutHere) {
                return if (next) {
                    timeOfSyncSample
                } else {
                    previous
                }
            }
            previous = timeOfSyncSample
        }
        return timeOfSyncSamples[timeOfSyncSamples.size - 1]
    }
}