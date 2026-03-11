# 地图时迹 - 技术架构

## 整体架构

```
用户手机
  │
  ▼
微信小程序（原生 + WeUI 2.x）
  │
  ▼ HTTPS
Nginx（反向代理 + HTTPS 终止）
  │
  ▼
Spring Boot 后端（REST API）
  │
  ├──▶ MySQL 8.0（业务数据）
  ├──▶ 腾讯云 COS（图片存储 + CDN）
  └──▶ Redis 7.x（缓存，后续按需引入）
```

## 后端技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| Java | 17 (LTS) | 长期支持到 2029，Spring Boot 3.x 最低要求 |
| Spring Boot | 3.4.x | 当前最新稳定版，基于 Spring Framework 6 |
| Maven | 3.9.x | 项目构建工具 |
| MySQL | 8.0 | 业务数据库，存储用户、图片元信息、地点等 |
| MyBatis-Plus | 3.5.x | ORM 框架，比原生 MyBatis 省代码，比 JPA 灵活 |
| Redis | 7.x | 缓存热点数据，MVP 阶段可选，后续按需引入 |
| 腾讯云 COS SDK | 5.6.x | 图片对象存储 |

## 小程序技术栈

| 技术 | 版本 | 说明 |
|------|------|------|
| 微信小程序基础库 | 3.x | 最新基础库 |
| WeUI | 2.x | 微信官方 UI 组件库 |

## 基础设施

| 技术 | 版本/规格 | 说明 |
|------|-----------|------|
| Nginx | 1.24+ | 反向代理，HTTPS 终止 |
| Docker | 24+ | 容器化部署 |
| 腾讯云 COS | - | 图片存储，自带 CDN 加速 |
| 腾讯云服务器 | - | 部署 Spring Boot + MySQL + Nginx |

## 核心设计决策

### 用户认证

微信登录（wx.login 获取 openid）+ JWT Token

- 小程序调用 wx.login 获取 code
- 后端用 code 换取 openid
- 后端签发 JWT Token，后续请求携带 Token 鉴权

### 图片上传流程

后端签名 + 小程序直传 COS

```
小程序 ──请求上传签名──▶ Spring Boot 后端
                            │
                        生成临时签名
                            │
小程序 ◀──返回签名 URL──── 后端
  │
  ├──直传图片──▶ 腾讯云 COS
  │
  └──上传完成，提交元信息──▶ 后端 ──▶ MySQL
```

优势：不经过后端中转，节省带宽，上传速度快

### ORM 选型

MyBatis-Plus 3.5.x

- 内置 CRUD 封装，减少样板代码
- 支持分页插件、逻辑删除、自动填充
- 比 JPA 更灵活，SQL 可控性强
