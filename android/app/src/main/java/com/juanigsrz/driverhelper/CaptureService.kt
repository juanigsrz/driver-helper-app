package com.juanigsrz.driverhelper

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class CaptureService : Service() {

    companion object {
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        private const val CHANNEL_ID = "capture"
        private const val NOTIF_ID = 1
        private const val SAMPLE_PERIOD_MS = 500L  // 2 fps
        private const val TAG = "CaptureService"
    }

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private val captureThread = HandlerThread("dh-capture").also { it.start() }
    private val captureHandler = Handler(captureThread.looper)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val recognizer =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val dedup = DedupCache()
    private val backend by lazy {
        BackendClient(BuildConfig.BACKEND_URL, BuildConfig.BACKEND_SECRET)
    }

    private var lastSampleAtMs = 0L

    private val projectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            virtualDisplay?.release()
            imageReader?.close()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForegroundWithType()
        intent ?: return START_NOT_STICKY

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        val resultData: Intent? = intent.getParcelableExtra(EXTRA_RESULT_DATA)
        if (resultCode != Activity.RESULT_OK || resultData == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        val mpm = getSystemService<MediaProjectionManager>() ?: run {
            stopSelf()
            return START_NOT_STICKY
        }
        projection = mpm.getMediaProjection(resultCode, resultData).also {
            it.registerCallback(projectionCallback, captureHandler)
        }
        startCapture()
        return START_STICKY
    }

    private fun startForegroundWithType() {
        val nm = getSystemService<NotificationManager>()!!
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Capture",
                    NotificationManager.IMPORTANCE_LOW,
                )
            )
        }
        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("driver-helper running")
            .setContentText("Watching for offers")
            .setOngoing(true)
            .build()
        startForeground(
            NOTIF_ID,
            notif,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION,
        )
    }

    private fun startCapture() {
        val wm = getSystemService<WindowManager>()!!
        val dm = DisplayMetrics().also {
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(it)
        }
        val width = dm.widthPixels
        val height = dm.heightPixels
        val density = dm.densityDpi

        imageReader = ImageReader.newInstance(
            width,
            height,
            PixelFormat.RGBA_8888,
            2,
        ).apply {
            setOnImageAvailableListener({ reader ->
                val now = System.currentTimeMillis()
                if (now - lastSampleAtMs < SAMPLE_PERIOD_MS) {
                    reader.acquireLatestImage()?.close()
                    return@setOnImageAvailableListener
                }
                lastSampleAtMs = now
                val img: Image = reader.acquireLatestImage()
                    ?: return@setOnImageAvailableListener
                val bmp = try { img.toBitmap() } finally { img.close() }
                onSample(bmp)
            }, captureHandler)
        }

        virtualDisplay = projection?.createVirtualDisplay(
            "driver-helper",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            captureHandler,
        )
    }

    private fun onSample(full: Bitmap) {
        val pkg = ForegroundDetector.foregroundTargetPkg(this)
        val platform = pkg?.let { OfferParser.detectPlatform(it) }
        if (platform == null) {
            full.recycle()
            return
        }

        // TEST full-screen crop — revert to 0.4f before shipping
        val cropped = full.cropBottomFraction(1.0f)
        full.recycle()

        scope.launch {
            try {
                val text = recognizer
                    .process(InputImage.fromBitmap(cropped, 0))
                    .await()
                    .text
                cropped.recycle()

                if (text.isBlank()) return@launch

                val hash = OfferParser.canonicalHash(platform, text)
                if (!dedup.isNew(hash)) return@launch

                val price = OfferParser.parsePrice(text)
                val loc = LocationProvider.current(this@CaptureService) ?: run {
                    Log.w(TAG, "no location; skip offer")
                    return@launch
                }

                val verdict = backend.evaluate(
                    OfferIn(
                        platform = platform,
                        price_ars = price,
                        driver = Point(loc.latitude, loc.longitude),
                        raw_text = text,
                    )
                )
                Notifier.showVerdict(this@CaptureService, verdict)
            } catch (t: Throwable) {
                Log.w(TAG, "pipeline error: ${t.message}")
                if (!cropped.isRecycled) cropped.recycle()
            }
        }
    }

    override fun onDestroy() {
        scope.cancel()
        recognizer.close()
        virtualDisplay?.release()
        imageReader?.close()
        projection?.unregisterCallback(projectionCallback)
        projection?.stop()
        captureThread.quitSafely()
        super.onDestroy()
    }
}
