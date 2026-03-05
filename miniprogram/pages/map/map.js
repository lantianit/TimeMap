const { request, uploadFile, checkLogin } = require('../../utils/request');
const QQMapWX = require('../../utils/qqmap-wx-jssdk.min.js');
const app = getApp();

const TEMP_MARKER_ID = 99999;
const YEARS = [];
const _currentYear = new Date().getFullYear();
for (let y = 1900; y <= _currentYear; y++) YEARS.push(y);

function buildDays(year, month) {
  const count = new Date(year, month, 0).getDate();
  const now = new Date();
  const maxDay = (year === now.getFullYear() && month === now.getMonth() + 1)
    ? now.getDate() : count;
  const arr = [];
  for (let d = 1; d <= maxDay; d++) arr.push(d);
  return arr;
}

function buildMonths(year) {
  const now = new Date();
  const max = (year === now.getFullYear()) ? now.getMonth() + 1 : 12;
  const arr = [];
  for (let m = 1; m <= max; m++) arr.push(m);
  return arr;
}

Page({
  data: {
    latitude: 30.5554,
    longitude: 114.3162,
    markers: [],
    photoMarkers: [],
    scale: 14,
    // 时光穿梭
    filterLabel: '全部时间',
    filterActive: false,
    showFilterPanel: false,
    panelTab: 'day',
    rangeStart: '',
    rangeEnd: '',
    years: YEARS,
    months: buildMonths(new Date().getFullYear()),
    selectedYear: new Date().getFullYear(),
    selectedMonth: new Date().getMonth() + 1,
    selectedDay: new Date().getDate(),
    days: buildDays(new Date().getFullYear(), new Date().getMonth() + 1),
    yearScrollLeft: 0,
    monthScrollLeft: 0,
    dayScrollLeft: 0,
    // 定位状态
    hasLocation: false,
    districtName: '',
    // 上传相关
    showHidePhotosBar: false,
    showUploadBar: false,
    tapLat: 0,
    tapLng: 0,
    tapLocationName: '',
    uploadDate: '',
    uploadDateDisplay: '今天',
    selectedImages: [],
    // 上传状态
    uploading: false,
    uploadProgress: 0,
    uploadToast: '',
    showToast: false,
    // 加载状态
    loadingPhotos: false,
    // 空状态
    emptyState: false,
    // 照片显隐
    photosHidden: false,
    // 定位按钮显隐
    showLocateBtn: false,
    // 临时标记预览图
    tempPreviewImage: ''
  },

  onLoad() {
    this.mapCtx = wx.createMapContext('map');
    this._loading = false;
    this._loadTimer = null;
    this._loadSeq = 0;
    this._historyMarkers = [];
    this._photoIdMap = {};
    this._photoUrlMap = {};
    this._qqmapsdk = new QQMapWX({ key: app.globalData.mapKey });

    const today = this._formatDate(new Date());
    this.setData({ uploadDate: today, uploadDateDisplay: '今天' });

    this._restoreFilterState();
    this.getCurrentLocation();
  },

  onShow() {
    // 上传流程中不要重新加载照片（选照片从相册返回会触发 onShow）
    if (this.data.showUploadBar || this.data.showHidePhotosBar) return;
    // 从详情页等子页面返回时，照片数据没变，跳过刷新避免闪烁
    if (this._skipNextShow) {
      this._skipNextShow = false;
      return;
    }
    this.debouncedLoadPhotos();
  },

  // ========== 筛选状态持久化 ==========

  _saveFilterState() {
    const state = {
      filterLabel: this.data.filterLabel,
      filterActive: this.data.filterActive,
      panelTab: this.data.panelTab,
      rangeStart: this.data.rangeStart,
      rangeEnd: this.data.rangeEnd,
      selectedYear: this.data.selectedYear,
      selectedMonth: this.data.selectedMonth,
      selectedDay: this.data.selectedDay,
      startDate: this._filterStartDate || '',
      endDate: this._filterEndDate || ''
    };
    wx.setStorageSync('filterState', state);
  },

  _restoreFilterState() {
    try {
      const state = wx.getStorageSync('filterState');
      if (state && state.filterActive) {
        this.setData({
          filterLabel: state.filterLabel,
          filterActive: state.filterActive,
          panelTab: state.panelTab,
          rangeStart: state.rangeStart || '',
          rangeEnd: state.rangeEnd || '',
          selectedYear: state.selectedYear,
          selectedMonth: state.selectedMonth,
          selectedDay: state.selectedDay,
          months: buildMonths(state.selectedYear),
          days: buildDays(state.selectedYear, state.selectedMonth)
        });
        this._filterStartDate = state.startDate;
        this._filterEndDate = state.endDate;
      }
    } catch (e) { /* ignore */ }
  },

  // ========== 滚轮居中计算 ==========

  /**
   * 精确居中：用 SelectorQuery 测量 item 实际位置，计算 scrollLeft
   */
  _scrollToCenter(svClass, itemId, dataKey) {
    const query = this.createSelectorQuery();
    query.select(svClass).scrollOffset();
    query.select(svClass).boundingClientRect();
    query.select('#' + itemId).boundingClientRect();
    query.exec((res) => {
      if (!res[0] || !res[1] || !res[2]) return;
      const curScroll = res[0].scrollLeft;
      const svLeft = res[1].left;
      const svWidth = res[1].width;
      const itemLeft = res[2].left;
      const itemWidth = res[2].width;
      const target = curScroll + (itemLeft + itemWidth / 2) - (svLeft + svWidth / 2);
      this.setData({ [dataKey]: Math.max(0, target) });
    });
  },

  _scrollYearTo(year) {
    this._yearGuard = true;
    setTimeout(() => {
      this._scrollToCenter('.wheel-scroll-year', 'year-' + year, 'yearScrollLeft');
      setTimeout(() => { this._yearGuard = false; }, 300);
    }, 20);
  },

  _scrollMonthTo(month) {
    this._monthGuard = true;
    setTimeout(() => {
      this._scrollToCenter('.wheel-scroll-month', 'month-' + month, 'monthScrollLeft');
      setTimeout(() => { this._monthGuard = false; }, 300);
    }, 20);
  },

  _scrollDayTo(day) {
    this._dayGuard = true;
    setTimeout(() => {
      this._scrollToCenter('.wheel-scroll-day', 'day-' + day, 'dayScrollLeft');
      setTimeout(() => { this._dayGuard = false; }, 300);
    }, 20);
  },

  /**
   * 滚动停止后 snap：测量屏幕中心最近的 item，选中并居中
   */
  _snapToNearest(svClass, itemClass, callback) {
    const query = this.createSelectorQuery();
    query.select(svClass).boundingClientRect();
    query.selectAll(itemClass).boundingClientRect();
    query.exec((res) => {
      if (!res[0] || !res[1] || !res[1].length) return;
      const svCenter = res[0].left + res[0].width / 2;
      let minDist = Infinity;
      let closest = 0;
      res[1].forEach((rect, i) => {
        const center = rect.left + rect.width / 2;
        const dist = Math.abs(center - svCenter);
        if (dist < minDist) { minDist = dist; closest = i; }
      });
      callback(closest);
    });
  },

  onYearScroll() {
    if (this._yearGuard) return;
    if (this._yearScrollTimer) clearTimeout(this._yearScrollTimer);
    this._yearScrollTimer = setTimeout(() => {
      this._snapToNearest('.wheel-scroll-year', '.year-item', (idx) => {
        idx = Math.max(0, Math.min(idx, YEARS.length - 1));
        if (YEARS[idx] !== this.data.selectedYear) {
          this.setData({ selectedYear: YEARS[idx] });
          this._updateDays(YEARS[idx], this.data.selectedMonth, false);
        }
        this._scrollYearTo(YEARS[idx]);
      });
    }, 120);
  },

  onMonthScroll() {
    if (this._monthGuard) return;
    if (this._monthScrollTimer) clearTimeout(this._monthScrollTimer);
    this._monthScrollTimer = setTimeout(() => {
      this._snapToNearest('.wheel-scroll-month', '.month-item', (idx) => {
        const months = this.data.months;
        idx = Math.max(0, Math.min(idx, months.length - 1));
        if (months[idx] !== this.data.selectedMonth) {
          this.setData({ selectedMonth: months[idx] });
          this._updateDays(this.data.selectedYear, months[idx], false);
        }
        this._scrollMonthTo(months[idx]);
      });
    }, 120);
  },

  onDayScroll() {
    if (this._dayGuard) return;
    if (this._dayScrollTimer) clearTimeout(this._dayScrollTimer);
    this._dayScrollTimer = setTimeout(() => {
      this._snapToNearest('.wheel-scroll-day', '.day-item', (idx) => {
        const days = this.data.days;
        idx = Math.max(0, Math.min(idx, days.length - 1));
        if (days[idx] !== this.data.selectedDay) {
          this.setData({ selectedDay: days[idx] });
        }
        this._scrollDayTo(days[idx]);
      });
    }, 120);
  },

  _updateDays(year, month, resetDay) {
    // 更新可用月份（当前年份限制到当前月）
    const months = buildMonths(year);
    const clampedMonth = Math.min(month, months.length);
    // 更新可用日期
    const days = buildDays(year, clampedMonth);
    const day = resetDay ? 1 : Math.min(this.data.selectedDay, days.length);
    this.setData({ months, days, selectedMonth: clampedMonth, selectedDay: day });
    setTimeout(() => {
      this._scrollMonthTo(clampedMonth);
      this._scrollDayTo(day);
    }, 50);
  },

  // ========== 定位 ==========

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
        this._updateDistrict(res.latitude, res.longitude);
        this.debouncedLoadPhotos();
      },
      fail: () => {
        this._showToast('未获取定位，可手动点击地图选点');
      }
    });
  },

  onLocateTap() {
    if (!this.data.hasLocation) {
      this.getCurrentLocation();
      return;
    }
    this.mapCtx.moveToLocation({
      latitude: this._userLat,
      longitude: this._userLng,
      success: () => {
        this.setData({ showLocateBtn: false });
      }
    });
  },

  // ========== 刷新按钮 ==========

  onRefreshTap() {
    this.loadNearbyPhotos();
    this._showToast('已刷新');
  },

  // ========== 时光穿梭 ==========

  onFilterTap() {
    if (!this.data.showFilterPanel) {
      if (this.data.panelTab === 'day' && !this.data.activeQuick) {
        setTimeout(() => {
          this._scrollYearTo(this.data.selectedYear);
          this._scrollMonthTo(this.data.selectedMonth);
          this._scrollDayTo(this.data.selectedDay);
        }, 350);
      }
    }
    this.setData({ showFilterPanel: !this.data.showFilterPanel });
  },

  onCloseFilter() {
    this.setData({ showFilterPanel: false });
  },

  onMaskTap() {
    this.setData({ showFilterPanel: false });
  },

  onTabAll() { this.setData({ panelTab: 'all' }); },

  onTabDay() {
    this.setData({ panelTab: 'day' });
    setTimeout(() => {
      this._scrollYearTo(this.data.selectedYear);
      this._scrollMonthTo(this.data.selectedMonth);
      this._scrollDayTo(this.data.selectedDay);
    }, 50);
  },

  onTabRange() { this.setData({ panelTab: 'range' }); },

  onRangeStartChange(e) { this.setData({ rangeStart: e.detail.value }); },
  onRangeEndChange(e) { this.setData({ rangeEnd: e.detail.value }); },

  onYearItemTap(e) {
    const year = e.currentTarget.dataset.year;
    this.setData({ selectedYear: year });
    this._scrollYearTo(year);
    this._updateDays(year, this.data.selectedMonth, false);
  },

  onMonthItemTap(e) {
    const month = e.currentTarget.dataset.month;
    this.setData({ selectedMonth: month });
    this._scrollMonthTo(month);
    this._updateDays(this.data.selectedYear, month, false);
  },

  onDayItemTap(e) {
    const day = e.currentTarget.dataset.day;
    this.setData({ selectedDay: day });
    this._scrollDayTo(day);
  },

  /** 确认穿梭 */
  onFilterConfirm() {
    const { panelTab, selectedYear, selectedMonth, selectedDay, rangeStart, rangeEnd } = this.data;
    let startDate = '';
    let endDate = '';
    let label = '全部时间';
    let filterActive = false;

    if (panelTab === 'all') {
      // 全部时间
    } else if (panelTab === 'range') {
      if (rangeStart) startDate = rangeStart;
      if (rangeEnd) endDate = rangeEnd;
      if (startDate && endDate) {
        label = startDate + ' ~ ' + endDate;
        filterActive = true;
      } else if (startDate) {
        label = startDate + ' 起';
        filterActive = true;
      } else if (endDate) {
        label = '至 ' + endDate;
        filterActive = true;
      }
    } else {
      const m = String(selectedMonth).padStart(2, '0');
      const dd = String(selectedDay).padStart(2, '0');
      const dateStr = selectedYear + '-' + m + '-' + dd;
      startDate = dateStr;
      endDate = dateStr;
      label = selectedYear + '年' + selectedMonth + '月' + selectedDay + '日';
      filterActive = true;
    }

    this._filterStartDate = startDate;
    this._filterEndDate = endDate;
    this.setData({
      filterLabel: label,
      filterActive: filterActive,
      showFilterPanel: false
    });
    this._saveFilterState();
    this.loadNearbyPhotos();
  },

  /** 重置 */
  onFilterReset() {
    const now = new Date();
    this._filterStartDate = '';
    this._filterEndDate = '';
    this.setData({
      panelTab: 'day',
      rangeStart: '',
      rangeEnd: '',
      filterLabel: '全部时间',
      filterActive: false,
      selectedYear: now.getFullYear(),
      selectedMonth: now.getMonth() + 1,
      selectedDay: now.getDate(),
      months: buildMonths(now.getFullYear()),
      days: buildDays(now.getFullYear(), now.getMonth() + 1)
    });
    this._saveFilterState();
    setTimeout(() => {
      this._scrollYearTo(now.getFullYear());
      this._scrollMonthTo(now.getMonth() + 1);
      this._scrollDayTo(now.getDate());
    }, 50);
  },

  // ========== 加载照片标记（含竞态处理 + 聚合） ==========

  debouncedLoadPhotos() {
    if (this._loadTimer) clearTimeout(this._loadTimer);
    this._loadTimer = setTimeout(() => { this.loadNearbyPhotos(); }, 500);
  },

  loadNearbyPhotos() {
    if (this._loading) return;
    if (this.data.photosHidden) return;
    this._loading = true;
    const seq = ++this._loadSeq;
    this.setData({ loadingPhotos: true });

    const latitude = this._currentLat || this.data.latitude;
    const longitude = this._currentLng || this.data.longitude;
    const params = { latitude, longitude, radius: 10 };

    if (this._filterStartDate) params.startDate = this._filterStartDate;
    if (this._filterEndDate) params.endDate = this._filterEndDate;

    request('/photo/nearby', 'GET', params)
      .then((res) => {
        if (seq !== this._loadSeq) return;

        const photos = res.data || [];
        this._photoIdMap = {};
        this._photoUrlMap = {};

        const scale = this.data.scale || 14;
        const precision = scale >= 15 ? 4 : scale >= 12 ? 3 : 2;

        const groups = {};
        photos.forEach((item) => {
          const key = item.latitude.toFixed(precision) + ',' + item.longitude.toFixed(precision);
          if (!groups[key]) groups[key] = [];
          groups[key].push(item);
        });

        let markerId = 1;
        this._historyMarkers = [];
        const photoMarkers = [];
        Object.keys(groups).forEach((key) => {
          const group = groups[key];
          const first = group[0];
          const id = markerId++;
          // 存储该聚合点的所有照片 id
          this._photoIdMap[id] = group.map(p => p.id);
          this._photoUrlMap[id] = first.imageUrl || first.thumbnailUrl || '';

          const thumbUrl = first.thumbnailUrl || first.imageUrl || '';
          const count = group.length;

          photoMarkers.push({
            id, thumbUrl, count,
            locationName: first.locationName || ''
          });

          this._historyMarkers.push({
            id, latitude: first.latitude, longitude: first.longitude,
            iconPath: '/images/marker-transparent.png', width: 1, height: 1,
            customCallout: { anchorY: 0, anchorX: 0, display: 'ALWAYS' }
          });
        });

        this.setData({
          photoMarkers,
          emptyState: photos.length === 0
        });
        this._rebuildMarkers();
      })
      .catch((err) => {
        if (seq !== this._loadSeq) return;
        console.log('加载附近照片失败', err);
      })
      .finally(() => {
        if (seq === this._loadSeq) {
          this._loading = false;
          this.setData({ loadingPhotos: false });
        }
      });
  },

  // ========== 地图点击 → 直接触发上传流程 ==========

  onMapTap(e) {
    if (this.data.uploading) return;
    if (this.data.showFilterPanel) {
      this.setData({ showFilterPanel: false });
      return;
    }
    // 如果已经在上传流程中，点击地图 = 重新选点
    if (this.data.showUploadBar) {
      let lat, lng;
      if (e.detail && e.detail.latitude !== undefined) {
        lat = e.detail.latitude;
        lng = e.detail.longitude;
      } else { return; }
      this.setData({ tapLat: lat, tapLng: lng, tapLocationName: '获取地址中...' });
      this._rebuildMarkers();
      this._reverseGeocode(lat, lng);
      return;
    }

    // 需要登录
    if (!checkLogin()) return;

    let lat, lng;
    if (e.detail && e.detail.latitude !== undefined) {
      lat = e.detail.latitude;
      lng = e.detail.longitude;
    } else { return; }

    const today = this._formatDate(new Date());
    const hasVisiblePhotos = this.data.photoMarkers.length > 0 && !this.data.photosHidden;
    this.setData({
      tapLat: lat, tapLng: lng,
      tapLocationName: '获取地址中...',
      uploadDate: today,
      uploadDateDisplay: '今天',
      selectedImages: [],
      showHidePhotosBar: hasVisiblePhotos,
      showUploadBar: true
    });
    // setData 之后再 rebuild，确保条件判断正确
    this._rebuildMarkers();
    this._reverseGeocode(lat, lng);
  },

  /** 隐藏照片提示条 → 确认隐藏 */
  onHidePhotosConfirm() {
    this._historyMarkers = [];
    this.setData({
      photosHidden: true, photoMarkers: [],
      showHidePhotosBar: false
    });
    this._rebuildMarkers();
  },

  /** 隐藏照片提示条 → 不用了 */
  onHidePhotosCancel() {
    this.setData({ showHidePhotosBar: false });
  },

  /** 取消上传（关闭上传悬浮条） */
  onCancelUpload() {
    this.setData({
      showUploadBar: false,
      showHidePhotosBar: false,
      tapLat: 0, tapLng: 0,
      tapLocationName: '',
      selectedImages: []
    });
    this._rebuildMarkers();
  },

  _reverseGeocode(lat, lng) {
    this._qqmapsdk.reverseGeocoder({
      location: { latitude: lat, longitude: lng },
      success: (res) => {
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
    if (this.data.tapLat && (this.data.showUploadBar || this.data.showHidePhotosBar)) {
      const hasImages = this.data.selectedImages.length > 0;
      if (hasImages) {
        // 有照片时用 customCallout 展示缩略图（和照片标记同样方式）
        markers.push({
          id: TEMP_MARKER_ID,
          latitude: this.data.tapLat,
          longitude: this.data.tapLng,
          iconPath: '/images/marker-transparent.png',
          width: 1, height: 1,
          customCallout: { anchorY: 0, anchorX: 0, display: 'ALWAYS' }
        });
        this.setData({ tempPreviewImage: this.data.selectedImages[0] });
      } else {
        // 没选照片时用文字 callout
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
        this.setData({ tempPreviewImage: '' });
      }
    } else {
      this.setData({ tempPreviewImage: '' });
    }
    this.setData({ markers });
  },

  // ========== 上传悬浮条操作 ==========

  onUploadDateChange(e) {
    const date = e.detail.value;
    const today = this._formatDate(new Date());
    this.setData({
      uploadDate: date,
      uploadDateDisplay: (date === today) ? '今天' : date
    });
  },

  onChooseImages() {
    const remaining = 9 - this.data.selectedImages.length;
    if (remaining <= 0) { this._showToast('最多选择9张'); return; }
    wx.chooseMedia({
      count: remaining,
      mediaType: ['image'],
      sizeType: ['compressed'],
      success: (res) => {
        const newImages = res.tempFiles.map(f => f.tempFilePath);
        this.setData({
          selectedImages: [...this.data.selectedImages, ...newImages]
        });
        this._rebuildMarkers();
      }
    });
  },

  onRemoveImage(e) {
    const idx = e.currentTarget.dataset.idx;
    const images = [...this.data.selectedImages];
    images.splice(idx, 1);
    this.setData({ selectedImages: images });
    this._rebuildMarkers();
  },

  onSubmitUpload() {
    const { tapLat, tapLng, uploadDate, tapLocationName, selectedImages } = this.data;
    if (!selectedImages.length) { this._showToast('请先选择照片'); return; }
    if (!uploadDate) { this._showToast('请选择日期'); return; }

    this.setData({ uploading: true, uploadProgress: 0 });
    const total = selectedImages.length;
    let failed = 0;

    const uploadNext = (idx) => {
      if (idx >= total) {
        const msg = failed > 0
          ? `上传完成，${total - failed}张成功，${failed}张失败`
          : total === 1 ? '上传成功，已加入时光地图'
          : `${total}张照片全部上传成功`;
        this._showToast(msg);
        this.setData({ uploading: false, uploadProgress: 0 });
        // 关闭上传条
        this.setData({
          showUploadBar: false,
          tapLat: 0, tapLng: 0,
          tapLocationName: '',
          selectedImages: []
        });
        this._rebuildMarkers();
        // 恢复照片显示并刷新
        if (this.data.photosHidden) {
          this.setData({ photosHidden: false });
        }
        this._loading = false;
        this.loadNearbyPhotos();
        return;
      }

      this.setData({ uploadProgress: Math.round(((idx + 0.5) / total) * 100) });

      uploadFile('/photo/upload', selectedImages[idx], {
        longitude: String(tapLng), latitude: String(tapLat),
        photoDate: uploadDate, locationName: tapLocationName || '', description: ''
      })
        .catch(() => { failed++; })
        .finally(() => { uploadNext(idx + 1); });
    };

    uploadNext(0);
  },

  // ========== 标记与地图事件 ==========

  onMarkerTap(e) {
    const markerId = e.markerId;
    if (markerId === TEMP_MARKER_ID) return;
    const photoIds = this._photoIdMap && this._photoIdMap[markerId];
    if (photoIds && photoIds.length) {
      this._skipNextShow = true;
      wx.navigateTo({ url: '/pages/detail/detail?ids=' + photoIds.join(',') });
    }
  },

  /** 长按照片标记 → 预览大图 */
  onCalloutLongPress(e) {
    const markerId = e.markerId;
    if (markerId === TEMP_MARKER_ID) return;
    const imageUrl = this._photoUrlMap && this._photoUrlMap[markerId];
    if (imageUrl) {
      wx.previewImage({ current: imageUrl, urls: [imageUrl] });
    }
  },

  onRegionChange(e) {
    if (e.type === 'end') {
      this.mapCtx.getCenterLocation({
        success: (res) => {
          this._currentLat = res.latitude;
          this._currentLng = res.longitude;
          this._updateDistrict(res.latitude, res.longitude);
        }
      });
      this._checkLocateVisible();
    }
  },

  /** 判断用户定位点是否在当前可视区域内，不在则显示定位按钮 */
  _checkLocateVisible() {
    if (!this._userLat || !this._userLng) return;
    this.mapCtx.getRegion({
      success: (res) => {
        const { southwest, northeast } = res;
        const inView = this._userLat >= southwest.latitude
          && this._userLat <= northeast.latitude
          && this._userLng >= southwest.longitude
          && this._userLng <= northeast.longitude;
        if (this.data.showLocateBtn !== !inView) {
          this.setData({ showLocateBtn: !inView });
        }
      }
    });
  },

  /** 切换照片显示/隐藏 */
  onTogglePhotos() {
    if (this.data.photosHidden) {
      this.setData({ photosHidden: false });
      this._loading = false;
      this.loadNearbyPhotos();
    } else {
      this._historyMarkers = [];
      this.setData({ photosHidden: true, photoMarkers: [] });
      this._rebuildMarkers();
    }
  },

  // ========== 底部悬浮栏 ==========

  onChatTap() {
    this._showToast('聊天功能即将上线');
  },

  onCommunityTap() {
    this._showToast('社区功能即将上线');
  },

  onProfileTap() {
    this._skipNextShow = true;
    wx.navigateTo({ url: '/pages/profile/profile' });
  },

  onUploadHintTap() {
    this._showToast('点击地图任意位置，即可在该位置上传照片');
  },

  /** 顶部区县名点击 → 选择城市/搜索 */
  onDistrictTap() {
    wx.chooseLocation({
      success: (res) => {
        if (res.latitude && res.longitude) {
          this.setData({
            latitude: res.latitude,
            longitude: res.longitude
          });
          this._currentLat = res.latitude;
          this._currentLng = res.longitude;
          this._updateDistrict(res.latitude, res.longitude);
          this.mapCtx.moveToLocation({
            latitude: res.latitude,
            longitude: res.longitude
          });
          this.debouncedLoadPhotos();
        }
      }
    });
  },

  /** 根据经纬度更新顶部区县名 */
  _updateDistrict(lat, lng) {
    this._qqmapsdk.reverseGeocoder({
      location: { latitude: lat, longitude: lng },
      success: (res) => {
        const comp = res.result.address_component || {};
        const district = comp.district || comp.city || '';
        this.setData({ districtName: district });
      },
      fail: () => {}
    });
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
