@echo off
chcp 65001 >nul
title Java AirPlay Server

echo ========================================
echo    Java AirPlay Server 启动器
echo ========================================

REM 设置环境变量
setlocal
set CURRENT_DIR=%~dp0

REM 设置 Java 路径
set JAVA_HOME=%CURRENT_DIR%jre
set PATH=%JAVA_HOME%\bin;%PATH%

REM 设置 Gstreamer 路径
set GSTREAMER_PATH=%CURRENT_DIR%gstreamer
set PATH=%GSTREAMER_PATH%\bin;%PATH%
set GST_PLUGIN_PATH=%GSTREAMER_PATH%\lib\gstreamer-1.0

REM 检查必要的文件
if not exist "%CURRENT_DIR%java-airplay-server-fixed.jar" (
    echo 错误: 未找到 java-airplay-server-fixed.jar
    pause
    exit /b 1
)

if not exist "%JAVA_HOME%\bin\java.exe" (
    echo 错误: 未找到 Java 运行时环境
    pause
    exit /b 1
)

if not exist "%GSTREAMER_PATH%\bin\gst-launch-1.0.exe" (
    echo 警告: 未找到 Gstreamer，视频播放可能无法正常工作
)

echo 正在启动 AirPlay 服务器...
echo Java 路径: %JAVA_HOME%
echo Gstreamer 路径: %GSTREAMER_PATH%
echo ========================================

REM 启动 AirPlay 服务器
"%JAVA_HOME%\bin\java.exe" -jar "%CURRENT_DIR%java-airplay-server-fixed.jar"

echo.
echo 服务器已停止
pause
