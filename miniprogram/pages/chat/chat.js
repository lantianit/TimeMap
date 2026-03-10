const { request, checkLogin } = require('../../utils/request');
const app = getApp();

const PAGE_SIZE = 30;
const DEFAULT_TITLE = '私信';
const POLL_INTERVAL = 5000;
const TIME_SEPARATOR_GAP = 5 * 60 * 1000;
const TEMP_MERGE_GAP = 2 * 60 * 1000;

function safeDecode(value) {
  if (!value) return '';
  try {
    return decodeURIComponent(value);
  } catch (err) {
    return value;
  }
}

function pad2(value) {
  const num = Number(value) || 0;
  return num < 10 ? '0' + num : String(num);
}

function parseDateTime(value) {
  if (value === undefined || value === null || value === '') return null;

  if (value instanceof Date) {
    return isNaN(value.getTime()) ? null : value;
  }

  if (typeof value === 'number') {
    const timestamp = value < 1000000000000 ? value * 1000 : value;
    const date = new Date(timestamp);
    return isNaN(date.getTime()) ? null : date;
  }

  const raw = String(value).trim();
  if (!raw) return null;

  const normalized = raw
    .replace(/\.\d+$/, '')
    .replace('T', ' ')
    .replace(/-/g, '/');

  const directDate = new Date(normalized);
  if (!isNaN(directDate.getTime())) {
    return directDate;
  }

  const timestamp = Date.parse(raw);
  if (!isNaN(timestamp)) {
    return new Date(timestamp);
  }

  return null;
}

function getWindowMetrics() {
  try {
    const info = wx.getWindowInfo ? wx.getWindowInfo() : wx.getSystemInfoSync();
    const safeAreaBottom = info.safeArea && info.screenHeight
      ? Math.max(info.screenHeight - info.safeArea.bottom, 0)
      : 0;

    return {
      windowHeight: info.windowHeight || 0,
      windowWidth: info.windowWidth || 375,
      safeAreaBottom
    };
  } catch (err) {
    return {
      windowHeight: 0,
      windowWidth: 375,
      safeAreaBottom: 0
    };
  }
}

function rpxToPx(rpx, windowWidth) {
  return Math.round((Number(rpx) || 0) * (windowWidth || 375) / 750);
}

function getWeekLabel(date) {
  return ['星期日', '星期一', '星期二', '星期三', '星期四', '星期五', '星期六'][date.getDay()];
}

function getMeridiemLabel(hour) {
  if (hour < 6) return '凌晨';
  if (hour < 12) return '上午';
  if (hour < 18) return '下午';
  return '晚上';
}

Page({
  data: {
    otherUserId: '',
    otherNickname: '',
    otherAvatarUrl: '',
    otherAvatarText: '对',
    messages: [],
    renderMessages: [],
    inputValue: '',
    page: 1,
    hasMore: true,
    loading: false,
    myUserId: '',
    scrollIntoView: '',
    keyboardHeight: 0,
    inputFocus: false,
    windowHeight: 0,
    safeAreaBottom: 0,
    inputBarHeight: 0,
    messageListHeight: 0
  },

  onLoad(options) {
    if (!checkLogin()) return;

    const otherUserId = options.userId ? String(options.userId) : '';
    if (!otherUserId) {
      wx.showToast({ title: '会话信息无效', icon: 'none' });
      setTimeout(() => {
        wx.navigateBack({ delta: 1 });
      }, 500);
      return;
    }

    const userInfo = app.globalData.userInfo || {};
    const metrics = getWindowMetrics();
    const inputBarHeight = rpxToPx(112, metrics.windowWidth);
    const otherNickname = safeDecode(options.nickname || '');
    const otherAvatarUrl = safeDecode(options.avatarUrl || '');
    const otherAvatarText = otherNickname ? otherNickname.charAt(0) : '对';

    this._pollTimer = null;
    this._pageVisible = false;
    this._syncingLatest = false;

    this.setData({
      otherUserId,
      otherNickname,
      otherAvatarUrl,
      otherAvatarText,
      myUserId: String(userInfo.userId || ''),
      windowHeight: metrics.windowHeight,
      safeAreaBottom: metrics.safeAreaBottom,
      inputBarHeight,
      messageListHeight: this._calcMessageListHeight({
        windowHeight: metrics.windowHeight,
        keyboardHeight: 0,
        inputBarHeight,
        safeAreaBottom: metrics.safeAreaBottom
      })
    });

    wx.setNavigationBarTitle({ title: otherNickname || DEFAULT_TITLE });
    this.loadHistory(true);
  },

  onShow() {
    this._pageVisible = true;
    this.markRead();
    this.startAutoSync();
    if (this.data.messages.length) {
      this.syncLatestMessages({ silent: true });
    }
  },

  onHide() {
    this._pageVisible = false;
    this.stopAutoSync();
  },

  onUnload() {
    this._pageVisible = false;
    this.stopAutoSync();
  },

  startAutoSync() {
    if (this._pollTimer || !this.data.otherUserId) return;
    this._pollTimer = setInterval(() => {
      if (!this._pageVisible || this.data.loading || this._syncingLatest) return;
      this.syncLatestMessages({ silent: true });
    }, POLL_INTERVAL);
  },

  stopAutoSync() {
    if (this._pollTimer) {
      clearInterval(this._pollTimer);
      this._pollTimer = null;
    }
  },

  loadHistory(refresh) {
    if (this.data.loading) return;
    if (!refresh && !this.data.hasMore) return;

    const page = refresh ? 1 : this.data.page;
    const anchorMessageId = !refresh && this.data.messages.length ? this.data.messages[0].id : '';

    this.setData({ loading: true });

    request('/message/history', 'GET', {
      otherUserId: this.data.otherUserId,
      page: page,
      size: PAGE_SIZE
    }).then(res => {
      const incoming = this._normalizeMessageList((res.data || []).reverse());
      const merged = refresh
        ? this._mergeMessages([], incoming)
        : this._mergeMessages(incoming, this.data.messages);

      this._applyMessages(merged, {
        loading: false,
        page: page + 1,
        hasMore: incoming.length >= PAGE_SIZE,
        anchorMessageId: refresh ? '' : anchorMessageId,
        scrollToBottom: refresh
      });

      if (refresh) {
        this.markRead();
      }
    }).catch(() => {
      this.setData({ loading: false });
    });
  },

  syncLatestMessages(options) {
    if (!this.data.otherUserId || this._syncingLatest) return;

    const opts = options || {};
    const previousTailId = this.data.messages.length ? this.data.messages[this.data.messages.length - 1].id : '';
    this._syncingLatest = true;

    request('/message/history', 'GET', {
      otherUserId: this.data.otherUserId,
      page: 1,
      size: PAGE_SIZE
    }).then(res => {
      const latest = this._normalizeMessageList((res.data || []).reverse());
      const merged = this._mergeMessages(this.data.messages, latest);
      const nextTailId = merged.length ? merged[merged.length - 1].id : '';
      const hasNewTail = nextTailId && nextTailId !== previousTailId;
      const hasIncomingUnread = latest.some(item => !item.isMine && Number(item.readStatus) !== 1);

      this._applyMessages(merged, {
        scrollToBottom: opts.scrollToBottom || hasNewTail
      });

      if (hasIncomingUnread) {
        this.markRead();
      }
    }).catch(() => {
      if (!opts.silent) {
        wx.showToast({ title: '同步失败', icon: 'none' });
      }
    }).finally(() => {
      this._syncingLatest = false;
    });
  },

  _normalizeMessageList(list) {
    return (list || []).map(item => this._formatMsg(item));
  },

  _formatMsg(m) {
    const msg = Object.assign({}, m);
    const messageTime = parseDateTime(msg.createTime) || new Date();

    msg.id = msg.id === undefined || msg.id === null ? 'msg_' + Date.now() : String(msg.id);
    msg.content = msg.content === undefined || msg.content === null ? '' : String(msg.content);
    msg.fromUserId = msg.fromUserId === undefined || msg.fromUserId === null ? '' : String(msg.fromUserId);
    msg.toUserId = msg.toUserId === undefined || msg.toUserId === null ? '' : String(msg.toUserId);
    msg.fromAvatarUrl = msg.fromAvatarUrl || '';
    msg.isMine = msg.fromUserId === this.data.myUserId;
    msg.createTime = messageTime.toISOString();
    msg.timestamp = messageTime.getTime();
    msg.sending = !!msg.sending;
    msg.sendFail = !!msg.sendFail;
    return msg;
  },

  _mergeMessages(baseMessages, incomingMessages) {
    const result = (baseMessages || []).map(item => Object.assign({}, item));
    const incoming = incomingMessages || [];

    for (let i = 0; i < incoming.length; i += 1) {
      const next = Object.assign({}, incoming[i]);
      let merged = false;

      for (let j = 0; j < result.length; j += 1) {
        if (String(result[j].id) === String(next.id)) {
          result[j] = Object.assign({}, result[j], next, {
            sending: false,
            sendFail: false
          });
          merged = true;
          break;
        }
      }

      if (merged) continue;

      for (let k = 0; k < result.length; k += 1) {
        if (this._canReplaceTempMessage(result[k], next)) {
          result[k] = Object.assign({}, result[k], next, {
            sending: false,
            sendFail: false
          });
          merged = true;
          break;
        }
      }

      if (!merged) {
        result.push(next);
      }
    }

    result.sort((a, b) => {
      const diff = (a.timestamp || 0) - (b.timestamp || 0);
      if (diff !== 0) return diff;
      return String(a.id).localeCompare(String(b.id));
    });

    return result;
  },

  _canReplaceTempMessage(current, incoming) {
    if (!current || !incoming) return false;
    if (String(current.id).indexOf('temp_') !== 0) return false;
    if (String(incoming.id).indexOf('temp_') === 0) return false;
    if (current.content !== incoming.content) return false;
    if (String(current.fromUserId) !== String(incoming.fromUserId)) return false;
    if (String(current.toUserId) !== String(incoming.toUserId)) return false;
    return Math.abs((current.timestamp || 0) - (incoming.timestamp || 0)) <= TEMP_MERGE_GAP;
  },

  _buildRenderMessages(messages) {
    const list = [];
    let prev = null;

    for (let i = 0; i < messages.length; i += 1) {
      const item = messages[i];
      if (this._shouldShowTimeDivider(prev, item)) {
        list.push({
          type: 'time',
          renderKey: 'time_' + item.id,
          label: this._formatTimeDivider(item.timestamp)
        });
      }

      list.push(Object.assign({}, item, {
        type: 'message',
        renderKey: 'msg_' + item.id,
        messageId: item.id
      }));

      prev = item;
    }

    return list;
  },

  _shouldShowTimeDivider(prev, current) {
    if (!current || !current.timestamp) return false;
    if (!prev || !prev.timestamp) return true;
    if (!this._isSameDay(prev.timestamp, current.timestamp)) return true;
    return Math.abs(current.timestamp - prev.timestamp) >= TIME_SEPARATOR_GAP;
  },

  _formatTimeDivider(timestamp) {
    const date = new Date(timestamp);
    const now = new Date();
    const timeText = getMeridiemLabel(date.getHours()) + this._formatClock(date);
    const todayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
    const yesterdayStart = todayStart - 24 * 60 * 60 * 1000;
    const weekDiff = now.getDay() === 0 ? 6 : now.getDay() - 1;
    const weekStart = todayStart - weekDiff * 24 * 60 * 60 * 1000;

    if (timestamp >= todayStart) {
      return timeText;
    }

    if (timestamp >= yesterdayStart) {
      return '昨天 ' + timeText;
    }

    if (timestamp >= weekStart && date.getFullYear() === now.getFullYear()) {
      return getWeekLabel(date) + ' ' + timeText;
    }

    if (date.getFullYear() === now.getFullYear()) {
      return (date.getMonth() + 1) + '月' + date.getDate() + '日 ' + timeText;
    }

    return date.getFullYear() + '年' + (date.getMonth() + 1) + '月' + date.getDate() + '日 ' + timeText;
  },

  _formatClock(date) {
    const hours = date.getHours();
    const displayHour = hours % 12 === 0 ? 12 : hours % 12;
    return displayHour + ':' + pad2(date.getMinutes());
  },

  _isSameDay(first, second) {
    const a = new Date(first);
    const b = new Date(second);
    return a.getFullYear() === b.getFullYear() &&
      a.getMonth() === b.getMonth() &&
      a.getDate() === b.getDate();
  },

  _applyMessages(messages, options) {
    const opts = options || {};
    const finalMessages = messages || [];
    const data = {
      messages: finalMessages,
      renderMessages: this._buildRenderMessages(finalMessages)
    };

    if (opts.page !== undefined) data.page = opts.page;
    if (opts.hasMore !== undefined) data.hasMore = opts.hasMore;
    if (opts.loading !== undefined) data.loading = opts.loading;

    this.setData(data, () => {
      if (opts.anchorMessageId) {
        this._scrollToMessage(opts.anchorMessageId);
      } else if (opts.scrollToBottom) {
        this._scrollToBottom();
      }
    });
  },

  _calcMessageListHeight(options) {
    const windowHeight = options.windowHeight || this.data.windowHeight || 0;
    const keyboardHeight = options.keyboardHeight === undefined ? this.data.keyboardHeight : options.keyboardHeight;
    const inputBarHeight = options.inputBarHeight || this.data.inputBarHeight || 0;
    const safeAreaBottom = options.safeAreaBottom === undefined ? this.data.safeAreaBottom : options.safeAreaBottom;
    return Math.max(windowHeight - keyboardHeight - inputBarHeight - safeAreaBottom, 240);
  },

  _setKeyboardHeight(height) {
    const keyboardHeight = Math.max(Number(height) || 0, 0);
    this.setData({
      keyboardHeight,
      messageListHeight: this._calcMessageListHeight({ keyboardHeight })
    }, () => {
      if (keyboardHeight > 0) {
        this._scrollToBottom();
      }
    });
  },

  _scrollToMessage(messageId) {
    if (!messageId) return;
    const targetId = 'msg-' + messageId;
    this.setData({ scrollIntoView: '' });
    const updateTarget = () => {
      this.setData({ scrollIntoView: targetId });
    };

    if (wx.nextTick) {
      wx.nextTick(updateTarget);
      return;
    }

    setTimeout(updateTarget, 60);
  },

  _scrollToBottom() {
    if (!this.data.messages.length) return;
    const last = this.data.messages[this.data.messages.length - 1];
    this._scrollToMessage(last.id);
  },

  markRead() {
    if (this.data.otherUserId) {
      request('/message/read?fromUserId=' + this.data.otherUserId, 'POST').catch(() => {});
    }
  },

  onInputChange(e) {
    this.setData({ inputValue: e.detail.value });
  },

  onInputFocus(e) {
    this.setData({ inputFocus: true });
    if (e.detail && e.detail.height) {
      this._setKeyboardHeight(e.detail.height);
      return;
    }
    this._scrollToBottom();
  },

  onInputBlur() {
    this.setData({ inputFocus: false });
    this._setKeyboardHeight(0);
  },

  onKeyboardHeight(e) {
    this._setKeyboardHeight(e.detail && e.detail.height);
  },

  onSend() {
    this._sendMessage(this.data.inputValue);
  },

  _sendMessage(rawContent) {
    const content = (rawContent || '').trim();
    if (!content || !this.data.otherUserId) return;

    const userInfo = app.globalData.userInfo || {};
    const now = new Date();
    const tempMsg = this._formatMsg({
      id: 'temp_' + Date.now() + '_' + Math.random().toString(36).slice(2, 8),
      fromUserId: String(userInfo.userId || ''),
      toUserId: this.data.otherUserId,
      content: content,
      msgType: 'text',
      readStatus: 0,
      createTime: now.toISOString(),
      fromNickname: userInfo.nickname || '我',
      fromAvatarUrl: userInfo.avatarUrl || '',
      sending: true
    });

    this.setData({ inputValue: '' });
    this._applyMessages(this._mergeMessages(this.data.messages, [tempMsg]), {
      scrollToBottom: true
    });

    request('/message/send', 'POST', {
      toUserId: Number(this.data.otherUserId),
      content: content,
      msgType: 'text'
    }).then(res => {
      const realMessage = this._formatMsg(res.data || {});
      this._applyMessages(this._mergeMessages(this.data.messages, [realMessage]), {
        scrollToBottom: true
      });
    }).catch(() => {
      const messages = this.data.messages.map(item => {
        if (item.id === tempMsg.id) {
          return Object.assign({}, item, {
            sending: false,
            sendFail: true
          });
        }
        return item;
      });
      this._applyMessages(messages);
      wx.showToast({ title: '发送失败', icon: 'none' });
    });
  },

  onScrollToUpper() {
    if (this.data.hasMore && !this.data.loading) {
      this.loadHistory(false);
    }
  },

  onResend(e) {
    const messageId = e.currentTarget.dataset.id;
    const msg = this.data.messages.find(item => item.id === messageId);
    if (!msg) return;

    const nextMessages = this.data.messages.filter(item => item.id !== messageId);
    this._applyMessages(nextMessages, {
      scrollToBottom: true
    });
    this._sendMessage(msg.content);
  },

  onMessageLongPress(e) {
    const messageId = e.currentTarget.dataset.id;
    const isMine = e.currentTarget.dataset.isMine;
    if (isMine) return;

    wx.showActionSheet({
      itemList: ['举报此消息'],
      success: () => {
        const reasons = ['色情低俗', '违法违规', '侵权', '虚假信息', '人身攻击', '其他'];
        wx.showActionSheet({
          itemList: reasons,
          success: (res) => {
            const reason = reasons[res.tapIndex];
            request('/report/submit?targetType=message&targetId=' + messageId
              + '&reason=' + encodeURIComponent(reason), 'POST')
              .then(() => { wx.showToast({ title: '举报已提交', icon: 'success' }); })
              .catch((err) => { wx.showToast({ title: (err && err.message) || '举报失败', icon: 'none' }); });
          }
        });
      }
    });
  }
});
