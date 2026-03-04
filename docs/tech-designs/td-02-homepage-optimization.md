# 首页优化技术方案 - P0 阶段

对应 PRD：`docs/prds/prd-02-homepage-optimization.md` 优化点 1-5

## 涉及文件

### 前端
- `miniprogram/pages/map/map.js` — 主逻辑重写
- `miniprogram/pages/map/map.wxml` — 模板重写
- `miniprogram/pages/map/map.wxss` — 样式重写
- `miniprogram/utils/qqmap-wx-jssdk.min.js` — 腾讯地图 SDK（新增）
- `miniprogram/images/marker-temp.png` — 上传选点标记（已有）

### 后端
- `GET /api/photo/nearby` 增加 `startDate`/`endDate` 可选参数，支持时间筛选

## 优化点 1：时光筛选

### 方案
- 顶部左侧固定胶囊按钮，显示当前筛选状态（默认「全部时间」）
- 点击弹出底部半屏日期选择面板，使用 picker-view 滚轮选择起止日期
- 选择后请求 `/api/photo/nearby?startDate=xxx&endDate=xxx` 重新加载标记
- 后端 PhotoMapper.findNearby 增加日期过滤条件

## 优化点 2：一键定位

### 方案
- 右下角圆形定位按钮，使用 cover-view 悬浮在地图上
- 点击调用 `mapCtx.moveToLocation()` 回到当前位置
- 简化实现：MVP 阶段不做跟随模式，只做一键回到当前位置
- 无定位权限时按钮置灰，点击引导开启权限

## 优化点 3：逆地理编码

### 方案
- 引入腾讯地图 JS SDK（qqmap-wx-jssdk）
- 用户选点后调用 `qqmapsdk.reverseGeocoder()` 获取中文地址
- 地址显示在底部操作栏左侧，替代经纬度
- Key 已配置在 app.js globalData.mapKey

## 优化点 4：照片标记展示

### 方案
- MVP 阶段使用统一橙色标记图标（marker-photo.png），不做缩略图
- 同一位置多张照片：前端按经纬度聚合（精度 4 位小数），显示数量 callout
- 点击标记跳转照片详情页

## 优化点 5：上传选点标记

### 方案
- 使用 marker-temp.png 绿色标记，比历史标记大 20%
- callout 显示「上传位置」
- 选点后底部操作栏显示中文地址（复用优化点 3）
- MVP 阶段不做拖动功能（微信小程序 map marker 的 draggable 兼容性一般）
