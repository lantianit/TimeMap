const { request, checkLogin } = require('../../utils/request');
const { idsEqual } = require('../../utils/jsonSafe');
const app = getApp();

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
    loading: false,
    isMe: false,
    followStatus: '', // '', 'following', 'mutual'
    followCount: { followingCount: 0, followerCount: 0, mutualCount: 0 }
  },

  onLoad(options) {
    this._userId = options.userId;
    if (options.nickname) {
      wx.setNavigationBarTitle({ title: decodeURIComponent(options.nickname) });
    }
    const myId = app.globalData.userInfo && app.globalData.userInfo.userId;
    this.setData({ isMe: !!(myId && idsEqual(myId, this._userId)) });
    this.loadUserPhotos(true);
    if (!this.data.isMe && app.isLoggedIn()) {
      this.loadFollowStatus();
    }
    this.loadFollowCount();
  },

  onShow() {
    const myId = app.globalData.userInfo && app.globalData.userInfo.userId;
    this.setData({ isMe: !!(myId && idsEqual(myId, this._userId)) });
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
  },

  onSendMessage() {
    if (!checkLogin()) return;
    const user = this.data.user || {};
    wx.navigateTo({
      url: '/pages/chat/chat?userId=' + this._userId +
        '&nickname=' + encodeURIComponent(user.nickname || '') +
        '&avatarUrl=' + encodeURIComponent(user.avatarUrl || '')
    });
  },

  loadFollowStatus() {
    request('/follow/status', 'GET', { targetUserId: this._userId })
      .then(res => {
        const d = res.data || {};
        let status = '';
        if (d.mutual) status = 'mutual';
        else if (d.followed) status = 'following';
        this.setData({ followStatus: status });
      })
      .catch(() => {});
  },

  loadFollowCount() {
    request('/follow/count', 'GET', { targetUserId: this._userId })
      .then(res => {
        this.setData({ followCount: res.data || {} });
      })
      .catch(() => {});
  },

  onFollowTap() {
    if (!checkLogin()) return;
    request('/follow/toggle?targetUserId=' + this._userId, 'POST')
      .then(res => {
        const d = res.data || {};
        let status = '';
        if (d.mutual) status = 'mutual';
        else if (d.followed) status = 'following';
        this.setData({ followStatus: status });
        this.loadFollowCount();
      })
      .catch(() => {
        wx.showToast({ title: '操作失败', icon: 'none' });
      });
  },

  onFollowCountTap() {
    wx.navigateTo({
      url: '/pages/follow-list/follow-list?userId=' + this._userId
    });
  },

  onFootprintTap() {
    const user = this.data.user || {};
    wx.navigateTo({
      url: '/pages/footprint/footprint?userId=' + this._userId +
        '&nickname=' + encodeURIComponent(user.nickname || '') +
        '&avatarUrl=' + encodeURIComponent(user.avatarUrl || '')
    });
  }
});
