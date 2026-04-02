const { request, checkLogin } = require('../../utils/request');
const { idsEqual } = require('../../utils/jsonSafe');
const app = getApp();

const REPORT_REASONS = ['色情低俗', '违法违规', '侵权', '虚假信息', '人身攻击', '其他'];

Page({
  data: {
    photos: [],
    current: 0,
    total: 0,
    photo: {},
    photoHeights: [],
    swiperHeight: 560,
    comments: [],
    commentTotal: 0,
    commentPage: 1,
    commentHasMore: true,
    commentLoading: false,
    inputValue: '',
    inputFocus: false,
    replyMode: false,
    replyPlaceholder: '说点什么...',
    replyParentId: 0,
    replyToUserId: 0,
    keyboardHeight: 0,
    myAvatarUrl: '',
    showReportDesc: false,
    reportDescValue: '',
    _pendingReportType: '',
    _pendingReportId: '',
    _pendingReportReason: '',
    todayDate: ''
  },

  onLoad(options) {
    this._photoId = null;
    const ui = app.globalData.userInfo || {};
    const now = new Date();
    const todayDate = now.getFullYear() + '-' + String(now.getMonth() + 1).padStart(2, '0') + '-' + String(now.getDate()).padStart(2, '0');
    this.setData({ myAvatarUrl: ui.avatarUrl || '', todayDate });
    if (options.ids) {
      this.loadBatch(options.ids);
    } else if (options.id) {
      this._photoId = options.id;
      this.loadDetail(options.id);
    }
  },

  onShow() {
    // 冷启动后 syncUserInfo 异步完成时，刷新「是否本人」以免缓存的错 userId 导致删除按钮不显示
    if (!this.data.photos || !this.data.photos.length) return;
    const photos = this.data.photos.map((p) => this._formatPhoto({ ...p }));
    const cur = this.data.current || 0;
    this.setData({ photos, photo: photos[cur] || photos[0] });
  },

  loadBatch(ids) {
    request('/photo/batch', 'GET', { ids })
      .then((res) => {
        const photos = (res.data || []).map(p => this._formatPhoto(p));
        if (!photos.length) { wx.showToast({ title: '照片不存在', icon: 'none' }); return; }
        this._photoId = photos[0].id;
        this.setData({ photos, total: photos.length, current: 0, photo: photos[0], photoHeights: [], swiperHeight: 560 });
        this.loadComments(true);
      })
      .catch(() => {
        wx.showToast({ title: '加载失败', icon: 'none' });
      });
  },

  loadDetail(id) {
    request('/photo/detail/' + id, 'GET')
      .then((res) => {
        const photo = this._formatPhoto(res.data || {});
        this.setData({ photos: [photo], total: 1, current: 0, photo, photoHeights: [], swiperHeight: 560 });
        this.loadComments(true);
      })
      .catch(() => {
        wx.showToast({ title: '加载失败', icon: 'none' });
      });
  },

  _formatPhoto(photo) {
    if (photo.createTime) {
      photo.createTimeFormatted = photo.createTime.replace('T', ' ').substring(0, 10);
    }
    if (photo.likeCount === undefined || photo.likeCount === null) photo.likeCount = 0;
    if (photo.liked === undefined || photo.liked === null) photo.liked = false;
    const myId = app.globalData.userInfo && app.globalData.userInfo.userId;
    photo.isOwner = !!(myId && idsEqual(photo.userId, myId));
    return photo;
  },

  // ========== 评论 ==========

  loadComments(refresh) {
    if (this.data.commentLoading) return;
    const page = refresh ? 1 : this.data.commentPage;
    this.setData({ commentLoading: true });

    request('/comment/list', 'GET', {
      photoId: this._photoId,
      page: page,
      size: 20
    }).then(res => {
      const data = res.data || {};
      const list = (data.list || []).map(c => this._formatComment(c));
      this.setData({
        comments: refresh ? list : this.data.comments.concat(list),
        commentTotal: data.total || 0,
        commentPage: page + 1,
        commentHasMore: data.hasMore !== false,
        commentLoading: false
      });
    }).catch(() => {
      this.setData({ commentLoading: false });
    });
  },

  onReachBottom() {
    if (this.data.commentHasMore && !this.data.commentLoading) {
      this.loadComments(false);
    }
  },

  _formatComment(c) {
    c.timeLabel = this._formatTime(c.createTime);
    c.showReplies = false;
    c.replies = c.replies || [];
    return c;
  },

  _formatTime(timeStr) {
    if (!timeStr) return '';
    const t = new Date(timeStr.replace('T', ' '));
    const now = new Date();
    const diff = (now - t) / 1000;
    if (diff < 60) return '刚刚';
    if (diff < 3600) return Math.floor(diff / 60) + '分钟前';
    if (diff < 86400) return Math.floor(diff / 3600) + '小时前';
    if (diff < 172800) return '昨天';
    const m = t.getMonth() + 1;
    const d = t.getDate();
    if (t.getFullYear() === now.getFullYear()) return m + '月' + d + '日';
    return t.getFullYear() + '-' + String(m).padStart(2, '0') + '-' + String(d).padStart(2, '0');
  },

  // ========== 发表评论 ==========

  onInputFocus() {
    if (!checkLogin()) return;
    this.setData({ inputFocus: true });
  },

  onInputBlur() {
    setTimeout(() => {
      this.setData({ inputFocus: false, replyMode: false, replyPlaceholder: '说点什么...' });
    }, 150);
  },

  onInputChange(e) {
    this.setData({ inputValue: e.detail.value });
  },

  onKeyboardHeight(e) {
    this.setData({ keyboardHeight: e.detail.height || 0 });
  },

  onSendComment() {
    const content = this.data.inputValue.trim();
    if (!content) return;
    if (!checkLogin()) return;
    if (this._sendingComment) return;
    this._sendingComment = true;

    const body = {
      photoId: this._photoId,
      content: content,
      parentId: this.data.replyParentId || 0,
      replyToUserId: this.data.replyToUserId || 0
    };

    const userInfo = app.globalData.userInfo || {};
    const optimistic = {
      id: 'temp_' + Date.now(),
      userId: userInfo.userId,
      nickname: userInfo.nickname || '我',
      avatarUrl: userInfo.avatarUrl || '',
      content: content,
      likeCount: 0,
      liked: false,
      replyCount: 0,
      timeLabel: '刚刚',
      replies: [],
      showReplies: false
    };

    if (body.parentId === 0) {
      this.setData({
        comments: [optimistic, ...this.data.comments],
        commentTotal: this.data.commentTotal + 1,
        inputValue: '',
        replyMode: false,
        replyPlaceholder: '说点什么...',
        replyParentId: 0,
        replyToUserId: 0
      });
    } else {
      this.setData({
        inputValue: '',
        replyMode: false,
        replyPlaceholder: '说点什么...',
        replyParentId: 0,
        replyToUserId: 0
      });
    }

    request('/comment/add', 'POST', body).then(res => {
      this._sendingComment = false;
      if (body.parentId === 0) {
        const real = this._formatComment(res.data);
        const comments = this.data.comments.map(c => c.id === optimistic.id ? real : c);
        this.setData({ comments });
      } else {
        this._refreshReplies(body.parentId);
        const comments = this.data.comments.map(c => {
          if (String(c.id) === String(body.parentId)) {
            c.replyCount = (c.replyCount || 0) + 1;
          }
          return c;
        });
        this.setData({ comments });
      }
    }).catch(() => {
      this._sendingComment = false;
      if (body.parentId === 0) {
        const comments = this.data.comments.filter(c => c.id !== optimistic.id);
        this.setData({ comments, commentTotal: this.data.commentTotal - 1 });
      }
      wx.showToast({ title: '发送失败', icon: 'none' });
    });
  },

  // ========== 回复 ==========

  onReplyTap(e) {
    if (!checkLogin()) return;
    const { id, nickname, parentId } = e.currentTarget.dataset;
    const actualParent = parentId && parentId !== '0' ? parentId : id;
    this.setData({
      replyMode: true,
      replyPlaceholder: '回复 @' + (nickname || '用户'),
      replyParentId: actualParent,
      replyToUserId: e.currentTarget.dataset.userId || 0,
      inputFocus: true
    });
  },

  // ========== 展开回复 ==========

  onToggleReplies(e) {
    const idx = e.currentTarget.dataset.idx;
    const comment = this.data.comments[idx];
    if (comment.showReplies) {
      this.setData({ ['comments[' + idx + '].showReplies']: false });
      return;
    }
    this._loadReplies(idx, comment.id);
  },

  _loadReplies(idx, commentId) {
    request('/comment/replies', 'GET', { commentId, page: 1, size: 50 })
      .then(res => {
        const replies = (res.data.list || []).map(c => this._formatComment(c));
        this.setData({
          ['comments[' + idx + '].replies']: replies,
          ['comments[' + idx + '].showReplies']: true
        });
      });
  },

  _refreshReplies(parentId) {
    const idx = this.data.comments.findIndex(c => String(c.id) === String(parentId));
    if (idx >= 0 && this.data.comments[idx].showReplies) {
      this._loadReplies(idx, parentId);
    }
  },

  // ========== 点赞 ==========

  onLikeTap(e) {
    if (!checkLogin()) return;
    const { id, idx, replyIdx } = e.currentTarget.dataset;

    let path, comment;
    if (replyIdx !== undefined && replyIdx !== '') {
      path = 'comments[' + idx + '].replies[' + replyIdx + ']';
      comment = this.data.comments[idx].replies[replyIdx];
    } else {
      path = 'comments[' + idx + ']';
      comment = this.data.comments[idx];
    }

    const newLiked = !comment.liked;
    this.setData({
      [path + '.liked']: newLiked,
      [path + '.likeCount']: comment.likeCount + (newLiked ? 1 : -1)
    });

    request('/comment/like?commentId=' + id, 'POST').catch(() => {
      this.setData({
        [path + '.liked']: comment.liked,
        [path + '.likeCount']: comment.likeCount
      });
    });
  },

  // ========== 删除/举报评论 ==========

  onCommentLongPress(e) {
    if (!checkLogin()) return;
    const { id, userId, idx, replyIdx } = e.currentTarget.dataset;
    const myId = app.globalData.userInfo && app.globalData.userInfo.userId;
    const isOwner = idsEqual(userId, myId);
    const itemList = isOwner ? ['删除'] : ['举报'];

    wx.showActionSheet({
      itemList,
      success: (res) => {
        if (res.tapIndex !== 0) return;
        if (isOwner) {
          request('/comment/delete?commentId=' + id, 'POST').then(() => {
            if (replyIdx !== undefined && replyIdx !== '') {
              const replies = this.data.comments[idx].replies.filter(c => String(c.id) !== String(id));
              const parentReplyCount = Math.max(0, (this.data.comments[idx].replyCount || 0) - 1);
              this.setData({
                ['comments[' + idx + '].replies']: replies,
                ['comments[' + idx + '].replyCount']: parentReplyCount,
                commentTotal: Math.max(0, this.data.commentTotal - 1)
              });
            } else {
              const comments = this.data.comments.filter(c => String(c.id) !== String(id));
              this.setData({ comments, commentTotal: Math.max(0, this.data.commentTotal - 1) });
            }
          }).catch(() => wx.showToast({ title: '删除失败', icon: 'none' }));
          return;
        }

        this._showReportDialog('comment', id);
      }
    });
  },

  // ========== 1.3 + 1.4: 统一举报流程（选原因 → 补充描述 → 提交） ==========

  _showReportDialog(targetType, targetId) {
    wx.showActionSheet({
      itemList: REPORT_REASONS,
      success: (res) => {
        const reason = REPORT_REASONS[res.tapIndex];
        this.setData({
          showReportDesc: true,
          reportDescValue: '',
          _pendingReportType: targetType,
          _pendingReportId: targetId,
          _pendingReportReason: reason
        });
      }
    });
  },

  onReportDescInput(e) {
    this.setData({ reportDescValue: e.detail.value || '' });
  },

  onReportDescCancel() {
    this.setData({ showReportDesc: false });
  },

  onReportDescSubmit() {
    const { _pendingReportType, _pendingReportId, _pendingReportReason, reportDescValue } = this.data;
    this.setData({ showReportDesc: false });

    let url = '/report/submit?targetType=' + _pendingReportType
      + '&targetId=' + _pendingReportId
      + '&reason=' + encodeURIComponent(_pendingReportReason);
    if (reportDescValue && reportDescValue.trim()) {
      url += '&description=' + encodeURIComponent(reportDescValue.trim());
    }

    request(url, 'POST')
      .then(() => {
        wx.showToast({ title: '举报已提交', icon: 'success' });
      })
      .catch((err) => { wx.showToast({ title: (err && err.message) || '举报失败', icon: 'none' }); });
  },

  // ========== 头像点击 ==========

  onAvatarTap(e) {
    const userId = e.currentTarget.dataset.userId;
    if (!userId) return;
    const myId = app.globalData.userInfo && app.globalData.userInfo.userId;
    if (idsEqual(userId, myId)) {
      wx.navigateTo({ url: '/pages/profile/profile' });
      return;
    }
    wx.navigateTo({
      url: '/pages/user/user?userId=' + userId +
        '&nickname=' + encodeURIComponent(e.currentTarget.dataset.nickname || '')
    });
  },

  // ========== 轮播 ==========

  onImageLoad(e) {
    const { width, height } = e.detail;
    const idx = e.currentTarget.dataset.idx;
    if (!width || !height) return;
    const h = Math.round(750 * height / width);
    const photoHeights = this.data.photoHeights || [];
    photoHeights[idx] = Math.min(1200, Math.max(300, h));
    const current = this.data.current;
    const updates = { photoHeights };
    if (idx === current) updates.swiperHeight = photoHeights[idx];
    this.setData(updates);
  },

  onSwiperChange(e) {
    const idx = e.detail.current;
    const photo = this.data.photos[idx];
    this._photoId = photo.id;
    const photoHeights = this.data.photoHeights || [];
    const swiperHeight = photoHeights[idx] || 560;
    this.setData({ current: idx, photo, swiperHeight });
    this.loadComments(true);
  },

  previewImage() {
    const urls = this.data.photos.map(p => p.imageUrl);
    wx.previewImage({ current: this.data.photo.imageUrl, urls });
  },

  onLocateOnMap() {
    const p = this.data.photo;
    if (!p.latitude || !p.longitude) return;
    // 使用缩略图参数，避免加载全尺寸图片导致白屏
    let thumb = p.thumbnailUrl || p.imageUrl || '';
    if (thumb && thumb.includes('.cos.')) {
      const sep = thumb.includes('?') ? '&' : '?';
      thumb = thumb + sep + 'imageMogr2/thumbnail/216x200';
    }
    wx.navigateTo({
      url: '/pages/map/map?focusLat=' + p.latitude +
        '&focusLng=' + p.longitude +
        '&focusImage=' + encodeURIComponent(thumb) +
        '&focusName=' + encodeURIComponent(p.locationName || '')
    });
  },

  onVisibilityTap() {
    if (!this.data.photo.isOwner) return;
    wx.showActionSheet({
      itemList: ['🔒 仅自己可见', '👥 互关可见', '🌍 所有人可见'],
      success: (res) => {
        const visibility = res.tapIndex;
        if (visibility === this.data.photo.visibility) return;
        const currentIdx = this.data.current;
        request('/photo/updateVisibility?photoId=' + this._photoId + '&visibility=' + visibility, 'POST')
          .then(() => {
            this.setData({
              'photo.visibility': visibility,
              [`photos[${currentIdx}].visibility`]: visibility
            });
            wx.showToast({ title: '已修改', icon: 'success' });
          })
          .catch(() => {
            wx.showToast({ title: '修改失败', icon: 'none' });
          });
      }
    });
  },

  onPhotoDateChange(e) {
    const newDate = e.detail.value;
    if (!newDate || newDate === this.data.photo.photoDate) return;
    const currentIdx = this.data.current;
    request('/photo/updateDate?photoId=' + this._photoId + '&photoDate=' + newDate, 'POST')
      .then(() => {
        this.setData({
          'photo.photoDate': newDate,
          [`photos[${currentIdx}].photoDate`]: newDate
        });
        wx.showToast({ title: '已修改', icon: 'success' });
      })
      .catch(() => {
        wx.showToast({ title: '修改失败', icon: 'none' });
      });
  },

  onDeletePhoto() {
    if (!checkLogin()) return;
    wx.showModal({
      title: '确认删除',
      content: '删除后不可恢复，确定要删除这张照片吗？',
      confirmColor: '#e64340',
      success: (res) => {
        if (!res.confirm) return;
        request('/photo/delete?photoId=' + this._photoId, 'POST')
          .then(() => {
            wx.showToast({ title: '已删除', icon: 'success' });
            setTimeout(() => { wx.navigateBack(); }, 800);
          })
          .catch(() => wx.showToast({ title: '删除失败', icon: 'none' }));
      }
    });
  },

  onReportPhoto() {
    if (!checkLogin()) return;
    if (this.data.photo && this.data.photo.isOwner) {
      wx.showToast({ title: '不能举报自己的照片', icon: 'none' });
      return;
    }
    this._showReportDialog('photo', this._photoId);
  },

  onUploaderTap() {
    const photo = this.data.photo;
    if (!photo || !photo.userId) return;
    const myId = app.globalData.userInfo && app.globalData.userInfo.userId;
    if (idsEqual(photo.userId, myId)) {
      wx.navigateTo({ url: '/pages/profile/profile' });
      return;
    }
    wx.navigateTo({
      url: '/pages/user/user?userId=' + photo.userId +
        '&nickname=' + encodeURIComponent(photo.nickname || '')
    });
  },

  onPhotoLikeTap() {
    if (!checkLogin()) return;
    const photo = this.data.photo;
    const currentIdx = this.data.current;
    const newLiked = !photo.liked;
    const newLikeCount = photo.likeCount + (newLiked ? 1 : -1);

    this.setData({
      'photo.liked': newLiked,
      'photo.likeCount': newLikeCount,
      [`photos[${currentIdx}].liked`]: newLiked,
      [`photos[${currentIdx}].likeCount`]: newLikeCount
    });

    request('/photo/like?photoId=' + this._photoId, 'POST').then(res => {
      const data = res.data || {};
      this.setData({
        'photo.liked': data.liked,
        'photo.likeCount': data.likeCount,
        [`photos[${currentIdx}].liked`]: data.liked,
        [`photos[${currentIdx}].likeCount`]: data.likeCount
      });
    }).catch(() => {
      this.setData({
        'photo.liked': photo.liked,
        'photo.likeCount': photo.likeCount,
        [`photos[${currentIdx}].liked`]: photo.liked,
        [`photos[${currentIdx}].likeCount`]: photo.likeCount
      });
    });
  }
});
