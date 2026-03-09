const { checkLogin } = require('../../utils/request');

Page({
  data: {
    imagePath: '',
    photoDate: '',
    locationName: '',
    latitude: 0,
    longitude: 0,
    description: '',
    todayDate: ''
  },

  onLoad() {
    checkLogin();
    const d = new Date();
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const dd = String(d.getDate()).padStart(2, '0');
    this.setData({ todayDate: y + '-' + m + '-' + dd });
  },

  /** 选择图片 */
  chooseImage() {
    wx.chooseMedia({
      count: 1,
      mediaType: ['image'],
      success: (res) => {
        this.setData({
          imagePath: res.tempFiles[0].tempFilePath
        });
      }
    });
  },

  /** 选择日期 */
  onDateChange(e) {
    this.setData({ photoDate: e.detail.value });
  },

  /** 选择地点 */
  chooseLocation() {
    wx.chooseLocation({
      success: (res) => {
        this.setData({
          locationName: res.name || res.address,
          latitude: res.latitude,
          longitude: res.longitude
        });
      }
    });
  },

  /** 描述输入 */
  onDescInput(e) {
    this.setData({ description: e.detail.value });
  },

  /** 提交上传 */
  submit() {
    const { imagePath, photoDate, latitude, longitude } = this.data;
    if (!imagePath || !photoDate || !latitude) {
      wx.showToast({ title: '请填写完整信息', icon: 'none' });
      return;
    }
    // TODO: 请求后端签名 → 直传 COS → 提交元信息
    wx.showToast({ title: '上传功能开发中', icon: 'none' });
  }
});
