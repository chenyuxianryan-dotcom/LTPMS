@echo off
REM LTPMS MySQL (detached via WMI, logs to .tools)
"C:\Users\ZhuanZ\mysql\mysql-8.4.6-winx64\bin\mysqld.exe" --basedir="C:\Users\ZhuanZ\mysql\mysql-8.4.6-winx64" --datadir="E:\projects\AI LTPMS\.tools\mysql-data" --port=3307 --console > "E:\projects\AI LTPMS\.tools\mysql-console.log" 2>&1
