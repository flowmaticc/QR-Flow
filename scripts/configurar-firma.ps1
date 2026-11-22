<#
.SYNOPSIS
  Sube a GitHub los secretos de firma de QR-Flow, usando la misma clave que Garbillo.

.DESCRIPTION
  Lee keystore.properties y el .jks de la carpeta de la clave (por defecto G:\garbillo-clave-firma,
  y si no existe, ~\.bloqueador-keys) y crea en el repositorio flowmaticc/QR-Flow:
    secretos  RELEASE_KEYSTORE_BASE64, RELEASE_KEYSTORE_PASSWORD, RELEASE_KEY_ALIAS, RELEASE_KEY_PASSWORD
    variable  CERT_SHA256 (huella del certificado, copiada de Garbillo; no es secreta)
  Los valores se pasan a "gh secret set" por la entrada estándar: no aparecen en pantalla, ni en la
  línea de comandos ni en el historial. Necesita estar conectado con "gh auth login".

.EXAMPLE
  .\scripts\configurar-firma.ps1
  .\scripts\configurar-firma.ps1 -CarpetaClave "D:\otra\carpeta"
#>
param(
    [string]$CarpetaClave = "",
    [string]$Repo = "flowmaticc/QR-Flow"
)
$ErrorActionPreference = "Stop"
# Windows PowerShell 5.1 envía texto a otros programas en ASCII: sin esto, una contraseña con tildes o ñ llegaría mal.
$OutputEncoding = New-Object System.Text.UTF8Encoding $false

if (-not $CarpetaClave) {
    $candidatas = @("G:\garbillo-clave-firma", (Join-Path $HOME ".bloqueador-keys"))
    $CarpetaClave = $candidatas | Where-Object { Test-Path (Join-Path $_ "keystore.properties") } | Select-Object -First 1
    if (-not $CarpetaClave) { throw "No encuentro keystore.properties en: $($candidatas -join ', ')" }
}
$propsFile = Join-Path $CarpetaClave "keystore.properties"
Write-Host "Clave: $CarpetaClave"

# Leer las propiedades sin mostrarlas.
$props = @{}
foreach ($line in Get-Content -LiteralPath $propsFile -Encoding UTF8) {
    if ($line -match '^\s*([^#=\s]+)\s*=\s*(.*)$') { $props[$matches[1]] = $matches[2].Trim() }
}
foreach ($k in "storePassword", "keyAlias", "keyPassword") {
    if (-not $props[$k]) { throw "Falta '$k' en $propsFile" }
}

# El .jks: el que esté junto a keystore.properties; si no hay, el que indique storeFile.
$jks = Get-ChildItem -LiteralPath $CarpetaClave -Filter *.jks | Select-Object -First 1 -ExpandProperty FullName
if (-not $jks -and $props["storeFile"] -and (Test-Path -LiteralPath $props["storeFile"])) { $jks = $props["storeFile"] }
if (-not $jks) { throw "No encuentro el archivo .jks en $CarpetaClave" }
Write-Host "Almacén: $jks"

# Huella SHA-256 del certificado (pública): es la misma clave que Garbillo, así que se copia de su
# variable CERT_SHA256. El workflow de QR-Flow la usa para comprobar la firma antes de publicar.
$certSha = (gh variable get CERT_SHA256 --repo flowmaticc/bloqueador-android).Trim()
if ($LASTEXITCODE -ne 0 -or $certSha -notmatch '^[0-9a-f]{64}$') {
    throw "No he podido leer CERT_SHA256 de flowmaticc/bloqueador-android (¿gh auth login?)"
}

function Set-Secret([string]$name, [string]$value) {
    $value | gh secret set $name --repo $Repo
    if ($LASTEXITCODE -ne 0) { throw "No se pudo guardar el secreto $name" }
    Write-Host "  secreto $name guardado"
}

Write-Host "Subiendo a $Repo..."
Set-Secret "RELEASE_KEYSTORE_BASE64" ([Convert]::ToBase64String([IO.File]::ReadAllBytes($jks)))
Set-Secret "RELEASE_KEYSTORE_PASSWORD" $props["storePassword"]
Set-Secret "RELEASE_KEY_ALIAS" $props["keyAlias"]
Set-Secret "RELEASE_KEY_PASSWORD" $props["keyPassword"]
gh variable set CERT_SHA256 --body $certSha --repo $Repo
Write-Host "  variable CERT_SHA256 = $certSha"
$props.Clear()

Write-Host ""
Write-Host "Listo. Publica una versión en: https://github.com/$Repo/actions/workflows/release.yml (Run workflow)"
