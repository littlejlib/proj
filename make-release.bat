@echo off
REM ====================================================================
REM make-release.bat - Create a timestamped release ZIP package
REM ====================================================================
REM This script builds the project and creates a release ZIP file
REM containing proj.bat and the shaded JAR with a timestamp in the name
REM ====================================================================

echo.
echo ========================================
echo   Creating Release Package
echo ========================================
echo.

REM Get the directory where this batch file is located
set SCRIPT_DIR=%~dp0

REM Change to the script directory
cd /d "%SCRIPT_DIR%"

REM Create releases directory if it doesn't exist
if not exist "releases" (
    echo Creating releases directory...
    mkdir releases
)

REM Generate timestamp in format: YYYYMMDD_HHMMSS
for /f "tokens=2 delims==" %%I in ('wmic os get localdatetime /value') do set datetime=%%I
set TIMESTAMP=%datetime:~0,8%_%datetime:~8,6%

REM Set release filename
set RELEASE_NAME=littlejlib-proj-release-%TIMESTAMP%.zip

echo Timestamp: %TIMESTAMP%
echo Release file: %RELEASE_NAME%
echo.

REM Check if JAR exists, if not build it
if not exist "target\littlejlib-proj-1.0.jar" (
    echo JAR not found. Building project...
    echo.
    call mvn package
    if errorlevel 1 (
        echo.
        echo ERROR: Maven build failed!
        pause
        exit /b 1
    )
    echo.
    echo Build completed successfully!
    echo.
) else (
    echo JAR found. Skipping build. Run 'mvn clean package' manually if you want to rebuild.
    echo.
)

REM Create the ZIP file
echo Creating release ZIP...
REM projold.bat is the superseded wrapper, shipped only as a fallback. The supported
REM launcher is proj.exe (a copy of jr.exe) plus proj.jrc; the shell adapter that does
REM the cd comes from `proj --init clink|cmd|powershell|bash`.
powershell -Command "Compress-Archive -Path 'projold.bat','target\littlejlib-proj-1.0.jar' -DestinationPath 'releases\%RELEASE_NAME%' -Force"

if errorlevel 1 (
    echo.
    echo ERROR: Failed to create ZIP file!
    pause
    exit /b 1
)

echo.
echo ========================================
echo   Release Created Successfully!
echo ========================================
echo.
echo Release file: releases\%RELEASE_NAME%
echo.

REM Show file size
for %%A in ("releases\%RELEASE_NAME%") do (
    set size=%%~zA
    set /a sizeInMB=!size! / 1048576
)

dir "releases\%RELEASE_NAME%" | find "%RELEASE_NAME%"

echo.
echo You can now upload this file to GitHub releases.
echo.
pause
