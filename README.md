# NomNom MVP Rebuild

NomNom 正在从一个“餐厅决策 demo”重构为一套真正可扩展的 MVP：

1. iOS App 负责搜索、收藏导入、候选池管理和两两 PK 交互。
2. Java 后端升级为 Spring Boot 服务，提供鉴权、搜索、收藏、导入、决策会话接口。
3. 数据模型、接口设计和建表方案见 [docs/mvp-prd-api-schema.md](docs/mvp-prd-api-schema.md)。

## 当前仓库状态

目前仓库里同时存在两部分代码：

1. `backend/src/main/java/com/nomnom/mvp`
   新的 Spring Boot MVP 后端骨架，按新的产品方案继续推进。
2. `backend/src/com/nomnom/backend`
   旧版纯 Java demo 后端，暂时保留作参考，后续会逐步下线。
3. `iOSApp/`
   已接入新的 MVP 协议客户端，并完成搜索、收藏、候选池、两两 PK、结果页的首版交互骨架。

## 目录

1. `NomNom.xcodeproj`
   iOS 工程
2. `iOSApp/`
   iOS SwiftUI 界面
3. `Sources/NomNomCore/`
   旧版共享模型和决策逻辑
4. `backend/`
   新后端骨架、旧后端参考代码与运行脚本
5. `docs/mvp-prd-api-schema.md`
   MVP PRD、接口设计、表结构

## 后端启动

环境要求：

1. Java 21+
2. Maven 3.9+
3. PostgreSQL
   当前默认不开启 Flyway，所以没有数据库时也可以先只看接口骨架。

在项目根目录执行：

```bash
./backend/run.sh
```

默认监听：

1. `backend/run.sh` 默认优先使用 `8080`
2. 如果 `8080` 被占用，会自动切到下一个空闲端口并打印地址
3. 启动后可访问：
   `http://127.0.0.1:<port>/api/v1/health`
   `http://127.0.0.1:<port>/api/v1/auth/device`
   `http://127.0.0.1:<port>/api/v1/venues/filters`
   `http://127.0.0.1:<port>/api/v1/venues/search`

如需切到真实 POI 搜索，在项目根目录创建 `.env.local`：

```bash
AMAP_WEB_SERVICE_KEY=你的高德 Web 服务 Key
BAIDU_MAPS_API_KEY=可选，用于给高德结果补评分/人均/评论量
TENCENT_MAPS_API_KEY=可选，用于高德不可用时做兜底
TENCENT_MAPS_SECRET_KEY=如果腾讯开启了 SN 校验，则填写控制台里的 SK
NOMNOM_POI_PROVIDER=amap
NOMNOM_POI_FALLBACK_ENABLED=true
```

`./backend/run.sh` 和 `./backend/test.sh` 会自动读取这个文件。

当前默认策略：

1. 主搜索 provider 为 `amap`
2. 若高德不可用，且本机配置了腾讯 Key，则会自动尝试腾讯兜底
3. 若都不可用，则回退到仓库内置样例数据
4. 若同时配置了 `BAIDU_MAPS_API_KEY`，后端会为前几条高德结果补抓百度详情字段，用于优化排序信号

当前多 provider 分工：

1. `Amap` 负责主搜索和主筛选元数据
2. `Baidu` 只做详情增强，优先补 `rating / price / comment_num / detail_url`
3. `Tencent` 只做兜底，不参与默认主排序
4. `大众点评 / 美团` 当前未接入主链路，因为我还没有在公开官方文档里确认到适合直接接入排序的评分详情接口

未配置 `AMAP_WEB_SERVICE_KEY` 时，后端会明确提示，并在可用时退回腾讯或本地样例数据。

如需启用数据库迁移：

```bash
cd backend
FLYWAY_ENABLED=true DB_URL=jdbc:postgresql://127.0.0.1:5432/nomnom DB_USERNAME=nomnom DB_PASSWORD=nomnom mvn spring-boot:run
```

## 测试

```bash
./backend/test.sh
```

Swift 包测试：

```bash
./tools/swift-test.sh
```

如果你想检查本机环境是否齐全：

```bash
./tools/doctor.sh
```

注意：iOS App 的 Xcode 构建仍依赖完整 Xcode 首次初始化。如果本机还没接受 license，需要先执行：

```bash
sudo xcodebuild -license
sudo xcodebuild -runFirstLaunch
```

## 下一步

当前这次重构已经完成的部分：

1. Spring Boot 项目结构
2. Flyway 建表脚本
3. 搜索 / 导入 / 收藏 / 决策会话 API 骨架
4. iOS 端新协议客户端
5. iOS 首页筛选、收藏导入、候选池、PK 与结果页骨架

下一阶段会继续推进：

1. 会话状态持久化
2. 收藏导入的分享扩展
3. 接真实数据库并补齐真实筛选元数据
4. 百度详情增强与跨 provider 去重
