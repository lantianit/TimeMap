import { create } from 'zustand';

interface AuthState {
  token: string | null;
  role: string;
  nickname: string;
  adminId: number | null;
  mustChangePassword: boolean;
  setAuth: (data: { token: string; role: string; nickname: string; adminId: number; mustChangePassword: boolean }) => void;
  logout: () => void;
  isLoggedIn: () => boolean;
}

export const useAuth = create<AuthState>((set, get) => ({
  token: localStorage.getItem('admin_token'),
  role: localStorage.getItem('admin_role') || '',
  nickname: localStorage.getItem('admin_nickname') || '',
  adminId: localStorage.getItem('admin_id') ? Number(localStorage.getItem('admin_id')) : null,
  mustChangePassword: false,
  setAuth: (data) => {
    localStorage.setItem('admin_token', data.token);
    localStorage.setItem('admin_role', data.role);
    localStorage.setItem('admin_nickname', data.nickname);
    localStorage.setItem('admin_id', String(data.adminId));
    set({ token: data.token, role: data.role, nickname: data.nickname, adminId: data.adminId, mustChangePassword: data.mustChangePassword });
  },
  logout: () => {
    localStorage.removeItem('admin_token');
    localStorage.removeItem('admin_role');
    localStorage.removeItem('admin_nickname');
    localStorage.removeItem('admin_id');
    set({ token: null, role: '', nickname: '', adminId: null, mustChangePassword: false });
  },
  isLoggedIn: () => !!get().token,
}));
