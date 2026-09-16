@echo off
REM ============================================
REM  LTPMS one-click stop (kills by listening port)
REM ============================================
echo Stopping LTPMS services ...

for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":3307" ^| findstr "LISTENING"') do taskkill /f /pid %%a >nul 2>&1
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":8080" ^| findstr "LISTENING"') do taskkill /f /pid %%a >nul 2>&1
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":8001" ^| findstr "LISTENING"') do taskkill /f /pid %%a >nul 2>&1
for /f "tokens=5" %%a in ('netstat -ano ^| findstr ":8000" ^| findstr "LISTENING"') do taskkill /f /pid %%a >nul 2>&1

echo All LTPMS services stopped (ports 3307 / 8080 / 8001 / 8000).
pause
