$ErrorActionPreference = 'Stop'
$appTestRoot = Split-Path $PSScriptRoot -Parent
$adb = Join-Path $appTestRoot 'android-toolchain\sdk\platform-tools\adb.exe'
$serial = 'emulator-5580'
$expectedName = 'HausGarten_QA_Pixel10Pro_API35'
$package = 'at.hausgarten.service'
function Invoke-TestAdb {
    param([string[]]$Arguments)
    $result = & $adb -s $serial @Arguments 2>&1
    if ($LASTEXITCODE -ne 0) { throw ($result -join "`n") }
    return $result
}
$actualName = Invoke-TestAdb -Arguments @('emu', 'avd', 'name')
if ($actualName -notcontains $expectedName) { throw 'Falsches Gerät. Die APK wird ausschließlich auf dem eigenen QA-Emulator installiert.' }
$booted = Invoke-TestAdb -Arguments @('shell', 'getprop', 'sys.boot_completed')
if (($booted -join '').Trim() -ne '1') { throw 'Android startet noch. Bitte das Skript nach dem vollständigen Start erneut ausführen.' }
$apk = Join-Path $appTestRoot 'outputs\Haus-und-Gartenservice-Pixel-v1.27.1.apk'
if (-not (Test-Path -LiteralPath $apk)) { throw 'Die geprüfte APK 1.27.1 fehlt.' }
Invoke-TestAdb -Arguments @('install', '-r', $apk)
# Deterministic transitions, only after the exact QA-device guard above.
foreach ($setting in @('window_animation_scale','transition_animation_scale','animator_duration_scale')) {
    Invoke-TestAdb -Arguments @('shell','settings','put','global',$setting,'0')
}
# Do not overwrite an existing emulator test session. Never configure productive Drive.
$hasPreferences = Invoke-TestAdb -Arguments @('shell', "run-as $package sh -c 'if test -f shared_prefs/haus_garten.xml; then echo existing; else echo fresh; fi'")
if (($hasPreferences -join '').Trim() -eq 'fresh') {
    $snapshot = Get-Content -LiteralPath (Join-Path $PSScriptRoot 'emulator-test-snapshot.json') -Raw | ConvertFrom-Json | ConvertTo-Json -Depth 12 -Compress
    $document = New-Object System.Xml.XmlDocument
    $map = $document.CreateElement('map')
    $document.AppendChild($map) | Out-Null
    $entry = $document.CreateElement('string')
    $entry.SetAttribute('name', 'drive_snapshot')
    $entry.InnerText = $snapshot
    $map.AppendChild($entry) | Out-Null
    $fixturePath = Join-Path $appTestRoot 'outputs\Emulator-Test-Preferences.xml'
    $document.Save($fixturePath)
    Invoke-TestAdb -Arguments @('push', $fixturePath, '/data/local/tmp/hausgarten-qa-preferences.xml')
    Invoke-TestAdb -Arguments @('shell', 'run-as', $package, 'mkdir', '-p', 'shared_prefs')
    Invoke-TestAdb -Arguments @('shell', 'run-as', $package, 'cp', '/data/local/tmp/hausgarten-qa-preferences.xml', 'shared_prefs/haus_garten.xml')
    Write-Host 'Künstliche Testdaten eingesetzt. Keine Drive-Freigabe und keine produktiven Änderungen.'
} else {
    Write-Host 'Vorhandene Emulator-Testdaten bleiben erhalten.'
}
Invoke-TestAdb -Arguments @('shell', 'am', 'start', '-n', ($package + '/.MainActivity'))
Invoke-TestAdb -Arguments @('shell', 'dumpsys', 'package', $package) | Select-String -Pattern 'versionCode=','versionName='
