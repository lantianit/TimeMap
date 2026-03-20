const app = getApp();
const { parseJsonPreservingBigInts } = require('./jsonSafe');

/**
 * 封装 wx.request，自动携带 Token
 */
function request(url, method, data) {
  return new Promise((resolve, reject) => {
    wx.request({
      url: `${app.globalData.baseUrl}${url}`,
      method: method || 'GET',
      data: data || {},
      dataType: 'text',
      header: {
        'Content-Type': 'application/json',
        'Authorization': app.globalData.token ? `Bearer ${app.globalData.token}` : ''
      },
      success(res) {
        let body;
        try {
          body = typeof res.data === 'string'
            ? parseJsonPreservingBigInts(res.data)
            : res.data;
        } catch (e) {
          console.error('[request] JSON 解析失败', url, e);
          reject({ code: -1, message: '响应解析失败' });
          return;
        }
        if (body.code === 0) {
          resolve(body);
        } else if (body.code === 40100) {
          // 未登录状态访问公开接口，静默 reject
          if (!app.globalData.token) {
            reject(body);
            return;
          }
          app.logout();
          wx.showToast({ title: '请先登录', icon: 'none' });
          wx.navigateTo({ url: '/pages/profile/profile' });
          reject(body);
        } else {
          reject(body);
        }
      },
      fail(err) {
        reject(err);
      }
    });
  });
}

/**
 * 封装 wx.uploadFile，自动携带 Token
 */
function uploadFile(url, filePath, formData) {
  return new Promise((resolve, reject) => {
    wx.uploadFile({
      url: `${app.globalData.baseUrl}${url}`,
      filePath: filePath,
      name: 'file',
      formData: formData || {},
      header: {
        'Authorization': app.globalData.token ? `Bearer ${app.globalData.token}` : ''
      },
      success(res) {
        let data;
        try {
          data = parseJsonPreservingBigInts(res.data);
        } catch (e) {
          console.error('[uploadFile] 响应解析失败, statusCode:', res.statusCode, 'data:', res.data);
          reject({ code: -1, message: '服务器响应异常' });
          return;
        }
        if (data.code === 0) {
          resolve(data);
        } else if (data.code === 40100) {
          app.logout();
          wx.showToast({ title: '请先登录', icon: 'none' });
          wx.navigateTo({ url: '/pages/profile/profile' });
          reject(data);
        } else {
          reject(data);
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
    wx.navigateTo({ url: '/pages/profile/profile' });
    return false;
  }
  return true;
}

module.exports = { request, uploadFile, checkLogin };
