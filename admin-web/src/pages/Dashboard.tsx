import { useQuery } from '@tanstack/react-query';
import { Card, Col, Row, Statistic, Spin } from 'antd';
import { AlertOutlined, ClockCircleOutlined, BellOutlined, UserAddOutlined } from '@ant-design/icons';
import ReactECharts from 'echarts-for-react';
import { getDashboardStats } from '../utils/api';

export default function Dashboard() {
  const { data, isLoading } = useQuery({ queryKey: ['dashboard'], queryFn: getDashboardStats });
  const stats: any = data || {};

  if (isLoading) return <Spin size="large" style={{ display: 'block', margin: '100px auto' }} />;

  const trendOption = {
    tooltip: { trigger: 'axis' as const },
    xAxis: { type: 'category' as const, data: (stats.reportTrend || []).map((i: any) => i.date) },
    yAxis: { type: 'value' as const },
    series: [{ name: '举报数', type: 'line', data: (stats.reportTrend || []).map((i: any) => i.count), smooth: true, areaStyle: {} }],
  };

  const reasonOption = {
    tooltip: { trigger: 'item' as const },
    series: [{ type: 'pie', radius: ['40%', '70%'], data: (stats.reasonDistribution || []).map((i: any) => ({ name: i.name, value: i.value })) }],
  };

  const userOption = {
    tooltip: { trigger: 'axis' as const },
    xAxis: { type: 'category' as const, data: (stats.userGrowthTrend || []).map((i: any) => i.date) },
    yAxis: { type: 'value' as const },
    series: [{ name: '新增用户', type: 'bar', data: (stats.userGrowthTrend || []).map((i: any) => i.count) }],
  };

  const photoOption = {
    tooltip: { trigger: 'axis' as const },
    xAxis: { type: 'category' as const, data: (stats.photoUploadTrend || []).map((i: any) => i.date) },
    yAxis: { type: 'value' as const },
    series: [{ name: '新增照片', type: 'line', data: (stats.photoUploadTrend || []).map((i: any) => i.count), smooth: true }],
  };

  return (
    <div>
      <Row gutter={16} style={{ marginBottom: 24 }}>
        <Col span={6}><Card><Statistic title="今日新增举报" value={stats.todayReports || 0} prefix={<AlertOutlined />} /></Card></Col>
        <Col span={6}><Card><Statistic title="待处理举报" value={stats.pendingReports || 0} prefix={<ClockCircleOutlined />} valueStyle={{ color: stats.pendingReports > 0 ? '#cf1322' : undefined }} /></Card></Col>
        <Col span={6}><Card><Statistic title="待处理申诉" value={stats.pendingAppeals || 0} prefix={<BellOutlined />} /></Card></Col>
        <Col span={6}><Card><Statistic title="今日新增用户" value={stats.todayUsers || 0} prefix={<UserAddOutlined />} /></Card></Col>
      </Row>
      <Row gutter={16} style={{ marginBottom: 24 }}>
        <Col span={6}><Card><Statistic title="总用户数" value={stats.totalUsers || 0} /></Card></Col>
        <Col span={6}><Card><Statistic title="总照片数" value={stats.totalPhotos || 0} /></Card></Col>
        <Col span={6}><Card><Statistic title="平均处理时长" value={stats.avgHandleTimeHours || 0} suffix="小时" /></Card></Col>
        <Col span={6}><Card><Statistic title="采纳率 / 驳回率" value={`${stats.resolveRate || 0}% / ${stats.rejectRate || 0}%`} /></Card></Col>
      </Row>
      <Row gutter={16}>
        <Col span={12}><Card title="近30天举报趋势"><ReactECharts option={trendOption} style={{ height: 300 }} /></Card></Col>
        <Col span={12}><Card title="举报原因分布"><ReactECharts option={reasonOption} style={{ height: 300 }} /></Card></Col>
      </Row>
      <Row gutter={16} style={{ marginTop: 16 }}>
        <Col span={12}><Card title="近30天用户增长"><ReactECharts option={userOption} style={{ height: 300 }} /></Card></Col>
        <Col span={12}><Card title="近30天照片上传"><ReactECharts option={photoOption} style={{ height: 300 }} /></Card></Col>
      </Row>
    </div>
  );
}
