import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Form, Input, Button, Card, Checkbox, message, Modal } from 'antd';
import { UserOutlined, LockOutlined } from '@ant-design/icons';
import { login, changePassword } from '../utils/api';
import { useAuth } from '../store/useAuth';

export default function Login() {
  const navigate = useNavigate();
  const setAuth = useAuth((s) => s.setAuth);
  const [loading, setLoading] = useState(false);
  const [pwdModal, setPwdModal] = useState(false);
  const [pwdForm] = Form.useForm();

  const onFinish = async (values: any) => {
    setLoading(true);
    try {
      const data: any = await login(values);
      setAuth({ token: data.token, role: data.role, nickname: data.nickname, adminId: data.adminId, mustChangePassword: data.mustChangePassword });
      if (data.mustChangePassword) {
        setPwdModal(true);
      } else {
        navigate('/dashboard');
      }
    } catch {
    } finally {
      setLoading(false);
    }
  };

  const handlePwdChange = async () => {
    try {
      const values = await pwdForm.validateFields();
      await changePassword(values);
      message.success('密码修改成功');
      setPwdModal(false);
      navigate('/dashboard');
    } catch {}
  };

  return (
    <div style={{ height: '100vh', display: 'flex', justifyContent: 'center', alignItems: 'center', background: '#f0f2f5' }}>
      <Card title="时迹地图 · 管理后台" style={{ width: 400 }}>
        <Form onFinish={onFinish} autoComplete="off">
          <Form.Item name="username" rules={[{ required: true, message: '请输入账号' }]}>
            <Input prefix={<UserOutlined />} placeholder="账号" size="large" />
          </Form.Item>
          <Form.Item name="password" rules={[{ required: true, message: '请输入密码' }]}>
            <Input.Password prefix={<LockOutlined />} placeholder="密码" size="large" />
          </Form.Item>
          <Form.Item name="rememberMe" valuePropName="checked">
            <Checkbox>记住我（7天）</Checkbox>
          </Form.Item>
          <Button type="primary" htmlType="submit" loading={loading} block size="large">登录</Button>
        </Form>
      </Card>
      <Modal title="首次登录，请修改密码" open={pwdModal} onOk={handlePwdChange} closable={false} maskClosable={false}>
        <Form form={pwdForm} layout="vertical">
          <Form.Item name="oldPassword" label="当前密码" rules={[{ required: true }]}>
            <Input.Password />
          </Form.Item>
          <Form.Item name="newPassword" label="新密码" rules={[{ required: true, min: 8, message: '至少8位，包含大小写字母和数字' }]}>
            <Input.Password />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
