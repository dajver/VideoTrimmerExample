package dajver.com.videotrimmerexample

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import androidx.appcompat.app.AppCompatActivity
import com.coremedia.iso.IsoFile
import dajver.com.videotrimmerexample.timmer.VideoTrimmer
import dajver.com.videotrimmerexample.timmer.interfaces.TrimmerEndWorkListener
import dajver.com.videotrimmerexample.timmer.model.RangesModel
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File

class MainActivity : AppCompatActivity(), TrimmerEndWorkListener {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val fileToTrim = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "sample.mp4")
        val fileDuration = getFileDuration(fileToTrim)

        startTrim.setText(0.toString())
        endTrim.setText(fileDuration.toString())
        trimButton.setOnClickListener {
            val startTime = startTrim.text.toString().toDouble()
            val endTime = endTrim.text.toString().toDouble()
            val rangeModel = RangesModel("trimmed_file", startTime, endTime)
            val trimmer = VideoTrimmer(MainActivity@this, fileToTrim, MainActivity@this)
            trimmer.startTrim(rangeModel)
        }
    }

    private fun getFileDuration(file: File) : Double {
        val videoTimeScale: Long?
        var totalLength: Double? = null

        val isoFile = IsoFile(file.absolutePath)
        if (isoFile.movieBox != null) {
            videoTimeScale = isoFile.movieBox.movieHeaderBox.timescale
            totalLength = isoFile.movieBox.movieHeaderBox.duration.toDouble() / videoTimeScale
        }
        return totalLength!!
    }

    override fun onTrimEnds(file: File, newRange: RangesModel) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(file!!.path))
        intent.setDataAndType(Uri.parse(file.path), "video/mp4")
        startActivity(intent)
    }

    override fun onTrimError(error: Exception) {
        error.printStackTrace()
    }

    override fun onEmptyFileError(error: Exception) {
        error.printStackTrace()
    }
}
