# PRD-01.1：用户资料完善

## 1. 需求背景

PRD-01 实现了静默登录，但只有 openid，没有用户的昵称、头像等信息。为了后续做个人页面、显示图片上传者信息，需要获取用户的基本资料。

## 2. 功能描述

用户可以主动授权获取微信头像、昵称等信息，完善个人资料。授权后，这些信息会显示在个人页面和图片详情页。

## 3. 用户流程

```
用户打开小程序 → 静默登录（已有）
    ↓
用户浏览地图、上传图片（资料未完善也能用）
    ↓
用户点击"个人中心"或"完善资料"按钮
    ↓
弹出授权弹窗："时光地图申请获取你的昵称、头像等信息"
    ↓
用户点击"允许" → 获取成功 → 保存到后端
用户点击"拒绝" → 提示"授权后可获得更好体验"
    ↓
授权成功后，个人页面显示头像昵称
```

## 4. 页面设计

### 4.1 个人中心页（新增 pages/profile）

未授权状态：
- 默认头像（灰色圆形）
- 昵称显示"微信用户"
- "完善资料"按钮（绿色，醒目）

已授权状态：
- 显示真实头像
- 显示真实昵称
- 显示性别、地区（可选）
- "我的上传"列表

### 4.2 地图主页（修改 pages/map）

右上角增加"个人中心"入口（头像图标）

## 5. 接口设计

### POST /api/user/profile

更新用户资料

请求参数：
```json
{
  "nickname": "张三",
  "avatarUrl": "https://...",
  "gender": 1,
  "country": "中国",
  "province": "广东省",
  "city": "深圳市"
}
```

成功响应：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "userId": 123,
    "nickname": "张三",
    "avatarUrl": "https://...",
    "profileCompleted": true
  }
}
```

### GET /api/user/info

获取当前用户完整信息

成功响应：
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "userId": 123,
    "nickname": "张三",
    "avatarUrl": "https://...",
    "gender": 1,
    "country": "中国",
    "province": "广东省",
    "city": "深圳市",
    "profileCompleted": true,
    "createTime": "2026-03-03 15:51:57"
  }
}
```

## 6. 数据模型

### t_user 表新增字段

```sql
ALTER TABLE t_user ADD COLUMN gender TINYINT DEFAULT 0 COMMENT '性别 0-未知 1-男 2-女';
ALTER TABLE t_user ADD COLUMN country VARCHAR(50) DEFAULT '' COMMENT '国家';
ALTER TABLE t_user ADD COLUMN province VARCHAR(50) DEFAULT '' COMMENT '省份';
ALTER TABLE t_user ADD COLUMN city VARCHAR(50) DEFAULT '' COMMENT '城市';
ALTER TABLE t_user ADD COLUMN profile_completed TINYINT DEFAULT 0 COMMENT '资料是否完善 0-否 1-是';
```

## 7. 前端实现要点

### wx.getUserProfile 调用规则

- 必须由用户真实点击按钮触发，不能自动调用
- 每次调用都会弹窗，不能静默获取
- 用户拒绝后，下次点击还会再弹窗

### 按钮写法

```xml
<button bindtap="getUserProfile">完善资料</button>
```

注意：不能用 `open-type="getUserProfile"`，这个已废弃。

## 8. 验收标准

- [ ] 个人中心页面正常显示
- [ ] 点击"完善资料"弹出微信授权弹窗
- [ ] 授权成功后，后端保存用户资料
- [ ] 个人中心显示真实头像昵称
- [ ] 图片详情页显示上传者头像昵称
- [ ] 拒绝授权后，仍可正常使用其他功能
- [ ] 再次点击"完善资料"可以重新授权
