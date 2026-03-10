import { useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Card, Descriptions, Tag, Image, Button, Input, Select, InputNumber, Space, message, Spin, Avatar, Divider } from 'antd';
import { getReportDetail, resolveReport, rejectReport } from '../utils/api';

const statusMap: Record<number, { text: string; color: string }> = {
  0: { text: '待处理', color: 'orange' }, 1: { text: '已采纳', color: 'green' }, 2: { text: '已驳回', color: 'red' },
};

export default function ReportDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const qc = useQueryClient();
  const { data, isLoading } = useQuery({ queryKey: ['report', id], queryFn: () => getReportDetail(Number(id)) });
  const report: any = data || {};
  const [handleResult, setHandleResult] = useState('');
  const [punishType, setPunishType] = useState<string>('');
  const [punishDays, setPunishDays] = useState(7);
  const [submitting, setSubmitting] = useState(false);

  if (isLoading) return <Spin size="large" style={{ display: 'block', margin: '100px auto' }} />;

  const handleResolve = async () => {
    if (!handleResult.trim()) { message.error('请填写处理意见'); return; }
    setSubmitting(true);
    try {
      await resolveReport({ reportId: Number(id), action: 'REMOVE_CONTENT', handleResult, punishmentType: punishType || undefined, punishmentDays: punishDays });
      message.success('已采纳');
      qc.invalidateQueries({ queryKey: ['reports'] });
      navigate('/reports');
    } finally { setSubmitting(false); }
  };

  const handleReject = async () => {
    if (!handleResult.trim()) { message.error('请填写驳回原因'); return; }
    setSubmitting(true);
    try {
      await rejectReport({ reportId: Number(id), handleResult });
      message.success('已驳回');
      qc.invalidateQueries({ queryKey: ['reports'] });
      navigate('/reports');
    } finally { setSubmitting(false); }
  };

  return (
    <div>
      <Button onClick={() => navigate('/reports')} style={{ marginBottom: 16 }}>← 返回列表</Button>
      <Card title={`举报详情 #${id}`} extra={<Tag color={statusMap[report.status]?.color}>{statusMap[report.status]?.text}</Tag>}>
        <Descriptions column={2} bordered size="small">
          <Descriptions.Item label="举报类型">{{ photo: '照片', comment: '评论', message: '消息' }[report.targetType as string] || report.targetType}</Descriptions.Item>
          <Descriptions.Item label="举报原因">{report.reason}</Descriptions.Item>
          <Descriptions.Item label="补充说明" span={2}>{report.description || '无'}</Descriptions.Item>
          <Descriptions.Item label="举报人">
            <Space><Avatar src={report.reporterAvatarUrl} size="small" />{report.reporterNickname}</Space>
          </Descriptions.Item>
          <Descriptions.Item label="举报时间">{report.createTime?.replace('T', ' ').slice(0, 16)}</Descriptions.Item>
          <Descriptions.Item label="被举报人">
            <Space><Avatar src={report.targetOwnerAvatarUrl} size="small" />{report.targetOwnerNickname}</Space>
          </Descriptions.Item>
          <Descriptions.Item label="处理人">{report.handledByNickname || '-'}</Descriptions.Item>
        </Descriptions>

        <Divider>被举报内容</Divider>
        {report.targetImageUrl && <Image src={report.targetImageUrl} style={{ maxWidth: 400, borderRadius: 8 }} />}
        {report.targetPreview && <p style={{ marginTop: 8, color: '#666' }}>{report.targetPreview}</p>}

        {report.status === 0 && (
          <>
            <Divider>处理操作</Divider>
            <Space direction="vertical" style={{ width: '100%' }}>
              <Input.TextArea rows={3} placeholder="处理意见 / 驳回原因（必填）" value={handleResult} onChange={(e) => setHandleResult(e.target.value)} />
              <Space>
                <span>处罚选项：</span>
                <Select value={punishType} onChange={setPunishType} style={{ width: 160 }} allowClear placeholder="不处罚"
                  options={[{ value: 'warning', label: '警告' }, { value: 'mute', label: '禁言' }, { value: 'ban_upload', label: '禁止拍摄' }, { value: 'ban_account', label: '封号' }]} />
                {(punishType === 'mute' || punishType === 'ban_upload') && <InputNumber min={1} max={365} value={punishDays} onChange={(v) => setPunishDays(v || 7)} addonAfter="天" />}
              </Space>
              <Space>
                <Button type="primary" onClick={handleResolve} loading={submitting}>采纳并处理</Button>
                <Button danger onClick={handleReject} loading={submitting}>驳回</Button>
              </Space>
            </Space>
          </>
        )}

        {report.status !== 0 && report.handleResult && (
          <>
            <Divider>处理结果</Divider>
            <p>{report.handleResult}</p>
          </>
        )}
      </Card>
    </div>
  );
}
