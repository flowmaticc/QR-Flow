package net.hilson.qrieux.screenshot

import androidx.compose.runtime.Composable
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import net.hilson.qrieux.AndroidContext
import net.hilson.qrieux.scanner.CameraPreview
import net.hilson.qrieux.ui.PrivacySettingsDialog
import net.hilson.qrieux.ui.ScanResultOverlay
import net.hilson.qrieux.ui.theme.QRieuxTheme
import net.hilson.qrieux.util.QrContentType
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale

/** Renders the QR-Flow screens on Android 10 (the Galaxy A7's version), in Spanish. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [29], qualifiers = "es-rES-w411dp-h891dp-xxhdpi")
class QrFlowScreenshotTest {

    private fun shot(name: String, content: @Composable () -> Unit) {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.forLanguageTag("es-ES"))
        try {
            captureRoboImage("build/qrflow-shots/$name.png") {
                QRieuxTheme(darkTheme = false, dynamicColor = false) { content() }
            }
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test fun dangerUrl() = shot("01-url-peligrosa") {
        ScanResultOverlay(QrContentType.fromRawValue("https://www.bbva.es@bbva-clientes.top/acceso/verificar"), onDismiss = {}, onForget = {})
    }

    @Test fun safeUrl() = shot("02-url-segura") {
        ScanResultOverlay(QrContentType.fromRawValue("https://www.wikipedia.org/"), onDismiss = {}, onForget = {})
    }

    @Test fun mmiPhone() = shot("03-telefono-mmi") {
        ScanResultOverlay(QrContentType.fromRawValue("tel:*21*612345678#"), onDismiss = {})
    }

    @Test fun premiumSms() = shot("04-sms-premium") {
        ScanResultOverlay(QrContentType.fromRawValue("SMSTO:25521:ALTA JUEGOS"), onDismiss = {})
    }

    @Test fun javascript() = shot("05-javascript") {
        ScanResultOverlay(QrContentType.fromRawValue("javascript:alert(document.cookie)"), onDismiss = {})
    }

    @Test fun incognitoWifi() = shot("06-wifi-incognito") {
        ScanResultOverlay(QrContentType.fromRawValue("WIFI:T:WPA;S:Casa;P:secreta123;;"), onDismiss = {}, incognito = true)
    }

    @Test fun event() = shot("07-evento") {
        ScanResultOverlay(
            QrContentType.fromRawValue("BEGIN:VEVENT\nSUMMARY:Cena de empresa\nDTSTART:20261218T210000\nDTEND:20261218T233000\nLOCATION:Madrid\nEND:VEVENT"),
            onDismiss = {}
        )
    }

    @Test fun cameraIncognito() = shot("08-camara-incognito") {
        CameraPreview(onQrCodeDetected = {}, isScanning = true, onGalleryClick = {}, demoMode = true, incognito = true)
    }

    @Test fun privacyDialog() = shot("09-privacidad") {
        PrivacySettingsDialog(AndroidContext(ApplicationProvider.getApplicationContext()), onDismiss = {})
    }
}
