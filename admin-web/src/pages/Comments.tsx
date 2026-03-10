import { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Table, Input, Button, Modal, message, Avatar, Space } from 'antd';
import { getComments, deleteComment } from '../utils/api';

export default function Comments() {
  const qc = useQueryClient();
  const [params, setParams] = useState<any>({ page: 1, size: 20 });
  const { data, isLoading } = useQuery({ queryKey: ['comments', params], queryFn: () => getComments(params) });
  const list: any = data || { list: [], total: 0 };

  const handleDelete = (id: string) => {
    Modal.confirm({
      title: '确认删除评论？',
      onOk: async () => {
        await deleteComment(id);
        message.success('已删除');
        qc.invalidateQueries({ queryKey: ['comments'] });
      },
    });
  };

  const columns = [
    { title: '内容', dataIndex: 'content', ellipsis: true },
    { title: '评论者', width: 120, render: (_: any, r: any) => <Space><Avatar src={r.avatarUrl} size="small" />{r.nickname}</Space> },
    { title: '照片ID', dataIndex: 'photoId', width: 100 },
    { title: '点赞', dataIndex: 'likeCount', width: 60 },
    { title: '举报', dataIndex: 'reportCount', width: 60, render: (v: number) => <span style={{ color: v > 0 ? '#cf1322' : undefined }}>{v}</span> },
    { title: '时间', dataIndex: 'createTime', width: 160, render: (v: string) => v?.replace('T', ' ').slice(0, 16) },
    { title: '操作', width: 80, render: (_: any, r: any) => <Button type="link" danger size="small" onClick={() => handleDelete(r.id)}>删除</Button> },
  ];

  return (
    <div>
      <Space style={{ marginBottom: 16 }}>
        <Input.Search placeholder="搜索评论内容" allowClear style={{ width: 240 }}
          onSearch={(v) => setParams({ ...params, keyword: v, page: 1 })} />
      </Space>
      <Table columns={columns} dataSource={list.list} rowKey="id" loading={isLoading}
        pagination={{ current: params.page, pageSize: params.size, total: list.total, showTotal: (t: number) => `共 ${t} 条`,
          onChange: (p, s) => setParams({ ...params, page: p, size: s }) }} />
    </div>
  );
}
