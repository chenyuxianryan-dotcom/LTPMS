@echo off
REM ============================================
REM  LTPMS one-click startup (LOCAL ONLY)
REM  MySQL 3307 -> Spring Boot 8080 -> Frontend 8000
REM  Both backend and frontend bind to 127.0.0.1 (no LAN access)
REM ============================================

REM [1/3] MySQL (dedicated instance, data in .tools\mysql-data)
start "LTPMS-MySQL" "C:\Users\ZhuanZ\mysql\mysql-8.4.6-winx64\bin\mysqld.exe" --basedir="C:\Users\ZhuanZ\mysql\mysql-8.4.6-winx64" --datadir="E:\projects\AI LTPMS\.tools\mysql-data" --port=3307 --console
echo [1/3] MySQL starting on 3307 ...
timeout /t 10 /nobreak >nul

REM [2/3] Spring Boot backend (bound to 127.0.0.1)
cd /d "E:\projects\AI LTPMS\backend"
set DB_PORT=3307
set DB_USER=ltpms
set DB_PASSWORD=ltpms
set JWT_SECRET=O2yNdfBAnYuRQsUGPKSgDAUo0Q1KV+fPE7rXmo9F2rxBHeJ5UgrVwvl3Q0JHj2yis13dKFESNrGxMihsfJN1sw==
set NLQ_INTERNAL_TOKEN=dev-internal-token
start "LTPMS-Backend" "C:\Users\ZhuanZ\.jdks\ms-17.0.16\bin\java.exe" -jar target\ltpms-backend-0.0.1-SNAPSHOT.jar --server.port=8080 --server.address=127.0.0.1
echo [2/3] Backend starting on 8080 (local only) ...
timeout /t 15 /nobreak >nul

REM [3/3] Frontend (production build, localhost only, proxies /api -> 8080)
cd /d "E:\projects\AI LTPMS\frontend"
start "LTPMS-Frontend" cmd /c ""C:\Users\ZhuanZ\.workbuddy\binaries\node\versions\22.22.2-3\npx.cmd" vite preview --port 8000 --strictPort"
echo [3/3] Frontend starting on 8000 (local only) ...

echo.
echo ==============================================
echo  All services started (LOCAL ONLY).
echo  Access:  http://localhost:8000
echo  Account: admin / admin123
echo ==============================================
pause
