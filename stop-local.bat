@echo off
setlocal EnableExtensions
title Dongleme - Stop Local Dev
cd /d "%~dp0"

set "found=0"

call :kill_port 8080 backend
call :kill_port 5173 frontend
call :kill_port 4321 website
call :kill_port 13306 ssh-tunnel

if "%found%"=="0" echo No running services found.
echo.
pause
exit /b 0

:kill_port
set "killPort=%~1"
set "killLabel=%~2"
for /f "tokens=5" %%p in ('netstat -ano ^| findstr /R /C:":%killPort% .*LISTENING"') do (
    taskkill /PID %%p /T /F >nul 2>&1
    if not errorlevel 1 (
        echo Stopped %killLabel% - PID %%p
        set "found=1"
    )
)
exit /b 0
