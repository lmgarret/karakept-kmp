@echo off
REM Windows launcher for Karakept desktop app
cd /d "%~dp0"
echo Launching desktop app...
if defined JAVA_HOME (
    echo Using JAVA_HOME: %JAVA_HOME%
) else (
    echo JAVA_HOME not set. Gradle is running on JDK 25 which is not supported by Kotlin 2.2.0.
    echo Please set JAVA_HOME to a JDK 21 installation and try again.
    echo Example: set JAVA_HOME=C:\Program Files\Java\temurin-21-jdk-x64
    pause
    exit /b 1
)
gradlew.bat run   --stacktrace 
