import { HashRouter, Routes, Route, Navigate } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import Login from './pages/Login';
import Layout from './components/Layout';
import Dashboard from './pages/Dashboard';
import Reports from './pages/Reports';
import ReportDetail from './pages/ReportDetail';
import Appeals from './pages/Appeals';
import Users from './pages/Users';
import UserDetail from './pages/UserDetail';
import Photos from './pages/Photos';
import Comments from './pages/Comments';
import Logs from './pages/Logs';
import Admins from './pages/Admins';
import { useAuth } from './store/useAuth';

const queryClient = new QueryClient({
  defaultOptions: { queries: { retry: 1, refetchOnWindowFocus: false } },
});

function PrivateRoute({ children }: { children: React.ReactNode }) {
  const token = useAuth((s) => s.token);
  return token ? <>{children}</> : <Navigate to="/login" />;
}

export default function App() {
  return (
    <ConfigProvider locale={zhCN}>
      <QueryClientProvider client={queryClient}>
        <HashRouter>
          <Routes>
            <Route path="/login" element={<Login />} />
            <Route path="/" element={<PrivateRoute><Layout /></PrivateRoute>}>
              <Route index element={<Navigate to="/dashboard" />} />
              <Route path="dashboard" element={<Dashboard />} />
              <Route path="reports" element={<Reports />} />
              <Route path="reports/:id" element={<ReportDetail />} />
              <Route path="appeals" element={<Appeals />} />
              <Route path="users" element={<Users />} />
              <Route path="users/:id" element={<UserDetail />} />
              <Route path="content/photos" element={<Photos />} />
              <Route path="content/comments" element={<Comments />} />
              <Route path="logs" element={<Logs />} />
              <Route path="settings/admins" element={<Admins />} />
            </Route>
          </Routes>
        </HashRouter>
      </QueryClientProvider>
    </ConfigProvider>
  );
}
