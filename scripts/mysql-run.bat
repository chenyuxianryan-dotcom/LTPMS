@echo off
REM LTPMS MySQL (background, hidden window; logs to .tools)
REM Idempotent: skips if 3307 is already listening.
REM The --child branch is this script relaunched in a hidden window; it runs
REM mysqld in the foreground there, so cmd handles quoting and redirection
REM natively (PowerShell Start-Process mangles args that contain spaces).
REM ASCII-only comments: cmd parses .bat in the system codepage (GBK).

if /i "%~1"=="--child" (
  "C:\Users\ZhuanZ\mysql\mysql-8.4.6-winx64\bin\mysqld.exe" --basedir="C:\Users\ZhuanZ\mysql\mysql-8.4.6-winx64" --datadir="E:\projects\AI LTPMS\.tools\mysql-data" --port=3307 --console > "E:\projects\AI LTPMS\.tools\mysql-console.log" 2>&1
  exit /b
)

setlocal
set "PID="
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":3307" ^| findstr "LISTENING"') do set "PID=%%a"
if defined PID (
  echo [MYSQL] already listening on 3307 ^(PID %PID%^), skip.
  exit /b 0
)

powershell -NoProfile -Command "Start-Process -FilePath '%~f0' -ArgumentList '--child' -WindowStyle Hidden"

REM wait ~10s for mysqld to bind (ping works even with redirected stdin, timeout.exe does not)
ping -n 11 127.0.0.1 >nul

set "PID="
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":3307" ^| findstr "LISTENING"') do set "PID=%%a"
if defined PID (
  echo [MYSQL] started on 3307 ^(PID %PID%^). Log: .tools\mysql-console.log
) else (
  echo [MYSQL] FAILED to start - see .tools\mysql-console.log
  exit /b 1
)
exit /b 0
