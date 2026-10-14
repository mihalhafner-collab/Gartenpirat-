$ErrorActionPreference = 'Stop'
$appTestRoot = Split-Path $PSScriptRoot -Parent
$sdkTestRoot = Join-Path $appTestRoot 'android-toolchain\sdk'
$downloads = Join-Path $appTestRoot 'android-toolchain\emulator-downloads'
New-Item -ItemType Directory -Path $downloads -Force | Out-Null
$packages = @(
    @{ Name='emulator'; Cache='sdkbin-1_b75fa71f-repository2-4_xml'; Archive='emulator-windows_x64-16428233.zip'; UrlBase='https://dl.google.com/android/repository/'; Target='emulator'; Revision='37.2.12'; Marker='emulator.exe' },
    @{ Name='system-images;android-35;google_apis;x86_64'; Cache='sdkbin-1_707bcb95-sys-img2-5_xml'; Archive='x86_64-35_r09.zip'; UrlBase='https://dl.google.com/android/repository/sys-img/google_apis/'; Target='system-images\android-35\google_apis'; Revision='9'; Marker='x86_64\system.img' }
)
foreach ($package in $packages) {
    $destination = Join-Path $sdkTestRoot $package.Target
    if (Test-Path -LiteralPath (Join-Path $destination $package.Marker)) {
        Write-Host "Bereits vorhanden: $($package.Name)"
        continue
    }
    [xml]$repository = Get-Content -LiteralPath (Join-Path $appTestRoot ('.android-user\cache\' + $package.Cache)) -Raw
    $remote = $repository.SelectNodes("//*[local-name()='remotePackage' and @path='$($package.Name)']") | Where-Object {
        $_.SelectSingleNode("./*[local-name()='archives']/*[local-name()='archive']/*[local-name()='complete']/*[local-name()='url'][text()='$($package.Archive)']")
    } | Select-Object -First 1
    if (-not $remote) { throw "Kein verifiziertes Google-Paket gefunden: $($package.Name)" }
    $archiveInfo = $remote.SelectSingleNode("./*[local-name()='archives']/*[local-name()='archive']/*[local-name()='complete'][*[local-name()='url']='$($package.Archive)']")
    $expectedSize = [long]$archiveInfo.SelectSingleNode("./*[local-name()='size']").InnerText
    $expectedHash = $archiveInfo.SelectSingleNode("./*[local-name()='checksum']").InnerText
    $license = $remote.SelectSingleNode("./*[local-name()='uses-license']").GetAttribute('ref')
    if (-not (Test-Path -LiteralPath (Join-Path $sdkTestRoot ('licenses\' + $license)))) { throw "SDK-Lizenz ist noch nicht hinterlegt: $license" }
    $archive = Join-Path $downloads $package.Archive
    if (-not (Test-Path -LiteralPath $archive) -or (Get-Item -LiteralPath $archive).Length -ne $expectedSize) {
        Write-Host "Download: $($package.Name), $expectedSize Bytes"
        if ($package.Name -eq 'emulator') {
            & curl.exe -4 --fail --location --retry 3 --connect-timeout 10 --max-time 900 --speed-limit 1024 --speed-time 45 -C - --output $archive ($package.UrlBase + $package.Archive)
            if ($LASTEXITCODE -ne 0) { throw 'Download unterbrochen. Vorhandene Teildatei bleibt für eine Fortsetzung erhalten.' }
        } else {
            & (Join-Path $PSScriptRoot 'download-emulator-image.ps1') -ArchivePath $archive -Url ($package.UrlBase + $package.Archive) -ExpectedSize $expectedSize
        }
    }
    if ((Get-Item -LiteralPath $archive).Length -ne $expectedSize -or (Get-FileHash -LiteralPath $archive -Algorithm SHA1).Hash -ne $expectedHash) { throw 'Paketgröße oder Prüfsumme stimmt nicht mit den offiziellen Google-Metadaten überein.' }
    Add-Type -AssemblyName System.IO.Compression.FileSystem
    $zip = [System.IO.Compression.ZipFile]::OpenRead($archive)
    try {
        $extractionRoot = if ($package.Name -eq 'emulator') { $sdkTestRoot } else { $destination }
        $packageDirectory = if ($package.Name -eq 'emulator') { $destination } else { Join-Path $destination 'x86_64' }
        $allowedRoot = [System.IO.Path]::GetFullPath($packageDirectory).TrimEnd('\') + '\'
        foreach ($entry in $zip.Entries) {
            $target = [System.IO.Path]::GetFullPath((Join-Path $extractionRoot $entry.FullName))
            if ($target.TrimEnd('\') -ne $allowedRoot.TrimEnd('\') -and -not $target.StartsWith($allowedRoot,[System.StringComparison]::OrdinalIgnoreCase)) { throw 'Ungültiger Pfad im SDK-Archiv.' }
        }
    } finally { $zip.Dispose() }
    New-Item -ItemType Directory -Path $destination -Force | Out-Null
    if ($package.Name -eq 'emulator') {
        # The Google archive includes its emulator/ top-level directory.
        Expand-Archive -LiteralPath $archive -DestinationPath $sdkTestRoot
    } else {
        # The system image archive includes its x86_64/ top-level directory.
        Expand-Archive -LiteralPath $archive -DestinationPath $destination
    }
    if (-not (Test-Path -LiteralPath (Join-Path $destination $package.Marker))) { throw 'SDK-Archiv hat nicht die erwartete Struktur.' }
    Write-Host "Installiert und Prüfsumme bestätigt: $($package.Name) $($package.Revision)"
}
& (Join-Path $PSScriptRoot 'register-emulator-packages.ps1')
