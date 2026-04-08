import type { NodeDefinition } from './nodeDefinition';
import { registerUmlShapes } from './shapes';
import { createInputData, inputNodeDefinition, registerStartNode } from './nodes/StartNode';
import { callNodeDefinition, createCallData } from './nodes/call';
import { apiNodeDefinition, createApiData } from './nodes/api';
import { setNodeDefinition, createSetData } from './nodes/set';
import { conditionNodeDefinition, createConditionData, registerConditionNode } from './nodes/condition';
import { ifNodeDefinition, createIfData } from './nodes/if';
import { switchNodeDefinition, createSwitchData } from './nodes/switch';
import { parallelNodeDefinition, createParallelData } from './nodes/parallel';
import { returnNodeDefinition, createReturnData } from './nodes/return';
import { databaseNodeDefinition, createDatabaseData } from './nodes/database';
import type { NodeData, NodeType } from '../types';

export function ensureRegistry() {
  registerUmlShapes();
  registerStartNode();
  registerConditionNode();
}

export const nodeDefinitions: NodeDefinition[] = [
  inputNodeDefinition,
  callNodeDefinition,
  apiNodeDefinition,
  setNodeDefinition,
  ifNodeDefinition,
  switchNodeDefinition,
  conditionNodeDefinition,
  parallelNodeDefinition,
  returnNodeDefinition,
  databaseNodeDefinition,
];

export function getNodeDefinition(type: NodeType) {
  return nodeDefinitions.find((d) => d.type === type) || null;
}

export function createDefaultNodeData(type: NodeType): NodeData | null {
  if (type === 'input') return createInputData();
  if (type === 'call') return createCallData();
  if (type === 'api') return createApiData();
  if (type === 'set') return createSetData();
  if (type === 'if') return createIfData();
  if (type === 'switch') return createSwitchData();
  if (type === 'condition') return createConditionData();
  if (type === 'parallel') return createParallelData();
  if (type === 'return') return createReturnData();
  if (type === 'database') return createDatabaseData();
  return null;
}

