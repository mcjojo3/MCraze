@echo off
REM Build MCraze into a runnable JAR file
echo ========================================
echo Building MCraze JAR
echo ========================================

REM Clean and compile
echo Cleaning build directory...
if exist build rmdir /s /q build
mkdir build

echo Compiling Java sources...
"C:\Program Files\Java\jdk-21\bin\javac.exe" -d build -cp "lib/*" -sourcepath src src/mc/sayda/mcraze/Game.java
if errorlevel 1 (
    echo Compilation failed!
    exit /b 1
)

echo Compilation successful!

REM Copy resources
echo Copying resources...
xcopy /E /I /Y src\sprites build\sprites
xcopy /E /I /Y src\sounds build\sounds
xcopy /E /I /Y src\items build\items
xcopy /E /I /Y src\assets build\assets
if exist src\META-INF xcopy /E /I /Y src\META-INF build\META-INF

REM Create manifest
echo Creating manifest...
echo Main-Class: mc.sayda.mcraze.Game> build\manifest.txt
echo Class-Path: lib/easyogg.jar lib/gson-2.1.jar lib/jogg-0.0.7.jar lib/jorbis-0.0.15.jar>> build\manifest.txt

REM Create JAR
echo Creating JAR file...
cd build
"C:\Program Files\Java\jdk-21\bin\jar.exe" cvfm ..\MCraze.jar manifest.txt mc sprites sounds items assets
if exist META-INF "C:\Program Files\Java\jdk-21\bin\jar.exe" uvf ..\MCraze.jar META-INF
cd ..

echo ========================================
echo JAR created successfully: MCraze.jar
echo ========================================
echo.
echo To run: java -jar MCraze.jar
echo Note: Make sure lib/ folder is in the same directory
