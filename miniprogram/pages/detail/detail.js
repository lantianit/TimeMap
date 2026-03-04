const { request } = require('../../utils/request');

Page({
  data: {
    photo: {}
  },

  onLoad(options) {
    if (options.id) {
      this.loadDetail(options.id);
    }
  },

  loadDetail(id) {
    request('/photo/detail/' + id, 'GET')
      .then((res) => {
        this.setData({ photo: res.data || {} });
      })
      .catch((err) => {
        console.log('加载照片详情失败', err);
        wx.showToast({ title: '加载失败', icon: 'none' });
      });
  },

  /** 预览大图 */
  previewImage() {
    wx.previewImage({
      current: this.data.photo.imageUrl,
      urls: [this.data.photo.imageUrl]
    });
  }
});
