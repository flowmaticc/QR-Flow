# QR-Flow + Garbillo: cómo se complementan

## Lo que ya funciona hoy, sin tocar código

Si Garbillo está activo, cada enlace que QR-Flow abre en el navegador pasa por su filtro DNS.
Un dominio de phishing que esté en las listas de amenazas de Hagezi no llega a cargar, aunque
QR-Flow no lo haya detectado. Son dos capas independientes:

| Capa | Cuándo actúa | Qué detecta |
|---|---|---|
| **QR-Flow** (heurística local) | Antes de abrir, al escanear | Trucos de forma: `@` en la URL, punycode, IP desnuda, marcas suplantadas, acortadores, `.apk`, códigos MMI, SMS premium, esquemas peligrosos |
| **Garbillo** (listas Hagezi TIF) | Al abrir, en la consulta DNS | Dominios concretos ya conocidos como maliciosos (cientos de miles) |

## Integración propuesta (futuro): consulta local entre apps

Objetivo: que QR-Flow muestre «**Dominio en la lista de amenazas de Garbillo**» en el panel rojo
*antes* de abrir, sin dar permiso de Internet a QR-Flow y sin que Garbillo exponga el historial.

### En Garbillo (`app.bloqueador`)

1. Un permiso propio con `protectionLevel="signature"` (solo apps firmadas con la misma clave):

   ```xml
   <permission android:name="app.bloqueador.permission.CHECK_DOMAIN"
       android:protectionLevel="signature" />
   ```

2. Un `ContentProvider` de solo lectura, protegido con ese permiso, que responde a una sola pregunta:

   ```
   content://app.bloqueador.threats/check?domain=ejemplo.top
   → columnas: threat (0/1), list ("amenazas" | "amenazas_max" | ""), updated_at
   ```

   Por dentro usa `FilterEngine.peek(domain)` + `FilterEngine.isThreat(rule)`, que ya existen y no
   tocan contadores ni historial. No hace falta red: las listas ya están en el móvil.

3. No registrar la consulta en el historial de Garbillo (no es navegación real).

### En QR-Flow

1. `<queries><package android:name="app.bloqueador" /></queries>` y `uses-permission` del permiso.
2. Nueva razón `RiskReason.KNOWN_THREAT_LIST(DANGER)` en `QrRiskAnalyzer`.
3. Al analizar una URL, si Garbillo está instalado, consultar el provider con `realDomain` y `host`
   (en un hilo de E/S, con un tiempo máximo de ~150 ms: si no contesta, se sigue sin él).
4. Si Garbillo no está instalado, mostrar una línea discreta: «Protección extra con Garbillo».

### Requisito

Ambas apps deben firmarse con **la misma clave de publicación** para que el permiso `signature`
funcione. Conviene crear esa clave una vez y usarla en los dos proyectos.

## Otras ideas de reutilización

- **Reportar un QR**: Garbillo ya tiene un servicio de reportes (`reportes/`, Cloudflare Workers).
  QR-Flow podría delegar el envío en Garbillo con un `Intent` explícito (QR-Flow sigue sin Internet):
  solo se envía el dominio, nunca el contenido completo del QR.
- **Listas compartidas de marcas**: la tabla de marcas y dominios oficiales de
  `QrRiskAnalyzer.BRANDS` podría vivir en un módulo común y servir también a Garbillo para avisar de
  dominios que imitan bancos españoles aunque aún no estén en Hagezi.
- **Aviso cruzado**: cuando Garbillo bloquea un dominio de amenaza abierto desde QR-Flow, su
  notificación puede decir «venía de un código QR».
