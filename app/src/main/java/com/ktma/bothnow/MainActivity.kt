package com.ktma.bothnow
import android.Manifest
import android.content.ContentValues
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.atan2
import kotlin.math.abs
import android.view.MotionEvent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.Crop
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.Composition
import android.media.MediaMetadataRetriever
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(androidx.media3.common.util.UnstableApi::class)
class MainActivity : AppCompatActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var cameraStage: FrameLayout
    private lateinit var frameGuideView: FrameGuideView
    private lateinit var landscapeWatermarkView: ImageView
    private lateinit var portraitWatermarkView: ImageView
    private lateinit var centerLogoButton: Button
    private lateinit var status: TextView
    private lateinit var captureButton: Button
    private lateinit var videoButton: Button
    private lateinit var switchButton: Button
    private lateinit var flashButton: Button
    private lateinit var zoomSeek: SeekBar
    private lateinit var watermarkSizeSeek: SeekBar
    private lateinit var watermarkOpacitySeek: SeekBar
    private lateinit var watermarkToggle: Switch
    private lateinit var watermarkLogoStatus: TextView
    private lateinit var uploadWatermarkButton: Button
    private lateinit var removeWatermarkButton: Button
    private lateinit var watermarkPositionButton: Button
    private lateinit var exportProgress: ProgressBar
    private lateinit var exposureSeek: SeekBar
    private lateinit var exposureLabel: TextView
    private lateinit var compositionButton: Button
    private lateinit var timerButton: Button
    private lateinit var galleryButton: Button
    private lateinit var recordingInfo: TextView
    private lateinit var audioMeterView: AudioMeterView
    private lateinit var audioMeterLabel: TextView
    private lateinit var horizonButton: Button
    private var horizonEnabled = true
    private var horizonRollDegrees = 0f
    private var sensorManager: SensorManager? = null
    private var accelerometer: Sensor? = null
    private val levelSensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            if (event.sensor.type == Sensor.TYPE_ACCELEROMETER && event.values.size >= 3) {
                val x = event.values[0]; val y = event.values[1]; val z = event.values[2]
                val measured = Math.toDegrees(atan2(x.toDouble(), z.toDouble())).toFloat()
                horizonRollDegrees = horizonRollDegrees * 0.82f + measured * 0.18f
                if (::frameGuideView.isInitialized) frameGuideView.invalidate()
            }
        }
        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }
    private lateinit var teleprompterText: TextView
    private lateinit var teleprompterScroll: ScrollView
    private lateinit var teleprompterStatus: TextView
    private lateinit var teleprompterToggleButton: Button
    private var teleprompterRunning = false
    private var teleprompterSpeed = 2
    private var teleprompterOffset = 0
    private var teleprompterTicker: Runnable? = null
    private lateinit var cameraExecutor: java.util.concurrent.ExecutorService

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var recording: Recording? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var torchEnabled = false
    private var isRecording = false
    private var isExporting = false
    private var maxZoom = 1f
    private var sourceVideo: File? = null
    private var exportTimestamp = ""
    private var exportQueue = listOf<ExportSpec>()
    private var exportIndex = 0
    private var activeTransformer: Transformer? = null
    private val prefs by lazy { getSharedPreferences("bothnow_settings", MODE_PRIVATE) }
    private val positions = listOf("Bottom right", "Bottom left", "Top right", "Top left", "Center")
    private var portraitCenterX = 0.5f
    private var portraitCenterY = 0.5f
    private var portraitGuideRect = RectF()
    private var activeWatermarkFormat = "LANDSCAPE"
    private var watermarkPosition = 0
    private var currentZoomRatio = 1f
    private var compositionMode = 1 // 0 off, 1 rule of thirds, 2 centre crosshair
    private val timerOptions = listOf(0, 3, 5, 10)
    private var photoTimerSeconds = 0
    private var countdownRemaining = 0
    private val mainHandler = Handler(Looper.getMainLooper())
    private var recordingStartedAt = 0L
    private var exposureMinIndex = 0
    private var exposureMaxIndex = 0
    private var photoCountdownRunnable: Runnable? = null
    private var recordingTicker: Runnable? = null

    private data class GalleryItem(val uri: Uri, val name: String, val mime: String, val dateAdded: Long)

    private data class ExportSpec(val label: String, val width: Int, val height: Int)

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.CAMERA] == true) startCamera()
        else showStatus("Camera permission is required. Enable it in Android Settings.")
    }
    private val audioPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        startVideoRecording(withAudio = granted)
    }
    private val watermarkPicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                prefs.edit().putString("watermark_logo_uri", uri.toString()).putBoolean("watermark_enabled", true).apply()
                updateWatermarkUi()
                updateLiveWatermark()
                showStatus("YOUR LOGO IS READY · EDIT EACH FRAME INDEPENDENTLY")
                Toast.makeText(this, "Your logo is ready. Configure each frame separately.", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                showStatus("LOGO IMPORT FAILED: ${e.localizedMessage ?: "Cannot access image"}")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        cameraExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
        if (!prefs.contains("landscape_logo_size")) {
            prefs.edit()
                .putInt("landscape_logo_size", prefs.getInt("watermark_size", 16))
                .putInt("landscape_logo_opacity", prefs.getInt("watermark_opacity", 85))
                .putFloat("landscape_logo_x", prefs.getFloat("watermark_x", 0.88f))
                .putFloat("landscape_logo_y", prefs.getFloat("watermark_y", 0.88f))
                .putInt("portrait_logo_size", 16)
                .putInt("portrait_logo_opacity", 85)
                .putFloat("portrait_logo_x", 0.88f)
                .putFloat("portrait_logo_y", 0.88f)
                .apply()
        }
        watermarkPosition = prefs.getInt("watermark_position", 0).coerceIn(-1, positions.lastIndex)
        portraitCenterX = prefs.getFloat("portrait_center_x", 0.5f)
        portraitCenterY = prefs.getFloat("portrait_center_y", 0.5f)
        compositionMode = prefs.getInt("composition_mode", 1).coerceIn(0, 2)
        photoTimerSeconds = prefs.getInt("photo_timer_seconds", 0).let { if (it in timerOptions) it else 0 }
        buildInterface()
        if (hasCameraPermission()) startCamera() else showPermissionDisclosure()
    }

    private fun buildInterface() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.rgb(7, 9, 27))
            setPadding(dp(12), dp(8), dp(12), dp(10))
        }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val logo = ImageView(this).apply { setImageResource(R.mipmap.ic_launcher); scaleType = ImageView.ScaleType.FIT_CENTER }
        header.addView(logo, LinearLayout.LayoutParams(dp(48), dp(48)))
        val brand = TextView(this).apply { text = "BothNow Camera"; setTextColor(-1); textSize = 19f; setPadding(dp(10), 0, 0, 0); typeface = android.graphics.Typeface.DEFAULT_BOLD }
        header.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        status = TextView(this).apply { text = "STARTING CAMERA"; textSize = 10f; setTextColor(android.graphics.Color.CYAN); setPadding(dp(8), dp(7), dp(8), dp(7)); setBackgroundColor(0xff19224c.toInt()) }
        header.addView(status)
        root.addView(header)

        cameraStage = FrameLayout(this).apply { setBackgroundColor(android.graphics.Color.BLACK) }
        previewView = PreviewView(this).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE; scaleType = PreviewView.ScaleType.FILL_CENTER }
        cameraStage.addView(previewView, FrameLayout.LayoutParams(-1, -1))
        frameGuideView = FrameGuideView()
        cameraStage.addView(frameGuideView, FrameLayout.LayoutParams(-1, -1))
        landscapeWatermarkView = createLiveLogoView("LANDSCAPE")
        portraitWatermarkView = createLiveLogoView("PORTRAIT")
        cameraStage.addView(landscapeWatermarkView)
        cameraStage.addView(portraitWatermarkView)
        teleprompterText = TextView(this).apply {
            text = prefs.getString("teleprompter_script", "Your script will appear here")
            setTextColor(android.graphics.Color.WHITE)
            textSize = 23f
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(24), dp(16), dp(24))
            setShadowLayer(4f, 1f, 2f, android.graphics.Color.BLACK)
        }
        teleprompterScroll = ScrollView(this).apply {
            visibility = View.GONE
            isFillViewport = true
            setBackgroundColor(0x99070b18.toInt())
            addView(teleprompterText, ScrollView.LayoutParams(-1, -2))
        }
        val prompterParams = FrameLayout.LayoutParams(-1, dp(138), Gravity.TOP).apply {
            leftMargin = dp(18); rightMargin = dp(18); topMargin = dp(12)
        }
        cameraStage.addView(teleprompterScroll, prompterParams)
        cameraStage.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            frameGuideView.invalidate()
            if (::landscapeWatermarkView.isInitialized) {
                positionLiveWatermark("LANDSCAPE", landscapeWatermarkView)
                positionLiveWatermark("PORTRAIT", portraitWatermarkView)
            }
        }
        root.addView(cameraStage, LinearLayout.LayoutParams(-1, dp(300)).apply { topMargin = dp(8); bottomMargin = dp(8) })

        val formatHint = TextView(this).apply {
            text = "ONE TAKE → LANDSCAPE 16:9 + PORTRAIT 9:16 EXPORTS"
            textSize = 11f; gravity = Gravity.CENTER; setTextColor(0xff00d9ff.toInt()); setPadding(0, dp(4), 0, dp(8))
        }
        root.addView(formatHint)
        val framingHint = TextView(this).apply {
            text = "DRAG PORTRAIT FRAME · PINCH TO ZOOM · TAP TO FOCUS"
            textSize = 10f; gravity = Gravity.CENTER; setTextColor(0xffb8c2df.toInt())
        }
        root.addView(framingHint)
        val frameCenterButton = makeButton("Center portrait frame") { portraitCenterX = 0.5f; portraitCenterY = 0.5f; prefs.edit().putFloat("portrait_center_x", portraitCenterX).putFloat("portrait_center_y", portraitCenterY).apply(); frameGuideView.invalidate() }
        root.addView(frameCenterButton, LinearLayout.LayoutParams(-1, dp(36)))

        val compositionAndTimer = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        compositionButton = makeButton("Guide: Thirds") { cycleCompositionGuide() }
        timerButton = makeButton("Photo timer: OFF") { cyclePhotoTimer() }
        compositionAndTimer.addView(compositionButton, LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginEnd = dp(5) })
        compositionAndTimer.addView(timerButton, LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginStart = dp(5) })
        horizonButton = makeButton("Horizon: ON") {
            horizonEnabled = !horizonEnabled
            horizonButton.text = if (horizonEnabled) "Horizon: ON" else "Horizon: OFF"
            frameGuideView.invalidate()
        }
        compositionAndTimer.addView(horizonButton, LinearLayout.LayoutParams(0, dp(40), 1f).apply { marginStart = dp(5) })
        root.addView(compositionAndTimer)
        updateCompositionAndTimerLabels()

        val zoomLabel = TextView(this).apply { text = "ZOOM"; textSize = 11f; setTextColor(-1) }
        root.addView(zoomLabel)
        zoomSeek = SeekBar(this).apply { max = 100; progress = 0; setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { if (fromUser) {
                currentZoomRatio = 1f + (maxZoom - 1f) * progress / 100f
                camera?.cameraControl?.setZoomRatio(currentZoomRatio)
            } }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }) }
        root.addView(zoomSeek, LinearLayout.LayoutParams(-1, dp(32)))

        exposureLabel = TextView(this).apply { text = "EXPOSURE · 0.0 EV"; textSize = 11f; setTextColor(-1) }
        root.addView(exposureLabel)
        exposureSeek = SeekBar(this).apply {
            max = 100; progress = 50
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) applyExposureFromProgress(progress)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        root.addView(exposureSeek, LinearLayout.LayoutParams(-1, dp(32)))
        val exposureReset = makeButton("Reset exposure") { setExposureIndex(0) }
        root.addView(exposureReset, LinearLayout.LayoutParams(-1, dp(34)))

        val tools = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        flashButton = makeButton("Flash off") { toggleTorch() }
        switchButton = makeButton("Flip camera") { switchCamera() }
        tools.addView(flashButton, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginEnd = dp(5) })
        tools.addView(switchButton, LinearLayout.LayoutParams(0, dp(44), 1f).apply { marginStart = dp(5) })
        root.addView(tools)

        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        captureButton = makeButton("PHOTO") { takePhoto() }
        videoButton = makeButton("●  RECORD BOTH FORMATS") { toggleVideoRecording() }
        captureButton.setBackgroundColor(0xff1d3652.toInt()); videoButton.setBackgroundColor(0xff8e1830.toInt())
        actions.addView(captureButton, LinearLayout.LayoutParams(0, dp(52), 0.75f).apply { marginEnd = dp(5); topMargin = dp(7) })
        actions.addView(videoButton, LinearLayout.LayoutParams(0, dp(52), 1.5f).apply { marginStart = dp(5); topMargin = dp(7) })
        root.addView(actions)
        recordingInfo = TextView(this).apply { text = "READY · STORAGE CHECK ENABLED"; textSize = 10f; gravity = Gravity.CENTER; setTextColor(0xffb8c2df.toInt()); setPadding(0, dp(4), 0, dp(4)) }
        root.addView(recordingInfo)
        audioMeterLabel = TextView(this).apply {
            text = "MIC INPUT · LEVEL METER ACTIVE DURING RECORDING · PEAK ALERT AT 85%"
            textSize = 9f; gravity = Gravity.CENTER; setTextColor(0xffb8c2df.toInt()); setPadding(0, dp(2), 0, dp(2))
        }
        root.addView(audioMeterLabel)
        audioMeterView = AudioMeterView()
        root.addView(audioMeterView, LinearLayout.LayoutParams(-1, dp(18)).apply { bottomMargin = dp(5) })

        val prompterTitle = TextView(this).apply {
            text = "TELEPROMPTER · READ WHILE RECORDING"
            textSize = 12f; typeface = android.graphics.Typeface.DEFAULT_BOLD
            setTextColor(0xff00d9ff.toInt()); setPadding(0, dp(10), 0, dp(4))
        }
        root.addView(prompterTitle)
        val prompterActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        val editScriptButton = makeButton("Edit script") { editTeleprompterScript() }
        teleprompterToggleButton = makeButton("Start scrolling") { toggleTeleprompter() }
        val resetScriptButton = makeButton("Reset") { resetTeleprompter() }
        val hideScriptButton = makeButton("Hide") { stopTeleprompter(); teleprompterScroll.visibility = View.GONE }
        prompterActions.addView(editScriptButton, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(4) })
        prompterActions.addView(teleprompterToggleButton, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginStart = dp(4); marginEnd = dp(4) })
        prompterActions.addView(resetScriptButton, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginStart = dp(4); marginEnd = dp(4) })
        prompterActions.addView(hideScriptButton, LinearLayout.LayoutParams(0, dp(42), 0.7f).apply { marginStart = dp(4) })
        root.addView(prompterActions)
        teleprompterStatus = TextView(this).apply {
            text = "Script is private to this device · Not burned into exported videos"
            textSize = 10f; setTextColor(0xffb8c2df.toInt()); gravity = Gravity.CENTER
        }
        root.addView(teleprompterStatus)
        val speedLabel = TextView(this).apply { text = "SCROLL SPEED"; textSize = 10f; setTextColor(-1) }
        root.addView(speedLabel)
        val speedSeek = SeekBar(this).apply {
            max = 4; progress = teleprompterSpeed
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) teleprompterSpeed = progress.coerceIn(0, 4)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        root.addView(speedSeek, LinearLayout.LayoutParams(-1, dp(30)))
        galleryButton = makeButton("Open BothNow Gallery · Preview / Share / Delete") { openBothNowGallery() }
        root.addView(galleryButton, LinearLayout.LayoutParams(-1, dp(42)).apply { topMargin = dp(4) })

        val settingsTitle = TextView(this).apply { text = "INDEPENDENT WATERMARK SETTINGS"; textSize = 12f; typeface = android.graphics.Typeface.DEFAULT_BOLD; setTextColor(0xff00d9ff.toInt()); setPadding(0, dp(10), 0, dp(4)) }
        root.addView(settingsTitle)
        val watermarkActions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        uploadWatermarkButton = makeButton("Upload my logo") { watermarkPicker.launch(arrayOf("image/png", "image/jpeg", "image/webp")) }
        removeWatermarkButton = makeButton("Remove logo") {
            prefs.edit().remove("watermark_logo_uri").putBoolean("watermark_enabled", false).apply()
            updateWatermarkUi()
            showStatus("CUSTOM WATERMARK REMOVED")
        }
        watermarkActions.addView(uploadWatermarkButton, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(5) })
        watermarkActions.addView(removeWatermarkButton, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginStart = dp(5) })
        root.addView(watermarkActions)
        watermarkLogoStatus = TextView(this).apply { textSize = 10f; setTextColor(0xffb8c2df.toInt()); setPadding(0, dp(4), 0, dp(3)) }
        root.addView(watermarkLogoStatus)
        watermarkToggle = Switch(this).apply {
            text = "Show both independent logos in their frames"; setTextColor(-1)
            setOnCheckedChangeListener { _, checked ->
                prefs.edit().putBoolean("watermark_enabled", checked).apply()
                updateLiveWatermark()
            }
        }
        root.addView(watermarkToggle)
        updateWatermarkUi()

        val profileRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        val landscapeProfileButton = makeButton("Edit landscape logo") {
            activeWatermarkFormat = "LANDSCAPE"
            refreshWatermarkControls()
            showStatus("EDITING LANDSCAPE WATERMARK")
        }
        val portraitProfileButton = makeButton("Edit portrait logo") {
            activeWatermarkFormat = "PORTRAIT"
            refreshWatermarkControls()
            showStatus("EDITING PORTRAIT WATERMARK")
        }
        profileRow.addView(landscapeProfileButton, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(5) })
        profileRow.addView(portraitProfileButton, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginStart = dp(5) })
        root.addView(profileRow)

        watermarkPositionButton = makeButton("Position: Bottom right") { cycleActiveWatermarkPosition() }
        root.addView(watermarkPositionButton, LinearLayout.LayoutParams(-1, dp(40)).apply { topMargin = dp(3) })
        centerLogoButton = makeButton("Center selected logo") {
            setActiveWatermarkPosition(0.5f, 0.5f, positions.lastIndex)
        }
        root.addView(centerLogoButton, LinearLayout.LayoutParams(-1, dp(38)))
        addWatermarkSettingSeek(root, "Selected logo size", "size", 5, 35, 16).also { watermarkSizeSeek = it }
        addWatermarkSettingSeek(root, "Selected logo opacity", "opacity", 10, 100, 85).also { watermarkOpacitySeek = it }
        exportProgress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; progress = 0; visibility = View.GONE }
        root.addView(exportProgress, LinearLayout.LayoutParams(-1, dp(8)).apply { topMargin = dp(7) })
        val note = TextView(this).apply { text = "Upload one logo, then edit Landscape and Portrait independently. Drag either preview logo to position it. Pinch the camera preview to zoom. Videos save to Movies/BothNow."; textSize = 10f; gravity = Gravity.CENTER; setTextColor(0xffb8c2df.toInt()); setPadding(0, dp(5), 0, 0) }
        root.addView(note)
        val privacyButton = makeButton("Privacy Policy") { showPrivacyPolicy() }
        root.addView(privacyButton, LinearLayout.LayoutParams(-1, dp(38)).apply { topMargin = dp(8) })
        val developer = TextView(this).apply {
            text = "Designed By: Kekeli Emmanuel\\nAll Rights Reserved By: Kekeli Tech Media Agency\\n+233 26 478 3970  |  info@ktmaonline.com\\nwww.ktmaonline.com"
            textSize = 10f; gravity = Gravity.CENTER; setTextColor(0xffb8c2df.toInt()); setPadding(0, dp(8), 0, dp(4))
        }
        root.addView(developer)
        val scrollContainer = ScrollView(this).apply {
            isFillViewport = true
            clipToPadding = false
            addView(root)
        }
        setContentView(scrollContainer)
        ViewCompat.setOnApplyWindowInsetsListener(scrollContainer) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, bars.top, 0, bars.bottom)
            insets
        }
        ViewCompat.requestApplyInsets(scrollContainer)
        updateLiveWatermark()
    }

    private fun editTeleprompterScript() {
        val editor = EditText(this).apply {
            setText(prefs.getString("teleprompter_script", ""))
            hint = "Paste or type your presentation script here"
            minLines = 6
            maxLines = 12
            gravity = Gravity.TOP or Gravity.START
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        }
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(8), dp(20), 0); addView(editor, LinearLayout.LayoutParams(-1, dp(220))) }
        AlertDialog.Builder(this)
            .setTitle("BothNow Teleprompter Script")
            .setView(container)
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Clear") { _, _ ->
                prefs.edit().remove("teleprompter_script").apply()
                teleprompterText.text = "Your script will appear here"
                resetTeleprompter()
            }
            .setPositiveButton("Save script") { _, _ ->
                val script = editor.text.toString().trim()
                prefs.edit().putString("teleprompter_script", script).apply()
                teleprompterText.text = script.ifBlank { "Your script will appear here" }
                resetTeleprompter()
                teleprompterStatus.text = "SCRIPT SAVED · ${script.length} CHARACTERS"
            }.show()
    }

    private fun toggleTeleprompter() {
        if (teleprompterRunning) {
            stopTeleprompter()
            return
        }
        if (teleprompterText.text.isNullOrBlank() || teleprompterText.text == "Your script will appear here") {
            editTeleprompterScript()
            return
        }
        teleprompterScroll.visibility = View.VISIBLE
        teleprompterRunning = true
        if (::teleprompterToggleButton.isInitialized) teleprompterToggleButton.text = "Pause"
        teleprompterStatus.text = "TELEPROMPTER RUNNING · SCRIPT IS NOT RECORDED INTO VIDEO"
        val tick = object : Runnable {
            override fun run() {
                if (!teleprompterRunning) return
                val maxScroll = (teleprompterText.height - teleprompterScroll.height).coerceAtLeast(0)
                teleprompterOffset = (teleprompterOffset + teleprompterSpeed + 1).coerceAtMost(maxScroll)
                teleprompterScroll.scrollTo(0, teleprompterOffset)
                if (teleprompterOffset >= maxScroll && maxScroll > 0) {
                    stopTeleprompter()
                } else {
                    mainHandler.postDelayed(this, 45L)
                }
            }
        }
        teleprompterTicker = tick
        mainHandler.post(tick)
    }

    private fun stopTeleprompter() {
        teleprompterRunning = false
        if (::teleprompterToggleButton.isInitialized) teleprompterToggleButton.text = "Resume scrolling"
        teleprompterTicker?.let { mainHandler.removeCallbacks(it) }
        teleprompterTicker = null
        if (::teleprompterStatus.isInitialized) teleprompterStatus.text = "TELEPROMPTER PAUSED · SCRIPT IS NOT RECORDED INTO VIDEO"
    }

    private fun resetTeleprompter() {
        stopTeleprompter()
        teleprompterOffset = 0
        if (::teleprompterScroll.isInitialized) teleprompterScroll.scrollTo(0, 0)
    }

    private fun cycleCompositionGuide() {
        compositionMode = (compositionMode + 1) % 3
        prefs.edit().putInt("composition_mode", compositionMode).apply()
        frameGuideView.invalidate()
        updateCompositionAndTimerLabels()
    }

    private fun cyclePhotoTimer() {
        val index = timerOptions.indexOf(photoTimerSeconds).coerceAtLeast(0)
        photoTimerSeconds = timerOptions[(index + 1) % timerOptions.size]
        prefs.edit().putInt("photo_timer_seconds", photoTimerSeconds).apply()
        updateCompositionAndTimerLabels()
    }

    private fun updateCompositionAndTimerLabels() {
        if (::compositionButton.isInitialized) compositionButton.text = "Guide: ${when (compositionMode) { 0 -> "OFF"; 1 -> "Thirds"; else -> "Centre" }}"
        if (::timerButton.isInitialized) timerButton.text = "Photo timer: ${if (photoTimerSeconds == 0) "OFF" else "${photoTimerSeconds}s"}"
    }

    private fun applyExposureFromProgress(progress: Int) {
        if (exposureMaxIndex <= exposureMinIndex) return
        val index = exposureMinIndex + ((exposureMaxIndex - exposureMinIndex) * progress / 100f).toInt()
        setExposureIndex(index)
    }

    private fun setExposureIndex(index: Int) {
        val active = camera ?: return
        val clamped = index.coerceIn(exposureMinIndex, exposureMaxIndex)
        active.cameraControl.setExposureCompensationIndex(clamped)
        val range = exposureMaxIndex - exposureMinIndex
        if (range > 0 && ::exposureSeek.isInitialized) exposureSeek.progress = (((clamped - exposureMinIndex).toFloat() / range) * 100f).toInt()
        updateExposureLabel(clamped)
    }

    private fun updateExposureLabel(index: Int) {
        val step = camera?.cameraInfo?.exposureState?.exposureCompensationStep?.toFloat() ?: 0f
        val ev = index * step
        if (::exposureLabel.isInitialized) exposureLabel.text = "EXPOSURE · ${if (ev >= 0) "+" else ""}${String.format(Locale.US, "%.1f", ev)} EV"
    }

    private fun startRecordingTicker() {
        stopRecordingTicker()
        val ticker = object : Runnable {
            override fun run() {
                if (!isRecording) return
                val seconds = ((android.os.SystemClock.elapsedRealtime() - recordingStartedAt) / 1000L).toInt()
                val mm = seconds / 60
                val ss = seconds % 60
                recordingInfo.text = "● RECORDING ${String.format(Locale.US, "%02d:%02d", mm, ss)}"
                mainHandler.postDelayed(this, 500L)
            }
        }
        recordingTicker = ticker
        mainHandler.post(ticker)
    }

    private fun stopRecordingTicker() {
        recordingTicker?.let { mainHandler.removeCallbacks(it) }
        recordingTicker = null
        if (::recordingInfo.isInitialized) recordingInfo.text = "READY · STORAGE CHECK ENABLED"
    }

    private fun openBothNowGallery() {
        val items = mutableListOf<GalleryItem>()
        fun collect(collection: Uri, relativePath: String) {
            val projection = arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.MIME_TYPE, MediaStore.MediaColumns.DATE_ADDED)
            contentResolver.query(collection, projection, "${MediaStore.MediaColumns.RELATIVE_PATH}=?", arrayOf(relativePath), "${MediaStore.MediaColumns.DATE_ADDED} DESC")?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    items.add(GalleryItem(ContentUris.withAppendedId(collection, id), cursor.getString(nameCol) ?: "BothNow media", cursor.getString(mimeCol) ?: "application/octet-stream", cursor.getLong(dateCol)))
                }
            }
        }
        try {
            collect(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "Movies/BothNow/")
            collect(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, "Pictures/BothNow/")
            items.sortByDescending { it.dateAdded }
        } catch (e: Exception) {
            Toast.makeText(this, "Could not read BothNow gallery: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            return
        }
        if (items.isEmpty()) {
            AlertDialog.Builder(this).setTitle("BothNow Gallery").setMessage("No BothNow photos or videos yet. Capture a photo or record a video to see it here.").setPositiveButton("Close", null).show()
            return
        }
        val labels = items.map { it.name }.toTypedArray()
        AlertDialog.Builder(this).setTitle("BothNow Gallery · ${items.size} items").setItems(labels) { _, which -> showGalleryItemActions(items[which]) }.setNegativeButton("Close", null).show()
    }

    private fun showGalleryItemActions(item: GalleryItem) {
        AlertDialog.Builder(this).setTitle(item.name).setItems(arrayOf("Preview", "Share", "Delete", "Cancel")) { _, which ->
            when (which) {
                0 -> try { startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(item.uri, item.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)) }
                    catch (_: Exception) { Toast.makeText(this, "No compatible app found to preview this file", Toast.LENGTH_LONG).show() }
                1 -> {
                    val share = Intent(Intent.ACTION_SEND).setType(item.mime).putExtra(Intent.EXTRA_STREAM, item.uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    startActivity(Intent.createChooser(share, "Share with"))
                }
                2 -> AlertDialog.Builder(this).setTitle("Delete ${item.name}?").setMessage("This permanently deletes this file from your device.")
                    .setNegativeButton("Cancel", null).setPositiveButton("Delete") { _, _ ->
                        val deleted = contentResolver.delete(item.uri, null, null)
                        Toast.makeText(this, if (deleted > 0) "Deleted" else "Could not delete this file", Toast.LENGTH_SHORT).show()
                    }.show()
            }
        }.show()
    }

    private fun watermarkKey(suffix: String, format: String = activeWatermarkFormat) =
        "${format.lowercase(Locale.US)}_logo_${suffix}"

    private fun addWatermarkSettingSeek(root: LinearLayout, label: String, suffix: String, min: Int, maxValue: Int, default: Int): SeekBar {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val title = TextView(this).apply { text = label; textSize = 11f; setTextColor(-1) }
        row.addView(title, LinearLayout.LayoutParams(dp(145), -2))
        val seek = SeekBar(this).apply {
            max = maxValue - min
            progress = (prefs.getInt(watermarkKey(suffix), default) - min).coerceIn(0, max)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                    if (fromUser) {
                        prefs.edit().putInt(watermarkKey(suffix), p + min).apply()
                        updateLiveWatermark()
                    }
                }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        row.addView(seek, LinearLayout.LayoutParams(0, dp(34), 1f))
        root.addView(row)
        return seek
    }

    private fun refreshWatermarkControls() {
        if (!::watermarkPositionButton.isInitialized) return
        val x = prefs.getFloat(watermarkKey("x"), 0.88f)
        val y = prefs.getFloat(watermarkKey("y"), 0.88f)
        watermarkPositionButton.text = "Position: ${positionNameFor(x, y)}"
        if (::watermarkSizeSeek.isInitialized) watermarkSizeSeek.progress =
            (prefs.getInt(watermarkKey("size"), 16) - 5).coerceIn(0, watermarkSizeSeek.max)
        if (::watermarkOpacitySeek.isInitialized) watermarkOpacitySeek.progress =
            (prefs.getInt(watermarkKey("opacity"), 85) - 10).coerceIn(0, watermarkOpacitySeek.max)
    }

    private fun positionNameFor(x: Float, y: Float): String = when {
        kotlin.math.abs(x - 0.5f) < 0.08f && kotlin.math.abs(y - 0.5f) < 0.08f -> "Center"
        x > 0.5f && y > 0.5f -> "Bottom right"
        x <= 0.5f && y > 0.5f -> "Bottom left"
        x > 0.5f -> "Top right"
        else -> "Top left"
    }

    private fun setActiveWatermarkPosition(x: Float, y: Float, index: Int = -1) {
        prefs.edit().putFloat(watermarkKey("x"), x).putFloat(watermarkKey("y"), y).apply()
        watermarkPosition = index
        refreshWatermarkControls()
        updateLiveWatermark()
    }

    private fun cycleActiveWatermarkPosition() {
        val current = positionNameFor(prefs.getFloat(watermarkKey("x"), 0.88f), prefs.getFloat(watermarkKey("y"), 0.88f))
        val next = (positions.indexOf(current).coerceAtLeast(0) + 1) % positions.size
        val point = when (next) {
            0 -> 0.88f to 0.88f
            1 -> 0.12f to 0.88f
            2 -> 0.88f to 0.12f
            3 -> 0.12f to 0.12f
            else -> 0.5f to 0.5f
        }
        setActiveWatermarkPosition(point.first, point.second, next)
    }

    private fun createLiveLogoView(format: String): ImageView = ImageView(this).apply {
        scaleType = ImageView.ScaleType.FIT_CENTER
        visibility = View.GONE
        contentDescription = "$format video watermark. Drag to reposition."
        setOnTouchListener { view, event ->
            val frame = if (format == "PORTRAIT") frameGuideView.currentPortraitRect() else frameGuideView.currentLandscapeRect()
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    view.parent?.requestDisallowInterceptTouchEvent(true)
                    view.tag = floatArrayOf(event.x, event.y)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val offset = view.tag as? FloatArray
                    if (offset != null) {
                        val stageLocation = IntArray(2)
                        cameraStage.getLocationOnScreen(stageLocation)
                        val stageX = event.rawX - stageLocation[0]
                        val stageY = event.rawY - stageLocation[1]
                        view.x = (stageX - offset[0]).coerceIn(frame.left, (frame.right - view.width).coerceAtLeast(frame.left))
                        view.y = (stageY - offset[1]).coerceIn(frame.top, (frame.bottom - view.height).coerceAtLeast(frame.top))
                        saveWatermarkPositionFromView(format, view)
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    saveWatermarkPositionFromView(format, view)
                    view.parent?.requestDisallowInterceptTouchEvent(false)
                    true
                }
                MotionEvent.ACTION_CANCEL -> { view.parent?.requestDisallowInterceptTouchEvent(false); true }
                else -> true
            }
        }
    }

    private fun saveWatermarkPositionFromView(format: String, view: View) {
        val frame = if (format == "PORTRAIT") frameGuideView.currentPortraitRect() else frameGuideView.currentLandscapeRect()
        if (frame.width() <= 0f || frame.height() <= 0f) return
        val x = ((view.x + view.width / 2f - frame.left) / frame.width()).coerceIn(0.02f, 0.98f)
        val y = ((view.y + view.height / 2f - frame.top) / frame.height()).coerceIn(0.02f, 0.98f)
        prefs.edit().putFloat("${format.lowercase(Locale.US)}_logo_x", x)
            .putFloat("${format.lowercase(Locale.US)}_logo_y", y).apply()
        if (format == activeWatermarkFormat) refreshWatermarkControls()
    }

    private fun positionLiveWatermark(format: String, view: ImageView) {
        if (cameraStage.width <= 0) return
        val frame = if (format == "PORTRAIT") frameGuideView.currentPortraitRect() else frameGuideView.currentLandscapeRect()
        val percent = prefs.getInt("${format.lowercase(Locale.US)}_logo_size", 16).coerceIn(5, 35) / 100f
        val width = (frame.width() * percent).toInt().coerceAtLeast(dp(28))
        val drawable = view.drawable
        val ratio = if (drawable != null && drawable.intrinsicWidth > 0) drawable.intrinsicHeight.toFloat() / drawable.intrinsicWidth else 0.35f
        val height = (width * ratio).toInt().coerceAtLeast(dp(18))
        val params = view.layoutParams ?: FrameLayout.LayoutParams(width, height)
        params.width = width
        params.height = height
        view.layoutParams = params
        val x = prefs.getFloat("${format.lowercase(Locale.US)}_logo_x", 0.88f)
        val y = prefs.getFloat("${format.lowercase(Locale.US)}_logo_y", 0.88f)
        view.x = (frame.left + frame.width() * x - width / 2f).coerceIn(frame.left, (frame.right - width).coerceAtLeast(frame.left))
        view.y = (frame.top + frame.height() * y - height / 2f).coerceIn(frame.top, (frame.bottom - height).coerceAtLeast(frame.top))
        view.alpha = prefs.getInt("${format.lowercase(Locale.US)}_logo_opacity", 85).coerceIn(10, 100) / 100f
    }

    private fun updateLiveWatermark() {
        if (!::landscapeWatermarkView.isInitialized) return
        val uriText = prefs.getString("watermark_logo_uri", null)
        val enabled = prefs.getBoolean("watermark_enabled", false) && !uriText.isNullOrBlank()
        if (!enabled) {
            landscapeWatermarkView.visibility = View.GONE
            portraitWatermarkView.visibility = View.GONE
            return
        }
        try {
            val bitmap = contentResolver.openInputStream(Uri.parse(uriText!!))?.use { BitmapFactory.decodeStream(it) }
            if (bitmap != null) {
                landscapeWatermarkView.setImageBitmap(bitmap)
                portraitWatermarkView.setImageBitmap(bitmap)
                landscapeWatermarkView.visibility = View.VISIBLE
                portraitWatermarkView.visibility = View.VISIBLE
                positionLiveWatermark("LANDSCAPE", landscapeWatermarkView)
                positionLiveWatermark("PORTRAIT", portraitWatermarkView)
                refreshWatermarkControls()
            } else {
                landscapeWatermarkView.visibility = View.GONE
                portraitWatermarkView.visibility = View.GONE
            }
        } catch (_: Exception) {
            landscapeWatermarkView.visibility = View.GONE
            portraitWatermarkView.visibility = View.GONE
        }
    }

    private fun makeButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label; textSize = 11f; isAllCaps = false; setTextColor(-1); setOnClickListener { action() }
    }

    private fun startCamera() {
        if (!hasCameraPermission()) { permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA)); return }
        showStatus("OPENING CAMERA…")
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try { cameraProvider = future.get(); bindCameraUseCases() }
            catch (e: Exception) { showStatus("CAMERA ERROR: ${e.localizedMessage ?: e.javaClass.simpleName}") }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases() {
        val provider = cameraProvider ?: return
        val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        if (!provider.hasCamera(selector)) {
            showStatus(if (lensFacing == CameraSelector.LENS_FACING_FRONT) "FRONT CAMERA UNAVAILABLE" else "BACK CAMERA UNAVAILABLE")
            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
            return
        }
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        imageCapture = ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()
        val recorder = Recorder.Builder().setQualitySelector(QualitySelector.from(Quality.FHD, FallbackStrategy.lowerQualityOrHigherThan(Quality.SD))).build()
        videoCapture = VideoCapture.withOutput(recorder)
        try {
            provider.unbindAll()
            camera = provider.bindToLifecycle(this, selector, preview, imageCapture, videoCapture)
            maxZoom = camera?.cameraInfo?.zoomState?.value?.maxZoomRatio ?: 1f
            currentZoomRatio = 1f
            zoomSeek.progress = 0; torchEnabled = false
            val exposureRange = camera?.cameraInfo?.exposureState?.exposureCompensationRange
            exposureMinIndex = exposureRange?.lower ?: 0
            exposureMaxIndex = exposureRange?.upper ?: 0
            exposureSeek.isEnabled = exposureMinIndex != exposureMaxIndex
            exposureSeek.progress = if (exposureMaxIndex > exposureMinIndex) {
                (((0 - exposureMinIndex).toFloat() / (exposureMaxIndex - exposureMinIndex)) * 100f).toInt().coerceIn(0, 100)
            } else 50
            updateExposureLabel(0)
            flashButton.text = if (camera?.cameraInfo?.hasFlashUnit() == true) "Flash off" else "No flash"
            flashButton.isEnabled = camera?.cameraInfo?.hasFlashUnit() == true
            showStatus(if (lensFacing == CameraSelector.LENS_FACING_BACK) "BACK CAMERA READY" else "FRONT CAMERA READY")
        } catch (e: Exception) { showStatus("CAMERA START FAILED: ${e.localizedMessage ?: e.javaClass.simpleName}") }
    }

    private fun takePhoto() {
        if (photoTimerSeconds > 0) {
            beginPhotoCountdown()
            return
        }
        capturePhotoNow()
    }

    private fun beginPhotoCountdown() {
        photoCountdownRunnable?.let { mainHandler.removeCallbacks(it) }
        countdownRemaining = photoTimerSeconds
        captureButton.isEnabled = false
        val countdown = object : Runnable {
            override fun run() {
                if (countdownRemaining <= 0) {
                    captureButton.isEnabled = true
                    photoCountdownRunnable = null
                    capturePhotoNow()
                } else {
                    showStatus("PHOTO IN $countdownRemaining…")
                    recordingInfo.text = "PHOTO TIMER · $countdownRemaining"
                    countdownRemaining--
                    mainHandler.postDelayed(this, 1000L)
                }
            }
        }
        photoCountdownRunnable = countdown
        mainHandler.post(countdown)
    }

    private fun capturePhotoNow() {
        val capture = imageCapture ?: run { showStatus("CAMERA NOT READY"); return }
        val name = "BothNow-${timestamp()}.jpg"
        val values = ContentValues().apply { put(MediaStore.Images.Media.DISPLAY_NAME, name); put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg"); put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/BothNow") }
        val output = ImageCapture.OutputFileOptions.Builder(contentResolver, MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values).build()
        captureButton.isEnabled = false; showStatus("CAPTURING PHOTO…")
        capture.takePicture(output, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(result: ImageCapture.OutputFileResults) { captureButton.isEnabled = true; showStatus("PHOTO SAVED · PICTURES/BOTHNOW"); Toast.makeText(this@MainActivity, "Photo saved to Pictures/BothNow", Toast.LENGTH_SHORT).show() }
            override fun onError(exception: ImageCaptureException) { captureButton.isEnabled = true; showStatus("PHOTO ERROR: ${exception.message ?: "Capture failed"}") }
        })
    }

    private fun toggleVideoRecording() {
        if (isExporting) { Toast.makeText(this, "Please wait for both exports to finish", Toast.LENGTH_SHORT).show(); return }
        if (isRecording) { recording?.stop(); return }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startVideoRecording(true)
        else audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun startVideoRecording(withAudio: Boolean) {
        val availableBytes = StatFs(filesDir.absolutePath).availableBytes
        if (availableBytes < 250L * 1024L * 1024L) {
            AlertDialog.Builder(this).setTitle("Low storage space")
                .setMessage("Less than 250 MB is available. Dual-format recording and export may fail. Free some storage before recording.")
                .setPositiveButton("Continue anyway") { _, _ -> startVideoRecordingAfterStorageCheck(withAudio) }
                .setNegativeButton("Cancel", null).show()
            return
        }
        startVideoRecordingAfterStorageCheck(withAudio)
    }

    private fun startVideoRecordingAfterStorageCheck(withAudio: Boolean) {
        val capture = videoCapture ?: run { showStatus("CAMERA NOT READY"); return }
        val raw = File(cacheDir, "BothNow_source_${timestamp()}.mp4")
        sourceVideo = raw
        val output = FileOutputOptions.Builder(raw).build()
        var pending = capture.output.prepareRecording(this, output)
        if (withAudio) pending = pending.withAudioEnabled()
        recording = pending.start(ContextCompat.getMainExecutor(this)) { event ->
            when (event) {
                is VideoRecordEvent.Start -> {
                    isRecording = true; recordingStartedAt = android.os.SystemClock.elapsedRealtime()
                    videoButton.text = "■  STOP RECORDING"
                    showStatus(if (withAudio) "RECORDING MASTER + AUDIO" else "RECORDING MASTER · NO AUDIO")
                    startRecordingTicker()
                }
                is VideoRecordEvent.Status -> {
                    val audioStats = event.recordingStats.audioStats
                    val amplitude = audioStats.audioAmplitude.toFloat().coerceIn(0f, 1f)
                    audioMeterView.setAmplitude(amplitude)
                    audioMeterLabel.text = if (withAudio && audioStats.audioState == androidx.camera.video.AudioStats.AUDIO_STATE_ACTIVE) {
                        if (amplitude >= 0.85f) "AUDIO PEAK · LOWER INPUT LEVEL · ${(amplitude * 100).toInt()}%"
                        else "MIC LIVE · ${(amplitude * 100).toInt()}% · GREEN / YELLOW / RED"
                    } else if (withAudio) "MIC NOT ACTIVE · CHECK MICROPHONE PERMISSION / OTHER APPS"
                    else "VIDEO ONLY · MICROPHONE NOT ENABLED"
                }
                is VideoRecordEvent.Finalize -> {
                    isRecording = false; recording = null; audioMeterView.setAmplitude(0f); audioMeterLabel.text = "MIC INPUT · LEVEL METER ACTIVE DURING RECORDING · PEAK ALERT AT 85%"; videoButton.text = "●  RECORD BOTH FORMATS"
                    stopRecordingTicker()
                    if (event.hasError()) { showStatus("RECORDING ERROR: ${event.error}"); raw.delete() }
                    else { showStatus("RECORDING COMPLETE · PREPARING TWO FORMATS"); beginDualExport(raw) }
                }
            }
        }
    }

    private fun beginDualExport(raw: File) {
        if (!raw.exists() || raw.length() == 0L) { showStatus("EXPORT ERROR: SOURCE VIDEO IS EMPTY"); return }
        isExporting = true; exportProgress.visibility = View.VISIBLE; exportProgress.progress = 0
        exportTimestamp = timestamp()
        exportQueue = listOf(ExportSpec("LANDSCAPE", 1920, 1080), ExportSpec("PORTRAIT", 1080, 1920))
        exportIndex = 0
        exportNext(raw)
    }

    private fun exportNext(raw: File) {
        if (exportIndex >= exportQueue.size) {
            isExporting = false; activeTransformer = null; exportProgress.visibility = View.GONE
            raw.delete(); showStatus("BOTH VIDEOS SAVED · MOVIES/BOTHNOW")
            Toast.makeText(this, "BothNow saved Landscape + Portrait videos to Movies/BothNow", Toast.LENGTH_LONG).show()
            return
        }
        val spec = exportQueue[exportIndex]
        showStatus("EXPORTING ${spec.label} ${if (spec.width > spec.height) "16:9" else "9:16"}…")
        try {
            val meta = MediaMetadataRetriever()
            meta.setDataSource(this, Uri.fromFile(raw))
            var sourceW = meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1920
            var sourceH = meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 1080
            val rotation = meta.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            meta.release()
            if (rotation == 90 || rotation == 270) { val t = sourceW; sourceW = sourceH; sourceH = t }
            val sourceAspect = sourceW.toFloat() / sourceH.toFloat()
            val targetAspect = spec.width.toFloat() / spec.height.toFloat()
            val halfW: Float; val halfH: Float
            if (sourceAspect > targetAspect) { halfW = targetAspect / sourceAspect; halfH = 1f }
            else { halfW = 1f; halfH = sourceAspect / targetAspect }
            val viewAspect = if (cameraStage.height > 0) cameraStage.width.toFloat() / cameraStage.height else sourceAspect
            val visibleHalfW = if (viewAspect < sourceAspect) viewAspect / sourceAspect else 1f
            val visibleHalfH = if (viewAspect > sourceAspect) sourceAspect / viewAspect else 1f
            val followGuide = spec.label == "PORTRAIT"
            val cropCenterX = if (followGuide) (((portraitCenterX * 2f) - 1f) * visibleHalfW).coerceIn(-(1f - halfW), 1f - halfW) else 0f
            val cropCenterY = if (followGuide) (((1f - portraitCenterY * 2f) * visibleHalfH)).coerceIn(-(1f - halfH), 1f - halfH) else 0f
            val crop = Crop(-halfW + cropCenterX, halfW + cropCenterX, -halfH + cropCenterY, halfH + cropCenterY)
            val effects = mutableListOf<androidx.media3.common.Effect>(crop, Presentation.createForWidthAndHeight(spec.width, spec.height, Presentation.LAYOUT_SCALE_TO_FIT))
            if (prefs.getBoolean("watermark_enabled", false) && prefs.getString("watermark_logo_uri", null) != null) {
                effects.add(createLogoOverlay(spec))
            }
            val mediaItem = MediaItem.fromUri(Uri.fromFile(raw))
            val edited = EditedMediaItem.Builder(mediaItem).setEffects(Effects(emptyList(), effects)).build()
            val out = File(cacheDir, "${spec.label.lowercase(Locale.US)}_${exportTimestamp}.mp4")
            val listener = object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    runOnUiThread {
                        try { saveExportToGallery(out, "BothNow_${spec.label}_${exportTimestamp}.mp4") }
                        catch (e: Exception) { out.delete(); failExport("${spec.label} SAVE FAILED: ${e.localizedMessage}"); return@runOnUiThread }
                        out.delete(); exportIndex++; exportProgress.progress = exportIndex * 50
                        exportNext(raw)
                    }
                }
                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    runOnUiThread { out.delete(); failExport("${spec.label} EXPORT FAILED: ${exportException.localizedMessage ?: "Media3 could not export this video"}") }
                }
            }
            activeTransformer = Transformer.Builder(this).setVideoMimeType(MimeTypes.VIDEO_H264).setAudioMimeType(MimeTypes.AUDIO_AAC).addListener(listener).build()
            activeTransformer!!.start(edited, out.absolutePath)
        } catch (e: Exception) { failExport("${spec.label} EXPORT FAILED: ${e.localizedMessage ?: e.javaClass.simpleName}") }
    }

    private fun updateWatermarkUi() {
        if (!::watermarkToggle.isInitialized) return
        val logoUri = prefs.getString("watermark_logo_uri", null)
        val hasLogo = !logoUri.isNullOrBlank()
        watermarkLogoStatus.text = if (hasLogo) "LOGO SELECTED · TWO INDEPENDENT PREVIEWS AND EXPORT SETTINGS"
            else "NO CUSTOM LOGO SELECTED · UPLOAD A PNG/JPG/WEBP LOGO"
        watermarkToggle.isEnabled = hasLogo
        watermarkToggle.isChecked = hasLogo && prefs.getBoolean("watermark_enabled", true)
        removeWatermarkButton.isEnabled = hasLogo
        if (::landscapeWatermarkView.isInitialized) updateLiveWatermark()
    }

    private fun createLogoOverlay(spec: ExportSpec): OverlayEffect {
        val logoUri = prefs.getString("watermark_logo_uri", null)
            ?: throw IllegalStateException("Upload your own watermark logo first")
        val original = contentResolver.openInputStream(Uri.parse(logoUri))?.use { BitmapFactory.decodeStream(it) }
            ?: throw IllegalStateException("Could not read your uploaded watermark image")
        val format = spec.label.lowercase(Locale.US)
        val percent = prefs.getInt("${format}_logo_size", 16).coerceIn(5, 35) / 100f
        val targetW = (spec.width * percent).toInt().coerceAtLeast(32)
        val targetH = (original.height * (targetW.toFloat() / original.width)).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(original, targetW, targetH, true)
        if (scaled !== original) original.recycle()
        val alpha = prefs.getInt("${format}_logo_opacity", 85).coerceIn(10, 100) / 100f
        val x = prefs.getFloat("${format}_logo_x", 0.88f).coerceIn(0.02f, 0.98f)
        val y = prefs.getFloat("${format}_logo_y", 0.88f).coerceIn(0.02f, 0.98f)
        val settings = StaticOverlaySettings.Builder()
            .setOverlayFrameAnchor(x * 2f - 1f, 1f - y * 2f)
            .setBackgroundFrameAnchor(x * 2f - 1f, 1f - y * 2f)
            .setAlphaScale(alpha)
            .build()
        return OverlayEffect(listOf(BitmapOverlay.createStaticBitmapOverlay(scaled, settings)))
    }

    private fun showPermissionDisclosure() {
        AlertDialog.Builder(this)
            .setTitle("Camera and microphone access")
            .setMessage("BothNow Camera uses the camera to take photos and record videos. Microphone access is requested when you start recording so the video can include sound; if you deny it, the video is recorded without audio. Your recordings and uploaded logo are processed and saved on your device; this app does not upload them to a BothNow server.")
            .setNegativeButton("Not now") { _, _ -> showStatus("CAMERA PERMISSION NOT GRANTED") }
            .setPositiveButton("Continue") { _, _ -> permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA)) }
            .setCancelable(false)
            .show()
    }

    private fun showPrivacyPolicy() {
        val body = """BothNow Camera — Privacy Policy
Effective date: 22 November 2026

Developer: Kekeli Tech Media Agency
Contact: +233 26 478 3970 | info@ktmaonline.com
Website: https://www.ktmaonline.com

1. Information and permissions
The app requests camera access to capture photos and videos. It requests microphone access when you start recording so the video can include sound. You may deny microphone access and record without audio. The app does not request contacts, location, SMS, or account credentials.

2. On-device processing
Camera and microphone data are used to create the photo or video you request. The original recording is temporarily processed on your device to create landscape and portrait exports. Finished media is saved to your device's Pictures/BothNow or Movies/BothNow folders. The temporary source file is deleted after successful export.

3. Uploaded logo
If you choose a logo from your device, BothNow uses that selected image to display a live preview and add a watermark to exported videos. The selected logo reference and watermark preferences are stored locally on your device until you remove or replace them.

4. Collection and sharing
The app is designed not to transmit camera footage, microphone audio, selected logos, or personal information to the developer. The current app build contains no advertising or analytics SDK. Media is not shared by the app; you decide whether to share files using other apps on your device.

5. Retention and deletion
Photos and finished videos remain on your device until you delete them. You can remove your selected watermark in the app settings. Unfinished temporary recording files are removed after successful export; if an export fails, a temporary file may remain in app storage until Android clears it or the app is reinstalled.

6. Security and children's privacy
Use a device lock and keep your device software up to date. The app is a general camera utility and is not designed to collect information from children.

7. Changes and contact
This policy may be updated when app functionality changes. For privacy questions, contact info@ktmaonline.com or +233 26 478 3970.
"""
        val content = TextView(this).apply { text = body; textSize = 14f; setTextColor(0xff172033.toInt()); setPadding(dp(18), dp(14), dp(18), dp(14)) }
        val scroll = ScrollView(this).apply { addView(content) }
        AlertDialog.Builder(this).setTitle("Privacy Policy").setView(scroll).setPositiveButton("Close", null).show()
    }

    private fun focusAt(x: Float, y: Float) {
        val activeCamera = camera ?: return
        val point = previewView.meteringPointFactory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
            .setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS).build()
        activeCamera.cameraControl.startFocusAndMetering(action)
        showStatus("FOCUSING…")
    }

    private inner class AudioMeterView : View(this@MainActivity) {
        private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private var amplitude = 0f
        fun setAmplitude(value: Float) { amplitude = value.coerceIn(0f, 1f); invalidate() }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat(); val h = height.toFloat()
            barPaint.color = 0xff26304b.toInt(); canvas.drawRoundRect(0f, 0f, w, h, h / 2, h / 2, barPaint)
            val segments = 30; val gap = dp(2).toFloat(); val segmentW = (w - gap * (segments - 1)) / segments
            val lit = (amplitude * segments).toInt()
            for (i in 0 until segments) {
                barPaint.color = when { i < 22 -> 0xff20d080.toInt(); i < 27 -> 0xffffc857.toInt(); else -> 0xffff4058.toInt() }
                if (i >= lit) barPaint.alpha = 65 else barPaint.alpha = 255
                val left = i * (segmentW + gap)
                canvas.drawRoundRect(left, 0f, left + segmentW, h, h / 2, h / 2, barPaint)
            }
            barPaint.alpha = 255
        }
    }

    private inner class FrameGuideView : View(this@MainActivity) {
        private val landscapePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xff00d9ff.toInt(); style = Paint.Style.STROKE; strokeWidth = dp(2).toFloat() }
        private val portraitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xffffc857.toInt(); style = Paint.Style.STROKE; strokeWidth = dp(2).toFloat(); pathEffect = android.graphics.DashPathEffect(floatArrayOf(dp(8).toFloat(), dp(5).toFloat()), 0f) }
        private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = -1; textSize = dp(10).toFloat(); setShadowLayer(3f, 1f, 1f, android.graphics.Color.BLACK) }
        private var draggingPortrait = false
        private var pinching = false
        private val scaleDetector = android.view.ScaleGestureDetector(this@MainActivity,
            object : android.view.ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: android.view.ScaleGestureDetector): Boolean {
                    val activeCamera = camera ?: return false
                    val state = activeCamera.cameraInfo.zoomState.value
                    val minRatio = state?.minZoomRatio ?: 1f
                    val maxRatioNow = state?.maxZoomRatio ?: maxZoom
                    currentZoomRatio = (currentZoomRatio * detector.scaleFactor).coerceIn(minRatio, maxRatioNow)
                    activeCamera.cameraControl.setZoomRatio(currentZoomRatio)
                    zoomSeek.progress = if (maxZoom > 1f) (((currentZoomRatio - 1f) / (maxZoom - 1f)) * 100f).toInt().coerceIn(0, 100) else 0
                    return true
                }
            })

        fun currentPortraitRect(): RectF = calculatePortraitRect()

        fun currentLandscapeRect(): RectF {
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return RectF()
            val landscapeW = minOf(w * 0.92f, h * 16f / 9f)
            val landscapeH = landscapeW * 9f / 16f
            val lx = (w - landscapeW) / 2f
            val ly = (h - landscapeH) / 2f
            return RectF(lx, ly, lx + landscapeW, ly + landscapeH)
        }

        private fun calculatePortraitRect(): RectF {
            val w = width.toFloat(); val h = height.toFloat()
            if (w <= 0f || h <= 0f) return RectF()
            val frameW = minOf(w * 0.42f, h * 9f / 16f)
            val frameH = frameW * 16f / 9f
            val cx = (portraitCenterX * w).coerceIn(frameW / 2f, w - frameW / 2f)
            val cy = (portraitCenterY * h).coerceIn(frameH / 2f, h - frameH / 2f)
            return RectF(cx - frameW / 2f, cy - frameH / 2f, cx + frameW / 2f, cy + frameH / 2f)
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val landscapeW = minOf(width * 0.92f, height * 16f / 9f)
            val landscapeH = landscapeW * 9f / 16f
            val lx = (width - landscapeW) / 2f
            val ly = (height - landscapeH) / 2f
            canvas.drawRect(lx, ly, lx + landscapeW, ly + landscapeH, landscapePaint)
            val portrait = calculatePortraitRect()
            portraitGuideRect = portrait
            canvas.drawRoundRect(portrait, dp(5).toFloat(), dp(5).toFloat(), portraitPaint)
            if (horizonEnabled && width > 0 && height > 0) {
                val horizonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = if (abs(horizonRollDegrees) < 2.0f) 0xff20e080.toInt() else 0xffffc857.toInt()
                    strokeWidth = dp(2).toFloat(); style = Paint.Style.STROKE
                    setShadowLayer(3f, 1f, 1f, android.graphics.Color.BLACK)
                }
                val cx = width / 2f; val cy = height * 0.78f
                val half = width * 0.16f
                val angle = Math.toRadians((-horizonRollDegrees).toDouble())
                val dx = (half * kotlin.math.cos(angle)).toFloat()
                val dy = (half * kotlin.math.sin(angle)).toFloat()
                canvas.drawLine(cx - dx, cy - dy, cx + dx, cy + dy, horizonPaint)
                canvas.drawCircle(cx, cy, dp(4).toFloat(), horizonPaint)
                canvas.drawText("LEVEL ${String.format(Locale.US, "%.1f", horizonRollDegrees)}°", cx + dp(8), cy - dp(8).toFloat(), labelPaint)
            }
            if (compositionMode != 0) {
                val guidePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x88ffffff.toInt(); style = Paint.Style.STROKE; strokeWidth = dp(1).toFloat(); pathEffect = android.graphics.DashPathEffect(floatArrayOf(dp(4).toFloat(), dp(4).toFloat()), 0f) }
                if (compositionMode == 1) {
                    for (fraction in listOf(1f / 3f, 2f / 3f)) {
                        val gx = lx + landscapeW * fraction
                        val gy = ly + landscapeH * fraction
                        canvas.drawLine(gx, ly, gx, ly + landscapeH, guidePaint)
                        canvas.drawLine(lx, gy, lx + landscapeW, gy, guidePaint)
                        val px = portrait.left + portrait.width() * fraction
                        val py = portrait.top + portrait.height() * fraction
                        canvas.drawLine(px, portrait.top, px, portrait.bottom, guidePaint)
                        canvas.drawLine(portrait.left, py, portrait.right, py, guidePaint)
                    }
                } else {
                    canvas.drawLine(lx + landscapeW / 2f, ly, lx + landscapeW / 2f, ly + landscapeH, guidePaint)
                    canvas.drawLine(lx, ly + landscapeH / 2f, lx + landscapeW, ly + landscapeH / 2f, guidePaint)
                    canvas.drawLine(portrait.centerX(), portrait.top, portrait.centerX(), portrait.bottom, guidePaint)
                    canvas.drawLine(portrait.left, portrait.centerY(), portrait.right, portrait.centerY(), guidePaint)
                }
            }
            canvas.drawText("LANDSCAPE 16:9", lx + dp(5), (ly + dp(14)).coerceAtMost(height - dp(4).toFloat()), labelPaint)
            canvas.drawText("PORTRAIT 9:16 · DRAG", portrait.left + dp(4), (portrait.top + dp(14)).coerceAtMost(height - dp(4).toFloat()), labelPaint)
            canvas.drawCircle(portrait.centerX(), portrait.centerY(), dp(4).toFloat(), portraitPaint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            scaleDetector.onTouchEvent(event)
            if (event.pointerCount > 1 || scaleDetector.isInProgress) {
                pinching = true
                draggingPortrait = false
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    draggingPortrait = portraitGuideRect.contains(event.x, event.y)
                    if (draggingPortrait) parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
                MotionEvent.ACTION_MOVE -> if (draggingPortrait) {
                    val w = width.toFloat().coerceAtLeast(1f); val h = height.toFloat().coerceAtLeast(1f)
                    val frameW = minOf(w * 0.42f, h * 9f / 16f)
                    val frameH = frameW * 16f / 9f
                    portraitCenterX = (event.x / w).coerceIn(frameW / (2f * w), 1f - frameW / (2f * w))
                    portraitCenterY = (event.y / h).coerceIn(frameH / (2f * h), 1f - frameH / (2f * h))
                    prefs.edit().putFloat("portrait_center_x", portraitCenterX).putFloat("portrait_center_y", portraitCenterY).apply()
                    invalidate()
                    positionLiveWatermark("LANDSCAPE", landscapeWatermarkView)
                    positionLiveWatermark("PORTRAIT", portraitWatermarkView)
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (!pinching && !draggingPortrait) focusAt(event.x, event.y)
                    else if (draggingPortrait) showStatus("PORTRAIT FRAME POSITION UPDATED")
                    draggingPortrait = false
                    pinching = false
                    parent?.requestDisallowInterceptTouchEvent(false)
                    return true
                }
                MotionEvent.ACTION_CANCEL -> { draggingPortrait = false; pinching = false; parent?.requestDisallowInterceptTouchEvent(false); return true }
            }
            return true
        }
    }

    private fun saveExportToGallery(file: File, displayName: String) {
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/BothNow")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("Could not create Gallery video entry")
        try {
            contentResolver.openOutputStream(uri)?.use { output -> FileInputStream(file).use { input -> input.copyTo(output) } }
                ?: throw IllegalStateException("Could not open Gallery output")
            val done = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
            contentResolver.update(uri, done, null, null)
        } catch (e: Exception) { contentResolver.delete(uri, null, null); throw e }
    }

    private fun failExport(message: String) {
        isExporting = false; activeTransformer = null; exportProgress.visibility = View.GONE
        sourceVideo?.delete(); showStatus(message)
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun switchCamera() {
        if (isRecording || isExporting) return
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
        bindCameraUseCases()
    }
    private fun toggleTorch() {
        val info = camera?.cameraInfo ?: return
        if (!info.hasFlashUnit()) { showStatus("FLASH NOT AVAILABLE"); return }
        torchEnabled = !torchEnabled; camera?.cameraControl?.enableTorch(torchEnabled)
        flashButton.text = if (torchEnabled) "Flash on" else "Flash off"
    }
    private fun hasCameraPermission() = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    private fun timestamp() = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
    private fun showStatus(message: String) { if (::status.isInitialized) status.text = message }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onResume() {
        super.onResume()
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        accelerometer?.let { sensorManager?.registerListener(levelSensorListener, it, SensorManager.SENSOR_DELAY_UI) }
    }

    override fun onPause() {
        sensorManager?.unregisterListener(levelSensorListener)
        super.onPause()
    }

    override fun onDestroy() {
        photoCountdownRunnable?.let { mainHandler.removeCallbacks(it) }
        stopRecordingTicker()
        if (isRecording) recording?.stop()
        activeTransformer?.cancel()
        cameraProvider?.unbindAll()
        if (::cameraExecutor.isInitialized) cameraExecutor.shutdown()
        super.onDestroy()
    }
}
