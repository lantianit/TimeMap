const { parseJsonPreservingBigInts } = require('./utils/jsonSafe');

App({
  globalData: {
    // 自动判断环境：正式版用线上，开发版/体验版用本地
    // 真机调试：用局域网 IP，确保手机能访问；调试完后改回 localhost
    baseUrl: 'http://localhost:8080/api',
    mapKey: 'HXKBZ-VM7L5-E4TII-ITIGH-2TYAV-BJFYS',
    token: '',
    userInfo: null,
    needPhone: false,
    needProfile: false
  },

  onLaunch() {
    const token = wx.getStorageSync('token');
    const authState = wx.getStorageSync('authState') || {};
    if (token) {
      this.globalData.token = token;
      this.globalData.userInfo = wx.getStorageSync('userInfo') || null;
      if (this.globalData.userInfo && this.globalData.userInfo.userId) {
        this.globalData.userInfo.userId = String(this.globalData.userInfo.userId);
      }
      this.globalData.needPhone = !!authState.needPhone;
      this.globalData.needProfile = !!authState.needProfile;
      // 已登录，自动连接 WebSocket
      const ws = require('./utils/websocket');
      ws.connect();
      // 用接口刷新 userId 等，覆盖本地 storage 里可能因 JSON 数字精度错误缓存的 userId
      this.syncUserInfo().catch(() => {});
    }
  },

  isLoggedIn() {
    return !!this.globalData.token;
  },

  needsProfileCompletion() {
    return !!(this.globalData.needPhone || this.globalData.needProfile);
  },

  setAuthState(state) {
    if (state.needPhone !== undefined) this.globalData.needPhone = !!state.needPhone;
    if (state.needProfile !== undefined) this.globalData.needProfile = !!state.needProfile;
    wx.setStorageSync('authState', {
      needPhone: this.globalData.needPhone,
      needProfile: this.globalData.needProfile
    });
  },

  setUserInfo(info) {
    if (info && info.userId !== undefined) info.userId = String(info.userId);
    this.globalData.userInfo = Object.assign({}, this.globalData.userInfo || {}, info || {});
    wx.setStorageSync('userInfo', this.globalData.userInfo);
  },

  login() {
    return new Promise((resolve, reject) => {
      wx.login({
        success: (loginRes) => {
          if (!loginRes.code) {
            reject(new Error('wx.login 失败'));
            return;
          }
          wx.request({
            url: this.globalData.baseUrl + '/auth/login',
            method: 'POST',
            data: { code: loginRes.code },
            dataType: 'text',
            header: { 'Content-Type': 'application/json' },
            success: (resp) => {
              let body;
              try {
                body = typeof resp.data === 'string'
                  ? parseJsonPreservingBigInts(resp.data)
                  : resp.data;
              } catch (e) {
                reject(new Error('登录响应解析失败'));
                return;
              }
              if (body && body.code === 0) {
                const d = body.data;
                this.globalData.token = d.token;
                wx.setStorageSync('token', d.token);
                this.setAuthState({ needPhone: d.needPhone, needProfile: d.needProfile });
                this.setUserInfo({ userId: d.userId });
                // 登录成功，连接 WebSocket
                const ws = require('./utils/websocket');
                ws.connect();
                resolve(d);
              } else {
                reject(new Error((body && body.message) || '登录失败'));
              }
            },
            fail: reject
          });
        },
        fail: reject
      });
    });
  },

  syncUserInfo() {
    return new Promise((resolve, reject) => {
      if (!this.globalData.token) {
        reject(new Error('未登录'));
        return;
      }
      wx.request({
        url: this.globalData.baseUrl + '/user/info',
        method: 'GET',
        dataType: 'text',
        header: {
          'Content-Type': 'application/json',
          'Authorization': 'Bearer ' + this.globalData.token
        },
        success: (resp) => {
          let body;
          try {
            body = typeof resp.data === 'string'
              ? parseJsonPreservingBigInts(resp.data)
              : resp.data;
          } catch (e) {
            reject(new Error('用户信息解析失败'));
            return;
          }
          if (body && body.code === 0) {
            const info = body.data || {};
            this.setUserInfo(info);
            this.setAuthState(info);
            resolve(info);
          } else {
            reject(new Error((body && body.message) || '获取用户信息失败'));
          }
        },
        fail: reject
      });
    });
  },

  logout() {
    // 断开 WebSocket
    const ws = require('./utils/websocket');
    ws.disconnect();
    this.globalData.token = '';
    this.globalData.userInfo = null;
    this.globalData.needPhone = false;
    this.globalData.needProfile = false;
    wx.removeStorageSync('token');
    wx.removeStorageSync('userInfo');
    wx.removeStorageSync('authState');
  }
});
