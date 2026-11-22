package net.hilson.qrieux.scanner

import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy

class QrAnalyzer(
    private val onQrCodeDetected: (String) -> Unit
) : ImageAnalysis.Analyzer {

    @Volatile
    var isEnabled: Boolean = true

    // After a code is reported it is ignored until it has left the frame, so tapping "Scan again"
    // with the phone still pointed at it does not reopen the same result straight away.
    private val repeatGuard = RepeatGuard()

    override fun analyze(imageProxy: ImageProxy) {
        try {
            if (!isEnabled) return
            val plane = imageProxy.planes.firstOrNull() ?: return
            val luminance = ByteArray(plane.buffer.remaining()).also { plane.buffer.get(it) }
            val value = QrDecoder.decodeLuminancePlane(
                plane = luminance,
                rowStride = plane.rowStride,
                width = imageProxy.width,
                height = imageProxy.height
            )
            if (repeatGuard.accept(value) && value != null && isEnabled) onQrCodeDetected(value)
        } finally {
            imageProxy.close()
        }
    }
}

/**
 * Lets a decoded value through once, then blocks that same value until [clearAfterMisses] frames in a
 * row decode something else or nothing (about a second at the analysis frame rate). Decoding fails on
 * some frames even with the code in view, hence a run of misses rather than a single one.
 */
class RepeatGuard(private val clearAfterMisses: Int = 15) {
    private var last: String? = null
    private var misses = 0

    @Synchronized
    fun accept(value: String?): Boolean {
        if (value != null && value == last) {
            misses = 0
            return false
        }
        if (last != null && ++misses < clearAfterMisses && value == null) return false
        if (value == null) {
            if (misses >= clearAfterMisses) last = null
            return false
        }
        last = value
        misses = 0
        return true
    }
}
