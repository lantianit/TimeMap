const { request, uploadFile, checkLogin } = require('../../utils/request');

const TEMP_MARKER_ID = 99999;

Page({
  data: {
    latitude: 39.908823,
    longitude: 116.397470,
    markers: [],
    scale: 14,
    // 底部操作栏
    showActionBar: false,
    tapLat: 0,
    tapLng: 0,
    tapLocationName: '定位中...',
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
    const today = this._formatDate(new Date());
    this.setData({ uploadDate: today, uploadDateDisplay: '今天' });
    this.getCurrentLocation();
  },

  onShow() {
    this.debouncedLoadPhotos();
  },

  getCurrentLocation() {
    wx.getLocation({
      type: 'gcj02',
      success: (res) => {
        this.setData({ latitude: res.latitude, longitude: res.longitude });
        this._currentLat = res.latitude;
        this._currentLng = res.longitude;
        this.debouncedLoadPhotos();
      },
      fail: () => {
        this._showToast('未获取定位，可手动点击地图选点');
      }
    });
  },

  debouncedLoadPhotos() {
    if (this._loadTimer) clearTimeout(this._loadTimer);
    this._loadTimer = setTimeout(() => { this.loadNearbyPhotos(); }, 500);
  },

  loadNearbyPhotos() {
    if (this._loading) return;
    this._loading = true;
    const latitude = this._currentLat || this.data.latitude;
    const longitude = this._currentLng || this.data.longitude;
    request('/photo/nearby', 'GET', { latitude, longitude, radius: 10 })
      .then((res) => {
        const photos = res.data || [];
        this._photoIdMap = {};
        this._historyMarkers = photos.map((item, index) => {
          const markerId = index + 1;
          this._photoIdMap[markerId] = item.id;
          return {
            id: markerId,
            latitude: item.latitude,
            longitude: item.longitude,
            iconPath: '/images/marker-photo.png',
            width: 36, height: 36,
            callout: {
              content: item.locationName || item.photoDate || '照片',
              color: '#333333', fontSize: 12, borderRadius: 8,
              padding: 6, display: 'BYCLICK',
              bgColor: '#ffffff', borderWidth: 1, borderColor: '#e0e0e0'
            }
          };
        });
        this._rebuildMarkers();
      })
      .catch((err) => { console.log('加载附近照片失败', err); })
      .finally(() => { this._loading = false; });
  },

  // ========== 地图选点交互 ==========

  onMapTap(e) {
    if (this.data.uploading) return;
    let lat, lng;
    if (e.detail && e.detail.latitude !== undefined) {
      lat = e.detail.latitude;
      lng = e.detail.longitude;
    } else {
      return;
    }
    this.setData({
      tapLat: lat, tapLng: lng,
      showActionBar: true, tapLocationName: '定位中...'
    });
    this._rebuildMarkers();
    this._reverseGeocode(lat, lng);
  },

  _reverseGeocode(lat, lng) {
    this.setData({
      tapLocationName: '纬度' + lat.toFixed(4) + ' 经度' + lng.toFixed(4)
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
          bgColor: '#ffffff', borderWidth: 1, borderColor: '#07c160'
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
    this._showToast('正在上传【' + photoDate + '】' + (locationName || '') + '的照片…');

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
    this._toastTimer = setTimeout(() => { this.setData({ showToast: false }); }, 3000);
  }
});
