import React, { useEffect, useState } from 'react';
import { Form, Select, Radio, Input, Button, Space, Typography, Divider, Card } from 'antd';
import { PlusOutlined, DeleteOutlined } from '@ant-design/icons';
import CodeEditor from '../../../components/CodeEditor';
import type { Node } from '@antv/x6';
import { DatabaseNodeData, DatabaseVariable } from './DatabaseNode';
import { createId } from '../../../utils/id';
import { queryDataSourceList } from '@/pages/flow/dataSource/services/dataSource';

const { Text, Title } = Typography;

interface DatabaseNodeConfigProps {
    node: Node;
}

// 与节点组件一致的转换函数
function inputsToVars(inputs?: Record<string, any>): DatabaseVariable[] {
    if (!inputs || typeof inputs !== 'object') return [];
    return Object.entries(inputs).map(([key, val]) => ({
        id: createId('var'),
        name: key,
        extractPath: typeof val === 'string' ? val : (val as any)?.extractPath || '',
    }));
}

function varsToInputs(vars: DatabaseVariable[]): Record<string, any> | undefined {
    const r: Record<string, any> = {};
    for (const v of vars) {
        if (v.name) r[v.name] = { extractPath: v.extractPath };
    }
    return Object.keys(r).length > 0 ? r : undefined;
}

const DatabaseNodeConfig: React.FC<DatabaseNodeConfigProps> = ({ node }) => {
    const [form] = Form.useForm();
    const data = node.getData<DatabaseNodeData>();

    // ── 数据源选项 ──
    const [dataSourceOptions, setDataSourceOptions] = useState<{ label: string; value: string }[]>([]);
    useEffect(() => {
        queryDataSourceList()
            .then((list: any[]) => {
                setDataSourceOptions(list.map((item: any) => ({ label: item.name, value: item.code })));
            })
            .catch(() => {
                setDataSourceOptions([
                    { label: '主库 (Primary)', value: 'ds1' },
                    { label: '从库 (Replica)', value: 'ds2' },
                ]);
            });
    }, []);

    // ── 表单同步 ──
    useEffect(() => {
        let variables: DatabaseVariable[] = [];
        if (data?.__variables && data.__variables.length > 0) {
            variables = data.__variables;
        } else {
            variables = inputsToVars(data?.inputs);
        }
        if (variables.length === 0 || variables[variables.length - 1].name) {
            variables.push({ id: createId('var'), name: '', extractPath: '' });
        }

        const initialData = {
            datasourceId: data?.datasourceId,
            sqlType: data?.sqlType || 'SELECT',
            returnType: data?.returnType,
            sql: data?.sql || '',
            variables,
        };
        if (initialData.sqlType === 'SELECT' && !initialData.returnType) {
            initialData.returnType = 'LIST';
        }
        form.setFieldsValue(initialData);
    }, [data, form]);

    // ── 值变化 ──
    const handleValuesChange = (_changedValues: any, allValues: any) => {
        const { variables, ...rest } = allValues;

        if (_changedValues.sqlType === 'SELECT' && !rest.returnType) {
            rest.returnType = 'LIST';
            form.setFieldValue('returnType', 'LIST');
        }

        const validVars = (variables || []).filter((v: any) => v);
        node.setData({
            ...data,
            ...rest,
            inputs: varsToInputs(validVars),
            __variables: validVars,
        });
    };

    return (
        <div style={{ padding: '0 4px', overflowY: 'auto', height: '100%' }}>
            <Form
                form={form}
                layout="vertical"
                onValuesChange={handleValuesChange}
                initialValues={{ sqlType: 'SELECT', returnType: 'LIST' }}
                size="small"
            >
                {/* ── 基本配置 ── */}
                <Card
                    size="small"
                    title={<Text strong style={{ fontSize: 13 }}>基本配置</Text>}
                    style={{ marginBottom: 12, borderRadius: 8 }}
                    styles={{ body: { padding: '12px 16px' } }}
                >
                    <Form.Item
                        label="数据源"
                        name="datasourceId"
                        style={{ marginBottom: 12 }}
                    >
                        <Select
                            options={dataSourceOptions}
                            placeholder="选择数据源..."
                            allowClear
                        />
                    </Form.Item>

                    <Form.Item label="操作类型" name="sqlType" style={{ marginBottom: 12 }}>
                        <Radio.Group buttonStyle="solid" size="small">
                            <Radio.Button value="SELECT">SELECT</Radio.Button>
                            <Radio.Button value="INSERT">INSERT</Radio.Button>
                            <Radio.Button value="UPDATE">UPDATE</Radio.Button>
                            <Radio.Button value="DELETE">DELETE</Radio.Button>
                        </Radio.Group>
                    </Form.Item>

                    <Form.Item noStyle shouldUpdate={(prev, curr) => prev.sqlType !== curr.sqlType}>
                        {({ getFieldValue }) =>
                            getFieldValue('sqlType') === 'SELECT' ? (
                                <Form.Item label="返回类型" name="returnType" style={{ marginBottom: 0 }}>
                                    <Radio.Group size="small">
                                        <Radio value="LIST">LIST (列表)</Radio>
                                        <Radio value="OBJECT">OBJECT (单对象)</Radio>
                                        <Radio value="PAGE">PAGE (分页)</Radio>
                                    </Radio.Group>
                                </Form.Item>
                            ) : null
                        }
                    </Form.Item>
                </Card>

                {/* ── SQL 编辑 ── */}
                <Card
                    size="small"
                    title={<Text strong style={{ fontSize: 13 }}>SQL 语句</Text>}
                    style={{ marginBottom: 12, borderRadius: 8 }}
                    styles={{ body: { padding: '8px' } }}
                >
                    <Form.Item name="sql" style={{ marginBottom: 0 }} tooltip="支持 SQL 语法高亮">
                        <CodeEditor
                            value={form.getFieldValue('sql') || ''}
                            onChange={(value) => {
                                form.setFieldValue('sql', value);
                                handleValuesChange({ sql: value }, form.getFieldsValue());
                            }}
                            language="sql"
                            height="300px"
                            lineNumbers={true}
                            fontSize={12}
                            style={{ border: '1px solid #e8e8e8' }}
                        />
                    </Form.Item>
                </Card>

                {/* ── 变量映射 ── */}
                <Card
                    size="small"
                    title={<Text strong style={{ fontSize: 13 }}>变量映射</Text>}
                    style={{ borderRadius: 8 }}
                    styles={{ body: { padding: '12px 16px' } }}
                >
                    <Form.List name="variables">
                        {(fields, { add, remove }) => (
                            <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
                                {fields.map(({ key, name, ...restField }, index) => {
                                    const currentVars = form.getFieldValue('variables') || [];
                                    const isPlaceholder =
                                        index === currentVars.length - 1 && !currentVars[index]?.name;

                                    if (isPlaceholder) {
                                        return (
                                            <div
                                                key={key}
                                                onClick={() => {
                                                    const vars = form.getFieldValue('variables') || [];
                                                    if (vars.length > 0 && !vars[vars.length - 1]?.name) {
                                                        form.setFieldValue(['variables', vars.length - 1, 'name'], `var${vars.length}`);
                                                    }
                                                    add({ id: createId('var'), name: '', extractPath: '' });
                                                }}
                                                style={{
                                                    height: 36,
                                                    display: 'flex',
                                                    alignItems: 'center',
                                                    padding: '0 12px',
                                                    gap: 6,
                                                    cursor: 'pointer',
                                                    backgroundColor: '#fafafa',
                                                    borderRadius: 6,
                                                    border: '1px dashed #d9d9d9',
                                                    transition: 'all 0.2s',
                                                }}
                                            >
                                                <PlusOutlined style={{ color: '#1677ff', fontSize: 12 }} />
                                                <Text style={{ fontSize: 12, color: '#8c8c8c' }}>添加变量</Text>
                                            </div>
                                        );
                                    }

                                    return (
                                        <div
                                            key={key}
                                            style={{
                                                display: 'flex',
                                                gap: 6,
                                                alignItems: 'center',
                                                backgroundColor: '#fafafa',
                                                padding: '6px 8px',
                                                borderRadius: 6,
                                            }}
                                        >
                                            <Form.Item {...restField} name={[name, 'id']} noStyle>
                                                <Input type="hidden" />
                                            </Form.Item>
                                            <Form.Item
                                                {...restField}
                                                name={[name, 'name']}
                                                rules={[{ required: true, message: '必填' }]}
                                                style={{ marginBottom: 0, flex: 1 }}
                                            >
                                                <Input placeholder="变量名" size="small" />
                                            </Form.Item>
                                            <Text style={{ color: '#bfbfbf', fontSize: 12 }}>=</Text>
                                            <Form.Item
                                                {...restField}
                                                name={[name, 'extractPath']}
                                                style={{ marginBottom: 0, flex: 1.5 }}
                                            >
                                                <Input placeholder="$.body.id" size="small" />
                                            </Form.Item>
                                            <Button
                                                type="text"
                                                danger
                                                size="small"
                                                icon={<DeleteOutlined />}
                                                onClick={() => remove(name)}
                                            />
                                        </div>
                                    );
                                })}

                                {fields.length === 0 && (
                                    <div
                                        style={{
                                            padding: 12,
                                            textAlign: 'center',
                                            background: '#fafafa',
                                            borderRadius: 6,
                                            color: '#bfbfbf',
                                            fontSize: 12,
                                        }}
                                    >
                                        暂无变量映射
                                    </div>
                                )}
                            </div>
                        )}
                    </Form.List>
                </Card>
            </Form>
        </div>
    );
};

export default DatabaseNodeConfig;
