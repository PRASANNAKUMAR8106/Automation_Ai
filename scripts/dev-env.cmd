@echo off
REM AutoFlow AI Windows Command Wrapper
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0dev-env.ps1" %*
