package dajver.com.videotrimmerexample.timmer.interfaces

import dajver.com.videotrimmerexample.timmer.model.RangesModel
import java.io.File

interface TrimmerEndWorkListener {
    fun onTrimEnds(file: File, newRange: RangesModel)

    fun onTrimError(error: Exception)

    fun onEmptyFileError(error: Exception)
}
