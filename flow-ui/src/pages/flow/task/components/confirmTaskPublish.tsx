import { Modal } from 'antd';
import type { FlowTask } from '../services/taskService';

/**
 * 列表「发布」确认：发布的是库中已保存草稿，非未保存的表单编辑。
 */
export function confirmTaskPublish(record: Pick<FlowTask, 'name' | 'dslContent' | 'hasUnpublishedChanges'>): Promise<boolean> {
  if (!record.dslContent?.trim()) {
    Modal.warning({
      title: '无法发布',
      content: '任务流程（DSL）为空，请先编辑并保存草稿后再发布。',
    });
    return Promise.resolve(false);
  }

  const dirtyHint = record.hasUnpublishedChanges
    ? '当前存在「待更新发布」标记：将发布数据库中已保存的草稿内容。'
    : '将发布数据库中已保存的草稿内容。';

  return new Promise((resolve) => {
    Modal.confirm({
      title: `确认发布「${record.name || '任务'}」？`,
      content: `${dirtyHint}若表单中有未保存修改，请先打开编辑页保存后再发布。`,
      okText: '发布',
      cancelText: '取消',
      onOk: () => resolve(true),
      onCancel: () => resolve(false),
    });
  });
}
