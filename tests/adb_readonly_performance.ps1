param(
  [Parameter(Mandatory=$true)][string]$AdbPath,
  [Parameter(Mandatory=$true)][string]$Serial
)
$ErrorActionPreference='Stop'
$adbExe=$AdbPath
$targets=@(& $adbExe devices -l | Where-Object { $_ -match '^\S+\s+device(\s|$)' })
if(-not($targets | Where-Object {($_ -split '\s+')[0] -eq $Serial})){throw 'Explicit serial not authorized'}
$serial=$Serial
function Get-Tech([string[]]$ArgsList){ $value=& $adbExe -s $serial @ArgsList; if($LASTEXITCODE -ne 0){return ''}; return ($value -join [Environment]::NewLine) }
$result=[ordered]@{format='hausgarten.adb-passive-performance.v1';observed_at_utc=[DateTime]::UtcNow.ToString('o');device_alias='adb-target-1';new_synthetic_scenarios=0;app_state_changed=$false;scope='Existing running process; cumulative statistics only; no reset or start/stop';memory_samples=@()}
$disk=Get-Tech @('shell','df','-k','/data')
$result.storage_rows=@($disk -split '\r?\n' | Where-Object {$_ -match '^\S+\s+\d+\s+\d+\s+\d+'} | ForEach-Object {$fields=$_ -split '\s+';[pscustomobject]@{total_kib=[long]$fields[1];used_kib=[long]$fields[2];free_kib=[long]$fields[3];used_percent=($fields[4] -replace '%','');mount_alias='app_data_volume'}})
$gfx=Get-Tech @('shell','dumpsys','gfxinfo','at.hausgarten.service')
$gfxValues=[ordered]@{}
foreach($label in @('Total frames rendered','Janky frames','50th percentile','90th percentile','95th percentile','99th percentile','Number Missed Vsync','Number High input latency','Number Slow UI thread','Number Slow bitmap uploads','Number Slow issue draw commands','Number Frame deadline missed','Number GPU deadline missed')) {
  $match=[regex]::Match($gfx,'(?m)^\s*'+[regex]::Escape($label)+':\s*([0-9.]+)(?:\s*(ms|%))?')
  $gfxValues[$label]=if($match.Success){[ordered]@{value=[double]$match.Groups[1].Value;unit=$match.Groups[2].Value}}else{$null}
}
$statsSince=[regex]::Match($gfx,'Stats since:\s*(\d+)ns')
$result.gfxinfo=[ordered]@{values=$gfxValues;stats_since_monotonic_ns=if($statsSince.Success){$statsSince.Groups[1].Value}else{$null};scope='Pre-existing cumulative gfxinfo; unknown actions and changing refresh rate; not new benchmark samples'}
$cpu=Get-Tech @('shell','dumpsys','cpuinfo')
$cpuLine=[regex]::Match($cpu,'(?m)^\s*([0-9.]+)%\s+\d+/at\.hausgarten\.service:\s*([0-9.]+)% user \+\s*([0-9.]+)% kernel')
$result.cpu_snapshot=if($cpuLine.Success){[ordered]@{total_percent=[double]$cpuLine.Groups[1].Value;user_percent=[double]$cpuLine.Groups[2].Value;kernel_percent=[double]$cpuLine.Groups[3].Value;scope='dumpsys existing system sampling window'}}else{$null}
$procstats=Get-Tech @('shell','dumpsys','procstats','at.hausgarten.service')
$result.procstats=[ordered]@{response_nonempty=($procstats.Length -gt 0);package_mentioned=$procstats.Contains('at.hausgarten.service');scope='Historical procstats returned; raw text and unrelated process metadata not retained'}
$exits=Get-Tech @('shell','dumpsys','activity','exit-info','at.hausgarten.service')
$exitReasons=@([regex]::Matches($exits,'\breason=(\d+)(?:\s*\(([^)\r\n]*)\))?') | ForEach-Object {
  [pscustomobject]@{code=[int]$_.Groups[1].Value;name=($_.Groups[2].Value -replace '[^A-Z_ ]','')}
})
$result.historical_exit_primary_count=$exitReasons.Count
$result.historical_exit_reason_counts=@($exitReasons | Group-Object code,name | ForEach-Object {
  [pscustomobject]@{code=$_.Group[0].code;name=$_.Group[0].name;count=$_.Count}
})
$result.historical_exits_note='Historical primary reasons only; no subreason duplication, no current-build attribution or newly reproduced crash; raw descriptions/paths discarded'
for($index=0;$index -lt 3;$index++){
  $mem=Get-Tech @('shell','dumpsys','meminfo','at.hausgarten.service')
  $values=[ordered]@{index=$index;observed_at_utc=[DateTime]::UtcNow.ToString('o')}
  foreach($label in @('TOTAL PSS','TOTAL RSS','TOTAL SWAP PSS')){
    $match=[regex]::Match($mem,[regex]::Escape($label)+':\s*(\d+)')
    $values[$label+'_kib']=if($match.Success){[long]$match.Groups[1].Value}else{$null}
  }
  foreach($label in @('Java Heap','Native Heap','Code','Stack','Graphics','Private Other','System')){
    $match=[regex]::Match($mem,'(?m)^\s*'+[regex]::Escape($label)+':\s*(\d+)')
    $values[$label+'_pss_kib']=if($match.Success){[long]$match.Groups[1].Value}else{$null}
  }
  $result.memory_samples+=,[pscustomobject]$values
}
$result.memory_scope='Three passive consecutive snapshots; no repeated user workflow, no leak inference'
$result.finished_at_utc=[DateTime]::UtcNow.ToString('o')
$json=$result | ConvertTo-Json -Depth 14
$resultDirectory=Join-Path $env:LOCALAPPDATA 'HausGarten-Adb-Testtools\results'
[IO.File]::WriteAllText((Join-Path $resultDirectory 'adb-passive-performance-2026-10-28.json'),$json,[Text.UTF8Encoding]::new($false))
$json
