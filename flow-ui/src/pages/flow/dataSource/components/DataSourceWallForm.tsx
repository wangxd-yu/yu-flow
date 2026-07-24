import {
  ProFormDependency,
  ProFormGroup,
  ProFormSelect,
  ProFormSwitch,
} from '@ant-design/pro-components';
import { Alert, Col } from 'antd';
import React from 'react';

/** 与后端 DataSourceWallConfig 默认危险函数对齐 */
export const DEFAULT_FUNCTION_BLACKLIST = [
  'sleep',
  'benchmark',
  'load_file',
  'updatexml',
  'extractvalue',
  'pg_sleep',
];

export const defaultWallConfig = () => ({
  enabled: false,
  multiStatementAllow: false,
  commentAllow: false,
  noneBaseStatementAllow: false,
  selectAllow: true,
  insertAllow: true,
  updateAllow: true,
  deleteAllow: true,
  tableCheck: true,
  tableWhiteList: [] as string[],
  tableBlackList: [] as string[],
  tableReadOnlyList: [] as string[],
  functionBlackList: [...DEFAULT_FUNCTION_BLACKLIST],
  variantCheck: true,
});

type Props = {
  disabled?: boolean;
};

/**
 * SQL 安全墙表单分组（字段前缀 wallConfig.*）
 */
const DataSourceWallForm: React.FC<Props> = ({ disabled }) => {
  return (
    <ProFormGroup title="SQL 安全墙" collapsible>
      <Col span={24}>
        <Alert
          type="info"
          showIcon
          style={{ marginBottom: 12 }}
          message="基于 Druid Wall 的 SQL 校验"
          description="开启后拦截多语句、危险函数、表黑白名单/只读表违规等。默认关闭以免影响存量流程；生产建议开启并配置表白名单。修改后立即生效，无需重启。"
        />
      </Col>
      <ProFormSwitch
        name={['wallConfig', 'enabled']}
        label="启用安全墙"
        colProps={{ span: 8 }}
        disabled={disabled}
      />
      <ProFormDependency name={[['wallConfig', 'enabled']]}>
        {(values) => {
          const enabled = !!values?.wallConfig?.enabled;
          const off = disabled || !enabled;
          return (
            <>
              <ProFormSwitch
                name={['wallConfig', 'multiStatementAllow']}
                label="允许多语句"
                colProps={{ span: 8 }}
                disabled={off}
              />
              <ProFormSwitch
                name={['wallConfig', 'commentAllow']}
                label="允许 SQL 注释"
                colProps={{ span: 8 }}
                disabled={off}
              />
              <ProFormSwitch
                name={['wallConfig', 'noneBaseStatementAllow']}
                label="允许非 CRUD"
                tooltip="开启后允许 DDL 等非增删改查语句"
                colProps={{ span: 8 }}
                disabled={off}
              />
              <ProFormSwitch
                name={['wallConfig', 'selectAllow']}
                label="允许 SELECT"
                colProps={{ span: 8 }}
                disabled={off}
              />
              <ProFormSwitch
                name={['wallConfig', 'insertAllow']}
                label="允许 INSERT"
                colProps={{ span: 8 }}
                disabled={off}
              />
              <ProFormSwitch
                name={['wallConfig', 'updateAllow']}
                label="允许 UPDATE"
                colProps={{ span: 8 }}
                disabled={off}
              />
              <ProFormSwitch
                name={['wallConfig', 'deleteAllow']}
                label="允许 DELETE"
                colProps={{ span: 8 }}
                disabled={off}
              />
              <ProFormSwitch
                name={['wallConfig', 'tableCheck']}
                label="表级校验"
                colProps={{ span: 8 }}
                disabled={off}
              />
              <ProFormSwitch
                name={['wallConfig', 'variantCheck']}
                label="变体攻击检查"
                colProps={{ span: 8 }}
                disabled={off}
              />
              <ProFormSelect
                name={['wallConfig', 'tableWhiteList']}
                label="表白名单"
                mode="tags"
                placeholder="输入表名后回车，如 order_info"
                tooltip="非空时仅允许访问这些表（支持 schema.table）"
                colProps={{ span: 24 }}
                fieldProps={{ disabled: off, tokenSeparators: [','] }}
              />
              <ProFormSelect
                name={['wallConfig', 'tableBlackList']}
                label="表黑名单"
                mode="tags"
                placeholder="禁止访问的表"
                colProps={{ span: 24 }}
                fieldProps={{ disabled: off, tokenSeparators: [','] }}
              />
              <ProFormSelect
                name={['wallConfig', 'tableReadOnlyList']}
                label="只读表"
                mode="tags"
                placeholder="输入表名后回车，如 sys_user"
                tooltip="允许 SELECT，禁止对这些表的 INSERT / UPDATE / DELETE（支持 schema.table）"
                colProps={{ span: 24 }}
                fieldProps={{ disabled: off, tokenSeparators: [','] }}
              />
              <ProFormSelect
                name={['wallConfig', 'functionBlackList']}
                label="函数黑名单"
                mode="tags"
                placeholder="如 sleep、load_file"
                tooltip="危险函数名（小写）"
                colProps={{ span: 24 }}
                fieldProps={{ disabled: off, tokenSeparators: [','] }}
              />
            </>
          );
        }}
      </ProFormDependency>
    </ProFormGroup>
  );
};

export default DataSourceWallForm;
