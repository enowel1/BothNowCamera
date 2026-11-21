package com.ktma.bothnow
import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
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
    private val positions = listOf("Bottom right", "Bottom left", "Top right", "Top left")
    private var watermarkPosition = 0

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
                showStatus("YOUR LOGO IS READY FOR BOTH VIDEO EXPORTS")
                Toast.makeText(this, "Your logo will appear on both exported videos", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                showStatus("LOGO IMPORT FAILED: ${e.localizedMessage ?: "Cannot access image"}")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()
        watermarkPosition = prefs.getInt("watermark_position", 0).coerceIn(0, 3)
        buildInterface()
        if (hasCameraPermission()) startCamera() else permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
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

        val stage = FrameLayout(this).apply { setBackgroundColor(android.graphics.Color.BLACK) }
        previewView = PreviewView(this).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE; scaleType = PreviewView.ScaleType.FILL_CENTER }
        stage.addView(previewView, FrameLayout.LayoutParams(-1, -1))
        root.addView(stage, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = dp(8); bottomMargin = dp(8) })

        val formatHint = TextView(this).apply {
            text = "ONE TAKE → LANDSCAPE 16:9 + PORTRAIT 9:16 EXPORTS"
            textSize = 11f; gravity = Gravity.CENTER; setTextColor(0xff00d9ff.toInt()); setPadding(0, dp(4), 0, dp(8))
        }
        root.addView(formatHint)

        val zoomLabel = TextView(this).apply { text = "ZOOM"; textSize = 11f; setTextColor(-1) }
        root.addView(zoomLabel)
        zoomSeek = SeekBar(this).apply { max = 100; progress = 0; setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) { if (fromUser) camera?.cameraControl?.setZoomRatio(1f + (maxZoom - 1f) * progress / 100f) }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        }) }
        root.addView(zoomSeek, LinearLayout.LayoutParams(-1, dp(32)))

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

        val settingsTitle = TextView(this).apply { text = "WATERMARK SETTINGS"; textSize = 12f; typeface = android.graphics.Typeface.DEFAULT_BOLD; setTextColor(0xff00d9ff.toInt()); setPadding(0, dp(10), 0, dp(4)) }
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
            text = "Add my uploaded logo to both exports"; setTextColor(-1)
            setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean("watermark_enabled", checked).apply() }
        }
        root.addView(watermarkToggle)
        updateWatermarkUi()
        watermarkPositionButton = makeButton("Position: ${positions[watermarkPosition]}") {
            watermarkPosition = (watermarkPosition + 1) % positions.size
            prefs.edit().putInt("watermark_position", watermarkPosition).apply()
            watermarkPositionButton.text = "Position: ${positions[watermarkPosition]}"
        }
        root.addView(watermarkPositionButton, LinearLayout.LayoutParams(-1, dp(40)).apply { topMargin = dp(3) })
        addSettingSeek(root, "Logo size", "watermark_size", 5, 30, 16).also { watermarkSizeSeek = it }
        addSettingSeek(root, "Logo opacity", "watermark_opacity", 10, 100, 85).also { watermarkOpacitySeek = it }
        exportProgress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 100; progress = 0; visibility = View.GONE }
        root.addView(exportProgress, LinearLayout.LayoutParams(-1, dp(8)).apply { topMargin = dp(7) })
        val note = TextView(this).apply { text = "Upload your own transparent PNG or image logo. It is burned into both Landscape and Portrait exports. Videos save to Movies/BothNow."; textSize = 10f; gravity = Gravity.CENTER; setTextColor(0xffb8c2df.toInt()); setPadding(0, dp(5), 0, 0) }
        root.addView(note)
        setContentView(root)
    }

    private fun addSettingSeek(root: LinearLayout, label: String, key: String, min: Int, maxValue: Int, default: Int): SeekBar {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val title = TextView(this).apply { text = label; textSize = 11f; setTextColor(-1) }
        row.addView(title, LinearLayout.LayoutParams(dp(105), -2))
        val seek = SeekBar(this).apply {
            max = maxValue - min
            progress = (prefs.getInt(key, default) - min).coerceIn(0, max)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { if (fromUser) prefs.edit().putInt(key, p + min).apply() }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) {}
            })
        }
        row.addView(seek, LinearLayout.LayoutParams(0, dp(34), 1f))
        root.addView(row)
        return seek
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
            zoomSeek.progress = 0; torchEnabled = false
            flashButton.text = if (camera?.cameraInfo?.hasFlashUnit() == true) "Flash off" else "No flash"
            flashButton.isEnabled = camera?.cameraInfo?.hasFlashUnit() == true
            showStatus(if (lensFacing == CameraSelector.LENS_FACING_BACK) "BACK CAMERA READY" else "FRONT CAMERA READY")
        } catch (e: Exception) { showStatus("CAMERA START FAILED: ${e.localizedMessage ?: e.javaClass.simpleName}") }
    }

    private fun takePhoto() {
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
        val capture = videoCapture ?: run { showStatus("CAMERA NOT READY"); return }
        val raw = File(cacheDir, "BothNow_source_${timestamp()}.mp4")
        sourceVideo = raw
        val output = FileOutputOptions.Builder(raw).build()
        var pending = capture.output.prepareRecording(this, output)
        if (withAudio) pending = pending.withAudioEnabled()
        recording = pending.start(ContextCompat.getMainExecutor(this)) { event ->
            when (event) {
                is VideoRecordEvent.Start -> { isRecording = true; videoButton.text = "■  STOP RECORDING"; showStatus(if (withAudio) "RECORDING MASTER + AUDIO" else "RECORDING MASTER · NO AUDIO") }
                is VideoRecordEvent.Status -> { /* CameraX recording continues on its own executor. */ }
                is VideoRecordEvent.Finalize -> {
                    isRecording = false; recording = null; videoButton.text = "●  RECORD BOTH FORMATS"
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
            val crop = Crop(-halfW, halfW, -halfH, halfH)
            val effects = mutableListOf<androidx.media3.common.Effect>(crop, Presentation.createForWidthAndHeight(spec.width, spec.height, Presentation.LAYOUT_SCALE_TO_FIT))
            if (watermarkToggle.isChecked && prefs.getString("watermark_logo_uri", null) != null) {
                effects.add(createLogoOverlay(spec.width, spec.height))
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
        watermarkLogoStatus.text = if (hasLogo) "CUSTOM LOGO SELECTED · WILL BE BURNED INTO BOTH EXPORTS" else "NO CUSTOM LOGO SELECTED · UPLOAD A PNG/JPG/WEBP LOGO"
        watermarkToggle.isEnabled = hasLogo
        watermarkToggle.isChecked = hasLogo && prefs.getBoolean("watermark_enabled", true)
        removeWatermarkButton.isEnabled = hasLogo
    }

    private fun createLogoOverlay(outputWidth: Int, outputHeight: Int): OverlayEffect {
        val logoUri = prefs.getString("watermark_logo_uri", null)
            ?: throw IllegalStateException("Upload your own watermark logo first")
        val original = contentResolver.openInputStream(Uri.parse(logoUri))?.use { BitmapFactory.decodeStream(it) }
            ?: throw IllegalStateException("Could not read your uploaded watermark image")
        val percent = prefs.getInt("watermark_size", 16) / 100f
        val targetW = (outputWidth * percent).toInt().coerceAtLeast(48)
        val targetH = (original.height * (targetW.toFloat() / original.width)).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(original, targetW, targetH, true)
        if (scaled !== original) original.recycle()
        val alpha = prefs.getInt("watermark_opacity", 85) / 100f
        val anchors = when (watermarkPosition) {
            1 -> floatArrayOf(-1f, -1f, -0.94f, -0.94f)
            2 -> floatArrayOf(1f, 1f, 0.94f, 0.94f)
            3 -> floatArrayOf(-1f, 1f, -0.94f, 0.94f)
            else -> floatArrayOf(1f, -1f, 0.94f, -0.94f)
        }
        val settings = StaticOverlaySettings.Builder()
            .setOverlayFrameAnchor(anchors[0], anchors[1])
            .setBackgroundFrameAnchor(anchors[2], anchors[3])
            .setAlphaScale(alpha)
            .build()
        return OverlayEffect(listOf(BitmapOverlay.createStaticBitmapOverlay(scaled, settings)))
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

    override fun onDestroy() {
        if (isRecording) recording?.stop()
        activeTransformer?.cancel()
        cameraProvider?.unbindAll()
        if (::cameraExecutor.isInitialized) cameraExecutor.shutdown()
        super.onDestroy()
    }
}
