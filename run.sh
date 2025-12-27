#!/bin/bash
# Quick launcher for MCraze
# Automatically selects the best available JAR

# Find Java
if [ -n "$JAVA_HOME" ]; then
    JAVA_CMD="$JAVA_HOME/bin/java"
else
    JAVA_CMD="java"
fi

# Check Java version
JAVA_VERSION=$($JAVA_CMD -version 2>&1 | head -n 1 | awk -F '"' '{print $2}' | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 17 ]; then
    echo "ERROR: Java 17 or higher required (found Java $JAVA_VERSION)"
    echo "Download from: https://adoptium.net/"
    exit 1
fi

# Run the appropriate JAR
if [ -f "MCraze-standalone.jar" ]; then
    echo "Running MCraze standalone version..."
    $JAVA_CMD -Xmx2G -XX:+UseG1GC -jar MCraze-standalone.jar "$@"
elif [ -f "MCraze.jar" ]; then
    echo "Running MCraze with external libraries..."
    $JAVA_CMD -Xmx2G -XX:+UseG1GC -jar MCraze.jar "$@"
else
    echo "ERROR: No MCraze JAR found!"
    echo ""
    echo "Please build first:"
    echo "  ./build-uber-jar.sh"
    echo ""
    exit 1
fi
