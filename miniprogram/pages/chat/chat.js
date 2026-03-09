const { request, checkLogin } = require('../../utils/request');
const app = getApp();

Page({
  data: {
    otherUserId: '',
    otherNickname: '',
    otherAvatarUrl: '',
    messages: [],
    inputValue: '',
    page: 1,
    hasMore: true,
    loading: false,
    myUserId: '',
    scrollIntoView: '',
    keyboardHeight: 0
  },

  onLoad(options) {
    if (!checkLogin()) return;
    const userInfo = app.globalData.userInfo || {};
    this.setData({
      otherUserId: options.userId || '',
      otherNickname: decodeURIComponent(options.nickname || ''),
      otherAvatarUrl: decodeURIComponent(options.avatarUrl || ''),
      myUserId: String(userInfo.userId || '')
    });
    wx.setNavigationBarTitle({ title: this.data.otherNickname || '私信' });
    this.loadHistory(true);
    this.markRead();
  },

  onShow() {
    this.markRead();
  },

  loadHistory(refresh) {
    if (this.data.loading) return;
    if (!refresh && !this.data.hasMore) return;
    const page = refresh ? 1 : this.data.page;
    this.setData({ loading: true });

    request('/message/history', 'GET', {
      otherUserId: this.data.otherUserId,
      page: page,
      size: 30
    }).then(res => {
      const list = (res.data || []).map(m => this._formatMsg(m));
      // API 返回倒序（最新在前），需要反转显示
      const reversed = list.reverse();
      const messages = refresh ? reversed : reversed.concat(this.data.messages);
      this.setData({
        messages,
        page: page + 1,
        hasMore: list.length >= 30,
        loading: false
      });
      if (refresh) {
        this._scrollToBottom();
      }
    }).catch(() => {
      this.setData({ loading: false });
    });
  },

  _formatMsg(m) {
    m.isMine = String(m.fromUserId) === this.data.myUserId;
    m.timeLabel = this._formatTime(m.createTime);
    return m;
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
    const h = String(t.getHours()).padStart(2, '0');
    const min = String(t.getMinutes()).padStart(2, '0');
    if (t.getFullYear() === now.getFullYear()) return m + '月' + d + '日 ' + h + ':' + min;
    return t.getFullYear() + '-' + String(m).padStart(2, '0') + '-' + String(d).padStart(2, '0') + ' ' + h + ':' + min;
  },

  _scrollToBottom() {
    setTimeout(() => {
      if (this.data.messages.length) {
        this.setData({ scrollIntoView: 'msg-' + (this.data.messages.length - 1) });
      }
    }, 100);
  },

  markRead() {
    if (this.data.otherUserId) {
      request('/message/read?fromUserId=' + this.data.otherUserId, 'POST').catch(() => {});
    }
  },

  onInputChange(e) {
    this.setData({ inputValue: e.detail.value });
  },

  onKeyboardHeight(e) {
    this.setData({ keyboardHeight: e.detail.height || 0 });
    if (e.detail.height > 0) this._scrollToBottom();
  },

  onSend() {
    const content = this.data.inputValue.trim();
    if (!content) return;

    // 乐观更新
    const userInfo = app.globalData.userInfo || {};
    const tempMsg = {
      id: 'temp_' + Date.now(),
      fromUserId: String(userInfo.userId),
      toUserId: this.data.otherUserId,
      content: content,
      msgType: 'text',
      isMine: true,
      timeLabel: '刚刚',
      fromNickname: userInfo.nickname,
      fromAvatarUrl: userInfo.avatarUrl,
      sending: true
    };

    this.setData({
      messages: [...this.data.messages, tempMsg],
      inputValue: ''
    });
    this._scrollToBottom();

    request('/message/send', 'POST', {
      toUserId: Number(this.data.otherUserId),
      content: content,
      msgType: 'text'
    }).then(res => {
      const real = this._formatMsg(res.data);
      const messages = this.data.messages.map(m => m.id === tempMsg.id ? real : m);
      this.setData({ messages });
    }).catch(() => {
      const messages = this.data.messages.map(m => {
        if (m.id === tempMsg.id) { m.sendFail = true; m.sending = false; }
        return m;
      });
      this.setData({ messages });
      wx.showToast({ title: '发送失败', icon: 'none' });
    });
  },

  onScrollToUpper() {
    if (this.data.hasMore && !this.data.loading) {
      this.loadHistory(false);
    }
  },

  onResend(e) {
    const idx = e.currentTarget.dataset.idx;
    const msg = this.data.messages[idx];
    if (!msg) return;
    // 移除失败消息，重新发送
    const messages = this.data.messages.filter((_, i) => i !== idx);
    this.setData({ messages, inputValue: msg.content });
    this.onSend();
  }
});
