# DeepSeek 余额监控

一款 Android APP，用于实时监控 DeepSeek API 账户余额。

## 功能

- ✅ 显示 DeepSeek 账户总余额、赠送余额、充值余额
- ✅ 显示账户可用状态
- ✅ 支持直连 DeepSeek API 或通过自有服务器代理
- ✅ Material 3 设计，支持深色模式
- ✅ 后台定时刷新，余额不足时推送通知
- ✅ API Key 仅存储在本地（加密存储）
- ✅ 提供可选的服务端代理组件

## 架构

### 模式一：直连模式（默认）

```mermaid
flowchart LR
    A[Android App] -->|API Key + GET /user/balance| B[api.deepseek.com]
```

API Key 保存在手机本地 DataStore 中，App 直接调用 DeepSeek 官方接口查询余额。

### 模式二：服务器代理模式（推荐）

```mermaid
flowchart LR
    A[Android App] -->|访问代理 API| B[你自己的服务器]
    B -->|API Key + GET /user/balance| C[api.deepseek.com]
```

API Key 存放在你的服务器上，App 通过你的服务器代理查询。适合多设备共享或对安全性要求更高的场景。

## 快速开始

### 方法一：Android Studio（推荐）

1. 用 Android Studio 打开 `DeepSeekBalance/` 目录
2. 等待 Gradle 同步完成
3. 连接手机或启动模拟器
4. 点击 Run ▶️ 按钮

### 方法二：命令行构建

```bash
# 确保已安装 JDK 17+ 和 Android SDK
export ANDROID_HOME=/path/to/your/android/sdk

# 构建 Debug APK
chmod +x gradlew
./gradlew assembleDebug

# APK 位置：app/build/outputs/apk/debug/
```

## 使用说明

1. 打开 App，输入你的 DeepSeek API Key（以 `sk-` 开头）
2. 点击"开始监控"，自动获取余额数据
3. 主界面显示总余额、赠送余额、充值余额和账户状态
4. 右上角刷新按钮手动刷新，退出按钮可更换 API Key

## 服务器部署（可选）

如果你选择使用服务器代理模式，可以在自己的服务器上运行代理服务：

### Docker 部署（推荐）

```bash
cd server
cp .env.example .env
# 编辑 .env 填入 DEEPSEEK_API_KEY
docker build -t deepseek-balance-proxy .
docker run -d -p 5000:5000 --name ds-proxy deepseek-balance-proxy
```

### 直接部署

```bash
cd server
cp .env.example .env
# 编辑 .env 填入 DEEPSEEK_API_KEY
pip install -r requirements.txt
python app.py
```

### Nginx 反代 + HTTPS（生产环境）

```nginx
server {
    listen 443 ssl;
    server_name your-domain.com;
    
    location / {
        proxy_pass http://127.0.0.1:5000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

### App 端配置

在 App 主界面右上角的设置中可以启用服务器代理模式，填入你的服务器地址。

## 技术栈

- **语言**: Kotlin
- **UI**: Jetpack Compose + Material 3
- **网络**: Retrofit + OkHttp
- **本地存储**: DataStore Preferences
- **后台任务**: WorkManager
- **服务器**: Python Flask (可选)

## 项目结构

```
DeepSeekBalance/
├── app/
│   ├── src/main/
│   │   ├── java/com/deepseek/balance/
│   │   │   ├── MainActivity.kt          # 主入口
│   │   │   ├── data/
│   │   │   │   ├── api/DeepSeekApi.kt   # DeepSeek API 接口
│   │   │   │   ├── model/BalanceModels.kt # 数据模型
│   │   │   │   ├── local/SettingsDataStore.kt # 本地存储
│   │   │   │   └── repository/BalanceRepository.kt
│   │   │   ├── viewmodel/BalanceViewModel.kt
│   │   │   ├── ui/screens/
│   │   │   │   ├── DashboardScreen.kt  # 余额仪表盘
│   │   │   │   └── ApiKeyScreen.kt     # API Key 输入
│   │   │   ├── ui/theme/               # 主题配置
│   │   │   └── worker/BalanceWorker.kt # 后台通知
│   │   ├── res/                         # 资源文件
│   │   └── AndroidManifest.xml
│   └── build.gradle.kts
├── server/                              # 服务器组件（可选）
│   ├── app.py                           # Flask 代理服务
│   ├── Dockerfile
│   └── requirements.txt
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

## 隐私说明

- API Key 仅保存在手机本地 DataStore 中
- 直连模式下，App 不经过任何第三方服务器
- 服务器代理模式下，API Key 仅存储在你的自有服务器上
- App 不会收集任何个人信息

## License

MIT
