# QR-Flow

Lector QR **seguro y privado** para Android.

## Qué añade QR-Flow

- **Analizador de QR maliciosos, sin conexión**: detecta URLs con `@` engañosa, punycode y letras
  falsas, IP desnudas, suplantación de marcas, bancos y organismos españoles (BBVA, Santander, Correos,
  DGT, Agencia Tributaria…), acortadores, dominios de estafa, descargas `.apk`/`.exe`, redirecciones
  ocultas, caracteres invisibles, esquemas peligrosos (`javascript:`, `intent:`, `data:`…), códigos
  MMI/USSD (`*21*…#`), números de tarificación especial, SMS a números cortos y WiFi abiertas o WEP.
  También reconoce direcciones sin `https://`, sigue las redirecciones (Google, Facebook…) hasta el
  destino real y detecta webs en alojamientos gratuitos, formularios y marcas escondidas en la ruta.
  Pide confirmación antes de abrir algo sospechoso y bloquea lo peligroso. Avisos en 20 idiomas.
- **Modo incógnito**: las lecturas no se guardan, lo que copias se oculta y se borra al minuto, y
  se bloquean las capturas. Se activa con el botón del ojo en la cámara o con el atajo del icono
  («Escanear en incógnito»), y se apaga solo tras 30 minutos sin usar la app. También puedes quitar una lectura concreta con «No guardar esta lectura».
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

[![Descargar APK](https://img.shields.io/github/v/release/flowmaticc/QR-Flow?label=Descargar%20APK&logo=github&style=for-the-badge&color=6A1B9A&labelColor=24292f)](https://github.com/flowmaticc/QR-Flow/releases/latest)
[![Añadir a Obtainium](https://img.shields.io/badge/Obtainium-A%C3%B1adir-6438B5?style=for-the-badge)](https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22com.flowmaticc.qrflow%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2Fflowmaticc%2FQR-Flow%22%2C%22author%22%3A%22flowmaticc%22%2C%22name%22%3A%22QR-Flow%22%2C%22additionalSettings%22%3A%22%7B%5C%22includePrereleases%5C%22%3Afalse%7D%22%7D)

Descarga `qr-flow-X.Y.Z.apk` de la [última versión](https://github.com/flowmaticc/QR-Flow/releases/latest)
y ábrelo en el móvil (Android 7 o superior). QR-Flow no tiene Internet y no puede avisarte de
versiones nuevas: con [Obtainium](https://github.com/ImranR98/Obtainium) (botón de arriba, ya
configurado) te llegarán solas.

**Comprueba que el APK es auténtico.** Va firmado con la misma clave que Garbillo; su huella SHA-256 es
`ee:5b:77:ee:24:58:c5:a6:a4:5e:3b:c6:a3:da:ac:ee:fb:31:79:19:49:3d:4d:85:8e:4f:46:a9:72:56:83:ce`
(con el SDK de Android: `apksigner verify --print-certs qr-flow-X.Y.Z.apk`). Cómo llevarla a
IzzyOnDroid y F-Droid: [docs/PUBLICAR_TIENDAS.md](docs/PUBLICAR_TIENDAS.md). Las versiones se publican a mano desde
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
