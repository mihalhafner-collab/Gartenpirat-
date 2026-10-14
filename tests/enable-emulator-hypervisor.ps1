$ErrorActionPreference = 'Stop'
$auditRoot = Split-Path $PSScriptRoot -Parent
$statusPath = Join-Path $auditRoot 'outputs\Emulator-Hypervisor-Status.json'
$logPath = Join-Path $auditRoot 'outputs\Emulator-Hypervisor-Installation.log'
$status = [ordered]@{ feature = 'HypervisorPlatform'; success = $false; restartNeeded = $false; error = $null }
try {
    Start-Transcript -Path $logPath -Force | Out-Null
    $feature = Get-WindowsOptionalFeature -Online -FeatureName HypervisorPlatform
    if ($feature.State -eq 'Enabled') {
        $status.success = $true
    } else {
        $result = Enable-WindowsOptionalFeature -Online -FeatureName HypervisorPlatform -All -NoRestart
        $status.success = $true
        $status.restartNeeded = [bool]$result.RestartNeeded
    }
    $status.state = [string](Get-WindowsOptionalFeature -Online -FeatureName HypervisorPlatform).State
} catch {
    $status.error = $_.Exception.Message
} finally {
    $status | ConvertTo-Json | Set-Content -LiteralPath $statusPath -Encoding UTF8
    try { Stop-Transcript | Out-Null } catch {}
}
if (-not $status.success) { exit 1 }
