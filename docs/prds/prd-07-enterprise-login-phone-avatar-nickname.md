# PRD-07：企业认证登录 — 手机号 / 头像 / 昵称

## 一、背景与目标

### 1.1 背景
- 小程序已完成**企业认证**，具备使用「获取手机号」「头像昵称填写」等能力的资格。
- 当前登录依赖已废弃的 `wx.getUserProfile` 获取昵称/头像，需迁移到官方推荐方案。
- 企业级场景需要可联系到用户的**手机号**，以及稳定的**头像**、**昵称**展示。

### 1.2 目标
- **手机号**：通过微信「获取手机号」能力，在用户授权后由后端解密/拉取并落库，供业务与合规使用。
- **头像**：通过「头像昵称填写」中的头像选择，用户选图后上传至自有存储（COS），得到永久 URL。
- **昵称**：通过「昵称填写」能力（如 `input type="nickname"`）获取用户输入或选择的微信昵称并落库。
- 登录流程与现有 JWT、用户体系兼容，可直接替换当前小程序登录逻辑并用于全链路展示。

### 1.3 用户价值
- 用户一次授权即可完成手机号 + 头像 + 昵称的获取与展示。
- 平台具备真实手机号，便于客服、风控与合规。
- 头像与昵称符合微信规范，体验统一。

---

## 二、范围与约束

### 2.1 在范围内
- 小程序端：登录/完善资料页的交互与接口调用（手机号按钮、头像选择、昵称输入）。
- 服务端：登录/绑定手机号接口、更新头像/昵称接口、User 表扩展与存储。
- 现有使用 `userInfo.nickname` / `userInfo.avatarUrl` 的页面无需改业务逻辑，仅数据来源改为新接口。

### 2.2 不在范围内
- 管理后台对手机号的展示与脱敏策略（可后续单独 PRD）。
- 手机号用于短信/营销等二次使用（仅在本 PRD 中完成「获取并存储」）。

### 2.3 约束与依赖
- **企业认证**：获取手机号能力仅企业认证小程序可用，已满足。
- 微信接口：`getPhoneNumber` 返回的 `code` 仅一次有效、约 5 分钟有效；需后端调用 `getuserphonenumber`。
- 头像：选择后为临时路径，必须上传至 COS（或自有存储）后存 URL。
- 昵称：需过微信内容安全；`input type="nickname"` 在部分版本会提供微信昵称快捷填充。

---

## 三、角色与流程

### 3.1 角色
- **未登录用户**：进入小程序后需先完成「微信登录 + 手机号授权 + 头像/昵称完善」才能使用需登录能力。
- **已登录用户**：若此前未留手机号/头像/昵称，在「我的」等入口引导补充。

### 3.2 主流程（推荐：两段式）

**阶段一：静默登录（仅 openid）**
1. 用户打开小程序。
2. 前端 `wx.login` 取 `code`，调用后端 `POST /api/auth/login`（仅传 `code`）。
3. 后端 `code2session` 得 `openid`，查/建用户，签发 JWT，返回 `token`、`userId`、`needPhone`（是否已绑手机）、`needProfile`（是否需完善头像/昵称）。
4. 前端存 `token` 与基础 `userInfo`，视为「已登录但可能未完善资料」。

**阶段二：授权手机号 + 完善头像昵称**
5. 若 `needPhone` 或 `needProfile` 为 true，进入「完善资料」页（或弹窗/引导）。
6. **手机号**：用户点击「授权手机号」按钮（`open-type="getPhoneNumber"`），前端拿到 `code`，调 `POST /api/auth/bind-phone`，后端用 access_token + code 调微信 `getuserphonenumber`，落库并返回。
7. **头像**：用户点击头像区域触发 `chooseAvatar`，选图后得到临时路径，调用现有或新上传接口上传至 COS，后端返回永久 URL，再调 `PATCH /api/user/profile` 更新 `avatarUrl`。
8. **昵称**：用户在 `input type="nickname"` 中填写或选择微信昵称，提交时随表单调 `PATCH /api/user/profile` 更新 `nickname`。
9. 全部完成后，`needPhone`、`needProfile` 为 false，可正常使用所有功能。

### 3.3 可选流程（一步到位）
- 也可在「登录页」同时提供：微信登录 + 获取手机号按钮 + 头像选择 + 昵称输入，一次提交完成登录与资料完善（实现复杂度略高，需处理手机号授权失败时的降级）。

---

## 四、功能需求详述

### 4.1 获取手机号（企业能力）

| 项 | 说明 |
|----|------|
| 前端 | 使用 `<button open-type="getPhoneNumber" bindgetphonenumber="onGetPhoneNumber">`，回调中取 `detail.code`（若用户拒绝则无 code）。 |
| 后端 | 新增接口 `POST /api/auth/bind-phone`，入参：`code`（必填）。需先有有效 JWT（即已通过 code2session 登录）。后端用 appid+secret 取 access_token，再调 `POST https://api.weixin.qq.com/wxa/business/getuserphonenumber?access_token=ACCESS_TOKEN`，body `{"code":"xxx"}`，返回体中含 `phone_info.phoneNumber`、`purePhoneNumber`、`countryCode`。将 `purePhoneNumber` 写入当前用户的 `phone` 字段。 |
| 安全 | code 一次一用、短时有效；接口需校验 JWT 且仅能绑定当前用户。 |

### 4.2 头像

| 项 | 说明 |
|----|------|
| 前端 | 使用 `<button open-type="chooseAvatar" bindchooseavatar="onChooseAvatar">`，在 `onChooseAvatar` 中取 `detail.avatarUrl`（临时路径），调用上传接口得到永久 URL，再调 `PATCH /api/user/profile` 更新 `avatarUrl`。 |
| 后端 | 复用现有 COS 上传能力；若无「仅上传头像」的接口，可新增 `POST /api/user/avatar`（multipart 或 base64），上传后写回 `user.avatarUrl` 并返回 URL。 |

### 4.3 昵称

| 项 | 说明 |
|----|------|
| 前端 | 使用 `<input type="nickname" ... />` 或配合 form 的昵称填写能力，在提交时读取 value，随 `PATCH /api/user/profile` 传 `nickname`。 |
| 后端 | 现有 `UpdateProfileRequest` 已含 `nickname`，确保校验长度与敏感词（可选），更新 `user.nickname`。 |

### 4.4 登录接口调整

| 项 | 说明 |
|----|------|
| 请求 | `POST /api/auth/login` 请求体：至少包含 `code`（必填）；可选保留 `nickname`、`avatarUrl` 等用于首次从「完善资料」页一并提交时的兼容。 |
| 响应 | 除 `token`、`userId` 外，增加 `needPhone`（boolean）、`needProfile`（boolean）。规则：`needPhone = (user.phone 为空)`，`needProfile = (user.nickname 为空或 user.avatarUrl 为空)`（可按产品细化）。 |

---

## 五、接口规格（概要）

- `POST /api/auth/login`  
  - 请求：`{ "code": "wx_login_code" }`（可保留可选 nickname/avatarUrl）。  
  - 响应：`{ "token", "userId", "needPhone", "needProfile" }`。

- `POST /api/auth/bind-phone`  
  - 请求：`{ "code": "getPhoneNumber_code" }`。  
  - 需 Header：`Authorization: Bearer <token>`。  
  - 响应：`{ "phoneNumber" }` 或仅 200 表示成功。

- `PATCH /api/user/profile`  
  - 已有；请求体支持 `nickname`、`avatarUrl`，确保可单独更新其一。

- `POST /api/user/avatar`（可选）  
  - 上传头像文件，返回 `{ "avatarUrl" }`，并更新 user 表。

---

## 六、数据与存储

- **t_user 表** 增加字段（示例）：  
  - `phone` VARCHAR(20) DEFAULT NULL COMMENT '手机号（纯数字）'  
  - 可选：`country_code` VARCHAR(10) DEFAULT NULL  
  - 建议对 `phone` 建唯一索引（一个手机号只绑一个账号），或按业务允许同一手机多账号。
- 头像、昵称沿用现有 `avatar_url`、`nickname` 字段即可。

---

## 七、前端页面与组件

- **登录/完善资料页**（可与当前 profile 未登录态合并或独立一页）：  
  - 第一步：仅「微信登录」按钮，调 `login` 拿 token。  
  - 第二步：若 `needPhone` 或 `needProfile` 为 true，展示：  
    - 「授权手机号」按钮（getPhoneNumber）；  
    - 头像选择（chooseAvatar）+ 上传；  
    - 昵称输入（input type="nickname"）；  
    - 「完成」提交更新 profile / bind-phone。
- 所有使用 `userInfo.nickname` / `userInfo.avatarUrl` 的页面（如个人中心、评论、私信、社区）无需改字段名，仅保证登录后从 `/user/info` 或登录响应中能拿到最新 nickname/avatarUrl 即可。

---

## 八、合规与提示

- 在获取手机号前，需明确告知用户用途（如「用于账号安全与客服联系」），并仅在用户点击授权按钮后获取。
- 头像、昵称需遵守微信内容安全与平台规范；昵称建议做长度与格式校验。

---

## 九、验收标准

1. 企业认证小程序在真机可完成：静默登录 → 授权手机号 → 选择头像并上传 → 填写昵称，且后端正确落库。
2. 已登录用户在各处展示的昵称、头像与「我的」页一致，且来源于新能力（非 getUserProfile）。
3. `POST /api/auth/bind-phone` 仅能绑定当前 JWT 对应用户，且手机号写入 `t_user.phone`。
4. 开发/体验版手机号可为微信测试号返回的测试数据；正式版为真实手机号。

---

## 十、参考

- [获取手机号 | 微信开放文档](https://developers.weixin.qq.com/miniprogram/dev/server/API/user-info/phone-number/api_getphonenumber)
- [头像昵称填写 | 微信开放文档](https://developers.weixin.qq.com/miniprogram/dev/framework/open-ability/userProfile.html)
