const { request, uploadFile } = require('../../utils/request');
const app = getApp();

function normalizeTopAreas(areas) {
  return (areas || []).map(item => ({
    name: item.name || '未标注区域',
    count: item.count || 0
  }));
}

Page({
  data: {
    isLoggedIn: false,
    userInfo: {},
    profileNickname: '',
    avatarPreview: '',
    canSubmit: false,
    savingProfile: false,
    isAdmin: false,
    topAreas: [],
    photos: [],
    photoTotal: 0,
    photoPage: 1,
    photoHasMore: true,
    photoLoading: false,
    unreadCount: 0,
    pendingReportCount: 0,
    pendingAppealCount: 0,
    // 弹窗
    showSetupSheet: false,
    setupStep: 1
  },

  // 本次 onShow 是否已尝试过自动弹窗（防止重复弹）
  _autoSheetTriggered: false,

  onShow() {
    this._autoSheetTriggered = false;
    this._syncLocalState();
    if (app.isLoggedIn()) {
      this.loadUserMeta();
      this.loadMyPhotos(true);
      this.loadUnreadCount();
    }
  },

  // 仅同步本地缓存到 data，不触发弹窗
  _syncLocalState() {
    const isLoggedIn = app.isLoggedIn();
    const ui = app.globalData.userInfo || {};
    this.setData({
      isLoggedIn,
      userInfo: ui,
      profileNickname: ui.nickname || '',
      avatarPreview: ui.avatarUrl || '',
      isAdmin: isLoggedIn ? this.data.isAdmin : false,
      topAreas: isLoggedIn ? this.data.topAreas : []
    });
    this.updateCanSubmit();
  },

  // 判断资料是否真正不完整（只看头像和昵称，手机号是选填）
  _isProfileIncomplete(info) {
    const noAvatar = !info.avatarUrl;
    const noNickname = !info.nickname || info.nickname === '微信用户';
    return noAvatar || noNickname;
  },

  // 根据已有资料智能定位起始步骤
  _calcStartStep(info) {
    if (!info.avatarUrl) return 1;
    if (!info.nickname || info.nickname === '微信用户') return 2;
    return 2; // 都有了，从昵称开始（编辑资料场景）
  },

  // 自动弹窗（仅在 loadUserMeta 拿到真实数据后调用一次）
  _tryAutoShowSheet(info) {
    if (this._autoSheetTriggered) return;
    this._autoSheetTriggered = true;

    if (!this._isProfileIncomplete(info)) return;

    const step = this._calcStartStep(info);
    setTimeout(() => {
      // 再次检查，防止用户在延迟期间已手动操作
      if (!this.data.showSetupSheet) {
        this.setData({ showSetupSheet: true, setupStep: step });
      }
    }, 500);
  },

  updateCanSubmit() {
    const nickname = (this.data.profileNickname || '').trim();
    const avatar = this.data.avatarPreview || '';
    this.setData({ canSubmit: !!nickname && !!avatar });
  },

  // -------- 数据加载 --------

  loadUserMeta() {
    request('/user/info', 'GET')
      .then(res => {
        const info = res.data || {};
        app.setUserInfo(info);
        app.setAuthState(info);
        this.setData({
          userInfo: Object.assign({}, this.data.userInfo, info),
          profileNickname: info.nickname || this.data.profileNickname,
          avatarPreview: info.avatarUrl || this.data.avatarPreview,
          isAdmin: !!info.isAdmin
        });
        this.updateCanSubmit();
        if (info.isAdmin) this.loadPendingCount();

        // 唯一的自动弹窗入口
        this._tryAutoShowSheet(info);
      })
      .catch(() => {});
  },

  loadPendingCount() {
    request('/admin/report/pending-count', 'GET')
      .then(res => {
        const d = res.data || {};
        this.setData({
          pendingReportCount: d.reportCount || 0,
          pendingAppealCount: d.appealCount || 0
        });
      })
      .catch(() => {});
  },

  loadMyPhotos(refresh) {
    const page = refresh ? 1 : this.data.photoPage;
    this.setData({ photoLoading: true });
    request('/photo/my', 'GET', { page, size: 20 })
      .then(res => {
        const data = res.data || {};
        const user = data.user || {};
        const list = (data.list || []).map(p => {
          if (p.photoDate) p.photoDateLabel = p.photoDate;
          return p;
        });
        this.setData({
          userInfo: Object.assign({}, this.data.userInfo, user),
          topAreas: normalizeTopAreas(user.topAreas),
          photos: refresh ? list : this.data.photos.concat(list),
          photoTotal: data.total || 0,
          photoPage: page + 1,
          photoHasMore: data.hasMore !== false,
          photoLoading: false
        });
      })
      .catch(() => this.setData({ photoLoading: false }));
  },

  loadUnreadCount() {
    const p1 = request('/message/unread', 'GET').then(r => (r.data && r.data.count) || 0).catch(() => 0);
    const p2 = request('/notification/unread', 'GET').then(r => (r.data && r.data.count) || 0).catch(() => 0);
    Promise.all([p1, p2]).then(([msg, notif]) => {
      this.setData({ unreadCount: msg + notif });
    });
  },

  onReachBottom() {
    if (this.data.photoHasMore && !this.data.photoLoading && this.data.isLoggedIn) {
      this.loadMyPhotos(false);
    }
  },

  // -------- 登录 --------

  onLogin() {
    wx.showLoading({ title: '登录中...' });
    app.login()
      .then(() => app.syncUserInfo().catch(() => null))
      .then(() => {
        wx.hideLoading();
        this._autoSheetTriggered = false; // 登录后重置，允许弹窗
        this._syncLocalState();
        this.loadUserMeta();
        this.loadMyPhotos(true);
        this.loadUnreadCount();
      })
      .catch(() => {
        wx.hideLoading();
        wx.showToast({ title: '登录失败，请重试', icon: 'none' });
      });
  },

  // -------- 弹窗引导 --------

  noop() {},

  onSheetMaskTap() {
    this.setData({ showSetupSheet: false });
  },

  onSetupNext() {
    const step = this.data.setupStep;
    if (step === 1 && !this.data.avatarPreview) {
      wx.showToast({ title: '请先点击头像选择你的微信头像', icon: 'none' });
      return;
    }
    if (step === 2 && !(this.data.profileNickname || '').trim()) {
      wx.showToast({ title: '请先填写你的昵称', icon: 'none' });
      return;
    }
    if (step === 2) {
      this.onSetupComplete();
      return;
    }
    this.setData({ setupStep: step + 1 });
  },

  onSetupBack() {
    if (this.data.setupStep > 1) {
      this.setData({ setupStep: this.data.setupStep - 1 });
    }
  },

  onStepTap(e) {
    const target = parseInt(e.currentTarget.dataset.step);
    if (target < this.data.setupStep) {
      this.setData({ setupStep: target });
    }
  },

  onSetupComplete() {
    const nickname = (this.data.profileNickname || '').trim();
    const avatarPreview = this.data.avatarPreview || '';
    if (!nickname || !avatarPreview) {
      wx.showToast({ title: '请先设置头像和昵称', icon: 'none' });
      return;
    }

    this.setData({ savingProfile: true });

    // 判断是否为本地临时路径（微信 chooseAvatar 返回 http://tmp/xxx 或 wxfile://xxx）
    const isLocal = /^(http:\/\/tmp|wxfile:\/\/|file:\/\/)/.test(avatarPreview)
      || !/^https:\/\//.test(avatarPreview);
    const avatarTask = isLocal
      ? this._uploadAvatarWithRetry(avatarPreview, 2)
      : Promise.resolve(avatarPreview);

    avatarTask
      .then(avatarUrl => {
        // 二次校验：确保是合法的 https URL
        if (!/^https:\/\/.+\..+/.test(avatarUrl)) {
          throw new Error('头像上传失败，请重试');
        }
        return request('/user/profile', 'POST', { nickname, avatarUrl });
      })
      .then(res => {
        const info = res.data || {};
        app.setUserInfo(info);
        app.setAuthState(info);
        this.setData({
          userInfo: Object.assign({}, this.data.userInfo, info),
          avatarPreview: info.avatarUrl || avatarPreview,
          profileNickname: info.nickname || nickname,
          showSetupSheet: false,
          setupStep: 1
        });
        wx.showToast({ title: '设置成功', icon: 'success' });
        this.loadMyPhotos(true);
        this.loadUnreadCount();
      })
      .catch(err => {
        wx.showToast({ title: (err && err.message) || '保存失败，请重试', icon: 'none' });
      })
      .finally(() => this.setData({ savingProfile: false }));
  },

  /**
   * 带重试的头像上传
   * @param {string} filePath 本地临时文件路径
   * @param {number} retries 剩余重试次数
   */
  _uploadAvatarWithRetry(filePath, retries) {
    return uploadFile('/user/avatar', filePath)
      .then(r => {
        const url = r.data && r.data.avatarUrl;
        if (!url) throw new Error('上传成功但未返回头像地址');
        return url;
      })
      .catch(err => {
        if (retries > 0) {
          return new Promise(resolve => setTimeout(resolve, 1000))
            .then(() => this._uploadAvatarWithRetry(filePath, retries - 1));
        }
        throw err;
      });
  },

  onChooseAvatar(e) {
    const d = (e && e.detail) || {};
    const err = d.errMsg || '';
    if (err && /fail/.test(err)) {
      wx.showToast({ title: '选择失败，请重试', icon: 'none' });
      return;
    }
    const url = d.avatarUrl;
    if (!url) return;
    this.setData({ avatarPreview: url });
    this.updateCanSubmit();
  },

  onNicknameInput(e) {
    this.setData({ profileNickname: e.detail.value || '' });
    this.updateCanSubmit();
  },

  onNicknameBlur(e) {
    this.setData({ profileNickname: e.detail.value || '' });
    this.updateCanSubmit();
  },

  // -------- 正常页面 --------

  onEditProfile() {
    // 用户主动编辑，始终从第1步开始
    this.setData({ showSetupSheet: true, setupStep: 1 });
  },

  onPhotoTap(e) {
    wx.navigateTo({ url: '/pages/detail/detail?id=' + e.currentTarget.dataset.id });
  },

  onGoMessages() { wx.navigateTo({ url: '/pages/messages/messages' }); },
  onGoMyReports() { wx.navigateTo({ url: '/pages/my-reports/my-reports' }); },
  onGoMyViolations() { wx.navigateTo({ url: '/pages/my-violations/my-violations' }); },
  onGoAdminReports() { wx.navigateTo({ url: '/pages/admin-reports/admin-reports' }); },

  onLogout() {
    wx.showModal({
      title: '提示',
      content: '确定退出登录吗？',
      success: (res) => {
        if (res.confirm) {
          app.logout();
          this.setData({
            photos: [], photoTotal: 0, unreadCount: 0, topAreas: [],
            isAdmin: false, pendingReportCount: 0, pendingAppealCount: 0,
            profileNickname: '', avatarPreview: '',
            showSetupSheet: false, setupStep: 1
          });
          this._syncLocalState();
          wx.showToast({ title: '已退出', icon: 'success' });
        }
      }
    });
  }
});
