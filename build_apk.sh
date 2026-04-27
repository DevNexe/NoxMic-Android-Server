#!/bin/bash

echo "=== NoxMic APK Builder ==="

# Проверка наличия JDK
if ! type -p java > /dev/null; then
    echo "Ошибка: JDK не найден. Пожалуйста, установите JDK 11 или выше."
    exit 1
fi

# Проверка наличия ANDROID_HOME
if [ -z "$ANDROID_HOME" ]; then
    echo "Предупреждение: Переменная ANDROID_HOME не установлена."
    echo "Если сборка упадет, убедитесь, что у вас установлен Android SDK."
fi

echo "1. Очистка проекта..."
chmod +x gradlew
./gradlew clean

echo "2. Сборка отладочного APK (Debug)..."
./gradlew assembleDebug

if [ $? -eq 0 ]; then
    echo "=== УСПЕХ! ==="
    echo "Ваш APK файл находится здесь: app/build/outputs/apk/debug/NoxMic.apk"
else
    echo "=== ОШИБКА СБОРКИ ==="
    echo "Возможно, не хватает компонентов Android SDK. Попробуйте открыть проект в Android Studio для автоматической докачки библиотек."
fi
