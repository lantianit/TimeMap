const { request, checkLogin } = require('../../utils/request');
const app = getApp();

Page({
  data: {
    photos: [],
    current: 0,
    total: 0,
    photo: {},
    // 评论
    comments: [],
    commentTotal: 0,
    commentPage: 1,
    commentHasMore: true,
    commentLoading: false,
    // 输入
    inputValue: '',
    inputFocus: false,
    replyMode: false,
    replyPlaceholder: '说点什么...',
    replyParentId: 0,
    replyToUserId: 0,
    // 键盘高度
    keyboardHeight: 0
  },

  onLoad(options) {
    this._photoId = null;
    if (options.ids) {
      this.loadBatch(options.ids);
    } else if (options.id) {
      this._photoId = options.id;
      this.loadDetail(options.id);
    }
  },

  loadBatch(ids) {
    console.log('[detail] loadBatch 开始, ids:', ids);
    request('/photo/batch', 'GET', { ids })
      .then((res) => {
        console.log('[detail] loadBatch 响应:', res);
        const photos = (res.data || []).map(p => this._formatPhoto(p));
        console.log('[detail] 格式化后的 photos:', photos);
        if (!photos.length) { wx.showToast({ title: '照片不存在', icon: 'none' }); return; }
        this._photoId = photos[0].id;
        this.setData({ photos, total: photos.length, current: 0, photo: photos[0] });
        console.log('[detail] setData 完成, photo.liked:', this.data.photo.liked, 'photo.likeCount:', this.data.photo.likeCount);
        this.loadComments(true);
      })
      .catch((err) => {
        console.error('[detail] loadBatch 失败:', err);
        wx.showToast({ title: '加载失败', icon: 'none' });
      });
  },

  loadDetail(id) {
    console.log('[detail] loadDetail 开始, id:', id);
    request('/photo/detail/' + id, 'GET')
      .then((res) => {
        console.log('[detail] loadDetail 响应:', res);
        const photo = this._formatPhoto(res.data || {});
        console.log('[detail] 格式化后的 photo:', photo);
        this.setData({ photos: [photo], total: 1, current: 0, photo });
        console.log('[detail] setData 完成, photo.liked:', this.data.photo.liked, 'photo.likeCount:', this.data.photo.likeCount);
        this.loadComments(true);
      })
      .catch((err) => {
        console.error('[detail] loadDetail 失败:', err);
        wx.showToast({ title: '加载失败', icon: 'none' });
      });
  },

  _formatPhoto(photo) {
    console.log('[detail] _formatPhoto 输入:', photo);
    if (photo.createTime) {
      photo.createTimeFormatted = photo.createTime.replace('T', ' ').substring(0, 10);
    }
    if (photo.likeCount === undefined || photo.likeCount === null) {
      photo.likeCount = 0;
    }
    if (photo.liked === undefined || photo.liked === null) {
      photo.liked = false;
    }
    // 判断是否是自己的照片
    const myId = app.globalData.userInfo && app.globalData.userInfo.userId;
    photo.isOwner = myId && String(photo.userId) === String(myId);
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

    const body = {
      photoId: this._photoId,
      content: content,
      parentId: this.data.replyParentId || 0,
      replyToUserId: this.data.replyToUserId || 0
    };

    // 乐观更新
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
      // 替换乐观数据或刷新回复
      if (body.parentId === 0) {
        const real = this._formatComment(res.data);
        const comments = this.data.comments.map(c => c.id === optimistic.id ? real : c);
        this.setData({ comments });
      } else {
        this._refreshReplies(body.parentId);
        // 更新父评论 replyCount
        const comments = this.data.comments.map(c => {
          if (String(c.id) === String(body.parentId)) {
            c.replyCount = (c.replyCount || 0) + 1;
          }
          return c;
        });
        this.setData({ comments });
      }
    }).catch(() => {
      // 回滚乐观更新
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
    // 如果是子评论的回复，parentId 指向顶级评论
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

    // 乐观更新
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
      // 回滚
      this.setData({
        [path + '.liked']: comment.liked,
        [path + '.likeCount']: comment.likeCount
      });
    });
  },

  // ========== 删除 ==========

  onCommentLongPress(e) {
    const { id, userId, idx } = e.currentTarget.dataset;
    const myId = app.globalData.userInfo && app.globalData.userInfo.userId;
    if (String(userId) !== String(myId)) return;

    wx.showActionSheet({
      itemList: ['删除'],
      success: (res) => {
        if (res.tapIndex === 0) {
          request('/comment/delete?commentId=' + id, 'POST').then(() => {
            const comments = this.data.comments.filter(c => String(c.id) !== String(id));
            this.setData({ comments, commentTotal: Math.max(0, this.data.commentTotal - 1) });
          }).catch(() => wx.showToast({ title: '删除失败', icon: 'none' }));
        }
      }
    });
  },

  // ========== 头像点击 → 私信 ==========

  onAvatarTap(e) {
    const userId = e.currentTarget.dataset.userId;
    if (!userId) return;
    const myId = app.globalData.userInfo && app.globalData.userInfo.userId;
    if (String(userId) === String(myId)) {
      wx.navigateTo({ url: '/pages/profile/profile' });
      return;
    }
    wx.navigateTo({
      url: '/pages/user/user?userId=' + userId +
        '&nickname=' + encodeURIComponent(e.currentTarget.dataset.nickname || '')
    });
  },

  // ========== 原有方法 ==========

  onSwiperChange(e) {
    const idx = e.detail.current;
    const photo = this.data.photos[idx];
    this._photoId = photo.id;
    this.setData({ current: idx, photo });
    this.loadComments(true);
  },

  previewImage() {
    const urls = this.data.photos.map(p => p.imageUrl);
    wx.previewImage({ current: this.data.photo.imageUrl, urls });
  },

  onLocateOnMap() {
    const p = this.data.photo;
    if (!p.latitude || !p.longitude) return;
    wx.navigateTo({
      url: '/pages/map/map?focusLat=' + p.latitude +
        '&focusLng=' + p.longitude +
        '&focusImage=' + encodeURIComponent(p.thumbnailUrl || p.imageUrl) +
        '&focusName=' + encodeURIComponent(p.locationName || '')
    });
  },

  // ========== 照片点赞 ==========

  /** 删除照片（仅自己的） */
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
          .catch(() => { wx.showToast({ title: '删除失败', icon: 'none' }); });
      }
    });
  },

  /** 举报照片 */
  onReportPhoto() {
    if (!checkLogin()) return;
    wx.showActionSheet({
      itemList: ['色情低俗', '违法违规', '侵权', '虚假信息', '其他'],
      success: (res) => {
        const reasons = ['色情低俗', '违法违规', '侵权', '虚假信息', '其他'];
        const reason = reasons[res.tapIndex];
        request('/report/submit?targetType=photo&targetId=' + this._photoId + '&reason=' + encodeURIComponent(reason), 'POST')
          .then(() => { wx.showToast({ title: '举报已提交', icon: 'success' }); })
          .catch(() => { wx.showToast({ title: '举报失败', icon: 'none' }); });
      }
    });
  },

  /** 上传者信息点击 → 用户主页 */
  onUploaderTap() {
    const photo = this.data.photo;
    if (!photo || !photo.userId) return;
    const myId = app.globalData.userInfo && app.globalData.userInfo.userId;
    if (String(photo.userId) === String(myId)) {
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

    // 乐观更新：同时更新 photo 和 photos 数组
    this.setData({
      'photo.liked': newLiked,
      'photo.likeCount': newLikeCount,
      [`photos[${currentIdx}].liked`]: newLiked,
      [`photos[${currentIdx}].likeCount`]: newLikeCount
    });

    request('/photo/like?photoId=' + this._photoId, 'POST').then(res => {
      const data = res.data || {};
      // 更新为服务器返回的真实值
      this.setData({
        'photo.liked': data.liked,
        'photo.likeCount': data.likeCount,
        [`photos[${currentIdx}].liked`]: data.liked,
        [`photos[${currentIdx}].likeCount`]: data.likeCount
      });
    }).catch(() => {
      // 回滚
      this.setData({
        'photo.liked': photo.liked,
        'photo.likeCount': photo.likeCount,
        [`photos[${currentIdx}].liked`]: photo.liked,
        [`photos[${currentIdx}].likeCount`]: photo.likeCount
      });
    });
  }
});
