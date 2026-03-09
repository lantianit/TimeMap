const { request } = require('../../utils/request');

function normalizeTopAreas(areas) {
  return (areas || []).map(item => ({
    name: item.name || '未标注区域',
    count: item.count || 0
  }));
}

Page({
  data: {
    user: null,
    topAreas: [],
    photos: [],
    total: 0,
    page: 1,
    hasMore: true,
    loading: false
  },

  onLoad(options) {
    this._userId = options.userId;
    if (options.nickname) {
      wx.setNavigationBarTitle({ title: decodeURIComponent(options.nickname) });
    }
    this.loadUserPhotos(true);
  },

  onReachBottom() {
    if (this.data.hasMore && !this.data.loading) {
      this.loadUserPhotos(false);
    }
  },

  loadUserPhotos(refresh) {
    const page = refresh ? 1 : this.data.page;
    this.setData({ loading: true });
    request('/photo/user/' + this._userId, 'GET', { page, size: 20 })
      .then(res => {
        const data = res.data || {};
        const user = data.user || this.data.user;
        const list = (data.list || []).map(p => {
          if (p.photoDate) p.photoDateLabel = p.photoDate;
          return p;
        });
        this.setData({
          user,
          topAreas: normalizeTopAreas(user && user.topAreas),
          photos: refresh ? list : this.data.photos.concat(list),
          total: data.total || 0,
          page: page + 1,
          hasMore: data.hasMore !== false,
          loading: false
        });
        if (data.user && data.user.nickname) {
          wx.setNavigationBarTitle({ title: data.user.nickname });
        }
      })
      .catch(() => { this.setData({ loading: false }); });
  },

  onPhotoTap(e) {
    const id = e.currentTarget.dataset.id;
    wx.navigateTo({ url: '/pages/detail/detail?id=' + id });
  }
});
