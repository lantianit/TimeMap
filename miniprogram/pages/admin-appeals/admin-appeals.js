const { request, checkLogin } = require('../../utils/request');

const STATUS_LABELS = { 0: '待处理', 1: '已采纳', 2: '已驳回' };
const TYPE_LABELS = { content_removed: '内容移除申诉', report_rejected: '举报驳回申诉' };
const TABS = [
  { key: '', label: '全部' },
  { key: '0', label: '待处理' },
  { key: '1', label: '已采纳' },
  { key: '2', label: '已驳回' }
];

Page({
  data: {
    tabs: TABS,
    activeStatus: '0',
    appeals: [],
    page: 1,
    hasMore: true,
    loading: false
  },

  onShow() {
    if (!checkLogin()) return;
    this.loadAppeals(true);
  },

  onPullDownRefresh() {
    this.loadAppeals(true).finally(() => wx.stopPullDownRefresh());
  },

  onReachBottom() {
    if (this.data.hasMore && !this.data.loading) this.loadAppeals(false);
  },

  onTabTap(e) {
    const status = e.currentTarget.dataset.status;
    if (status === this.data.activeStatus) return;
    this.setData({ activeStatus: status });
    this.loadAppeals(true);
  },

  loadAppeals(refresh) {
    if (this.data.loading) return Promise.resolve();
    const page = refresh ? 1 : this.data.page;
    const params = { page, size: 20 };
    if (this.data.activeStatus !== '') params.status = Number(this.data.activeStatus);

    this.setData({ loading: true });
    return request('/admin/report/appeals', 'GET', params)
      .then(res => {
        const data = res.data || {};
        const list = (data.list || []).map(item => ({
          ...item,
          statusLabel: STATUS_LABELS[item.status] || '未知',
          typeLabel: TYPE_LABELS[item.type] || '申诉',
          createTimeLabel: (item.createTime || '').replace('T', ' ').substring(0, 16)
        }));
        this.setData({
          appeals: refresh ? list : this.data.appeals.concat(list),
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

  onAppealTap(e) {
    const item = e.currentTarget.dataset.item;
    if (item.status !== 0) return;

    wx.showActionSheet({
      itemList: ['采纳申诉', '驳回申诉'],
      success: (res) => {
        const action = res.tapIndex === 0 ? 'resolve' : 'reject';
        wx.showModal({
          title: action === 'resolve' ? '采纳申诉' : '驳回申诉',
          editable: true,
          placeholderText: action === 'resolve' ? '处理说明（选填）' : '请输入驳回原因',
          success: (mr) => {
            if (!mr.confirm) return;
            const text = (mr.content || '').trim();
            if (action === 'reject' && !text) {
              wx.showToast({ title: '请输入驳回原因', icon: 'none' });
              return;
            }
            const url = action === 'resolve'
              ? '/admin/report/appeal/resolve'
              : '/admin/report/appeal/reject';
            request(url, 'POST', {
              appealId: Number(item.id),
              handleResult: text || '已处理'
            }).then(() => {
              wx.showToast({ title: '操作成功', icon: 'success' });
              this.loadAppeals(true);
            }).catch(err => {
              wx.showToast({ title: (err && err.message) || '操作失败', icon: 'none' });
            });
          }
        });
      }
    });
  }
});
