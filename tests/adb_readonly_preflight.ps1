param(
  [Parameter(Mandatory=$true)][string]$AdbPath,
  [Parameter(Mandatory=$true)][string]$Serial
)
$ErrorActionPreference = 'Stop'
$adbExe = $AdbPath
if (-not (Test-Path -LiteralPath $adbExe -PathType Leaf)) { throw 'ADB executable missing' }
$adbLines = @(& $adbExe devices -l)
$adbTargets = @($adbLines | Where-Object { $_ -match '^\S+\s+device(\s|$)' })
if (-not ($adbTargets | Where-Object { ($_ -split '\s+')[0] -eq $Serial })) { throw 'Explicit serial is not an authorized connected device' }
$adbSerial = $Serial
$adbCalls = [System.Collections.Generic.List[object]]::new()
function Read-Adb([string]$Label, [string[]]$Arguments) {
  $startInfo = [System.Diagnostics.ProcessStartInfo]::new()
  $startInfo.FileName = $adbExe
  $startInfo.UseShellExecute = $false
  $startInfo.CreateNoWindow = $true
  $startInfo.RedirectStandardOutput = $true
  $startInfo.RedirectStandardError = $true
  foreach ($argValue in (@('-s', $adbSerial) + $Arguments)) { [void]$startInfo.ArgumentList.Add($argValue) }
  $proc = [System.Diagnostics.Process]::new()
  $proc.StartInfo = $startInfo
  $watch = [System.Diagnostics.Stopwatch]::StartNew()
  [void]$proc.Start()
  $stdoutTask = $proc.StandardOutput.ReadToEndAsync()
  $stderrTask = $proc.StandardError.ReadToEndAsync()
  if (-not $proc.WaitForExit(15000)) {
    $proc.Kill()
    $watch.Stop()
    $adbCalls.Add([pscustomobject]@{label=$Label;exit_code=$null;timeout=$true;roundtrip_ms=[math]::Round($watch.Elapsed.TotalMilliseconds,3)})
    return ''
  }
  $watch.Stop()
  $stdout = $stdoutTask.GetAwaiter().GetResult()
  $null = $stderrTask.GetAwaiter().GetResult()
  $adbCalls.Add([pscustomobject]@{label=$Label;exit_code=$proc.ExitCode;timeout=$false;roundtrip_ms=[math]::Round($watch.Elapsed.TotalMilliseconds,3)})
  return $stdout.Trim()
}
function Read-Prop([string]$Property) { return Read-Adb $Property @('shell','getprop',$Property) }
$report = [ordered]@{
  format='hausgarten.adb-passive-preflight.v1'
  observed_at_utc=[DateTime]::UtcNow.ToString('o')
  authorization='Direct user request: Test per adb auszuführen; laptop used only as ADB bridge and technical protocol'
  package_expected='at.hausgarten.service'
  device_count=$adbTargets.Count
  selected_alias='adb-target-1'
  serial_published=$false
  scope='PASSIVE_READONLY; no app launch/stop/input/instrumentation/install or business-record reads'
}
$report.model=Read-Prop 'ro.product.model'
$report.android_version=Read-Prop 'ro.build.version.release'
$report.api_level=Read-Prop 'ro.build.version.sdk'
$report.qemu=Read-Prop 'ro.kernel.qemu'
$report.soc_model=Read-Prop 'ro.soc.model'
$report.cpu_abi=Read-Prop 'ro.product.cpu.abilist'
$report.cpu_present=Read-Adb 'cpu_present' @('shell','cat','/sys/devices/system/cpu/present')
$memory=Read-Adb 'system_memory' @('shell','cat','/proc/meminfo')
$memoryValues=[ordered]@{}
foreach ($key in @('MemTotal','MemAvailable')) {
  $match=[regex]::Match($memory, '(?m)^'+$key+':\s+(\d+)\s+kB')
  $memoryValues[$key+'_kib']=if($match.Success){[long]$match.Groups[1].Value}else{$null}
}
$report.memory=$memoryValues
$size=Read-Adb 'screen_size' @('shell','wm','size')
$density=Read-Adb 'screen_density' @('shell','wm','density')
$report.screen_sizes=@([regex]::Matches($size,'(?m)^(Physical|Override) size:\s*(\d+)x(\d+)') | ForEach-Object {[pscustomobject]@{kind=$_.Groups[1].Value;width_px=[int]$_.Groups[2].Value;height_px=[int]$_.Groups[3].Value}})
$report.display_densities=@([regex]::Matches($density,'(?m)^(Physical|Override) density:\s*(\d+)') | ForEach-Object {[pscustomobject]@{kind=$_.Groups[1].Value;dpi=[int]$_.Groups[2].Value}})
$disk=Read-Adb 'data_storage' @('shell','df','-k','/data')
$diskMatch=[regex]::Match($disk,'(?m)^\S+\s+(\d+)\s+(\d+)\s+(\d+)\s+(\d+)%\s+\S+\s*$')
$report.storage=if($diskMatch.Success){[ordered]@{total_kib=[long]$diskMatch.Groups[1].Value;used_kib=[long]$diskMatch.Groups[2].Value;free_kib=[long]$diskMatch.Groups[3].Value;used_percent=[int]$diskMatch.Groups[4].Value}}else{$null}
$battery=Read-Adb 'battery' @('shell','dumpsys','battery')
$batteryResult=[ordered]@{}
foreach($key in @('level','scale','status','health','temperature','voltage')) {
  $match=[regex]::Match($battery,'(?m)^\s*'+$key+':\s*(-?\d+)\s*$')
  $batteryResult[$key]=if($match.Success){[int]$match.Groups[1].Value}else{$null}
}
foreach($key in @('AC powered','USB powered','Wireless powered')) {
  $match=[regex]::Match($battery,'(?m)^\s*'+[regex]::Escape($key)+':\s*(true|false)\s*$')
  $batteryResult[$key]=if($match.Success){$match.Groups[1].Value -eq 'true'}else{$null}
}
$batteryResult['temperature_c']=if($null -ne $batteryResult.temperature){$batteryResult.temperature/10.0}else{$null}
$report.battery=$batteryResult
$thermal=Read-Adb 'thermal_status' @('shell','dumpsys','thermalservice')
$thermalMatch=[regex]::Match($thermal,'(?m)^\s*Thermal Status:\s*(\d+)')
$report.thermal_status=if($thermalMatch.Success){[int]$thermalMatch.Groups[1].Value}else{$null}
$display=Read-Adb 'display_refresh_metadata' @('shell','dumpsys','display')
$displayValues=[ordered]@{}
foreach($key in @('mActiveRenderFrameRate','mActiveModeId','mRenderFrameRate')) {
  $matches=[regex]::Matches($display,[regex]::Escape($key)+'\s*=\s*(\d+(?:\.\d+)?)')
  $displayValues[$key]=@($matches | ForEach-Object {[double]$_.Groups[1].Value} | Select-Object -Unique)
}
$report.display_refresh_metadata=$displayValues
$modeFields=[ordered]@{}
foreach($line in @($display -split '\r?\n' | Where-Object {$_ -match '^\s*mActiveSfDisplayMode='})){
  foreach($entry in [regex]::Matches($line,'\b(width|height|peakRefreshRate|vsyncRate|refreshRate|fps)=\s*(\d+(?:\.\d+)?)')){$modeFields[$entry.Groups[1].Value]=[double]$entry.Groups[2].Value}
}
$report.active_physical_mode_numeric_fields=$modeFields
$report.actual_refresh_rate_hz=$modeFields.vsyncRate
$webview=Read-Adb 'webview_provider' @('shell','dumpsys','webviewupdate')
$webMatch=[regex]::Match($webview,'Current WebView package \(name, version\): \(([A-Za-z0-9_.]+), ([0-9.]+)\)')
$report.webview=if($webMatch.Success){[ordered]@{package=$webMatch.Groups[1].Value;version=$webMatch.Groups[2].Value}}else{$null}
$packageDump=Read-Adb 'package_metadata' @('shell','dumpsys','package','at.hausgarten.service')
$versionName=[regex]::Match($packageDump,'(?m)^\s*versionName=([A-Za-z0-9._+-]+)')
$versionCode=[regex]::Match($packageDump,'(?m)^\s*versionCode=(\d+)')
$report.installed_version=if($versionName.Success){$versionName.Groups[1].Value}else{$null}
$report.installed_build=if($versionCode.Success){[int]$versionCode.Groups[1].Value}else{$null}
$report.granted_permission_flags=@([regex]::Matches($packageDump,'(?m)^\s+(android\.permission\.[A-Z0-9_]+): granted=(true|false)') | ForEach-Object {[pscustomobject]@{permission=$_.Groups[1].Value;granted=$_.Groups[2].Value -eq 'true'}} | Sort-Object permission,granted -Unique)
$requestedMatch=[regex]::Match($packageDump,'(?ms)^\s*requested permissions:\s*\r?\n(.*?)(?=^\s*(?:install permissions:|User \d+:|runtime permissions:)|\z)')
$report.requested_permissions=if($requestedMatch.Success){@([regex]::Matches($requestedMatch.Groups[1].Value,'(?m)^\s+(android\.permission\.[A-Z0-9_]+)\s*$') | ForEach-Object {$_.Groups[1].Value} | Select-Object -Unique)}else{@()}
$installer=Read-Adb 'installer' @('shell','cmd','package','list','packages','-i','at.hausgarten.service')
$installerMatch=[regex]::Match($installer,'package:at\.hausgarten\.service\s+installer=([A-Za-z0-9_.]+|null)')
$report.installer=if($installerMatch.Success){$installerMatch.Groups[1].Value}else{$null}
$apkPaths=Read-Adb 'installed_apk_paths' @('shell','pm','path','at.hausgarten.service')
$apkHashes=[System.Collections.Generic.List[object]]::new()
foreach($line in ($apkPaths -split '\r?\n')) {
  if($line.StartsWith('package:')) {
    $apkPath=$line.Substring(8)
    if($apkPath -notmatch '^/data/app/[A-Za-z0-9_./+=~@-]+\.apk$'){throw 'Unexpected APK path; no file access attempted'}
    $hashReply=Read-Adb 'installed_apk_sha256' @('shell','sha256sum',$apkPath)
    $hashMatch=[regex]::Match($hashReply,'^([0-9a-fA-F]{64})\s')
    $byteReply=Read-Adb 'installed_apk_bytes' @('shell','stat','-c','%s',$apkPath)
    $apkHashes.Add([pscustomobject]@{file_name=($apkPath -split '/')[-1];sha256=if($hashMatch.Success){$hashMatch.Groups[1].Value.ToLowerInvariant()}else{$null};bytes=if($byteReply -match '^\d+$'){[long]$byteReply}else{$null}})
  }
}
$report.installed_apks=@($apkHashes.ToArray())
$report.certificate_sha256=$null
$report.certificate_note='Independent certificate byte proof not collected; no APK pulled'
$network=Read-Adb 'network_transport_metadata' @('shell','dumpsys','connectivity')
$defaultMatch=[regex]::Match($network,'(?m)^\s*Active default network:\s*(\d+)')
$networkResult=[ordered]@{default_network_present=$defaultMatch.Success;default_transports=@();default_validated=$null}
if($defaultMatch.Success){
  $netId=$defaultMatch.Groups[1].Value
  foreach($line in @($network -split '\r?\n' | Where-Object {$_ -match ('network\{'+$netId+'\}|\bnetworkId[=: ]+'+$netId+'\b')})){
    $transportMatch=[regex]::Match($line,'Transports:\s*([A-Z_| ]+?)(?=\s+Capabilities:|\r?\n|$)')
    if($transportMatch.Success){$networkResult.default_transports+=,$transportMatch.Groups[1].Value.Trim();$networkResult.default_validated=$line -match '\bVALIDATED\b'}
  }
}
$report.network=$networkResult
$processIdReply=Read-Adb 'app_running' @('shell','pidof','at.hausgarten.service')
$report.app_running=($processIdReply -match '^\d+(?: \d+)*$')
$report.isolation_proven=$false
$report.isolation_note='User confirmed productive Pixel with real data. No application state changes allowed by this preflight.'
$report.calls=@($adbCalls.ToArray())
$report.target_role='PRODUCTIVE_USER_CONFIRMED'
$report.expected_apk_sha256='5bdaa005664ac2a436ac50b7b4acff3f19bcaa09ce05f3568a1c6147937bb7d2'
$report.exact_original108_apk_hash_match=($report.installed_apks.Count -eq 1 -and $report.installed_apks[0].sha256 -eq $report.expected_apk_sha256)
$report.checks=@(
  [pscustomobject]@{id='ADB01';requirement='Explicit authorized ADB target';status='PASS'},
  [pscustomobject]@{id='ADB02';requirement='Expected package version1.55.3/build108';status=if($report.installed_version -eq '1.55.3' -and $report.installed_build -eq 108){'PASS'}else{'FAIL'}},
  [pscustomobject]@{id='ADB03';requirement='Installed executable APK hash equals original108';status=if($report.exact_original108_apk_hash_match){'PASS'}else{'FAIL'}},
  [pscustomobject]@{id='ADB04';requirement='Isolated synthetic test installation';status='BLOCKED'}
)
$report.completed_at_utc=[DateTime]::UtcNow.ToString('o')
$resultDirectory=Join-Path $env:LOCALAPPDATA 'HausGarten-Adb-Testtools\results'
New-Item -ItemType Directory -Path $resultDirectory -Force | Out-Null
$resultPath=Join-Path $resultDirectory 'adb-passive-preflight-2026-10-28.json'
$json=$report | ConvertTo-Json -Depth 12
[IO.File]::WriteAllText($resultPath,$json,[Text.UTF8Encoding]::new($false))
$json
