import { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Table, Tag, Button, Modal, Form, Input, Select, Space, message } from 'antd';
import { getAdminAccounts, createAdminAccount, updateAdminAccount, resetAdminPassword, toggleAdminAccount } from '../utils/api';

export default function Admins() {
  const qc = useQueryClient();
  const { data, isLoading } = useQuery({ queryKey: ['admins'], queryFn: getAdminAccounts });
  const list: any[] = (data as any) || [];
  const [modalOpen, setModalOpen] = useState(false);
  const [editId, setEditId] = useState<number | null>(null);
  const [form] = Form.useForm();

  const openCreate = () => { setEditId(null); form.resetFields(); setModalOpen(true); };
  const openEdit = (r: any) => { setEditId(r.id); form.setFieldsValue(r); setModalOpen(true); };

  const handleSave = async () => {
    const values = await form.validateFields();
    if (editId) {
      await updateAdminAccount(editId, values);
      message.success('已更新');
    } else {
      await createAdminAccount(values);
      message.success('已创建');
    }
    setModalOpen(false);
    qc.invalidateQueries({ queryKey: ['admins'] });
  };

  const handleReset = (id: number) => {
    Modal.confirm({
      title: '确认重置密码？',
      content: '密码将重置为 Admin@2026',
      onOk: async () => { await resetAdminPassword(id); message.success('已重置'); },
    });
  };

  const handleToggle = (id: number, enabled: boolean) => {
    Modal.confirm({
      title: enabled ? '确认禁用？' : '确认启用？',
      onOk: async () => { await toggleAdminAccount(id); message.success('已切换'); qc.invalidateQueries({ queryKey: ['admins'] }); },
    });
  };

  const roleMap: Record<string, { text: string; color: string }> = {
    super_admin: { text: '超级管理员', color: 'red' },
    moderator: { text: '审核员', color: 'blue' },
    viewer: { text: '观察员', color: 'default' },
  };

  const columns = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    { title: '账号', dataIndex: 'username', width: 120 },
    { title: '昵称', dataIndex: 'nickname', width: 120 },
    { title: '角色', dataIndex: 'role', width: 120, render: (v: string) => { const m = roleMap[v]; return m ? <Tag color={m.color}>{m.text}</Tag> : v; } },
    { title: '状态', dataIndex: 'isEnabled', width: 80, render: (v: boolean) => v ? <Tag color="green">启用</Tag> : <Tag color="red">禁用</Tag> },
    { title: '最后登录', dataIndex: 'lastLoginTime', width: 160, render: (v: string) => v?.replace('T', ' ').slice(0, 16) || '-' },
    { title: '创建时间', dataIndex: 'createTime', width: 160, render: (v: string) => v?.replace('T', ' ').slice(0, 16) },
    {
      title: '操作', width: 200,
      render: (_: any, r: any) => (
        <Space>
          <Button type="link" size="small" onClick={() => openEdit(r)}>编辑</Button>
          <Button type="link" size="small" onClick={() => handleReset(r.id)}>重置密码</Button>
          <Button type="link" size="small" danger={r.isEnabled} onClick={() => handleToggle(r.id, r.isEnabled)}>{r.isEnabled ? '禁用' : '启用'}</Button>
        </Space>
      ),
    },
  ];

  return (
    <div>
      <Button type="primary" onClick={openCreate} style={{ marginBottom: 16 }}>新增管理员</Button>
      <Table columns={columns} dataSource={list} rowKey="id" loading={isLoading} pagination={false} />
      <Modal title={editId ? '编辑管理员' : '新增管理员'} open={modalOpen} onOk={handleSave} onCancel={() => setModalOpen(false)}>
        <Form form={form} layout="vertical">
          {!editId && <Form.Item name="username" label="账号" rules={[{ required: true }]}><Input /></Form.Item>}
          {!editId && <Form.Item name="password" label="密码" rules={[{ required: true, min: 8 }]}><Input.Password /></Form.Item>}
          <Form.Item name="nickname" label="昵称" rules={[{ required: true }]}><Input /></Form.Item>
          <Form.Item name="role" label="角色" rules={[{ required: true }]}>
            <Select options={[{ value: 'super_admin', label: '超级管理员' }, { value: 'moderator', label: '审核员' }, { value: 'viewer', label: '观察员' }]} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
