const { request, checkLogin } = require('../../utils/request');

const TYPE_LABELS = {
  comment: '评论了你的照片',
  reply: '回复了你的评论',
  photo_like: '赞了你的照片',
  comment_like: '赞了你的评论'
};

Page({
  data: {
    activeTab: 'chat',
    // 私信
    conversations: [],
    loading: false,
    chatUnread: 0,
    // 互动
    notifications: [],
    notifLoading: false,
    notifPage: 1,
    notifHasMore: true,
    notifUnread: 0
  },

  onShow() {
    if (!checkLogin()) return;
    if (this.data.activeTab === 'chat') {
      this.loadConversations();
    } else {
      this.loadNotifications(true);
    }
    this.loadUnreadCounts();
  },

  onPullDownRefresh() {
    if (this.data.activeTab === 'chat') {
      this.loadConversations().finally(() => wx.stopPullDownRefresh());
    } else {
      this.loadNotifications(true).finally(() => wx.stopPullDownRefresh());
    }
  },

  onReachBottom() {
    if (this.data.activeTab === 'notification' && this.data.notifHasMore && !this.data.notifLoading) {
      this.loadNotifications(false);
    }
  },

  onTabChat() {
    this.setData({ activeTab: 'chat' });
    this.loadConversations();
  },

  onTabNotification() {
    this.setData({ activeTab: 'notification' });
    // 进入互动 tab 即标记全部已读
    if (this.data.notifUnread > 0) {
      request('/notification/readAll', 'POST').then(() => {
        this.setData({ notifUnread: 0 });
      });
    }
    if (!this.data.notifications.length) {
      this.loadNotifications(true);
    }
  },

  loadUnreadCounts() {
    request('/message/unread', 'GET').then(r => {
      this.setData({ chatUnread: (r.data && r.data.count) || 0 });
    }).catch(() => {});
    request('/notification/unread', 'GET').then(r => {
      this.setData({ notifUnread: (r.data && r.data.count) || 0 });
    }).catch(() => {});
  },

  loadConversations() {
    this.setData({ loading: true });
    return request('/message/conversations', 'GET').then(res => {
      const list = (res.data || []).map(c => {
        c.timeLabel = this._formatTime(c.lastTime);
        return c;
      });
      this.setData({ conversations: list, loading: false });
    }).catch(() => { this.setData({ loading: false }); });
  },

  loadNotifications(refresh) {
    const page = refresh ? 1 : this.data.notifPage;
    this.setData({ notifLoading: true });
    return request('/notification/list', 'GET', { page, size: 20 }).then(res => {
      const list = (res.data || []).map(n => {
        n.typeLabel = TYPE_LABELS[n.type] || '与你互动';
        n.timeLabel = this._formatTime(n.createTime);
        return n;
      });
      this.setData({
        notifications: refresh ? list : this.data.notifications.concat(list),
        notifPage: page + 1,
        notifHasMore: list.length >= 20,
        notifLoading: false
      });
    }).catch(() => { this.setData({ notifLoading: false }); });
  },

  onReadAll() {
    request('/notification/readAll', 'POST').then(() => {
      const notifications = this.data.notifications.map(n => {
        n.isRead = 1;
        return n;
      });
      this.setData({ notifications, notifUnread: 0 });
    });
  },

  onConversationTap(e) {
    const item = e.currentTarget.dataset.item;
    wx.navigateTo({
      url: '/pages/chat/chat?userId=' + item.userId +
        '&nickname=' + encodeURIComponent(item.nickname || '') +
        '&avatarUrl=' + encodeURIComponent(item.avatarUrl || '')
    });
  },

  onNotifTap(e) {
    const item = e.currentTarget.dataset.item;
    if (item.photoId) {
      wx.navigateTo({ url: '/pages/detail/detail?id=' + item.photoId });
    }
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
  }
});
