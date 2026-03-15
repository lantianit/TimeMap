App({
  globalData: {
    baseUrl: 'https://maptrace.top/api',
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
      this.globalData.needPhone = !!authState.needPhone;
      this.globalData.needProfile = !!authState.needProfile;
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
            header: { 'Content-Type': 'application/json' },
            success: (resp) => {
              if (resp.data && resp.data.code === 0) {
                const d = resp.data.data;
                this.globalData.token = d.token;
                wx.setStorageSync('token', d.token);
                this.setAuthState({ needPhone: d.needPhone, needProfile: d.needProfile });
                this.setUserInfo({ userId: d.userId });
                resolve(d);
              } else {
                reject(new Error((resp.data && resp.data.message) || '登录失败'));
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
        header: {
          'Content-Type': 'application/json',
          'Authorization': 'Bearer ' + this.globalData.token
        },
        success: (resp) => {
          if (resp.data && resp.data.code === 0) {
            const info = resp.data.data || {};
            this.setUserInfo(info);
            this.setAuthState(info);
            resolve(info);
          } else {
            reject(new Error((resp.data && resp.data.message) || '获取用户信息失败'));
          }
        },
        fail: reject
      });
    });
  },

  logout() {
    this.globalData.token = '';
    this.globalData.userInfo = null;
    this.globalData.needPhone = false;
    this.globalData.needProfile = false;
    wx.removeStorageSync('token');
    wx.removeStorageSync('userInfo');
    wx.removeStorageSync('authState');
  }
});
