# QR-Flow

Lector QR **seguro y privado** para Android. Variante de [QRieux](https://github.com/abumalick/QRieux)
(© 2025 Emmanuel Hilson, licencia MIT), con id propio `com.flowmaticc.qrflow`, así que puede convivir con el original.

## Qué añade QR-Flow

- **Analizador de QR maliciosos, sin conexión**: detecta URLs con `@` engañosa, punycode y letras
  falsas, IP desnudas, suplantación de marcas, bancos y organismos españoles (BBVA, Santander, Correos,
  DGT, Agencia Tributaria…), acortadores, dominios de estafa, descargas `.apk`/`.exe`, redirecciones
  ocultas, caracteres invisibles, esquemas peligrosos (`javascript:`, `intent:`, `data:`…), códigos
  MMI/USSD (`*21*…#`), números de tarificación especial, SMS a números cortos y WiFi abiertas o WEP.
  Pide confirmación antes de abrir algo sospechoso y bloquea lo peligroso.
- **Modo incógnito**: las lecturas no se guardan, lo que copias se oculta y se borra al minuto, y
  se bloquean las capturas. Se activa con el botón del ojo en la cámara o con el atajo del icono
  («Escanear en incógnito»). También puedes quitar una lectura concreta con «No guardar esta lectura».
- **Historial cifrado** (AES-256-GCM con clave en el Android Keystore), desactivable, con borrado
  automático (1, 7 o 30 días) y **sin contraseñas WiFi** por defecto.
- **Más tipos de QR**: SMS, ubicación (`geo:`), eventos de calendario, MECARD, MATMSG y enlaces a
  apps (`market:`, `otpauth:`, `bitcoin:`…), además de URL, email, teléfono, WiFi, vCard y texto.
- **Cámara**: toca la imagen para enfocar en ese punto, pellizca para acercar o usa el botón de
  zoom bajo el marco (1× → 2× → 4×). Útil con códigos pequeños, lejanos o desenfocados.
- **Arreglos**: WiFi WPA3 y redes sin tipo, sin copia de seguridad ni transferencia del historial,
  modo demo solo en builds de depuración, límite de tamaño al recibir vCards, limpieza de archivos
  temporales y APK de 10,3 MB → 2,9 MB.
- Sigue **sin permiso de Internet**: CAMERA, VIBRATE y CHANGE_WIFI_STATE.

## Instalar

Descarga `qr-flow-X.Y.Z.apk` de la [última versión](https://github.com/flowmaticc/QR-Flow/releases/latest)
y ábrelo en el móvil (Android 7 o superior). Las versiones se publican a mano desde
*Actions → Publicar*, firmadas y comprobadas: el workflow falla si el APK pidiera permiso de Internet.

## Compilar

Requiere JDK 17 y el Android SDK (API 36).

```bash
./gradlew :composeApp:testDebugUnitTest   # tests (incluido el analizador de riesgos)
./gradlew :composeApp:assembleDebug       # APK de depuración ("QR-Flow Dev", convive con la normal)
./gradlew :composeApp:assembleRelease     # APK firmado si existe key.properties
```

Integración prevista con Garbillo: [docs/INTEGRACION_GARBILLO.md](docs/INTEGRACION_GARBILLO.md).

---

*README original de QRieux:*

# QRieux

A simple, ad-free QR code scanner designed for everyone — especially our parents and grandparents.

<p align="center">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/1_scanner.png" width="250" alt="Scanner">
  <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/2_url_result.png" width="250" alt="URL Result">
</p>

<p align="center">
  <a href="https://f-droid.org/packages/net.hilson.qrieux/"><img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" height="70" alt="Get it on F-Droid"></a>
  <a href="https://play.google.com/store/apps/details?id=net.hilson.qrieux"><img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" height="70" alt="Get it on Google Play"></a>
  <a href="https://apps.apple.com/app/id6758559665"><img src="https://developer.apple.com/assets/elements/badges/download-on-the-app-store.svg" height="47" alt="Download on the App Store"></a>
</p>

## Why QRieux?

Finding a QR code scanner that is simple, ad-free, free, and privacy-respecting is nearly impossible. So I built one.

- **No ads** — clean experience, no distractions
- **No tracking** — your data stays on your device
- **Simple UI** — large buttons, obvious actions
- **Smart actions** — open links, call phones, send emails, connect to WiFi, copy, share
- **Gallery scanning** — scan QR codes from photos
- **Share to scan** — share images from other apps to scan
- **Create QR codes** — for a website, email address, phone number, WiFi network or any text
- **Multilingual** — 20 languages

## Coming Soon

- Security warnings for suspicious links (anti-phishing)

## Tech Stack

- Kotlin Multiplatform + Compose Multiplatform
- CameraX for camera capture
- ZXing for barcode scanning and generation
- Material3 for UI

## Build

```bash
# Debug build
./gradlew :composeApp:assembleDebug

# Release build (requires key.properties)
./gradlew :composeApp:assembleRelease

# Run tests
./gradlew :composeApp:testDebugUnitTest
```

## Testing

Open [examples/test-qr-codes.md](examples/test-qr-codes.md) to test scanning all supported QR code types (URLs, emails, phones, WiFi, plain text).

## Contributing

Contributions welcome! Please open an issue first to discuss what you'd like to change.

## License

[MIT](LICENSE)
