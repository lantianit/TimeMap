import request from '../utils/request';
import type { PageVO, PhotoListItem, CommentListItem } from './typings';

/** 照片列表 */
export const getPhotos = (params: Record<string, unknown>) =>
  request.get<unknown, PageVO<PhotoListItem>>('/api/admin/photo/list', { params });

/** 删除照片 */
export const deletePhoto = (id: string) =>
  request.delete<unknown, void>(`/api/admin/photo/${id}`);

/** 评论列表 */
export const getComments = (params: Record<string, unknown>) =>
  request.get<unknown, PageVO<CommentListItem>>('/api/admin/comment/list', { params });

/** 删除评论 */
export const deleteComment = (id: string) =>
  request.delete<unknown, void>(`/api/admin/comment/${id}`);
