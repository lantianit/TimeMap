const { request } = require('../../utils/request');
const QQMapWX = require('../../utils/qqmap-wx-jssdk.min.js');
const app = getApp();

const DAY_NAMES = ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六'];

Page({
  data: {
    groups: [],
    months: [],
    activeMonth: '',
    activeDate: '',
    activePhotoId: '',
    loading: false,
    hasMore: true,
    isEmpty: false,
    page: 1,
    latitude: 0,
    longitude: 0,
    districtName: '',
    // 统计摘要
    statTotal: 0,
    statDays: 0,
    statBusiestDay: '',
    // 排序方式：photoDate=拍摄时间, createTime=上传时间, commentCount=评论数, likeCount=点赞数
    sortBy: 'photoDate',
    // 排行模式（评论数/点赞数排序时，不按日期分组）
    isRankMode: false,
    rankList: [],
    // 时间轴展开面板
    timelineExpanded: false,
    panelDates: [],
    panelScrollTo: ''
  },

  onLoad(options) {
    this.setData({
      latitude: parseFloat(options.latitude) || 30.5554,
      longitude: parseFloat(options.longitude) || 114.3162
    });
    this._scrollTimer = null;
    this._photoRects = null;
    this._rectsCacheTime = 0;
    this._windowHeight = wx.getSystemInfoSync().windowHeight;
    this._qqmapsdk = new QQMapWX({ key: app.globalData.mapKey });
    this._district = '';

    // 如果直接传了 district 参数，跳过坐标解析
    if (options.district) {
      this._district = decodeURIComponent(options.district);
      const city = options.city ? decodeURIComponent(options.city) : '';
      const name = city ? city + ' ' + this._district : this._district;
      this.setData({ districtName: name });
      this.loadData(true);
    } else {
      this._resolveDistrictThenLoad(this.data.latitude, this.data.longitude);
    }
  },

  onUnload() {
    if (this._scrollTimer) {
      clearTimeout(this._scrollTimer);
      this._scrollTimer = null;
    }
  },

  onPullDownRefresh() {
    this.loadData(true);
  },

  onReachBottom() {
    if (this.data.hasMore && !this.data.loading) {
      this.loadData(false);
    }
  },

  onPageScroll() {
    // 节流：150ms 内最多查询一次
    if (this._scrollTimer) return;
    this._scrollTimer = setTimeout(() => {
      this._scrollTimer = null;
      this._queryVisiblePhoto();
    }, 150);
  },

  loadData(refresh) {
    if (this.data.loading) return;
    const page = refresh ? 1 : this.data.page;
    this.setData({ loading: true });

    const isRankMode = this.data.sortBy === 'commentCount' || this.data.sortBy === 'likeCount';

    request('/photo/community', 'GET', {
      district: this._district || '',
      page: page,
      size: 20,
      sortBy: this.data.sortBy
    }).then(res => {
      const data = res.data || {};
      const list = data.list || [];
      const allPhotos = refresh ? list : (this._flatList || []).concat(list);
      this._flatList = allPhotos;

      if (isRankMode) {
        // 排行模式：不按日期分组，直接展示排行列表
        const rankList = allPhotos.map((item, idx) => ({
          ...item,
          rank: idx + 1
        }));
        this.setData({
          isRankMode: true,
          rankList,
          groups: [],
          months: [],
          panelDates: [],
          statTotal: 0,
          statDays: 0,
          statBusiestDay: '',
          hasMore: data.hasMore !== false,
          isEmpty: allPhotos.length === 0,
          page: page + 1,
          loading: false
        });
      } else {
        // 时间轴模式
        const groups = this._groupByDate(allPhotos);
        const months = this._extractMonths(groups);
        const panelDates = this._buildPanelDates(groups);

        // 计算统计摘要
        const statTotal = allPhotos.length;
        const statDays = groups.length;
        let statBusiestDay = '';
        if (groups.length > 0) {
          let max = groups[0];
          for (let i = 1; i < groups.length; i++) {
            if (groups[i].photoCount > max.photoCount) max = groups[i];
          }
          const p = max.dateKey.split('-');
          const userSet = {};
          max.photos.forEach(photo => {
            if (photo.userId) userSet[photo.userId] = true;
          });
          const userCount = Object.keys(userSet).length;
          statBusiestDay = parseInt(p[1]) + '月' + parseInt(p[2]) + '日(' + userCount + '位用户共上传' + max.photoCount + '张)';
        }

        this.setData({
          isRankMode: false,
          rankList: [],
          groups,
          months,
          panelDates,
          statTotal,
          statDays,
          statBusiestDay,
          hasMore: data.hasMore !== false,
          isEmpty: allPhotos.length === 0,
          page: page + 1,
          loading: false
        });

        // 设置初始激活日期
        if (groups.length > 0 && !this.data.activeDate) {
          this.setData({
            activeDate: groups[0].dateKey,
            activeMonth: months.length > 0 ? months[0].key : ''
          });
        }

        // 清除位置缓存，下次滚动时重新查询
        this._photoRects = null;
        this._buildPhotoDateMap(groups);
      }
    }).catch(() => {
      this.setData({ loading: false });
    }).finally(() => {
      if (refresh) wx.stopPullDownRefresh();
    });
  },

  // ========== 数据处理 ==========

  _groupByDate(list) {
    const map = {};
    const order = [];
    const useCreateTime = this.data.sortBy === 'createTime';
    list.forEach(item => {
      // 按上传时间分组时，取 createTime 的日期部分
      let date = item.photoDate || '';
      if (useCreateTime && item.createTime) {
        date = item.createTime.substring(0, 10); // "2026-03-06T22:59:57" → "2026-03-06"
      }
      if (!map[date]) {
        map[date] = {
          dateKey: date,
          dateId: date.replace(/-/g, ''),
          dateLabel: this._formatDateLabel(date),
          shortLabel: this._formatShortLabel(date),
          isToday: date === this._toDateStr(new Date()),
          photoCount: 0,
          photos: []
        };
        order.push(date);
      }
      map[date].photos.push(item);
      map[date].photoCount = map[date].photos.length;
    });
    return order.map(d => map[d]);
  },

  _extractMonths(groups) {
    const seen = {};
    const months = [];
    const years = new Set();
    groups.forEach(g => {
      const parts = g.dateKey.split('-');
      years.add(parts[0]);
    });
    const multiYear = years.size > 1;
    groups.forEach(g => {
      const parts = g.dateKey.split('-');
      const key = parts[0] + '-' + parts[1];
      if (!seen[key]) {
        seen[key] = true;
        months.push({
          key,
          label: multiYear ? parts[0] + '年' + parseInt(parts[1]) + '月' : parseInt(parts[1]) + '月',
          firstDate: g.dateKey
        });
      }
    });
    return months;
  },

  _formatDateLabel(dateStr) {
    if (!dateStr) return '';
    const today = new Date();
    const todayStr = this._toDateStr(today);
    const yesterday = new Date(today);
    yesterday.setDate(yesterday.getDate() - 1);
    const yesterdayStr = this._toDateStr(yesterday);

    if (dateStr === todayStr) return '今天';
    if (dateStr === yesterdayStr) return '昨天';

    const parts = dateStr.split('-');
    const d = new Date(parseInt(parts[0]), parseInt(parts[1]) - 1, parseInt(parts[2]));
    return parseInt(parts[1]) + '月' + parseInt(parts[2]) + '日 · ' + DAY_NAMES[d.getDay()];
  },

  _formatShortLabel(dateStr) {
    if (!dateStr) return '';
    const parts = dateStr.split('-');
    return parseInt(parts[1]) + '/' + parseInt(parts[2]);
  },

  _toDateStr(d) {
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const dd = String(d.getDate()).padStart(2, '0');
    return y + '-' + m + '-' + dd;
  },

  _buildPanelDates(groups) {
    const result = [];
    let lastMonth = '';
    const today = new Date();
    const todayStr = this._toDateStr(today);
    const yesterday = new Date(today);
    yesterday.setDate(yesterday.getDate() - 1);
    const yesterdayStr = this._toDateStr(yesterday);

    groups.forEach(g => {
      const parts = g.dateKey.split('-');
      const monthKey = parts[0] + '-' + parts[1];
      if (monthKey !== lastMonth) {
        result.push({
          type: 'month',
          key: 'month-' + monthKey,
          label: parts[0] + '年' + parseInt(parts[1]) + '月'
        });
        lastMonth = monthKey;
      }
      let weekDay = '';
      if (g.dateKey === todayStr) {
        weekDay = '今天';
      } else if (g.dateKey === yesterdayStr) {
        weekDay = '昨天';
      } else {
        const d = new Date(parseInt(parts[0]), parseInt(parts[1]) - 1, parseInt(parts[2]));
        weekDay = DAY_NAMES[d.getDay()];
      }
      result.push({
        type: 'date',
        key: 'pd-' + g.dateId,
        dateKey: g.dateKey,
        dateId: g.dateId,
        shortLabel: g.shortLabel,
        weekDay: weekDay,
        photoCount: g.photoCount,
        isToday: g.isToday
      });
    });
    return result;
  },

  // ========== 滚动追踪（替代 IntersectionObserver） ==========

  _buildPhotoDateMap(groups) {
    this._photoDateMap = {};
    this._photoIds = [];
    groups.forEach(g => {
      g.photos.forEach(photo => {
        this._photoDateMap[photo.id] = g.dateKey;
        this._photoIds.push(photo.id);
      });
    });
  },

  /** 查询当前屏幕中可见的照片，找到最上方且 70%+ 可见的那张 */
  _queryVisiblePhoto() {
    if (!this._photoIds || !this._photoIds.length) return;

    // 用 boundingClientRect 批量查询所有照片卡片的位置
    const query = wx.createSelectorQuery().in(this);
    query.selectAll('.tl-card-row').boundingClientRect();
    query.selectViewport().scrollOffset();
    query.exec((results) => {
      if (!results || !results[0]) return;
      const rects = results[0];
      const viewportHeight = this._windowHeight;

      let bestId = '';
      let bestTop = Infinity;

      for (let i = 0; i < rects.length; i++) {
        const rect = rects[i];
        if (!rect || !rect.id) continue;

        const photoId = rect.id.replace('photo-', '');
        const cardHeight = rect.height;
        if (cardHeight <= 0) continue;

        // 计算卡片在视口内的可见高度
        const visibleTop = Math.max(rect.top, 0);
        const visibleBottom = Math.min(rect.bottom, viewportHeight);
        const visibleHeight = visibleBottom - visibleTop;
        const visibleRatio = visibleHeight / cardHeight;

        // 70%+ 可见，且顶部在视口内（不是从上方溢出的半截）
        if (visibleRatio >= 0.7 && rect.top >= 0 && rect.top < bestTop) {
          bestTop = rect.top;
          bestId = photoId;
        }
      }

      if (!bestId) return;

      const updates = {};
      if (bestId !== this.data.activePhotoId) {
        updates.activePhotoId = bestId;
      }
      const dateKey = this._photoDateMap[bestId];
      if (dateKey && dateKey !== this.data.activeDate) {
        updates.activeDate = dateKey;
        updates.activeMonth = dateKey.substring(0, 7);
      }
      if (Object.keys(updates).length > 0) {
        this.setData(updates);
      }
    });
  },

  // ========== 交互事件 ==========

  onAxisTap() {
    if (this.data.timelineExpanded) return;
    // 计算滚动到当前激活日期
    const activeId = this.data.activeDate
      ? 'pd-' + this.data.activeDate.replace(/-/g, '')
      : '';
    this.setData({
      timelineExpanded: true,
      panelScrollTo: activeId
    });
  },

  onPanelDateTap(e) {
    const dateKey = e.currentTarget.dataset.date;
    const dateId = dateKey.replace(/-/g, '');
    this.setData({
      timelineExpanded: false,
      activeDate: dateKey,
      activeMonth: dateKey.substring(0, 7)
    });
    // 收起动画完成后再滚动，避免卡顿
    setTimeout(() => {
      wx.pageScrollTo({
        selector: '#date-' + dateId,
        duration: 300,
        offsetTop: -10
      });
    }, 280);
  },

  onPanelMaskTap() {
    this.setData({ timelineExpanded: false });
  },

  preventTouchMove() {
    // 阻止遮罩层的触摸穿透
  },

  onPanelTouchMove() {
    // 阻止面板区域的触摸事件穿透到页面，scroll-view 内部滚动不受影响
  },

  _resolveDistrictThenLoad(lat, lng) {
    this._qqmapsdk.reverseGeocoder({
      location: { latitude: lat, longitude: lng },
      success: (res) => {
        const comp = res.result.address_component || {};
        const city = comp.city || '';
        const district = comp.district || '';
        const name = city && district ? city + ' ' + district : city || district || '';
        this._district = district;
        this.setData({ districtName: name });
        this.loadData(true);
      },
      fail: () => {
        this._district = '';
        this.setData({ districtName: '' });
        this.loadData(true);
      }
    });
  },

  onDistrictTap() {
    wx.chooseLocation({
      success: (res) => {
        if (res.latitude && res.longitude) {
          this._photoRects = null;
          this._flatList = [];
          this.setData({
            latitude: res.latitude,
            longitude: res.longitude,
            loading: false,
            activeDate: '',
            activePhotoId: '',
            groups: [],
            months: [],
            panelDates: [],
            rankList: [],
            isRankMode: false,
            statTotal: 0,
            statDays: 0,
            statBusiestDay: '',
            isEmpty: false,
            hasMore: true,
            page: 1
          });
          this._resolveDistrictThenLoad(res.latitude, res.longitude);
        }
      }
    });
  },

  onSortToggle(e) {
    const sortBy = e.currentTarget.dataset.sort;
    if (sortBy === this.data.sortBy) return;
    this._flatList = [];
    this.setData({
      sortBy,
      groups: [],
      months: [],
      panelDates: [],
      rankList: [],
      isRankMode: false,
      activeDate: '',
      activePhotoId: '',
      statTotal: 0,
      statDays: 0,
      statBusiestDay: '',
      isEmpty: false,
      hasMore: true,
      page: 1
    });
    this.loadData(true);
  },

  onMonthTap(e) {
    const firstDate = e.currentTarget.dataset.first;
    const nodeId = 'date-' + firstDate.replace(/\-/g, '');
    const monthKey = firstDate.substring(0, 7);
    this.setData({ activeMonth: monthKey });
    wx.pageScrollTo({
      selector: '#' + nodeId,
      duration: 300,
      offsetTop: -10
    });
  },

  onPhotoTap(e) {
    const id = e.currentTarget.dataset.id;
    wx.navigateTo({ url: '/pages/detail/detail?id=' + id });
  }
});
