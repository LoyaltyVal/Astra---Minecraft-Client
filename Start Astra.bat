@echo off
cd /d "%~dp0"
node tools\astra.js start
if errorlevel 1 pause
