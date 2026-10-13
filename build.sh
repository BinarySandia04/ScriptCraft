#!/bin/bash

# REQUIRED: Java 8, Ant, and wget (when Bukkit isn't available locally).
set -e

cd "$(dirname "$0")"

# Reuse the server's Bukkit JAR when building inside Docker.
if [ ! -f Bukkit.jar ]; then
    if [ -f /bukkit/bukkit.jar ]; then
        cp /bukkit/bukkit.jar Bukkit.jar
    else
        echo "Downloading Bukkit 1.8.8"
        wget https://maven.elmakers.com/repository/org/bukkit/craftbukkit/1.8.8-R0.1-SNAPSHOT/craftbukkit-1.8.8-R0.1-SNAPSHOT.jar -O Bukkit.jar.tmp
        mv Bukkit.jar.tmp Bukkit.jar
    fi
fi

# Ant generates event helpers and packages the JavaScript resources as well
# as compiling the plugin against the server's Bukkit API.
echo "Building ScriptCraft ..."
ant -Dlib.bukkit="$PWD/Bukkit.jar" clean package
cp target/scriptcraft.jar scriptcraft.jar

echo "Done!"
