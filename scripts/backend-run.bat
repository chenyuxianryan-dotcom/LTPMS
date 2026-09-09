@echo off
REM LTPMS backend (detached via WMI, logs to .tools)
cd /d "E:\projects\AI LTPMS\backend"
set "DB_PORT=3307"
set "DB_USER=ltpms"
set "DB_PASSWORD=ltpms"
set "JWT_SECRET=O2yNdfBAnYuRQsUGPKSgDAUo0Q1KV+fPE7rXmo9F2rxBHeJ5UgrVwvl3Q0JHj2yis13dKFESNrGxMihsfJN1sw=="
set "NLQ_INTERNAL_TOKEN=dev-internal-token"
"C:\Users\ZhuanZ\.jdks\ms-17.0.16\bin\java.exe" -jar target\ltpms-backend-0.0.1-SNAPSHOT.jar --server.port=8080 --server.address=127.0.0.1 > "E:\projects\AI LTPMS\.tools\backend-console.log" 2>&1
