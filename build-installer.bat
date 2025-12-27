@echo off
REM Create native Windows installer using jpackage
echo ========================================
echo Building MCraze Native Installer
echo ========================================

REM Check if JAR exists
if not exist MCraze.jar (
    echo ERROR: MCraze.jar not found!
    echo Please run build-jar.bat first
    exit /b 1
)

REM Clean installer output
if exist installer rmdir /s /q installer
mkdir installer
mkdir installer\input

REM Copy files to input directory (avoids packaging entire repo)
echo Preparing input files...
copy MCraze.jar installer\input\
xcopy /E /I /Y lib installer\input\lib

REM Get version from Game.java or use default
set VERSION=1.0.0

echo Creating Windows installer with jpackage...
echo This may take several minutes...
echo.

REM Create Windows EXE and MSI installer
"C:\Program Files\Java\jdk-21\bin\jpackage.exe" ^
    --type msi ^
    --input installer\input ^
    --name MCraze ^
    --main-jar MCraze.jar ^
    --main-class mc.sayda.mcraze.Game ^
    --dest installer ^
    --app-version %VERSION% ^
    --vendor "SaydaGames" ^
    --description "A Minecraft-inspired 2D sandbox game" ^
    --copyright "Copyright 2025 SaydaGames (mc_jojo3)" ^
    --win-dir-chooser ^
    --win-menu ^
    --win-shortcut ^
    --java-options "-Xmx2G" ^
    --java-options "-XX:+UseG1GC"

if errorlevel 1 (
    echo.
    echo ========================================
    echo Installer creation failed!
    echo ========================================
    echo.
    echo Trying portable EXE instead...

    REM Fallback: Create portable app-image
    "C:\Program Files\Java\jdk-21\bin\jpackage.exe" ^
        --type app-image ^
        --input installer\input ^
        --name MCraze ^
        --main-jar MCraze.jar ^
        --main-class mc.sayda.mcraze.Game ^
        --dest installer ^
        --app-version %VERSION% ^
        --vendor "SaydaGames" ^
        --java-options "-Xmx2G" ^
        --java-options "-XX:+UseG1GC"

    if errorlevel 1 (
        echo App-image creation also failed!
        exit /b 1
    )

    echo.
    echo ========================================
    echo Portable application created!
    echo Location: installer\MCraze\
    echo Run: installer\MCraze\MCraze.exe
    echo ========================================
) else (
    echo.
    echo ========================================
    echo Installer created successfully!
    echo Location: installer\MCraze-%VERSION%.msi
    echo ========================================
)

echo.
echo To distribute: Share the installer\ folder contents
