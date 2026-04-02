const { request, checkLogin } = require('../../utils/request');
const app = getApp();

Page({
  data: {
    tab: 'following',
    list: [],
    page: 1,
    hasMore: true,
    loading: false,
    followingCount: 0,
    followerCount: 0,
    mutualCount: 0
  },

  onLoad(options) {
    if (options && options.tab) {
      this.setData({ tab: options.tab });
    }
    this.loadCount();
    this.loadList(true);
  },

  onReachBottom() {
    if (this.data.hasMore && !this.data.loading) {
      this.loadList(false);
    }
  },

  onTab(e) {
    const tab = e.currentTarget.dataset.tab;
    if (tab === this.data.tab) return;
    this.setData({ tab, list: [], page: 1, hasMore: true });
    this.loadList(true);
  },

  loadCount() {
    request('/follow/count', 'GET')
      .then(res => {
        const d = res.data || {};
        this.setData({
          followingCount: d.followingCount || 0,
          followerCount: d.followerCount || 0,
          mutualCount: d.mutualCount || 0
        });
      })
      .catch(() => {});
  },

  loadList(refresh) {
    if (this.data.loading) return;
    const page = refresh ? 1 : this.data.page;
    this.setData({ loading: true });

    const url = this.data.tab === 'followers' ? '/follow/followers' : '/follow/following';

    request(url, 'GET', { page, size: 20 })
      .then(res => {
        const d = res.data || {};
        let list = d.list || [];

        // 互关 tab：过滤只显示 mutual=true 的
        if (this.data.tab === 'mutual') {
          list = list.filter(item => item.mutual);
        }

        this.setData({
          list: refresh ? list : this.data.list.concat(list),
          page: page + 1,
          hasMore: d.hasMore !== false,
          loading: false
        });
      })
      .catch(() => {
        this.setData({ loading: false });
      });
  },

  onUserTap(e) {
    const { userId, nickname } = e.currentTarget.dataset;
    wx.navigateTo({
      url: '/pages/user/user?userId=' + userId +
        '&nickname=' + encodeURIComponent(nickname || '')
    });
  },

  onFollowTap(e) {
    if (!checkLogin()) return;
    const { userId, idx } = e.currentTarget.dataset;

    request('/follow/toggle?targetUserId=' + userId, 'POST')
      .then(res => {
        const d = res.data || {};
        const list = this.data.list;
        list[idx].following = d.following;
        list[idx].mutual = d.mutual;
        this.setData({ list });
        this.loadCount();
      })
      .catch(() => {
        wx.showToast({ title: '操作失败', icon: 'none' });
      });
  }
});
