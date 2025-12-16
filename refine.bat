@echo off
rem ============================================================================
rem SenTient Startup Script (Windows - Optimized)
rem ============================================================================

set "REFINE_HOME=%~dp0"
if %REFINE_HOME:~-1%==\ set REFINE_HOME=%REFINE_HOME:~0,-1%

rem --- Configuration ---
set REFINE_MEMORY=4096M
set JAVA_OPTIONS=-XX:+UseG1GC -XX:MaxGCPauseMillis=200 -Drefine.headless=true -Drefine.host=127.0.0.1 -Drefine.port=3333 -Dfile.encoding=UTF-8 -Dbutterfly.properties.path=config/core/butterfly.properties

rem --- Build Classpath (Using Wildcards to save space) ---
rem 1. Add Compiled Classes
set "CP=%REFINE_HOME%\webapp\WEB-INF\classes"

rem 2. Add Core Libraries (Wildcard *)
set "CP=%CP%;%REFINE_HOME%\server\lib\*"
set "CP=%CP%;%REFINE_HOME%\webapp\WEB-INF\lib\*"

rem 3. Add Extensions (Loop required here, but usually safe)
for /d %%d in ("%REFINE_HOME%\extensions\*") do (
    if exist "%%d\module\lib" (
        for %%f in ("%%d\module\lib\*.jar") do call :append_cp "%%f"
    )
)

rem --- Launch ---
echo Starting SenTient Core...
echo   Memory:  %REFINE_MEMORY%
echo   Binding: 127.0.0.1:3333

java -cp "%CP%" %JAVA_OPTIONS% -Xms%REFINE_MEMORY% -Xmx%REFINE_MEMORY% -Drefine.home="%REFINE_HOME%" -Drefine.webapp="%REFINE_HOME%\webapp" com.google.refine.Refine
goto :eof

:append_cp
set "CP=%CP%;%~1"
goto :eof