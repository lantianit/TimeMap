import request from '../utils/request';
import type {
  AdminReportPageVO, AdminReportDetailVO, PendingReportCountVO,
  ResolveReportParams, RejectReportParams, BatchReportParams,
  UserViolationPageVO,
} from './typings';

/** 举报列表 */
export const getReports = (params: Record<string, unknown>) =>
  request.get<unknown, AdminReportPageVO>('/api/admin/report/list', { params });

/** 举报详情 */
export const getReportDetail = (id: string) =>
  request.get<unknown, AdminReportDetailVO>(`/api/admin/report/detail/${id}`);

/** 采纳举报 */
export const resolveReport = (data: ResolveReportParams) =>
  request.post<unknown, void>('/api/admin/report/resolve', data);

/** 驳回举报 */
export const rejectReport = (data: RejectReportParams) =>
  request.post<unknown, void>('/api/admin/report/reject', data);

/** 批量采纳 */
export const batchResolveReports = (data: BatchReportParams) =>
  request.post<unknown, void>('/api/admin/report/batch-resolve', data);

/** 批量驳回 */
export const batchRejectReports = (data: BatchReportParams) =>
  request.post<unknown, void>('/api/admin/report/batch-reject', data);

/** 待处理数量 */
export const getPendingCount = () =>
  request.get<unknown, PendingReportCountVO>('/api/admin/report/pending-count');

/** 用户违规记录 */
export const getUserViolations = (params: Record<string, unknown>) =>
  request.get<unknown, UserViolationPageVO>('/api/admin/report/user-violations', { params });
