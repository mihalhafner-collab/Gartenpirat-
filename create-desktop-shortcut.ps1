$ErrorActionPreference = 'Stop'
$desktop = [Environment]::GetFolderPath('Desktop')
$target = Join-Path $PSScriptRoot 'start-app.cmd'
$shortcutPath = Join-Path $desktop 'Haus- und Gartenservice.lnk'
$shell = New-Object -ComObject WScript.Shell
$shortcut = $shell.CreateShortcut($shortcutPath)
$shortcut.TargetPath = $target
$shortcut.WorkingDirectory = $PSScriptRoot
$shortcut.Description = 'Lokale Haus- und Gartenservice-App starten'
$shortcut.IconLocation = "$env:SystemRoot\System32\shell32.dll,167"
$shortcut.Save()
Write-Host "Desktop-Verknuepfung erstellt: $shortcutPath"
