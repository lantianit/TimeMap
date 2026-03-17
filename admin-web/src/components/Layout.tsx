import { useState, useEffect } from 'react';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import { Layout as AntLayout, Menu, Dropdown, Avatar, Modal, Form, Input, message } from 'antd';
import {
  DashboardOutlined, AlertOutlined, BellOutlined, TeamOutlined,
  PictureOutlined, FileTextOutlined, SettingOutlined, LogoutOutlined,
  CommentOutlined, UserOutlined, LockOutlined,
} from '@ant-design/icons';
import { useAuth } from '../store/useAuth';
import { getPendingCount, changePassword } from '../api';

const { Sider, Header, Content } = AntLayout;

export default function Layout() {
  const navigate = useNavigate();
  const location = useLocation();
  const { nickname, role, logout } = useAuth();
  const [collapsed, setCollapsed] = useState(false);
  const [pendingReports, setPendingReports] = useState(0);
  const [pendingAppeals, setPendingAppeals] = useState(0);
  const [pwdOpen, setPwdOpen] = useState(false);
  const [form] = Form.useForm();

  useEffect(() => {
    const fetchPending = () => {
      getPendingCount().then((data: any) => {
        setPendingReports(data.reportCount || 0);
        setPendingAppeals(data.appealCount || 0);
        const total = (data.reportCount || 0) + (data.appealCount || 0);
        document.title = total > 0 ? `(${total}) 时迹管理后台` : '时迹管理后台';
      }).catch(() => {});
    };
    fetchPending();
    const timer = setInterval(fetchPending, 30000);
    return () => clearInterval(timer);
  }, []);

  const menuItems = [
    { key: '/dashboard', icon: <DashboardOutlined />, label: '数据看板' },
    {
      key: '/reports', icon: <AlertOutlined />,
      label: <span>举报管理{pendingReports > 0 && <span style={{ marginLeft: 8, background: '#ff4d4f', color: '#fff', borderRadius: 10, padding: '0 6px', fontSize: 12, lineHeight: '18px', display: 'inline-block' }}>{pendingReports}</span>}</span>,
    },
    {
      key: '/appeals', icon: <BellOutlined />,
      label: <span>申诉管理{pendingAppeals > 0 && <span style={{ marginLeft: 8, background: '#ff4d4f', color: '#fff', borderRadius: 10, padding: '0 6px', fontSize: 12, lineHeight: '18px', display: 'inline-block' }}>{pendingAppeals}</span>}</span>,
    },
    { key: '/users', icon: <TeamOutlined />, label: '用户管理' },
    {
      key: 'content', icon: <PictureOutlined />, label: '内容管理',
      children: [
        { key: '/content/photos', icon: <PictureOutlined />, label: '照片管理' },
        { key: '/content/comments', icon: <CommentOutlined />, label: '评论管理' },
      ],
    },
    { key: '/logs', icon: <FileTextOutlined />, label: '操作日志' },
    ...(role === 'super_admin' ? [{
      key: 'settings', icon: <SettingOutlined />, label: '系统设置',
      children: [{ key: '/settings/admins', icon: <UserOutlined />, label: '管理员管理' }],
    }] : []),
  ];

  const selectedKey = '/' + location.pathname.split('/').slice(1, 3).join('/');

  const handlePwdChange = async () => {
    try {
      const values = await form.validateFields();
      await changePassword(values);
      message.success('密码修改成功，请重新登录');
      setPwdOpen(false);
      form.resetFields();
      logout();
      navigate('/login');
    } catch {}
  };

  return (
    <AntLayout style={{ height: '100vh' }}>
      <Sider collapsible collapsed={collapsed} onCollapse={setCollapsed} theme="dark">
        <div style={{ height: 48, display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#fff', fontWeight: 600, fontSize: collapsed ? 14 : 16 }}>
          {collapsed ? '时迹' : '时迹管理后台'}
        </div>
        <Menu theme="dark" mode="inline" selectedKeys={[selectedKey]}
          items={menuItems} onClick={({ key }) => navigate(key)} />
      </Sider>
      <AntLayout>
        <Header style={{ background: '#fff', padding: '0 24px', display: 'flex', justifyContent: 'flex-end', alignItems: 'center', borderBottom: '1px solid #f0f0f0' }}>
          <Dropdown menu={{
            items: [
              { key: 'pwd', icon: <LockOutlined />, label: '修改密码', onClick: () => setPwdOpen(true) },
              { key: 'logout', icon: <LogoutOutlined />, label: '退出登录', onClick: () => { logout(); navigate('/login'); } },
            ],
          }}>
            <div style={{ cursor: 'pointer', display: 'flex', alignItems: 'center', gap: 8 }}>
              <Avatar icon={<UserOutlined />} size="small" />
              <span>{nickname}</span>
            </div>
          </Dropdown>
        </Header>
        <Content style={{ margin: 16, padding: 24, background: '#fff', borderRadius: 8, overflow: 'auto' }}>
          <Outlet />
        </Content>
      </AntLayout>

      <Modal title="修改密码" open={pwdOpen} onOk={handlePwdChange} onCancel={() => setPwdOpen(false)}>
        <Form form={form} layout="vertical">
          <Form.Item name="oldPassword" label="原密码" rules={[{ required: true }]}>
            <Input.Password />
          </Form.Item>
          <Form.Item name="newPassword" label="新密码" rules={[{ required: true, min: 8, message: '至少8位，包含大小写字母和数字' }]}>
            <Input.Password />
          </Form.Item>
        </Form>
      </Modal>
    </AntLayout>
  );
}
