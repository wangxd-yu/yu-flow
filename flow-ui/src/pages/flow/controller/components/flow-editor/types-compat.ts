// ============================================================================
// types-compat.ts
// 旧版类型兼容层 —— 让 graph.ts / FlowEditor.tsx 在迁移期间继续编译
// 迁移完成后可删除此文件
// ============================================================================

export type StepType = 'call' | 'api' | 'parallel' | 'condition' | 'if' | 'switch' | 'set' | 'return' | 'database';
export type NodeType = StepType | 'input';

export type VarType = 'string' | 'number' | 'boolean' | 'object' | 'array' | 'any';

export type VarDef = {
    key: string;
    name: string;
    type: VarType;
    required?: boolean;
};

export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'DELETE';

export type BaseStep = {
    id: string;
    type: StepType;
    name?: string;
    next?: Record<string, string | string[]>;
};

export type ServiceCallStep = BaseStep & {
    type: 'call';
    service: string;
    method: string;
    args: any[];
    output?: string;
    errorHandlers?: any[];
};

export type ApiStep = BaseStep & {
    type: 'api';
    url: string;
    method: HttpMethod;
    headers?: VarDef[];
    params?: VarDef[];
    body?: VarDef[];
    output?: string;
    errorHandlers?: any[];
};

export type SetStep = BaseStep & {
    type: 'set';
    assignments: { var: string; value: any }[];
};

export type ParallelStep = BaseStep & {
    type: 'parallel';
    branches: Step[][];
};

export type IfStep = BaseStep & {
    type: 'if';
    expression: string;
};

export type SwitchStep = BaseStep & {
    type: 'switch';
    expression: string;
    cases: string[];
};

export type ConditionCandidate = {
    expression: string;
    next?: string;
};

export type ConditionStep = BaseStep & {
    type: 'condition';
    candidates: ConditionCandidate[];
    params: VarDef[];
};

export type ReturnStep = BaseStep & {
    type: 'return';
    mode: 'response' | 'output';
    value?: string;
    response?: {
        statusCode: number;
        headers: VarDef[];
        body: VarDef[];
    };
};

export type Step =
    | ServiceCallStep
    | ApiStep
    | SetStep
    | ParallelStep
    | IfStep
    | SwitchStep
    | ConditionStep
    | ReturnStep;

export const UI_LAYOUT_KEY = '__ui_layout__';
export const UI_LAYOUT_GRAPH_KEY = '__graph_layout__';

export type UiGraphLayout = {
    nodes: Record<
        string,
        { x: number; y: number; width: number; height: number }
    >;
    edges: Record<string, { source?: string; target?: string }>;
};

export type NodeData =
    | { kind: 'step'; nodeType: StepType; step: Step }
    | { kind: 'ui'; nodeType: 'input'; name?: string; start?: any };

export type FlowDefinition = {
    id?: string;
    version?: string;
    name?: string;
    description?: string;
    args?: VarDef[];
    steps: Step[];
    errors?: any[];
    startStepId?: string;
    [UI_LAYOUT_KEY]?: UiGraphLayout;
};

export type FlowEditorProps = {
    value?: string;
    onChange?: (nextJsonScript: string) => void;
    height?: number;
};
