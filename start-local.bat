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

echo [1/4] Checking SSH tunnel...
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

echo [2/4] Checking backend...
call :port_open 8080
if errorlevel 1 (
    set "JAR=backend-spring\target\dongleme-backend.jar"
    set "NEED_BUILD=0"
    if not exist "%JAR%" set "NEED_BUILD=1"
    rem A runnable jar is a ~30MB fat jar. A few hundred KB means repackage failed.
    if exist "%JAR%" for %%A in ("%JAR%") do if %%~zA LSS 5000000 set "NEED_BUILD=1"
    if "%NEED_BUILD%"=="1" (
        echo       jar missing or invalid, building backend...
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

echo [3/4] Checking frontend...
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

echo [4/4] Checking website...
call :port_open 4321
if errorlevel 1 (
    if not exist "site\index.html" (
        echo       site\index.html not found, skip website
    ) else (
        where python >nul 2>&1
        if errorlevel 1 (
            echo       python not found, skip website
        ) else (
            start "dongleme-site" /min /d "site" cmd /c "python -m http.server 4321 --bind 127.0.0.1 > ..\.run\site.log 2>&1"
            echo       website starting...
            call :wait_port 4321 15
            if errorlevel 1 (
                echo       website not ready in 15s, see .run\site.log
            ) else (
                echo       website ready at 127.0.0.1:4321
            )
        )
    )
) else (
    echo       port 4321 already listening, skip website
)

echo.
echo ============================================
echo   frontend  http://127.0.0.1:5173
echo   admin     http://127.0.0.1:5173/admin
echo   website   http://127.0.0.1:4321
echo   backend   http://127.0.0.1:8080/api/health
echo   logs      .run\backend.log / .run\frontend.log / .run\site.log
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
