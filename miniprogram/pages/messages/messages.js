const { request, checkLogin } = require('../../utils/request');

Page({
  data: {
    conversations: [],
    loading: false
  },

  onShow() {
    if (!checkLogin()) return;
    this.loadConversations();
  },

  onPullDownRefresh() {
    this.loadConversations().finally(() => {
      wx.stopPullDownRefresh();
    });
  },

  loadConversations() {
    this.setData({ loading: true });
    return request('/message/conversations', 'GET').then(res => {
      const list = (res.data || []).map(c => {
        c.timeLabel = this._formatTime(c.lastTime);
        return c;
      });
      this.setData({ conversations: list, loading: false });
    }).catch(() => {
      this.setData({ loading: false });
    });
  },

  _formatTime(timeStr) {
    if (!timeStr) return '';
    const t = new Date(timeStr.replace('T', ' '));
    const now = new Date();
    const diff = (now - t) / 1000;
    if (diff < 60) return '刚刚';
    if (diff < 3600) return Math.floor(diff / 60) + '分钟前';
    if (diff < 86400) return Math.floor(diff / 3600) + '小时前';
    const m = t.getMonth() + 1;
    const d = t.getDate();
    if (t.getFullYear() === now.getFullYear()) return m + '月' + d + '日';
    return t.getFullYear() + '-' + String(m).padStart(2, '0') + '-' + String(d).padStart(2, '0');
  },

  onConversationTap(e) {
    const item = e.currentTarget.dataset.item;
    wx.navigateTo({
      url: '/pages/chat/chat?userId=' + item.userId +
        '&nickname=' + encodeURIComponent(item.nickname || '') +
        '&avatarUrl=' + encodeURIComponent(item.avatarUrl || '')
    });
  }
});
