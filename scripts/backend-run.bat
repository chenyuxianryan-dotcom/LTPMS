@echo off
REM LTPMS backend (background, hidden window; log to .tools)
REM Idempotent: skips if 8080 is already listening.
REM The --child branch is this script relaunched in a hidden window; it runs
REM java in the foreground there, so cmd handles quoting and redirection
REM natively (PowerShell Start-Process mangles args that contain spaces).
REM ASCII-only comments: cmd parses .bat in the system codepage (GBK).
setlocal
set "JAVA=C:\Users\ZhuanZ\.jdks\ms-17.0.16\bin\java.exe"
set "APPDIR=E:\projects\AI LTPMS\backend"
set "LOG=E:\projects\AI LTPMS\.tools\backend-console.log"
set "DB_PORT=3307"
set "DB_USER=ltpms"
set "DB_PASSWORD=ltpms"
set "JWT_SECRET=O2yNdfBAnYuRQsUGPKSgDAUo0Q1KV+fPE7rXmo9F2rxBHeJ5UgrVwvl3Q0JHj2yis13dKFESNrGxMihsfJN1sw=="
set "NLQ_INTERNAL_TOKEN=dev-internal-token"

if /i "%~1"=="--child" (
  cd /d "%APPDIR%"
  "%JAVA%" -jar target\ltpms-backend-0.0.1-SNAPSHOT.jar --server.port=8080 --server.address=127.0.0.1 > "%LOG%" 2>&1
  exit /b
)

set "PID="
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":8080" ^| findstr "LISTENING"') do set "PID=%%a"
if defined PID (
  echo [BACKEND] already listening on 8080 ^(PID %PID%^), skip.
  exit /b 0
)

powershell -NoProfile -Command "Start-Process -FilePath '%~f0' -ArgumentList '--child' -WindowStyle Hidden"

REM wait ~20s for Spring Boot to bind (ping works even with redirected stdin, timeout.exe does not)
ping -n 21 127.0.0.1 >nul

set "PID="
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":8080" ^| findstr "LISTENING"') do set "PID=%%a"
if defined PID (
  echo [BACKEND] started on 8080 ^(PID %PID%^). Log: .tools\backend-console.log
) else (
  echo [BACKEND] FAILED to start - see .tools\backend-console.log
  exit /b 1
)
exit /b 0
