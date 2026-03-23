# NomNom V1.5 Full-stack Demo

一个可在 Xcode 中调试运行的 iOS SwiftUI 演示工程，配套一个纯 Java 本地后端，聚焦 V1.5 决策页能力：

- 两两 PK 决策流程
- 规则型推荐理由
- AI 一句话简介（当前由后端基于样例评论摘要拼装）
- 好评 / 差评摘要
- iOS 前端通过 HTTP 调用 Java 后端接口

## 目录

- `NomNom.xcodeproj`: Xcode 工程
- `iOSApp/`: iOS SwiftUI 界面与网络请求
- `Sources/NomNomCore/`: 共享业务模型与本地测试逻辑
- `backend/`: 纯 Java 后端与联调脚本
- `Tests/NomNomCoreTests/`: Swift 核心逻辑测试

## 你拿到 Mac 后怎么运行

### 环境要求

- macOS
- Xcode 15+（建议）
- Command Line Tools
- Java 21+（运行本仓库里的后端脚本）

### 第 1 步：启动后端

在项目根目录执行：

```bash
./backend/run.sh
```

默认监听：

- `http://127.0.0.1:8080/api/v1/health`
- `http://127.0.0.1:8080/api/v1/restaurants`
- `http://127.0.0.1:8080/api/v1/decisions`

如果你想先确认后端真的起来了：

```bash
curl http://127.0.0.1:8080/api/v1/health
```

预期返回：

```json
{"status":"ok","service":"nomnom-backend"}
```

### 第 2 步：打开 iOS 工程

1. 双击 `NomNom.xcodeproj`
2. 选择 `NomNom` scheme
3. 选择一个 iPhone Simulator（推荐 iPhone 15 / iPhone 16）
4. 直接 Run

### 第 3 步：检查 App 内的连接状态

首页最上方会显示：

- 当前后端地址
- 连接状态（未检测 / 检测中 / 已连接 / 连接失败）
- “连接设置”入口

默认情况下：

- **Simulator** 直接使用 `http://127.0.0.1:8080`
- **真机** 需要把地址改成你 Mac 的局域网 IP，例如 `http://192.168.1.10:8080`

### 第 4 步：如果连接失败

请按下面顺序检查：

1. `./backend/run.sh` 是否还在运行
2. `curl http://127.0.0.1:8080/api/v1/health` 是否成功
3. App 里的“连接设置”地址是否正确
4. 如果是真机，后端地址是否改成了 Mac 的局域网 IP

## 本地联调验证

```bash
./backend/test.sh
swift test
python tools/render_mock_screens.py
```

## 当前版本说明

- Java 后端为无三方依赖的轻量 HTTP 服务，便于当前环境直接编译运行。
- iOS 前端已切换为实时请求后端，而不是直接消费本地假数据。
- iOS 工程已加入自定义 `Info.plist`，允许本地开发阶段直接访问 HTTP 后端。
- 首页提供“连接设置”面板，可修改后端地址并检测连通性。
- Swift Package 仍保留共享模型与基础逻辑测试，方便后续抽离公共模块。

## 模拟截图

可通过下面命令重新生成当前仓库里的模拟效果图：

```bash
python -m pip install pillow
python tools/render_mock_screens.py
```

生成产物（默认不纳入 git，以避免 PR 因二进制 PNG 失败）：

- `docs/screenshots/home.png`
- `docs/screenshots/decision.png`
- `docs/screenshots/result.png`

> 这些图片是基于当前页面结构和样例数据生成的模拟效果图，不是 iOS Simulator 的真实截屏。
> 如果你只是想提交代码，请不要把这些 PNG 加入版本库。
