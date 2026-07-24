import React from 'react';
import { Graph, Edge, Shape } from '@antv/x6';

export default function MinimalX6Test() {
    const containerRef = React.useRef<HTMLDivElement>(null);
    const graphRef = React.useRef<Graph | null>(null);

    React.useEffect(() => {
        if (!containerRef.current || graphRef.current) return;

        console.log('🚀 初始化最简单的Graph');

        // 最简单的Graph配置
        const graph = new Graph({
            container: containerRef.current,
            width: 800,
            height: 600,
            background: { color: '#f5f5f5' },
            grid: { size: 10, visible: true },
            connecting: {
                router: 'manhattan',
                connector: {
                    name: 'rounded',
                    args: {
                        radius: 8,
                    },
                },
                anchor: 'center',
                connectionPoint: 'anchor',
                allowBlank: false,
                snap: {
                    radius: 20,
                },
                createEdge() {
                    return new Shape.Edge({
                        attrs: {
                            line: {
                                stroke: '#A2B1C3',
                                strokeWidth: 2,
                                targetMarker: {
                                    name: 'block',
                                    width: 12,
                                    height: 8,
                                },
                            },
                        },
                        zIndex: 0,
                    })
                },
                validateConnection({ targetMagnet }) {
                    return !!targetMagnet
                },
            },
            highlighting: {
                magnetAdsorbed: {
                    name: 'stroke',
                    args: {
                        attrs: {
                            fill: '#5F95FF',
                            stroke: '#5F95FF',
                        },
                    },
                },
            },
        });

        graphRef.current = graph;

        // 添加两个最简单的矩形节点
        const node1 = graph.addNode({
            id: 'node1',
            shape: 'rect',
            x: 100,
            y: 100,
            width: 100,
            height: 60,
            label: '节点1',
            attrs: {
                body: {
                    fill: '#ffffff',
                    stroke: '#333333',
                    strokeWidth: 2,
                },
                label: {
                    fill: '#000000',
                    fontSize: 14,
                },
            },
            ports: {
                groups: {
                    right: {
                        position: 'right',
                        attrs: {
                            circle: {
                                r: 6,
                                magnet: true,
                                stroke: '#31d0c6',
                                strokeWidth: 2,
                                fill: '#fff',
                            },
                        },
                    },
                    left: {
                        position: 'left',
                        attrs: {
                            circle: {
                                r: 6,
                                magnet: true,
                                stroke: '#31d0c6',
                                strokeWidth: 2,
                                fill: '#fff',
                            },
                        },
                    },
                },
                items: [
                    { id: 'right', group: 'right' },
                    { id: 'left', group: 'left' },
                ],
            },
        });

        const node2 = graph.addNode({
            id: 'node2',
            shape: 'rect',
            x: 400,
            y: 100,
            width: 100,
            height: 60,
            label: '节点2',
            attrs: {
                body: {
                    fill: '#ffffff',
                    stroke: '#333333',
                    strokeWidth: 2,
                },
                label: {
                    fill: '#000000',
                    fontSize: 14,
                },
            },
            ports: {
                groups: {
                    right: {
                        position: 'right',
                        attrs: {
                            circle: {
                                r: 6,
                                magnet: true,
                                stroke: '#31d0c6',
                                strokeWidth: 2,
                                fill: '#fff',
                            },
                        },
                    },
                    left: {
                        position: 'left',
                        attrs: {
                            circle: {
                                r: 6,
                                magnet: true,
                                stroke: '#31d0c6',
                                strokeWidth: 2,
                                fill: '#fff',
                            },
                        },
                    },
                },
                items: [
                    { id: 'right', group: 'right' },
                    { id: 'left', group: 'left' },
                ],
            },
        });

        console.log('✅ 节点已创建:', node1.id, node2.id);

        return () => {
            graph.dispose();
        };
    }, []);

    return (
        <div style={{ padding: 20 }}>
            <h2>最简单的X6测试</h2>
            <p>从节点1的右侧端口拖拽到节点2的左侧端口</p>
            <div
                ref={containerRef}
                style={{
                    border: '1px solid #ddd',
                    borderRadius: 4,
                    marginTop: 10,
                }}
            />
        </div>
    );
}
