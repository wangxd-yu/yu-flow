import React, { useEffect } from 'react';
import { Form, Input, Select, InputNumber, Tabs, Button, Radio, Typography } from 'antd';
import { PlusOutlined, DeleteOutlined } from '@ant-design/icons';
import type { Node } from '@antv/x6';
import { createId } from '../../utils/id';

const { TextArea } = Input;
const { Option } = Select;

interface HttpNodeConfigProps {
    node?: Node;
    data: Record<string, any>;
    onChange: (data: Record<string, any>) => void;
}

export default function HttpNodeConfig({ node, data, onChange }: HttpNodeConfigProps) {
    const [form] = Form.useForm();

    // Init form
    useEffect(() => {
        // Ensure data is in Array format for Form.List
        // Should have been migrated by NodeComponent, but just in case:
        const d = { ...data };
        if (d.params && !Array.isArray(d.params)) {
            d.params = Object.entries(d.params).map(([k, v]) => ({ id: createId('p'), key: k, value: v }));
        }
        if (d.headers && !Array.isArray(d.headers)) {
            d.headers = Object.entries(d.headers).map(([k, v]) => ({ id: createId('h'), key: k, value: v }));
        }

        form.setFieldsValue({
            method: 'GET',
            bodyType: 'json',
            timeout: 30000,
            params: [],
            headers: [],
            formData: [],
            ...d,
            body: typeof d.body === 'object' ? JSON.stringify(d.body, null, 2) : (d.body || '')
        });
    }, [data, form]);

    const handleValuesChange = (changedValues: any, allValues: any) => {
        // Direct sync for List-based data structure
        // Note: For 'body', we might want to validate JSON, but raw string is fine for now.
        onChange(allValues);
    };

    const renderKeyValueList = (name: string, prefix: string, placeholderKey = "Key", placeholderValue = "Value") => (
        <Form.List name={name}>
            {(fields, { add, remove }) => (
                <>
                    {fields.map(({ key, name, ...restField }) => (
                        <div key={key} style={{ display: 'flex', gap: 8, marginBottom: 8, alignItems: 'center' }}>
                            <Form.Item
                                {...restField}
                                name={[name, 'key']}
                                style={{ marginBottom: 0, flex: 1 }}
                            >
                                <Input placeholder={placeholderKey} size="small" />
                            </Form.Item>
                            <Form.Item
                                {...restField}
                                name={[name, 'value']}
                                style={{ marginBottom: 0, flex: 1 }}
                            >
                                <Input placeholder={placeholderValue} size="small" />
                            </Form.Item>
                            <DeleteOutlined
                                onClick={() => remove(name)}
                                style={{ color: '#ff4d4f', cursor: 'pointer', padding: 4 }}
                            />
                        </div>
                    ))}
                    <Button
                        type="dashed"
                        onClick={() => add({ id: createId(prefix), key: '', value: '' })}
                        block
                        icon={<PlusOutlined />}
                        size="small"
                    >
                        Add
                    </Button>
                </>
            )}
        </Form.List>
    );

    const items = [
        {
            key: 'general',
            label: 'General',
            children: (
                <div style={{ paddingTop: 16 }}>
                    <Form.Item label="Method" name="method" style={{ marginBottom: 16 }}>
                        <Select>
                            <Option value="GET">GET</Option>
                            <Option value="POST">POST</Option>
                            <Option value="PUT">PUT</Option>
                            <Option value="DELETE">DELETE</Option>
                            <Option value="PATCH">PATCH</Option>
                        </Select>
                    </Form.Item>
                    <Form.Item label="URL" name="url" style={{ marginBottom: 16 }}>
                        <Input
                            addonBefore={
                                <Form.Item name="method" noStyle>
                                    <Select style={{ width: 90 }}>
                                        <Option value="GET">GET</Option>
                                        <Option value="POST">POST</Option>
                                        <Option value="PUT">PUT</Option>
                                        <Option value="DELETE">DEL</Option>
                                    </Select>
                                </Form.Item>
                            }
                            placeholder="https://api.example.com"
                        />
                    </Form.Item>
                    <div style={{ fontSize: 12, color: '#8c8c8c', marginTop: -8, marginBottom: 16 }}>
                        支持 {'${var}'} 变量替换
                    </div>

                    <Form.Item label="Success Condition (Expression)" name="successCondition" tooltip="e.g. status == 200">
                        <Input placeholder="status == 200" />
                    </Form.Item>
                </div>
            )
        },
        {
            key: 'params',
            label: 'Params',
            children: <div style={{ paddingTop: 16 }}>{renderKeyValueList('params', 'p')}</div>
        },
        {
            key: 'headers',
            label: 'Headers',
            children: <div style={{ paddingTop: 16 }}>{renderKeyValueList('headers', 'h', 'Header', 'Value')}</div>
        },
        {
            key: 'body',
            label: 'Body',
            children: (
                <div style={{ paddingTop: 16, height: '100%', display: 'flex', flexDirection: 'column' }}>
                    <Form.Item name="bodyType" style={{ marginBottom: 16 }}>
                        <Radio.Group buttonStyle="solid">
                            <Radio.Button value="none">None</Radio.Button>
                            <Radio.Button value="json">JSON</Radio.Button>
                            <Radio.Button value="form-data">Form</Radio.Button>
                        </Radio.Group>
                    </Form.Item>

                    <Form.Item noStyle shouldUpdate={(prev, curr) => prev.bodyType !== curr.bodyType}>
                        {({ getFieldValue }) => {
                            const type = getFieldValue('bodyType');
                            if (type === 'form-data') {
                                return renderKeyValueList('formData', 'f', 'Field', 'Value');
                            }
                            if (type === 'none') {
                                return <div style={{ color: '#999', textAlign: 'center' }}>No Body Content</div>;
                            }
                            // Default JSON
                            return (
                                <Form.Item name="body" style={{ flex: 1, marginBottom: 0 }}>
                                    <TextArea
                                        style={{ minHeight: 300, fontFamily: 'monospace', resize: 'vertical' }}
                                        placeholder="{}"
                                    />
                                </Form.Item>
                            );
                        }}
                    </Form.Item>
                </div>
            )
        },
        {
            key: 'settings',
            label: 'Settings',
            children: (
                <div style={{ paddingTop: 16 }}>
                    <Form.Item label="Timeout (ms)" name="timeout">
                        <InputNumber min={0} style={{ width: '100%' }} />
                    </Form.Item>
                </div>
            )
        }
    ];

    return (
        <div style={{ height: '100%', display: 'flex', flexDirection: 'column', padding: 24 }}>
            <Form
                form={form}
                layout="vertical"
                onValuesChange={handleValuesChange}
                style={{ flex: 1, display: 'flex', flexDirection: 'column' }}
                initialValues={{ method: 'GET', bodyType: 'json', timeout: 30000 }}
            >
                <Tabs
                    items={items}
                    defaultActiveKey="general"
                    style={{ flex: 1, height: 0, overflow: 'hidden' }}
                />
            </Form>
        </div>
    );
}
