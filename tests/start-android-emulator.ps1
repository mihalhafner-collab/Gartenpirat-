param([switch]$ShowWindow, [switch]$InstallApp, [switch]$StayRunning)
$ErrorActionPreference = 'Stop'
$appTestRoot = Split-Path $PSScriptRoot -Parent
$sdkTestRoot = Join-Path $appTestRoot 'android-toolchain\sdk'
$env:JAVA_HOME = Join-Path $appTestRoot 'android-toolchain\jdk\jdk-17.0.20.1+1'
$env:ANDROID_HOME = $sdkTestRoot
$env:ANDROID_SDK_ROOT = $sdkTestRoot
$env:ANDROID_USER_HOME = Join-Path $appTestRoot '.android-user'
$env:ANDROID_AVD_HOME = Join-Path $env:ANDROID_USER_HOME 'avd'
$avdName = 'HausGarten_QA_Pixel10Pro_API35'
$avdManager = Join-Path $sdkTestRoot 'cmdline-tools\latest\bin\avdmanager.bat'
$emulator = Join-Path $sdkTestRoot 'emulator\emulator.exe'
$avdDirectory = Join-Path $env:ANDROID_AVD_HOME ($avdName + '.avd')
$serial = 'emulator-5580'
if (-not (Test-Path -LiteralPath $emulator)) { throw 'Android-Emulator noch nicht installiert.' }
if (-not (Test-Path -LiteralPath $env:ANDROID_AVD_HOME)) {
    New-Item -ItemType Directory -Path $env:ANDROID_AVD_HOME | Out-Null
}
if (-not (Test-Path -LiteralPath (Join-Path $avdDirectory 'config.ini'))) {
    'no' | & $avdManager create avd --name $avdName --package 'system-images;android-35;google_apis;x86_64' --device 'pixel_10_pro' --path $avdDirectory
    if ($LASTEXITCODE -ne 0) { throw 'Das Testgerät konnte nicht erstellt werden.' }
}
$acceleration = & $emulator -accel-check 2>&1
$acceleration | ForEach-Object { Write-Host $_ }
if ($LASTEXITCODE -ne 0) {
    throw 'Hardwarebeschleunigung noch nicht nutzbar. Nach Aktivierung der Windows-Hypervisor-Plattform Windows neu starten und dieses Skript erneut ausführen.'
}
$listener = Get-NetTCPConnection -State Listen -LocalPort 5580 -ErrorAction SilentlyContinue
if ($listener) { throw 'Emulator-Port 5580 ist bereits belegt. Es wird kein fremdes Gerät gestoppt.' }
$arguments = @('-avd', $avdName, '-port', '5580', '-memory', '1536', '-cores', '2', '-gpu', 'auto', '-no-snapshot', '-no-boot-anim', '-no-audio')
if (-not $ShowWindow) { $arguments += '-no-window' }
$process = Start-Process -FilePath $emulator -ArgumentList $arguments -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $appTestRoot 'outputs\Emulator-Start.log') -RedirectStandardError (Join-Path $appTestRoot 'outputs\Emulator-Fehler.log')
[PSCustomObject]@{ Name = $avdName; Serial = $serial; ProcessId = $process.Id; Visible = [bool]$ShowWindow; Android = '15 / API 35' } | ConvertTo-Json
Write-Host 'Der erste Start kann mehrere Minuten dauern. Danach install-android-emulator-app.ps1 ausführen.'
if ($InstallApp) {
    $adb = Join-Path $sdkTestRoot 'platform-tools\adb.exe'
    $deadline = (Get-Date).AddMinutes(8)
    do {
        Start-Sleep -Seconds 3
        $booted = & $adb -s $serial shell getprop sys.boot_completed 2>$null
        if ($LASTEXITCODE -eq 0 -and ($booted -join '').Trim() -eq '1') { break }
    } while ((Get-Date) -lt $deadline -and -not $process.HasExited)
    if ($LASTEXITCODE -ne 0 -or ($booted -join '').Trim() -ne '1') { throw 'Android ist noch nicht vollständig gestartet. Installationsskript später ausführen; keine Daten wurden gelöscht.' }
    & (Join-Path $PSScriptRoot 'install-android-emulator-app.ps1')
}
if($StayRunning) {
    # Keep the task's process lifetime attached during automated acceptance.
    # Detached helpers may be reclaimed by a restricted execution host.
    $process.WaitForExit()
    exit $process.ExitCode
}
