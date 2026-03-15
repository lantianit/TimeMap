const { request } = require('../../utils/request');
const app = getApp();

Page({
  data: {
    sortBy: 'photoCount',
    rankList: [],
    districtCount: 0,
    totalPhotos: 0,
    loading: false,
    isEmpty: false
  },

  onLoad() {
    this.loadRanking();
  },

  loadRanking() {
    this.setData({ loading: true, isEmpty: false });
    request('/photo/district-ranking', 'GET', {
      sortBy: this.data.sortBy,
      limit: 50
    }).then(res => {
      const data = res.data || {};
      const list = data.list || [];

      // 如果是今日活跃排序，过滤掉 todayCount=0 的
      const filtered = this.data.sortBy === 'todayCount'
        ? list.filter(item => item.todayCount > 0)
        : list;

      this.setData({
        rankList: filtered,
        districtCount: data.districtCount || 0,
        totalPhotos: data.totalPhotos || 0,
        isEmpty: filtered.length === 0,
        loading: false
      });
    }).catch(() => {
      this.setData({ loading: false, isEmpty: true });
    });
  },

  onSortTap(e) {
    const sortBy = e.currentTarget.dataset.sort;
    if (sortBy === this.data.sortBy) return;
    this.setData({ sortBy, rankList: [] });
    this.loadRanking();
  },

  /** 点击区县 → 跳转到该区县的社区页 */
  onDistrictTap(e) {
    const district = e.currentTarget.dataset.district;
    const city = e.currentTarget.dataset.city || '';
    wx.navigateTo({
      url: '/pages/community/community?district=' + encodeURIComponent(district) + '&city=' + encodeURIComponent(city)
    });
  }
});
