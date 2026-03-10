const { request, checkLogin } = require('../../utils/request');

const STATUS_LABELS = { 0: '待处理', 1: '已处理', 2: '已驳回' };
const TARGET_LABELS = { photo: '照片', comment: '评论', message: '消息' };
const PUNISHMENT_OPTIONS = [
  { key: '', label: '不处罚' },
  { key: 'warning', label: '警告' },
  { key: 'mute', label: '禁言' },
  { key: 'ban_upload', label: '禁止上传' },
  { key: 'ban_account', label: '封禁账号' }
];

Page({
  data: {
    id: '',
    detail: null,
    loading: true,
    handleResult: '',
    submitting: false,
    punishmentOptions: PUNISHMENT_OPTIONS,
    punishmentIndex: 0,
    punishmentDays: '7',
    violations: [],
    violationsLoading: false
  },

  onLoad(options) {
    this.setData({ id: options.id || '' });
  },

  onShow() {
    if (!checkLogin()) return;
    this.loadDetail();
  },

  loadDetail() {
    if (!this.data.id) {
      wx.showToast({ title: '参数错误', icon: 'none' });
      return;
    }
    this.setData({ loading: true });
    request('/admin/report/detail/' + this.data.id, 'GET')
      .then(res => {
        const detail = res.data || {};
        detail.statusLabel = STATUS_LABELS[detail.status] || '未知状态';
        detail.targetLabel = TARGET_LABELS[detail.targetType] || '未知对象';
        detail.createTimeLabel = (detail.createTime || '').replace('T', ' ');
        detail.handledTimeLabel = (detail.handledTime || '').replace('T', ' ');
        this.setData({ detail, loading: false, handleResult: detail.handleResult || '' });

        if (detail.targetOwnerUserId) {
          this.loadViolations(detail.targetOwnerUserId);
        }
      })
      .catch(err => {
        this.setData({ loading: false });
        wx.showToast({ title: err && err.message ? err.message : '加载失败', icon: 'none' });
      });
  },

  loadViolations(userId) {
    this.setData({ violationsLoading: true });
    request('/admin/report/user-violations', 'GET', { targetUserId: userId, page: 1, size: 10 })
      .then(res => {
        this.setData({
          violations: (res.data && res.data.list) || [],
          violationsLoading: false
        });
      })
      .catch(() => { this.setData({ violationsLoading: false }); });
  },

  onResultInput(e) {
    this.setData({ handleResult: e.detail.value || '' });
  },

  onPunishmentChange(e) {
    this.setData({ punishmentIndex: e.detail.value });
  },

  onPunishmentDaysInput(e) {
    this.setData({ punishmentDays: e.detail.value || '' });
  },

  onPreviewImage() {
    const url = this.data.detail && this.data.detail.targetImageUrl;
    if (url) wx.previewImage({ current: url, urls: [url] });
  },

  onGoPhoto() {
    const photoId = this.data.detail && this.data.detail.photoId;
    if (photoId) wx.navigateTo({ url: '/pages/detail/detail?id=' + photoId });
  },

  onResolve() {
    this.submitAction('resolve');
  },

  onReject() {
    this.submitAction('reject');
  },

  submitAction(action) {
    if (this.data.submitting) return;
    const text = (this.data.handleResult || '').trim();
    if (!text) {
      wx.showToast({ title: action === 'resolve' ? '请填写处理结果' : '请填写驳回原因', icon: 'none' });
      return;
    }
    const content = action === 'resolve' ? '确认采纳举报并处理内容吗？' : '确认驳回该举报吗？';
    wx.showModal({
      title: '确认操作',
      content,
      success: (res) => {
        if (!res.confirm) return;
        this.setData({ submitting: true });
        const url = action === 'resolve' ? '/admin/report/resolve' : '/admin/report/reject';

        let body;
        if (action === 'resolve') {
          const pIdx = this.data.punishmentIndex;
          const pType = PUNISHMENT_OPTIONS[pIdx] ? PUNISHMENT_OPTIONS[pIdx].key : '';
          body = {
            reportId: Number(this.data.id),
            action: 'REMOVE_CONTENT',
            handleResult: text,
            punishmentType: pType || null,
            punishmentDays: pType && pType !== 'warning' ? Number(this.data.punishmentDays) || 7 : 0
          };
        } else {
          body = { reportId: Number(this.data.id), handleResult: text };
        }

        request(url, 'POST', body)
          .then(() => {
            wx.showToast({ title: '操作成功', icon: 'success' });
            this.loadDetail();
          })
          .catch(err => {
            wx.showToast({ title: err && err.message ? err.message : '操作失败', icon: 'none' });
          })
          .finally(() => {
            this.setData({ submitting: false });
          });
      }
    });
  },

  onPunishUser() {
    const detail = this.data.detail;
    if (!detail || !detail.targetOwnerUserId) {
      wx.showToast({ title: '无法获取内容作者', icon: 'none' });
      return;
    }
    wx.showActionSheet({
      itemList: ['警告', '禁言7天', '禁言30天', '禁止上传30天', '封禁账号'],
      success: (res) => {
        const configs = [
          { type: 'warning', days: 0 },
          { type: 'mute', days: 7 },
          { type: 'mute', days: 30 },
          { type: 'ban_upload', days: 30 },
          { type: 'ban_account', days: 0 }
        ];
        const cfg = configs[res.tapIndex];
        wx.showModal({
          title: '确认处罚',
          content: '确认对该用户执行此处罚？',
          success: (mr) => {
            if (!mr.confirm) return;
            request('/admin/report/punish', 'POST', {
              userId: Number(detail.targetOwnerUserId),
              reportId: Number(this.data.id),
              punishmentType: cfg.type,
              punishmentDays: cfg.days,
              reason: detail.reason || '违反社区规范'
            }).then(() => {
              wx.showToast({ title: '处罚成功', icon: 'success' });
              this.loadViolations(detail.targetOwnerUserId);
            }).catch(err => {
              wx.showToast({ title: (err && err.message) || '处罚失败', icon: 'none' });
            });
          }
        });
      }
    });
  }
});
