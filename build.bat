@echo off
rem Compiles the game and packs it into dist\ReverseHeist.jar. Needs a JDK (8 or newer).
setlocal
cd /d "%~dp0"
if exist build\classes rmdir /s /q build\classes
mkdir build\classes
if not exist dist mkdir dist
javac -d build\classes src\heist\*.java
if errorlevel 1 goto fail
jar cfe dist\ReverseHeist.jar heist.Main -C build\classes .
if errorlevel 1 goto fail
echo Built dist\ReverseHeist.jar
echo Run it with: java -jar dist\ReverseHeist.jar
exit /b 0
:fail
echo Build failed. Is a JDK installed and on your PATH?
exit /b 1
