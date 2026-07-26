import { sanitizeAmisSchema } from '../amisSanitize';

describe('sanitizeAmisSchema', () => {
  const originalWindow = global.window;

  beforeEach(() => {
    // @ts-ignore
    global.window = { location: { origin: 'http://localhost:8000' } };
  });

  afterEach(() => {
    global.window = originalWindow;
  });

  it('should pass through normal schema', () => {
    const schema = { type: 'page', body: 'hello' };
    expect(sanitizeAmisSchema(schema)).toEqual(schema);
  });

  it('should strip script keys', () => {
    const schema = {
      type: 'page',
      script: 'alert(1)',
      jsEngine: 'eval',
      jsFunction: 'foo',
      srcdoc: '<script>alert(1)</script>',
    };
    expect(sanitizeAmisSchema(schema)).toEqual({ type: 'page' });
  });

  it('should replace custom component with safe tpl', () => {
    const schema = { type: 'custom', component: 'MyComponent' };
    expect(sanitizeAmisSchema(schema)).toEqual({
      type: 'tpl',
      tpl: '[已拦截不安全组件: custom]',
    });
  });

  it('should sanitize html and richText', () => {
    const schema = {
      type: 'page',
      html: '<div onclick="alert(1)">x</div><script>alert(2)</script>',
      richText: '<img src="javascript:alert(1)">',
    };
    const result = sanitizeAmisSchema(schema);
    expect(result.html).not.toContain('<script');
    expect(result.html).not.toContain('onclick');
    expect(result.richText).not.toContain('javascript:');
  });

  it('should sanitize tpl but keep simple variable interpolation', () => {
    const schema = { type: 'tpl', tpl: 'Hello ${name}' };
    expect(sanitizeAmisSchema(schema).tpl).toBe('Hello ${name}');
  });

  it('should remove dangerous iframe src', () => {
    expect(sanitizeAmisSchema({ type: 'iframe', src: 'javascript:alert(1)' })).toEqual({ type: 'iframe' });
    expect(sanitizeAmisSchema({ type: 'iframe', src: 'data:text/html,<script>alert(1)</script>' })).toEqual({ type: 'iframe' });
    expect(sanitizeAmisSchema({ type: 'iframe', src: '//example.com' })).toEqual({ type: 'iframe' });
  });

  it('should keep same-origin http iframe src', () => {
    const schema = { type: 'iframe', src: 'http://localhost:8000/page' };
    expect(sanitizeAmisSchema(schema)).toEqual(schema);
  });

  it('should rewrite cross-origin http iframe src', () => {
    const schema = { type: 'iframe', src: 'https://example.com/page' };
    expect(sanitizeAmisSchema(schema)).toEqual({ type: 'iframe', src: 'about:blank' });
  });

  it('should replace custom actionType', () => {
    const schema = { type: 'button', actionType: 'custom', customScript: 'alert(1)' };
    expect(sanitizeAmisSchema(schema)).toEqual({
      type: 'button',
      actionType: 'toast',
      msgType: 'warning',
      msg: '已拦截自定义脚本动作',
    });
  });

  it('should recursively sanitize nested nodes', () => {
    const schema = {
      type: 'page',
      body: [
        { type: 'custom' },
        { type: 'tpl', tpl: '${name}<script>alert(1)</script>' },
      ],
    };
    const result = sanitizeAmisSchema(schema);
    expect(result.body[0]).toEqual({ type: 'tpl', tpl: '[已拦截不安全组件: custom]' });
    expect(result.body[1].tpl).toBe('${name}');
  });

  it('should return safe fallback when sanitizing throws', () => {
    // Circular reference would throw during structured clone / recursion
    const circular: any = { type: 'page' };
    circular.self = circular;
    expect(sanitizeAmisSchema(circular)).toEqual({ type: 'tpl', tpl: '[Schema 消毒失败]' });
  });
});
