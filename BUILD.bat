@echo off
setlocal
set "JAVA_HOME=C:\Program Files\Eclipse Adoptium\jdk-17.0.20.101-hotspot"
set "PATH=%JAVA_HOME%\bin;%PATH%"
pushd "%~dp0examples\minecraft-sa-de-bridge"
if errorlevel 1 goto directory_error
powershell.exe -NoLogo -NoProfile -ExecutionPolicy Bypass -File "tools\Build-Ready.ps1"
set "BUILD_EXIT=%ERRORLEVEL%"
popd
if not "%BUILD_EXIT%"=="0" goto build_error
endlocal
exit /b 0

:directory_error
echo ERROR: Cannot open the bridge directory next to BUILD.bat.
echo Extract the entire repository ZIP before running BUILD.bat.
set "BUILD_EXIT=1"

:build_error
echo.
echo BUILD FAILED. Read the error above and the build log in BUILD-LOGS.
echo No mod was installed into any game folder.
pause
endlocal & exit /b %BUILD_EXIT%
