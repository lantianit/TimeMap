const { request, checkLogin } = require('../../utils/request');
const { idsEqual } = require('../../utils/jsonSafe');
const ws = require('../../utils/websocket');
const app = getApp();

const PAGE_SIZE = 30;
const DEFAULT_TITLE = '私信';
const FALLBACK_POLL_INTERVAL = 5000;
const TIME_SEPARATOR_GAP = 5 * 60 * 1000;

function safeDecode(value) {
  if (!value) return '';
  try { return decodeURIComponent(value); } catch (e) { return value; }
}

function pad2(v) {
  return (Number(v) || 0) < 10 ? '0' + v : String(v);
}

function parseDateTime(value) {
  if (value == null || value === '') return null;
  if (value instanceof Date) return isNaN(value.getTime()) ? null : value;
  if (typeof value === 'number') {
    var ts = value < 1e12 ? value * 1000 : value;
    var d = new Date(ts);
    return isNaN(d.getTime()) ? null : d;
  }
  var raw = String(value).trim();
  if (!raw) return null;
  // 纯数字字符串当时间戳
  if (/^\d{10,13}$/.test(raw)) {
    var n = Number(raw);
    var ms = n < 1e12 ? n * 1000 : n;
    var dt = new Date(ms);
    return isNaN(dt.getTime()) ? null : dt;
  }
  var norm = raw.replace(/\.\d+Z?$/, '').replace(/Z$/, '').replace('T', ' ').replace(/-/g, '/');
  var dd = new Date(norm);
  return isNaN(dd.getTime()) ? null : dd;
}

function getWindowMetrics() {
  try {
    var info = wx.getWindowInfo ? wx.getWindowInfo() : wx.getSystemInfoSync();
    var sab = info.safeArea && info.screenHeight ? Math.max(info.screenHeight - info.safeArea.bottom, 0) : 0;
    return { windowHeight: info.windowHeight || 0, windowWidth: info.windowWidth || 375, safeAreaBottom: sab };
  } catch (e) {
    return { windowHeight: 0, windowWidth: 375, safeAreaBottom: 0 };
  }
}

function rpxToPx(rpx, w) { return Math.round((Number(rpx) || 0) * (w || 375) / 750); }

var WEEK = ['星期日','星期一','星期二','星期三','星期四','星期五','星期六'];
function getMeridiem(h) {
  if (h < 6) return '凌晨'; if (h < 12) return '上午'; if (h < 18) return '下午'; return '晚上';
}
function formatClock(date) {
  var h = date.getHours(), dh = h % 12 === 0 ? 12 : h % 12;
  return dh + ':' + pad2(date.getMinutes());
}

Page({
  data: {
    otherUserId: '', otherNickname: '', otherAvatarUrl: '', otherAvatarText: '对',
    messages: [], renderMessages: [], inputValue: '',
    page: 1, hasMore: true, loading: false,
    myUserId: '', myAvatarUrl: '',
    scrollIntoView: '', keyboardHeight: 0, inputFocus: false,
    windowHeight: 0, safeAreaBottom: 0, inputBarHeight: 0, messageListHeight: 0
  },

  onLoad(options) {
    if (!checkLogin()) return;
    var otherUserId = options.userId ? String(options.userId) : '';
    if (!otherUserId) {
      wx.showToast({ title: '会话信息无效', icon: 'none' });
      setTimeout(function() { wx.navigateBack({ delta: 1 }); }, 500);
      return;
    }
    var userInfo = app.globalData.userInfo || {};
    var metrics = getWindowMetrics();
    var inputBarHeight = rpxToPx(112, metrics.windowWidth);
    var otherNickname = safeDecode(options.nickname || '');
    var otherAvatarUrl = safeDecode(options.avatarUrl || '');

    this._pollTimer = null;
    this._pageVisible = false;
    this._syncingLatest = false;
    this._wsHandler = null;
    // temp id -> real id 映射，用于轮询去重
    this._tempToRealId = {};

    this.setData({
      otherUserId: otherUserId,
      otherNickname: otherNickname,
      otherAvatarUrl: otherAvatarUrl,
      otherAvatarText: otherNickname ? otherNickname.charAt(0) : '对',
      myUserId: String(userInfo.userId || ''),
      myAvatarUrl: userInfo.avatarUrl || '',
      windowHeight: metrics.windowHeight,
      safeAreaBottom: metrics.safeAreaBottom,
      inputBarHeight: inputBarHeight,
      messageListHeight: this._calcListHeight(metrics.windowHeight, 0, inputBarHeight, metrics.safeAreaBottom)
    });
    wx.setNavigationBarTitle({ title: otherNickname || DEFAULT_TITLE });
    this.loadHistory(true);
  },

  onShow() {
    this._pageVisible = true;
    var ui = app.globalData.userInfo || {};
    var uid = ui.userId != null ? String(ui.userId) : '';
    if (uid && uid !== this.data.myUserId) {
      var self = this;
      var msgs = this.data.messages.map(function(m) {
        var x = Object.assign({}, m);
        x.isMine = idsEqual(x.fromUserId, uid);
        return x;
      });
      this.setData({ myUserId: uid });
      this._render(msgs, false);
    }
    this.markRead();
    this._registerWs();
    this._startPoll();
    if (this.data.messages.length) this.syncLatest();
  },
  onHide() { this._pageVisible = false; this._unregisterWs(); this._stopPoll(); },
  onUnload() { this._pageVisible = false; this._unregisterWs(); this._stopPoll(); },

  // ========== WebSocket ==========
  _registerWs() {
    if (this._wsHandler) return;
    var self = this;
    this._wsHandler = function(msgData) {
      if (!self._pageVisible || !msgData) return;
      var fromId = String(msgData.fromUserId || '');
      var toId = String(msgData.toUserId || '');
      var myId = self.data.myUserId;
      var otherId = self.data.otherUserId;
      if (!((fromId === otherId && toId === myId) || (fromId === myId && toId === otherId))) return;

      var formatted = self._fmtMsg(msgData);
      var msgs = self.data.messages;
      var realId = String(formatted.id);

      // 去重：检查是否已存在
      for (var i = msgs.length - 1; i >= 0; i--) {
        if (String(msgs[i].id) === realId) return; // 已存在，跳过
      }

      // 检查是否能替换 temp 消息
      var tempIdx = self._findTempMatch(msgs, formatted);
      if (tempIdx >= 0) {
        var updated = msgs.slice();
        // 替换 temp，保留本地时间
        updated[tempIdx] = Object.assign({}, updated[tempIdx], { id: realId, sending: false, sendFail: false });
        self._tempToRealId[msgs[tempIdx].id] = realId;
        self._render(updated, true);
      } else {
        // 新消息追加到末尾
        self._render(msgs.concat([formatted]), true);
      }

      if (fromId === otherId) self.markRead();
    };
    ws.on('new_message', this._wsHandler);
  },
  _unregisterWs() {
    if (this._wsHandler) { ws.off('new_message', this._wsHandler); this._wsHandler = null; }
  },

  // ========== 降级轮询 ==========
  _startPoll() {
    if (this._pollTimer || !this.data.otherUserId) return;
    var self = this;
    this._pollTimer = setInterval(function() {
      if (!self._pageVisible || self.data.loading || self._syncingLatest) return;
      if (ws.getConnected()) return;
      self.syncLatest();
    }, FALLBACK_POLL_INTERVAL);
  },
  _stopPoll() {
    if (this._pollTimer) { clearInterval(this._pollTimer); this._pollTimer = null; }
  },

  // ========== 加载历史 ==========
  loadHistory(refresh) {
    if (this.data.loading) return;
    if (!refresh && !this.data.hasMore) return;
    var page = refresh ? 1 : this.data.page;
    var anchorId = !refresh && this.data.messages.length ? this.data.messages[0].id : '';
    this.setData({ loading: true });
    var self = this;
    request('/message/history', 'GET', {
      otherUserId: this.data.otherUserId, page: page, size: PAGE_SIZE
    }).then(function(res) {
      var incoming = (res.data || []).reverse().map(function(m) { return self._fmtMsg(m); });
      var merged = refresh ? incoming : self._merge(incoming, self.data.messages);
      self._render(merged, refresh, {
        loading: false, page: page + 1, hasMore: incoming.length >= PAGE_SIZE,
        anchorId: refresh ? '' : anchorId
      });
      if (refresh) self.markRead();
    }).catch(function() { self.setData({ loading: false }); });
  },

  // ========== 同步最新 ==========
  syncLatest() {
    if (!this.data.otherUserId || this._syncingLatest) return;
    this._syncingLatest = true;
    var self = this;
    var prevTailId = this.data.messages.length ? this.data.messages[this.data.messages.length - 1].id : '';

    request('/message/history', 'GET', {
      otherUserId: this.data.otherUserId, page: 1, size: PAGE_SIZE
    }).then(function(res) {
      var latest = (res.data || []).reverse().map(function(m) { return self._fmtMsg(m); });
      var merged = self._merge(self.data.messages, latest);
      var newTailId = merged.length ? merged[merged.length - 1].id : '';
      var hasNew = newTailId && newTailId !== prevTailId;
      self._render(merged, hasNew);
      if (latest.some(function(m) { return !m.isMine && Number(m.readStatus) !== 1; })) {
        self.markRead();
      }
    }).catch(function() {}).finally(function() { self._syncingLatest = false; });
  },

  // ========== 核心：格式化消息 ==========
  _fmtMsg(m) {
    var msg = Object.assign({}, m);
    var t = parseDateTime(msg.createTime) || new Date();
    msg.id = msg.id == null ? 'msg_' + Date.now() : String(msg.id);
    msg.content = msg.content == null ? '' : String(msg.content);
    msg.fromUserId = msg.fromUserId == null ? '' : String(msg.fromUserId);
    msg.toUserId = msg.toUserId == null ? '' : String(msg.toUserId);
    msg.fromAvatarUrl = msg.fromAvatarUrl || '';
    msg.isMine = idsEqual(msg.fromUserId, this.data.myUserId);
    msg.timestamp = t.getTime();
    msg.sending = !!msg.sending;
    msg.sendFail = !!msg.sendFail;
    return msg;
  },

  // ========== 核心：合并消息（简单可靠） ==========
  _merge(base, incoming) {
    // 用 id 做去重，同时处理 temp->real 映射
    var map = {};
    var result = [];
    var realIds = this._tempToRealId || {};

    // 先放 base
    for (var i = 0; i < base.length; i++) {
      var b = Object.assign({}, base[i]);
      map[String(b.id)] = true;
      result.push(b);
    }

    // 再放 incoming，跳过已存在的
    for (var j = 0; j < incoming.length; j++) {
      var inc = Object.assign({}, incoming[j]);
      var incId = String(inc.id);
      // 已存在（精确 id 匹配）
      if (map[incId]) {
        // 更新非时间字段（保留本地时间）
        for (var k = 0; k < result.length; k++) {
          if (String(result[k].id) === incId) {
            result[k].readStatus = inc.readStatus;
            result[k].sending = false;
            result[k].sendFail = false;
            break;
          }
        }
        continue;
      }
      // 检查是否是 temp 消息对应的真实消息
      var isTempReal = false;
      for (var tid in realIds) {
        if (realIds[tid] === incId) { isTempReal = true; break; }
      }
      if (isTempReal) continue; // 已经通过 temp 替换过了

      // 检查是否能替换某个 temp 消息
      var tempIdx = this._findTempMatch(result, inc);
      if (tempIdx >= 0) {
        realIds[result[tempIdx].id] = incId;
        result[tempIdx] = Object.assign({}, result[tempIdx], { id: incId, sending: false, sendFail: false });
        map[incId] = true;
        continue;
      }

      // 全新消息
      map[incId] = true;
      result.push(inc);
    }

    // 按时间排序
    result.sort(function(a, b) {
      var diff = (a.timestamp || 0) - (b.timestamp || 0);
      return diff !== 0 ? diff : String(a.id).localeCompare(String(b.id));
    });
    return result;
  },

  _findTempMatch(msgs, incoming) {
    if (String(incoming.id).indexOf('temp_') === 0) return -1;
    for (var i = msgs.length - 1; i >= 0; i--) {
      var m = msgs[i];
      if (String(m.id).indexOf('temp_') !== 0) continue;
      if (m.content === incoming.content &&
          String(m.fromUserId) === String(incoming.fromUserId) &&
          String(m.toUserId) === String(incoming.toUserId)) {
        return i;
      }
    }
    return -1;
  },

  // ========== 渲染 ==========
  _render(messages, scrollToBottom, extra) {
    var list = [];
    var prev = null;
    for (var i = 0; i < messages.length; i++) {
      var item = messages[i];
      if (this._needTimeDivider(prev, item)) {
        list.push({ type: 'time', renderKey: 'time_' + i, label: this._fmtTime(item.timestamp) });
      }
      list.push(Object.assign({}, item, { type: 'message', renderKey: 'msg_' + item.id, messageId: item.id }));
      prev = item;
    }
    var data = { messages: messages, renderMessages: list };
    if (extra) {
      if (extra.page !== undefined) data.page = extra.page;
      if (extra.hasMore !== undefined) data.hasMore = extra.hasMore;
      if (extra.loading !== undefined) data.loading = extra.loading;
    }
    var self = this;
    this.setData(data, function() {
      if (extra && extra.anchorId) {
        self._scrollTo(extra.anchorId);
      } else if (scrollToBottom) {
        self._scrollEnd();
      }
    });
  },

  _needTimeDivider(prev, cur) {
    if (!cur || !cur.timestamp) return false;
    if (!prev || !prev.timestamp) return true;
    var a = new Date(prev.timestamp), b = new Date(cur.timestamp);
    if (a.getFullYear() !== b.getFullYear() || a.getMonth() !== b.getMonth() || a.getDate() !== b.getDate()) return true;
    return Math.abs(cur.timestamp - prev.timestamp) >= TIME_SEPARATOR_GAP;
  },

  _fmtTime(ts) {
    var date = new Date(ts), now = new Date();
    var timeText = getMeridiem(date.getHours()) + formatClock(date);
    var todayStart = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime();
    if (ts >= todayStart) return timeText;
    if (ts >= todayStart - 86400000) return '昨天 ' + timeText;
    var wd = now.getDay() === 0 ? 6 : now.getDay() - 1;
    var weekStart = todayStart - wd * 86400000;
    if (ts >= weekStart && date.getFullYear() === now.getFullYear()) return WEEK[date.getDay()] + ' ' + timeText;
    if (date.getFullYear() === now.getFullYear()) return (date.getMonth()+1) + '月' + date.getDate() + '日 ' + timeText;
    return date.getFullYear() + '年' + (date.getMonth()+1) + '月' + date.getDate() + '日 ' + timeText;
  },

  _calcListHeight(wh, kh, ibh, sab) {
    return Math.max((wh||this.data.windowHeight) - (kh===undefined?this.data.keyboardHeight:kh) - (ibh||this.data.inputBarHeight) - (sab===undefined?this.data.safeAreaBottom:sab), 240);
  },

  _scrollTo(id) {
    if (!id) return;
    var target = 'msg-' + id;
    this.setData({ scrollIntoView: '' });
    var self = this;
    if (wx.nextTick) { wx.nextTick(function() { self.setData({ scrollIntoView: target }); }); }
    else { setTimeout(function() { self.setData({ scrollIntoView: target }); }, 60); }
  },
  _scrollEnd() {
    if (!this.data.messages.length) return;
    this._scrollTo(this.data.messages[this.data.messages.length - 1].id);
  },

  markRead() {
    if (this.data.otherUserId) {
      request('/message/read?fromUserId=' + this.data.otherUserId, 'POST').catch(function(){});
    }
  },

  // ========== 输入和发送 ==========
  onInputChange(e) { this.setData({ inputValue: e.detail.value }); },
  onInputFocus(e) {
    this.setData({ inputFocus: true });
    if (e.detail && e.detail.height) { this._setKbHeight(e.detail.height); return; }
    this._scrollEnd();
  },
  onInputBlur() { this.setData({ inputFocus: false }); this._setKbHeight(0); },
  onKeyboardHeight(e) { this._setKbHeight(e.detail && e.detail.height); },
  _setKbHeight(h) {
    var kh = Math.max(Number(h) || 0, 0);
    var self = this;
    this.setData({ keyboardHeight: kh, messageListHeight: this._calcListHeight(undefined, kh) }, function() {
      if (kh > 0) self._scrollEnd();
    });
  },

  onSend() { this._doSend(this.data.inputValue); },

  _doSend(rawContent) {
    var content = (rawContent || '').trim();
    if (!content || !this.data.otherUserId) return;

    var userInfo = app.globalData.userInfo || {};
    var now = new Date();
    var tempId = 'temp_' + Date.now() + '_' + Math.random().toString(36).slice(2, 8);
    var tempMsg = this._fmtMsg({
      id: tempId,
      fromUserId: String(userInfo.userId || ''),
      toUserId: this.data.otherUserId,
      content: content, msgType: 'text', readStatus: 0,
      createTime: Date.now(),
      fromNickname: userInfo.nickname || '我',
      fromAvatarUrl: userInfo.avatarUrl || '',
      sending: false
    });

    this.setData({ inputValue: '' });
    // 追加并渲染
    this._render(this.data.messages.concat([tempMsg]), true);

    var self = this;
    request('/message/send', 'POST', {
      toUserId: this.data.otherUserId, content: content, msgType: 'text'
    }).then(function(res) {
      var real = self._fmtMsg(res.data || {});
      var realId = String(real.id);
      // 记录映射
      self._tempToRealId[tempId] = realId;
      // 替换 temp id，保留本地时间
      var msgs = self.data.messages.map(function(m) {
        if (m.id === tempId) {
          return Object.assign({}, m, { id: realId, sending: false, sendFail: false });
        }
        return m;
      });
      self._render(msgs, false);
    }).catch(function(err) {
      console.error('[Chat] 发送失败:', JSON.stringify(err));
      var msgs = self.data.messages.map(function(m) {
        if (m.id === tempId) return Object.assign({}, m, { sending: false, sendFail: true });
        return m;
      });
      self._render(msgs, false);
      wx.showToast({ title: '发送失败', icon: 'none' });
    });
  },

  onScrollToUpper() {
    if (this.data.hasMore && !this.data.loading) this.loadHistory(false);
  },

  onResend(e) {
    var id = e.currentTarget.dataset.id;
    var msg = this.data.messages.find(function(m) { return m.id === id; });
    if (!msg) return;
    this._render(this.data.messages.filter(function(m) { return m.id !== id; }), true);
    this._doSend(msg.content);
  },

  onMessageLongPress(e) {
    var messageId = e.currentTarget.dataset.id;
    var isMine = e.currentTarget.dataset.isMine;
    if (isMine) return;
    wx.showActionSheet({
      itemList: ['举报此消息'],
      success: function() {
        var reasons = ['色情低俗','违法违规','侵权','虚假信息','人身攻击','其他'];
        wx.showActionSheet({
          itemList: reasons,
          success: function(res) {
            request('/report/submit?targetType=message&targetId=' + messageId
              + '&reason=' + encodeURIComponent(reasons[res.tapIndex]), 'POST')
              .then(function() { wx.showToast({ title: '举报已提交', icon: 'success' }); })
              .catch(function(err) { wx.showToast({ title: (err && err.message) || '举报失败', icon: 'none' }); });
          }
        });
      }
    });
  }
});
