@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0tests\start-android-emulator.ps1" -ShowWindow -InstallApp
if errorlevel 1 pause
