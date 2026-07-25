import { parseCurl, tokenizeShell, exampleJsonToSchemaNodes } from '../parseCurl';

describe('tokenizeShell', () => {
  it('handles single and double quotes', () => {
    expect(tokenizeShell(`curl -H 'A: 1' -H "B: 2" http://x/y`)).toEqual([
      'curl', '-H', 'A: 1', '-H', 'B: 2', 'http://x/y',
    ]);
  });

  it('joins line continuations', () => {
    const tokens = tokenizeShell("curl \\\n  -X POST \\\n  'http://a/b'");
    expect(tokens).toEqual(['curl', '-X', 'POST', 'http://a/b']);
  });
});

describe('parseCurl', () => {
  it('parses GET with query', () => {
    const { draft, error } = parseCurl(
      `curl -X GET 'http://localhost:8080/yu-demo/host-ping?name=demo&x=1'`,
    );
    expect(error).toBeUndefined();
    expect(draft?.method).toBe('GET');
    expect(draft?.path).toBe('/yu-demo/host-ping');
    expect(draft?.query.map((q) => q.name)).toEqual(['name', 'x']);
    expect(draft?.query.find((q) => q.name === 'name')?.defaultValue).toBe('demo');
    expect(draft?.bodyType).toBe('none');
  });

  it('parses POST JSON body and headers', () => {
    const { draft, error } = parseCurl(`
      curl -X POST 'https://api.example.com/orders' \\
        -H 'Content-Type: application/json' \\
        -H 'Authorization: Bearer t' \\
        -d '{"id":1,"ok":true,"nested":{"a":"b"}}'
    `);
    expect(error).toBeUndefined();
    expect(draft?.method).toBe('POST');
    expect(draft?.path).toBe('/orders');
    expect(draft?.bodyType).toBe('json');
    expect(draft?.headers.some((h) => h.name === 'Authorization')).toBe(true);
    const id = draft?.body.find((n) => n.name === 'id');
    expect(id?.type).toBe('integer');
    const nested = draft?.body.find((n) => n.name === 'nested');
    expect(nested?.type).toBe('object');
    expect(nested?.children?.[0]?.name).toBe('a');
  });

  it('infers POST when -d present without -X', () => {
    const { draft } = parseCurl(`curl 'http://h/p' -d 'a=1&b=2'`);
    expect(draft?.method).toBe('POST');
    expect(draft?.bodyType).toBe('x-www-form-urlencoded');
    expect(draft?.body.map((n) => n.name).sort()).toEqual(['a', 'b']);
  });

  it('supports -G moving data to query', () => {
    const { draft } = parseCurl(`curl -G 'http://h/search' -d 'q=flow'`);
    expect(draft?.method).toBe('GET');
    expect(draft?.bodyType).toBe('none');
    expect(draft?.query.some((q) => q.name === 'q' && q.defaultValue === 'flow')).toBe(true);
  });

  it('warns and skips file form fields', () => {
    const { draft, error } = parseCurl(
      `curl -X POST http://h/upload -F 'meta=ok' -F 'file=@/tmp/a.png'`,
    );
    expect(error).toBeUndefined();
    expect(draft?.bodyType).toBe('form-data');
    expect(draft?.body.some((n) => n.name === 'meta')).toBe(true);
    expect(draft?.warnings.some((w) => w.includes('文件字段'))).toBe(true);
  });

  it('returns error without url', () => {
    const { error } = parseCurl('curl -X GET');
    expect(error).toMatch(/URL/);
  });

  it('returns error without curl keyword', () => {
    const { error } = parseCurl('wget http://a');
    expect(error).toMatch(/curl/);
  });
});

describe('exampleJsonToSchemaNodes', () => {
  it('flattens root object fields', () => {
    const nodes = exampleJsonToSchemaNodes({ a: 1, b: 'x' });
    expect(nodes.map((n) => n.name)).toEqual(['a', 'b']);
    expect(nodes[0].type).toBe('integer');
  });
});
