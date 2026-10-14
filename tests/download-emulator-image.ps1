param([Parameter(Mandatory=$true)][string]$ArchivePath,
      [Parameter(Mandatory=$true)][string]$Url,
      [Parameter(Mandatory=$true)][long]$ExpectedSize)
$ErrorActionPreference = 'Stop'
$allowedDownloadRoot = [IO.Path]::GetFullPath((Join-Path (Split-Path $PSScriptRoot -Parent) 'android-toolchain\emulator-downloads')).TrimEnd('\') + '\'
$ArchivePath = [IO.Path]::GetFullPath($ArchivePath)
if (-not $ArchivePath.StartsWith($allowedDownloadRoot,[StringComparison]::OrdinalIgnoreCase) -or $Url -ne 'https://dl.google.com/android/repository/sys-img/google_apis/x86_64-35_r09.zip') { throw 'Unerwartetes Downloadziel.' }
$chunkSize = [long][Math]::Ceiling($ExpectedSize / 4.0)
$parts = @()
$transfers = @()
try {
    for ($index=0; $index -lt 4; $index++) {
        $begin = $index * $chunkSize
        $end = [Math]::Min($ExpectedSize - 1, $begin + $chunkSize - 1)
        $part = $ArchivePath + '.part' + $index
        $length = $end - $begin + 1
        $parts += @{ Path=$part; Size=$length }
        $already = if (Test-Path -LiteralPath $part) { (Get-Item -LiteralPath $part).Length } else { 0 }
        if ($already -eq $length) { continue }
        if ($already -gt $length) { throw 'Zu große Download-Teildatei. Keine automatische Überschreibung.' }
        $start = $begin + $already
        $fileStream = [IO.File]::Open($part,[IO.FileMode]::OpenOrCreate,[IO.FileAccess]::Write,[IO.FileShare]::Read)
        $fileStream.Position = $already
        $process = New-Object Diagnostics.Process
        $process.StartInfo.FileName = 'curl.exe'
        $process.StartInfo.Arguments = "-4 --silent --show-error --fail --location --connect-timeout 10 --max-time 3600 --range $start-$end `"$Url`""
        $process.StartInfo.UseShellExecute = $false
        $process.StartInfo.CreateNoWindow = $true
        $process.StartInfo.RedirectStandardOutput = $true
        $process.StartInfo.RedirectStandardError = $true
        if (-not $process.Start()) { $fileStream.Dispose(); throw 'Downloadprozess konnte nicht starten.' }
        $transfers += @{ Process=$process; Stream=$fileStream; Copy=$process.StandardOutput.BaseStream.CopyToAsync($fileStream,262144); Error=$process.StandardError.ReadToEndAsync() }
        Write-Host "Systemabbild-Teil ${index}: $already von $length Bytes vorhanden; Fortsetzung ab $start."
    }
    foreach ($transfer in $transfers) {
        $transfer.Copy.GetAwaiter().GetResult()
        $transfer.Process.WaitForExit()
        $errorText = $transfer.Error.GetAwaiter().GetResult()
        if ($transfer.Process.ExitCode -ne 0) { throw "Download unterbrochen: $errorText. Teildateien bleiben erhalten." }
    }
} finally {
    foreach ($transfer in $transfers) {
        if (-not $transfer.Process.HasExited) { $transfer.Process.Kill(); $transfer.Process.WaitForExit() }
        $transfer.Stream.Dispose()
        $transfer.Process.Dispose()
    }
}
foreach ($part in $parts) {
    if ((Get-Item -LiteralPath $part.Path).Length -ne $part.Size) { throw 'Eine Teildatei ist noch nicht vollständig.' }
}
$assembled = [IO.File]::Open($ArchivePath,[IO.FileMode]::Create,[IO.FileAccess]::Write,[IO.FileShare]::None)
try {
    foreach ($part in $parts) {
        $inputStream = [IO.File]::OpenRead($part.Path)
        try { $inputStream.CopyTo($assembled) } finally { $inputStream.Dispose() }
    }
} finally { $assembled.Dispose() }
Write-Host 'Systemabbild zusammengesetzt. Offizielle Prüfsumme wird vor Installation geprüft.'
