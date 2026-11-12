@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo === HQS Wi-Fi Navi - testovaci LAN server ===
echo IP pocitace zjistite prikazem ipconfig
py -3 HQS_test_server.py
if errorlevel 1 (
    echo.
    echo Chyba: nainstalujte Python 3 a zpristupnete prikaz py nebo spustte python HQS_test_server.py.
)
pause
