@echo off
REM Build MCraze as a single executable JAR (uber-jar) with all dependencies included
echo ========================================
echo Building MCraze Uber-JAR
echo ========================================

REM Clean and compile
echo Cleaning build directory...
if exist build rmdir /s /q build
mkdir build
mkdir build\lib-extracted

echo Compiling Java sources...
"C:\Program Files\Java\jdk-21\bin\javac.exe" -d build -cp "lib/*" -sourcepath src src/mc/sayda/mcraze/*.java
if errorlevel 1 (
    echo Compilation failed!
    exit /b 1
)

echo Compilation successful!

REM Extract all dependency JARs
echo Extracting dependencies...
cd build\lib-extracted

for %%f in (..\..\lib\*.jar) do (
    echo Extracting %%f...
    "C:\Program Files\Java\jdk-21\bin\jar.exe" xf %%f
)

REM Remove META-INF signature files that cause conflicts
if exist META-INF\*.SF del /q META-INF\*.SF
if exist META-INF\*.RSA del /q META-INF\*.RSA
if exist META-INF\*.DSA del /q META-INF\*.DSA

cd ..\..

REM Copy resources
echo Copying resources...
xcopy /E /I /Y src\items build\items
xcopy /E /I /Y src\assets build\assets

REM Copy extracted dependencies to build root
echo Merging dependencies...
xcopy /E /Y build\lib-extracted\* build\

REM Create manifest
echo Creating manifest...
echo Main-Class: mc.sayda.mcraze.Game> build\manifest.txt

REM Create uber JAR
echo Creating uber-JAR file...
cd build
"C:\Program Files\Java\jdk-21\bin\jar.exe" cvfm ..\MCraze-standalone.jar manifest.txt .
cd ..

REM Clean up
rmdir /s /q build

echo ========================================
echo Uber-JAR created successfully: MCraze-standalone.jar
echo ========================================
echo.
echo To run: java -jar MCraze-standalone.jar
echo This JAR includes all dependencies and can run standalone!
echo.
echo File size:
dir MCraze-standalone.jar | find "MCraze-standalone.jar"