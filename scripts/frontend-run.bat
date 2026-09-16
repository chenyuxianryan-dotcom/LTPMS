@echo off
REM LTPMS frontend preview (background, hidden window; log to .tools)
REM Idempotent: skips if 8000 is already listening.
REM The --child branch is this script relaunched in a hidden window; it runs
REM vite in the foreground there, so cmd handles quoting and redirection
REM natively (PowerShell Start-Process mangles args that contain spaces).
REM ASCII-only comments: cmd parses .bat in the system codepage (GBK).
setlocal
set "NODE=C:\Users\ZhuanZ\.workbuddy\binaries\node\versions\22.22.2-3\node.exe"
set "APPDIR=E:\projects\AI LTPMS\frontend"
set "LOG=E:\projects\AI LTPMS\.tools\frontend-console.log"

if /i "%~1"=="--child" (
  cd /d "%APPDIR%"
  "%NODE%" node_modules\vite\bin\vite.js preview --port 8000 --strictPort --host 127.0.0.1 > "%LOG%" 2>&1
  exit /b
)

set "PID="
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":8000" ^| findstr "LISTENING"') do set "PID=%%a"
if defined PID (
  echo [FRONTEND] already listening on 8000 ^(PID %PID%^), skip.
  exit /b 0
)

powershell -NoProfile -Command "Start-Process -FilePath '%~f0' -ArgumentList '--child' -WindowStyle Hidden"

REM wait ~5s for vite preview to bind (ping works even with redirected stdin, timeout.exe does not)
ping -n 6 127.0.0.1 >nul

set "PID="
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":8000" ^| findstr "LISTENING"') do set "PID=%%a"
if defined PID (
  echo [FRONTEND] started on 8000 ^(PID %PID%^). Log: .tools\frontend-console.log
) else (
  echo [FRONTEND] FAILED to start - see .tools\frontend-console.log
  exit /b 1
)
exit /b 0
