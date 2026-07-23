@echo off
chcp 65001 >nul
rem ==========================================
rem  RikkaHub Desktop 本地构建脚本
rem  产物：绿色版 exe / MSI 安装包 / FatJar
rem ==========================================
cd /d %~dp0

echo [构建] 开始（首次较慢，需下载依赖）...
echo.

call gradlew.bat createDistributable packageMsi shadowJar --console=plain
if %errorlevel% equ 0 goto success

echo.
echo ==========================================
echo  [失败] 构建出错，请查看上方日志
echo ==========================================
pause
exit /b 1

:success
echo.
echo ==========================================
echo  [成功] 构建完成，产物位置：
echo ==========================================
echo.
echo  绿色版目录（整个目录拷走即用）：
echo    build\compose\binaries\main\app\RikkaHub\
dir /b build\compose\binaries\main\app\RikkaHub\RikkaHub.exe 2>nul
echo.
echo  MSI 安装包：
dir /b build\compose\binaries\main\msi\*.msi 2>nul
echo.
echo  跨平台 FatJar（需 JDK 17+）：
dir /b build\libs\*-all.jar 2>nul
echo.
pause
