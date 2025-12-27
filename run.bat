@echo off
REM Quick launcher for MCraze
REM Automatically selects the best available JAR

if exist MCraze-standalone.jar (
    echo Running MCraze standalone version...
    java -Xmx2G -XX:+UseG1GC -jar MCraze-standalone.jar %*
) else if exist MCraze.jar (
    echo Running MCraze with external libraries...
    java -Xmx2G -XX:+UseG1GC -jar MCraze.jar %*
) else (
    echo ERROR: No MCraze JAR found!
    echo.
    echo Please build first:
    echo   build-uber-jar.bat
    echo.
    pause
    exit /b 1
)
