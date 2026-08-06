import { Typography, Divider, Alert } from 'antd';
import React from 'react';

const { Title, Paragraph, Text } = Typography;

const AuthSpiDoc: React.FC = () => {
  return (
    <Typography style={{ maxWidth: 900, margin: '0 auto', paddingBottom: 40 }}>
      <Title level={2} style={{ marginTop: 0 }}>宿主系统对接与安全认证指南</Title>
      
      <Alert 
        message="架构说明" 
        description="Yu Flow 支持独立运行（此时使用内置的测试用户上下文，仅作基础鉴权），但在生产环境中，为了获得企业级的数据隔离与业务体系打通，强烈建议您将其深度集成到您的核心业务系统（宿主系统）中。"
        type="info" 
        showIcon 
        style={{ marginBottom: 24 }}
      />

      <Title level={3}>1. 核心用户体系对接 (FlowHostPrincipalProvider)</Title>
      <Paragraph>
        当 Yu Flow 嵌入到您的 Spring Boot 宿主系统后，流程引擎需要知道当前操作者是谁。您只需在宿主工程中实现 <Text code>FlowHostPrincipalProvider</Text> 接口即可注入用户信息。
      </Paragraph>
      <pre style={{ background: '#f5f5f5', padding: 16, borderRadius: 6, overflowX: 'auto' }}>
        <code className="language-java">{`import org.springframework.stereotype.Component;
import org.yu.flow.spi.FlowHostPrincipalProvider;
import org.yu.flow.spi.dto.FlowPrincipalDTO;

@Component
public class MyHostPrincipalProvider implements FlowHostPrincipalProvider {
    @Override
    public FlowPrincipalDTO getCurrentPrincipal() {
        // 从您自己的系统上下文（如 Spring Security / Shiro / Sa-Token）获取当前登录用户
        // 返回包含 userId, username, tenantId 等信息的 FlowPrincipalDTO
        return new FlowPrincipalDTO("user123", "张三");
    }
}`}</code>
      </pre>

      <Divider />

      <Title level={3}>2. 数据隔离与权限对接 (FlowHostDataScopeProvider)</Title>
      <Paragraph>
        如果您希望实现部门级或公司级的数据隔离（例如：张三只能查看本部门发起的流程实例，李四可以查看全公司），需要实现 <Text code>FlowHostDataScopeProvider</Text>。
      </Paragraph>
      <Paragraph>
        引擎在执行查询（如待办列表、实例列表）时，会自动回调此 SPI 获取当前用户的数据可见范围（Data Scope），并拼装底层 SQL。
      </Paragraph>
      <pre style={{ background: '#f5f5f5', padding: 16, borderRadius: 6, overflowX: 'auto' }}>
        <code className="language-java">{`@Component
public class MyHostDataScopeProvider implements FlowHostDataScopeProvider {
    @Override
    public FlowDataScopeDTO getCurrentDataScope() {
        // 根据当前用户的角色，返回其可见的部门 ID 列表或标识
        FlowDataScopeDTO scope = new FlowDataScopeDTO();
        scope.setScopeType(FlowHostScopeType.DEPT);
        scope.setDeptIds(Arrays.asList("dept-1", "dept-2"));
        return scope;
    }
}`}</code>
      </pre>

      <Divider />

      <Title level={3}>3. 前端界面与菜单融合</Title>
      <Paragraph>
        通过将本系统的 <Text code>flow-ui</Text> 路由直接嵌入到您宿主前端的 Umi 或 Vue 路由中，您可以实现左侧菜单栏的完全统一。通过隐藏当前的默认 Layout，您的用户将感觉不到这是两个系统。
      </Paragraph>
    </Typography>
  );
};

export default AuthSpiDoc;
