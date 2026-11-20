@echo off
setlocal EnableExtensions
title Dongleme - Local Dev
cd /d "%~dp0"

if not exist ".env" (
    echo [ERROR] .env not found. Copy .env.example and fill it first.
    pause
    exit /b 1
)

if not exist ".run" mkdir ".run"

rem Load .env into environment
for /f "usebackq eol=# tokens=1,* delims==" %%a in (".env") do (
    if not "%%a"=="" set "%%a=%%b"
)
if not defined DB_HOST set "DB_HOST=127.0.0.1"
if not defined SERVER_PORT set "SERVER_PORT=8080"

echo [1/3] Checking SSH tunnel...
call :port_open 13306
if errorlevel 1 (
    if not defined DEPLOY_HOST goto :tunnel_skip
    if not defined DEPLOY_SSH_KEY goto :tunnel_skip
    if not exist "%DEPLOY_SSH_KEY%" (
        echo       SSH key not found, skip tunnel
        goto :tunnel_skip
    )
    start "dongleme-tunnel" /min cmd /c "ssh -i %DEPLOY_SSH_KEY% -N -L 13306:127.0.0.1:3306 %DEPLOY_HOST% > .run\tunnel.log 2>&1"
    call :wait_port 13306 15
    if errorlevel 1 (
        echo       tunnel not ready in 15s, see .run\tunnel.log
    ) else (
        echo       tunnel ready at 127.0.0.1:13306
    )
) else (
    echo       port 13306 already listening, skip tunnel
)
goto :tunnel_done

:tunnel_skip
echo       DEPLOY_HOST / DEPLOY_SSH_KEY not configured, skip tunnel

:tunnel_done

echo [2/3] Checking backend...
call :port_open 8080
if errorlevel 1 (
    if not exist "backend-spring\target\dongleme-backend.jar" (
        echo       jar not found, building backend...
        pushd backend-spring
        call mvn -B -DskipTests package
        if errorlevel 1 (
            popd
            echo [ERROR] backend build failed
            pause
            exit /b 1
        )
        popd
    )
    start "dongleme-backend" /min /d "backend-spring" cmd /c "java -jar target\dongleme-backend.jar > ..\.run\backend.log 2>&1"
    echo       backend starting...
    call :wait_port 8080 40
    if errorlevel 1 (
        echo       backend not ready in 40s, see .run\backend.log
    ) else (
        echo       backend ready at 127.0.0.1:8080
    )
) else (
    echo       port 8080 already listening, skip backend
)

echo [3/3] Checking frontend...
call :port_open 5173
if errorlevel 1 (
    start "dongleme-frontend" /min cmd /c "npm run dev -- --host 127.0.0.1 > .run\frontend.log 2>&1"
    echo       frontend starting...
    call :wait_port 5173 30
    if errorlevel 1 (
        echo       frontend not ready in 30s, see .run\frontend.log
    ) else (
        echo       frontend ready at 127.0.0.1:5173
    )
) else (
    echo       port 5173 already listening, skip frontend
)

echo.
echo ============================================
echo   frontend  http://127.0.0.1:5173
echo   admin     http://127.0.0.1:5173/admin
echo   backend   http://127.0.0.1:8080/api/health
echo   logs      .run\backend.log / .run\frontend.log
echo   stop      stop-local.bat
echo ============================================
pause
exit /b 0

:port_open
netstat -ano | findstr /R /C:":%~1 .*LISTENING" >nul
exit /b %errorlevel%

:wait_port
set "waitPort=%~1"
set /a waitTries=%~2
:wait_loop
call :port_open %waitPort%
if not errorlevel 1 exit /b 0
set /a waitTries-=1
if %waitTries% leq 0 exit /b 1
ping -n 2 127.0.0.1 >nul
goto wait_loop
