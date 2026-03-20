import request from '../utils/request';
import type { AdminLoginVO, LoginParams, ChangePasswordParams } from './typings';

/** 管理员登录 */
export const login = (data: LoginParams) =>
  request.post<unknown, AdminLoginVO>('/api/admin/auth/login', data);

/** 修改密码 */
export const changePassword = (data: ChangePasswordParams) =>
  request.post<unknown, void>('/api/admin/auth/change-password', data);
