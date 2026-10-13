#!/bin/bash
set -eu
cd "$(dirname "$0")"
mkdir -p target/test-classes
javac -source 8 -target 8 -cp 'lib/*' -d target/test-classes \
    src/main/java/common/org/scriptcraftjs/LimitedJavaScriptEngine.java \
    src/main/java/bukkit/org/scriptcraftjs/bukkit/ScriptCraftPlugin.java \
    src/main/java/canary/org/scriptcraftjs/canarymod/ScriptCraftPlugin.java \
    src/test/java/org/scriptcraftjs/InstructionLimitTest.java
timeout 20s java -cp 'target/test-classes:lib/*' org.scriptcraftjs.InstructionLimitTest
