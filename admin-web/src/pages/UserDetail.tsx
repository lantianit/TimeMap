import { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Card, Descriptions, Tabs, Table, Image, Tag, Button, Space, Modal, Select, InputNumber, message, Spin, Avatar } from 'antd';
import { getUserDetail, getUserPhotos, getUserComments, getUserViolations, punishUser, unpunishUser, deletePhoto, deleteComment } from '../utils/api';

export default function UserDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const qc = useQueryClient();
  const { data: user, isLoading } = useQuery({ queryKey: ['user', id], queryFn: () => getUserDetail(id!) });
  const [photoPage, setPhotoPage] = useState(1);
  const [commentPage, setCommentPage] = useState(1);
  const [violationPage, setViolationPage] = useState(1);
  const { data: photos } = useQuery({ queryKey: ['userPhotos', id, photoPage], queryFn: () => getUserPhotos(id!, { page: photoPage, size: 20 }) });
  const { data: comments } = useQuery({ queryKey: ['userComments', id, commentPage], queryFn: () => getUserComments(id!, { page: commentPage, size: 20 }) });
  const { data: violations } = useQuery({ queryKey: ['userViolations', id, violationPage], queryFn: () => getUserViolations({ targetUserId: id, page: violationPage, size: 20 }) });
  const u: any = user || {};
  const [punishType, setPunishType] = useState('');
  const [punishDays, setPunishDays] = useState(7);

  if (isLoading) return <Spin size="large" style={{ display: 'block', margin: '100px auto' }} />;

  const handlePunish = () => {
    if (!punishType) { message.error('请选择处罚类型'); return; }
    Modal.confirm({
      title: '确认处罚',
      content: `对用户 ${u.nickname} 执行 ${punishType}`,
      onOk: async () => {
        await punishUser({ userId: id, punishmentType: punishType, punishmentDays: punishDays, reason: '管理员手动处罚' });
        message.success('处罚成功');
        qc.invalidateQueries({ queryKey: ['user', id] });
      },
    });
  };

  const handleUnpunish = (type: string) => {
    Modal.confirm({
      title: '确认解除处罚',
      onOk: async () => {
        await unpunishUser({ userId: id, type });
        message.success('已解除');
        qc.invalidateQueries({ queryKey: ['user', id] });
      },
    });
  };

  const getStatus = () => {
    if (u.isBanned) return <Tag color="red">已封号</Tag>;
    if (u.muteUntil && new Date(u.muteUntil) > new Date()) return <Tag color="orange">禁言至 {u.muteUntil?.replace('T', ' ').slice(0, 16)}</Tag>;
    if (u.banUploadUntil && new Date(u.banUploadUntil) > new Date()) return <Tag color="volcano">禁止拍摄至 {u.banUploadUntil?.replace('T', ' ').slice(0, 16)}</Tag>;
    return <Tag color="green">正常</Tag>;
  };

  const photoColumns = [
    { title: '缩略图', dataIndex: 'thumbnailUrl', width: 80, render: (v: string) => <Image src={v} width={48} height={48} style={{ objectFit: 'cover', borderRadius: 4 }} /> },
    { title: '地点', dataIndex: 'locationName', ellipsis: true },
    { title: '拍摄日期', dataIndex: 'photoDate', width: 120 },
    { title: '评论', dataIndex: 'commentCount', width: 60 },
    { title: '点赞', dataIndex: 'likeCount', width: 60 },
    { title: '操作', width: 80, render: (_: any, r: any) => <Button type="link" danger size="small" onClick={() => {
      Modal.confirm({ title: '确认删除照片？', onOk: async () => { await deletePhoto(r.id); message.success('已删除'); qc.invalidateQueries({ queryKey: ['userPhotos'] }); } });
    }}>删除</Button> },
  ];

  const commentColumns = [
    { title: '内容', dataIndex: 'content', ellipsis: true },
    { title: '照片ID', dataIndex: 'photoId', width: 100 },
    { title: '点赞', dataIndex: 'likeCount', width: 60 },
    { title: '时间', dataIndex: 'createTime', width: 160, render: (v: string) => v?.replace('T', ' ').slice(0, 16) },
    { title: '操作', width: 80, render: (_: any, r: any) => <Button type="link" danger size="small" onClick={() => {
      Modal.confirm({ title: '确认删除评论？', onOk: async () => { await deleteComment(r.id); message.success('已删除'); qc.invalidateQueries({ queryKey: ['userComments'] }); } });
    }}>删除</Button> },
  ];

  const violationColumns = [
    { title: '类型', dataIndex: 'violationType', width: 120 },
    { title: '原因', dataIndex: 'reason', ellipsis: true },
    { title: '处罚', dataIndex: 'punishmentType', width: 100 },
    { title: '天数', dataIndex: 'punishmentDays', width: 60 },
    { title: '时间', dataIndex: 'createTime', width: 160, render: (v: string) => v?.replace('T', ' ').slice(0, 16) },
  ];

  return (
    <div>
      <Button onClick={() => navigate('/users')} style={{ marginBottom: 16 }}>← 返回列表</Button>
      <Card>
        <Space align="start" size={24}>
          <Avatar src={u.avatarUrl} size={80} />
          <Descriptions column={2} size="small">
            <Descriptions.Item label="昵称">{u.nickname}</Descriptions.Item>
            <Descriptions.Item label="用户ID">{u.id}</Descriptions.Item>
            <Descriptions.Item label="照片数">{u.photoCount}</Descriptions.Item>
            <Descriptions.Item label="评论数">{u.commentCount}</Descriptions.Item>
            <Descriptions.Item label="获赞数">{u.totalLikes}</Descriptions.Item>
            <Descriptions.Item label="违规次数"><span style={{ color: u.violationCount > 0 ? '#cf1322' : undefined }}>{u.violationCount}</span></Descriptions.Item>
            <Descriptions.Item label="状态">{getStatus()}</Descriptions.Item>
            <Descriptions.Item label="注册时间">{u.createTime?.replace('T', ' ').slice(0, 16)}</Descriptions.Item>
          </Descriptions>
        </Space>
        <div style={{ marginTop: 16 }}>
          <Space>
            <Select value={punishType} onChange={setPunishType} style={{ width: 140 }} placeholder="处罚类型"
              options={[{ value: 'warning', label: '警告' }, { value: 'mute', label: '禁言' }, { value: 'ban_upload', label: '禁止拍摄' }, { value: 'ban_account', label: '封号' }]} />
            {(punishType === 'mute' || punishType === 'ban_upload') && <InputNumber min={1} max={365} value={punishDays} onChange={(v) => setPunishDays(v || 7)} addonAfter="天" />}
            <Button type="primary" danger onClick={handlePunish}>执行处罚</Button>
            {u.isBanned && <Button onClick={() => handleUnpunish('ban_account')}>解除封号</Button>}
            {u.muteUntil && new Date(u.muteUntil) > new Date() && <Button onClick={() => handleUnpunish('mute')}>解除禁言</Button>}
            {u.banUploadUntil && new Date(u.banUploadUntil) > new Date() && <Button onClick={() => handleUnpunish('ban_upload')}>解除禁止拍摄</Button>}
          </Space>
        </div>
      </Card>
      <Tabs style={{ marginTop: 16 }} items={[
        { key: 'photos', label: `照片 (${(photos as any)?.total || 0})`, children: <Table columns={photoColumns} dataSource={(photos as any)?.list || []} rowKey="id" size="small" pagination={{ current: photoPage, pageSize: 20, total: (photos as any)?.total, onChange: setPhotoPage }} /> },
        { key: 'comments', label: `评论 (${(comments as any)?.total || 0})`, children: <Table columns={commentColumns} dataSource={(comments as any)?.list || []} rowKey="id" size="small" pagination={{ current: commentPage, pageSize: 20, total: (comments as any)?.total, onChange: setCommentPage }} /> },
        { key: 'violations', label: `违规记录 (${(violations as any)?.total || 0})`, children: <Table columns={violationColumns} dataSource={(violations as any)?.list || []} rowKey="id" size="small" pagination={{ current: violationPage, pageSize: 20, total: (violations as any)?.total, onChange: setViolationPage }} /> },
      ]} />
    </div>
  );
}
