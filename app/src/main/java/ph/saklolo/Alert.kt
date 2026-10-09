package ph.saklolo

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.concurrent.thread

// "Ring phone" on the victim: loud chirp + flashlight strobe for 30 s. No torch (or camera busy) → sound only.
object Alert {
    var ringing by mutableStateOf(false)
        private set

    // ponytail: 2 Hz strobe (kept under 3 Hz for photosensitivity); tone and rate are the tuning knobs for the hall
    private const val STEP_MS = 250L

    fun ring(ctx: Context, ms: Long = 30_000) {
        if (ringing) return
        ringing = true
        thread(name = "ring") {
            val am = ctx.getSystemService(AudioManager::class.java)
            runCatching { am.setStreamVolume(AudioManager.STREAM_ALARM, am.getStreamMaxVolume(AudioManager.STREAM_ALARM), 0) }
            val tone = ToneGenerator(AudioManager.STREAM_ALARM, ToneGenerator.MAX_VOLUME)
            val cam = ctx.getSystemService(CameraManager::class.java)
            val torch = runCatching {
                cam.cameraIdList.first { cam.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
            }.getOrNull()
            val end = SystemClock.elapsedRealtime() + ms
            var on = false
            while (ringing && SystemClock.elapsedRealtime() < end) {
                tone.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 200)
                on = !on
                torch?.let { runCatching { cam.setTorchMode(it, on) } }
                Thread.sleep(STEP_MS)
            }
            torch?.let { runCatching { cam.setTorchMode(it, false) } }
            tone.release()
            ringing = false
        }
    }

    fun stop() { ringing = false }
}
