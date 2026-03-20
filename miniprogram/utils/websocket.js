/**
 * WebSocket 管理器
 * 
 * 功能：自动连接、心跳保活、断线重连、消息分发
 * 使用：
 *   const ws = require('./websocket');
 *   ws.connect();
 *   ws.on('new_message', (data) => { ... });
 *   ws.off('new_message', handler);
 *   ws.disconnect();
 */
const { parseJsonPreservingBigInts } = require('./jsonSafe');

const HEARTBEAT_INTERVAL = 30000; // 30秒心跳
const RECONNECT_DELAY = 3000;     // 3秒后重连
const MAX_RECONNECT = 10;         // 最大重连次数

let socketTask = null;
let heartbeatTimer = null;
let reconnectTimer = null;
let reconnectCount = 0;
let isConnecting = false;
let isConnected = false;
let manualClose = false;

// 事件监听器 { type: [handler, handler, ...] }
const listeners = {};

function getWsUrl() {
  const app = getApp();
  const base = (app && app.globalData && app.globalData.baseUrl) || '';
  const token = (app && app.globalData && app.globalData.token) || '';
  // http://localhost:8080/api -> ws://localhost:8080/ws/chat
  // https://maptrace.top/api -> wss://maptrace.top/ws/chat
  const origin = base.replace(/\/api$/, '').replace(/^http/, 'ws');
  return origin + '/ws/chat?token=' + token;
}

function connect() {
  const app = getApp();
  const token = (app && app.globalData && app.globalData.token) || '';
  if (isConnecting || isConnected || !token) return;

  manualClose = false;
  isConnecting = true;

  const url = getWsUrl();
  console.log('[WS] 连接中...', url.replace(/token=.*/, 'token=***'));

  socketTask = wx.connectSocket({
    url: url,
    success: () => {},
    fail: (err) => {
      console.warn('[WS] 连接失败', err);
      isConnecting = false;
      scheduleReconnect();
    }
  });

  socketTask.onOpen(() => {
    console.log('[WS] 连接成功');
    isConnecting = false;
    isConnected = true;
    reconnectCount = 0;
    startHeartbeat();
    emit('connected');
  });

  socketTask.onMessage((res) => {
    try {
      const msg = typeof res.data === 'string'
        ? parseJsonPreservingBigInts(res.data)
        : res.data;
      if (msg.type === 'pong') return; // 心跳回复，忽略
      console.log('[WS] 收到消息:', msg.type);
      emit(msg.type, msg.data);
    } catch (e) {
      console.warn('[WS] 消息解析失败', e);
    }
  });

  socketTask.onClose(() => {
    console.log('[WS] 连接关闭');
    cleanup();
    if (!manualClose) {
      scheduleReconnect();
    }
    emit('disconnected');
  });

  socketTask.onError((err) => {
    console.warn('[WS] 连接错误', err);
    cleanup();
    if (!manualClose) {
      scheduleReconnect();
    }
  });
}

function disconnect() {
  manualClose = true;
  cleanup();
  if (socketTask) {
    try { socketTask.close(); } catch (e) {}
    socketTask = null;
  }
}

function cleanup() {
  isConnecting = false;
  isConnected = false;
  if (heartbeatTimer) {
    clearInterval(heartbeatTimer);
    heartbeatTimer = null;
  }
}

function startHeartbeat() {
  if (heartbeatTimer) clearInterval(heartbeatTimer);
  heartbeatTimer = setInterval(() => {
    if (isConnected && socketTask) {
      socketTask.send({ data: '{"type":"ping"}', fail: () => {} });
    }
  }, HEARTBEAT_INTERVAL);
}

function scheduleReconnect() {
  if (manualClose || reconnectTimer) return;
  if (reconnectCount >= MAX_RECONNECT) {
    console.warn('[WS] 达到最大重连次数，停止重连');
    return;
  }
  reconnectCount++;
  const delay = RECONNECT_DELAY * Math.min(reconnectCount, 5);
  console.log('[WS] ' + delay + 'ms 后第 ' + reconnectCount + ' 次重连');
  reconnectTimer = setTimeout(() => {
    reconnectTimer = null;
    connect();
  }, delay);
}

function send(data) {
  if (isConnected && socketTask) {
    socketTask.send({
      data: typeof data === 'string' ? data : JSON.stringify(data),
      fail: () => {}
    });
  }
}

// 事件系统
function on(type, handler) {
  if (!listeners[type]) listeners[type] = [];
  listeners[type].push(handler);
}

function off(type, handler) {
  if (!listeners[type]) return;
  if (!handler) {
    delete listeners[type];
    return;
  }
  listeners[type] = listeners[type].filter(h => h !== handler);
}

function emit(type, data) {
  const handlers = listeners[type];
  if (handlers) {
    handlers.forEach(h => {
      try { h(data); } catch (e) { console.warn('[WS] 事件处理异常', type, e); }
    });
  }
}

function getConnected() {
  return isConnected;
}

module.exports = {
  connect,
  disconnect,
  send,
  on,
  off,
  getConnected
};
