@echo off
REM ============================================================
REM  LTPMS autostart (Task Scheduler onlogon, silent)
REM  MySQL 3307 -> Spring Boot 8080 -> NLQ 8001 -> Frontend 8000
REM  Ports already occupied => new instance exits by itself (idempotent)
REM  Stop everything: stop-all.bat (kills by listening ports)
REM ============================================================

start "" /min cmd /c ""E:\projects\AI LTPMS\.tools\mysql-run.bat""
timeout /t 10 /nobreak >nul

start "" /min cmd /c ""E:\projects\AI LTPMS\.tools\backend-run.bat""
timeout /t 15 /nobreak >nul

start "" /min cmd /c ""E:\projects\AI LTPMS\.tools\nlq-run.bat""
timeout /t 8 /nobreak >nul

start "" /min cmd /c ""E:\projects\AI LTPMS\.tools\frontend-run.bat""

exit /b 0
