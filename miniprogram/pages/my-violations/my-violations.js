const { request, checkLogin } = require('../../utils/request');

const VIOLATION_LABELS = {
  content_removed: '内容被移除',
  warning: '警告',
  mute: '禁言',
  ban_upload: '禁止上传',
  ban_account: '账号封禁'
};

const TARGET_LABELS = {
  photo: '照片',
  comment: '评论',
  message: '消息'
};

Page({
  data: {
    violations: [],
    page: 1,
    hasMore: true,
    loading: false,
    showAppealDialog: false,
    appealReason: '',
    _appealReportId: null,
    _appealType: ''
  },

  onShow() {
    if (!checkLogin()) return;
    this.loadViolations(true);
  },

  onPullDownRefresh() {
    this.loadViolations(true).finally(() => wx.stopPullDownRefresh());
  },

  onReachBottom() {
    if (this.data.hasMore && !this.data.loading) {
      this.loadViolations(false);
    }
  },

  loadViolations(refresh) {
    if (this.data.loading) return Promise.resolve();
    const page = refresh ? 1 : this.data.page;
    this.setData({ loading: true });
    return request('/report/my-violations', 'GET', { page, size: 20 })
      .then(res => {
        const data = res.data || {};
        const list = (data.list || []).map(item => ({
          ...item,
          violationLabel: VIOLATION_LABELS[item.violationType] || item.violationType,
          targetLabel: TARGET_LABELS[item.targetType] || '',
          punishmentLabel: item.punishmentType
            ? (VIOLATION_LABELS[item.punishmentType] || item.punishmentType)
              + (item.punishmentDays > 0 ? ' ' + item.punishmentDays + '天' : '')
            : '',
          createTimeLabel: (item.createTime || '').replace('T', ' ').substring(0, 16)
        }));
        this.setData({
          violations: refresh ? list : this.data.violations.concat(list),
          page: page + 1,
          hasMore: data.hasMore !== false,
          loading: false
        });
      })
      .catch(() => {
        this.setData({ loading: false });
        wx.showToast({ title: '加载失败', icon: 'none' });
      });
  },

  onAppeal(e) {
    const item = e.currentTarget.dataset.item;
    this.setData({
      showAppealDialog: true,
      appealReason: '',
      _appealReportId: item.reportId,
      _appealType: 'content_removed'
    });
  },

  onAppealInput(e) {
    this.setData({ appealReason: e.detail.value || '' });
  },

  onAppealCancel() {
    this.setData({ showAppealDialog: false });
  },

  onAppealSubmit() {
    const reason = (this.data.appealReason || '').trim();
    if (!reason) {
      wx.showToast({ title: '请填写申诉原因', icon: 'none' });
      return;
    }
    request('/report/appeal', 'POST', {
      type: this.data._appealType,
      reportId: Number(this.data._appealReportId),
      reason: reason
    }).then(() => {
      this.setData({ showAppealDialog: false });
      wx.showToast({ title: '申诉已提交', icon: 'success' });
    }).catch(err => {
      wx.showToast({ title: (err && err.message) || '提交失败', icon: 'none' });
    });
  }
});
