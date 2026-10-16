param(
    [Parameter(Mandatory=$true)][string]$NewApk,
    [Parameter(Mandatory=$true)][string]$PreviousApk,
    [Parameter(Mandatory=$true)][string]$ExpectedAppVersion
)
$ErrorActionPreference = 'Stop'
$taskRoot = $PSScriptRoot
$taskNew = (Resolve-Path -LiteralPath $NewApk).Path
$taskPrevious = (Resolve-Path -LiteralPath $PreviousApk).Path
$env:JAVA_HOME = Join-Path $taskRoot 'android-toolchain/jdk/jdk-17.0.20.1+1'
$taskSigner = Join-Path $taskRoot 'android-toolchain/sdk/build-tools/35.0.0/apksigner.bat'
$taskAapt = Join-Path $taskRoot 'android-toolchain/sdk/build-tools/35.0.0/aapt.exe'
function Get-TaskSigningCertificate([string]$ApkPath) {
    $taskResult = (& $taskSigner verify --print-certs $ApkPath) -join "`n"
    if ($LASTEXITCODE -ne 0) { throw "APK signature verification failed: $ApkPath" }
    $taskMatch = [regex]::Match($taskResult, 'Signer #1 certificate SHA-256 digest: ([a-f0-9]+)')
    if (!$taskMatch.Success) { throw 'Signing certificate could not be determined.' }
    return $taskMatch.Groups[1].Value
}
$taskNewCertificate = Get-TaskSigningCertificate $taskNew
$taskOldCertificate = Get-TaskSigningCertificate $taskPrevious
if ($taskNewCertificate -cne $taskOldCertificate) { throw 'Signing identity changed. Do not publish this APK as an update.' }
$taskBadging = (& $taskAapt dump badging $taskNew) -join "`n"
if ($LASTEXITCODE -ne 0) { throw 'APK metadata could not be read.' }
if (!$taskBadging.Contains("name='at.hausgarten.service'") -or !$taskBadging.Contains("versionName='$ExpectedAppVersion'")) { throw 'Unexpected app ID or version.' }
$taskNewCode = [int]([regex]::Match($taskBadging,"versionCode='(\d+)'").Groups[1].Value)
$taskOldBadging = (& $taskAapt dump badging $taskPrevious) -join "`n"
if ($LASTEXITCODE -ne 0) { throw 'Previous APK metadata could not be read.' }
$taskOldCode = [int]([regex]::Match($taskOldBadging,"versionCode='(\d+)'").Groups[1].Value)
if ($taskNewCode -le $taskOldCode) { throw 'New versionCode must exceed the installed predecessor.' }
Add-Type -AssemblyName System.IO.Compression.FileSystem
$taskArchive = [IO.Compression.ZipFile]::OpenRead($taskNew)
try {
    foreach ($taskAsset in @('mobile.html','workday-ui.js','workday-ui.css','order-window.js','design-concept.js','design-concept.css','daily-usability.js','daily-usability.css','billing-ui.js','billing-ui.css')) {
        $taskEntry = $taskArchive.GetEntry('assets/' + $taskAsset)
        if (!$taskEntry) { throw "Missing asset: $taskAsset" }
        $taskReader = [IO.StreamReader]::new($taskEntry.Open())
        try { $taskContent = $taskReader.ReadToEnd() } finally { $taskReader.Dispose() }
        $taskSource = [IO.File]::ReadAllText((Join-Path $taskRoot ('android/app/src/main/assets/' + $taskAsset)))
        if ($taskContent -cne $taskSource) { throw "Outdated packaged asset: $taskAsset" }
    }
} finally { $taskArchive.Dispose() }
Write-Output "Android update verified: $ExpectedAppVersion (build $taskNewCode), stable app ID/signature, current UI assets."
& py -3.11 (Join-Path $taskRoot 'tests/apk_privacy_guard.py') --apk $taskNew
if ($LASTEXITCODE -ne 0) { throw 'APK-Datentrennung nicht bestätigt. Nicht freigeben.' }
& {
    $taskProofPath=Join-Path $taskRoot ("outputs/Release-Lifecycle-v"+$ExpectedAppVersion+".json")
    if(-not (Test-Path -LiteralPath $taskProofPath)){throw 'Fresh-Install-/Update-Nachweis fehlt. Nicht freigeben.'}
    $taskProof=Get-Content -LiteralPath $taskProofPath -Raw | ConvertFrom-Json
    $taskHash=(Get-FileHash -LiteralPath $taskNew -Algorithm SHA256).Hash
    if(-not $taskProof.success -or $taskProof.apk_sha256 -ine $taskHash -or $taskProof.version_code -ne $taskNewCode){throw 'Fresh-/Update-Nachweis gehört nicht zu dieser APK. Nicht freigeben.'}
}
Get-FileHash -LiteralPath $taskNew -Algorithm SHA256
