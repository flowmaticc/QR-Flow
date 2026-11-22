# Publicar QR-Flow en Obtainium, IzzyOnDroid y F-Droid

QR-Flow no tiene permiso de Internet, así que **no puede actualizarse sola**. Las actualizaciones
llegan por una de estas vías. La ficha (títulos, descripciones, novedades y capturas) está en
`fastlane/metadata/android/{es-ES,en-US}/`, que es lo que leen IzzyOnDroid y F-Droid.

Todas las versiones se firman con la misma clave que Garbillo. Huella SHA-256 del certificado:

```
ee:5b:77:ee:24:58:c5:a6:a4:5e:3b:c6:a3:da:ac:ee:fb:31:79:19:49:3d:4d:85:8e:4f:46:a9:72:56:83:ce
```

## 1. Obtainium (ya funciona)

Obtainium consulta GitHub Releases y avisa de cada versión nueva. El README tiene el botón
«Añadir a Obtainium» ya configurado (`com.flowmaticc.qrflow`, solo versiones estables).
No hace falta hacer nada más: cada vez que se publica desde *Actions → Publicar*, Obtainium la ve.

## 2. IzzyOnDroid (rápido, recomendado primero)

IzzyOnDroid publica el **mismo APK** de GitHub Releases, así que no hay que preparar compilaciones.

1. Abre una solicitud en <https://gitlab.com/IzzyOnDroid/repo/-/issues> (plantilla «App inclusion request»).
2. Datos que piden:
   - Repositorio: `https://github.com/flowmaticc/QR-Flow`
   - Package: `com.flowmaticc.qrflow`
   - Licencia: MIT. Sin anuncios, sin rastreadores, sin permiso de Internet.
   - Nombre del APK en cada versión: `qr-flow-X.Y.Z.apk`
3. Izzy revisa el APK con sus escáneres (librerías, rastreadores, permisos) y lo añade.

## 3. F-Droid (compila desde el código; más lento)

F-Droid compila la app en sus servidores. Como cada versión publicada lleva nuestra firma, se puede
pedir *compilación reproducible*: F-Droid compila, comprueba que su APK es idéntico al nuestro y
publica el nuestro (así se actualiza encima sin desinstalar).

1. Haz un fork de <https://gitlab.com/fdroid/fdroiddata> y crea `metadata/com.flowmaticc.qrflow.yml`:

```yaml
Categories:
  - Security
  - System
License: MIT
AuthorName: flowmaticc
SourceCode: https://github.com/flowmaticc/QR-Flow
IssueTracker: https://github.com/flowmaticc/QR-Flow/issues
Changelog: https://github.com/flowmaticc/QR-Flow/releases

AutoName: QR-Flow

RepoType: git
Repo: https://github.com/flowmaticc/QR-Flow.git
Binaries: https://github.com/flowmaticc/QR-Flow/releases/download/v%v/qr-flow-%v.apk

Builds:
  - versionName: 1.1.0
    versionCode: 2
    commit: v1.1.0
    subdir: composeApp
    gradle:
      - yes

AllowedAPKSigningKeys: ee5b77ee2458c5a6a45e3bc6a3daaceefb317919493d4d858e4f46a9725683ce

AutoUpdateMode: Version
UpdateCheckMode: Tags
CurrentVersion: 1.1.0
CurrentVersionCode: 2
```

2. Comprueba la receta en local con `fdroid build -v -l com.flowmaticc.qrflow` (necesita el
   servidor de compilación de F-Droid o Docker con `fdroidserver`). Si el APK no sale idéntico, la
   receta se puede publicar sin `Binaries:` ni `AllowedAPKSigningKeys:` (F-Droid firmará con su clave
   y habrá que desinstalar la versión de GitHub para pasar a la de F-Droid).
3. Abre el *merge request* en fdroiddata. Los revisores suelen pedir ajustes; el proceso tarda semanas.

El proyecto ya cumple lo que F-Droid exige: código libre, sin Google Play Services ni ML Kit (lee con
ZXing), sin bloque de dependencias en la firma del APK (`dependenciesInfo` desactivado) y sin rastreadores.
