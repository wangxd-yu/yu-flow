import React from 'react';
import { Graph, Edge } from '@antv/x6';
import { Button, Space, Typography } from 'antd';

const { Title, Paragraph } = Typography;

export type EdgeTestPanelProps = {
    graphRef: React.MutableRefObject<Graph | null>;
};

export default function EdgeTestPanel({ graphRef }: EdgeTestPanelProps) {
    const [testLog, setTestLog] = React.useState<string[]>([]);

    const addLog = (message: string) => {
        setTestLog(prev => [...prev, `[${new Date().toLocaleTimeString()}] ${message}`]);
    };

    const createTestNodes = () => {
        const graph = graphRef.current;
        if (!graph) {
            addLog('❌ Graph未初始化');
            return;
        }

        graph.clearCells();
        addLog('🧹 清空画布');

        // 创建两个简单的矩形节点
        const node1 = graph.addNode({
            id: 'test-node-1',
            x: 100,
            y: 100,
            width: 120,
            height: 60,
            shape: 'rect',
            label: '节点 1',
            attrs: {
                body: {
                    fill: '#f0f0f0',
                    stroke: '#8c8c8c',
                    strokeWidth: 1,
                },
                label: {
                    fill: '#000',
                    fontSize: 14,
                },
            },
            ports: {
                groups: {
                    out: {
                        position: 'right',
                        attrs: {
                            circle: {
                                r: 4,
                                magnet: true,
                                stroke: '#1677ff',
                                strokeWidth: 1,
                                fill: '#fff',
                            },
                        },
                    },
                    in: {
                        position: 'left',
                        attrs: {
                            circle: {
                                r: 4,
                                magnet: true,
                                stroke: '#1677ff',
                                strokeWidth: 1,
                                fill: '#fff',
                            },
                        },
                    },
                },
                items: [
                    { id: 'out', group: 'out' },
                    { id: 'in', group: 'in' },
                ],
            },
        });

        const node2 = graph.addNode({
            id: 'test-node-2',
            x: 400,
            y: 100,
            width: 120,
            height: 60,
            shape: 'rect',
            label: '节点 2',
            attrs: {
                body: {
                    fill: '#f0f0f0',
                    stroke: '#8c8c8c',
                    strokeWidth: 1,
                },
                label: {
                    fill: '#000',
                    fontSize: 14,
                },
            },
            ports: {
                groups: {
                    out: {
                        position: 'right',
                        attrs: {
                            circle: {
                                r: 4,
                                magnet: true,
                                stroke: '#1677ff',
                                strokeWidth: 1,
                                fill: '#fff',
                            },
                        },
                    },
                    in: {
                        position: 'left',
                        attrs: {
                            circle: {
                                r: 4,
                                magnet: true,
                                stroke: '#1677ff',
                                strokeWidth: 1,
                                fill: '#fff',
                            },
                        },
                    },
                },
                items: [
                    { id: 'out', group: 'out' },
                    { id: 'in', group: 'in' },
                ],
            },
        });

        addLog('✅ 创建了两个测试节点');
        addLog(`节点1: ${node1.id} at (100, 100)`);
        addLog(`节点2: ${node2.id} at (400, 100)`);
    };

    const createTestEdge = () => {
        const graph = graphRef.current;
        if (!graph) {
            addLog('❌ Graph未初始化');
            return;
        }

        const nodes = graph.getNodes();
        if (nodes.length < 2) {
            addLog('❌ 请先创建测试节点');
            return;
        }

        try {
            const edge = graph.addEdge({
                source: { cell: 'test-node-1', port: 'out' },
                target: { cell: 'test-node-2', port: 'in' },
                attrs: {
                    line: {
                        stroke: '#FF0000',
                        strokeWidth: 3,
                        targetMarker: {
                            name: 'classic',
                            size: 8,
                        },
                    },
                },
                router: { name: 'metro' },
                connector: { name: 'smooth' },
            });

            addLog('✅ 成功创建边');
            addLog(`边ID: ${edge.id}`);
            addLog(`边可见性: ${edge.visible}`);
            addLog(`边zIndex: ${edge.getZIndex()}`);
            addLog(`边属性: ${JSON.stringify(edge.getAttrs())}`);

            // 检查端口位置
            setTimeout(() => {
                const node1 = graph.getCellById('test-node-1') as any;
                const node2 = graph.getCellById('test-node-2') as any;

                if (node1 && node2) {
                    const port1 = node1.getPort?.('out');
                    const port2 = node2.getPort?.('in');

                    addLog(`📍 节点1端口out: ${JSON.stringify(port1)}`);
                    addLog(`📍 节点2端口in: ${JSON.stringify(port2)}`);

                    // 获取端口的绝对位置
                    const portPos1 = node1.getPortProp?.('out', 'args') as any;
                    const portPos2 = node2.getPortProp?.('in', 'args') as any;

                    addLog(`📍 端口1 args: ${JSON.stringify(portPos1)}`);
                    addLog(`📍 端口2 args: ${JSON.stringify(portPos2)}`);
                }

                // 检查边的source和target
                const source = edge.getSource();
                const target = edge.getTarget();
                addLog(`📍 边source: ${JSON.stringify(source)}`);
                addLog(`📍 边target: ${JSON.stringify(target)}`);

                // 🔍 检查红色粗线的DOM path
                const edgeView = graph.findViewByCell(edge);
                if (edgeView) {
                    const allPaths = edgeView.container.querySelectorAll('path');
                    addLog(`📍 红色粗线：找到 ${allPaths.length} 个path`);
                    allPaths.forEach((path, index) => {
                        const d = path.getAttribute('d');
                        const stroke = path.getAttribute('stroke');
                        const visible = d && d !== 'undefined' && d !== 'M0,0';
                        addLog(`📍 红色Path[${index}]: ${visible ? '✅可见' : '❌不可见'}, d="${d?.substring(0, 60)}", stroke=${stroke}`);
                    });
                } else {
                    addLog(`❌ 红色粗线EdgeView不存在`);
                }
            }, 100);
        } catch (error: any) {
            addLog(`❌ 创建边失败: ${error.message}`);
        }
    };

    const testDragConnection = () => {
        const graph = graphRef.current;
        if (!graph) {
            addLog('❌ Graph未初始化');
            return;
        }

        addLog('👆 请手动从节点1拖拽到节点2测试连线');
        addLog('观察是否能看到红色连线...');
    };

    const checkGraphConfig = () => {
        const graph = graphRef.current;
        if (!graph) {
            addLog('❌ Graph未初始化');
            return;
        }

        addLog('📋 Graph配置信息:');
        addLog(`- panning: ${JSON.stringify(graph.options.panning)}`);
        addLog(`- connecting: ${JSON.stringify(graph.options.connecting).substring(0, 100)}...`);
        addLog(`- highlighting: ${JSON.stringify(graph.options.highlighting)}`);
        addLog(`- interacting: ${JSON.stringify(graph.options.interacting)}`);
    };

    const testCreateEdgeForConnect = () => {
        const graph = graphRef.current;
        if (!graph) {
            addLog('❌ Graph未初始化');
            return;
        }

        const nodes = graph.getNodes();
        if (nodes.length < 2) {
            addLog('❌ 请先创建测试节点');
            return;
        }

        try {
            addLog('🧪 测试 createEdgeForConnect()...');

            // 使用与createEdgeForConnect相同的配置
            const testEdge = graph.addEdge({
                shape: 'edge',
                data: { kind: 'control' },
                source: { cell: 'test-node-1', port: 'out' },
                target: { cell: 'test-node-2', port: 'in' },
                attrs: {
                    line: {
                        stroke: '#8c8c8c',
                        strokeWidth: 2, // 增加到2，提高可见性
                        targetMarker: {
                            name: 'classic',
                            size: 8,
                            fill: '#8c8c8c',
                        },
                        fill: 'none',
                    },
                    wrap: {
                        strokeWidth: 10,
                        stroke: 'transparent',
                    }
                },
                router: { name: 'metro' },
                connector: { name: 'smooth' },
                zIndex: 0,
            });

            addLog('✅ 使用createEdgeForConnect配置创建边');
            addLog(`边ID: ${testEdge.id}`);
            addLog(`边可见性: ${testEdge.visible}`);
            addLog(`边zIndex: ${testEdge.getZIndex()}`);
            addLog(`边stroke: ${testEdge.attr('line/stroke')}`);
            addLog(`边strokeWidth: ${testEdge.attr('line/strokeWidth')}`);

            // 检查DOM
            setTimeout(() => {
                const edgeView = graph.findViewByCell(testEdge);
                if (edgeView) {
                    addLog(`📍 EdgeView存在`);

                    // 查找所有path元素
                    const allPaths = edgeView.container.querySelectorAll('path');
                    addLog(`📍 找到 ${allPaths.length} 个path元素`);

                    allPaths.forEach((path, index) => {
                        const d = path.getAttribute('d');
                        const stroke = path.getAttribute('stroke');
                        const strokeWidth = path.getAttribute('stroke-width');
                        const fill = path.getAttribute('fill');
                        const dataType = path.getAttribute('data-type');

                        addLog(`📍 Path[${index}] type="${dataType}", d="${d?.substring(0, 50)}", stroke=${stroke}, strokeWidth=${strokeWidth}, fill=${fill}`);

                        if (!d || d === 'undefined' || d === 'M0,0') {
                            addLog(`❌ Path[${index}] 路径为空或无效！`);
                        }
                    });

                    // 检查容器transform
                    const container = edgeView.container;
                    const transform = container.getAttribute('transform');
                    addLog(`📍 容器transform: ${transform || 'none'}`);
                } else {
                    addLog(`❌ EdgeView不存在！边未被渲染`);
                }
            }, 100);
        } catch (error: any) {
            addLog(`❌ 创建边失败: ${error.message}`);
        }
    };

    const testSimpleEdge = () => {
        const graph = graphRef.current;
        if (!graph) {
            addLog('❌ Graph未初始化');
            return;
        }

        const nodes = graph.getNodes();
        if (nodes.length < 2) {
            addLog('❌ 请先创建测试节点');
            return;
        }

        try {
            addLog('🧪 测试简单边（无router/connector）...');

            // 不使用router和connector
            const simpleEdge = new Edge({
                shape: 'edge',
                source: { cell: 'test-node-1', port: 'out' },
                target: { cell: 'test-node-2', port: 'in' },
                attrs: {
                    line: {
                        stroke: '#00FF00', // 绿色
                        strokeWidth: 3,
                    },
                },
                // 不设置router和connector
            });

            graph.addEdge(simpleEdge);

            addLog('✅ 创建简单边（绿色，无router）');
            addLog(`边ID: ${simpleEdge.id}`);

            setTimeout(() => {
                const edgeView = graph.findViewByCell(simpleEdge);
                if (edgeView) {
                    const allPaths = edgeView.container.querySelectorAll('path');
                    addLog(`📍 简单边：找到 ${allPaths.length} 个path`);
                    allPaths.forEach((path, index) => {
                        const d = path.getAttribute('d');
                        addLog(`📍 简单边Path[${index}]: d="${d?.substring(0, 80)}"`);
                    });
                }
            }, 100);
        } catch (error: any) {
            addLog(`❌ 创建简单边失败: ${error.message}`);
        }
    };

    const clearLog = () => {
        setTestLog([]);
    };

    return (
        <div style={{ padding: 16, height: '100%', overflow: 'auto', background: '#fafafa' }}>
            <Title level={4}>連線測試面板</Title>
            <Paragraph type="secondary">
                這是一個簡單的測試環境，用於逐步調試連線問題。
            </Paragraph>

            <Space direction="vertical" size="middle" style={{ width: '100%' }}>
                <Space wrap>
                    <Button type="primary" onClick={createTestNodes}>
                        1. 創建測試節點
                    </Button>
                    <Button onClick={createTestEdge}>
                        2a. 手動創建連線(紅色)
                    </Button>
                    <Button onClick={testCreateEdgeForConnect}>
                        2b. 測試createEdge配置(灰色)
                    </Button>
                    <Button onClick={testSimpleEdge}>
                        2c. 測試簡單邊(綠色,無router)
                    </Button>
                    <Button onClick={testDragConnection}>
                        3. 測試拖拽連線
                    </Button>
                    <Button onClick={checkGraphConfig}>
                        檢查Graph配置
                    </Button>
                    <Button danger onClick={clearLog}>
                        清空日誌
                    </Button>
                </Space>

                <div
                    style={{
                        background: '#fff',
                        padding: 12,
                        border: '1px solid #d9d9d9',
                        borderRadius: 4,
                        maxHeight: 400,
                        overflow: 'auto',
                        fontFamily: 'Monaco, Consolas, monospace',
                        fontSize: 12,
                    }}
                >
                    <div style={{ fontWeight: 'bold', marginBottom: 8 }}>測試日誌:</div>
                    {testLog.length === 0 ? (
                        <div style={{ color: '#999' }}>暂无日志，点击上方按钮开始测试...</div>
                    ) : (
                        testLog.map((log, index) => (
                            <div key={index} style={{ padding: '2px 0', borderBottom: '1px solid #f0f0f0' }}>
                                {log}
                            </div>
                        ))
                    )}
                </div>

                <div style={{ background: '#fff3cd', padding: 12, border: '1px solid #ffd666', borderRadius: 4 }}>
                    <div style={{ fontWeight: 'bold', marginBottom: 8 }}>💡 測試步驟建議:</div>
                    <ol style={{ margin: 0, paddingLeft: 20 }}>
                        <li>點擊"創建測試節點"，應該看到兩個灰色矩形</li>
                        <li>點擊"手動創建連線"，應該看到一條<span style={{ color: 'red', fontWeight: 'bold' }}>紅色粗線</span>連接兩個節點</li>
                        <li>如果第2步能看到紅線，說明靜態創建邊工作正常</li>
                        <li>點擊"測試拖拽連線"，然後手動從節點1的右側端口拖到節點2的左側端口</li>
                        <li>如果拖拽時看不到線，但連接完成後出現，說明是拖拽時臨時邊的渲染問題</li>
                        <li>點擊"檢查Graph配置"查看當前配置</li>
                    </ol>
                </div>
            </Space>
        </div>
    );
}
