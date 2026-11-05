package com.example.dualcam

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var recordButton: Button
    private lateinit var flipButton: Button
    private lateinit var timerText: TextView

    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    // الكاميرا الخلفية هي الأساس
    private var selector = CameraSelector.DEFAULT_BACK_CAMERA

    private val handler = Handler(Looper.getMainLooper())
    private var seconds = 0
    private val ticker = object : Runnable {
        override fun run() {
            seconds++
            timerText.text = "%02d:%02d".format(seconds / 60, seconds % 60)
            handler.postDelayed(this, 1000)
        }
    }

    private val permLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        if (granted[Manifest.permission.CAMERA] == true) startCamera()
        else Toast.makeText(this, "لازم إذن الكاميرا", Toast.LENGTH_LONG).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        previewView = findViewById(R.id.previewView)
        recordButton = findViewById(R.id.recordButton)
        flipButton = findViewById(R.id.flipButton)
        timerText = findViewById(R.id.timerText)

        recordButton.setOnClickListener { toggleRecording() }
        flipButton.setOnClickListener { flipCamera() }

        permLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }
            val recorder = Recorder.Builder()
                .setQualitySelector(QualitySelector.from(Quality.HD, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD)))
                .build()
            videoCapture = VideoCapture.withOutput(recorder)
            try {
                provider.unbindAll()
                provider.bindToLifecycle(this, selector, preview, videoCapture!!)
            } catch (e: Exception) {
                Toast.makeText(this, "تعذّر تشغيل الكاميرا: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun flipCamera() {
        if (recording != null) {
            Toast.makeText(this, "أوقف التسجيل الأول", Toast.LENGTH_SHORT).show()
            return
        }
        selector = if (selector == CameraSelector.DEFAULT_BACK_CAMERA)
            CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        startCamera()
    }

    private fun toggleRecording() {
        recording?.let { it.stop(); recording = null; return }
        val vc = videoCapture ?: return

        val name = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(System.currentTimeMillis())
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "DualCam_$name")
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/DualCam")
        }
        val options = MediaStoreOutputOptions.Builder(
            contentResolver, MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        ).setContentValues(values).build()

        var pending = vc.output.prepareRecording(this, options)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            == PackageManager.PERMISSION_GRANTED
        ) pending = pending.withAudioEnabled()

        recording = pending.start(ContextCompat.getMainExecutor(this)) { event ->
            when (event) {
                is VideoRecordEvent.Start -> setRecordingUi(true)
                is VideoRecordEvent.Finalize -> {
                    setRecordingUi(false)
                    recording = null
                    val msg = if (event.hasError()) "فشل الحفظ (${event.error})"
                    else "تم الحفظ في Movies/DualCam"
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                }
                else -> {}
            }
        }
    }

    private fun setRecordingUi(on: Boolean) {
        handler.removeCallbacks(ticker)
        if (on) {
            seconds = 0
            timerText.text = "00:00"
            timerText.visibility = TextView.VISIBLE
            recordButton.text = "■ إيقاف"
            handler.postDelayed(ticker, 1000)
        } else {
            timerText.visibility = TextView.INVISIBLE
            recordButton.text = "● تسجيل"
        }
    }
}
