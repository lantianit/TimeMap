const { request, checkLogin } = require('../../utils/request');

const ACTION_LABELS = {
  resolve_report: '采纳举报',
  reject_report: '驳回举报',
  punish_user: '处罚用户',
  resolve_appeal: '采纳申诉',
  reject_appeal: '驳回申诉'
};

Page({
  data: {
    logs: [],
    page: 1,
    hasMore: true,
    loading: false
  },

  onShow() {
    if (!checkLogin()) return;
    this.loadLogs(true);
  },

  onPullDownRefresh() {
    this.loadLogs(true).finally(() => wx.stopPullDownRefresh());
  },

  onReachBottom() {
    if (this.data.hasMore && !this.data.loading) this.loadLogs(false);
  },

  loadLogs(refresh) {
    if (this.data.loading) return Promise.resolve();
    const page = refresh ? 1 : this.data.page;
    this.setData({ loading: true });
    return request('/admin/report/logs', 'GET', { page, size: 20 })
      .then(res => {
        const data = res.data || {};
        const list = (data.list || []).map(item => ({
          ...item,
          actionLabel: ACTION_LABELS[item.action] || item.action,
          createTimeLabel: (item.createTime || '').replace('T', ' ').substring(0, 16)
        }));
        this.setData({
          logs: refresh ? list : this.data.logs.concat(list),
          page: page + 1,
          hasMore: data.hasMore !== false,
          loading: false
        });
      })
      .catch(() => {
        this.setData({ loading: false });
        wx.showToast({ title: '加载失败', icon: 'none' });
      });
  }
});
