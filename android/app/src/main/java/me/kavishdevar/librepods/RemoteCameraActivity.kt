package me.kavishdevar.librepods

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.SurfaceTexture
import android.graphics.Matrix
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import android.view.Surface
import android.view.TextureView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import me.kavishdevar.librepods.data.CaptureGate
import me.kavishdevar.librepods.presentation.theme.LibrePodsTheme
import me.kavishdevar.librepods.services.ServiceManager
import java.util.concurrent.Executors

/** Foreground-only camera. It never injects keys into another camera app. */
class RemoteCameraActivity : ComponentActivity() {
    private var permission by mutableStateOf(false)
    private var status by mutableIntStateOf(R.string.camera_note)
    private var ready by mutableStateOf(false)
    private var foreground = false
    private var preview: TextureView? = null
    private var previewSurface: Surface? = null
    private var camera: CameraDevice? = null
    private var captureSession: CameraCaptureSession? = null
    private var images: ImageReader? = null
    private var sensorOrientation = 90
    private var previewSize: android.util.Size? = null
    private var captureRevision = 0L
    private val gate = CaptureGate(SystemClock::elapsedRealtime)
    private var session = 0L
    private val handler = Handler(Looper.getMainLooper())
    private val saves = Executors.newSingleThreadExecutor()
    private val permissionRequest = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        permission = granted
        if (granted) openCamera()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        permission = checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        setContent {
            LibrePodsTheme {
                Column(Modifier.fillMaxSize().padding(16.dp)) {
                    Text(stringResource(R.string.feature_camera_remote), style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(top = 32.dp, bottom = 12.dp))
                    Text(stringResource(status), modifier = Modifier.padding(bottom = 12.dp))
                    if (permission) AndroidView(modifier = Modifier.fillMaxWidth().weight(1f), factory = { context ->
                        TextureView(context).also { texture ->
                            preview = texture
                            texture.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                                override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) { openCamera() }
                                override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) { configurePreview() }
                                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean { closeCamera(); return true }
                                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
                            }
                        }
                    })
                    Button(onClick = {
                        if (permission && camera == null) openCamera()
                        else if (permission) capture() else permissionRequest.launch(Manifest.permission.CAMERA)
                    }, enabled = !permission || ready || camera == null, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(if (permission) R.string.camera_capture else R.string.camera_permission))
                    }
                    Button(onClick = ::finish, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.camera_close)) }
                }
            }
        }
    }

    override fun onResume() { super.onResume(); foreground = true; openCamera() }
    override fun onPause() { foreground = false; closeCamera(); super.onPause() }
    override fun onDestroy() { closeCamera(); saves.shutdown(); super.onDestroy() }

    @SuppressLint("MissingPermission") // Checked immediately before openCamera.
    private fun openCamera() {
        if (!foreground || !permission || preview?.isAvailable != true || camera != null || gate.isCurrent(session)) return
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) { permission = false; return }
        val manager = getSystemService(CameraManager::class.java)
        try {
            val id = manager.cameraIdList.firstOrNull { manager.getCameraCharacteristics(it)
                .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK } ?: error("no back camera")
            val characteristics = manager.getCameraCharacteristics(id)
            sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
            val outputs = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: error("no outputs")
            val jpeg = outputs.getOutputSizes(ImageFormat.JPEG).filter { it.width <= 1920 && it.height <= 1920 }
                .maxByOrNull { it.width.toLong() * it.height } ?: outputs.getOutputSizes(ImageFormat.JPEG).minBy { it.width.toLong() * it.height }
            val previewSize = outputs.getOutputSizes(SurfaceTexture::class.java)
                .filter { it.width <= 1920 && it.height <= 1920 }
                .minByOrNull { kotlin.math.abs(it.width.toFloat() / it.height - jpeg.width.toFloat() / jpeg.height) }
                ?: error("no preview")
            this.previewSize = previewSize
            configurePreview()
            val texture = preview!!.surfaceTexture ?: return
            texture.setDefaultBufferSize(previewSize.width, previewSize.height)
            previewSurface = Surface(texture)
            session = gate.opened()
            val token = session
            images = ImageReader.newInstance(jpeg.width, jpeg.height, ImageFormat.JPEG, 2).also { reader ->
                reader.setOnImageAvailableListener({ source ->
                    val image = runCatching { source.acquireNextImage() }.getOrNull() ?: return@setOnImageAvailableListener
                    val bytes = image.use { it.planes[0].buffer.let { buffer -> ByteArray(buffer.remaining()).also(buffer::get) } }
                    if (gate.isCurrent(token)) saves.execute { savePhoto(bytes, token) }
                }, handler)
            }
            manager.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) {
                    if (!foreground || !gate.isCurrent(token)) { device.close(); return }
                    camera = device
                    val targets = listOf(OutputConfiguration(previewSurface!!), OutputConfiguration(images!!.surface))
                    try { device.createCaptureSession(SessionConfiguration(SessionConfiguration.SESSION_REGULAR, targets,
                        mainExecutor, object : CameraCaptureSession.StateCallback() {
                            override fun onConfigured(created: CameraCaptureSession) {
                                if (!foreground || !gate.isCurrent(token)) { created.close(); return }
                                captureSession = created
                                try {
                                    val request = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply { addTarget(previewSurface!!) }
                                    created.setRepeatingRequest(request.build(), null, handler)
                                    ready = true
                                    status = R.string.camera_note
                                    ServiceManager.getService()?.cameraOpened(this@RemoteCameraActivity) { capture() }
                                } catch (_: Exception) { failed(token) }
                            }
                            override fun onConfigureFailed(created: CameraCaptureSession) { created.close(); failed(token) }
                        })) } catch (_: Exception) { failed(token) }
                }
                override fun onDisconnected(device: CameraDevice) { device.close(); failed(token) }
                override fun onError(device: CameraDevice, error: Int) { device.close(); failed(token) }
            }, handler)
        } catch (_: Exception) { failed(session) }
    }

    private fun capture() {
        val device = camera ?: return
        val target = images?.surface ?: return
        val current = captureSession ?: return
        val token = session
        if (!foreground || !ready || !gate.begin(token)) return
        val revision = ++captureRevision
        ready = false
        status = R.string.camera_saving
        try {
            val degrees = when (display?.rotation) { Surface.ROTATION_90 -> 90; Surface.ROTATION_180 -> 180; Surface.ROTATION_270 -> 270; else -> 0 }
            val request = device.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE).apply {
                addTarget(target)
                set(CaptureRequest.JPEG_ORIENTATION, (sensorOrientation - degrees + 360) % 360)
            }
            current.capture(request.build(), object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureFailed(session: CameraCaptureSession, request: CaptureRequest, failure: CaptureFailure) { recover(token, false) }
            }, handler)
            handler.postDelayed({ if (gate.isCurrent(token) && captureRevision == revision && !ready) failed(token) }, 5000)
        } catch (_: Exception) { recover(token, false) }
    }

    private fun configurePreview() {
        val texture = preview ?: return
        val size = previewSize ?: return
        if (texture.width == 0 || texture.height == 0) return
        val degrees = when (display?.rotation) { Surface.ROTATION_90 -> 90; Surface.ROTATION_180 -> 180; Surface.ROTATION_270 -> 270; else -> 0 }
        val orientation = (sensorOrientation - degrees + 360) % 360
        val width = if (orientation % 180 == 0) size.width else size.height
        val height = if (orientation % 180 == 0) size.height else size.width
        val scale = maxOf(texture.width.toFloat() / width, texture.height.toFloat() / height)
        texture.setTransform(Matrix().apply {
            setScale(size.width.toFloat() / texture.width, size.height.toFloat() / texture.height)
            postTranslate(-size.width / 2f, -size.height / 2f)
            postRotate(orientation.toFloat())
            postScale(scale, scale)
            postTranslate(texture.width / 2f, texture.height / 2f)
        })
    }

    private fun savePhoto(bytes: ByteArray, token: Long) {
        var uri: android.net.Uri? = null
        val success = runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "LibrePods-${System.currentTimeMillis()}.jpg")
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/LibrePods")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("insert failed")
            contentResolver.openOutputStream(uri!!)?.use { it.write(bytes) } ?: error("write failed")
            contentResolver.update(uri!!, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
            true
        }.getOrDefault(false)
        if (!success) uri?.let { runCatching { contentResolver.delete(it, null, null) } }
        handler.post { recover(token, success) }
    }

    private fun recover(token: Long, success: Boolean) {
        if (!gate.isCurrent(token)) return
        gate.completed(token)
        ready = foreground && camera != null && captureSession != null
        status = if (success) R.string.camera_saved else R.string.camera_failed
    }
    private fun failed(token: Long) {
        if (token != session) return
        closeCamera()
        status = R.string.camera_failed
    }
    private fun closeCamera() {
        gate.close()
        ready = false
        ServiceManager.getService()?.cameraClosed(this)
        captureSession?.close(); captureSession = null
        camera?.close(); camera = null
        images?.close(); images = null
        previewSurface?.release(); previewSurface = null
    }
}
