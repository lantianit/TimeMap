const { request } = require('../../utils/request');

Page({
  data: {
    photos: [],
    current: 0,
    total: 0,
    // 当前展示的照片信息
    photo: {}
  },

  onLoad(options) {
    if (options.ids) {
      this.loadBatch(options.ids);
    } else if (options.id) {
      this.loadDetail(options.id);
    }
  },

  loadBatch(ids) {
    request('/photo/batch', 'GET', { ids })
      .then((res) => {
        const photos = res.data || [];
        if (photos.length === 0) {
          wx.showToast({ title: '照片不存在', icon: 'none' });
          return;
        }
        this.setData({
          photos,
          total: photos.length,
          current: 0,
          photo: photos[0]
        });
      })
      .catch(() => {
        wx.showToast({ title: '加载失败', icon: 'none' });
      });
  },

  loadDetail(id) {
    request('/photo/detail/' + id, 'GET')
      .then((res) => {
        const photo = res.data || {};
        this.setData({ photos: [photo], total: 1, current: 0, photo });
      })
      .catch(() => {
        wx.showToast({ title: '加载失败', icon: 'none' });
      });
  },

  onSwiperChange(e) {
    const idx = e.detail.current;
    this.setData({ current: idx, photo: this.data.photos[idx] });
  },

  previewImage() {
    const urls = this.data.photos.map(p => p.imageUrl);
    wx.previewImage({
      current: this.data.photo.imageUrl,
      urls
    });
  }
});
