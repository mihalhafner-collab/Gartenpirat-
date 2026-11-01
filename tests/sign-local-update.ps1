param(
    [Parameter(Mandatory=$true)][string]$BaselineApk,
    [Parameter(Mandatory=$true)][string]$UnsignedApk,
    [Parameter(Mandatory=$true)][string]$OutputApk
)
# Local existing-key signer. Never generates keys, installs or contacts a service.
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path -Parent $PSScriptRoot
$taskSdk = Join-Path $taskRoot 'android-toolchain/sdk/build-tools/37.0.0'
$taskJdk = Join-Path $taskRoot 'android-toolchain/jdk/jdk-17.0.20.1+1'
$env:JAVA_HOME = $taskJdk
$taskBaseline = (Resolve-Path -LiteralPath $BaselineApk).Path
$taskUnsigned = (Resolve-Path -LiteralPath $UnsignedApk).Path
$taskOutput = [IO.Path]::GetFullPath($OutputApk)
if (Test-Path -LiteralPath $taskOutput) { throw 'Ziel existiert bereits; keine alte APK wird überschrieben.' }
$taskBuild = Get-Content -LiteralPath (Join-Path $taskRoot 'app/build.gradle') -Raw
if (-not $taskBuild.Contains("'/.android/debug.keystore'")) { throw 'Unbekannter Signierungsweg; sichere manuelle Prüfung erforderlich.' }
$taskStore = Join-Path ([Environment]::GetFolderPath('UserProfile')) '.android/debug.keystore'
if (-not (Test-Path -LiteralPath $taskStore -PathType Leaf)) { throw 'Konfigurierter Original-Keystore fehlt. Kein Ersatzschlüssel wird erzeugt.' }

function Get-UpdatePackage([string]$path) {
    $taskInfo = (& (Join-Path $taskSdk 'aapt2.exe') dump badging $path 2>&1) -join "`n"
    if ($LASTEXITCODE -ne 0) { throw 'APK-Metadaten konnten nicht geprüft werden.' }
    $taskMatch = [regex]::Match($taskInfo, "package: name='([^']+)' versionCode='(\d+)' versionName='([^']+)'")
    if (-not $taskMatch.Success) { throw 'APK-Paketidentität fehlt.' }
    return @{ id=$taskMatch.Groups[1].Value; code=[int]$taskMatch.Groups[2].Value; name=$taskMatch.Groups[3].Value }
}
function Get-UpdateCertificate([string]$path) {
    $taskCheck = (& (Join-Path $taskSdk 'apksigner.bat') verify --verbose --print-certs $path 2>&1) -join "`n"
    if ($LASTEXITCODE -ne 0 -or $taskCheck -notmatch 'Number of signers: 1') { throw 'APK-Signatur ungültig oder nicht eindeutig.' }
    $taskDigest = [regex]::Match($taskCheck, 'certificate SHA-256 digest:\s*([a-fA-F0-9]+)').Groups[1].Value.ToLowerInvariant()
    if ($taskDigest.Length -ne 64) { throw 'Zertifikat-Prüfsumme fehlt.' }
    return $taskDigest
}

$taskOld = Get-UpdatePackage $taskBaseline
$taskNew = Get-UpdatePackage $taskUnsigned
if ($taskOld.id -ne 'at.hausgarten.service' -or $taskNew.id -ne $taskOld.id -or $taskNew.code -le $taskOld.code) {
    throw 'Application ID oder erhöhter versionCode nicht bestätigt.'
}
$taskOriginalCertificate = Get-UpdateCertificate $taskBaseline
$env:HG_LOCAL_STORE_PASSWORD = [regex]::Match($taskBuild, "storePassword\s+'([^']+)'").Groups[1].Value
$env:HG_LOCAL_KEY_PASSWORD = [regex]::Match($taskBuild, "keyPassword\s+'([^']+)'").Groups[1].Value
$taskAlias = [regex]::Match($taskBuild, "keyAlias\s+'([^']+)'").Groups[1].Value
try {
    if (-not $env:HG_LOCAL_STORE_PASSWORD -or -not $env:HG_LOCAL_KEY_PASSWORD -or -not $taskAlias) {
        throw 'Vorhandener sicherer Signierungszugriff fehlt. Keine Zugangsdaten ausgeben.'
    }
    $taskKeyCheck = (& (Join-Path $taskJdk 'bin/keytool.exe') -list -v -keystore $taskStore -alias $taskAlias -storepass:env HG_LOCAL_STORE_PASSWORD 2>&1) -join "`n"
    if ($LASTEXITCODE -ne 0) { throw 'Original-Keystore ist nicht zugreifbar; Signierung angehalten.' }
    $taskKeyDigest = [regex]::Match($taskKeyCheck, 'SHA256:\s*([A-Fa-f0-9:]+)').Groups[1].Value.Replace(':','').ToLowerInvariant()
    if ($taskKeyDigest -ne $taskOriginalCertificate) { throw 'Keystore passt nicht zur 1.41-Basis. Kein Ersatzschlüssel zulässig.' }
    $taskAligned = [IO.Path]::ChangeExtension($taskOutput, '.aligned.apk')
    if (Test-Path -LiteralPath $taskAligned) { throw 'Zwischenartefakt existiert bereits; bitte separat prüfen.' }
    & (Join-Path $taskSdk 'zipalign.exe') -p 4 $taskUnsigned $taskAligned
    if ($LASTEXITCODE -ne 0) { throw 'APK-Ausrichtung fehlgeschlagen.' }
    & (Join-Path $taskSdk 'apksigner.bat') sign --ks $taskStore --ks-key-alias $taskAlias --ks-pass env:HG_LOCAL_STORE_PASSWORD --key-pass env:HG_LOCAL_KEY_PASSWORD --v1-signing-enabled false --v2-signing-enabled true --v3-signing-enabled true --v4-signing-enabled false --out $taskOutput $taskAligned
    if ($LASTEXITCODE -ne 0) { throw 'Signierung fehlgeschlagen.' }
    & (Join-Path $taskSdk 'zipalign.exe') -c -p 4 $taskOutput
    if ($LASTEXITCODE -ne 0) { throw 'Signierte APK ist nicht korrekt ausgerichtet.' }
    $taskFinalCertificate = Get-UpdateCertificate $taskOutput
    $taskFinal = Get-UpdatePackage $taskOutput
    if ($taskFinalCertificate -ne $taskOriginalCertificate -or $taskFinal.id -ne $taskNew.id -or $taskFinal.code -ne $taskNew.code -or $taskFinal.name -ne $taskNew.name) {
        throw 'Abschließende Update-Prüfung fehlgeschlagen; APK nicht zur Installation freigeben.'
    }
    $taskReceipt = [ordered]@{
        application_id=$taskFinal.id; version_name=$taskFinal.name; version_code=$taskFinal.code
        baseline_version_name=$taskOld.name; baseline_version_code=$taskOld.code
        baseline_evidence='local signed APK; live Pixel not verified'
        certificate_sha256=$taskFinalCertificate; same_signing_certificate=$true
        apk=$taskOutput; sha256=(Get-FileHash -LiteralPath $taskOutput -Algorithm SHA256).Hash
        replacement_key_generated=$false; installed_on_pixel=$false
        privacy_and_device_acceptance='separate checks required'
    }
    $taskReceipt | ConvertTo-Json
    [IO.File]::WriteAllText(($taskOutput + '.verification.json'), ($taskReceipt | ConvertTo-Json), [Text.UTF8Encoding]::new($false))
} finally {
    Remove-Item -LiteralPath 'Env:\HG_LOCAL_STORE_PASSWORD','Env:\HG_LOCAL_KEY_PASSWORD' -ErrorAction SilentlyContinue
}
