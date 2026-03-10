import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Table, Tag, Input, Select, Space, Avatar, Button } from 'antd';
import { useNavigate } from 'react-router-dom';
import { getUsers } from '../utils/api';

export default function Users() {
  const navigate = useNavigate();
  const [params, setParams] = useState<any>({ page: 1, size: 20 });
  const { data, isLoading } = useQuery({ queryKey: ['users', params], queryFn: () => getUsers(params) });
  const list: any = data || { list: [], total: 0 };

  const getStatus = (r: any) => {
    if (r.isBanned) return <Tag color="red">已封号</Tag>;
    if (r.muteUntil && new Date(r.muteUntil) > new Date()) return <Tag color="orange">禁言中</Tag>;
    if (r.banUploadUntil && new Date(r.banUploadUntil) > new Date()) return <Tag color="volcano">禁止拍摄</Tag>;
    return <Tag color="green">正常</Tag>;
  };

  const columns = [
    { title: '头像', dataIndex: 'avatarUrl', width: 60, render: (v: string) => <Avatar src={v} size="small" /> },
    { title: '昵称', dataIndex: 'nickname', width: 120 },
    { title: '照片数', dataIndex: 'photoCount', width: 80 },
    { title: '评论数', dataIndex: 'commentCount', width: 80 },
    { title: '违规次数', dataIndex: 'violationCount', width: 90, render: (v: number) => <span style={{ color: v > 0 ? '#cf1322' : undefined }}>{v}</span> },
    { title: '状态', width: 100, render: (_: any, r: any) => getStatus(r) },
    { title: '注册时间', dataIndex: 'createTime', width: 160, render: (v: string) => v?.replace('T', ' ').slice(0, 16) },
    { title: '操作', width: 100, render: (_: any, r: any) => <Button type="link" onClick={() => navigate(`/users/${r.id}`)}>详情</Button> },
  ];

  return (
    <div>
      <Space style={{ marginBottom: 16 }}>
        <Input.Search placeholder="搜索昵称/用户ID" allowClear style={{ width: 240 }}
          onSearch={(v) => setParams({ ...params, keyword: v, page: 1 })} />
        <Select placeholder="状态" allowClear style={{ width: 120 }} onChange={(v) => setParams({ ...params, status: v, page: 1 })}
          options={[{ value: 'banned', label: '已封号' }, { value: 'muted', label: '禁言中' }, { value: 'ban_upload', label: '禁止拍摄' }]} />
      </Space>
      <Table columns={columns} dataSource={list.list} rowKey="id" loading={isLoading}
        pagination={{ current: params.page, pageSize: params.size, total: list.total, showTotal: (t: number) => `共 ${t} 条`,
          onChange: (p, s) => setParams({ ...params, page: p, size: s }) }} />
    </div>
  );
}
