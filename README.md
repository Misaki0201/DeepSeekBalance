<p align="center">
  <img src="app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml" width="100" alt="DeepSeek Balance"/>
</p>

<h1 align="center">DeepSeek 余额监控</h1>

<p align="center">
  <a href="#">
    <img src="https://img.shields.io/badge/Android-14%2B-3DDC84?logo=android" alt="Android">
  </a>
  <a href="#">
    <img src="https://img.shields.io/badge/Kotlin-1.9.22-7F52FF?logo=kotlin" alt="Kotlin">
  </a>
  <a href="#">
    <img src="https://img.shields.io/badge/Compose-BOM%202024-4285F4?logo=jetpackcompose" alt="Compose">
  </a>
  <a href="LICENSE">
    <img src="https://img.shields.io/badge/License-MIT-yellow" alt="License">
  </a>
  <a href="#">
    <img src="https://img.shields.io/badge/API-DeepSeek-4F6BED?logo=deepseek" alt="DeepSeek API">
  </a>
</p>

<p align="center">
  🚀 实时监控你的 DeepSeek API 账户余额和 Token 消耗 · 开源 · 隐私安全
</p>

---

## 📱 截图

| 余额仪表盘 | 用量统计 | 设置页面 |
|:---:|:---:|:---:|
| ![Screenshot 1](screenshots/balance.png) | ![Screenshot 2](screenshots/usage.png) | ![Screenshot 3](screenshots/settings.png) |

---

## ✨ 功能

<table>
  <tr>
    <td align="center">💰 <b>余额监控</b></td>
    <td>总余额 · 赠送余额 · 充值余额 · 账户状态一目了然</td>
  </tr>
  <tr>
    <td align="center">📊 <b>Token 用量</b></td>
    <td>今日/累计 Token 消耗（含 Prompt + Completion 细分）</td>
  </tr>
  <tr>
    <td align="center">💵 <b>消费统计</b></td>
    <td>今日花费 · 累计花费 · 自动换算人民币</td>
  </tr>
  <tr>
    <td align="center">🔔 <b>后台通知</b></td>
    <td>定时刷新 · 余额不足时推送告警</td>
  </tr>
  <tr>
    <td align="center">🔒 <b>隐私安全</b></td>
    <td>API Key 仅存本地 · 支持自有服务器代理 · 零数据上报</td>
  </tr>
  <tr>
    <td align="center">🎨 <b>Material You</b></td>
    <td>Material 3 设计 · 支持深色模式 · Android 12+ 动态取色</td>
  </tr>
</table>

---

## 🏗️ 架构

### 模式一：直连（默认 · 推荐）

```
手机 App ──HTTPS──▶ api.deepseek.com
```

API Key 存在手机本地 DataStore，App 直接调用 DeepSeek 官方接口。**电脑/服务器关机不影响**。

### 模式二：服务器代理（可选）

```
手机 App ──▶ 你的服务器 ──▶ api.deepseek.com
```

API Key 存放在你的服务器上，适合多设备共享或对安全性要求更高的场景。

---

## 🚀 快速开始

### 方法一：下载 APK

> 直接从 [GitHub Releases](../../releases) 下载最新 APK 安装到手机

### 方法二：自行构建

<details>
<summary>展开构建步骤</summary>

**环境要求：** JDK 17+ · Android SDK

```bash
# 克隆仓库
git clone https://github.com/Misaki0201/DeepSeekBalance.git
cd DeepSeekBalance

# 生成 Gradle Wrapper（如不存在）
gradle wrapper --gradle-version 8.4

# 构建 Debug APK
./gradlew assembleDebug

# APK 位置：app/build/outputs/apk/debug/app-debug.apk
```

或直接使用 **Android Studio** 打开项目，点击 Run ▶️。

</details>

### 使用方式

1. 打开 App，输入你的 DeepSeek API Key（以 `sk-` 开头）
2. 点击 **"开始监控"**，自动获取余额 + 用量数据
3. 余额仪表盘：总余额、赠送余额、充值余额、账户状态
4. 用量统计：今日/累计 Token 数、今日/累计花费
5. 右上角刷新按钮手动刷新，退出按钮可更换 API Key

---

## 🖥️ 服务器部署（可选）

<details>
<summary>展开服务器部署说明</summary>

`server/` 目录提供了 Python Flask 代理服务，用于将 API Key 存放在你的服务器上。

### Docker 部署（推荐）

```bash
cd server
cp .env.example .env
# 编辑 .env 填入 DEEPSEEK_API_KEY 和 ACCESS_TOKEN
docker build -t deepseek-balance-proxy .
docker run -d -p 5000:5000 --restart always --name ds-proxy deepseek-balance-proxy
```

### 直接部署

```bash
cd server
cp .env.example .env
pip install -r requirements.txt
python app.py
```

### Nginx 反代 + HTTPS

```nginx
server {
    listen 443 ssl;
    server_name your-domain.com;
    # SSL 配置...

    location /api/ {
        proxy_pass http://127.0.0.1:5000;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

### App 端配置

在 App 中启用服务器代理模式，填入你的服务器地址即可。

</details>

---

## 🛠️ 技术栈

| 类别 | 技术 |
|------|------|
| **语言** | Kotlin |
| **UI** | Jetpack Compose · Material 3 |
| **网络** | Retrofit · OkHttp · Gson |
| **本地存储** | DataStore Preferences |
| **后台任务** | WorkManager · 通知 |
| **服务器（可选）** | Python · Flask · Docker |

---

## 📁 项目结构

```
DeepSeekBalance/
├── app/
│   ├── src/main/
│   │   ├── java/com/deepseek/balance/
│   │   │   ├── MainActivity.kt           # 主入口
│   │   │   ├── data/
│   │   │   │   ├── api/DeepSeekApi.kt    # API 接口
│   │   │   │   ├── model/                 # 数据模型
│   │   │   │   ├── local/                 # 本地存储
│   │   │   │   └── repository/            # 仓库层
│   │   │   ├── viewmodel/                 # ViewModel
│   │   │   ├── ui/screens/                # 界面
│   │   │   ├── ui/theme/                  # 主题
│   │   │   └── worker/                    # 后台任务
│   │   └── res/                           # 资源
│   └── build.gradle.kts
├── server/                                # 服务器代理（可选）
│   ├── app.py                             # Flask 服务
│   ├── Dockerfile
│   └── requirements.txt
├── .github/workflows/build.yml            # CI 自动构建
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

---

## 🔒 隐私说明

- ✅ API Key **仅保存**在手机本地 DataStore 中
- ✅ 直连模式：**不经过任何第三方服务器**
- ✅ 代理模式：API Key 仅存储在你的**自有服务器**
- ✅ **零数据上报** · **无广告** · **无追踪**
- ✅ 开源代码可审计

---

## 📋 路线图

- [x] 余额实时查询
- [x] 今日/累计 Token 消耗统计
- [x] 今日/累计消费金额
- [x] 后台定时刷新 + 通知
- [x] 服务器代理模式
- [ ] 多账户支持
- [ ] 用量趋势图表
- [ ] 自定义告警阈值
- [ ] 国际化（英文）

---

## 🤝 贡献

欢迎提交 Issue 和 PR！请确保：

1. 代码风格与项目一致（Kotlin · Compose）
2. 提交前确保编译通过
3. 尽量附带截图或复现步骤

---

## 📄 License

[MIT](LICENSE) © 2026 Misaki0201

<p align="center">
  Made with ❤️ by <a href="https://github.com/Misaki0201">Misaki0201</a>
</p>
