import { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Table, Tag, Select, Space, Modal, Input, message, Button } from 'antd';
import { getAppeals, resolveAppeal, rejectAppeal } from '../utils/api';

const statusMap: Record<number, { text: string; color: string }> = {
  0: { text: '待处理', color: 'orange' }, 1: { text: '已采纳', color: 'green' }, 2: { text: '已驳回', color: 'red' },
};

export default function Appeals() {
  const qc = useQueryClient();
  const [params, setParams] = useState<any>({ page: 1, size: 20 });
  const { data, isLoading } = useQuery({ queryKey: ['appeals', params], queryFn: () => getAppeals(params) });
  const list: any = data || { list: [], total: 0 };

  const handleAction = (id: number, action: 'resolve' | 'reject') => {
    Modal.confirm({
      title: action === 'resolve' ? '采纳申诉' : '驳回申诉',
      content: <Input.TextArea id="appeal-reason" placeholder={action === 'resolve' ? '采纳说明' : '驳回原因'} rows={3} />,
      onOk: async () => {
        const reason = (document.getElementById('appeal-reason') as HTMLTextAreaElement)?.value;
        if (!reason) { message.error('请填写原因'); return; }
        const fn = action === 'resolve' ? resolveAppeal : rejectAppeal;
        await fn({ appealId: id, handleResult: reason });
        message.success(action === 'resolve' ? '已采纳' : '已驳回');
        qc.invalidateQueries({ queryKey: ['appeals'] });
      },
    });
  };

  const columns = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    { title: '类型', dataIndex: 'type', width: 120, render: (v: string) => v === 'content_author' ? '内容移除' : '举报驳回' },
    { title: '申诉人', dataIndex: 'userNickname', width: 120 },
    { title: '关联举报', dataIndex: 'reportId', width: 100 },
    { title: '申诉原因', dataIndex: 'reason', ellipsis: true },
    { title: '时间', dataIndex: 'createTime', width: 160, render: (v: string) => v?.replace('T', ' ').slice(0, 16) },
    { title: '状态', dataIndex: 'status', width: 90, render: (v: number) => <Tag color={statusMap[v]?.color}>{statusMap[v]?.text}</Tag> },
    {
      title: '操作', width: 160,
      render: (_: any, r: any) => r.status === 0 ? (
        <Space>
          <Button type="link" onClick={() => handleAction(r.id, 'resolve')}>采纳</Button>
          <Button type="link" danger onClick={() => handleAction(r.id, 'reject')}>驳回</Button>
        </Space>
      ) : '-',
    },
  ];

  return (
    <div>
      <Space style={{ marginBottom: 16 }}>
        <Select placeholder="状态" allowClear style={{ width: 120 }} onChange={(v) => setParams({ ...params, status: v, page: 1 })}
          options={[{ value: 0, label: '待处理' }, { value: 1, label: '已采纳' }, { value: 2, label: '已驳回' }]} />
      </Space>
      <Table columns={columns} dataSource={list.list} rowKey="id" loading={isLoading}
        pagination={{ current: params.page, pageSize: params.size, total: list.total, showTotal: (t: number) => `共 ${t} 条`,
          onChange: (p, s) => setParams({ ...params, page: p, size: s }) }} />
    </div>
  );
}
