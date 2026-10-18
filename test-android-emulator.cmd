@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0tests\run-android-emulator-tests.ps1"
if errorlevel 1 pause
