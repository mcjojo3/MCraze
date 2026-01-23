#!/bin/bash
# Build MCraze as a single executable JAR (uber-jar) with all dependencies included

echo "========================================"
echo "Building MCraze Uber-JAR"
echo "========================================"

# Find Java
if [ -n "$JAVA_HOME" ]; then
    JAVA_CMD="$JAVA_HOME/bin/java"
    JAVAC_CMD="$JAVA_HOME/bin/javac"
    JAR_CMD="$JAVA_HOME/bin/jar"
else
    JAVA_CMD="java"
    JAVAC_CMD="javac"
    JAR_CMD="jar"
fi

# Check Java version
JAVA_VERSION=$($JAVA_CMD -version 2>&1 | head -n 1 | awk -F '"' '{print $2}' | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 17 ]; then
    echo "ERROR: Java 17 or higher required (found Java $JAVA_VERSION)"
    exit 1
fi

# Clean and compile
echo "Cleaning build directory..."
rm -rf build
mkdir -p build/lib-extracted

echo "Compiling Java sources..."
$JAVAC_CMD -d build -cp "lib/*" -sourcepath src src/mc/sayda/mcraze/*.java
if [ $? -ne 0 ]; then
    echo "Compilation failed!"
    exit 1
fi

echo "Compilation successful!"

# Extract all dependency JARs
echo "Extracting dependencies..."
cd build/lib-extracted

for jarfile in ../../lib/*.jar; do
    echo "Extracting $(basename $jarfile)..."
    $JAR_CMD xf "$jarfile"
done

# Remove META-INF signature files that cause conflicts
rm -f META-INF/*.SF META-INF/*.RSA META-INF/*.DSA 2>/dev/null

cd ../..

# Copy resources
echo "Copying resources..."
cp -r src/assets build/
cp -r src/items build/

# Copy extracted dependencies to build root
echo "Merging dependencies..."
cp -r build/lib-extracted/* build/

# Create manifest
echo "Creating manifest..."
echo "Main-Class: mc.sayda.mcraze.Game" > build/manifest.txt

# Create uber JAR
echo "Creating uber-JAR file..."
cd build
$JAR_CMD cvfm ../MCraze-standalone.jar manifest.txt .
cd ..

# Clean up
rm -rf build

echo "========================================"
echo "Uber-JAR created successfully: MCraze-standalone.jar"
echo "========================================"
echo ""
echo "To run: java -jar MCraze-standalone.jar"
echo "This JAR includes all dependencies and can run standalone!"
echo ""
echo "File size:"
ls -lh MCraze-standalone.jar | awk '{print $5 " " $9}'

# Make the JAR executable on Unix systems
chmod +x MCraze-standalone.jar
