@echo off
cd /d "%~dp0"
echo Haus- ^& Gartenservice wird gestartet ...
start "" http://localhost:8765
py -3.11 server.py
if errorlevel 1 (
  echo.
  echo Python 3.11 konnte nicht gestartet werden. Bitte pruefen Sie die Installation.
  pause
)
