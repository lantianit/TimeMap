/**
 * 后端统一返回体（由 request.ts 拦截器解包，API 函数直接返回 data 部分）
 */

// ===== Auth =====
export interface AdminLoginVO {
  token: string;
  role: string;
  nickname: string;
  adminId: string;
  mustChangePassword: boolean;
}

export interface AdminAccountVO {
  id: string;
  username: string;
  nickname: string;
  role: string;
  linkedUserId: string | null;
  isEnabled: boolean;
  lastLoginTime: string;
  lastLoginIp: string;
  createTime: string;
}

export interface LoginParams {
  username: string;
  password: string;
  rememberMe?: boolean;
}

export interface ChangePasswordParams {
  oldPassword: string;
  newPassword: string;
}

// ===== Dashboard =====
export interface TrendItem {
  date: string;
  count: number;
}

export interface DistributionItem {
  name: string;
  value: number;
}

export interface DashboardStatsVO {
  todayReports: number;
  pendingReports: number;
  pendingAppeals: number;
  todayUsers: number;
  totalUsers: number;
  totalPhotos: number;
  reportTrend: TrendItem[];
  reasonDistribution: DistributionItem[];
  userGrowthTrend: TrendItem[];
  photoUploadTrend: TrendItem[];
  avgHandleTimeHours: number;
  resolveRate: number;
  rejectRate: number;
}


// ===== Reports =====
export interface AdminReportListItemVO {
  id: string;
  targetType: string;
  targetId: string;
  reason: string;
  status: number;
  createTime: string;
  reporterUserId: string;
  reporterNickname: string;
  targetPreview: string;
  targetImageUrl: string;
}

export interface AdminReportPageVO {
  list: AdminReportListItemVO[];
  total: number;
  hasMore: boolean;
}

export interface AdminReportDetailVO {
  id: string;
  targetType: string;
  targetId: string;
  reason: string;
  description: string;
  status: number;
  handleResult: string;
  createTime: string;
  handledTime: string;
  reporterUserId: string;
  reporterNickname: string;
  reporterAvatarUrl: string;
  targetOwnerUserId: string;
  targetOwnerNickname: string;
  targetOwnerAvatarUrl: string;
  targetPreview: string;
  targetImageUrl: string;
  photoId: string;
  commentId: string;
  handledBy: string;
  handledByNickname: string;
}

export interface ResolveReportParams {
  reportId: string;
  action: string;
  handleResult: string;
  punishmentType?: string;
  punishmentDays?: number;
}

export interface RejectReportParams {
  reportId: string;
  handleResult: string;
}

export interface BatchReportParams {
  reportIds: string[];
  handleResult: string;
}

export interface PendingReportCountVO {
  reportCount: number;
  appealCount: number;
}

// ===== Appeals =====
export interface AppealVO {
  id: string;
  userId: string;
  userNickname: string;
  userAvatarUrl: string;
  type: string;
  reportId: string;
  reason: string;
  status: number;
  handleResult: string;
  createTime: string;
  handledTime: string;
  handledBy: string;
  handledByNickname: string;
  reportReason: string;
  reportTargetType: string;
}

export interface AppealPageVO {
  list: AppealVO[];
  total: number;
  hasMore: boolean;
}

export interface HandleAppealParams {
  appealId: string;
  handleResult: string;
}

// ===== Users =====
export interface UserListItem {
  id: string;
  nickname: string;
  avatarUrl: string;
  photoCount: number;
  commentCount: number;
  violationCount: number;
  isBanned: boolean;
  muteUntil: string | null;
  banUploadUntil: string | null;
  createTime: string;
  totalLikes: number;
}

export interface UserDetailVO {
  id: string;
  nickname: string;
  avatarUrl: string;
  photoCount: number;
  commentCount: number;
  totalLikes: number;
  violationCount: number;
  isBanned: boolean;
  muteUntil: string | null;
  banUploadUntil: string | null;
  createTime: string;
}

export interface PageVO<T> {
  list: T[];
  total: number;
  hasMore?: boolean;
}

export interface PunishUserParams {
  userId: string;
  punishmentType: string;
  punishmentDays?: number;
  reason: string;
}

export interface UnpunishUserParams {
  userId: string;
  type: string;
}

// ===== Content =====
export interface PhotoListItem {
  id: string;
  imageUrl: string;
  thumbnailUrl: string;
  locationName: string;
  district: string;
  photoDate: string;
  nickname: string;
  avatarUrl: string;
  commentCount: number;
  reportCount: number;
  createTime: string;
}

export interface CommentListItem {
  id: string;
  content: string;
  nickname: string;
  avatarUrl: string;
  photoId: string;
  likeCount: number;
  reportCount: number;
  createTime: string;
}

// ===== Logs =====
export interface AdminLogVO {
  id: string;
  adminUserId: string;
  adminNickname: string;
  action: string;
  targetType: string;
  targetId: string;
  detail: string;
  createTime: string;
}

export interface AdminLogPageVO {
  list: AdminLogVO[];
  total: number;
  hasMore: boolean;
}

// ===== Admin Accounts =====
export interface CreateAdminParams {
  username: string;
  password: string;
  nickname: string;
  role: string;
}

export interface UpdateAdminParams {
  nickname: string;
  role: string;
}

// ===== Violations =====
export interface UserViolationVO {
  id: string;
  userId: string;
  userNickname: string;
  reportId: string;
  violationType: string;
  reason: string;
  targetType: string;
  targetId: string;
  punishmentType: string;
  punishmentDays: number;
  createTime: string;
  canAppeal: boolean;
}

export interface UserViolationPageVO {
  list: UserViolationVO[];
  total: number;
  hasMore: boolean;
}
