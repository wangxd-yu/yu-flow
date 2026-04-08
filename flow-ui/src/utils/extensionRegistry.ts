import React from 'react';

/**
 * 全局插件与扩展组件注册表
 * 
 * 用于在开源版（OSS）与商业增强版（Pro）之间解耦组件。
 * OSS 抛出插槽，去此处获取；
 * Pro 版在初始化或者启动阶段，注入增强版组件至此处。
 */
class ExtensionRegistry {
  private components = new Map<string, React.ComponentType<any>>();

  public register(name: string, component: React.ComponentType<any>) {
    this.components.set(name, component);
  }

  public get(name: string): React.ComponentType<any> | undefined {
    return this.components.get(name);
  }
}

// 导出一个全局单例实例
export const extensionRegistry = new ExtensionRegistry();
