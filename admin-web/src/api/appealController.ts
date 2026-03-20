import request from '../utils/request';
import type { AppealPageVO, HandleAppealParams } from './typings';

/** 申诉列表 */
export const getAppeals = (params: Record<string, unknown>) =>
  request.get<unknown, AppealPageVO>('/api/admin/report/appeals', { params });

/** 采纳申诉 */
export const resolveAppeal = (data: HandleAppealParams) =>
  request.post<unknown, void>('/api/admin/report/appeal/resolve', data);

/** 驳回申诉 */
export const rejectAppeal = (data: HandleAppealParams) =>
  request.post<unknown, void>('/api/admin/report/appeal/reject', data);
