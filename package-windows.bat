@echo off
rem Makes a Windows version that includes its own Java, so players need nothing installed.
rem Needs JDK 14 or newer. Output: release\ReverseHeist\ReverseHeist.exe
rem Zip the release\ReverseHeist folder and upload that zip to itch.io.
setlocal
cd /d "%~dp0"
if not exist dist\ReverseHeist.jar call build.bat
if exist release\ReverseHeist rmdir /s /q release\ReverseHeist
jpackage --type app-image --name ReverseHeist --input dist --main-jar ReverseHeist.jar --main-class heist.Main --add-modules java.desktop --dest release
if errorlevel 1 goto fail
echo Done. Zip the folder release\ReverseHeist and upload it to itch.io.
exit /b 0
:fail
echo Packaging failed. jpackage needs JDK 14 or newer.
exit /b 1
