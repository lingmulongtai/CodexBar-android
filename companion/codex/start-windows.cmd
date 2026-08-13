@echo off
setlocal
cd /d "%~dp0"
where node >nul 2>&1 || (echo Node.js 20 or newer is required. & pause & exit /b 1)
if not exist node_modules call npm ci --omit=dev || (pause & exit /b 1)
call npm start
if errorlevel 1 pause
