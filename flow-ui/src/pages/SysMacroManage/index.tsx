import React, { useRef, useState, useCallback } from 'react';
import {
  ActionType,
  ModalForm,
  PageContainer,
  ProColumns,
  ProFormDependency,
  ProFormInstance,
  ProFormSelect,
  ProFormSwitch,
  ProFormText,
  ProFormTextArea,
  ProTable,
} from '@ant-design/pro-components';
import { request } from '@umijs/max';
import { Alert, Button, message, Popconfirm, Space, Tag } from 'antd';
import { PlusOutlined, EditOutlined, DeleteOutlined } from '@ant-design/icons';
import { invalidateMacroCache } from '@/pages/flow/controller/components/flow-editor/components/MacroCompletion';

/**
 * ============================================================================
 * 接口声明 (稍后可提取到 services/sysMacro.ts)
 * ============================================================================
 */
const API_BASE = '/flow-api/sys-macros';

/** 接口：分页查询 */
const querySysMacroPage = async (params: any) => {
  const { current, pageSize, ...rest } = params;
  const result = await request(`${API_BASE}/page`, {
    method: 'GET',
    params: {
      ...rest,
      page: (current || 1) - 1,
      size: pageSize || 10,
    },
  });
  return {
    data: result.items || [],
    success: true,
    total: result.total || 0,
  };
};

/** 接口：保存（新建）*/
const createSysMacro = async (data: any) => {
  return request(API_BASE, {
    method: 'POST',
    data,
  });
};

/** 接口：更新 */
const updateSysMacro = async (id: string, data: any) => {
  return request(`${API_BASE}/${id}`, {
    method: 'PUT',
    data,
  });
};

/** 接口：删除 */
const deleteSysMacro = async (id: string) => {
  return request(`${API_BASE}/${id}`, {
    method: 'DELETE',
  });
};

/**
 * ============================================================================
 * 类型定义
 * ============================================================================
 */
export interface SysMacroDTO {
  id: string;
  macroCode: string;
  macroName: string;
  macroType: 'VARIABLE' | 'FUNCTION';
  expression: string;
  scope: 'ALL' | 'SQL_ONLY' | 'JS_ONLY';
  returnType?: string;
  macroParams?: string;
  status: 0 | 1;
  remark?: string;
  createTime?: string;
  updateTime?: string;
}

/**
 * ============================================================================
 * 组件实现
 * ============================================================================
 */
const SysMacroManage: React.FC = () => {
  const actionRef = useRef<ActionType>();
  const formRef = useRef<ProFormInstance>();
  const [modalVisible, setModalVisible] = useState<boolean>(false);
  const [currentRow, setCurrentRow] = useState<Partial<SysMacroDTO>>();

  /**
   * ========================================================================
   * 从 SpEL 表达式中自动提取入参列表（核心魔法 ✨）
   * ========================================================================
   *
   * 正则说明：/#([a-zA-Z_][a-zA-Z0-9_]*)/g
   *
   *   #                      — 匹配井号前缀，SpEL 中 #varName 表示引用变量
   *   ([a-zA-Z_]             — 捕获组开头：变量名首字符必须是字母或下划线
   *     [a-zA-Z0-9_]*)       — 后续字符可以是字母、数字、下划线，0 次或多次
   *   /g                     — 全局匹配，提取表达式中所有的 #变量名
   *
   * 示例：
   *   输入  → T(cn.hutool.DateUtil).format(#date, #format)
   *   匹配  → ['#date', '#format']
   *   捕获组 → ['date', 'format']
   *   去重后 → ['date', 'format']
   *   回填  → 'date, format'
   */
  const extractParamsFromExpression = useCallback((expressionText: string) => {
    // 正则：匹配所有 #开头的变量引用（捕获组提取纯变量名）
    const SPEL_PARAM_REGEX = /#([a-zA-Z_][a-zA-Z0-9_]*)/g;

    // 使用 matchAll 遍历所有匹配项，取捕获组 [1] 即变量名部分
    const matches = [...expressionText.matchAll(SPEL_PARAM_REGEX)];
    const paramNames = matches.map((m) => m[1]);

    // 利用 Set 对提取结果进行去重，保持首次出现的顺序
    const uniqueParams = [...new Set(paramNames)];

    return uniqueParams;
  }, []);

  /**
   * SpEL 表达式输入框的 onChange 处理器
   * 当用户编辑表达式时，自动提取参数并回填到 macroParams 字段
   */
  const handleExpressionChange = useCallback(
    (e: React.ChangeEvent<HTMLTextAreaElement>) => {
      const text = e.target.value || '';
      const currentType = formRef.current?.getFieldValue('macroType');

      // 仅当宏类型为 FUNCTION 时，才自动提取并回填入参列表
      if (currentType === 'FUNCTION') {
        const extracted = extractParamsFromExpression(text);
        formRef.current?.setFieldValue('macroParams', extracted.join(', '));
      }
    },
    [extractParamsFromExpression],
  );

  /**
   * 处理删除
   */
  const handleRemove = async (id: string) => {
    try {
      await deleteSysMacro(id);
      invalidateMacroCache();
      message.success('删除成功');
      actionRef.current?.reload();
    } catch (error) {
      message.error('删除失败，请重试');
    }
  };

  /**
   * 表格列定义
   */
  const columns: ProColumns<SysMacroDTO>[] = [
    {
      title: '宏编码',
      dataIndex: 'macroCode',
      copyable: true,
      formItemProps: {
        rules: [
          { required: true, message: '请输入宏编码' },
          { pattern: /^[a-zA-Z_]+$/, message: '只能包含英文和下划线' },
        ],
      },
    },
    {
      title: '宏名称',
      dataIndex: 'macroName',
      formItemProps: {
        rules: [{ required: true, message: '请输入宏名称' }],
      },
    },
    {
      title: '类型',
      dataIndex: 'macroType',
      valueType: 'select',
      valueEnum: {
        VARIABLE: { text: '变量', status: 'Default' },
        FUNCTION: { text: '方法', status: 'Processing' },
      },
      formItemProps: {
        rules: [{ required: true, message: '请选择类型' }],
      },
    },
    {
      title: 'SpEL 表达式',
      dataIndex: 'expression',
      hideInTable: true,
      valueType: 'textarea',
      formItemProps: {
        rules: [{ required: true, message: '请输入表达式' }],
      },
    },
    {
      title: '返回值类型',
      dataIndex: 'returnType',
      hideInSearch: true,
      initialValue: 'String',
    },
    {
      title: '作用域',
      dataIndex: 'scope',
      valueType: 'select',
      valueEnum: {
        ALL: { text: '全局' },
        SQL_ONLY: { text: '仅SQL' },
        JS_ONLY: { text: '仅JS' },
      },
      initialValue: 'ALL',
    },
    {
      title: '状态',
      dataIndex: 'status',
      valueType: 'switch',
      render: (_, record) => (
        <Tag color={record.status === 1 ? 'success' : 'default'}>
          {record.status === 1 ? '启用' : '停用'}
        </Tag>
      ),
      formItemProps: {
        valuePropName: 'checked',
      },
    },
    {
      title: '备注',
      dataIndex: 'remark',
      hideInSearch: true,
      ellipsis: true,
    },
    {
      title: '创建时间',
      dataIndex: 'createTime',
      valueType: 'dateTime',
      hideInForm: true,
      search: false,
    },
    {
      title: '操作',
      dataIndex: 'option',
      valueType: 'option',
      render: (_, record) => [
        <Button
          key="edit"
          type="link"
          icon={<EditOutlined />}
          onClick={() => {
            setCurrentRow({
              ...record,
              // 如果 record.status 是 1, 表单 Switch 需要 true
              status: record.status as any,
            });
            setModalVisible(true);
          }}
        >
          编辑
        </Button>,
        <Popconfirm
          key="delete"
          title="确认删除该宏定义吗？"
          onConfirm={() => handleRemove(record.id)}
          okText="确认"
          cancelText="取消"
        >
          <Button type="link" danger icon={<DeleteOutlined />}>
            删除
          </Button>
        </Popconfirm>,
      ],
    },
  ];

  return (
    <PageContainer>
      {/* 顶部警告提示 */}
      <Alert
        message="安全提示"
        description="⚠️ 此处的表达式为底层 SpEL 代码，配置错误可能导致系统异常，非高级管理员请勿随意修改。"
        type="warning"
        showIcon
        closable
        style={{ marginBottom: 24 }}
      />

      <ProTable<SysMacroDTO>
        headerTitle="全局参数管理"
        actionRef={actionRef}
        rowKey="id"
        search={{
          labelWidth: 'auto',
        }}
        toolBarRender={() => [
          <Button
            key="button"
            icon={<PlusOutlined />}
            type="primary"
            onClick={() => {
              setCurrentRow(undefined);
              setModalVisible(true);
            }}
          >
            新建
          </Button>,
        ]}
        request={querySysMacroPage}
        columns={columns}
      />

      {/* 新增/编辑弹窗 */}
      <ModalForm
        formRef={formRef}
        title={currentRow?.id ? '编辑宏定义' : '新建宏定义'}
        open={modalVisible}
        onOpenChange={setModalVisible}
        initialValues={currentRow ? {
          ...currentRow,
          status: currentRow.status === 1
        } : {
          status: true,
          scope: 'ALL',
          macroType: 'VARIABLE'
        }}
        modalProps={{
          destroyOnClose: true,
          maskClosable: false,
        }}
        onFinish={async (values) => {
          const hide = message.loading('正在保存');
          try {
            // 转换值为 0/1 (通过 transform 已经处理了一部分，但在 ModalForm onFinish 中手动处理更稳妥)
            const payload = {
              ...values,
              status: values.status ? 1 : 0,
            };

            if (currentRow?.id) {
              await updateSysMacro(currentRow.id, payload);
            } else {
              await createSysMacro(payload);
            }
            invalidateMacroCache();
            hide();
            message.success('保存成功');
            setModalVisible(false);
            actionRef.current?.reload();
            return true;
          } catch (error) {
            hide();
            message.error('保存失败，请检查参数或网络');
            return false;
          }
        }}
      >
        <Space direction="vertical" style={{ width: '100%' }} size="large">
          <Space style={{ width: '100%' }} size="middle">
            <ProFormText
              name="macroCode"
              label="宏编码"
              placeholder="如 sys_user_id"
              width="md"
              disabled={!!currentRow?.id} // 编码一般不允许修改
            />
            <ProFormText
              name="macroName"
              label="宏名称"
              placeholder="如 当前登录用户 ID"
              width="md"
            />
          </Space>

          <Space style={{ width: '100%' }} size="middle">
            <ProFormSelect
              name="macroType"
              label="类型"
              valueEnum={{
                VARIABLE: '变量',
                FUNCTION: '方法',
              }}
              width="md"
            />
            <ProFormSelect
              name="scope"
              label="作用域"
              valueEnum={{
                ALL: '全局',
                SQL_ONLY: '仅SQL',
                JS_ONLY: '仅JS',
              }}
              width="md"
            />
          </Space>

          <ProFormText
            name="returnType"
            label="返回值类型"
            placeholder="String, Number, Boolean 等"
            tooltip="用于前端 JS 解析时的类型推导提示"
          />

          <ProFormTextArea
            name="expression"
            label="SpEL 表达式"
            placeholder="@userContext.getUserId()"
            tooltip="Spring Expression Language 表达式，支持引用容器中的 Bean"
            fieldProps={{
              onChange: handleExpressionChange,
            }}
          />

          {/* ---- 入参列表：仅当 macroType === 'FUNCTION' 时渲染 ---- */}
          <ProFormDependency name={['macroType']}>
            {({ macroType }) =>
              macroType === 'FUNCTION' ? (
                <ProFormText
                  name="macroParams"
                  label="入参列表"
                  placeholder="如 date, format（多个参数用逗号分隔）"
                  tooltip="多个参数用逗号分隔，顺序须与表达式中 #参数名 一致"
                />
              ) : null
            }
          </ProFormDependency>

          <ProFormSwitch
            name="status"
            label="启用状态"
          />

          <ProFormTextArea
            name="remark"
            label="备注"
            placeholder="请输入备注说明"
          />
        </Space>
      </ModalForm>
    </PageContainer>
  );
};

export default SysMacroManage;
