const app = getApp();

Page({
  data: {
    isLoggedIn: false,
    userInfo: {}
  },

  onShow() {
    this.checkLoginStatus();
  },

  /** 检查登录状态 */
  checkLoginStatus() {
    const isLoggedIn = app.isLoggedIn();
    this.setData({
      isLoggedIn,
      userInfo: app.globalData.userInfo || {}
    });
  },

  /** 一键登录 */
  onLogin() {
    wx.getUserProfile({
      desc: '用于完善个人资料',
      success: (profileRes) => {
        const userProfile = profileRes.userInfo;

        wx.showLoading({ title: '登录中...' });

        app.login(userProfile)
          .then(() => {
            wx.hideLoading();
            wx.showToast({ title: '登录成功', icon: 'success' });
            this.checkLoginStatus();
          })
          .catch((err) => {
            wx.hideLoading();
            wx.showToast({ title: '登录失败', icon: 'none' });
            console.error('[Profile] 登录失败', err);
          });
      },
      fail: (err) => {
        console.log('[Profile] 用户拒绝授权', err);
      }
    });
  },

  /** 跳转消息列表 */
  onGoMessages() {
    wx.navigateTo({ url: '/pages/messages/messages' });
  },

  /** 退出登录 */
  onLogout() {
    wx.showModal({
      title: '提示',
      content: '确定退出登录吗？',
      success: (res) => {
        if (res.confirm) {
          app.logout();
          this.checkLoginStatus();
          wx.showToast({ title: '已退出', icon: 'success' });
        }
      }
    });
  }
});
