@echo off
chcp 65001 > nul
REM Get the directory where this batch file is located
rem set SCRIPT_DIR=%~dp0
set SCRIPT_DIR=%usrp%/code\littlejlib\proj\
set DEBUG=0
set AOT=1
REM set AOT=1
REM Create temp batch file path and set as environment variable for Java to use
set PROJ_POSTRUN_BATCH=%TEMP%\proj_postrun_%RANDOM%_%RANDOM%.bat

REM JVM flags to suppress warnings
REM --sun-misc-unsafe-memory-access=allow suppresses Unsafe deprecation warnings from Truffle/GraalVM (Java 24+)
set JVM_FLAGS=--enable-native-access=ALL-UNNAMED --add-opens java.base/sun.nio.ch=ALL-UNNAMED --add-opens java.base/java.nio.channels.spi=ALL-UNNAMED --add-exports jdk.unsupported/sun.misc=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow

rem set AOT_CACHE=%USERPROFILE%\littlejlib\proj\AOTCache.aot
set AOT_CACHE=%usrp%\code\littlejlib\proj\target\AOTCache.aot
REM Check which JAR location exists and run it with AOT support (JDK 25 JEP 514/515)
if exist "%SCRIPT_DIR%target\littlejlib-proj-1.0.jar" (
    set JAR_FILE=%SCRIPT_DIR%target\littlejlib-proj-1.0.jar
) else if exist "%SCRIPT_DIR%littlejlib-proj-1.0.jar" (
    set JAR_FILE=%SCRIPT_DIR%littlejlib-proj-1.0.jar
) else (
    echo ERROR: JAR file not found in either location:
    echo   - %SCRIPT_DIR%target\littlejlib-proj-1.0.jar
    echo   - %SCRIPT_DIR%littlejlib-proj-1.0.jar
    exit /b 1
)

REM Configure AOT: use cache if exists, otherwise create it
if exist "%AOT_CACHE%" (
    if %DEBUG%==1 ( echo [AOT] Using cache for faster startup )
	if %AOT%==1 (
		java %JVM_FLAGS% -XX:AOTCache="%AOT_CACHE%" -jar "%JAR_FILE%" %*
	) else (
		java %JVM_FLAGS% -jar "%JAR_FILE%" %*
	)
) else (
    if %DEBUG%==1 ( echo [AOT] First run - creating cache )
	if %AOT%==1 (
		java %JVM_FLAGS% -XX:AOTCacheOutput="%AOT_CACHE%" -jar "%JAR_FILE%" %*
	) else (
		java %JVM_FLAGS% -jar "%JAR_FILE%" %*
	)	
)

REM If Java created the temp batch file, execute it (it will delete itself)
if exist "%PROJ_POSTRUN_BATCH%" (
    REM Suppress "batch file cannot be found" error when batch deletes itself
    call "%PROJ_POSTRUN_BATCH%" 2>nul
    REM Check if self-deletion was successful
    if not exist "%PROJ_POSTRUN_BATCH%" (
        if %DEBUG%==1 ( echo [Cleanup successful] )
    ) else (
        REM Fallback: delete temp batch if it still exists after self-delete attempt
        del "%PROJ_POSTRUN_BATCH%" 2>nul && if %DEBUG%==1 ( echo [proj.bat: fallback cleanup successful]) || if %DEBUG%==1 ( echo [proj.bat: fallback cleanup failed - manual deletion needed: %PROJ_POSTRUN_BATCH%])
    )
)
