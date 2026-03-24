# NomNom MVP PRD + 接口设计 + 表结构

## 1. 产品定位

NomNom 是一个 iOS App，目标不是替代大众点评，而是解决用户已经有一些候选餐厅时的决策困难。

MVP 版本聚焦三件事：

1. 基于位置与筛选条件，快速筛出一批可吃的餐厅。
2. 把用户在大众点评里看到并收藏过的餐厅，低风险地导入到 NomNom。
3. 让用户通过两两比较，最终选出一家的晚饭目的地。

一句话定位：

> 用“大众点评式筛选 + 收藏导入 + 两两 PK”，把“今晚吃什么”从搜索问题变成决策问题。

## 2. 关键取舍

### 2.1 这版 MVP 明确做什么

1. 支持按位置、菜系、价格、营业状态、距离、评分等维度筛餐厅。
2. 支持把大众点评里的单店链接或分享文本导入到 NomNom。
3. 支持用户从搜索结果和收藏列表里组建候选池。
4. 支持 2 到 32 家候选餐厅的两两比较，直到选出最终结果。
5. 支持查看最终结果、对战过程、胜出原因。

### 2.2 这版 MVP 明确不做什么

1. 不做大众点评账号级 OAuth 登录。
2. 不做“大众点评整个收藏夹一键全量同步”。
3. 不依赖大众点评私有接口、模拟登录或 App 抓包。
4. 不做商家下单、排队、团购、预约。
5. 不做复杂 AI 排名模型，MVP 使用可解释、可测试的规则与淘汰赛机制。

### 2.3 为什么这样设计

当前公开信息里，我没有检索到可直接供普通开发者读取“大众点评个人收藏夹”的公开 API。为了让方案可以真实落地并可上架，MVP 采用：

1. 搜索数据源使用公开可接入的 POI/LBS 服务。
2. 大众点评导入使用 iOS Share Extension 接收“分享出来的文本和链接”。
3. 如果分享数据不完整，则在 App 内补全少量字段。

这条路线能做、能测、能发布。

## 3. 目标用户

### 3.1 核心用户

1. 一二线城市年轻人，工作日晚饭、周末约会、朋友聚餐场景频繁。
2. 平时会刷大众点评、地图、社交平台收藏店，但真正决定去哪吃很慢。
3. 有明确筛选偏好，但最后一步决策困难。

### 3.2 典型场景

1. 两个人下班后，想在 30 分钟内定一家店。
2. 周末约会，从收藏的十几家店里选一家。
3. 朋友聚餐，先按人均和距离筛一轮，再用 PK 决定。

## 4. MVP 目标

### 4.1 业务目标

1. 用户在 60 秒内完成“筛选 -> 组候选池 -> 开始 PK”。
2. 用户在 3 分钟内完成最终选择。
3. 收藏导入链路成功率达到 80% 以上。

### 4.2 产品成功指标

1. `search_to_session_rate`
   搜索后发起决策会话的比例，目标 >= 25%。
2. `session_completion_rate`
   决策会话完成率，目标 >= 70%。
3. `import_success_rate`
   分享导入成功率，目标 >= 80%。
4. `decision_time_p50`
   从点击开始 PK 到选出结果的中位时间，目标 <= 180 秒。

## 5. 核心用户故事

1. 作为用户，我想按“商圈、菜系、人均、距离、营业中、评分”筛出一批店。
2. 作为用户，我想把大众点评里看到的某家店，通过分享直接加入 NomNom 收藏池。
3. 作为用户，我想从搜索结果和收藏里勾选若干家店，组成今晚候选池。
4. 作为用户，我想在每一轮只比较两家，看清价格、距离、评分、标签和我自己的备注。
5. 作为用户，我想在所有比较结束后得到一个明确结果，并能看到它为什么胜出。

## 6. 功能范围

## 6.1 搜索与筛选

### 必做字段

1. 城市
2. 定位点
3. 距离范围
4. 商圈 / 行政区
5. 菜系大类
6. 人均价格区间
7. 评分下限
8. 是否营业中
9. 排序方式

### 排序方式

1. 距离优先
2. 评分优先
3. 人均从低到高
4. 人均从高到低
5. 综合推荐

### 参考大众点评但先不做的筛选项

1. 包厢
2. 停车
3. 团购
4. 排队
5. 品牌连锁
6. 适合场景标签

这些字段先保留扩展位，不进入 MVP 主流程。

## 6.2 收藏导入

### MVP 支持方式

1. 分享导入
   用户在大众点评里点击“分享”，把文本或链接分享到 NomNom。
2. 批量粘贴链接
   用户把多个大众点评店铺链接粘贴到 NomNom，由后端批量创建导入任务。
3. 手动补全
   对于未能自动识别完整信息的店铺，用户补全名称、地址、价格、标签后入库。

### 不在 MVP 范围内

1. 账号密码登录大众点评
2. 自动读取整份收藏夹
3. 后台持续同步大众点评收藏变化

## 6.3 候选池

1. 搜索结果可一键加入候选池。
2. 收藏列表可多选加入候选池。
3. 候选池至少 2 家，最多 32 家。
4. 同一家店如果从多个来源加入，需要自动去重。
5. 候选池支持手动排序和删除。

## 6.4 两两比较

### 交互规则

1. 每轮只展示两家。
2. 展示字段：
   餐厅名、封面、菜系、人均、距离、评分、营业状态、来源标签、我的备注。
3. 用户操作：
   左边胜、右边胜、跳过本轮、结束并查看当前领先结果。
4. 会话支持中断恢复。

### MVP 算法

MVP 采用“带种子的单败淘汰赛”：

1. 创建会话时，对候选池按综合分进行种子排序。
2. 第一轮按“高种子对低种子”配对。
3. 奇数时自动给最高种子轮空。
4. 每轮胜者进入下一轮，直到只剩 1 家。

### 综合分

综合分只用于初始种子，不直接替用户做决定：

1. 评分
2. 距离
3. 是否营业中
4. 是否来自用户收藏
5. 是否命中当前筛选条件

## 6.5 结果页

1. 展示最终胜出餐厅。
2. 展示对战路径。
3. 展示胜出原因：
   评分更高、距离更近、价格更合适、用户收藏、营业中。
4. 支持“重新开始”。
5. 支持把结果加入“今晚决定”历史。

## 7. 典型用户流程

### 流程 A：筛附近的店再 PK

1. 用户打开 App。
2. 允许定位或选择城市。
3. 设置筛选条件。
4. 浏览搜索结果，勾选 4 到 10 家。
5. 发起决策。
6. 完成若干轮 PK。
7. 查看最终结果并导航。

### 流程 B：从大众点评导入再 PK

1. 用户在大众点评中分享某家店到 NomNom。
2. NomNom 接收分享内容并解析。
3. 用户确认或补全店铺信息。
4. 该店加入收藏池。
5. 用户继续导入 3 到 5 家后发起决策。

## 8. 技术方案

## 8.1 客户端

1. `SwiftUI`
2. `MapKit` 或腾讯地图 iOS SDK 用于地图展示与选点
3. `Share Extension` 接收大众点评分享内容
4. `URLSession` 调用 Java 后端
5. 本地缓存使用 `SwiftData` 或轻量本地存储缓存最近一次搜索、草稿候选池

## 8.2 服务端

1. `Java 21`
2. `Spring Boot 3`
3. `PostgreSQL`
4. `Redis`
   用于会话状态缓存、幂等与短期热点缓存
5. Provider 抽象层
   - `PoiSearchProvider`
   - `ImportParser`
   - `VenueNormalizer`

## 8.3 数据源策略

### 搜索数据

使用公开 POI 服务作为主数据源，例如腾讯位置服务。

### 收藏导入

1. 优先从分享文本中提取：
   店名、地址、来源链接、来源 ID。
2. 如能从来源链接识别外部 ID，则写入来源映射表。
3. 如信息不足，则转为“待补全导入”状态，由用户补字段。

## 9. 权限与账号

MVP 推荐使用“设备账号 + 可选 Apple 登录升级”的组合。

### 设备账号

1. App 首次启动生成 `installation_id`
2. 后端分配 `user_id`
3. 返回访问令牌

### Apple 登录

1. 后续可把设备账号绑定到 Apple 账号
2. 解决换机同步问题

## 10. 接口设计

统一约定：

1. Base URL: `/api/v1`
2. 鉴权头：`Authorization: Bearer <token>`
3. 返回时间字段使用 ISO 8601 UTC
4. 错误格式统一：

```json
{
  "error": {
    "code": "INVALID_ARGUMENT",
    "message": "budget_max must be greater than budget_min",
    "request_id": "req_123"
  }
}
```

## 10.1 设备注册

### `POST /api/v1/auth/device`

#### 请求

```json
{
  "installationId": "7a52f7c6-56d8-4c74-a2de-95ea0c2f57f2",
  "deviceName": "iPhone 16 Pro",
  "appVersion": "1.0.0"
}
```

#### 响应

```json
{
  "userId": "9a5c3c86-96c5-4d7e-a4d0-76f47106c61f",
  "accessToken": "jwt-token",
  "expiresAt": "2026-03-30T12:00:00Z"
}
```

## 10.2 搜索餐厅

### `POST /api/v1/venues/search`

#### 请求

```json
{
  "cityCode": "shanghai",
  "latitude": 31.2281,
  "longitude": 121.4547,
  "radiusMeters": 3000,
  "district": "静安区",
  "businessArea": "静安寺",
  "categories": ["日料", "烧肉", "居酒屋"],
  "priceMin": 80,
  "priceMax": 220,
  "ratingMin": 4.0,
  "openNow": true,
  "sortBy": "RECOMMENDED",
  "page": 1,
  "pageSize": 20
}
```

#### 响应

```json
{
  "items": [
    {
      "venueId": "87f95d73-1f3d-4bd7-aa3b-5193f9601f93",
      "name": "鸟居烧肉",
      "coverImageUrl": "https://cdn.example.com/1.jpg",
      "category": "日料",
      "subcategory": "日式烧肉",
      "avgPrice": 138,
      "rating": 4.6,
      "reviewCount": 831,
      "distanceMeters": 820,
      "openStatus": "OPEN",
      "district": "静安区",
      "businessArea": "静安寺",
      "address": "南京西路 xxx 号",
      "sourceProvider": "TENCENT_LBS",
      "sourceUrl": "https://example.com/poi/123",
      "isInFavorites": true
    }
  ],
  "page": 1,
  "pageSize": 20,
  "hasMore": true
}
```

## 10.3 导入大众点评分享

### `POST /api/v1/imports/share-links`

#### 请求

```json
{
  "imports": [
    {
      "sourceProvider": "DIANPING",
      "sharedText": "鸟居烧肉，快来看看吧 https://m.dianping.com/shopshare/abc123",
      "sharedUrl": "https://m.dianping.com/shopshare/abc123"
    }
  ]
}
```

#### 响应

```json
{
  "results": [
    {
      "importJobId": "ee7d41b1-9bc1-4c87-a2b8-d3e38f850f3c",
      "status": "IMPORTED",
      "venueId": "87f95d73-1f3d-4bd7-aa3b-5193f9601f93",
      "requiresManualCompletion": false
    }
  ]
}
```

### 可能状态

1. `IMPORTED`
2. `PENDING_MANUAL_COMPLETION`
3. `DUPLICATED`
4. `FAILED`

## 10.4 完成导入补全

### `POST /api/v1/imports/{importJobId}/complete`

#### 请求

```json
{
  "name": "鸟居烧肉",
  "category": "日式烧肉",
  "avgPrice": 138,
  "district": "静安区",
  "businessArea": "静安寺",
  "address": "南京西路 xxx 号",
  "tags": ["约会", "烧肉", "收藏导入"]
}
```

#### 响应

```json
{
  "importJobId": "ee7d41b1-9bc1-4c87-a2b8-d3e38f850f3c",
  "status": "IMPORTED",
  "venueId": "87f95d73-1f3d-4bd7-aa3b-5193f9601f93"
}
```

## 10.5 获取收藏列表

### `GET /api/v1/lists/default`

#### 响应

```json
{
  "listId": "b8c9ac89-492e-4eb2-a3cc-3be7b19a7b3a",
  "name": "我的收藏",
  "items": [
    {
      "itemId": "f6d70d58-f5e0-4e34-935e-d0f95ab92f5d",
      "venueId": "87f95d73-1f3d-4bd7-aa3b-5193f9601f93",
      "name": "鸟居烧肉",
      "avgPrice": 138,
      "rating": 4.6,
      "distanceMeters": 820,
      "note": "适合约会",
      "sourceProvider": "DIANPING"
    }
  ]
}
```

## 10.6 添加到收藏

### `POST /api/v1/lists/{listId}/items`

#### 请求

```json
{
  "venueId": "87f95d73-1f3d-4bd7-aa3b-5193f9601f93",
  "note": "下次约会可以来"
}
```

#### 响应

```json
{
  "itemId": "f6d70d58-f5e0-4e34-935e-d0f95ab92f5d"
}
```

## 10.7 创建决策会话

### `POST /api/v1/decision-sessions`

#### 请求

```json
{
  "name": "今晚吃什么",
  "context": {
    "peopleCount": 2,
    "priceMin": 80,
    "priceMax": 220,
    "openNow": true,
    "district": "静安区",
    "businessArea": "静安寺"
  },
  "candidateVenueIds": [
    "87f95d73-1f3d-4bd7-aa3b-5193f9601f93",
    "c7ea0990-08cf-4c6e-9850-cad2f8e5b6de",
    "5af5c7d6-b1d6-4ea8-8f5a-c767ea2f4669",
    "658d11f9-d737-4f5c-8ce9-d2d2c650fe4c"
  ]
}
```

#### 响应

```json
{
  "sessionId": "7d5b1320-360d-4b20-bc30-6532abf52f4a",
  "status": "IN_PROGRESS",
  "round": 1,
  "nextMatchup": {
    "matchupId": "e0bbd0d1-558b-445c-9d96-e46b39f0fdf3",
    "left": {
      "venueId": "87f95d73-1f3d-4bd7-aa3b-5193f9601f93",
      "name": "鸟居烧肉",
      "avgPrice": 138,
      "rating": 4.6,
      "distanceMeters": 820,
      "openStatus": "OPEN"
    },
    "right": {
      "venueId": "5af5c7d6-b1d6-4ea8-8f5a-c767ea2f4669",
      "name": "炭吉居酒屋",
      "avgPrice": 118,
      "rating": 4.4,
      "distanceMeters": 430,
      "openStatus": "CLOSED"
    }
  }
}
```

## 10.8 获取会话详情

### `GET /api/v1/decision-sessions/{sessionId}`

#### 响应

```json
{
  "sessionId": "7d5b1320-360d-4b20-bc30-6532abf52f4a",
  "status": "IN_PROGRESS",
  "round": 2,
  "context": {
    "peopleCount": 2,
    "priceMin": 80,
    "priceMax": 220
  },
  "history": [
    {
      "matchupId": "e0bbd0d1-558b-445c-9d96-e46b39f0fdf3",
      "round": 1,
      "leftVenueId": "87f95d73-1f3d-4bd7-aa3b-5193f9601f93",
      "rightVenueId": "5af5c7d6-b1d6-4ea8-8f5a-c767ea2f4669",
      "winnerVenueId": "87f95d73-1f3d-4bd7-aa3b-5193f9601f93",
      "decidedAt": "2026-03-23T07:00:00Z"
    }
  ],
  "nextMatchup": {
    "matchupId": "4b2fd3c0-67fd-4e3e-a4ad-5590b904f0da",
    "left": {
      "venueId": "c7ea0990-08cf-4c6e-9850-cad2f8e5b6de",
      "name": "山葵割烹"
    },
    "right": {
      "venueId": "658d11f9-d737-4f5c-8ce9-d2d2c650fe4c",
      "name": "汤城小厨"
    }
  }
}
```

## 10.9 提交本轮选择

### `POST /api/v1/decision-sessions/{sessionId}/votes`

#### 请求

```json
{
  "matchupId": "4b2fd3c0-67fd-4e3e-a4ad-5590b904f0da",
  "winnerVenueId": "c7ea0990-08cf-4c6e-9850-cad2f8e5b6de"
}
```

#### 响应

```json
{
  "sessionId": "7d5b1320-360d-4b20-bc30-6532abf52f4a",
  "status": "IN_PROGRESS",
  "round": 2,
  "history": [
    {
      "matchupId": "e0bbd0d1-558b-445c-9d96-e46b39f0fdf3",
      "round": 1,
      "winnerVenueId": "87f95d73-1f3d-4bd7-aa3b-5193f9601f93"
    },
    {
      "matchupId": "4b2fd3c0-67fd-4e3e-a4ad-5590b904f0da",
      "round": 1,
      "winnerVenueId": "c7ea0990-08cf-4c6e-9850-cad2f8e5b6de"
    }
  ],
  "nextMatchup": {
    "matchupId": "f527b26e-bc5e-4ad4-af24-65304bb5ab48",
    "left": {
      "venueId": "87f95d73-1f3d-4bd7-aa3b-5193f9601f93",
      "name": "鸟居烧肉"
    },
    "right": {
      "venueId": "c7ea0990-08cf-4c6e-9850-cad2f8e5b6de",
      "name": "山葵割烹"
    }
  }
}
```

## 10.10 获取结果

### `GET /api/v1/decision-sessions/{sessionId}/result`

#### 响应

```json
{
  "sessionId": "7d5b1320-360d-4b20-bc30-6532abf52f4a",
  "status": "COMPLETED",
  "winner": {
    "venueId": "87f95d73-1f3d-4bd7-aa3b-5193f9601f93",
    "name": "鸟居烧肉",
    "avgPrice": 138,
    "rating": 4.6,
    "distanceMeters": 820,
    "openStatus": "OPEN"
  },
  "reasons": [
    "评分更高",
    "当前营业中",
    "来自你的收藏列表"
  ],
  "history": [
    {
      "matchupId": "e0bbd0d1-558b-445c-9d96-e46b39f0fdf3",
      "round": 1,
      "winnerVenueId": "87f95d73-1f3d-4bd7-aa3b-5193f9601f93"
    },
    {
      "matchupId": "4b2fd3c0-67fd-4e3e-a4ad-5590b904f0da",
      "round": 1,
      "winnerVenueId": "c7ea0990-08cf-4c6e-9850-cad2f8e5b6de"
    },
    {
      "matchupId": "f527b26e-bc5e-4ad4-af24-65304bb5ab48",
      "round": 2,
      "winnerVenueId": "87f95d73-1f3d-4bd7-aa3b-5193f9601f93"
    }
  ]
}
```

## 11. 表结构

数据库默认使用 PostgreSQL。

## 11.1 用户与鉴权

```sql
create table app_user (
    id uuid primary key,
    status varchar(16) not null default 'ACTIVE',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create table user_device (
    id uuid primary key,
    user_id uuid not null references app_user(id),
    installation_id uuid not null,
    device_name varchar(128),
    app_version varchar(32),
    last_seen_at timestamptz not null default now(),
    created_at timestamptz not null default now(),
    unique (installation_id)
);

create table auth_identity (
    id uuid primary key,
    user_id uuid not null references app_user(id),
    provider varchar(32) not null,
    provider_subject varchar(255) not null,
    created_at timestamptz not null default now(),
    unique (provider, provider_subject)
);
```

## 11.2 餐厅主表与来源映射

```sql
create table venue (
    id uuid primary key,
    name varchar(255) not null,
    normalized_name varchar(255),
    city_code varchar(32),
    district varchar(64),
    business_area varchar(64),
    address varchar(255),
    latitude numeric(10, 6),
    longitude numeric(10, 6),
    category varchar(64),
    subcategory varchar(64),
    avg_price integer,
    rating numeric(3, 2),
    review_count integer,
    open_status varchar(16),
    phone varchar(64),
    cover_image_url text,
    tags jsonb not null default '[]'::jsonb,
    source_provider varchar(32) not null,
    source_url text,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index idx_venue_city_category on venue(city_code, category);
create index idx_venue_district_business_area on venue(district, business_area);
create index idx_venue_rating on venue(rating desc);
create index idx_venue_avg_price on venue(avg_price);
```

```sql
create table venue_source_ref (
    id uuid primary key,
    venue_id uuid not null references venue(id),
    provider varchar(32) not null,
    external_id varchar(255),
    source_url text,
    raw_payload jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (provider, external_id)
);
```

## 11.3 收藏列表

```sql
create table user_list (
    id uuid primary key,
    user_id uuid not null references app_user(id),
    name varchar(64) not null,
    list_type varchar(32) not null default 'FAVORITES',
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create unique index idx_user_default_list on user_list(user_id, list_type);
```

```sql
create table user_list_item (
    id uuid primary key,
    list_id uuid not null references user_list(id),
    venue_id uuid not null references venue(id),
    source_provider varchar(32),
    source_import_job_id uuid,
    note varchar(255),
    pinned boolean not null default false,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (list_id, venue_id)
);

create index idx_user_list_item_list_created on user_list_item(list_id, created_at desc);
```

## 11.4 导入任务

```sql
create table import_job (
    id uuid primary key,
    user_id uuid not null references app_user(id),
    source_provider varchar(32) not null,
    shared_text text,
    shared_url text,
    parsed_name varchar(255),
    parsed_external_id varchar(255),
    status varchar(32) not null,
    failure_reason varchar(255),
    venue_id uuid references venue(id),
    raw_parse_result jsonb,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index idx_import_job_user_created on import_job(user_id, created_at desc);
create index idx_import_job_status on import_job(status);
```

## 11.5 决策会话

```sql
create table decision_session (
    id uuid primary key,
    user_id uuid not null references app_user(id),
    name varchar(128) not null,
    status varchar(32) not null,
    people_count integer,
    price_min integer,
    price_max integer,
    open_now boolean,
    district varchar(64),
    business_area varchar(64),
    started_at timestamptz,
    completed_at timestamptz,
    winner_venue_id uuid references venue(id),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index idx_decision_session_user_created on decision_session(user_id, created_at desc);
create index idx_decision_session_status on decision_session(status);
```

```sql
create table decision_session_candidate (
    id uuid primary key,
    session_id uuid not null references decision_session(id),
    venue_id uuid not null references venue(id),
    seed integer not null,
    initial_score numeric(10, 4) not null,
    eliminated boolean not null default false,
    eliminated_round integer,
    created_at timestamptz not null default now(),
    unique (session_id, venue_id)
);

create index idx_decision_candidate_session_seed on decision_session_candidate(session_id, seed);
```

```sql
create table decision_matchup (
    id uuid primary key,
    session_id uuid not null references decision_session(id),
    round_no integer not null,
    bracket_order integer not null,
    left_venue_id uuid not null references venue(id),
    right_venue_id uuid references venue(id),
    winner_venue_id uuid references venue(id),
    status varchar(16) not null,
    decided_at timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create unique index idx_matchup_session_round_order on decision_matchup(session_id, round_no, bracket_order);
create index idx_matchup_session_status on decision_matchup(session_id, status);
```

```sql
create table decision_vote (
    id uuid primary key,
    matchup_id uuid not null references decision_matchup(id),
    session_id uuid not null references decision_session(id),
    user_id uuid not null references app_user(id),
    winner_venue_id uuid not null references venue(id),
    created_at timestamptz not null default now(),
    unique (matchup_id, user_id)
);
```

## 12. 去重与数据归一策略

同一家店可能从搜索结果、分享导入、手动补录三个入口进入系统，MVP 需要做轻量去重：

1. 优先按 `provider + external_id` 命中。
2. 次优先按 `provider + source_url` 命中。
3. 再按 `normalized_name + district + address` 做弱匹配。
4. 命中后不新建 `venue`，只新增来源映射或列表项。

## 13. 幂等与稳定性要求

1. 导入接口支持客户端幂等键，防止重复导入。
2. 投票接口必须校验 `matchupId` 与会话状态，防止重复点击。
3. 获取会话详情接口必须返回完整历史，避免前端“过程为空”。
4. 结果接口与详情接口中的最终 winner 必须一致。

## 14. MVP 开发顺序

### Sprint 1

1. 设备登录
2. 餐厅搜索
3. 收藏列表
4. 手动加入候选池

### Sprint 2

1. 分享导入
2. 导入补全
3. 去重

### Sprint 3

1. 决策会话
2. Matchup 生成
3. 投票与结果页

### Sprint 4

1. 会话恢复
2. 埋点
3. 稳定性与灰度

## 15. 风险与后续演进

### 风险

1. 大众点评分享格式可能变化，导入解析要做兜底。
2. 不同 POI 数据源在价格、评分、营业状态字段上可能不完整。
3. 餐厅去重会有一定误判，需要运营工具后续纠偏。

### V1.5 方向

1. 批量导入增强
2. 多人共选
3. 收藏夹分组
4. 地图模式选候选
5. 更智能的排序与“提前收敛”机制
