# 足迹地图技术方案

对应 PRD：`docs/prds/prd-09-footprint-map.md`

## 1. 后端实现

### 1.1 新增 VO

```java
// FootprintVO.java
@Data
public class FootprintVO {
    private List<FootprintPhotoVO> photos;
    private FootprintSummary summary;
}

@Data
public class FootprintPhotoVO {
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String imageUrl;
    private String thumbnailUrl;
    private Double longitude;
    private Double latitude;
    private String locationName;
    private String photoDate;
    private String district;
}

@Data
public class FootprintSummary {
    private Integer totalPhotos;
    private Integer totalDistricts;
    private Integer totalCities;
    private List<CityGroup> cityGroups;
}

@Data
public class CityGroup {
    private String city;
    private Integer count;
    private Double latitude;   // 该城市照片的平均纬度
    private Double longitude;  // 该城市照片的平均经度
    private List<DistrictGroup> districts;
}

@Data
public class DistrictGroup {
    private String district;
    private Integer count;
    private Double latitude;
    private Double longitude;
}
```

### 1.2 新增 Mapper 查询

```sql
-- 查询用户所有照片坐标（带可见性过滤，限 500 条）
SELECT id, image_url, thumbnail_url, longitude, latitude,
       location_name, photo_date, district
FROM t_photo
WHERE deleted = 0 AND user_id = #{targetUserId}
  AND [可见性过滤条件]
ORDER BY photo_date DESC
LIMIT 500;

-- 按城市聚合统计
SELECT
    COALESCE(
        SUBSTRING_INDEX(location_name, '市', 1),
        district
    ) AS city_name,
    district,
    COUNT(*) AS photo_count,
    AVG(latitude) AS avg_lat,
    AVG(longitude) AS avg_lng
FROM t_photo
WHERE deleted = 0 AND user_id = #{targetUserId}
  AND [可见性过滤条件]
GROUP BY city_name, district;
```

> 注意：当前 `t_photo` 表没有 `city` 字段，只有 `district`（区县）。城市信息需要从 `location_name` 中提取，或者在聚合查询中通过 `district` 的上级行政区推断。
>
> **推荐方案**：在 `t_photo` 表新增 `city VARCHAR(50)` 字段，上传时由逆地理编码填充。存量数据通过迁移脚本从 `location_name` 中提取。

### 1.3 数据库迁移

```sql
-- 新增 city 字段
ALTER TABLE t_photo ADD COLUMN city VARCHAR(50) DEFAULT '' AFTER district;
CREATE INDEX idx_city ON t_photo(city);

-- 存量数据回填（从 location_name 中提取城市名）
-- 需要根据实际数据格式编写，示例：
-- UPDATE t_photo SET city = '杭州市' WHERE district IN ('西湖区', '上城区', '拱墅区', ...);
-- 或通过应用层脚本调用逆地理编码 API 批量回填
```

### 1.4 Controller 接口

```java
@GetMapping("/footprint")
public Result<FootprintVO> footprint(
        @RequestParam(value = "targetUserId", required = false) Long targetUserId,
        @RequestAttribute(value = "userId", required = false) Long userId) {
    Long queryUserId = targetUserId != null ? targetUserId : userId;
    ThrowUtils.throwIf(queryUserId == null, ErrorCode.PARAMS_ERROR, "用户ID不能为空");
    FootprintVO data = photoService.getFootprint(queryUserId, userId);
    return Result.success(data);
}
```

### 1.5 Service 层逻辑

```
getFootprint(targetUserId, viewerUserId):
  1. 判断可见性：
     - targetUserId == viewerUserId → 查全部
     - 互关 → 查 visibility IN (1, 2)
     - 其他 → 查 visibility = 2
  2. 查询照片列表（限 500 条）
  3. 在应用层按 city → district 聚合
     - 计算每个 city 的平均经纬度
     - 计算每个 district 的平均经纬度
  4. 组装 FootprintVO 返回
```

### 1.6 WebConfig 配置

将 `/api/photo/footprint` 加入 optional JWT 路径列表（允许未登录访问，但只能看公开照片）。

## 2. 前端实现

### 2.1 页面结构 `footprint.wxml`

```
┌── container ──────────────────────────┐
│                                       │
│  ┌── top-bar (fixed, z:200) ────────┐ │
│  │ [avatar] xxx的足迹               │ │
│  │ 24张照片 · 7个区域 · 3个城市      │ │
│  └──────────────────────────────────┘ │
│                                       │
│  ┌── map (全屏) ────────────────────┐ │
│  │                                  │ │
│  │   [城市气泡] / [区县气泡]         │ │
│  │   / [照片缩略图标记]              │ │
│  │                                  │ │
│  │                                  │ │
│  └──────────────────────────────────┘ │
│                                       │
│  ┌── bottom-panel (可上滑) ─────────┐ │
│  │  ─── 拖拽条                      │ │
│  │  📍 足迹覆盖 3个城市 7个区域      │ │
│  │  ┌ 展开后 ─────────────────────┐ │ │
│  │  │ 杭州市           14张  ▸    │ │ │
│  │  │   西湖区    8张             │ │ │
│  │  │   上城区    6张             │ │ │
│  │  │ 湖州市            6张  ▸   │ │ │
│  │  │   吴兴区    6张             │ │ │
│  │  └─────────────────────────────┘ │ │
│  └──────────────────────────────────┘ │
└───────────────────────────────────────┘
```

### 2.2 核心 JS 逻辑

```javascript
// footprint.js 核心流程

Page({
  data: {
    // 用户信息
    nickname: '', avatarUrl: '', isSelf: false,
    // 地图
    latitude: 35.86, longitude: 104.19, // 中国中心
    scale: 5, markers: [],
    // 数据
    photos: [], summary: {},
    cityGroups: [],
    // 面板
    panelExpanded: false,
    // 当前标记级别
    markerLevel: 'city', // 'city' | 'district' | 'photo'
    // 加载状态
    loading: true, empty: false
  },

  onLoad(options) {
    // 1. 解析参数
    // 2. 请求 footprint 数据
    // 3. 构建三级标记数据
    // 4. includePoints 自适应视角
  },

  onRegionChange(e) {
    if (e.type !== 'end') return;
    this.mapCtx.getScale({
      success: (res) => {
        const scale = res.scale;
        const newLevel =
          scale <= 8 ? 'city' :
          scale <= 12 ? 'district' : 'photo';
        if (newLevel !== this.data.markerLevel) {
          this._switchMarkerLevel(newLevel);
        }
        // photo 级别：过滤可视范围内的照片
        if (newLevel === 'photo') {
          this._filterVisiblePhotos();
        }
      }
    });
  },

  _switchMarkerLevel(level) {
    // 根据 level 切换 markers 数据
    // city → this._cityMarkers
    // district → this._districtMarkers
    // photo → this._photoMarkers (经过可视范围过滤)
  },

  _filterVisiblePhotos() {
    // 获取当前地图可视范围
    // 过滤 photos 数组中在范围内的照片
    // 按经纬度聚合（复用 map.js 的聚合逻辑）
    // 生成照片标记
  },

  onCityTap(e) {
    // 点击城市气泡 → 飞到该城市中心，scale=10
  },

  onDistrictTap(e) {
    // 点击区县气泡 → 飞到该区县中心，scale=14
  },

  onPhotoMarkerTap(e) {
    // 点击照片标记 → 跳转详情页
  },

  onPanelCityTap(e) {
    // 底部面板点击城市 → 飞到该城市
  },

  onPanelDistrictTap(e) {
    // 底部面板点击区县 → 飞到该区县
  }
});
```

### 2.3 气泡标记实现

微信小程序 map 的 `customCallout` 使用 `cover-view` 实现：

```xml
<!-- 城市气泡标记 -->
<cover-view slot="callout" marker-id="{{item.id}}">
  <cover-view class="city-bubble size-{{item.sizeClass}}">
    <cover-view class="bubble-count">{{item.count}}</cover-view>
    <cover-view class="bubble-name">{{item.name}}</cover-view>
  </cover-view>
</cover-view>
```

```css
/* 气泡样式 */
.city-bubble {
  display: flex; flex-direction: column;
  align-items: center; justify-content: center;
  background: rgba(255,255,255,0.92);
  border: 3rpx solid #07c160;
  border-radius: 50%;
  box-shadow: 0 4rpx 16rpx rgba(7,193,96,0.2);
}
.city-bubble.size-s { width: 80rpx; height: 80rpx; }
.city-bubble.size-m { width: 100rpx; height: 100rpx; }
.city-bubble.size-l { width: 120rpx; height: 120rpx; }
.city-bubble.size-xl { width: 140rpx; height: 140rpx; }
.bubble-count {
  font-size: 28rpx; font-weight: 700; color: #07c160;
}
.bubble-name {
  font-size: 18rpx; color: #666;
  max-width: 120rpx;
  overflow: hidden; text-overflow: ellipsis;
  white-space: nowrap;
}
```

### 2.4 底部面板实现

使用 `bindtouchstart` / `bindtouchmove` / `bindtouchend` 实现拖拽展开/收起：

- 收起高度：120rpx（摘要行 + 安全区）
- 展开高度：最大 60vh
- 拖拽阈值：移动 > 80rpx 触发切换
- 展开时地图仍可交互（面板不遮挡地图操作）

### 2.5 标记切换防抖

```javascript
// 避免频繁缩放时大量 setData
_switchMarkerLevel(level) {
  if (this._switchTimer) clearTimeout(this._switchTimer);
  this._switchTimer = setTimeout(() => {
    this.setData({ markerLevel: level });
    // ... 切换标记
  }, 200);
}
```

## 3. 入口改造

### 3.1 user.wxml / profile.wxml

在"足迹亮点"卡片的 `section-head` 中添加"查看全部"：

```xml
<view class="section-head">
  <text class="section-title">足迹亮点</text>
  <view class="section-more" bindtap="onFootprintTap">
    <text class="section-subtitle">查看全部</text>
    <text class="section-arrow">▸</text>
  </view>
</view>
```

### 3.2 跳转逻辑

```javascript
// user.js
onFootprintTap() {
  const user = this.data.user || {};
  wx.navigateTo({
    url: '/pages/footprint/footprint?userId=' + this._userId +
      '&nickname=' + encodeURIComponent(user.nickname || '') +
      '&avatarUrl=' + encodeURIComponent(user.avatarUrl || '')
  });
}

// profile.js
onFootprintTap() {
  const ui = this.data.userInfo || {};
  wx.navigateTo({
    url: '/pages/footprint/footprint' +
      '&nickname=' + encodeURIComponent(ui.nickname || '') +
      '&avatarUrl=' + encodeURIComponent(ui.avatarUrl || '')
  });
}
```

## 4. 数据流总览

```
用户点击"查看全部"
    │
    ▼
footprint 页面 onLoad
    │
    ├── GET /api/photo/footprint?targetUserId=xxx
    │       │
    │       ▼ 后端
    │   1. 判断可见性（自己/互关/陌生人）
    │   2. 查询照片列表（≤500条）
    │   3. 按 city → district 聚合
    │   4. 计算各级平均坐标
    │   5. 返回 FootprintVO
    │
    ▼ 前端
1. 构建 cityMarkers（城市气泡）
2. 构建 districtMarkers（区县气泡）
3. 缓存 photos 原始数据
4. includePoints → 自适应视角
5. 默认显示 cityMarkers
    │
    ▼ 用户交互
缩放/点击 → onRegionChange → 判断 scale
    │
    ├── scale ≤ 8 → setData({ markers: cityMarkers })
    ├── 8 < scale ≤ 12 → setData({ markers: districtMarkers })
    └── scale > 12 → 过滤可视范围 photos → 聚合 → setData({ markers: photoMarkers })
```

## 5. 边界情况处理

| 场景 | 处理方式 |
|------|---------|
| 用户无照片 | 显示空状态，不渲染地图标记 |
| 只有一个城市 | 初始 scale=10（城市级），直接显示区县气泡 |
| 只有一个区县 | 初始 scale=14（街道级），直接显示照片标记 |
| 照片无 district 字段 | 归入"未标注区域"分组 |
| 照片无 city 字段 | 从 location_name 提取，提取失败归入"其他" |
| 500 条限制 | 顶部提示"仅展示最近 500 张照片的足迹" |
| 网络请求失败 | 显示重试按钮 |
