import { PageQueryTrait } from './impl/PageQueryTrait';
import { DetailQueryTrait } from './impl/DetailQueryTrait';
import { InsertTrait } from './impl/InsertTrait';
import { UpdateTrait } from './impl/UpdateTrait';
import { DeleteTrait } from './impl/DeleteTrait';
import { BatchDeleteTrait } from './impl/BatchDeleteTrait';
import { PublishTrait } from './impl/PublishTrait';
import { QuickEditTrait } from './impl/QuickEditTrait';
import type { FeatureTrait } from './types';

export * from './types';

export const TRAIT_LIST: FeatureTrait[] = [
  PageQueryTrait,
  DetailQueryTrait,
  InsertTrait,
  UpdateTrait,
  DeleteTrait,
  BatchDeleteTrait,
  PublishTrait,
  QuickEditTrait,
];
