import {
  formatBytes,
  parseContractParams,
  syncColumnsFromContract,
  buildParamsFromForm,
  FALLBACK_LABEL,
} from '../apiDataViewUtils';

describe('apiDataViewUtils', () => {
  describe('formatBytes', () => {
    it('should return fallback for invalid values', () => {
      expect(formatBytes(undefined)).toBe('-');
      expect(formatBytes(NaN)).toBe('-');
      expect(formatBytes(null as any)).toBe('-');
    });

    it('should format bytes', () => {
      expect(formatBytes(0)).toBe('0 B');
      expect(formatBytes(512)).toBe('512 B');
      expect(formatBytes(1024)).toBe('1.0 KB');
      expect(formatBytes(1024 * 1024)).toBe('1.00 MB');
    });
  });

  describe('parseContractParams', () => {
    it('should return empty array when contract is empty', () => {
      expect(parseContractParams('')).toEqual([]);
      expect(parseContractParams(undefined)).toEqual([]);
    });

    it('should parse query/path/body params', () => {
      const contract = {
        request: {
          query: [{ name: 'q1', title: 'Query 1' }],
          pathParams: [{ name: 'p1', description: 'Path 1' }],
          body: [{ name: 'b1' }],
        },
      };
      expect(parseContractParams(JSON.stringify(contract))).toEqual([
        { name: 'q1', title: 'Query 1', section: 'query' },
        { name: 'p1', title: 'Path 1', section: 'path' },
        { name: 'b1', section: 'body' },
      ]);
    });

    it('should skip nodes without name', () => {
      const contract = { request: { query: [{ title: 'No name' }] } };
      expect(parseContractParams(JSON.stringify(contract))).toEqual([]);
    });

    it('should handle invalid JSON', () => {
      expect(parseContractParams('{invalid')).toEqual([]);
    });
  });

  describe('syncColumnsFromContract', () => {
    it('should return prev when contract is empty', () => {
      expect(syncColumnsFromContract('', [{ field: 'a' } as any])).toEqual([{ field: 'a' }]);
      expect(syncColumnsFromContract(undefined, [])).toEqual([]);
    });

    it('should sync columns from response body', () => {
      const contract = {
        responses: {
          '200': {
            body: [
              { name: 'id', title: 'ID', description: 'identifier' },
              { name: 'name' },
            ],
          },
        },
      };
      const result = syncColumnsFromContract(JSON.stringify(contract), []);
      expect(result).toHaveLength(2);
      expect(result[0]).toMatchObject({ field: 'id', header: 'ID', exportable: true, visible: true });
      expect(result[1]).toMatchObject({ field: 'name', header: 'name', exportable: true, visible: true });
    });

    it('should preserve previous column config', () => {
      const contract = {
        responses: {
          '200': {
            body: [{ name: 'id', title: 'New ID' }],
          },
        },
      };
      const prev = [{ field: 'id', header: 'Old ID', exportable: false, visible: false, width: 100 }];
      const result = syncColumnsFromContract(JSON.stringify(contract), prev as any);
      expect(result[0]).toMatchObject({
        field: 'id',
        header: 'Old ID',
        exportable: false,
        visible: false,
        width: 100,
      });
    });

    it('should walk nested array/object children', () => {
      const contract = {
        responses: {
          '200': {
            body: [
              {
                name: 'items',
                type: 'array',
                children: [
                  { name: 'itemId', title: 'Item ID' },
                ],
              },
              {
                name: 'meta',
                type: 'object',
                children: [{ name: 'total' }],
              },
            ],
          },
        },
      };
      const result = syncColumnsFromContract(JSON.stringify(contract), []);
      expect(result.map((c) => c.field)).toEqual(['itemId', 'total']);
    });
  });

  describe('buildParamsFromForm', () => {
    it('should build query/path/body params from form values', () => {
      const paramForm = {
        getFieldsValue: () => ({
          'query__q1': 'value1',
          'path__p1': 'pathValue',
          'body__b1': { key: 'value' },
          'body__empty': '',
          'query__undefined': undefined,
          'query__null': null,
        }),
      } as any;
      const fields = [
        { name: 'q1', section: 'query' as const },
        { name: 'p1', section: 'path' as const },
        { name: 'b1', section: 'body' as const },
        { name: 'empty', section: 'query' as const },
        { name: 'undefined', section: 'query' as const },
        { name: 'null', section: 'query' as const },
      ];
      const result = buildParamsFromForm(paramForm, fields);
      expect(result.queryParams).toEqual({ q1: 'value1' });
      expect(result.pathParams).toEqual({ p1: 'pathValue' });
      expect(result.bodyParams).toEqual({ b1: { key: 'value' } });
    });
  });

  describe('FALLBACK_LABEL', () => {
    it('should contain known fallback labels', () => {
      expect(FALLBACK_LABEL.TEMPLATE_MISSING).toBe('未找到模板');
      expect(FALLBACK_LABEL.TEMPLATE_NO_LIST_PLACEHOLDER).toBe('模板缺少 {.字段} 占位符');
      expect(FALLBACK_LABEL.TEMPLATE_FILL_FAILED).toBe('模板填充失败');
    });
  });
});
