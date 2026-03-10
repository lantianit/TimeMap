const { request } = require('../../utils/request');
const app = getApp();

function normalizeTopAreas(areas) {
  return (areas || []).map(item => ({
    name: item.name || '未标注区域',
    count: item.count || 0
  }));
}

Page({
  data: {
    isLoggedIn: false,
    userInfo: {},
    isAdmin: false,
    topAreas: [],
    photos: [],
    photoTotal: 0,
    photoPage: 1,
    photoHasMore: true,
    photoLoading: false,
    unreadCount: 0,
    pendingReportCount: 0,
    pendingAppealCount: 0
  },

  onShow() {
    this.checkLoginStatus();
    if (app.isLoggedIn()) {
      this.loadUserMeta();
      this.loadMyPhotos(true);
      this.loadUnreadCount();
    }
  },

  checkLoginStatus() {
    const isLoggedIn = app.isLoggedIn();
    this.setData({
      isLoggedIn,
      userInfo: app.globalData.userInfo || {},
      isAdmin: isLoggedIn ? this.data.isAdmin : false,
      topAreas: isLoggedIn ? this.data.topAreas : []
    });
  },

  loadUserMeta() {
    request('/user/info', 'GET')
      .then(res => {
        const info = res.data || {};
        this.setData({
          userInfo: Object.assign({}, this.data.userInfo, info),
          isAdmin: !!info.isAdmin
        });
        if (info.isAdmin) {
          this.loadPendingCount();
        }
      })
      .catch(() => {});
  },

  loadPendingCount() {
    request('/admin/report/pending-count', 'GET')
      .then(res => {
        const data = res.data || {};
        this.setData({
          pendingReportCount: data.reportCount || 0,
          pendingAppealCount: data.appealCount || 0
        });
      })
      .catch(() => {});
  },

  loadMyPhotos(refresh) {
    const page = refresh ? 1 : this.data.photoPage;
    this.setData({ photoLoading: true });
    request('/photo/my', 'GET', { page, size: 20 })
      .then(res => {
        const data = res.data || {};
        const user = data.user || {};
        const list = (data.list || []).map(p => {
          if (p.photoDate) p.photoDateLabel = p.photoDate;
          return p;
        });
        this.setData({
          userInfo: Object.assign({}, this.data.userInfo, user),
          topAreas: normalizeTopAreas(user.topAreas),
          photos: refresh ? list : this.data.photos.concat(list),
          photoTotal: data.total || 0,
          photoPage: page + 1,
          photoHasMore: data.hasMore !== false,
          photoLoading: false
        });
      })
      .catch(() => { this.setData({ photoLoading: false }); });
  },

  loadUnreadCount() {
    const p1 = request('/message/unread', 'GET').then(r => (r.data && r.data.count) || 0).catch(() => 0);
    const p2 = request('/notification/unread', 'GET').then(r => (r.data && r.data.count) || 0).catch(() => 0);
    Promise.all([p1, p2]).then(([msg, notif]) => {
      this.setData({ unreadCount: msg + notif });
    });
  },

  onReachBottom() {
    if (this.data.photoHasMore && !this.data.photoLoading && this.data.isLoggedIn) {
      this.loadMyPhotos(false);
    }
  },

  onPhotoTap(e) {
    const id = e.currentTarget.dataset.id;
    wx.navigateTo({ url: '/pages/detail/detail?id=' + id });
  },

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
            this.loadMyPhotos(true);
            this.loadUnreadCount();
          })
          .catch(() => {
            wx.hideLoading();
            wx.showToast({ title: '登录失败', icon: 'none' });
          });
      }
    });
  },

  onGoMessages() {
    wx.navigateTo({ url: '/pages/messages/messages' });
  },

  onGoMyReports() {
    wx.navigateTo({ url: '/pages/my-reports/my-reports' });
  },

  onGoMyViolations() {
    wx.navigateTo({ url: '/pages/my-violations/my-violations' });
  },

  onGoAdminReports() {
    wx.navigateTo({ url: '/pages/admin-reports/admin-reports' });
  },

  onLogout() {
    wx.showModal({
      title: '提示',
      content: '确定退出登录吗？',
      success: (res) => {
        if (res.confirm) {
          app.logout();
          this.setData({ photos: [], photoTotal: 0, unreadCount: 0, topAreas: [], isAdmin: false,
            pendingReportCount: 0, pendingAppealCount: 0 });
          this.checkLoginStatus();
          wx.showToast({ title: '已退出', icon: 'success' });
        }
      }
    });
  }
});
