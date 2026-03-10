import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { Table, Tag, Button, Space, Select, Input, Image, Modal, message, Avatar } from 'antd';
import { useNavigate } from 'react-router-dom';
import { getReports, batchResolveReports, batchRejectReports } from '../utils/api';

const statusMap: Record<number, { text: string; color: string }> = {
  0: { text: '待处理', color: 'orange' },
  1: { text: '已采纳', color: 'green' },
  2: { text: '已驳回', color: 'red' },
};
const typeMap: Record<string, string> = { photo: '照片', comment: '评论', message: '消息' };

export default function Reports() {
  const navigate = useNavigate();
  const qc = useQueryClient();
  const [params, setParams] = useState<any>({ page: 1, size: 20 });
  const [selected, setSelected] = useState<number[]>([]);
  const { data, isLoading } = useQuery({ queryKey: ['reports', params], queryFn: () => getReports(params) });
  const list: any = data || { list: [], total: 0 };

  const batchResolve = useMutation({
    mutationFn: (ids: number[]) => {
      return new Promise<void>((resolve, reject) => {
        Modal.confirm({
          title: `批量采纳 ${ids.length} 条举报`,
          content: <Input.TextArea id="batch-reason" placeholder="处理意见" rows={3} />,
          onOk: async () => {
            const reason = (document.getElementById('batch-reason') as HTMLTextAreaElement)?.value;
            if (!reason) { message.error('请填写处理意见'); reject(); return; }
            await batchResolveReports({ reportIds: ids, handleResult: reason });
            message.success('批量采纳成功');
            qc.invalidateQueries({ queryKey: ['reports'] });
            setSelected([]);
            resolve();
          },
          onCancel: () => reject(),
        });
      });
    },
  });

  const batchReject = useMutation({
    mutationFn: (ids: number[]) => {
      return new Promise<void>((resolve, reject) => {
        Modal.confirm({
          title: `批量驳回 ${ids.length} 条举报`,
          content: <Input.TextArea id="batch-reject-reason" placeholder="驳回原因" rows={3} />,
          onOk: async () => {
            const reason = (document.getElementById('batch-reject-reason') as HTMLTextAreaElement)?.value;
            if (!reason) { message.error('请填写驳回原因'); reject(); return; }
            await batchRejectReports({ reportIds: ids, handleResult: reason });
            message.success('批量驳回成功');
            qc.invalidateQueries({ queryKey: ['reports'] });
            setSelected([]);
            resolve();
          },
          onCancel: () => reject(),
        });
      });
    },
  });

  const columns = [
    { title: 'ID', dataIndex: 'id', width: 80 },
    { title: '类型', dataIndex: 'targetType', width: 80, render: (v: string) => typeMap[v] || v },
    { title: '原因', dataIndex: 'reason', ellipsis: true, width: 120 },
    {
      title: '内容预览', dataIndex: 'targetImageUrl', width: 80,
      render: (v: string, r: any) => v ? <Image src={v} width={48} height={48} style={{ objectFit: 'cover', borderRadius: 4 }} /> : <span>{r.targetPreview?.slice(0, 20)}</span>,
    },
    {
      title: '举报人', dataIndex: 'reporterNickname', width: 120,
      render: (v: string) => <Space><Avatar size="small" />{v}</Space>,
    },
    { title: '时间', dataIndex: 'createTime', width: 160, render: (v: string) => v?.replace('T', ' ').slice(0, 16) },
    {
      title: '状态', dataIndex: 'status', width: 90,
      render: (v: number) => <Tag color={statusMap[v]?.color}>{statusMap[v]?.text}</Tag>,
    },
    {
      title: '操作', width: 100,
      render: (_: any, r: any) => <Button type="link" onClick={() => navigate(`/reports/${r.id}`)}>详情</Button>,
    },
  ];

  return (
    <div>
      <Space style={{ marginBottom: 16 }} wrap>
        <Select placeholder="状态" allowClear style={{ width: 120 }} onChange={(v) => setParams({ ...params, status: v, page: 1 })}
          options={[{ value: 0, label: '待处理' }, { value: 1, label: '已采纳' }, { value: 2, label: '已驳回' }]} />
        <Select placeholder="类型" allowClear style={{ width: 120 }} onChange={(v) => setParams({ ...params, targetType: v, page: 1 })}
          options={[{ value: 'photo', label: '照片' }, { value: 'comment', label: '评论' }, { value: 'message', label: '消息' }]} />
        {selected.length > 0 && (
          <>
            <Button type="primary" onClick={() => batchResolve.mutate(selected)}>批量采纳 ({selected.length})</Button>
            <Button danger onClick={() => batchReject.mutate(selected)}>批量驳回 ({selected.length})</Button>
          </>
        )}
      </Space>
      <Table columns={columns} dataSource={list.list} rowKey="id" loading={isLoading}
        rowSelection={{ selectedRowKeys: selected, onChange: (keys) => setSelected(keys as number[]),
          getCheckboxProps: (r: any) => ({ disabled: r.status !== 0 }) }}
        pagination={{ current: params.page, pageSize: params.size, total: list.total, showTotal: (t: number) => `共 ${t} 条`,
          onChange: (p, s) => setParams({ ...params, page: p, size: s }) }} />
    </div>
  );
}
