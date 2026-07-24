// ============================================================================
// send-mail/index.tsx — 发送邮件节点注册（in:payload + 变量行）
// ============================================================================

import React from 'react';
import { Input } from 'antd';
import type { DslPort } from '../../../types';
import type { NodeRegistration, PropertyEditorProps } from '../../types';
import { SendMailNodeComponent, SEND_MAIL_LAYOUT, SEND_MAIL_COLOR } from './SendMailNodeComponent';
import { PropertyField, PropertyHint, PropertySection } from '../../shared/PropertyPanel';
import { PAYLOAD_PORT_ID, PAYLOAD_PORT_Y } from '../../shared/usePayloadEntryPort';
import { NODE_FOOTER_HEIGHT, NODE_FOOTER_PORT_OFFSET_Y } from '../../shared/useNodeSelection';

function SendMailEditor({ data, onChange }: PropertyEditorProps) {
    return (
        <PropertySection title="邮件配置">
            <PropertyHint>
                左上角 <code>in:payload</code> 为数据总入口；卡片变量行可逐项映射。字段支持{' '}
                <code>{'${var}'}</code>（var 来自 inputs）。SMTP 见「系统配置 → 邮件 SMTP」。
            </PropertyHint>
            <PropertyField label="收件人 to">
                <Input
                    size="small"
                    value={data.to || ''}
                    placeholder="a@b.com, c@d.com 或 ${email}"
                    onChange={(e) => onChange({ to: e.target.value })}
                />
            </PropertyField>
            <PropertyField label="抄送 cc">
                <Input
                    size="small"
                    value={data.cc || ''}
                    onChange={(e) => onChange({ cc: e.target.value })}
                />
            </PropertyField>
            <PropertyField label="密送 bcc">
                <Input
                    size="small"
                    value={data.bcc || ''}
                    onChange={(e) => onChange({ bcc: e.target.value })}
                />
            </PropertyField>
            <PropertyField label="主题 subject">
                <Input
                    size="small"
                    value={data.subject || ''}
                    onChange={(e) => onChange({ subject: e.target.value })}
                />
            </PropertyField>
            <PropertyField label="纯文本 text">
                <Input.TextArea
                    size="small"
                    rows={4}
                    value={data.text || ''}
                    onChange={(e) => onChange({ text: e.target.value })}
                />
            </PropertyField>
            <PropertyField label="HTML html" extra="可选">
                <Input.TextArea
                    size="small"
                    rows={4}
                    value={data.html || ''}
                    placeholder="<p>可选 HTML 正文</p>"
                    onChange={(e) => onChange({ html: e.target.value })}
                />
            </PropertyField>
        </PropertySection>
    );
}

const buildSendMailPortItems = (ports: DslPort[]) => {
    const w = SEND_MAIL_LAYOUT.width;
    const outY = SEND_MAIL_LAYOUT.height - NODE_FOOTER_HEIGHT + NODE_FOOTER_PORT_OFFSET_Y;
    const seen = new Set<string>();
    const items: any[] = [];

    for (const p of ports) {
        if (seen.has(p.id) || p.id === 'in' || p.id.startsWith('in:var:')) continue;
        seen.add(p.id);
        if (p.id === PAYLOAD_PORT_ID) {
            items.push({
                id: PAYLOAD_PORT_ID,
                group: 'absolute-in-solid',
                args: { x: 0, y: PAYLOAD_PORT_Y, dx: 0 },
            });
        } else if (p.id === 'out') {
            items.push({
                id: 'out',
                group: 'absolute-out-solid',
                args: { x: w, y: outY, dx: 0 },
            });
        }
    }
    if (!seen.has(PAYLOAD_PORT_ID)) {
        items.unshift({
            id: PAYLOAD_PORT_ID,
            group: 'absolute-in-solid',
            args: { x: 0, y: PAYLOAD_PORT_Y, dx: 0 },
        });
    }
    if (!seen.has('out')) {
        items.push({
            id: 'out',
            group: 'absolute-out-solid',
            args: { x: w, y: outY, dx: 0 },
        });
    }
    return items;
};

export const sendMailNodeRegistration: NodeRegistration = {
    type: 'sendMail',
    label: '发送邮件 (Send Mail)',
    category: '调用节点',
    color: SEND_MAIL_COLOR,
    tagColor: 'orange',
    description:
        '通过平台 SMTP 发送邮件。\n\n' +
        '· 左上角 in:payload 总入口；卡片内可新增变量行\n' +
        '· to/subject/text/html 支持 ${变量名}\n' +
        '· 需先在「系统配置 → 邮件 SMTP」启用',
    sortOrder: 45,
    hasInputs: true,

    shape: {
        shapeName: 'flow-send-mail',
        kind: 'react',
        component: SendMailNodeComponent,
        reactPorts: {
            items: [
                {
                    id: PAYLOAD_PORT_ID,
                    group: 'absolute-in-solid',
                    args: { x: 0, y: PAYLOAD_PORT_Y, dx: 0 },
                },
                {
                    id: 'out',
                    group: 'absolute-out-solid',
                    args: { x: SEND_MAIL_LAYOUT.width, y: SEND_MAIL_LAYOUT.outPortY, dx: 0 },
                },
            ],
        },
    },

    defaults: {
        ports: [
            { id: PAYLOAD_PORT_ID, group: 'absolute-in-solid' },
            { id: 'out', group: 'absolute-out-solid' },
        ],
        data: {
            to: '',
            cc: '',
            bcc: '',
            subject: '',
            text: '',
            html: '',
            inputs: {},
        },
        size: { width: SEND_MAIL_LAYOUT.width, height: SEND_MAIL_LAYOUT.height },
    },

    importConfig: {
        portMode: 'manual',
        buildPortItems: buildSendMailPortItems,
    },

    buildLabel: (data) =>
        data.subject ? `Mail: ${String(data.subject).slice(0, 24)}` : 'Send Mail',

    PropertyEditor: SendMailEditor,
};
