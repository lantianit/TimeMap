import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Table, Tag } from 'antd';
import { getLogs } from '../utils/api';

const actionMap: Record<string, { text: string; color: string }> = {
  resolve_report: { text: '采纳举报', color: 'green' },
  reject_report: { text: '驳回举报', color: 'red' },
  punish_user: { text: '处罚用户', color: 'volcano' },
  unpunish_user: { text: '解除处罚', color: 'blue' },
  resolve_appeal: { text: '采纳申诉', color: 'cyan' },
  reject_appeal: { text: '驳回申诉', color: 'orange' },
  delete_photo: { text: '删除照片', color: 'magenta' },
  delete_comment: { text: '删除评论', color: 'purple' },
  create_admin: { text: '创建管理员', color: 'geekblue' },
  update_admin: { text: '更新管理员', color: 'geekblue' },
  reset_password: { text: '重置密码', color: 'gold' },
  toggle_admin: { text: '切换状态', color: 'lime' },
};

export default function Logs() {
  const [params, setParams] = useState<any>({ page: 1, size: 20 });
  const { data, isLoading } = useQuery({ queryKey: ['logs', params], queryFn: () => getLogs(params) });
  const list: any = data || { list: [], total: 0 };

  const columns = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    { title: '操作人', dataIndex: 'adminNickname', width: 120 },
    { title: '操作类型', dataIndex: 'action', width: 120, render: (v: string) => { const m = actionMap[v]; return m ? <Tag color={m.color}>{m.text}</Tag> : v; } },
    { title: '对象类型', dataIndex: 'targetType', width: 100 },
    { title: '对象ID', dataIndex: 'targetId', width: 100 },
    { title: '详情', dataIndex: 'detail', ellipsis: true },
    { title: '时间', dataIndex: 'createTime', width: 160, render: (v: string) => v?.replace('T', ' ').slice(0, 16) },
  ];

  return (
    <Table columns={columns} dataSource={list.list} rowKey="id" loading={isLoading}
      pagination={{ current: params.page, pageSize: params.size, total: list.total, showTotal: (t: number) => `共 ${t} 条`,
        onChange: (p, s) => setParams({ ...params, page: p, size: s }) }} />
  );
}
