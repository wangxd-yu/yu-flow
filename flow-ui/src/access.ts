import { hasPerm } from '@/services/auth';

export type InitialStateType = {
  name?: string;
  displayName?: string;
  isLogin?: boolean;
  userId?: string;
  roles?: string[];
  permissions?: string[];
  legacyAdmin?: boolean;
  ossEnabled?: boolean;
};

/**
 * Umi access：路由 / 菜单鉴权。
 * 约定：拥有 * 或对应 view/write 即可进入页面；写操作另由后端 @RequirePerm 兜底。
 *
 * 重要：未登录时必须返回 true，否则会在跳转 /login 前把带 access 的路由全部判为不可达 → 白屏。
 * 未登录时的菜单/页头已由 layout.menuRender/headerRender = false 隐藏。
 */
export default function access(initialState: InitialStateType) {
  const perms = initialState?.permissions || [];
  const loggedIn = !!initialState?.isLogin;
  // 旧后端无此字段时视为已启用，避免独立部署误藏菜单
  const ossEnabled = !loggedIn || initialState?.ossEnabled !== false;

  const can = (...codes: string[]) => {
    if (!loggedIn) return true;
    return hasPerm(perms, ...codes);
  };

  return {
    canHome: can('home:view', '*'),
    canApi: can('flow:api:view', 'flow:api:write', '*'),
    canApiWrite: can('flow:api:write', '*'),
    canTask: can('flow:task:view', 'flow:task:write', '*'),
    canTaskWrite: can('flow:task:write', '*'),
    canService: can('flow:service:view', 'flow:service:write', '*'),
    canServiceWrite: can('flow:service:write', '*'),
    canPage: can('flow:page:view', 'flow:page:write', '*'),
    canRuntime: can('flow:runtime:view', '*'),
    canAlert: can('flow:alert:view', 'flow:alert:edit', '*'),
    canOpen: can('flow:open:view', 'flow:open:write', '*'),
    canLog: can('log:view', '*'),
    canDs: can('flow:ds:view', 'flow:ds:write', '*'),
    canMq: can('flow:mq:view', 'flow:mq:write', '*'),
    canMqWrite: can('flow:mq:write', '*'),
    canOss: ossEnabled && can('flow:oss:view', 'flow:oss:write', '*'),
    canOssWrite: ossEnabled && can('flow:oss:write', '*'),
    canOssAudit: ossEnabled && can('flow:oss:audit', 'flow:oss:admin', '*'),
    canModel: can('flow:model:view', 'flow:model:write', '*'),
    canTemplate: can('sys:template:view', 'sys:template:write', '*'),
    canMacro: can('sys:macro:view', 'sys:macro:write', '*'),
    canConfig: can('sys:config:view', 'sys:config:write', '*'),
    canHost: can('sys:host:view', 'sys:host:write', '*'),
    canHostWrite: can('sys:host:write', '*'),
    canUser: can('sys:user:view', 'sys:user:write', '*'),
    canUserWrite: can('sys:user:write', '*'),
    canRole: can('sys:role:view', 'sys:role:write', '*'),
    canRoleWrite: can('sys:role:write', '*'),
    canDocs: can('docs:view', '*'),
    /** 流程资产父菜单：任一子权限（含 MQ 任务；OSS 上传配置仅模块启用时计入） */
    canAssetGroup:
      can(
        'flow:api:view',
        'flow:api:write',
        'flow:task:view',
        'flow:task:write',
        'flow:service:view',
        'flow:service:write',
        'flow:page:view',
        'flow:page:write',
        'flow:mq:view',
        'flow:mq:write',
        '*',
      ) || (ossEnabled && can('flow:oss:view', 'flow:oss:write')),
    /** 运行观测：运行 / 告警 / 开放（日志已独立一级） */
    canOpsGroup: can(
      'flow:runtime:view',
      'flow:alert:view',
      'flow:alert:edit',
      'flow:open:view',
      'flow:open:write',
      '*',
    ),
    /** 日志中心父菜单：执行日志或（模块启用时）OSS 台账/下载 */
    canLogGroup:
      can('log:view', '*') ||
      (ossEnabled && can('flow:oss:view', 'flow:oss:write', 'flow:oss:audit', 'flow:oss:admin')),
    /** 基础设施：连接与模型（OSS 连接仅模块启用时计入） */
    canInfraGroup:
      can(
        'flow:ds:view',
        'flow:ds:write',
        'flow:mq:view',
        'flow:mq:write',
        'flow:model:view',
        'flow:model:write',
        '*',
      ) || (ossEnabled && can('flow:oss:view', 'flow:oss:write')),
    /** 消息队列父菜单：任一子权限 */
    canMqGroup: can('flow:mq:view', 'flow:mq:write', '*'),
    canPlatformGroup: can(
      'sys:template:view',
      'sys:template:write',
      'sys:macro:view',
      'sys:macro:write',
      'sys:config:view',
      'sys:config:write',
      'sys:host:view',
      'sys:host:write',
      'sys:user:view',
      'sys:user:write',
      'sys:role:view',
      'sys:role:write',
      'docs:view',
      '*',
    ),
  };
}
