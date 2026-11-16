@echo off
cd /d "%~dp0"
java -jar dist\ReverseHeist.jar
if errorlevel 1 pause
