import type React from 'react';
import type { NodeMetadata } from '@antv/x6';
import type { NodeData, NodeType } from '../types';

export type NodeDefinition<T extends NodeType = NodeType> = {
  type: T;
  label: string;
  category: string;
  buildNodeConfig: (data: NodeData, position?: { x: number; y: number }) => NodeMetadata;
  getDisplayLabel: (data: NodeData) => string;
  renderProperties?: (params: {
    data: NodeData;
    onChange: (next: NodeData) => void;
    setValidationError: (message: string | null) => void;
  }) => React.ReactNode;
};

