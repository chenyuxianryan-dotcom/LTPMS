@echo off
REM LTPMS NL2SQL service (detached, logs to .tools)
cd /d "E:\projects\AI LTPMS\nlq-service"
"C:\Users\ZhuanZ\.workbuddy\binaries\python\envs\default\Scripts\python.exe" -m app.main > "E:\projects\AI LTPMS\.tools\nlq-console.log" 2>&1
