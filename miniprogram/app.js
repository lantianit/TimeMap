App({
  globalData: {
    baseUrl: 'http://localhost:8080/api',
    mapKey: 'HXKBZ-VM7L5-E4TII-ITIGH-2TYAV-BJFYS',
    token: '',
    userInfo: null
  },

  onLaunch() {
    // 检查本地缓存的 token
    const token = wx.getStorageSync('token');
    if (token) {
      this.globalData.token = token;
      this.globalData.userInfo = wx.getStorageSync('userInfo') || null;
      console.log('[TimeMap] 使用缓存Token');
    }
  },

  /** 是否已登录 */
  isLoggedIn() {
    return !!this.globalData.token;
  },

  /** 登录（由页面主动调用） */
  login(userProfile) {
    return new Promise((resolve, reject) => {
      wx.login({
        success: (res) => {
          if (!res.code) {
            reject(new Error('wx.login 失败'));
            return;
          }
          wx.request({
            url: `${this.globalData.baseUrl}/auth/login`,
            method: 'POST',
            data: {
              code: res.code,
              nickname: userProfile.nickName,
              avatarUrl: userProfile.avatarUrl,
              gender: userProfile.gender,
              country: userProfile.country,
              province: userProfile.province,
              city: userProfile.city
            },
            header: { 'Content-Type': 'application/json' },
            success: (resp) => {
              if (resp.data.code === 200) {
                const { token, userId } = resp.data.data;
                this.globalData.token = token;
                this.globalData.userInfo = {
                  userId,
                  nickname: userProfile.nickName,
                  avatarUrl: userProfile.avatarUrl
                };
                wx.setStorageSync('token', token);
                wx.setStorageSync('userInfo', this.globalData.userInfo);
                console.log('[TimeMap] 登录成功', resp.data.data);
                resolve(resp.data.data);
              } else {
                reject(new Error(resp.data.message));
              }
            },
            fail: (err) => reject(err)
          });
        },
        fail: (err) => reject(err)
      });
    });
  },

  /** 退出登录 */
  logout() {
    this.globalData.token = '';
    this.globalData.userInfo = null;
    wx.removeStorageSync('token');
    wx.removeStorageSync('userInfo');
  }
});
