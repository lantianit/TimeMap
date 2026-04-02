# TD-08：互关系统与照片可见性 - 技术方案

对应需求：用户互关 + 照片三级可见性（自己可见 / 互关可见 / 所有人可见）

## 1. 概念定义

### 1.1 互关（Mutual Follow）

- 用户 A 关注用户 B = 单向关注
- 用户 B 也关注了用户 A = 双向关注 = 互关
- 互关是两条独立的关注记录，不需要"申请-同意"流程
- 任何一方取消关注，互关关系即解除

### 1.2 照片可见性（Visibility）

| 值 | 含义 | 规则 |
|----|------|------|
| 0 | 仅自己可见 | 只有上传者本人能看到 |
| 1 | 互关可见 | 上传者本人 + 与上传者互关的用户可见 |
| 2 | 所有人可见 | 任何人都能看到（默认值） |

### 1.3 可见性判断逻辑

```
查看者是照片上传者本人？ → 可见
visibility = 2？ → 可见
visibility = 1 且查看者与上传者互关？ → 可见
visibility = 0？ → 仅上传者本人可见
其他情况 → 不可见
```

## 2. 数据库设计

### 2.1 新增表：关注关系表 `t_follow`

```sql
CREATE TABLE IF NOT EXISTS `t_follow` (
  `id` BIGINT NOT NULL COMMENT '主键',
  `user_id` BIGINT NOT NULL COMMENT '关注者ID（谁发起关注）',
  `target_user_id` BIGINT NOT NULL COMMENT '被关注者ID',
  `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '关注时间',
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_user_target` (`user_id`, `target_user_id`),
  INDEX `idx_target_user` (`target_user_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='关注关系表';
```

设计说明：
- 每条记录代表一个单向关注关系
- `uk_user_target` 唯一索引防止重复关注
- `idx_target_user` 反向索引，用于查询"谁关注了我"和判断互关
- 不设 `status` 字段，关注即插入，取消关注即删除，简单直接
- 互关判断：同时存在 `(A→B)` 和 `(B→A)` 两条记录

### 2.2 修改表：照片表 `t_photo` 新增字段

```sql
ALTER TABLE `t_photo` ADD COLUMN `visibility` TINYINT NOT NULL DEFAULT 2
  COMMENT '可见性: 0=仅自己 1=互关可见 2=所有人可见' AFTER `district`;

-- 为可见性过滤加索引（与现有 district_date 索引配合）
ALTER TABLE `t_photo` ADD INDEX `idx_visibility` (`visibility`, `deleted`);
```

### 2.3 互关判断 SQL 模式

在各查询中判断互关关系的核心 SQL 片段：

```sql
-- 判断 viewerUserId 与照片上传者是否互关
EXISTS (
  SELECT 1 FROM t_follow f1
  INNER JOIN t_follow f2
    ON f1.user_id = f2.target_user_id
    AND f1.target_user_id = f2.user_id
  WHERE f1.user_id = #{viewerUserId}
    AND f1.target_user_id = p.user_id
)
```

### 2.4 通用可见性过滤 SQL 片段

所有照片查询都需要加上的 WHERE 条件：

```sql
-- 未登录用户（viewerUserId 为 null）：只能看 visibility=2
-- 已登录用户：能看自己的所有照片 + 互关用户的 visibility<=1 照片 + 所有 visibility=2 照片
<if test="viewerUserId == null">
  AND p.visibility = 2
</if>
<if test="viewerUserId != null">
  AND (
    p.visibility = 2
    OR p.user_id = #{viewerUserId}
    OR (p.visibility = 1 AND EXISTS (
      SELECT 1 FROM t_follow f1
      INNER JOIN t_follow f2
        ON f1.user_id = f2.target_user_id
        AND f1.target_user_id = f2.user_id
      WHERE f1.user_id = #{viewerUserId}
        AND f1.target_user_id = p.user_id
    ))
  )
</if>
```

## 3. 后端实现

### 3.1 新增/修改文件清单

```
server/src/main/java/com/maptrace/
├── model/entity/Follow.java                    # 新增：关注实体
├── mapper/FollowMapper.java                    # 新增：关注 Mapper
├── service/FollowService.java                  # 新增：关注服务接口
├── service/impl/FollowServiceImpl.java         # 新增：关注服务实现
├── controller/FollowController.java            # 新增：关注接口
├── model/vo/FollowUserVO.java                  # 新增：关注列表返回 VO
├── model/vo/UserRelationVO.java                # 新增：用户关系状态 VO
│
├── model/entity/Photo.java                     # 修改：加 visibility 字段
├── controller/PhotoController.java             # 修改：upload 加 visibility 参数
├── service/impl/PhotoServiceImpl.java          # 修改：查询逻辑加可见性过滤
├── mapper/PhotoMapper.java                     # 修改：SQL 加可见性条件
├── config/WebConfig.java                       # 修改：配置新接口的拦截规则
└── service/NotificationService.java            # 修改：新增 follow 通知类型
```

### 3.2 关注模块接口设计

#### POST /api/follow/toggle

关注/取消关注（切换）

请求参数：
```json
{ "targetUserId": 123456 }
```

响应：
```json
{
  "code": 0,
  "data": {
    "followed": true,
    "mutual": true
  }
}
```

逻辑：
1. 不能关注自己
2. 查询是否已关注 → 已关注则删除（取消关注），未关注则插入
3. 关注成功后，检查对方是否也关注了自己 → 返回 `mutual` 状态
4. 关注成功时，发送通知给被关注者（类型 `follow`）
5. 如果形成互关，双方都收到 `mutual_follow` 通知

#### GET /api/follow/status?targetUserId=123

查询与某用户的关注关系

响应：
```json
{
  "code": 0,
  "data": {
    "followed": true,
    "followedBy": true,
    "mutual": true
  }
}
```

#### GET /api/follow/following?page=1&size=20

我的关注列表

响应：
```json
{
  "code": 0,
  "data": {
    "list": [
      {
        "userId": 123,
        "nickname": "用户A",
        "avatarUrl": "https://...",
        "mutual": true
      }
    ],
    "total": 15,
    "hasMore": false
  }
}
```

#### GET /api/follow/followers?page=1&size=20

我的粉丝列表（格式同上，`mutual` 表示是否互关）

#### GET /api/follow/count

关注/粉丝计数

响应：
```json
{
  "code": 0,
  "data": {
    "followingCount": 15,
    "followerCount": 23,
    "mutualCount": 8
  }
}
```

### 3.3 照片上传接口修改

`POST /api/photo/upload` 新增可选参数：

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|------|------|------|--------|------|
| visibility | Integer | 否 | 2 | 0=仅自己 1=互关可见 2=所有人 |

### 3.4 照片修改可见性接口（新增）

`POST /api/photo/updateVisibility`

```json
请求：{ "photoId": 123, "visibility": 1 }
响应：{ "code": 0 }
```

只有照片所有者可以修改。

### 3.5 需要加可见性过滤的查询（影响范围）

| 方法 | 当前行为 | 改造方式 |
|------|----------|----------|
| `PhotoMapper.findNearby()` | 返回范围内所有照片 | SQL 加可见性过滤，需传入 viewerUserId |
| `PhotoMapper.findCommunity()` | 返回区县所有照片 | SQL 加可见性过滤，需传入 viewerUserId |
| `PhotoMapper.findDistrictRanking()` | 统计所有照片 | 只统计 visibility=2 的照片 |
| `PhotoMapper.countByDistrict()` | 统计区县照片数 | 只统计 visibility=2 的照片 |
| `PhotoMapper.countTodayByDistrict()` | 统计今日照片数 | 只统计 visibility=2 的照片 |
| `PhotoServiceImpl.getDetail()` | 返回任意照片详情 | 加权限校验，无权限返回 null 或 403 |
| `PhotoServiceImpl.getBatchDetail()` | 批量返回详情 | 过滤掉无权限的照片 |
| `PhotoMapper.findMyPhotos()` | 查自己的照片 | 不需要改（自己看自己的全部可见） |
| `PhotoController.userPhotos()` | 查他人主页照片 | 加可见性过滤，需传入 viewerUserId |

### 3.6 WebConfig 拦截器配置修改

```java
// optionalJwtInterceptor 新增（有 token 解析 userId，没有也放行）：
"/api/follow/count"

// jwtInterceptor excludePathPatterns 新增：
"/api/follow/count"

// jwtInterceptor 默认拦截（需要登录）：
// /api/follow/toggle, /api/follow/status, /api/follow/following, /api/follow/followers
// 这些已被 /api/** 覆盖，无需额外配置
```

### 3.7 核心流程图

#### 关注/取消关注

```
用户点击"关注"按钮
    │
    ▼
FollowController.toggle(targetUserId)
    │
    ▼
FollowServiceImpl.toggle(userId, targetUserId)
    │
    ├── userId == targetUserId → 抛异常"不能关注自己"
    │
    ├── 查询 t_follow 是否已存在 (userId → targetUserId)
    │       │
    │       ├── 已存在 → DELETE → 返回 { followed: false, mutual: false }
    │       │
    │       └── 不存在 → INSERT → 检查反向记录是否存在
    │                               │
    │                               ├── 反向存在 → 互关成立 → 通知双方
    │                               │   返回 { followed: true, mutual: true }
    │                               │
    │                               └── 反向不存在 → 单向关注 → 通知被关注者
    │                                   返回 { followed: true, mutual: false }
```

#### 照片可见性判断

```
请求照片数据（nearby / community / detail / batch）
    │
    ▼
从 request attribute 获取 viewerUserId（可能为 null）
    │
    ▼
SQL 查询加可见性过滤条件
    │
    ├── viewerUserId == null → 只返回 visibility=2 的照片
    │
    └── viewerUserId != null
            │
            ├── visibility=2 → 可见
            ├── user_id == viewerUserId → 可见（自己的照片）
            ├── visibility=1 且互关 → 可见
            └── 其他 → 不可见（被过滤掉）
```

## 4. 前端实现

### 4.1 新增/修改文件清单

```
miniprogram/
├── pages/
│   ├── user/user.js              # 修改：加关注按钮、显示关注状态
│   ├── user/user.wxml            # 修改：加关注按钮 UI
│   ├── user/user.wxss            # 修改：关注按钮样式
│   ├── follow-list/follow-list.js    # 新增：关注/粉丝列表页
│   ├── follow-list/follow-list.wxml  # 新增
│   ├── follow-list/follow-list.wxss  # 新增
│   ├── follow-list/follow-list.json  # 新增
│   ├── profile/profile.js        # 修改：显示关注/粉丝数，入口
│   ├── profile/profile.wxml      # 修改：加关注数/粉丝数展示
│   ├── map/map.js                # 修改：上传时传 visibility 参数
│   ├── map/map.wxml              # 修改：上传栏加可见性选择
│   └── detail/detail.js          # 修改：处理无权限照片的提示
├── components/
│   └── visibility-picker/        # 新增：可见性选择组件（复用于上传和编辑）
│       ├── visibility-picker.js
│       ├── visibility-picker.wxml
│       ├── visibility-picker.wxss
│       └── visibility-picker.json
└── app.json                      # 修改：注册新页面
```

### 4.2 用户主页关注交互

```
进入他人主页 /pages/user/user?userId=xxx
    │
    ▼
调用 GET /api/follow/status?targetUserId=xxx
    │
    ▼
根据返回状态显示按钮：
    ├── mutual=true → 显示"互关" (灰色，点击可取消)
    ├── followed=true, mutual=false → 显示"已关注" (灰色)
    ├── followedBy=true, followed=false → 显示"回关" (绿色高亮)
    └── 都是 false → 显示"关注" (绿色)
    │
    ▼
点击按钮 → POST /api/follow/toggle
    │
    ▼
更新按钮状态（乐观更新）
```

### 4.3 个人中心关注数展示

```
┌─────────────────────────────┐
│  头像  昵称                  │
│                             │
│  15 关注  |  23 粉丝  |  8 互关  │
│  (可点击)    (可点击)    (可点击)  │
└─────────────────────────────┘
```

点击跳转到 `/pages/follow-list/follow-list?type=following` 或 `?type=followers` 或 `?type=mutual`

### 4.4 上传时可见性选择

在上传栏（底部操作栏）中，日期选择旁边增加可见性选择：

```
┌──────────────────────────────────────┐
│ 取消  |  2026-03-20  |  🌐所有人可见 ▼  │
│                                      │
│          [ 去上传 ]                   │
└──────────────────────────────────────┘
```

点击"🌐所有人可见"弹出 ActionSheet：
- 🌐 所有人可见（默认）
- 🤝 互关好友可见
- 🔒 仅自己可见

### 4.5 照片详情页可见性标识

在照片详情页信息卡片中显示可见性图标：
- visibility=2 → 不显示（默认状态无需标注）
- visibility=1 → 显示 🤝 标识
- visibility=0 → 显示 🔒 标识

仅照片所有者能看到此标识，并可点击修改。

## 5. 通知类型扩展

`t_notification.type` 新增两个值：

| type | 含义 | content 示例 |
|------|------|-------------|
| `follow` | 有人关注了你 | "关注了你" |
| `mutual_follow` | 你们成为互关好友 | "你们已成为互关好友" |

## 6. 性能考量

### 6.1 互关判断的性能

每次照片查询都要判断互关关系，涉及 `t_follow` 表的 JOIN。优化策略：

1. `t_follow` 表的 `uk_user_target (user_id, target_user_id)` 和 `idx_target_user (target_user_id, user_id)` 两个索引覆盖了正向和反向查询，EXISTS 子查询走索引，单次判断 O(1)
2. 对于列表查询（nearby 返回最多 200 条，community 每页 20 条），EXISTS 子查询会对每行执行一次，但因为走索引且 `t_follow` 表数据量相对小，性能可接受
3. 后续如果用户量大（10w+），可以引入 Redis 缓存互关关系：
   - Key: `mutual:{userId}` → Set 存储互关用户 ID
   - 关注/取消关注时同步更新缓存
   - 照片查询时先从 Redis 获取互关列表，用 `IN` 条件替代 `EXISTS` 子查询

### 6.2 统计类查询的简化

`findDistrictRanking`、`countByDistrict` 等统计接口不需要做互关判断，直接只统计 `visibility=2` 的照片即可。原因：
- 统计是全局聚合数据，不针对特定查看者
- 非公开照片不应影响公共排行榜数据

### 6.3 索引建议

```sql
-- t_follow 已有的两个索引足够覆盖所有查询场景
-- uk_user_target: 查"我是否关注了某人" + 防重复
-- idx_target_user: 查"某人是否关注了我" + 粉丝列表

-- t_photo 新增的 visibility 索引
-- idx_visibility: 用于统计类查询快速过滤 visibility=2
```

## 7. 分阶段实施建议

### 第一阶段：关注系统（约 1 周）

1. 建 `t_follow` 表
2. 实现 `FollowController` 全部接口
3. 小程序用户主页加关注按钮
4. 个人中心加关注/粉丝数
5. 关注/粉丝列表页
6. 关注通知

### 第二阶段：照片可见性（约 1.5 周）

1. `t_photo` 加 `visibility` 字段
2. 上传接口支持 `visibility` 参数
3. 小程序上传 UI 加可见性选择
4. 改造所有照片查询 SQL（加可见性过滤）
5. 照片详情页权限校验
6. 照片可见性修改接口
7. 全面测试各场景

### 第三阶段：体验优化（约 0.5 周）

1. 照片详情页显示可见性标识
2. 个人主页照片列表标注可见性
3. 无权限照片的友好提示
4. 边界场景处理（取消互关后，对方已缓存的照片处理等）

总计预估：约 3 周
