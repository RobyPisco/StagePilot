@echo off
cd /d "%~dp0"
echo StagePilot - Anteprima grafica
echo Apri http://127.0.0.1:8795 nel browser.
echo Per chiudere premi Ctrl+C.
start "" http://127.0.0.1:8795
py -3 -m http.server 8795 --bind 127.0.0.1 --directory preview
if errorlevel 1 pause
