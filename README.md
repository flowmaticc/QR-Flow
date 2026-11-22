# QR-Flow

Lector QR **seguro y privado** para Android.

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

## Créditos

QR-Flow parte del código de **QRieux**, de Emmanuel Hilson, publicado con licencia MIT. Se conserva su aviso de copyright en [LICENSE](LICENSE). QR-Flow es un proyecto independiente: no tiene relación con el autor original ni con sus versiones publicadas.
