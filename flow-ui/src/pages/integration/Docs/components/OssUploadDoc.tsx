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

      <Title level={3}>1. 基础单文件上传 (直传)</Title>
      <Paragraph>
        <Space>
          <Tag color="blue">POST</Tag>
          <Text code>/flow-api/oss/upload/{`{profileCode}`}</Text>
        </Space>
      </Paragraph>
      <Paragraph>
        这是最基础的上传方式。适用于小文件（推荐小于 20MB）的头像、证件、小文档上传。
        您需要先在管理后台创建一个“上传配置场景”并获取 <Text code>profileCode</Text>。
      </Paragraph>
      <Paragraph>
        <Text strong>请求示例 (cURL)：</Text>
      </Paragraph>
      <pre style={{ background: '#f5f5f5', padding: 16, borderRadius: 6, overflowX: 'auto' }}>
        <code className="language-bash">{`curl -X POST "http://localhost:11281/flow-api/oss/upload/avatar_upload" \\
  -H "Authorization: Bearer <your-token>" \\
  -F "file=@/path/to/your/avatar.jpg"`}</code>
      </pre>
      <Paragraph>
        <Text strong>响应结构：</Text>
      </Paragraph>
      <pre style={{ background: '#f5f5f5', padding: 16, borderRadius: 6, overflowX: 'auto' }}>
        <code className="language-json">{`{
  "code": 200,
  "msg": "success",
  "data": {
    "objectId": "1234567890",
    "bucket": "yu-flow",
    "objectKey": "avatar_upload/2026/08/xyz.jpg",
    "previewUrl": "http://192.168.1.100:9000/yu-flow/..."
  }
}`}</code>
      </pre>

      <Divider />

      <Title level={3}>2. 多文件批量上传</Title>
      <Paragraph>
        <Space>
          <Tag color="blue">POST</Tag>
          <Text code>/flow-api/oss/upload/{`{profileCode}`}/batch</Text>
        </Space>
      </Paragraph>
      <Paragraph>
        如果您需要在一个表单中同时上传多个附件，可以使用批量上传接口。
      </Paragraph>
      <pre style={{ background: '#f5f5f5', padding: 16, borderRadius: 6, overflowX: 'auto' }}>
        <code className="language-bash">{`curl -X POST "http://localhost:11281/flow-api/oss/upload/doc_upload/batch" \\
  -H "Authorization: Bearer <your-token>" \\
  -F "files=@file1.pdf" \\
  -F "files=@file2.docx"`}</code>
      </pre>

      <Divider />

      <Title level={3}>3. 大文件分片上传 (预签名上传)</Title>
      <Paragraph>
        <Space>
          <Tag color="blue">POST</Tag>
          <Text code>/flow-api/oss/upload/{`{profileCode}`}/presign</Text>
        </Space>
      </Paragraph>
      <Paragraph>
        对于视频、大型安装包等大文件（大于 20MB），为了避免占用应用服务器带宽并提高上传速度，强烈推荐使用**预签名直传（Presigned URL）**方案。
      </Paragraph>
      <Paragraph>
        <Text strong>对接流程：</Text>
      </Paragraph>
      <ol style={{ lineHeight: '2' }}>
        <li>前端请求 <Text code>/presign</Text> 接口，声明要上传的文件名和大小。</li>
        <li>后端进行安全校验（后缀、大小、空间等），并向 OSS 申请生成一段带有时间戳签名的 <Text code>uploadUrl</Text>，并提前在数据库落档一条待上传的记录。</li>
        <li>前端拿到 <Text code>uploadUrl</Text> 后，直接使用 <Text code>PUT</Text> 请求将文件流抛给 OSS 存储节点（此时流量完全不经过应用后端）。</li>
        <li>OSS 接收成功后，前端拿到新生成的 <Text code>objectId</Text> 即可进行业务提交。</li>
      </ol>
      <Alert 
        message="为什么推荐预签名？" 
        description="传统上传方式下，文件流：前端 -> Nginx -> Java 后端 -> OSS，不仅链路极长，而且极易触发 Nginx client_max_body_size 限制与 Java 内存 OOM。预签名模式下，文件流：前端 -> OSS，速度最快且不消耗服务器资源。"
        type="success" 
        showIcon 
      />
    </Typography>
  );
};

export default OssUploadDoc;
