const app = getApp();

/**
 * 封装 wx.request，自动携带 Token
 */
function request(url, method, data) {
  return new Promise((resolve, reject) => {
    wx.request({
      url: `${app.globalData.baseUrl}${url}`,
      method: method || 'GET',
      data: data || {},
      header: {
        'Content-Type': 'application/json',
        'Authorization': app.globalData.token ? `Bearer ${app.globalData.token}` : ''
      },
      success(res) {
        if (res.data.code === 200) {
          resolve(res.data);
        } else if (res.data.code === 401) {
          // Token 过期或无效，清除缓存，提示登录
          app.logout();
          wx.showToast({ title: '请先登录', icon: 'none' });
          wx.switchTab({ url: '/pages/profile/profile' });
          reject(res.data);
        } else {
          reject(res.data);
        }
      },
      fail(err) {
        reject(err);
      }
    });
  });
}

/**
 * 检查是否已登录，未登录则跳转到个人中心
 */
function checkLogin() {
  if (!app.isLoggedIn()) {
    wx.showToast({ title: '请先登录', icon: 'none' });
    wx.switchTab({ url: '/pages/profile/profile' });
    return false;
  }
  return true;
}

module.exports = { request, checkLogin };
