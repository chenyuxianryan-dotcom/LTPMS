@echo off
REM LTPMS frontend preview (detached via WMI, logs to .tools)
cd /d "E:\projects\AI LTPMS\frontend"
"C:\Users\ZhuanZ\.workbuddy\binaries\node\versions\22.22.2-2\node.exe" node_modules\vite\bin\vite.js preview --port 8000 --strictPort --host 127.0.0.1 > "E:\projects\AI LTPMS\.tools\frontend-console.log" 2>&1
