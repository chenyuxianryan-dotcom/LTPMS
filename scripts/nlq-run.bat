@echo off
REM LTPMS NL2SQL service (background, hidden window; log to .tools)
REM Idempotent: skips if 8001 is already listening.
REM The --child branch is this script relaunched in a hidden window; it runs
REM the service in the foreground there, so cmd handles quoting and redirection
REM natively (PowerShell Start-Process mangles args that contain spaces).
REM ASCII-only comments: cmd parses .bat in the system codepage (GBK).
setlocal
set "PY=C:\Users\ZhuanZ\.workbuddy\binaries\python\envs\default\Scripts\python.exe"
set "SRVDIR=E:\projects\AI LTPMS\nlq-service"
set "LOG=E:\projects\AI LTPMS\.tools\nlq-console.log"

if /i "%~1"=="--child" (
  cd /d "%SRVDIR%"
  "%PY%" -m app.main > "%LOG%" 2>&1
  exit /b
)

set "PID="
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":8001" ^| findstr "LISTENING"') do set "PID=%%a"
if defined PID (
  echo [NLQ] already listening on 8001 ^(PID %PID%^), skip.
  exit /b 0
)

powershell -NoProfile -Command "Start-Process -FilePath '%~f0' -ArgumentList '--child' -WindowStyle Hidden"

REM wait ~5s for the service to bind (ping works even with redirected stdin, timeout.exe does not)
ping -n 6 127.0.0.1 >nul

set "PID="
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":8001" ^| findstr "LISTENING"') do set "PID=%%a"
if defined PID (
  echo [NLQ] started on 8001 ^(PID %PID%^). Log: .tools\nlq-console.log
) else (
  echo [NLQ] FAILED to start - see .tools\nlq-console.log
  exit /b 1
)
exit /b 0
