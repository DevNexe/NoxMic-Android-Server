@echo off
echo === NoxMic APK Builder for Windows ===

where java >nul 2>nul
if %errorlevel% neq 0 (
    echo Ошибка: JDK не найден. Пожалуйста, установите JDK 11 или выше и добавьте его в PATH.
    pause
    exit /b
)

echo 1. Очистка проекта...
call gradlew.bat clean

echo 2. Сборка отладочного APK (Debug)...
call gradlew.bat assembleDebug

if %errorlevel% equ 0 (
    echo === УСПЕХ! ===
    echo Ваш APK файл находится здесь: app\build\outputs\apk\debug\NoxMic.apk
) else (
    echo === ОШИБКА СБОРКИ ===
    echo Пожалуйста, убедитесь, что у вас установлен Android SDK.
)
pause
