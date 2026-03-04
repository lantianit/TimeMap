const { request, uploadFile, checkLogin } = require('../../utils/request');
const QQMapWX = require('../../utils/qqmap-wx-jssdk.min.js');
const app = getApp();

const TEMP_MARKER_ID = 99999;

Page({
  data: {
    latitude: 30.5554,
    longitude: 114.3162,
    markers: [],
    scale: 14,
    // 时光筛选（优化点1）
    filterLabel: '全部时间',
    showFilterPanel: false,
    filterStartDate: '',
    filterEndDate: '',
    // 定位状态（优化点2）
    hasLocation: false,
    // 底部操作栏
    showActionBar: false,
    tapLat: 0,
    tapLng: 0,
    tapLocationName: '',
    uploadDate: '',
    uploadDateDisplay: '今天',
    // 上传状态
    uploading: false,
    uploadToast: '',
    showToast: false
  },

  onLoad() {
    this.mapCtx = wx.createMapContext('map');
    this._loading = false;
    this._loadTimer = null;
    this._historyMarkers = [];
    this._photoIdMap = {};
    this._qqmapsdk = new QQMapWX({ key: app.globalData.mapKey });

    const today = this._formatDate(new Date());
    this.setData({ uploadDate: today, uploadDateDisplay: '今天' });
    this.getCurrentLocation();
  },

  onShow() {
    this.debouncedLoadPhotos();
  },

  // ========== 定位（优化点2） ==========

  getCurrentLocation() {
    wx.getLocation({
      type: 'gcj02',
      success: (res) => {
        this.setData({
          latitude: res.latitude,
          longitude: res.longitude,
          hasLocation: true
        });
        this._currentLat = res.latitude;
        this._currentLng = res.longitude;
        this._userLat = res.latitude;
        this._userLng = res.longitude;
        this.debouncedLoadPhotos();
      },
      fail: () => {
        this._showToast('未获取定位，可手动点击地图选点');
      }
    });
  },

  /** 一键回到当前位置 */
  onLocateTap() {
    if (!this.data.hasLocation) {
      this.getCurrentLocation();
      return;
    }
    this.mapCtx.moveToLocation({
      latitude: this._userLat,
      longitude: this._userLng
    });
  },

  // ========== 时光筛选（优化点1） ==========

  onFilterTap() {
    this.setData({ showFilterPanel: !this.data.showFilterPanel });
  },

  onFilterStartChange(e) {
    this.setData({ filterStartDate: e.detail.value });
  },

  onFilterEndChange(e) {
    this.setData({ filterEndDate: e.detail.value });
  },

  onFilterConfirm() {
    const { filterStartDate, filterEndDate } = this.data;
    let label = '全部时间';
    if (filterStartDate && filterEndDate) {
      label = filterStartDate + ' ~ ' + filterEndDate;
    } else if (filterStartDate) {
      label = filterStartDate + ' 起';
    } else if (filterEndDate) {
      label = '至 ' + filterEndDate;
    }
    this.setData({ filterLabel: label, showFilterPanel: false });
    this.loadNearbyPhotos();
  },

  onFilterReset() {
    this.setData({
      filterStartDate: '', filterEndDate: '',
      filterLabel: '全部时间', showFilterPanel: false
    });
    this.loadNearbyPhotos();
  },

  // ========== 加载照片标记 ==========

  debouncedLoadPhotos() {
    if (this._loadTimer) clearTimeout(this._loadTimer);
    this._loadTimer = setTimeout(() => { this.loadNearbyPhotos(); }, 500);
  },

  loadNearbyPhotos() {
    if (this._loading) return;
    this._loading = true;
    const latitude = this._currentLat || this.data.latitude;
    const longitude = this._currentLng || this.data.longitude;
    const params = { latitude, longitude, radius: 10 };

    // 时光筛选参数
    if (this.data.filterStartDate) params.startDate = this.data.filterStartDate;
    if (this.data.filterEndDate) params.endDate = this.data.filterEndDate;

    request('/photo/nearby', 'GET', params)
      .then((res) => {
        const photos = res.data || [];
        this._photoIdMap = {};

        // 按位置聚合（精度4位小数）（优化点4）
        const groups = {};
        photos.forEach((item) => {
          const key = item.latitude.toFixed(4) + ',' + item.longitude.toFixed(4);
          if (!groups[key]) groups[key] = [];
          groups[key].push(item);
        });

        let markerId = 1;
        this._historyMarkers = [];
        Object.keys(groups).forEach((key) => {
          const group = groups[key];
          const first = group[0];
          const id = markerId++;
          this._photoIdMap[id] = first.id;

          let content = first.locationName || first.photoDate || '照片';
          if (group.length > 1) {
            content = content + '（' + group.length + '张）';
          }

          this._historyMarkers.push({
            id: id,
            latitude: first.latitude,
            longitude: first.longitude,
            iconPath: '/images/marker-photo.png',
            width: 36, height: 36,
            callout: {
              content: content,
              color: '#333', fontSize: 12, borderRadius: 8,
              padding: 6, display: 'BYCLICK',
              bgColor: '#fff', borderWidth: 1, borderColor: '#e0e0e0'
            }
          });
        });
        this._rebuildMarkers();
      })
      .catch((err) => { console.log('加载附近照片失败', err); })
      .finally(() => { this._loading = false; });
  },

  // ========== 地图选点（优化点5） ==========

  onMapTap(e) {
    if (this.data.uploading) return;
    // 关闭筛选面板
    if (this.data.showFilterPanel) {
      this.setData({ showFilterPanel: false });
      return;
    }
    let lat, lng;
    if (e.detail && e.detail.latitude !== undefined) {
      lat = e.detail.latitude;
      lng = e.detail.longitude;
    } else {
      return;
    }
    this.setData({
      tapLat: lat, tapLng: lng,
      showActionBar: true, tapLocationName: '获取地址中...'
    });
    this._rebuildMarkers();
    this._reverseGeocode(lat, lng);
    this._showToast('已选上传位置');
  },

  /** 逆地理编码（优化点3） */
  _reverseGeocode(lat, lng) {
    this._qqmapsdk.reverseGeocoder({
      location: { latitude: lat, longitude: lng },
      success: (res) => {
        console.log('[Map] 逆地理编码成功', res);
        const addr = res.result;
        const name = addr.formatted_addresses
          ? addr.formatted_addresses.recommend
          : addr.address;
        this.setData({ tapLocationName: name || addr.address });
      },
      fail: (err) => {
        console.error('[Map] 逆地理编码失败', err);
        this.setData({
          tapLocationName: lat.toFixed(4) + ', ' + lng.toFixed(4)
        });
      }
    });
  },

  _rebuildMarkers() {
    let markers = [...(this._historyMarkers || [])];
    if (this.data.showActionBar && this.data.tapLat) {
      markers.push({
        id: TEMP_MARKER_ID,
        latitude: this.data.tapLat,
        longitude: this.data.tapLng,
        iconPath: '/images/marker-temp.png',
        width: 44, height: 44,
        callout: {
          content: '上传位置', color: '#07c160', fontSize: 13,
          borderRadius: 8, padding: 6, display: 'ALWAYS',
          bgColor: '#fff', borderWidth: 1, borderColor: '#07c160'
        }
      });
    }
    this.setData({ markers });
  },

  // ========== 底部操作栏 ==========

  onUploadDateChange(e) {
    const date = e.detail.value;
    const today = this._formatDate(new Date());
    this.setData({
      uploadDate: date,
      uploadDateDisplay: (date === today) ? '今天' : date
    });
  },

  onCancelTap() {
    this.setData({ showActionBar: false, tapLat: 0, tapLng: 0, tapLocationName: '' });
    this._rebuildMarkers();
  },

  onGoUpload() {
    if (!checkLogin()) return;
    const { tapLat, tapLng, uploadDate, tapLocationName } = this.data;
    if (!uploadDate) { this._showToast('请选择日期'); return; }

    wx.chooseMedia({
      count: 1, mediaType: ['image'], sizeType: ['compressed'],
      success: (res) => {
        this._doUpload(res.tempFiles[0].tempFilePath, tapLat, tapLng, uploadDate, tapLocationName);
      }
    });
  },

  _doUpload(filePath, lat, lng, photoDate, locationName) {
    this.setData({ uploading: true, showActionBar: false });
    this._showToast('正在上传…');

    uploadFile('/photo/upload', filePath, {
      longitude: String(lng), latitude: String(lat),
      photoDate: photoDate, locationName: locationName || '', description: ''
    })
      .then(() => {
        this._showToast('上传成功，已加入时光地图');
        this.setData({ tapLat: 0, tapLng: 0 });
        this.loadNearbyPhotos();
      })
      .catch(() => {
        this._showToast('上传失败，请重试');
        this.setData({ showActionBar: true });
        this._rebuildMarkers();
      })
      .finally(() => { this.setData({ uploading: false }); });
  },

  // ========== 标记与地图事件 ==========

  onMarkerTap(e) {
    const markerId = e.markerId;
    if (markerId === TEMP_MARKER_ID) return;
    const photoId = this._photoIdMap && this._photoIdMap[markerId];
    if (photoId) {
      wx.navigateTo({ url: '/pages/detail/detail?id=' + photoId });
    }
  },

  onRegionChange(e) {
    if (e.type === 'end') {
      this.mapCtx.getCenterLocation({
        success: (res) => {
          this._currentLat = res.latitude;
          this._currentLng = res.longitude;
          this.debouncedLoadPhotos();
        }
      });
    }
  },

  // ========== 工具方法 ==========

  _formatDate(date) {
    const y = date.getFullYear();
    const m = String(date.getMonth() + 1).padStart(2, '0');
    const d = String(date.getDate()).padStart(2, '0');
    return y + '-' + m + '-' + d;
  },

  _showToast(msg) {
    this.setData({ uploadToast: msg, showToast: true });
    if (this._toastTimer) clearTimeout(this._toastTimer);
    this._toastTimer = setTimeout(() => { this.setData({ showToast: false }); }, 2500);
  }
});
