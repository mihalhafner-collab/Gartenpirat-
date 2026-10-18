@echo off
setlocal
cd /d "%~dp0"
set "HAUSGARTEN_MCP_ENABLED=1"
set "PYTHONPATH=%~dp0.dependencies\mcp;%PYTHONPATH%"
py -3.11 mcp_integration\run_server.py
endlocal
