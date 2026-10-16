$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
Start-Process 'http://localhost:8765'
py -3.11 server.py
