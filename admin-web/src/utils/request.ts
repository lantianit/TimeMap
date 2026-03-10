import axios from 'axios';
import { message } from 'antd';

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE || 'http://localhost:8080',
  timeout: 30000,
});

request.interceptors.request.use((config) => {
  const token = localStorage.getItem('admin_token');
  if (token) config.headers.Authorization = `Bearer ${token}`;
  config.headers['X-Client-Type'] = 'web';
  return config;
});

request.interceptors.response.use(
  (res) => {
    const data = res.data;
    if (data.code === 200) return data.data;
    message.error(data.message || '请求失败');
    return Promise.reject(new Error(data.message));
  },
  (err) => {
    if (err.response?.status === 401) {
      localStorage.removeItem('admin_token');
      window.location.hash = '#/login';
    }
    message.error(err.response?.data?.message || err.message || '网络错误');
    return Promise.reject(err);
  }
);

export default request;
