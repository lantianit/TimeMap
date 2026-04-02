const { request } = require('../../utils/request');
const app = getApp();

function buildThumbUrl(imageUrl) {
  if (!imageUrl || !imageUrl.includes('.cos.')) return imageUrl || '';
  const sep = imageUrl.includes('?') ? '&' : '?';
  return imageUrl + sep + 'imageMogr2/thumbnail/216x200';
}

function sizeClass(count) {
  if (count >= 100) return 'size-xl';
  if (count >= 21) return 'size-l';
  if (count >= 6) return 'size-m';
  return 'size-s';
}

Page({
  data: {
    nickname: '', avatarUrl: '', isSelf: false,
    latitude: 35.86, longitude: 104.19,
    scale: 4, markers: [],
    summary: {},
    cityBubbles: [], photoCallouts: [],
    markerLevel: 'city', // 'city' | 'photo'
    showResetBtn: false,
    infoBarText: '',
    loading: true, empty: false, showLimitTip: false
  },

  onLoad(options) {
    this.mapCtx = wx.createMapContext('footprint-map');
    this._photos = [];
    this._cityMarkers = [];
    this._photoIdMap = {};
    this._lastLevel = 'city';
    this._debounceTimer = null;
    this._flying = false; // 防止飞行动画期间触发 onRegionChange

    const myId = app.globalData.userInfo && app.globalData.userInfo.userId;
    const userId = options.userId || '';
    const isSelf = !userId || (myId && String(myId) === String(userId));
    const nickname = options.nickname ? decodeURIComponent(options.nickname) : '';
    const avatarUrl = options.avatarUrl ? decodeURIComponent(options.avatarUrl) : '';

    this.setData({
      isSelf,
      nickname: isSelf ? '' : nickname,
      avatarUrl: isSelf ? (app.globalData.userInfo && app.globalData.userInfo.avatarUrl || '') : avatarUrl
    });

    wx.setNavigationBarTitle({
      title: isSelf ? '我的足迹' : (nickname ? nickname + ' 的足迹' : '足迹地图')
    });

    this._targetUserId = userId;
    this._loadFootprint();
  },

  _loadFootprint() {
    const params = {};
    if (this._targetUserId) params.targetUserId = this._targetUserId;

    request('/photo/footprint', 'GET', params)
      .then(res => {
        const data = res.data || {};
        const photos = data.photos || [];
        const summary = data.summary || {};

        this._photos = photos;
        this.setData({
          summary,
          loading: false,
          empty: photos.length === 0,
          showLimitTip: photos.length >= 500
        });

        if (photos.length === 0) return;

        this._buildCityMarkers(summary.cityGroups || []);
        // 初始展示：中国全景 + 城市气泡
        this.setData({
          markerLevel: 'city',
          markers: this._cityMarkers,
          cityBubbles: this._cityBubbles,
          infoBarText: '📍 ' + (summary.totalPhotos || 0) + '张照片 · ' + (summary.totalCities || 0) + '个城市 · ' + (summary.totalDistricts || 0) + '个区域'
        });
      })
      .catch(() => {
        this.setData({ loading: false, empty: true });
      });
  },

  _buildCityMarkers(cityGroups) {
    const markers = [];
    const bubbles = [];
    cityGroups.forEach((cg, i) => {
      const id = 1000 + i;
      markers.push({
        id, latitude: cg.latitude, longitude: cg.longitude,
        iconPath: '/images/marker-transparent.png', width: 1, height: 1,
        customCallout: { anchorY: 0, anchorX: 0, display: 'ALWAYS' }
      });
      bubbles.push({
        id, name: cg.city.replace('市', ''), count: cg.count,
        sizeClass: sizeClass(cg.count),
        lat: cg.latitude, lng: cg.longitude,
        cityIndex: i
      });
    });
    this._cityMarkers = markers;
    this._cityBubbles = bubbles;
  },

  /** 飞到指定城市/区县并展示照片 */
  _flyToAndShowPhotos(lat, lng, filterPhotos) {
    this._flying = true;
    // 先用 includePoints 自适应范围
    const points = filterPhotos.map(p => ({ latitude: p.latitude, longitude: p.longitude }));
    // 切换到照片模式
    this._lastLevel = 'photo';
    this.setData({
      markerLevel: 'photo',
      cityBubbles: [],
      showResetBtn: true
    });

    if (points.length === 1) {
      // 只有一个点，直接移动过去
      this.setData({
        latitude: points[0].latitude,
        longitude: points[0].longitude,
        scale: 15
      });
      setTimeout(() => {
        this._buildPhotoMarkersFromList(filterPhotos);
        this._flying = false;
      }, 400);
    } else {
      // 多个点，用 includePoints
      this.mapCtx.includePoints({
        points,
        padding: [100, 60, 180, 60],
        success: () => {
          setTimeout(() => {
            this._buildPhotoMarkersFromList(filterPhotos);
            this._flying = false;
          }, 400);
        },
        fail: () => {
          this._buildPhotoMarkersFromList(filterPhotos);
          this._flying = false;
        }
      });
    }
  },

  /** 从指定照片列表构建标记（不依赖 getRegion） */
  _buildPhotoMarkersFromList(photos) {
    const groups = {};
    photos.forEach(p => {
      const key = p.latitude.toFixed(4) + ',' + p.longitude.toFixed(4);
      if (!groups[key]) groups[key] = [];
      groups[key].push(p);
    });

    const markers = [];
    const callouts = [];
    const photoIdMap = {};
    let id = 3000;

    Object.keys(groups).forEach(key => {
      const group = groups[key];
      const first = group[0];
      const mid = id++;
      markers.push({
        id: mid, latitude: first.latitude, longitude: first.longitude,
        iconPath: '/images/marker-transparent.png', width: 1, height: 1,
        customCallout: { anchorY: 0, anchorX: 0, display: 'ALWAYS' }
      });
      callouts.push({
        id: mid,
        thumbUrl: buildThumbUrl(first.thumbnailUrl || first.imageUrl),
        count: group.length
      });
      photoIdMap[mid] = group.map(p => p.id);
    });

    this._photoIdMap = photoIdMap;
    this.setData({ markers, photoCallouts: callouts });
  },

  /** 根据可视范围刷新照片标记 */
  _refreshPhotoMarkers() {
    this.mapCtx.getRegion({
      success: (res) => {
        const sw = res.southwest || {};
        const ne = res.northeast || {};
        const visible = this._photos.filter(p =>
          p.latitude >= sw.latitude && p.latitude <= ne.latitude &&
          p.longitude >= sw.longitude && p.longitude <= ne.longitude
        );
        this._buildPhotoMarkersFromList(visible.length > 0 ? visible : this._photos);
      },
      fail: () => {
        this._buildPhotoMarkersFromList(this._photos);
      }
    });
  },

  // ========== 地图事件 ==========

  onRegionChange(e) {
    if (e.type !== 'end' || this._flying) return;
    if (this._debounceTimer) clearTimeout(this._debounceTimer);
    this._debounceTimer = setTimeout(() => {
      this.mapCtx.getScale({
        success: (res) => {
          const scale = res.scale;
          const newLevel = scale <= 9 ? 'city' : 'photo';

          if (newLevel === 'city' && this._lastLevel !== 'city') {
            // 缩小到全国级别，切回城市气泡
            this._lastLevel = 'city';
            const s = this.data.summary;
            this.setData({
              markerLevel: 'city',
              markers: this._cityMarkers,
              cityBubbles: this._cityBubbles || [],
              photoCallouts: [],
              showResetBtn: false,
              infoBarText: '📍 ' + (s.totalPhotos || 0) + '张照片 · ' + (s.totalCities || 0) + '个城市 · ' + (s.totalDistricts || 0) + '个区域'
            });
          } else if (newLevel === 'photo') {
            this._lastLevel = 'photo';
            // 照片级别，刷新可视范围内的照片
            this._refreshPhotoMarkers();
          }
        }
      });
    }, 300);
  },

  onMarkerTap(e) {
    const id = e.markerId;
    const level = this.data.markerLevel;

    if (level === 'city') {
      const bubble = (this._cityBubbles || []).find(b => b.id === id);
      if (bubble) {
        this._flyToCity(bubble.cityIndex);
      }
    } else {
      // 照片标记，跳转详情
      const photoIds = this._photoIdMap[id];
      if (photoIds && photoIds.length) {
        wx.navigateTo({ url: '/pages/detail/detail?ids=' + photoIds.join(',') });
      }
    }
  },

  /** 飞到指定城市 */
  _flyToCity(cityIndex) {
    const cg = (this.data.summary.cityGroups || [])[cityIndex];
    if (!cg) return;
    const distCount = (cg.districts || []).length;
    this.setData({
      infoBarText: '📍 ' + cg.city + ' · ' + cg.count + '张照片' + (distCount > 1 ? ' · ' + distCount + '个区域' : '')
    });
    // 筛选该城市下的所有照片
    const cityPhotos = this._photos.filter(p => {
      const pCity = p.city || '';
      return pCity === cg.city;
    });
    const photos = cityPhotos.length > 0 ? cityPhotos : this._photos;
    this._flyToAndShowPhotos(cg.latitude, cg.longitude, photos);
  },

  /** 飞到指定区县 */
  _flyToDistrict(cityIndex, distIndex) {
    const cg = (this.data.summary.cityGroups || [])[cityIndex];
    if (!cg) return;
    const dg = (cg.districts || [])[distIndex];
    if (!dg) return;
    this.setData({
      infoBarText: '📍 ' + dg.district + ' · ' + dg.count + '张照片'
    });
    // 筛选该区县下的所有照片
    const distPhotos = this._photos.filter(p => {
      const pDist = p.district || '';
      return pDist === dg.district;
    });
    const photos = distPhotos.length > 0 ? distPhotos : this._photos;
    this._flyToAndShowPhotos(dg.latitude, dg.longitude, photos);
  },

  // ========== 导航 ==========

  /** 重置到全国视图 */
  onResetView() {
    this._flying = true;
    this._lastLevel = 'city';
    const s = this.data.summary;
    this.setData({
      latitude: 35.86,
      longitude: 104.19,
      scale: 4,
      markerLevel: 'city',
      markers: this._cityMarkers,
      cityBubbles: this._cityBubbles || [],
      photoCallouts: [],
      showResetBtn: false,
      infoBarText: '📍 ' + (s.totalPhotos || 0) + '张照片 · ' + (s.totalCities || 0) + '个城市 · ' + (s.totalDistricts || 0) + '个区域'
    });
    setTimeout(() => { this._flying = false; }, 500);
  },

  onBack() {
    wx.navigateBack({ fail: () => wx.switchTab({ url: '/pages/map/map' }) });
  }
});
