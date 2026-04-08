import { Graph } from '@antv/x6';
import { registerStartNode } from './nodes/StartNode';

let registered = false;

export function registerUmlShapes() {
  if (registered) return;
  registered = true;

  // Register StartNode
  registerStartNode();

  Graph.registerNode(
    'uml-input',
    {
      width: 42,
      height: 42,
      markup: [{ tagName: 'circle', selector: 'body' }],
      attrs: {
        body: {
          cx: 21,
          cy: 21,
          r: 18,
          stroke: '#8c8c8c',
          strokeWidth: 2,
          fill: '#f0f0f0',
        },
      },
      ports: {
        groups: {
          bottom: {
            position: 'bottom',
            attrs: {
              circle: { r: 4, magnet: true, stroke: '#8c8c8c', strokeWidth: 1, fill: '#fff' },
            },
          },
        },
        items: [{ id: 'bottom', group: 'bottom' }],
      },
    },
    true,
  );

  Graph.registerNode(
    'uml-return',
    {
      width: 46,
      height: 46,
      markup: [
        { tagName: 'circle', selector: 'outer' },
        { tagName: 'circle', selector: 'inner' },
      ],
      attrs: {
        outer: {
          cx: 23,
          cy: 23,
          r: 20,
          stroke: '#8c8c8c',
          strokeWidth: 2,
          fill: '#f0f0f0',
        },
        inner: {
          cx: 23,
          cy: 23,
          r: 12,
          fill: '#8c8c8c',
        },
      },
      ports: {
        groups: {
          top: {
            position: 'top',
            attrs: {
              circle: { r: 4, magnet: true, stroke: '#8c8c8c', strokeWidth: 1, fill: '#fff' },
            },
          },
        },
        items: [{ id: 'top', group: 'top' }],
      },
    },
    true,
  );
}

