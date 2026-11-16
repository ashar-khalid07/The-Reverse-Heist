#!/bin/sh
# Compiles the game and packs it into dist/ReverseHeist.jar. Needs a JDK (8 or newer).
set -e
cd "$(dirname "$0")"
rm -rf build/classes
mkdir -p build/classes dist
javac -d build/classes src/heist/*.java
jar cfe dist/ReverseHeist.jar heist.Main -C build/classes .
echo "Built dist/ReverseHeist.jar  -  run it with: java -jar dist/ReverseHeist.jar"
