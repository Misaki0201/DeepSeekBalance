#!/bin/bash
# DeepSeek 余额监控 - APK 构建脚本
# 需要在安装了 Android SDK 的机器上运行

set -e

echo "=== DeepSeek 余额监控 APK 构建 ==="
echo ""

# 检查 Java
if ! command -v java &> /dev/null; then
    echo "错误: 未找到 Java，请安装 JDK 17+"
    echo "  Ubuntu/Debian: sudo apt install openjdk-17-jdk"
    echo "  macOS: brew install openjdk@17"
    exit 1
fi

JAVA_VER=$(java -version 2>&1 | head -1 | cut -d'"' -f2 | cut -d'.' -f1)
echo "Java 版本: $(java -version 2>&1 | head -1)"

# 检查 ANDROID_HOME
if [ -z "$ANDROID_HOME" ] && [ -z "$ANDROID_SDK_ROOT" ]; then
    echo "警告: ANDROID_HOME 未设置"

    # 尝试常用路径
    for path in "$HOME/Android/Sdk" "/usr/lib/android-sdk" "/opt/android-sdk"; do
        if [ -d "$path" ]; then
            export ANDROID_HOME="$path"
            echo "找到 Android SDK: $path"
            break
        fi
    done

    if [ -z "$ANDROID_HOME" ]; then
        echo "错误: 未找到 Android SDK"
        echo "请安装 Android SDK 或设置 ANDROID_HOME 环境变量"
        exit 1
    fi
fi

echo "Android SDK: $ANDROID_HOME"
echo ""

# 创建 Gradle Wrapper（如果不存在）
if [ ! -f "gradlew" ]; then
    echo "创建 Gradle Wrapper..."
    if command -v gradle &> /dev/null; then
        gradle wrapper --gradle-version 8.4
    else
        echo "下载 Gradle Wrapper..."
        mkdir -p gradle/wrapper
        curl -sL "https://services.gradle.org/distributions/gradle-8.4-bin.zip" -o /tmp/gradle-8.4-bin.zip

        # 创建 wrapper properties
        cat > gradle/wrapper/gradle-wrapper.properties << EOF
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.4-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
EOF
        echo "Gradle Wrapper 配置已创建"
        echo "请下载 gradle-wrapper.jar 放到 gradle/wrapper/ 目录"
        echo "下载地址: https://raw.githubusercontent.com/gradle/gradle/v8.4.0/gradle/wrapper/gradle-wrapper.jar"
        echo "或直接在 Android Studio 中打开项目，会自动完成配置"
    fi
fi

# 构建
echo ""
echo "开始构建..."

if [ -f "gradlew" ]; then
    chmod +x gradlew

    # Debug APK
    echo "构建 Debug APK..."
    ./gradlew assembleDebug

    # Release APK
    echo ""
    echo "构建 Release APK (需要签名配置)..."
    if [ -f "app/keystore.properties" ]; then
        ./gradlew assembleRelease
    else
        echo "跳过 Release 构建（无签名配置）"
        echo "如需 Release APK，请创建 app/keystore.properties"
    fi
else
    cd app
    ../gradlew assembleDebug
fi

# 输出
echo ""
echo "=== 构建完成 ==="
echo ""
echo "APK 文件位置:"
find . -name "*.apk" -type f 2>/dev/null

echo ""
echo "Debug APK 位置: app/build/outputs/apk/debug/"
echo "直接在手机上安装 Debug APK 即可使用"
