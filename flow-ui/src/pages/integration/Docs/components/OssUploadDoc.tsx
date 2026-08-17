import { Typography, Divider, Alert, Space, Tag } from 'antd';
import React from 'react';

const { Title, Paragraph, Text } = Typography;

const OssUploadDoc: React.FC = () => {
  return (
    <Typography style={{ maxWidth: 900, margin: '0 auto', paddingBottom: 40 }}>
      <Title level={2} style={{ marginTop: 0 }}>对象存储 (OSS) 文件上传 API 指南</Title>
      
      <Alert 
        message="统一上传入口" 
        description="系统内置了统一的 OSS 文件管理引擎，支持主流云厂商（阿里云、腾讯云、AWS S3、MinIO 等）。为了保证文件的安全性与可追溯性，所有附件上传均需通过统一的凭证（上传场景编码）进行拦截管控。"
        type="info" 
        showIcon 
        style={{ marginBottom: 24 }}
      />

      <Title level={3} id="oss-single">1. 基础单文件上传 (代理上传)</Title>
      <Paragraph>
        <Space>
          <Tag color="blue">POST</Tag>
          <Text code>/flow-api/oss/upload?profile={`{profileCode}`}</Text>
        </Space>
      </Paragraph>
      <Paragraph>
        这是最基础的上传方式。适用于小文件（推荐小于 20MB）的头像、证件、小文档上传。
        您需要先在管理后台创建一个“上传配置场景”并获取 <Text code>profileCode</Text>。
      </Paragraph>
      <Paragraph>
        <Text strong>请求约定：</Text>场景编码通过 <Text code>profile</Text> 参数传入（Query 参数或表单字段均可），
        文件放在 <Text code>file</Text> 表单字段（也接受 <Text code>files</Text>）。其余表单字段会作为业务字段（bizFields）原样落档，
        并按场景配置的 <Text code>bizFieldsSchema</Text> 校验。
      </Paragraph>
      <Paragraph>
        <Text strong>请求示例 (cURL)：</Text>
      </Paragraph>
      <pre style={{ background: '#f5f5f5', padding: 16, borderRadius: 6, overflowX: 'auto' }}>
        <code className="language-bash">{`curl -X POST "http://localhost:11281/flow-api/oss/upload?profile=avatar_upload" \\
  -H "Authorization: Bearer <your-token>" \\
  -F "file=@/path/to/your/avatar.jpg"`}</code>
      </pre>

      <Paragraph>
        <Text strong>代理上传时序图：</Text>
      </Paragraph>
      <pre style={{ background: '#282c34', color: '#abb2bf', padding: 16, borderRadius: 6, overflowX: 'auto', fontSize: 13, fontFamily: 'Consolas, monospace', lineHeight: '1.4' }}>
{`  +---------+                               +---------+                               +--------+
  | 客户终端 |                               | Yu Flow |                               |  OSS   |
  +----+----+                               +----+----+                               +----+---+
       |                                         |                                         |
       | 1. POST /flow-api/oss/upload?profile=…  |                                         |
       |    (提交 multipart/form-data 文件)      |                                         |
       |---------------------------------------->|                                         |
       |                                         | 2. 代理上传 (双倍消耗应用服务器带宽)    |
       |                                         |========================================>|
       |                                         |<========================================|
       |                                         |                                         |
       | 3. 返回新生成的 objectId 等对象信息     |                                         |
       |<----------------------------------------|                                         |
       |                                         |                                         |
  +----+----+                               +----+----+                               +----+---+`}
      </pre>

      <Paragraph>
        <Text strong>响应结构（注意 <Text code>data</Text> 是数组）：</Text>
      </Paragraph>
      <pre style={{ background: '#f5f5f5', padding: 16, borderRadius: 6, overflowX: 'auto' }}>
        <code className="language-json">{`{
  "code": 200,
  "msg": "success",
  "data": [
    {
      "id":             "1234567890",        // 对象台账 ID，业务系统保存这个值
      "visibility":     "PRIVATE",           // PUBLIC / PRIVATE，取场景配置
      "originalName":   "avatar.jpg",
      "sizeBytes":      20480,
      "contentType":    "image/jpeg",
      "publicPath":     null,                // 仅 PUBLIC 场景有值
      "publicUrl":      null,                // 仅 PUBLIC 场景有值
      "thumbStatus":    "PENDING",           // NONE / PENDING / READY / FAILED / SKIPPED
      "thumbPublicPath": null,
      "hasThumbnail":   false,
      "expiresAt":      null                 // 传入 expiresInSeconds/expiresAt 时有值
    }
  ]
}`}</code>
      </pre>
      <Paragraph>
        私有文件不返回可直接访问的 URL，需通过 <Text code>GET /flow-api/oss/objects/{`{id}`}/content</Text>（流式下载）
        或 <Text code>GET /flow-api/oss/objects/{`{id}`}/presign</Text>（换取带签名的临时 URL）访问。
      </Paragraph>

      <Divider />

      <Title level={3} id="oss-batch">2. 多文件批量上传</Title>
      <Paragraph>
        <Space>
          <Tag color="blue">POST</Tag>
          <Text code>/flow-api/oss/upload?profile={`{profileCode}`}</Text>
        </Space>
      </Paragraph>
      <Paragraph>
        批量上传<Text strong>没有独立端点</Text>，与单文件复用同一个接口：重复提交 <Text code>files</Text> 表单字段即可，
        响应 <Text code>data</Text> 数组按提交顺序逐个返回。单次文件数受场景配置 <Text code>maxFilesPerRequest</Text> 限制（默认 1，需要批量请先调大）。
      </Paragraph>
      <pre style={{ background: '#f5f5f5', padding: 16, borderRadius: 6, overflowX: 'auto' }}>
        <code className="language-bash">{`curl -X POST "http://localhost:11281/flow-api/oss/upload?profile=doc_upload" \\
  -H "Authorization: Bearer <your-token>" \\
  -F "files=@file1.pdf" \\
  -F "files=@file2.docx"`}</code>
      </pre>
      <Alert
        message="第三方系统（开放平台 AppKey）请走 open 端点"
        description={<>使用 AppKey / HMAC 签名而非用户 JWT 的外部系统，请调用 <Text code>POST /flow-api/open/oss/upload?profile={'{profileCode}'}</Text>，参数与本接口一致。</>}
        type="info"
        showIcon
        style={{ marginBottom: 24 }}
      />

      <Divider />

      <Title level={3} id="oss-multipart">3. 大文件分片上传（网关侧分片，后端合并）</Title>
      <Paragraph>
        对于视频、大型安装包等大文件（建议大于 10MB 时使用），系统提供了 <Text strong>init → 逐片 PUT → complete</Text> 三步分片上传。
        它的实现是<Text strong>网关侧分片</Text>：每个分片先暂存在应用服务器，<Text code>complete</Text> 时由后端拼接成完整文件再推送到 OSS，
        <Text strong>并非</Text> S3 原生 multipart（OSS 侧不会创建分片会话，不存在需要上报 ETag 的环节）。
      </Paragraph>
      <Alert
        message="为什么用分片上传而不是直接 POST？"
        description="单次 POST 大文件会受 Nginx client_max_body_size 与网关读超时限制，且中途断网需要从头重传。分片上传每次只传一个小块（建议 5~10MB/片），单片失败可单片重试，也可小并发提速。"
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
      />
      <Alert
        message="使用前必读：当前分片上传的真实限制"
        description={
          <ul style={{ margin: '4px 0 0', paddingLeft: 18 }}>
            <li><Text strong>大小上限仍受全局配置卡控</Text>：<Text code>yu.flow.oss.max-upload-bytes</Text>（默认 50MB）同时限制<Text strong>单个分片</Text>和<Text strong>合并后的总大小</Text>；上传真正的大文件前必须先调大它，否则 complete 会以 <Text code>OSS_FILE_TOO_LARGE</Text> 失败。场景配置 <Text code>maxSizeBytes</Text> 同样生效。</li>
            <li><Text strong>会话是单实例内存态</Text>：uploadId 会话与分片临时文件保存在当前应用实例的内存与本地磁盘。多实例部署时必须保证同一次上传的所有请求命中同一实例（粘滞会话），应用重启后会话也会丢失，表现为 <Text code>OSS_MULTIPART_SESSION_NOT_FOUND</Text>。</li>
            <li><Text strong>断点续传是会话内续传</Text>：仅在会话存活期内（<Text code>yu.flow.oss.multipart-session-ttl-minutes</Text>，默认 120 分钟）可重传单片或继续上传；不支持跨会话/跨天续传。</li>
            <li><Text strong>complete 是一次长请求</Text>：合并时才把完整文件推送给 OSS，文件越大这一步越久，需相应放宽网关/LB 的读超时。</li>
            <li><Text strong>真正的大文件请优先用预签名直传</Text>（见下文第 4 节）：不受 50MB 限制、无会话状态、多实例无需粘滞。本节分片通道仅适用于<Text strong>客户端无法直连对象存储</Text>、但仍需分片重试的场景。</li>
          </ul>
        }
        type="warning"
        showIcon
        style={{ marginBottom: 24 }}
      />

      <Title level={4}>接口调用步骤</Title>

      {/* 步骤一 */}
      <Title level={5} style={{ marginTop: 16 }}>第 1 步：初始化分片上传任务</Title>
      <Paragraph>
        <Space wrap>
          <Tag color="blue">POST</Tag>
          <Text code>/flow-api/oss/multipart/init</Text>
        </Space>
      </Paragraph>
      <Paragraph>
        调用此接口声明本次上传任务，后端完成鉴权、场景校验（扩展名 / MIME 白名单、配额）后创建一个上传会话与分片暂存目录，
        并返回贯穿整个上传流程的 <Text code>uploadId</Text>。此时 OSS 侧尚未写入任何数据。
      </Paragraph>
      <Paragraph><Text strong>请求参数：</Text></Paragraph>
      <pre style={{ background: '#f5f5f5', padding: 16, borderRadius: 6, overflowX: 'auto' }}>
        <code>{`POST /flow-api/oss/multipart/init
    ?profile=video_upload          // 必填，且必须放在 Query / 表单参数（不能只写在 JSON 体里）
    &originalName=大会议录制.mp4   // 推荐：原始文件名（用于落档与展示）
    &contentType=video/mp4         // 推荐：MIME 类型，缺省 application/octet-stream

// 可选：另带一个 JSON 体补充业务字段（originalName / contentType 也可写在这里）
Content-Type: application/json
{
  "originalName": "大会议录制.mp4",
  "contentType":  "video/mp4",
  "bizField1":    "自定义业务字段"      // 其他字段作为 bizFields 原样存入对象记录
}`}</code>
      </pre>
      <Paragraph><Text strong>成功响应（data 字段）：</Text></Paragraph>
      <pre style={{ background: '#f5f5f5', padding: 16, borderRadius: 6, overflowX: 'auto' }}>
        <code>{`{
  "code": 200,
  "data": {
    "uploadId":  "xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",     // 本次上传任务 ID（后端会话 ID），后续步骤必须携带
    "bucket":    "yu-flow",                              // OSS Bucket 名称（仅供参考）
    "objectKey": "video_upload/2026/08/大会议录制.mp4"  // OSS 存储路径（仅供参考）
  }
}`}</code>
      </pre>

      <Divider dashed />

      {/* 步骤二 */}
      <Title level={5}>第 2 步：循环上传每个分片</Title>
      <Paragraph>
        <Space wrap>
          <Tag color="orange">PUT</Tag>
          <Text code>/flow-api/oss/multipart/{`{uploadId}`}/parts/{`{partNumber}`}</Text>
        </Space>
      </Paragraph>
      <Paragraph>
        将文件按固定大小（推荐 <Text strong>5~10 MB / 片</Text>）分割，对每个分片调用一次此接口。
        <Text code>partNumber</Text> 从 <Text strong>1</Text> 开始递增，最大 10000；重复上传同一个 <Text code>partNumber</Text> 会覆盖旧分片（单片重试就靠这个）。
        分片可以并发上传（建议并发数 ≤ 3），也可以串行逐片上传。合并时按 <Text code>partNumber</Text> 升序拼接，因此上传顺序不影响结果。
      </Paragraph>
      <Paragraph><Text strong>上传方式（二选一）：</Text></Paragraph>
      <pre style={{ background: '#f5f5f5', padding: 16, borderRadius: 6, overflowX: 'auto' }}>
        <code>{`// 方式一：multipart/form-data（推荐浏览器端使用）
PUT /flow-api/oss/multipart/{uploadId}/parts/1
Content-Type: multipart/form-data
file=<第 1 个分片的二进制数据>

// 方式二：直接发送原始字节流（适合服务端 SDK 调用）
PUT /flow-api/oss/multipart/{uploadId}/parts/1
Content-Type: application/octet-stream
Content-Length: 5242880
<原始字节流>

// 成功响应
{ "code": 200, "data": null }`}</code>
      </pre>

      <Divider dashed />

      {/* 步骤三 */}
      <Title level={5}>第 3 步：合并分片，完成上传</Title>
      <Paragraph>
        <Space wrap>
          <Tag color="blue">POST</Tag>
          <Text code>/flow-api/oss/multipart/{`{uploadId}`}/complete</Text>
        </Space>
      </Paragraph>
      <Paragraph>
        所有分片上传完毕后，调用此接口通知后端执行合并。后端按分片序号拼接为完整文件推送到 OSS，落对象台账，
        并返回与单文件上传<Text strong>完全相同结构</Text>的 <Text code>OssUploadResultDTO</Text>（注意这里 <Text code>data</Text> 是单个对象而非数组）。
        返回的 <Text code>id</Text> 即业务系统应保存的对象 ID。合并成功后会话与临时分片会被自动清理。
      </Paragraph>
      <pre style={{ background: '#f5f5f5', padding: 16, borderRadius: 6, overflowX: 'auto' }}>
        <code>{`POST /flow-api/oss/multipart/{uploadId}/complete

// 成功响应
{
  "code": 200,
  "data": {
    "id":           "1234567890",
    "visibility":   "PRIVATE",
    "originalName": "大会议录制.mp4",
    "sizeBytes":    104857600,
    "contentType":  "video/mp4",
    "publicPath":   null,          // 仅 PUBLIC 场景有值
    "publicUrl":    null,          // 仅 PUBLIC 场景有值
    "thumbStatus":  "NONE",
    "hasThumbnail": false,
    "expiresAt":    null
  }
}`}</code>
      </pre>

      <Alert
        message="上传失败时如何清理？"
        description={<>如果上传过程中途放弃，请调用 <Text code>DELETE /flow-api/oss/multipart/{'{uploadId}'}</Text> 主动中止，立即释放服务器上的临时分片文件；即使不调用，超过会话 TTL 后也会被定时任务清理。未执行 complete 的任务不会在 OSS 侧产生任何残留对象。</>}
        type="warning"
        showIcon
        style={{ marginBottom: 24 }}
      />

      <Paragraph><Text strong>分片上传时序图：</Text></Paragraph>
      <pre style={{ background: '#282c34', color: '#abb2bf', padding: 16, borderRadius: 6, overflowX: 'auto', fontSize: 13, fontFamily: 'Consolas, monospace', lineHeight: '1.4' }}>
{`  +---------+                               +---------+                               +--------+
  | 客户终端 |                               | Yu Flow |                               |  OSS   |
  +----+----+                               +----+----+                               +----+---+
       |                                         |                                         |
       | 1. POST /flow-api/oss/multipart/init    |                                         |
       |    (profile + originalName)             |                                         |
       |---------------------------------------->|                                         |
       |                                         | 创建会话+本地暂存目录(不写 OSS)         |
       |                                         |                                         |
       |  返回 uploadId + bucket + objectKey     |                                         |
       |<----------------------------------------|                                         |
       |                                         |                                         |
       | 2. PUT /flow-api/oss/multipart/{uploadId}/parts/1                                 |
       |    (第 1 个分片数据, 5~10MB)            |                                         |
       |---------------------------------------->|                                         |
       |                                         | 分片写入服务器本地临时文件              |
       |<----------------------------------------|                                         |
       |  (重复上传 Part 2, 3, ... N)            |                                         |
       |                                         |                                         |
       | 3. POST /flow-api/oss/multipart/{uploadId}/complete                               |
       |---------------------------------------->|                                         |
       |                                         | 按序拼接分片, 一次性 putObject          |
       |                                         |========================================>|
       |                                         |<========================================|
       |                                         | 落库 flow_oss_object + 触发缩略图       |
       |  返回 objectId 与完整对象信息           |                                         |
       |<----------------------------------------|                                         |
       |                                         |                                         |
  +----+----+                               +----+----+                               +----+---+`}
      </pre>

      <Divider />

      <Title level={3} id="oss-presign">4. 预签名直传（客户端 PUT 直达 OSS）</Title>
      <Paragraph>
        与前三种「代理上传」不同，预签名直传的<Text strong>文件字节不经过 Yu Flow</Text>：网关只负责
        <Text strong>开票（签发一个带签名的临时 PUT 地址）</Text>与<Text strong>复核（核对 OSS 上的真实对象）</Text>，
        客户端把文件直接 PUT 给对象存储。流程为 <Text strong>init → PUT uploadUrl → confirm</Text>。
      </Paragraph>
      <Alert
        message="什么时候该选它？"
        description={
          <ul style={{ margin: '4px 0 0', paddingLeft: 18 }}>
            <li><Text strong>大文件首选</Text>：不占用应用内存与磁盘，不受 <Text code>spring.servlet.multipart</Text>（默认 50MB）与 <Text code>yu.flow.oss.max-upload-bytes</Text> 约束，改由 <Text code>yu.flow.oss.presign-max-upload-bytes</Text>（默认 5GB）与场景 <Text code>maxSizeBytes</Text> 卡控。</li>
            <li><Text strong>无会话状态</Text>：不依赖单实例内存会话，多实例部署无需粘滞会话，应用重启也不影响正在直传的文件。</li>
            <li><Text strong>需要能直连对象存储</Text>：签名地址基于连接的对外访问地址（publicBaseUrl）签发，客户端网络必须可达；纯内网隔离场景仍应走代理上传。</li>
          </ul>
        }
        type="info"
        showIcon
        style={{ marginBottom: 16 }}
      />
      <Alert
        message="启用前必做的两件事"
        description={
          <ul style={{ margin: '4px 0 0', paddingLeft: 18 }}>
            <li><Text strong>逐场景开启开关</Text>：上传场景需勾选「开放预签名直传」（<Text code>presignUploadEnabled</Text>），默认关闭；全局还有总开关 <Text code>yu.flow.oss.presign-upload-enabled</Text>。未开启时 init 返回 <Text code>OSS_PRESIGN_UPLOAD_DISABLED</Text>。</li>
            <li><Text strong>桶上放通跨域 PUT</Text>：浏览器直传属于跨域请求，需在对象存储的 CORS 规则里允许来源域的 <Text code>PUT</Text> 方法与 <Text code>Content-Type</Text> 请求头，否则浏览器会在预检阶段直接失败。</li>
          </ul>
        }
        type="warning"
        showIcon
        style={{ marginBottom: 24 }}
      />

      <Title level={4}>接口调用步骤</Title>

      <Title level={5} style={{ marginTop: 16 }}>第 1 步：开票，换取直传地址</Title>
      <Paragraph>
        <Space wrap>
          <Tag color="blue">POST</Tag>
          <Text code>/flow-api/oss/presign/init</Text>
        </Space>
      </Paragraph>
      <Paragraph>
        后端完成鉴权、场景校验（扩展名 / MIME 白名单、声明大小、配额）后，预创建一条 <Text code>PENDING</Text> 状态的对象台账并签发
        <Text code>uploadUrl</Text>。<Text strong>PENDING 台账对所有业务查询与下载均不可见</Text>，只有 confirm 通过后才转为 <Text code>ACTIVE</Text>。
      </Paragraph>
      <pre style={{ background: '#f5f5f5', padding: 16, borderRadius: 6, overflowX: 'auto' }}>
        <code>{`POST /flow-api/oss/presign/init
    ?profile=video_upload          // 必填，且必须放在 Query / 表单参数（不能只写在 JSON 体里）
    &originalName=大会议录制.mp4   // 必填：原始文件名（据此校验扩展名并生成 objectKey）
    &contentType=video/mp4         // 推荐：MIME 类型，缺省 application/octet-stream
    &sizeBytes=1073741824          // 推荐：声明大小，便于提前拦截超限与配额不足

// 可选：另带一个 JSON 体补充业务字段
Content-Type: application/json
{ "bizField1": "自定义业务字段" }

// 成功响应
{
  "code": 200,
  "data": {
    "objectId":      "1234567890",                          // PENDING 台账 ID，confirm / abort 都用它
    "uploadUrl":     "https://oss.example.com/yu-flow/...", // 带签名的直传地址，请勿改写任何参数
    "method":        "PUT",
    "bucket":        "yu-flow",
    "objectKey":     "video_upload/2026/08/大会议录制.mp4",
    "expireSeconds": 3600,
    "urlExpiresAt":  "2026-08-06 12:00:00"
  }
}`}</code>
      </pre>

      <Divider dashed />

      <Title level={5}>第 2 步：把文件 PUT 到 uploadUrl</Title>
      <Paragraph>
        <Space wrap>
          <Tag color="orange">PUT</Tag>
          <Text code>{'{uploadUrl}'}</Text>
          <Tag>直连对象存储，不经过 Yu Flow</Tag>
        </Space>
      </Paragraph>
      <Paragraph>
        请求体为<Text strong>完整文件的原始字节</Text>（不是 <Text code>multipart/form-data</Text>，不要包 FormData）。
        签名只覆盖桶、对象键与有效期，因此<Text strong>不要</Text>附带登录 Cookie、Token、CSRF 头等任何业务凭证，也不要经由前端统一请求封装发出，否则可能因附加头/改写 URL 导致签名不匹配。
      </Paragraph>
      <pre style={{ background: '#f5f5f5', padding: 16, borderRadius: 6, overflowX: 'auto' }}>
        <code>{`// 浏览器端示例（用 XHR 便于拿上传进度）
const xhr = new XMLHttpRequest();
xhr.open('PUT', uploadUrl, true);
xhr.setRequestHeader('Content-Type', file.type);
xhr.upload.onprogress = (e) => console.log(Math.round((e.loaded / e.total) * 100) + '%');
xhr.send(file);

// curl 示例
curl -X PUT -T ./大会议录制.mp4 -H "Content-Type: video/mp4" "<uploadUrl>"`}</code>
      </pre>

      <Divider dashed />

      <Title level={5}>第 3 步：确认，完成台账</Title>
      <Paragraph>
        <Space wrap>
          <Tag color="blue">POST</Tag>
          <Text code>/flow-api/oss/presign/{`{objectId}`}/confirm</Text>
        </Space>
      </Paragraph>
      <Paragraph>
        直传成功后必须回调 confirm。后端会向 OSS 查询该对象的<Text strong>真实大小与真实 Content-Type</Text>再次校验（签名无法约束这两项），
        通过后台账转 <Text code>ACTIVE</Text> 并按需触发缩略图，返回与单文件上传<Text strong>完全相同结构</Text>的 <Text code>OssUploadResultDTO</Text>。
        该接口<Text strong>幂等</Text>：对已 ACTIVE 的台账重复调用会直接回放结果。
      </Paragraph>
      <pre style={{ background: '#f5f5f5', padding: 16, borderRadius: 6, overflowX: 'auto' }}>
        <code>{`POST /flow-api/oss/presign/{objectId}/confirm

// 成功响应（结构同 1. 单文件上传，注意 data 是单个对象）
{
  "code": 200,
  "data": {
    "id":           "1234567890",
    "visibility":   "PRIVATE",
    "originalName": "大会议录制.mp4",
    "sizeBytes":    1073741824,   // 以 OSS 上的真实大小为准
    "contentType":  "video/mp4",
    "publicPath":   null,
    "publicUrl":    null,
    "thumbStatus":  "NONE",
    "hasThumbnail": false,
    "expiresAt":    null
  }
}`}</code>
      </pre>
      <Alert
        message="复核不通过或中途放弃怎么办？"
        description={
          <ul style={{ margin: '4px 0 0', paddingLeft: 18 }}>
            <li><Text strong>复核不通过</Text>（超出大小上限、MIME 不在白名单、配额不足、对象为空）：后端会<Text strong>立即物理删除已直传的对象</Text>并抛出错误，台账保持 PENDING 由定时任务回收。</li>
            <li><Text strong>直传失败或用户取消</Text>：调用 <Text code>DELETE /flow-api/oss/presign/{'{objectId}'}</Text> 主动放弃，台账置为已删除并交由清理任务物理删除远端残留。</li>
            <li><Text strong>忘记回调</Text>：超过 <Text code>yu.flow.oss.presign-pending-ttl-minutes</Text>（默认 120 分钟）仍处于 PENDING 的台账会被定时任务自动回收，不会长期占用配额。</li>
            <li><Text strong>未找到对象</Text>：confirm 返回 <Text code>OSS_PRESIGN_OBJECT_MISSING</Text> 说明 PUT 实际没有成功，请重新走 init（旧票据的 objectKey 不可复用）。</li>
          </ul>
        }
        type="warning"
        showIcon
        style={{ marginBottom: 24 }}
      />

      <Paragraph><Text strong>三种上传通道怎么选：</Text></Paragraph>
      <pre style={{ background: '#f5f5f5', padding: 16, borderRadius: 6, overflowX: 'auto' }}>
        <code>{`                        代理上传            网关侧分片          预签名直传
文件字节是否过网关       是                  是                  否
大小上限              max-upload-bytes    max-upload-bytes    presign-max-upload-bytes (5GB)
多实例部署             无要求               需粘滞会话           无要求
上传进度               单请求粒度           按分片               原生进度事件
客户端可否直连 OSS      不要求               不要求               必须可达 + 桶放通 CORS
推荐场景               中小文件、表单附件    需分片重试且不可直连  大文件、高并发上传`}</code>
      </pre>

      <Divider />

      <Title level={3} id="oss-caller">5. 访问规则</Title>
      <Paragraph>
        私有上传场景用一张规则表同时表达「谁能传、谁能下、能看多大」
        （字段 <Text code>caller_policy</Text>：<Text code>{`{"rules":[...]}`}</Text>）。
        配置入口：管理端 → OSS 上传场景 → <Text strong>上传与访问权限</Text>。
        预置覆盖常见场景：个人文件、用户+运营、部门资料、运营上传·全员下载、仅运营内部、开放应用代传、匿名征集。
      </Paragraph>
      <ul>
        <li>一行一类人，组间或；下载范围 SELF / DEPT / ALL 取最宽合并。</li>
        <li>身份：任何已登录 / 指定身份 / 开放应用。开放应用不会被「任何已登录」带入。</li>
        <li>私有且要求登录时必须至少一条规则，否则宿主侧失败关闭。</li>
        <li>匹配失败 HTTP <Text code>403</Text>，错误码 <Text code>OSS_CALLER_DENIED</Text>。</li>
        <li>管理端 JWT 不走规则表；<Text code>downloadPerm</Text> 仅作管理端跨范围兜底。</li>
      </ul>
      <Paragraph>
        字段语义见「核心用户体系与数据隔离」§3.1。接口/目录入站仍用原来的单组调用方策略，与 OSS 规则表无关。
      </Paragraph>
    </Typography>

  );
};

export default OssUploadDoc;
