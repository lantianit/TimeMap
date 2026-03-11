# PRD-01：微信登录

## 1. 需求背景

地图时迹所有功能都依赖用户身份识别。用户上传图片需要关联上传者，后续的社区、私信等功能也需要用户体系。微信登录是最基础的前置依赖。

## 2. 功能描述

用户打开小程序时，静默完成微信登录，获取用户身份标识（openid），后端签发 JWT Token 用于后续接口鉴权。

## 3. 用户流程

```
用户打开小程序
    │
    ▼
小程序调用 wx.login() 获取临时 code
    │
    ▼
小程序将 code 发送到后端 /api/auth/login
    │
    ▼
后端用 code + AppID + AppSecret 调用微信接口换取 openid
    │
    ├── 新用户 → 自动创建用户记录
    └── 老用户 → 查询已有记录
    │
    ▼
后端签发 JWT Token，返回给小程序
    │
    ▼
小程序将 Token 存入本地缓存，后续请求携带 Token
```

## 4. 接口设计

### POST /api/auth/login

请求参数：

```json
{
  "code": "微信登录临时code"
}
```

成功响应：

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "userId": 1234567890,
    "isNew": true
  }
}
```

失败响应：

```json
{
  "code": 401,
  "message": "微信登录失败",
  "data": null
}
```

### 微信服务端接口（后端调用）

GET https://api.weixin.qq.com/sns/jscode2session

参数：
- appid：小程序 AppID
- secret：小程序 AppSecret
- js_code：小程序传来的 code
- grant_type：authorization_code

返回：
- openid：用户唯一标识
- session_key：会话密钥（需安全存储，不下发给前端）

## 5. 数据模型

使用已有的 t_user 表，无需新增字段。

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 主键，雪花算法生成 |
| openid | VARCHAR(64) | 微信 openid，唯一索引 |
| nickname | VARCHAR(64) | 昵称，默认空字符串，后续支持修改 |
| avatar_url | VARCHAR(512) | 头像，默认空字符串 |
| create_time | DATETIME | 创建时间，自动填充 |
| update_time | DATETIME | 更新时间，自动填充 |
| deleted | TINYINT | 逻辑删除，默认 0 |

## 6. JWT Token 规则

- 签名算法：HS256
- 有效期：7 天
- Payload 包含：userId、openid
- 前端存储位置：wx.setStorageSync('token', token)
- 请求携带方式：Header Authorization: Bearer {token}

## 7. 鉴权拦截器

- 拦截所有 /api/** 请求
- 放行 /api/auth/** 接口（登录不需要 Token）
- 其他接口校验 Token 有效性，无效返回 401

## 8. 前端逻辑

### app.js onLaunch 流程

1. 检查本地缓存是否有 token
2. 如果有 → 验证是否过期（可选，MVP 阶段简单判断即可）
3. 如果没有或已过期 → 调用 wx.login() → 请求 /api/auth/login → 存储 token

### 请求封装（utils/request.js）

- 所有请求自动携带 Authorization Header
- 收到 401 响应 → 自动重新登录

## 9. 安全要求

- AppSecret 只存在后端环境变量中，不下发给前端
- session_key 不下发给前端
- JWT Secret 通过环境变量配置
- Token 过期后需重新登录获取

## 10. 验收标准

- [ ] 打开小程序，自动完成静默登录，控制台无报错
- [ ] 后端 t_user 表成功创建用户记录
- [ ] 再次打开小程序，使用缓存 Token，不重复登录
- [ ] Token 过期后，自动重新登录
- [ ] 未携带 Token 访问受保护接口，返回 401
- [ ] 携带有效 Token 访问受保护接口，正常返回数据
