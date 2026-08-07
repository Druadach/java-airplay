@echo off
setlocal
set "CURRENT_DIR=%~dp0"
set "JAVA_EXE=%CURRENT_DIR%jre\bin\javaw.exe"
set "LAUNCHER_JAR=%CURRENT_DIR%java-airplay-launcher.jar"

if not exist "%JAVA_EXE%" (
    echo ERROR: Bundled Java runtime was not found.
    pause
    exit /b 1
)

if not exist "%LAUNCHER_JAR%" (
    echo ERROR: java-airplay-launcher.jar was not found.
    echo Run build_patch.ps1 to create it.
    pause
    exit /b 1
)

start "" "%JAVA_EXE%" -jar "%LAUNCHER_JAR%" "--base-dir=%CURRENT_DIR%."
exit /b %ERRORLEVEL%
