#!/bin/sh
# Runs the automated checks (no window needed) and saves screenshots to build/shots.
set -e
cd "$(dirname "$0")"
rm -rf build/classes build/test
mkdir -p build/classes build/test
javac -d build/classes src/heist/*.java
javac -cp build/classes -d build/test test/heist/HeadlessTest.java
java -Djava.awt.headless=true -cp build/classes:build/test heist.HeadlessTest build/shots
