@echo off
chcp 65001 >nul
rem 双击即可启动全部服务；也可以带参数调用，例如：
rem   start-all.bat -SkipBuild
rem   start-all.bat -SkipBuild -SkipWeb
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0start-all.ps1" %*
echo.
echo ----------------------------------------
echo 按任意键关闭此窗口（服务会继续在后台运行）
pause >nul
