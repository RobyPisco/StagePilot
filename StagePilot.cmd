@echo off
cd /d "%~dp0"
if not exist "web\dist\index.html" (
  echo Prima compila lo studio web:
  echo   cd web
  echo   npm install
  echo   npm run build
  pause
  exit /b 1
)
py -3 studio\server.py
if errorlevel 1 pause
