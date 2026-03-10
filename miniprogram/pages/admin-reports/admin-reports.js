const { request, checkLogin } = require('../../utils/request');

const STATUS_TABS = [
  { key: '', label: '全部' },
  { key: '0', label: '待处理' },
  { key: '1', label: '已处理' },
  { key: '2', label: '已驳回' }
];

const TYPE_TABS = [
  { key: '', label: '全部类型' },
  { key: 'photo', label: '照片' },
  { key: 'comment', label: '评论' },
  { key: 'message', label: '消息' }
];

const TARGET_LABELS = {
  photo: '照片',
  comment: '评论',
  message: '消息'
};

Page({
  data: {
    statusTabs: STATUS_TABS,
    typeTabs: TYPE_TABS,
    activeStatus: '0',
    activeType: '',
    reports: [],
    page: 1,
    hasMore: true,
    loading: false,
    checkedAdmin: false,
    isAdmin: false,
    batchMode: false,
    selectedIds: [],
    batchResult: ''
  },

  onShow() {
    if (!checkLogin()) return;
    this.ensureAdmin().then(ok => {
      if (ok) this.loadReports(true);
    });
  },

  onPullDownRefresh() {
    this.ensureAdmin().then(ok => {
      if (!ok) { wx.stopPullDownRefresh(); return; }
      this.loadReports(true).finally(() => wx.stopPullDownRefresh());
    });
  },

  onReachBottom() {
    if (this.data.isAdmin && this.data.hasMore && !this.data.loading) {
      this.loadReports(false);
    }
  },

  ensureAdmin() {
    if (this.data.checkedAdmin) return Promise.resolve(this.data.isAdmin);
    return request('/user/info', 'GET')
      .then(res => {
        const isAdmin = !!(res.data && res.data.isAdmin);
        this.setData({ checkedAdmin: true, isAdmin });
        if (!isAdmin) wx.showToast({ title: '无管理员权限', icon: 'none' });
        return isAdmin;
      })
      .catch(() => {
        this.setData({ checkedAdmin: true, isAdmin: false });
        wx.showToast({ title: '权限校验失败', icon: 'none' });
        return false;
      });
  },

  onStatusTabTap(e) {
    const status = e.currentTarget.dataset.status;
    if (status === this.data.activeStatus) return;
    this.setData({ activeStatus: status, batchMode: false, selectedIds: [] });
    this.loadReports(true);
  },

  onTypeTabTap(e) {
    const type = e.currentTarget.dataset.type;
    if (type === this.data.activeType) return;
    this.setData({ activeType: type, batchMode: false, selectedIds: [] });
    this.loadReports(true);
  },

  loadReports(refresh) {
    if (this.data.loading) return Promise.resolve();
    const page = refresh ? 1 : this.data.page;
    const params = { page, size: 20 };
    if (this.data.activeStatus !== '') params.status = Number(this.data.activeStatus);
    if (this.data.activeType !== '') params.targetType = this.data.activeType;

    this.setData({ loading: true });
    return request('/admin/report/list', 'GET', params)
      .then(res => {
        const data = res.data || {};
        const list = (data.list || []).map(item => ({
          ...item,
          targetLabel: TARGET_LABELS[item.targetType] || '未知对象',
          createTimeLabel: (item.createTime || '').replace('T', ' '),
          checked: false
        }));
        this.setData({
          reports: refresh ? list : this.data.reports.concat(list),
          page: page + 1,
          hasMore: data.hasMore !== false,
          loading: false
        });
      })
      .catch(err => {
        this.setData({ loading: false });
        wx.showToast({ title: err && err.message ? err.message : '加载失败', icon: 'none' });
      });
  },

  onReportTap(e) {
    if (this.data.batchMode) {
      this.onCheckTap(e);
      return;
    }
    const id = e.currentTarget.dataset.id;
    wx.navigateTo({ url: '/pages/admin-report-detail/admin-report-detail?id=' + id });
  },

  // ========== 批量操作 ==========

  onToggleBatch() {
    this.setData({
      batchMode: !this.data.batchMode,
      selectedIds: [],
      reports: this.data.reports.map(r => ({ ...r, checked: false }))
    });
  },

  onCheckTap(e) {
    const id = e.currentTarget.dataset.id;
    const idx = this.data.reports.findIndex(r => r.id === id);
    if (idx < 0) return;
    const checked = !this.data.reports[idx].checked;
    let selectedIds = [...this.data.selectedIds];
    if (checked) {
      selectedIds.push(id);
    } else {
      selectedIds = selectedIds.filter(x => x !== id);
    }
    this.setData({
      [`reports[${idx}].checked`]: checked,
      selectedIds
    });
  },

  onBatchResultInput(e) {
    this.setData({ batchResult: e.detail.value || '' });
  },

  onBatchResolve() {
    if (!this.data.selectedIds.length) {
      wx.showToast({ title: '请先选择举报', icon: 'none' }); return;
    }
    wx.showModal({
      title: '批量采纳',
      content: '确认采纳选中的 ' + this.data.selectedIds.length + ' 条举报？',
      editable: true,
      placeholderText: '请输入处理结果',
      success: (res) => {
        if (!res.confirm) return;
        const text = (res.content || '').trim();
        if (!text) { wx.showToast({ title: '请输入处理结果', icon: 'none' }); return; }
        request('/admin/report/batch-resolve', 'POST', {
          reportIds: this.data.selectedIds.map(Number),
          handleResult: text
        }).then(() => {
          wx.showToast({ title: '批量采纳成功', icon: 'success' });
          this.setData({ batchMode: false, selectedIds: [] });
          this.loadReports(true);
        }).catch(err => {
          wx.showToast({ title: (err && err.message) || '操作失败', icon: 'none' });
        });
      }
    });
  },

  onBatchReject() {
    if (!this.data.selectedIds.length) {
      wx.showToast({ title: '请先选择举报', icon: 'none' }); return;
    }
    wx.showModal({
      title: '批量驳回',
      content: '确认驳回选中的 ' + this.data.selectedIds.length + ' 条举报？',
      editable: true,
      placeholderText: '请输入驳回原因',
      success: (res) => {
        if (!res.confirm) return;
        const text = (res.content || '').trim();
        if (!text) { wx.showToast({ title: '请输入驳回原因', icon: 'none' }); return; }
        request('/admin/report/batch-reject', 'POST', {
          reportIds: this.data.selectedIds.map(Number),
          handleResult: text
        }).then(() => {
          wx.showToast({ title: '批量驳回成功', icon: 'success' });
          this.setData({ batchMode: false, selectedIds: [] });
          this.loadReports(true);
        }).catch(err => {
          wx.showToast({ title: (err && err.message) || '操作失败', icon: 'none' });
        });
      }
    });
  },

  onGoAggregated() {
    wx.navigateTo({ url: '/pages/admin-aggregated-reports/admin-aggregated-reports' });
  },

  onGoAppeals() {
    wx.navigateTo({ url: '/pages/admin-appeals/admin-appeals' });
  },

  onGoLogs() {
    wx.navigateTo({ url: '/pages/admin-logs/admin-logs' });
  }
});
