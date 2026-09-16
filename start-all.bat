@echo off
REM ============================================
REM  LTPMS one-click startup (LOCAL ONLY)
REM  MySQL 3307 -> Spring Boot 8080 -> NL2SQL 8001 -> Frontend 8000
REM  Each step delegates to scripts\*-run.bat: hidden background process,
REM  log to .tools\*-console.log, idempotent (skips if port busy).
REM  Stop everything: stop-all.bat
REM ============================================

REM [1/4] MySQL
call "E:\projects\AI LTPMS\scripts\mysql-run.bat"

REM [2/4] Spring Boot backend
call "E:\projects\AI LTPMS\scripts\backend-run.bat"

REM [3/4] NL2SQL service
call "E:\projects\AI LTPMS\scripts\nlq-run.bat"

REM [4/4] Frontend
call "E:\projects\AI LTPMS\scripts\frontend-run.bat"

echo.
echo ==============================================
echo  All services started (LOCAL ONLY).
echo  Access:  http://localhost:8000
echo  Account: admin / admin123
echo ==============================================
pause
