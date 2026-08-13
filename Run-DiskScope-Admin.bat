@echo off
set "SCRIPT=%~dp0Run-DiskScope.ps1"
powershell -NoProfile -ExecutionPolicy Bypass -Command "Start-Process PowerShell -Verb RunAs -ArgumentList '-NoExit','-ExecutionPolicy','Bypass','-File','%SCRIPT%'"
