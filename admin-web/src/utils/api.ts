import request from './request';

// Auth
export const login = (data: any) => request.post('/api/admin/auth/login', data);
export const getAdminInfo = () => request.get('/api/admin/auth/info');
export const changePassword = (data: any) => request.post('/api/admin/auth/change-password', data);

// Dashboard
export const getDashboardStats = () => request.get('/api/admin/dashboard/stats');

// Reports
export const getReports = (params: any) => request.get('/api/admin/report/list', { params });
export const getReportDetail = (id: number) => request.get(`/api/admin/report/detail/${id}`);
export const resolveReport = (data: any) => request.post('/api/admin/report/resolve', data);
export const rejectReport = (data: any) => request.post('/api/admin/report/reject', data);
export const batchResolveReports = (data: any) => request.post('/api/admin/report/batch-resolve', data);
export const batchRejectReports = (data: any) => request.post('/api/admin/report/batch-reject', data);
export const getPendingCount = () => request.get('/api/admin/report/pending-count');
export const getAggregatedReports = (params: any) => request.get('/api/admin/report/aggregated', { params });

// Appeals
export const getAppeals = (params: any) => request.get('/api/admin/report/appeals', { params });
export const getAppealDetail = (id: number) => request.get(`/api/admin/report/appeal/${id}`);
export const resolveAppeal = (data: any) => request.post('/api/admin/report/appeal/resolve', data);
export const rejectAppeal = (data: any) => request.post('/api/admin/report/appeal/reject', data);

// Users
export const getUsers = (params: any) => request.get('/api/admin/user/list', { params });
export const getUserDetail = (id: string) => request.get(`/api/admin/user/${id}`);
export const getUserPhotos = (id: string, params: any) => request.get(`/api/admin/user/${id}/photos`, { params });
export const getUserComments = (id: string, params: any) => request.get(`/api/admin/user/${id}/comments`, { params });
export const punishUser = (data: any) => request.post('/api/admin/user/punish', data);
export const unpunishUser = (data: any) => request.post('/api/admin/user/unpunish', data);
export const getUserViolations = (params: any) => request.get('/api/admin/report/user-violations', { params });

// Content
export const getPhotos = (params: any) => request.get('/api/admin/photo/list', { params });
export const getPhotoDetail = (id: string) => request.get(`/api/admin/photo/${id}`);
export const deletePhoto = (id: string) => request.delete(`/api/admin/photo/${id}`);
export const getComments = (params: any) => request.get('/api/admin/comment/list', { params });
export const deleteComment = (id: string) => request.delete(`/api/admin/comment/${id}`);

// Logs
export const getLogs = (params: any) => request.get('/api/admin/report/logs', { params });

// Admin accounts
export const getAdminAccounts = () => request.get('/api/admin/account/list');
export const createAdminAccount = (data: any) => request.post('/api/admin/account/create', data);
export const updateAdminAccount = (id: number, data: any) => request.put(`/api/admin/account/${id}`, data);
export const resetAdminPassword = (id: number) => request.post(`/api/admin/account/${id}/reset-password`);
export const toggleAdminAccount = (id: number) => request.post(`/api/admin/account/${id}/toggle`);
