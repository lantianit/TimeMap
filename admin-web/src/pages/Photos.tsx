import { useState } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Table, Image, Input, Button, Modal, message, Avatar, Space } from 'antd';
import { getPhotos, deletePhoto } from '../utils/api';

export default function Photos() {
  const qc = useQueryClient();
  const [params, setParams] = useState<any>({ page: 1, size: 20 });
  const { data, isLoading } = useQuery({ queryKey: ['photos', params], queryFn: () => getPhotos(params) });
  const list: any = data || { list: [], total: 0 };

  const handleDelete = (id: string) => {
    Modal.confirm({
      title: '确认删除照片？',
      content: '删除后不可恢复',
      onOk: async () => {
        await deletePhoto(id);
        message.success('已删除');
        qc.invalidateQueries({ queryKey: ['photos'] });
      },
    });
  };

  const columns = [
    { title: '缩略图', dataIndex: 'thumbnailUrl', width: 80, render: (v: string, r: any) => <Image src={v || r.imageUrl} width={48} height={48} style={{ objectFit: 'cover', borderRadius: 4 }} /> },
    { title: '地点', dataIndex: 'locationName', ellipsis: true, width: 160 },
    { title: '区域', dataIndex: 'district', width: 100 },
    { title: '拍摄日期', dataIndex: 'photoDate', width: 110 },
    { title: '上传者', width: 120, render: (_: any, r: any) => <Space><Avatar src={r.avatarUrl} size="small" />{r.nickname}</Space> },
    { title: '评论', dataIndex: 'commentCount', width: 60 },
    { title: '举报', dataIndex: 'reportCount', width: 60, render: (v: number) => <span style={{ color: v > 0 ? '#cf1322' : undefined }}>{v}</span> },
    { title: '上传时间', dataIndex: 'createTime', width: 160, render: (v: string) => v?.replace('T', ' ').slice(0, 16) },
    { title: '操作', width: 80, render: (_: any, r: any) => <Button type="link" danger size="small" onClick={() => handleDelete(r.id)}>删除</Button> },
  ];

  return (
    <div>
      <Space style={{ marginBottom: 16 }}>
        <Input.Search placeholder="搜索地点/描述" allowClear style={{ width: 240 }}
          onSearch={(v) => setParams({ ...params, keyword: v, page: 1 })} />
        <Input placeholder="区域" allowClear style={{ width: 120 }}
          onBlur={(e) => setParams({ ...params, district: e.target.value, page: 1 })} />
      </Space>
      <Table columns={columns} dataSource={list.list} rowKey="id" loading={isLoading}
        pagination={{ current: params.page, pageSize: params.size, total: list.total, showTotal: (t: number) => `共 ${t} 条`,
          onChange: (p, s) => setParams({ ...params, page: p, size: s }) }} />
    </div>
  );
}
