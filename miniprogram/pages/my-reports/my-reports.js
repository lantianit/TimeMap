const { request, checkLogin } = require('../../utils/request');

const STATUS_LABELS = {
  0: '待处理',
  1: '已处理',
  2: '已驳回'
};

const TARGET_LABELS = {
  photo: '照片举报',
  comment: '评论举报',
  message: '消息举报'
};

Page({
  data: {
    reports: [],
    page: 1,
    hasMore: true,
    loading: false,
    showAppealDialog: false,
    appealReason: '',
    _appealReportId: null
  },

  onShow() {
    if (!checkLogin()) return;
    this.loadReports(true);
  },

  onPullDownRefresh() {
    this.loadReports(true).finally(() => wx.stopPullDownRefresh());
  },

  onReachBottom() {
    if (this.data.hasMore && !this.data.loading) {
      this.loadReports(false);
    }
  },

  loadReports(refresh) {
    if (this.data.loading) return Promise.resolve();
    const page = refresh ? 1 : this.data.page;
    this.setData({ loading: true });
    return request('/report/my', 'GET', { page, size: 20 })
      .then(res => {
        const data = res.data || {};
        const list = (data.list || []).map(item => ({
          ...item,
          statusLabel: STATUS_LABELS[item.status] || '未知状态',
          targetLabel: TARGET_LABELS[item.targetType] || '举报',
          createTimeLabel: (item.createTime || '').replace('T', ' '),
          handledTimeLabel: (item.handledTime || '').replace('T', ' ')
        }));
        this.setData({
          reports: refresh ? list : this.data.reports.concat(list),
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
      _appealReportId: item.id
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
      type: 'report_rejected',
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
