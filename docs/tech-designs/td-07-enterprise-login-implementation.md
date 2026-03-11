# TD-07：企业认证登录 — 实现难度与实现细节

## 一、实现难度评估

| 模块 | 难度 | 说明 |
|------|------|------|
| **手机号获取** | ⭐⭐ 中 | 前端一个按钮 + 一个接口；后端需调微信 `getuserphonenumber`（需 access_token），你已有 code2session，补一个取 access_token 的调用即可。 |
| **头像** | ⭐ 低 | 前端 `chooseAvatar` → 临时路径 → 用现有 COS 上传接口上传 → 把返回 URL 写入 profile。你已有 CosService，只需确认上传接口对「头像」开放或新增一个专用接口。 |
| **昵称** | ⭐ 低 | 前端 `input type="nickname"` 或昵称填写组件，提交时带 nickname 调现有 `PATCH /api/user/profile`。后端已有 UpdateProfileRequest，基本不用改。 |
| **登录流程改造** | ⭐⭐ 中 | 把当前「getUserProfile + 一次 login」改成「先静默 login(code)，再按需 bindPhone + 完善头像昵称」；前端需处理 needPhone/needProfile 引导与状态。 |

**整体难度：中等**。主要工作量在：① 后端新增 access_token 与 getuserphonenumber 调用；② 前端登录与完善资料流程改造；③ User 表与 DTO 增加 phone 字段。预计 1～2 天可完成联调与基础测试。

---

## 二、实现细节（可直接落地）

### 2.1 后端

#### 2.1.1 微信接口
- **access_token**：  
  `GET https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid=APPID&secret=SECRET`  
  返回 `access_token`，建议缓存（约 2 小时有效），避免每次 bind-phone 都请求。
- **getuserphonenumber**：  
  `POST https://api.weixin.qq.com/wxa/business/getuserphonenumber?access_token=ACCESS_TOKEN`  
  Body: `{"code": "用户 getPhoneNumber 返回的 code"}`  
  返回示例：`{"errcode":0,"errmsg":"ok","phone_info":{"phoneNumber":"xx","purePhoneNumber":"xx","countryCode":"86"}}`  
  使用 `phone_info.purePhoneNumber` 落库即可。

#### 2.1.2 数据与实体
- **DB**：在 `t_user` 增加 `phone VARCHAR(20) DEFAULT NULL`，可选 `country_code VARCHAR(10)`；若业务要求一机一号可加 `UNIQUE(phone)`。
- **Entity**：`User` 增加 `phone`（及可选 `countryCode`）。
- **DTO**：  
  - `LoginResponse` 增加 `needPhone`、`needProfile`。  
  - 新增 `BindPhoneRequest { code }`、`BindPhoneResponse { phoneNumber }`（或仅 200）。

#### 2.1.3 接口
- **POST /api/auth/login**  
  - 入参：仅必填 `code`；可选保留 `nickname`、`avatarUrl` 等以便一次完善资料时兼容。  
  - 逻辑：code2session → 查/建 User → 计算 needPhone（phone 为空）、needProfile（nickname 或 avatarUrl 为空）→ 签发 JWT → 返回 token、userId、needPhone、needProfile。
- **POST /api/auth/bind-phone**  
  - 需 JWT。从 token 取 userId，用 body 的 `code` + 缓存的 access_token 调 getuserphonenumber，将 purePhoneNumber 写入 `user.phone`，返回脱敏手机号或仅成功状态。
- **PATCH /api/user/profile**  
  - 已有；确保支持只更新 `nickname` 或只更新 `avatarUrl`。
- **POST /api/user/avatar**（可选）  
  - 接收文件上传，走 CosService 上传，把返回 URL 写入当前 user.avatarUrl 并返回。

#### 2.1.4 配置与工具类
- 在 `WxApiUtil` 或新建 `WxAccessTokenCache`：  
  - 方法 `getAccessToken()`：若缓存未过期则返回缓存，否则请求 token 接口并缓存（建议 7000 秒过期，早于 2 小时）。
- 在 `WxApiUtil` 新增 `getUserPhoneNumber(String code)`：先 getAccessToken()，再 POST getuserphonenumber，解析返回并返回 phoneNumber / purePhoneNumber。

---

### 2.2 小程序端

#### 2.2.1 登录流程（两段式）
1. **静默登录**  
   - 点击「微信登录」或进入需登录页时：`wx.login` → 只传 `code` 调 `POST /api/auth/login`。  
   - 存 token、userId、以及服务端返回的 needPhone、needProfile。
2. **完善资料页**（当 needPhone 或 needProfile 为 true 时展示）  
   - **手机号**：`<button open-type="getPhoneNumber" bindgetphonenumber="onGetPhoneNumber">授权手机号</button>`  
     - 在 `onGetPhoneNumber` 中取 `e.detail.code`，有 code 则调 `POST /api/auth/bind-phone`，成功后置 needPhone = false。  
   - **头像**：`<button open-type="chooseAvatar" bindchooseavatar="onChooseAvatar">`，在回调里用 `e.detail.avatarUrl` 调用上传接口，再 PATCH profile 写 avatarUrl。  
   - **昵称**：`<input type="nickname" bindinput="onNicknameInput" />`，提交时带 nickname 调 PATCH profile。  
   - 三项都完成后，可关闭完善资料页或跳回「我的」。

#### 2.2.2 与 app.js 的配合
- `app.login()` 改为可选：仅传 `code` 的静默登录；或保留 `app.login({ code })`，不再依赖 getUserProfile。
- 登录成功后，将服务端返回的 `needPhone`、`needProfile` 存到 globalData 或 storage，供完善资料页与路由判断使用。
- 完善资料后再次调用 `GET /api/user/info` 或登录接口返回的最新 user 信息，同步到 globalData.userInfo（nickname、avatarUrl、phone 脱敏等），这样评论、私信、社区等已有展示逻辑无需改字段名。

#### 2.2.3 移除 getUserProfile
- 删除 `wx.getUserProfile` 相关调用（如 profile 页的 onLogin 内），改为上述「静默 login + 完善资料页」流程。

---

### 2.3 安全与边界

- **bind-phone**：必须校验 JWT，且只更新当前 userId 对应用户的 phone。
- **access_token**：仅在后端使用，不要下发给前端。
- **手机号**：建议仅存纯数字；展示时脱敏（如 138****8000）。
- **昵称**：可做长度限制与敏感词校验（可选），与微信内容安全不冲突。

---

## 三、与 PRD 的对应关系

- 完整产品需求、流程与验收标准见 **docs/prds/prd-07-enterprise-login-phone-avatar-nickname.md**。
- 本文档为技术实现难度评估与可执行的实现细节，开发时两篇对照即可。
