import { Typography, Divider, Alert, Table } from 'antd';
import React from 'react';

const { Title, Paragraph, Text } = Typography;

const AuthSpiDoc: React.FC = () => {
  return (
    <Typography style={{ maxWidth: 900, margin: '0 auto', paddingBottom: 40 }}>
      <Title level={2} style={{ marginTop: 0 }}>宿主系统对接与安全认证指南</Title>

      <Alert
        message="双层鉴权（嵌入生产推荐）"
        description={
          <>
            管控面路径 <Text code>/flow-ui/**</Text>、<Text code>/flow-api/**</Text> 应先过
            <Text strong>宿主登录</Text>，再过 <Text strong>Flow 管理端 JWT + RBAC</Text>。
            宿主登录与 Flow 登录相互独立；<Text strong>不提供</Text>宿主登录后静默换发 / 同步 Flow JWT，管理员需单独登录 Flow。
          </>
        }
        type="info"
        showIcon
        style={{ marginBottom: 24 }}
      />

      <Title level={3} id="auth-dual">1. 宿主 Security 与双层鉴权</Title>
      <Paragraph>
        宿主使用 <Text code>authenticated()</Text> 放行 Session（保留 SecurityContext），
        <Text strong>不要</Text>对 Flow 路径使用 <Text code>web.ignoring()</Text>，否则 Probe / SPI 读不到宿主用户。
      </Paragraph>
      <pre style={{ background: '#f5f5f5', padding: 16, borderRadius: 6, overflowX: 'auto' }}>
        <code className="language-java">{`http.authorizeHttpRequests(auth -> auth
    .requestMatchers("/flow-api/open/**").permitAll()              // AppKey
    .requestMatchers("/flow-api/download/excel/**").permitAll()  // 签名下载
    .requestMatchers("/flow-api/**", "/flow-ui/**").authenticated()
    // …宿主自有规则
);`}</code>
      </pre>
      <Paragraph>
        进程内第二关由配置控制（<Text strong>默认关闭</Text>，仅加强防护时打开）：
      </Paragraph>
      <pre style={{ background: '#f5f5f5', padding: 16, borderRadius: 6, overflowX: 'auto' }}>
        <code className="language-yaml">{`yu:
  flow:
    security:
      # 管理端 JWT 通过后再校验 HostAuthenticationProbe（默认 false）
      management-require-host-auth: true`}</code>
      </pre>
      <Paragraph>
        开启后，网关在管理端 JWT 通过后会再调用 <Text code>HostAuthenticationProbe</Text>。
        嵌入时请覆盖 Probe 读宿主登录态（默认 Bean 只校验 Flow JWT，加强意义有限）：
      </Paragraph>
      <pre style={{ background: '#f5f5f5', padding: 16, borderRadius: 6, overflowX: 'auto' }}>
        <code className="language-java">{`import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.yu.flow.module.open.auth.HostAuthenticationProbe;

import jakarta.servlet.http.HttpServletRequest;

@Component
public class HostSessionAuthenticationProbe implements HostAuthenticationProbe {
    @Override
    public boolean isAuthenticated(HttpServletRequest request) {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.isAuthenticated()
                && !(auth instanceof AnonymousAuthenticationToken);
    }
}`}</code>
      </pre>
      <Paragraph>
        未开启时管理端只走 Flow JWT + RBAC；宿主仍可用上方 Security <Text code>authenticated()</Text> 做外层限制。
        开放口 <Text code>/flow-api/open/**</Text>、签名下载链不走管理端双层。
      </Paragraph>

      <Divider />

      <Title level={3} id="auth-principal">2. 用户主体对接（当前是谁）</Title>
      <Paragraph>
        嵌入后 OSS、已发布 API 调用方策略、流程 <Text code>@AUTH</Text> 上下文等都需要「当前是谁」。
        两条路径任选其一：<Text strong>配置式</Text>（宿主机配置页，零 Java 代码，见 2.1）或
        <Text strong>Java SPI</Text>（<Text code>FlowHostPrincipalProvider</Text>，见 2.2）。
        两者都不做时使用内置 JWT：固定 <Text code>userType=ADMIN</Text>，并填充 Flow RBAC 角色/权限，
        <Text strong>无法区分「本人」与「运营」</Text>。
      </Paragraph>

      <Title level={4} id="auth-principal-config">2.1 配置式主体解析（纯 SaaS 对接，推荐先试）</Title>
      <Paragraph>
        入口：<Text strong>平台设置 → 宿主机配置 → 当前用户解析</Text>。开启后由配置决定如何识别调用者，
        不需要在宿主工程里写任何 Flow 的 Java 类。解析不到宿主会话时自动回退内置 JWT，
        因此运营照常登录 Flow 管理端。
      </Paragraph>
      <ul>
        <li>
          <Text strong>调用宿主接口</Text>（推荐）：Flow 把请求头按白名单转发给系统保留接口
          <Text code>/__sys/host-catalog</Text> 同族的 <Text code>/__sys/host-principal/resolve</Text>，
          由你用 FLOW 编排（HTTP 节点回调宿主 <Text code>/api/me</Text>）或 DB 查询（查会话表）返回一行主体字段。
          该接口<Text strong>必须发布</Text>，运行时不认草稿。
        </li>
        <li>
          <Text strong>读取网关请求头</Text>：Flow 直接读 <Text code>X-User-Id</Text> 等头。
          仅当 Flow 不直接暴露公网、且网关会剥离客户端传入的同名头时才可用，
          因此保存前必须打开「已确认部署前提」开关。
        </li>
      </ul>
      <Paragraph>
        解析接口的入参为 <Text code>headers</Text>（转发的请求头）、<Text code>token</Text>
        （去掉 Bearer 前缀的 Authorization）、<Text code>clientIp</Text>；编排里用
        <Text code>{"$.request.headers['Cookie']"}</Text> 或 <Text code>{'${token}'}</Text> 取用。
        返回值字段名可在页面上逐项映射，也兼容下划线风格（<Text code>user_id</Text>）与逗号分隔的
        <Text code>roles</Text> / <Text code>permissions</Text>。结果按凭证指纹缓存，默认 30 秒。
      </Paragraph>
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message="配置式能覆盖到哪一步"
        description={
          <>
            配置式解析给出的行级范围只有<Text strong>全部</Text>与<Text strong>本人</Text>两档：
            页面上「运营用户类型」里勾中的类型看全部，其余宿主用户只看自己的记录。
            需要「按部门」或「指定用户列表」时，仍要实现 <Text code>FlowHostDataScopeProvider</Text>（见第 4 节）。
          </>
        }
      />

      <Title level={4} id="auth-principal-spi">2.2 Java SPI（FlowHostPrincipalProvider）</Title>
      <Paragraph>
        宿主注册了自己的 <Text code>FlowHostPrincipalProvider</Text> Bean 时，配置式解析整体不装配，一切以 SPI 为准。
      </Paragraph>
      <Paragraph>
        引擎预置 <Text code>ADMIN</Text>（运营）、<Text code>END_USER</Text>（业务用户）、
        <Text code>OPEN_APP</Text>（开放平台，网关合成）三个码，但 <Text code>userType</Text> 是
        自由字符串，<Text strong>推荐直接使用宿主自己的类型码</Text>（如 <Text code>CUSTOMER</Text> /
        <Text code>STAFF</Text> / <Text code>SUPPLIER</Text>），并在身份目录里登记同名项。
      </Paragraph>
      <Alert
        type="warning"
        showIcon
        style={{ marginBottom: 16 }}
        message="多套用户表：userId 保持各体系主键，userType 单独落库"
        description={
          <>
            OSS 台账「仅本人」判定是 <Text code>uploadedBy == principal.userId</Text>
            {' '}且{' '}
            <Text code>uploadedByUserType == principal.userType</Text>
            （见 <Text code>OssUploaderIdentity</Text>）。
            宿主把 <Text code>userId</Text> 写成各用户表主键即可，不要再拼
            <Text code>类型:主键</Text> 前缀。
            引擎写入 <Text code>flow_oss_object.uploaded_by</Text> 与
            <Text code>uploaded_by_user_type</Text>（与 <Text code>FlowHostPrincipal.userType</Text> 相同：
            <Text code>ADMIN</Text> / <Text code>END_USER</Text> / <Text code>OPEN_APP</Text>，宿主可扩展）。
          </>
        }
      />
      <pre style={{ background: '#f5f5f5', padding: 16, borderRadius: 6, overflowX: 'auto' }}>
        <code className="language-java">{`import org.springframework.stereotype.Component;
import org.yu.flow.module.host.BuiltinJwtFlowHostPrincipalProvider;
import org.yu.flow.module.host.FlowHostPrincipal;
import org.yu.flow.module.host.FlowHostPrincipalProvider;
import org.yu.flow.module.rbac.service.RbacService;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;

@Component
public class MyHostPrincipalProvider implements FlowHostPrincipalProvider {

    private final MyAuthFacade hostAuth;
    /** 兜底：运营直接访问 Flow 管理端时仍需解析出主体 */
    private final FlowHostPrincipalProvider fallback;

    public MyHostPrincipalProvider(MyAuthFacade hostAuth, RbacService rbacService) {
        this.hostAuth = hostAuth;
        this.fallback = new BuiltinJwtFlowHostPrincipalProvider(rbacService);
    }

    @Override
    public Optional<FlowHostPrincipal> resolve(HttpServletRequest request) {
        MyLoginUser user = hostAuth.current(request);
        if (user == null) {
            return fallback.resolve(request);
        }
        String userType = user.getUserType();   // 宿主自定义码，如 ADMIN / END_USER
        return Optional.of(FlowHostPrincipal.builder()
                .userId(user.getId())
                .username(user.getNickname())
                .userType(userType)
                .deptId(user.getDeptId())
                .deptIds(user.getDeptIdsWithChildren())
                .roles(user.getRoleCodes())
                .permissions(user.getPermCodes())
                .authChannel("HOST_SESSION")
                .build());
    }
}`}</code>
      </pre>
      <Paragraph>
        自定义 Bean 会通过 <Text code>@ConditionalOnMissingBean</Text> 顶掉内置实现，
        因此 <Text strong>必须</Text>保留上面的 fallback 分支，否则运营登录 Flow 管理端后解析不出主体，
        OSS 文件列表等页面会直接 401。
      </Paragraph>

      <Title level={4} id="auth-catalog">2.3 身份目录清单（可选 FlowHostIdentityCatalogProvider）</Title>
      <Paragraph>
        调用方策略表单的下拉选项<Text strong>不写死</Text> ADMIN / END_USER。
        优先级：宿主实现了 <Text code>FlowHostIdentityCatalogProvider</Text> 时走 SPI；
        否则走「平台设置 → 宿主机配置」里<Text strong>已启用</Text>的 5 条身份目录保留接口
        （FLOW / DB / JSON / STRING，进程内按 API id 执行，不挂网关）。
        都未对接时，独立运行前端提供示例选项，仍可手输码；运行时匹配不受影响。
      </Paragraph>
      <Paragraph>
        五个维度与策略字段对应：<Text code>USER_TYPE</Text> / <Text code>ROLE</Text> /
        <Text code>PERMISSION</Text> / <Text code>DEPT</Text> / <Text code>USER</Text>。
        目录清单是「策略里能勾哪些码」，不是当前登录身份
        （当前主体仍由 <Text code>FlowHostPrincipalProvider</Text> / 内置 JWT 提供）。
        宿主已对接 SPI 时，用户类型会额外补一条引擎合成的 <Text code>OPEN_APP</Text>。
      </Paragraph>
      <pre style={{ background: '#f5f5f5', padding: 16, borderRadius: 6, overflowX: 'auto' }}>
        <code className="language-java">{`import org.springframework.stereotype.Component;
import org.yu.flow.module.host.*;

import java.util.List;

@Component
public class MyIdentityCatalog implements FlowHostIdentityCatalogProvider {
    @Override
    public boolean supports(FlowHostCatalogDimension dimension) {
        // 没有的维度返回 false，管理端该栏无下拉
        return dimension != FlowHostCatalogDimension.USER;
    }

    @Override
    public List<FlowHostCatalogItem> list(FlowHostCatalogDimension dimension,
                                         String keyword, int limit) {
        return switch (dimension) {
            case USER_TYPE -> List.of(
                    FlowHostCatalogItem.builder().value("ADMIN").label("运营").build(),
                    FlowHostCatalogItem.builder().value("END_USER").label("C 端用户").build(),
                    FlowHostCatalogItem.builder().value("MERCHANT").label("商户").build());
            case ROLE -> hostRoles(keyword, limit);
            case PERMISSION -> hostPerms(keyword, limit);
            case DEPT -> hostDepts(keyword, limit);
            default -> List.of();
        };
    }

    private List<FlowHostCatalogItem> hostRoles(String keyword, int limit) { return List.of(); }
    private List<FlowHostCatalogItem> hostPerms(String keyword, int limit) { return List.of(); }
    private List<FlowHostCatalogItem> hostDepts(String keyword, int limit) { return List.of(); }
}`}</code>
      </pre>
      <Paragraph>
        管理端接口：<Text code>GET /flow-api/host/identity-catalog</Text>（快照）、
        <Text code>GET /flow-api/host/identity-catalog/items?dimension=&amp;keyword=</Text>（搜索）。
        无 Bean 时 <Text code>available=false</Text>。
      </Paragraph>

      <Divider />

      <Title level={3} id="auth-caller-policy">3. 已发布 API 调用方策略</Title>
      <Paragraph>
        在接口编辑「基本信息 → 访问控制」可配置 <Text strong>谁可以调用</Text>
        （写入 <Text code>securityConfig.callerPolicy</Text>，<Text strong>发布后生效</Text>）。
        用于区分「仅管理员可调」与「仅普通用户可调」等，而无需改 Flow 管理端 RBAC。
      </Paragraph>
      <Paragraph>
        运行链路：<Text code>authMode</Text>（HOST/OPEN/…）→ 解析
        <Text code>FlowHostPrincipal</Text> →（HOST）匹配 callerPolicy → 限流 → 执行。
        匹配失败返回 <Text code>403 INGRESS_CALLER_DENIED</Text>；无主体且策略已启用时多为
        <Text code>401 INGRESS_HOST_AUTH_REQUIRED</Text>。
      </Paragraph>
      <pre style={{ background: '#f5f5f5', padding: 16, borderRadius: 6, overflowX: 'auto' }}>
        <code className="language-json">{`{
  "authMode": "HOST",
  "callerPolicy": {
    "enabled": true,
    "rules": [
      { "name": "运营可调", "principals": "MATCH", "userTypes": ["STAFF"], "effect": "ALLOW" },
      { "name": "开放应用", "principals": "OPEN_APP", "effect": "ALLOW" }
    ]
  }
}`}</code>
      </pre>
      <Paragraph>
        <Text strong>规则摘要：</Text>
      </Paragraph>
      <ul>
        <li><Text code>enabled=false</Text>（默认）：与历史一致，只做 authMode 门禁。</li>
        <li>新格式 <Text code>rules</Text>：多行允许，身份与 OSS 相同（任何已登录 / 指定身份 / 开放应用）。启用后未命中拒绝。</li>
        <li>旧格式单块 <Text code>userTypes/roles/...</Text> 读入时升成一条 MATCH 规则。</li>
        <li><Text code>authMode=OPEN</Text>：仍以开放平台 grant 为准，<Text strong>不跑</Text> callerPolicy 匹配（可保存便于切回 HOST）。</li>
        <li><Text code>authMode=NONE</Text>：禁止启用 callerPolicy（保存/发布校验）。</li>
      </ul>
      <Paragraph>
        调用方身份以系统预制参数 <Text code>@AUTH</Text> 注入执行上下文，与
        <Text code>@QP</Text> / <Text code>@BP</Text> / <Text code>@PP</Text> / <Text code>@FP</Text>
        同属 <Text code>@</Text> 前缀命名空间，不会和编排内自定义变量重名。字段含
        <Text code>userId</Text>、<Text code>username</Text>、<Text code>userType</Text>、
        <Text code>deptId</Text>、<Text code>deptIds</Text>、<Text code>roles</Text>、
        <Text code>permissions</Text>、<Text code>authChannel</Text>、<Text code>attributes</Text>。
      </Paragraph>
      <ul>
        <li>SQL 与 <Text code>{'${}'}</Text> 表达式：<Text code>{'${@AUTH.userId}'}</Text></li>
        <li>节点 inputs（JSONPath）：<Text code>{"$['@AUTH'].userId"}</Text></li>
        <li>直连 DB 类型接口与 FLOW 编排均可用；非网关触发（保留接口、定时任务）时该键不存在。</li>
      </ul>
      <Alert
        type="warning"
        showIcon
        style={{ marginBottom: 16 }}
        message="双用户表必须由宿主实现 PrincipalProvider"
        description="内置 JWT 只能表示运营 ADMIN。C 端用户表 / 另一套 Token 请在宿主 SPI 中解析为 END_USER（或自定义类型），否则调用方策略无法区分。"
      />

      <Title level={4} id="auth-oss-caller">3.1 OSS 上传场景访问规则</Title>
      <Paragraph>
        OSS 私有场景用一张<Text strong>访问规则表</Text>（字段 <Text code>caller_policy</Text>），
        不再拆上传组 / 下载组，也不再跟宿主机配置里的「运营用户类型」耦合。
      </Paragraph>
      <pre style={{ background: '#f5f5f5', padding: 16, borderRadius: 6, overflowX: 'auto' }}>
        <code className="language-json">{`{
  "rules": [
    {
      "name": "已登录用户",
      "principals": "ANY_AUTHENTICATED",
      "upload": true,
      "downloadScope": "SELF"
    },
    {
      "name": "运营",
      "principals": "MATCH",
      "match": "ALL",
      "userTypes": ["STAFF"],
      "upload": false,
      "downloadScope": "ALL"
    }
  ]
}`}</code>
      </pre>
      <ul>
        <li>配置入口：管理端 → OSS 上传场景 → <Text strong>上传与访问权限</Text> → 访问规则。可一键套用预置：个人文件、用户+运营、部门资料、运营上传·全员下载、仅运营内部、开放应用代传、匿名征集。</li>
        <li>一行一类人，组间<Text strong>或</Text>。命中多行时上传/下载取并集，可见范围取最宽（<Text code>ALL</Text> &gt; <Text code>DEPT</Text> ∪ <Text code>SELF</Text>）。</li>
        <li>
          身份三种：<Text code>ANY_AUTHENTICATED</Text> 任何已登录宿主用户（不含开放应用）；
          <Text code>MATCH</Text> 用户类型 / 角色（高级还可配权限、指定用户）；
          <Text code>OPEN_APP</Text> 开放应用（可再限定 AppKey）。
        </li>
        <li>
          下载范围：<Text code>OFF</Text> 不可下 / <Text code>SELF</Text> 仅本人（<Text code>uploaded_by</Text>）/
          <Text code>DEPT</Text> 本部门含下级（对象上的 <Text code>dept_id</Text> 快照）/
          <Text code>ALL</Text> 该场景全部。
        </li>
        <li>私有且要求登录：必须至少一条规则，否则宿主用户无法上传下载（失败关闭）。公有文件不配下载范围。</li>
        <li>
          开放应用必须用身份 <Text code>OPEN_APP</Text>（或 MATCH 勾 <Text code>OPEN_APP</Text> / <Text code>open:&lt;appKey&gt;</Text>），
          不会被「任何已登录」带进去。失败 <Text code>403 OSS_CALLER_DENIED</Text>。
        </li>
        <li>Flow 管理端 JWT 不走这张表，仍用 <Text code>flow:oss:admin</Text> / 本人；<Text code>downloadPerm</Text> 只给管理端跨范围兜底。</li>
      </ul>

      <Divider />

      <Title level={4} id="auth-privacy">3.2 接口出站隐私拦截</Title>
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message="入站管「能不能调」，出站管「看见什么」"
        description={
          <>
            调用方策略（第 3 节）决定请求进不进网关。出站隐私（<Text code>privacyConfig</Text>）在 SQL / 编排跑完之后拦截响应 JSON：
            先按方案解开库内密文，再按「谁看什么」决定明文、脱敏或删字段。
            已发布 JSON 接口若判定为明文，还会再套一层传输 SM4（<Text code>X-Privacy-Key</Text>），避免 JWT 一过就把手机号写进响应体。
          </>
        }
      />
      <Paragraph>
        三层各管各的，不要混用密钥：
      </Paragraph>
      <Table
        size="small"
        pagination={false}
        style={{ marginBottom: 16 }}
        rowKey="layer"
        columns={[
          { title: '层', dataIndex: 'layer', width: 88 },
          { title: '解决的问题', dataIndex: 'problem' },
          { title: '密钥 / 开关', dataIndex: 'key' },
        ]}
        dataSource={[
          {
            layer: '库内解密',
            problem: '列是密文，解开后才能脱敏或给明文',
            key: '隐私方案里的 SM4/AES 密钥（或 YAML at-rest-sm4-key）',
          },
          {
            layer: '谁看什么',
            problem: '运营看明文、业务用户看脱敏；未命中一律脱敏',
            key: '目录/接口「谁看什么」规则，匹配 FlowHostPrincipal',
          },
          {
            layer: '传输封装',
            problem: '获准看明文时，JSON 里仍不要裸奔 PII',
            key: '请求头 X-Privacy-Key（一次性 SM4 会话密钥，SM2 加密）',
          },
        ]}
      />
      <Paragraph>
        <Text strong>出站总流程：</Text>
      </Paragraph>
      <pre style={{ background: '#282c34', color: '#abb2bf', padding: 16, borderRadius: 6, overflowX: 'auto', fontSize: 13, fontFamily: 'Consolas, monospace', lineHeight: '1.45' }}>
{`  原始响应 JSON（库内密文 / 已是明文列）
           |
           |  识别隐私字段
           |  · 键名以 fieldSuffix 结尾（默认 _encrypt）
           |  · 或出现在 extraFields（补充字段，如 loginPhone）
           v
     按隐私方案解密（SM4 / AES / PLAIN）
           |                    \\
           | 成功                 \\ 失败 → 固定占位 ****（绝不回传库内密文）
           v
     去掉后缀（stripSuffix=true 时 phone_encrypt → phone）
           |
           |  谁看什么（从上到下第一条命中）
           +-- 未命中 / MASK -----------> 按「脱敏规则」打码后输出
           +-- DROP 该字段 -------------> 响应里删除该键
           +-- REVEAL
                 |
                 |  通道
                 +-- 数据查看 / Excel（wrapTransport=false）
                 |     直接写明文，不再套 SM4
                 +-- 已发布 JSON 接口（wrapTransport=true）
                       |
                       +-- 有合法 X-Privacy-Key → { "__p":1, "alg":"SM4", "v":"…" }
                       +-- 无 / 解不开 --------→ 降级脱敏（日志 degraded=missing-transport-key）`}
      </pre>
      <Paragraph>
          配置入口与合并顺序：
      </Paragraph>
      <ul>
        <li>
          <Text strong>隐私方案</Text>（平台设置 → 宿主机配置 → 隐私解密与脱敏方案）：库内算法、模式、编码、IV、密钥、密文后缀、补充字段、脱敏规则。
        </li>
        <li>
          <Text strong>目录 / 接口「访问控制」</Text>：选方案、可再填补充字段与本级脱敏规则（非空则<Text strong>整表覆盖</Text>方案规则）、配置「谁看什么」。
        </li>
        <li>
          <Text strong>系统参数</Text>（平台设置 → 系统参数 → 出站隐私）：<Text code>PRIVACY_WRAP_TRANSPORT</Text> 控制已发布 JSON 是否套传输信封（热更新）。
        </li>
        <li>
          合并：接口显式值 → 目录链（<Text code>inherit=false</Text> 停止向上）→ 平台默认规则 → 系统默认（默认<Text strong>关闭</Text>）。
          「谁看什么」<Text code>rules</Text> 与「脱敏规则」<Text code>maskRules</Text> 都是整表覆盖，不逐行 merge。
        </li>
        <li>
          目录上改隐私对已发布接口立即生效（运行时按目录链合并）；接口自己的 <Text code>privacyConfig</Text> 仍随发布快照。
        </li>
        <li>
          SQL 别名没有 <Text code>_encrypt</Text> 时（如 <Text code>login_phone AS loginPhone</Text>），必须把
          <Text code>loginPhone</Text> 写进方案或目录的「补充字段」，否则不会当密文解。
        </li>
      </ul>

      <Title level={5} id="auth-privacy-mask">3.2.1 脱敏规则（MASK 时长什么样）</Title>
      <Paragraph>
        脱敏长什么样<Text strong>只跟界面上能看到的规则走</Text>：目录/接口填了脱敏规则就用本级；
        不填则继承所选方案的规则。两级都空时，不解类型、不猜手机号，统一
        <Text strong>只留第一位，其余每位一个 <Text code>*</Text>，长度与原文一致</Text>
        （11 位手机号 <Text code>1**********</Text>，不是写死的 <Text code>1****</Text>）。
      </Paragraph>
      <Paragraph>
        字段名匹配：<Text code>EXACT</Text> 精确等于别名（忽略大小写）；<Text code>CONTAINS</Text> 字段名包含别名。
        别名写去后缀后的名字，如 <Text code>loginPhone</Text>、<Text code>phone</Text>。
        命中第一条规则即停。
      </Paragraph>
      <Table
        size="small"
        pagination={false}
        style={{ marginBottom: 16 }}
        rowKey="method"
        columns={[
          { title: '方式', dataIndex: 'label', width: 140 },
          { title: '行为', dataIndex: 'behavior' },
          { title: '例子', dataIndex: 'example', width: 200 },
        ]}
        dataSource={[
          {
            method: 'KEEP_HEAD_TAIL',
            label: '留头尾、藏中间',
            behavior: '保留头 N、尾 M，中间按原文剩余位数填 *。长度不够（≤ 头+尾）则整段 *，不报错。',
            example: '头3尾4：138****1234',
          },
          {
            method: 'KEEP_HEAD',
            label: '只留开头',
            behavior: '留头，其余每位一个 *。',
            example: '头1：1**********',
          },
          {
            method: 'KEEP_TAIL',
            label: '只留末尾',
            behavior: '留尾，前面每位一个 *。',
            example: '尾4：*******1234',
          },
          {
            method: 'PHONE',
            label: '手机前3后4',
            behavior: '留前 3 后 4，中间按剩余位数填 *（11 位仍是四颗 *）。',
            example: '138****1234',
          },
          {
            method: 'NAME_KEEP_ENDS',
            label: '姓名藏中间',
            behavior: '1 字→*；2 字藏姓留名；3 字及以上留首尾、中间一颗 *。',
            example: '李华→*华；张三丰→张*丰',
          },
          {
            method: 'ID_CARD',
            label: '身份证留首尾',
            behavior: '留首尾，中间按实际长度填 *。',
            example: '3****************X',
          },
          {
            method: 'FULL',
            label: '全部隐藏',
            behavior: '每位一个 *，长度与原文一致。',
            example: 'secret→******',
          },
        ]}
      />
      <Paragraph>
        解密失败与「解开了但走 MASK」不是一回事：失败关闭固定 <Text code>****</Text>（四位占位，不泄露密文长度）；
        脱敏则按规则保留长度。日志不要把两者当成同一原因。
      </Paragraph>

      <Title level={5} id="auth-privacy-who">3.2.2 谁看什么（REVEAL / MASK）</Title>
      <Paragraph>
        身份勾选与 OSS 访问规则相同：<Text code>ANY_AUTHENTICATED</Text> 任何已登录（不含开放应用）、
        <Text code>MATCH</Text> 指定用户类型/角色/权限/部门/用户、<Text code>OPEN_APP</Text> 开放应用。
        从上到下第一条命中生效；<Text strong>未命中一律 MASK</Text>。
        可选「字段动作」：回车或逗号生成标签（与脱敏规则相同），按 JSON 键名覆盖本行。
        <Text code>DROP</Text> 可去掉任意字段（如 <Text code>createBy</Text>）；
        <Text code>REVEAL</Text> / <Text code>MASK</Text> 只作用于隐私字段。
      </Paragraph>
      <Paragraph>
        规则里的用户类型必须等于 <Text code>FlowHostPrincipal.userType</Text>，不是 JWT 里的 <Text code>platform</Text>。
        嵌入宿主时请在 SPI 里映射（例如运营 JWT <Text code>platform=OPCENTER</Text> → <Text code>ADMIN</Text>），
        「谁看什么」勾「管理员/运营」才能命中。开放应用不会被「任何已登录」带进明文。
      </Paragraph>
      <Paragraph>
        Flow 管理端 JWT 默认脱敏；权限码 <Text code>flow:privacy:reveal</Text>（或 <Text code>*</Text>）
        只给控制台预览明文当逃生口，<Text strong>不替代</Text>宿主「谁看什么」。
        Excel / 数据查看 / 开放下载链走同一套过滤；签名下载链绑定签发时的隐私档，转发不会升格明文。
        明文档禁止响应缓存；脱敏档缓存 key 带 <Text code>:pMASK</Text> 及规则字段指纹。
      </Paragraph>
      <pre style={{ background: '#f5f5f5', padding: 16, borderRadius: 6, overflowX: 'auto' }}>
        <code className="language-json">{`{
  "enabled": true,
  "inherit": true,
  "profileId": "p_ops_sm4",
  "extraFields": ["loginPhone", "authPhone"],
  "maskRules": [
    {
      "matchMode": "CONTAINS",
      "aliases": ["phone"],
      "method": "KEEP_HEAD_TAIL",
      "keepHead": 3,
      "keepTail": 4
    }
  ],
  "rules": [
    {
      "name": "运营看明文",
      "principals": "MATCH",
      "match": "ALL",
      "userTypes": ["ADMIN"],
      "privacy": "REVEAL"
    }
  ]
}`}</code>
      </pre>

      <Title level={5} id="auth-privacy-transport">3.2.3 传输封装与 X-Privacy-Key</Title>
      <Alert
        type="warning"
        showIcon
        style={{ marginBottom: 16 }}
        message="X-Privacy-Key 不是库内解密密钥"
        description={
          <>
            库内 SM4 配在隐私方案（或 YAML <Text code>yu.flow.privacy.at-rest-sm4-key</Text>）。
            <Text code>X-Privacy-Key</Text> 只用于<Text strong>已发布 JSON 接口的明文档</Text>：
            客户端每次（或按会话）生成一把 16 字节 SM4，用<Text strong>登录 SM2 公钥</Text>加密后放进请求头；
            服务端用 SM2 私钥解开，再用这把会话密钥把 REVEAL 字段封成信封。
            curl 只带运营 JWT、不建会话时，即使规则是明文也会降成脱敏，这是失败关闭，不是配置没生效。
          </>
        }
      />
      <Paragraph>
        <Text strong>会话握手：</Text>
      </Paragraph>
      <pre style={{ background: '#282c34', color: '#abb2bf', padding: 16, borderRadius: 6, overflowX: 'auto', fontSize: 13, fontFamily: 'Consolas, monospace', lineHeight: '1.45' }}>
{`  +-------------+                         +------------------+
  | 宿主 / Amis |                         | Flow 已发布接口  |
  +------+------+                         +--------+---------+
         |  1. GET /flow-api/login/public-key      |
         |---------------------------------------->|
         |  2. SM2 公钥（与登录同一套）            |
         |<----------------------------------------|
         |                                         |
         |  3. 本地随机 16 字节 SM4 会话密钥        |
         |     用公钥加密 → 请求头 X-Privacy-Key   |
         |  4. GET /op/…  + 宿主 JWT + 该头        |
         |---------------------------------------->|
         |                                         | SM2 私钥解出会话密钥
         |                                         | 库内解密 → 谁看什么
         |                                         | REVEAL 字段用会话 SM4 封装
         |  5. loginPhone: { "__p":1, "v":"…" }    |
         |<----------------------------------------|
         |  6. 用本地会话密钥 unwrapPrivacyTree    |
         |     用户看到明文                         |
         +-------------+                         +--------+---------+`}
      </pre>
      <Paragraph>
        信封字段：<Text code>{`{ "__p": 1, "alg": "SM4", "v": "<32位IV hex + 密文 hex>" }`}</Text>。
        前端用 <Text code>@/utils/privacyDecrypt</Text>：先 <Text code>createPrivacySession()</Text> 写头，
        再 <Text code>unwrapPrivacyTree(json, session.sm4KeyHex)</Text> 递归解开。Amis 在请求适配器写头、响应适配器拆树。
      </Paragraph>
      <Table
        size="small"
        pagination={false}
        style={{ marginBottom: 16 }}
        rowKey="scene"
        columns={[
          { title: '场景', dataIndex: 'scene', width: 200 },
          { title: 'wrapTransport', dataIndex: 'wrap', width: 120 },
          { title: '要不要 X-Privacy-Key', dataIndex: 'need' },
        ]}
        dataSource={[
          {
            scene: '已发布业务 JSON（/op、/co、/open）',
            wrap: '系统参数 PRIVACY_WRAP_TRANSPORT（默认开）',
            need: '开着时要带头；缺了 REVEAL 也会降成脱敏。关掉则 JSON 直接明文',
          },
          {
            scene: '管理端「数据查看」',
            wrap: 'false',
            need: '不要。服务端直接写明文或脱敏串',
          },
          {
            scene: 'Excel 导出',
            wrap: 'false',
            need: '不要。单元格里是脱敏或明文，不再套信封',
          },
          {
            scene: '规则命中 MASK',
            wrap: '—',
            need: '无意义，响应已是脱敏串',
          },
        ]}
      />
      <pre style={{ background: '#f5f5f5', padding: 16, borderRadius: 6, overflowX: 'auto' }}>
        <code className="language-ts">{`import { createPrivacySession, unwrapPrivacyTree, PRIVACY_KEY_HEADER } from '@/utils/privacyDecrypt';

const session = await createPrivacySession();
const res = await fetch('/op/user/me', {
  headers: {
    Authorization: 'Bearer <host-jwt>',
    [PRIVACY_KEY_HEADER]: session.headerValue,
  },
});
const json = unwrapPrivacyTree(await res.json(), session.sm4KeyHex);
// json.data.loginPhone === '13812341234'
// 未带头时同一字段是脱敏串，例如 '1**********'`}</code>
      </pre>
      <Paragraph>
        日志对照：<Text code>resolved=REVEAL effective=MASK … degraded=missing-transport-key</Text>
        表示「谁看什么」已命中明文，但传输会话没建立。
        <Text code>resolved=MASK</Text> 才是规则没勾上运营明文。不要用 curl 无头结果去判断库内 SM4 配错了。
      </Paragraph>

      <Title level={5} id="auth-privacy-host">3.2.4 宿主接入清单</Title>
      <Paragraph>
        传输封装开关不在 YAML 里改。到管理端
        <Text strong>平台设置 → 系统参数 → 出站隐私</Text>，关闭
        <Text code>PRIVACY_WRAP_TRANSPORT</Text> 即可（热更新，无需重启）。
        优先级：本项 &gt; yml 兜底 &gt; 默认开。生产公网不建议关。
      </Paragraph>
      <pre style={{ background: '#f5f5f5', padding: 16, borderRadius: 6, overflowX: 'auto' }}>
        <code className="language-yaml">{`yu:
  flow:
    privacy:
      # 仅「系统内置」方案回退用；自定义方案在页面里各自配密钥
      # 16 字节明文或 32 位 hex；不要复用数据源 AES 或登录 SM2 私钥
      at-rest-sm4-key: \${YU_FLOW_PRIVACY_AT_REST_SM4_KEY:}`}</code>
      </pre>
      <ul>
        <li>
          关掉「这一套」要分清关哪一层：目录/接口 <Text code>enabled=false</Text> 整段拦截不跑（库内密文会原样出站）；
          系统参数 <Text code>PRIVACY_WRAP_TRANSPORT=false</Text> 只关传输信封，库内解密和「谁看什么」仍生效，REVEAL 的 JSON 里就是明文手机号。
        </li>
        <li>宿主必须实现 <Text code>FlowHostPrincipalProvider</Text>（或配置式主体解析），把运营 / C 端映射成规则里能勾到的 <Text code>userType</Text>。</li>
        <li>业务前端若要在已发布接口上看明文：接入 <Text code>createPrivacySession</Text> + <Text code>unwrapPrivacyTree</Text>；只带 JWT 且封装仍开着时永远拿不到 JSON 明文。</li>
        <li>
          自测顺序：数据查看（确认库内解密）→ 日志 <Text code>resolved=REVEAL</Text>（确认谁看什么）→
          带 <Text code>X-Privacy-Key</Text> 调已发布接口（确认传输封装）；内网也可先关系统参数再 curl。
        </li>
        <li><Text code>stripSuffix=true</Text> 时包装 JSONPath 写去后缀后的字段名（<Text code>phone</Text> 不是 <Text code>phone_encrypt</Text>）。</li>
      </ul>

      <Divider />

      <Title level={3} id="auth-datascope">4. 数据隔离对接 (FlowHostDataScopeProvider)</Title>
      <Paragraph>
        需要部门 / 指定用户列表等行级范围时，实现
        <Text code>FlowHostDataScopeProvider</Text>。内置实现的规则是
        <Text code>flow:oss:admin</Text> 或 <Text code>*</Text> → ALL，其余登录用户 → SELF。
        域常量见 <Text code>FlowHostDomains</Text>（如 <Text code>OSS_OBJECT</Text>、
        <Text code>FLOW_API_RUNTIME</Text>）。
      </Paragraph>
      <Paragraph>
        调用方策略解决「能不能调接口」；DataScope 解决「调了之后能看哪些行」。
        OSS 台账已内建这层过滤（SELF 比对 <Text code>uploaded_by</Text>，DEPT_LIST 比对
        <Text code>dept_id</Text>）；业务 SQL 则不会被自动改写，需在编排中显式使用主体字段。
      </Paragraph>
      <Paragraph>
        下面的例子实现「宿主管理员看全部、普通用户只看本人上传」，管理员由<Text strong>宿主类型码</Text>
        判定而非 Flow RBAC 权限码：
      </Paragraph>
      <pre style={{ background: '#f5f5f5', padding: 16, borderRadius: 6, overflowX: 'auto' }}>
        <code className="language-java">{`import org.springframework.stereotype.Component;
import org.yu.flow.module.host.*;
import org.yu.flow.module.rbac.service.RbacService;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;

@Component
public class MyHostDataScopeProvider implements FlowHostDataScopeProvider {

    /** 宿主自定义的管理员类型码 */
    private static final Set<String> ADMIN_TYPES = Set.of("STAFF", "PLATFORM_OPS");

    private final FlowHostDataScopeProvider fallback;

    public MyHostDataScopeProvider(RbacService rbacService) {
        this.fallback = new BuiltinJwtFlowHostDataScopeProvider(rbacService);
    }

    @Override
    public FlowHostDataScope resolve(HttpServletRequest request,
                                     FlowHostPrincipal principal,
                                     String domain) {
        if (principal == null) {
            return FlowHostDataScope.deny();
        }
        // 运营走 Flow 管理端 JWT 进来：沿用内置的 flow:oss:admin 判定
        if ("FLOW_JWT".equals(principal.getAuthChannel())) {
            return fallback.resolve(request, principal, domain);
        }
        if (ADMIN_TYPES.contains(principal.getUserType())) {
            return FlowHostDataScope.all();
        }
        return FlowHostDataScope.self();
    }
}`}</code>
      </pre>
      <Paragraph>
        同样注意覆盖后内置规则整体失效，Flow 管理端的 OSS 文件页也走这条链，
        故保留 <Text code>FLOW_JWT</Text> 分支。若某类角色只是偶尔需要跨范围取文件，
        不必改 DataScope，给上传场景配 <Text code>downloadPerm</Text> 作兜底放行即可。
      </Paragraph>

      <Title level={4} id="auth-scope-recipe">4.1 落地配方：普通用户查自己、运营查所有</Title>
      <Alert
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
        message="OSS 用规则表；接口仍要自己写 SQL"
        description={
          <>
            文件场景在「访问规则」里一行一类人即可同时表达门禁和可见范围。
            已发布 API / 目录上的调用方策略仍然只回答「能不能调」，行级过滤要写在 SQL / 编排的
            <Text code>@AUTH</Text> 里。
          </>
        }
      />
      <Paragraph>
        <Text strong>第 1 步 · 让 Flow 认得出宿主用户（必做）</Text>：
        「宿主机配置 → 当前用户解析」开启并映射出 <Text code>userId</Text>、<Text code>userType</Text>
        （部门资料再映射 <Text code>deptId</Text>）。不做这步，所有人都是管理端 JWT，「本人」无从谈起。
        多套用户表时把各体系主键放进 <Text code>userId</Text>，类型放进 <Text code>userType</Text>
        （见 2.2）；不要把类型前缀拼进 <Text code>userId</Text>。
      </Paragraph>
      <Paragraph>
        <Text strong>第 2 步 · 文件上传下载</Text>：打开场景，套用预置。常见对照：
        头像/个人附件 →「个人文件」；工单附件客服看全部 →「用户 + 运营」；部门网盘 →「部门资料」；
        只许运营传、全员下 →「运营上传 · 全员下载」；AppKey 代传 →「开放应用代传」；游客投稿 →「匿名征集」。
        套用后再把运营那一行的用户类型改成宿主的运营码。
      </Paragraph>
      <Paragraph>
        <Text strong>第 3 步 · 接口管理（目录配置）</Text>：目录上的「调用方策略」是给<Text strong>下属接口</Text>
        统一设默认调用方门槛（子目录/接口可「本级覆盖」）。
        它只管「能不能调」。想让同一个查询接口做到「普通用户查自己、运营查所有」，
        在接口的 SQL 或编排里用主体字段自行收敛：
      </Paragraph>
      <pre style={{ background: '#f5f5f5', padding: 16, borderRadius: 6, overflowX: 'auto' }}>
        <code className="language-sql">{`-- 目录/接口策略放行 END_USER 与 STAFF；行级由 @AUTH 收敛
SELECT id, title, amount, created_by
FROM biz_order
WHERE 1 = 1
  -- 运营类型放开，其余只看本人
  AND (\${@AUTH.userType} = 'STAFF' OR created_by = \${@AUTH.userId})
ORDER BY id DESC`}</code>
      </pre>
      <Paragraph>
        业务表的 SQL <Text strong>不会</Text>被 DataScope 自动改写——Flow 只对自己的 OSS 台账内建了行级过滤。
        所以接口侧必须显式写条件；把判定写成 <Text code>@AUTH</Text> 表达式而不是前端传参，
        才能防止调用方伪造他人 ID。
      </Paragraph>

      <Divider />

      <Title level={3} id="auth-frontend">5. 前端界面与菜单融合</Title>
      <Paragraph>
        可将 <Text code>flow-ui</Text> 挂到宿主前端路由下并隐藏默认 Layout，实现菜单统一。
        访问管理页仍须具备 Flow JWT 与对应 RBAC 权限码；宿主已登录但未登录 Flow、或无权限码时，
        无法使用编排 / 连接配置等管理能力。
      </Paragraph>
      <Paragraph>
        配置已发布 API 的调用方策略：管理端 → 接口编排 → 编辑接口 →
        <Text strong>基本信息 → 访问控制 → 谁可以调用</Text>，保存并发布。
      </Paragraph>
    </Typography>
  );
};

export default AuthSpiDoc;
