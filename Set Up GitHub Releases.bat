@echo off
rem Puts the release workflow where GitHub looks for it (.github\workflows). Run once, then commit and push.
cd /d "%~dp0"
if not exist ".github\workflows" mkdir ".github\workflows"
copy /Y "tools\github\release.yml" ".github\workflows\release.yml" >nul
if errorlevel 1 (
  echo Could not copy the workflow.
) else (
  echo Done. Commit and push .github\workflows\release.yml in GitHub Desktop.
  echo Then see docs\RELEASING.md for making a release.
)
pause
