const { request, checkLogin } = require('../../utils/request');

const TARGET_LABELS = { photo: '照片', comment: '评论', message: '消息' };

Page({
  data: {
    list: [],
    page: 1,
    loading: false,
    hasMore: true
  },

  onShow() {
    if (!checkLogin()) return;
    this.loadData(true);
  },

  onPullDownRefresh() {
    this.loadData(true).finally(() => wx.stopPullDownRefresh());
  },

  onReachBottom() {
    if (this.data.hasMore && !this.data.loading) this.loadData(false);
  },

  loadData(refresh) {
    if (this.data.loading) return Promise.resolve();
    const page = refresh ? 1 : this.data.page;
    this.setData({ loading: true });
    return request('/admin/report/aggregated', 'GET', { page, size: 20 })
      .then(res => {
        const list = (res.data || []).map(item => ({
          ...item,
          targetLabel: TARGET_LABELS[item.targetType] || '未知',
          reasonText: (item.reasons || []).join('、')
        }));
        this.setData({
          list: refresh ? list : this.data.list.concat(list),
          page: page + 1,
          hasMore: list.length >= 20,
          loading: false
        });
      })
      .catch(() => {
        this.setData({ loading: false });
        wx.showToast({ title: '加载失败', icon: 'none' });
      });
  },

  onItemTap(e) {
    const item = e.currentTarget.dataset.item;
    if (item.reportIds && item.reportIds.length > 0) {
      wx.navigateTo({
        url: '/pages/admin-report-detail/admin-report-detail?id=' + item.reportIds[0]
      });
    }
  },

  onBatchResolve(e) {
    const item = e.currentTarget.dataset.item;
    wx.showModal({
      title: '批量采纳',
      content: '确认采纳该内容的全部 ' + item.reportCount + ' 条举报？',
      editable: true,
      placeholderText: '请输入处理结果',
      success: (res) => {
        if (!res.confirm) return;
        const text = (res.content || '').trim();
        if (!text) { wx.showToast({ title: '请输入处理结果', icon: 'none' }); return; }
        request('/admin/report/batch-resolve', 'POST', {
          reportIds: item.reportIds.map(Number),
          handleResult: text
        }).then(() => {
          wx.showToast({ title: '批量采纳成功', icon: 'success' });
          this.loadData(true);
        }).catch(err => {
          wx.showToast({ title: (err && err.message) || '操作失败', icon: 'none' });
        });
      }
    });
  }
});
