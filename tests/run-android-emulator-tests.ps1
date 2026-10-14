$ErrorActionPreference = 'Stop'
$nodeCommand = Get-Command node -ErrorAction SilentlyContinue
$nodePath = if ($nodeCommand) { $nodeCommand.Source } else {
    Join-Path $env:USERPROFILE '.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin\node.exe'
}
if (-not (Test-Path -LiteralPath $nodePath)) {
    # The bundled runtime is user-local, not necessarily on Explorer's PATH.
    $nodePath = Join-Path $env:USERPROFILE '.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin\node.exe'
}
if (-not (Test-Path -LiteralPath $nodePath)) { throw 'Die Node-Testlaufzeit fehlt. Tests aus Codex starten; keine App-Daten wurden geändert.' }
& $nodePath (Join-Path $PSScriptRoot 'android-emulator.test.js')
exit $LASTEXITCODE
