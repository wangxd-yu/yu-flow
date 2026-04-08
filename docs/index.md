---
layout: home

hero:
  name: "Yu Flow"
  text: "接口逻辑在线改，不发版，秒生效。"
  tagline: "嵌入式 API 编排引擎。引入一个 Spring Boot Starter，你的系统即刻拥有「运行时动态修改接口逻辑」的能力——改完保存，线上直接生效，全程零停机。"
  image:
    src: /image.png
    alt: Yu Flow 流程编排架构
  actions:
    - theme: brand
      text: 🚀 免费体验 Demo
      link: /guide/quick-start
    - theme: alt
      text: 5 分钟快速接入 →
      link: /guide/introduction

features:
  - icon: ⚡
    title: 零改造动态化
    details: 把「发版」变成「配置」。接口业务逻辑在线修改，变更毫秒级生效。告别 改代码→提测→停机上线 的噩梦循环，让运维从此不再被业务叫醒。

  - icon: 🔌
    title: 纯粹的嵌入式架构
    details: 以 Spring Boot Starter 形式无缝寄生于现有工程，不替换框架、不入侵架构、不产生技术债。引入依赖，就是全部。你的项目还是你的项目。

  - icon: 🧩
    title: 可视化 Flow 编排
    details: 拖拽式流程画布，将多个接口编排成完整业务流程。条件分支、数据映射、脚本计算、异常处理——全部可视化完成，所见即所得。

  - icon: 🛡️
    title: 企业级安全治理
    details: 内置脚本沙箱防止恶意注入，完整的审计日志追溯一切变更，多租户权限隔离开箱即用。每一次改动，都有据可查。

---

<div class="trust-bar">
  <span>MIT 开源</span>
  <span class="trust-dot">·</span>
  <span>Spring Boot 无缝嵌入</span>
  <span class="trust-dot">·</span>
  <span>生产环境验证</span>
  <span class="trust-dot">·</span>
  <span>5 分钟完成接入</span>
</div>

<div class="home-content">

## ⏱️ 同一个需求，两种命运

<p class="section-subtitle">你选哪个？</p>

<div class="comparison-scenario">
  <div class="scenario-quote">
    <span class="scenario-role">📢 产品经理</span>
    <p>「把 <code>/order/price</code> 接口加个 <strong>VIP 用户打八折</strong> 的逻辑，明天要上线。」</p>
  </div>
</div>

<div class="comparison-grid">
  <div class="comparison-card comparison-old">
    <div class="comparison-header comparison-header-old">
      <span class="comparison-badge badge-old">😤 传统模式</span>
      <h3>一次需求，三天上线</h3>
    </div>
    <div class="comparison-timeline">
      <div class="timeline-item">
        <span class="timeline-day">Day 1</span>
        <span>研发修改 Java 代码，本地联调、写单测</span>
      </div>
      <div class="timeline-item">
        <span class="timeline-day">Day 2</span>
        <span>提交测试单，等 QA 排期回归</span>
      </div>
      <div class="timeline-item">
        <span class="timeline-day">Day 3</span>
        <span>申请上线窗口，凌晨停机发版</span>
      </div>
    </div>

```java
// OrderPriceService.java — 改动散落在业务代码深处
public BigDecimal calculate(Order order) {
    BigDecimal price = order.getOriginalPrice();
    // ↓↓↓ 每次需求变更都要改这里，提测，发版 ↓↓↓
    if (order.getUser().isVip()) {
        price = price.multiply(new BigDecimal("0.8"));
    }
    return price;
}
```

  <div class="comparison-result result-old">
    <span>⏰ 2~3 天</span>
    <span>🔴 需停机</span>
    <span>⚠️ 回滚难</span>
    <span>😫 加班</span>
  </div>
  </div>

  <div class="comparison-card comparison-new">
    <div class="comparison-header comparison-header-new">
      <span class="comparison-badge badge-new">🚀 Yu Flow</span>
      <h3>一次配置，秒级生效</h3>
    </div>
    <div class="comparison-steps">
      <p class="step-desc">打开编排器 → 拖入 <strong>If 分支节点</strong> → 保存。<strong>完了。</strong></p>
    </div>

```json
{
  "node": "if-branch",
  "condition": "#{user.vip == true}",
  "trueHandler": {
    "node": "script",
    "script": "price = price * 0.8"
  }
}
```

  <div class="comparison-result result-new">
    <span>⚡ < 5 分钟</span>
    <span>🟢 零停机</span>
    <span>↩️ 一键回滚</span>
    <span>😎 不加班</span>
  </div>
  </div>
</div>

<p class="comparison-footnote">Yu Flow 让接口执行逻辑从「编译时固化」变为「运行时可配」。所有变更均有完整审计记录，随时可一键回滚。<br>这不是演示环境的魔法——<strong>这是你的生产环境的日常。</strong></p>

## 🎯 谁最需要 Yu Flow？

<p class="section-subtitle">如果你正为以下场景头疼，Yu Flow 就是为你而生</p>

<div class="audience-grid">
  <div class="audience-card">
    <div class="audience-icon">🏦</div>
    <h3>政务 / 金融系统</h3>
    <p><strong>监管政策频繁调整，每次发版审批都是漫长等待。</strong></p>
    <p>Yu Flow 让规则变更绕过发版流程——审批通过的瞬间，新规则直接生效。内置沙箱隔离与审计日志满足等保合规要求。</p>
    <p class="audience-tagline">「政策今天发，逻辑今天改，明天不用加班」</p>
  </div>
  <div class="audience-card">
    <div class="audience-icon">🔗</div>
    <h3>多系统数据集成</h3>
    <p><strong>ERP、CRM、WMS 各自为政，接口格式五花八门。</strong></p>
    <p>Yu Flow 编排引擎统一纳管异构接口，通过可视化流程完成数据清洗、映射与分发，不再维护一堆胶水代码。</p>
    <p class="audience-tagline">「5 个系统的数据打通，不再写 5 套对接代码」</p>
  </div>
  <div class="audience-card">
    <div class="audience-icon">🏃</div>
    <h3>敏捷互联网团队</h3>
    <p><strong>产品需求永远排不完，研发永远在加班。</strong></p>
    <p>将高频变更的业务逻辑从代码仓库中剥离，交还给业务侧自主配置。研发聚焦核心功能，不再为琐碎改动消耗精力。</p>
    <p class="audience-tagline">「产品改需求，不用再排研发工期」</p>
  </div>
</div>

## 💎 技术底座一览

<p class="section-subtitle">坚实的基座，不妥协的生产级品质</p>

<div class="tech-grid">
  <div class="tech-item">
    <div class="tech-icon">📦</div>
    <div class="tech-label">接入成本</div>
    <div class="tech-value">引入 Maven Starter，配置数据源，<strong>5 分钟完成接入</strong></div>
  </div>
  <div class="tech-item">
    <div class="tech-icon">⚙️</div>
    <div class="tech-label">执行引擎</div>
    <div class="tech-value">自研流程编排 + 动态代理，<strong>毫秒级热更新</strong></div>
  </div>
  <div class="tech-item">
    <div class="tech-icon">🔒</div>
    <div class="tech-label">安全隔离</div>
    <div class="tech-value">脚本沙箱 + 多租户隔离，<strong>满足等保合规</strong></div>
  </div>
  <div class="tech-item">
    <div class="tech-icon">📊</div>
    <div class="tech-label">可观测性</div>
    <div class="tech-value">全链路执行日志、变更审计、<strong>慢查询追踪一体化</strong></div>
  </div>
  <div class="tech-item">
    <div class="tech-icon">🧩</div>
    <div class="tech-label">扩展机制</div>
    <div class="tech-value">后端可插拔 Handler / Trait 插件，<strong>前端可注册自定义渲染器</strong></div>
  </div>
  <div class="tech-item">
    <div class="tech-icon">🤖</div>
    <div class="tech-label">AI 集成</div>
    <div class="tech-value">内置自然语言 → DSL 转换，<strong>接入主流大模型开箱即用</strong></div>
  </div>
</div>

## 🎬 5 分钟，从零到一个动态 API

<p class="section-subtitle">不需要安装任何东西。打开浏览器，跟着做</p>

<div class="demo-steps">
  <div class="demo-step">
    <div class="step-number">1</div>
    <div class="step-content">
      <h3>连接数据库 <span class="step-time">30 秒</span></h3>
      <p>填入 JDBC 连接串，Yu Flow 自动发现所有表结构</p>
    </div>
  </div>
  <div class="demo-step">
    <div class="step-number">2</div>
    <div class="step-content">
      <h3>创建数据模型 <span class="step-time">60 秒</span></h3>
      <p>选择目标表，配置字段名称和校验规则，一键生成 CRUD API</p>
    </div>
  </div>
  <div class="demo-step">
    <div class="step-number">3</div>
    <div class="step-content">
      <h3>编排业务逻辑 <span class="step-time">120 秒</span></h3>
      <p>在可视化画布上拖拽节点：数据库查询 → 条件判断 → 脚本计算 → 返回结果</p>
    </div>
  </div>
  <div class="demo-step">
    <div class="step-number">4</div>
    <div class="step-content">
      <h3>保存，生效 <span class="step-time">10 秒</span></h3>
      <p>点击保存，你的新接口已经在线上运行了。<strong>没有编译，没有打包，没有发版。</strong></p>
    </div>
  </div>
</div>

<div class="demo-cta">
  <a href="/guide/quick-start" class="cta-button cta-button-demo">打开在线 Demo，亲手试试 →</a>
  <p class="demo-cta-sub">无需注册，浏览器直接体验</p>
</div>

---

<div class="bottom-cta">
  <h2>不要再为无休止的接口变更加班了。</h2>
  <p>给 Yu Flow 5 分钟，它还你每一个本不该加班的夜晚。</p>
  <div class="bottom-cta-actions">
    <a href="/guide/quick-start" class="cta-button">立即开始，免费体验 →</a>
    <a href="https://github.com/your-repo/yu-flow" class="cta-button-secondary" target="_blank">查看 GitHub 源码</a>
  </div>
  <p class="bottom-trust">MIT 开源 · 永久免费 · 生产级品质</p>
</div>

</div>

<style>
:root {
  --vp-home-hero-name-color: transparent;
  --vp-home-hero-name-background: -webkit-linear-gradient(135deg, #6366f1 0%, #8b5cf6 40%, #06b6d4 100%);
  --vp-home-hero-image-background-image: linear-gradient(135deg, rgba(99, 102, 241, 0.15) 0%, rgba(6, 182, 212, 0.15) 100%);
  --vp-home-hero-image-filter: blur(56px);
}

/* ---- 放大首页图片 ---- */
.VPHero .image {
  transform: translateX(40px);
}

.VPHero .image-container {
  transform: scale(2);
  transform-origin: center center;
}

@media (max-width: 960px) {
  .VPHero .image {
    transform: none;
  }
  .VPHero .image-container {
    transform: scale(1.2);
    transform-origin: center center;
  }
}

/* ---- 信任条 ---- */
.trust-bar {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 8px;
  padding: 16px 24px;
  font-size: 0.9rem;
  color: var(--vp-c-text-2);
  border-bottom: 1px solid var(--vp-c-divider);
  letter-spacing: 0.02em;
}

.trust-dot {
  opacity: 0.4;
}

/* ---- 通用内容区 ---- */
.home-content {
  max-width: 1060px;
  margin: 0 auto;
  padding: 64px 24px 80px;
}

.home-content h2 {
  font-size: 2rem;
  font-weight: 700;
  margin-bottom: 4px;
  background: -webkit-linear-gradient(135deg, #6366f1, #06b6d4);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
}

.home-content .section-subtitle {
  font-size: 1.05rem;
  color: var(--vp-c-text-2);
  margin-top: 0;
  margin-bottom: 32px;
}

.home-content hr {
  margin: 64px 0;
  border: none;
  border-top: 1px solid var(--vp-c-divider);
}

.home-content h3 {
  font-size: 1.3rem;
  font-weight: 600;
  margin: 24px 0 8px;
}

/* ---- 对比模块：场景引述 ---- */
.comparison-scenario {
  margin-bottom: 32px;
}

.scenario-quote {
  background: var(--vp-c-bg-soft);
  border-left: 4px solid #6366f1;
  border-radius: 0 12px 12px 0;
  padding: 20px 24px;
}

.scenario-role {
  font-size: 0.85rem;
  font-weight: 600;
  color: #6366f1;
  text-transform: uppercase;
  letter-spacing: 0.05em;
}

.scenario-quote p {
  margin: 8px 0 0;
  font-size: 1.1rem;
  line-height: 1.6;
  color: var(--vp-c-text-1);
}

/* ---- 对比模块：卡片网格 ---- */
.comparison-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
  margin-bottom: 24px;
}

.comparison-card {
  border-radius: 16px;
  padding: 28px 24px;
  border: 1px solid var(--vp-c-divider);
  transition: all 0.3s ease;
}

.comparison-card:hover {
  transform: translateY(-2px);
}

.comparison-old {
  background: linear-gradient(180deg, rgba(239, 68, 68, 0.04) 0%, var(--vp-c-bg) 100%);
}

.comparison-old:hover {
  border-color: rgba(239, 68, 68, 0.3);
  box-shadow: 0 8px 32px rgba(239, 68, 68, 0.08);
}

.comparison-new {
  background: linear-gradient(180deg, rgba(99, 102, 241, 0.06) 0%, var(--vp-c-bg) 100%);
}

.comparison-new:hover {
  border-color: rgba(99, 102, 241, 0.4);
  box-shadow: 0 8px 32px rgba(99, 102, 241, 0.12);
}

.comparison-header {
  margin-bottom: 16px;
}

.comparison-header h3 {
  margin: 8px 0 0;
  font-size: 1.2rem;
}

.comparison-badge {
  display: inline-block;
  padding: 4px 12px;
  border-radius: 20px;
  font-size: 0.8rem;
  font-weight: 600;
  letter-spacing: 0.03em;
}

.badge-old {
  background: rgba(239, 68, 68, 0.1);
  color: #ef4444;
}

.badge-new {
  background: rgba(99, 102, 241, 0.1);
  color: #6366f1;
}

/* 时间线 */
.comparison-timeline {
  display: flex;
  flex-direction: column;
  gap: 8px;
  margin-bottom: 16px;
}

.timeline-item {
  display: flex;
  align-items: center;
  gap: 12px;
  font-size: 0.92rem;
  color: var(--vp-c-text-2);
  padding: 6px 0;
}

.timeline-day {
  flex-shrink: 0;
  display: inline-block;
  width: 48px;
  padding: 2px 0;
  text-align: center;
  font-size: 0.78rem;
  font-weight: 700;
  color: #ef4444;
  background: rgba(239, 68, 68, 0.08);
  border-radius: 4px;
}

.comparison-steps .step-desc {
  font-size: 1rem;
  color: var(--vp-c-text-2);
  line-height: 1.7;
  margin: 0 0 16px;
}

/* 对比结果标签 */
.comparison-result {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 16px;
  padding-top: 16px;
  border-top: 1px solid var(--vp-c-divider);
}

.comparison-result span {
  display: inline-block;
  padding: 4px 12px;
  border-radius: 6px;
  font-size: 0.82rem;
  font-weight: 500;
}

.result-old span {
  background: rgba(239, 68, 68, 0.06);
  color: #dc2626;
}

.result-new span {
  background: rgba(99, 102, 241, 0.08);
  color: #6366f1;
}

.comparison-footnote {
  text-align: center;
  font-size: 0.95rem;
  color: var(--vp-c-text-2);
  line-height: 1.7;
  max-width: 720px;
  margin: 0 auto;
}

/* ---- 受众卡片网格 ---- */
.audience-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
  margin-top: 28px;
}

.audience-card {
  background: var(--vp-c-bg-soft);
  border: 1px solid var(--vp-c-divider);
  border-radius: 12px;
  padding: 28px 24px;
  transition: all 0.3s ease;
}

.audience-card:hover {
  border-color: #6366f1;
  box-shadow: 0 8px 32px rgba(99, 102, 241, 0.1);
  transform: translateY(-4px);
}

.audience-icon {
  font-size: 2.2rem;
  margin-bottom: 12px;
}

.audience-card h3 {
  margin-top: 0;
  margin-bottom: 12px;
  font-size: 1.15rem;
}

.audience-card p {
  font-size: 0.95rem;
  line-height: 1.65;
  color: var(--vp-c-text-2);
  margin: 8px 0 0;
}

.audience-card p strong {
  color: var(--vp-c-text-1);
}

.audience-tagline {
  margin-top: 16px !important;
  padding-top: 12px;
  border-top: 1px dashed var(--vp-c-divider);
  font-style: italic;
  color: #6366f1 !important;
  font-weight: 500;
}

/* ---- 技术底座网格 ---- */
.tech-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16px;
  margin-top: 24px;
}

.tech-item {
  background: var(--vp-c-bg-soft);
  border: 1px solid var(--vp-c-divider);
  border-radius: 12px;
  padding: 24px 20px;
  transition: all 0.3s ease;
}

.tech-item:hover {
  border-color: rgba(99, 102, 241, 0.3);
  box-shadow: 0 4px 20px rgba(99, 102, 241, 0.08);
  transform: translateY(-2px);
}

.tech-icon {
  font-size: 1.6rem;
  margin-bottom: 8px;
}

.tech-label {
  font-size: 0.82rem;
  font-weight: 600;
  color: #6366f1;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  margin-bottom: 8px;
}

.tech-value {
  font-size: 0.92rem;
  color: var(--vp-c-text-2);
  line-height: 1.6;
}

.tech-value strong {
  color: var(--vp-c-text-1);
}

/* ---- Demo 步骤 ---- */
.demo-steps {
  display: flex;
  flex-direction: column;
  gap: 0;
  margin: 32px 0;
  position: relative;
}

.demo-steps::before {
  content: '';
  position: absolute;
  left: 23px;
  top: 24px;
  bottom: 24px;
  width: 2px;
  background: linear-gradient(180deg, #6366f1, #06b6d4);
  border-radius: 1px;
}

.demo-step {
  display: flex;
  align-items: flex-start;
  gap: 20px;
  padding: 20px 0;
  position: relative;
}

.step-number {
  flex-shrink: 0;
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 50%;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  color: #fff;
  font-size: 1.1rem;
  font-weight: 700;
  z-index: 1;
  box-shadow: 0 4px 16px rgba(99, 102, 241, 0.3);
}

.step-content {
  flex: 1;
  padding-top: 4px;
}

.step-content h3 {
  margin: 0 0 6px;
  font-size: 1.15rem;
  display: flex;
  align-items: center;
  gap: 12px;
}

.step-time {
  display: inline-block;
  padding: 2px 10px;
  border-radius: 12px;
  font-size: 0.75rem;
  font-weight: 600;
  background: rgba(6, 182, 212, 0.1);
  color: #06b6d4;
}

.step-content p {
  margin: 0;
  font-size: 0.95rem;
  color: var(--vp-c-text-2);
  line-height: 1.6;
}

.demo-cta {
  text-align: center;
  margin-top: 40px;
}

.demo-cta-sub {
  margin-top: 12px;
  font-size: 0.88rem;
  color: var(--vp-c-text-3);
}

/* ---- CTA 按钮 ---- */
.cta-button {
  display: inline-block;
  padding: 14px 40px;
  border-radius: 8px;
  font-size: 1.05rem;
  font-weight: 600;
  color: #fff;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  text-decoration: none;
  transition: all 0.3s ease;
  box-shadow: 0 4px 16px rgba(99, 102, 241, 0.3);
}

.cta-button:hover {
  transform: translateY(-2px);
  box-shadow: 0 8px 24px rgba(99, 102, 241, 0.45);
  color: #fff;
}

.cta-button-demo {
  padding: 16px 48px;
  font-size: 1.1rem;
  border-radius: 10px;
  box-shadow: 0 6px 24px rgba(99, 102, 241, 0.35);
}

.cta-button-secondary {
  display: inline-block;
  padding: 14px 32px;
  border-radius: 8px;
  font-size: 1rem;
  font-weight: 600;
  color: var(--vp-c-text-1);
  background: transparent;
  border: 1px solid var(--vp-c-divider);
  text-decoration: none;
  transition: all 0.3s ease;
  margin-left: 16px;
}

.cta-button-secondary:hover {
  border-color: #6366f1;
  color: #6366f1;
  transform: translateY(-2px);
  box-shadow: 0 4px 16px rgba(99, 102, 241, 0.1);
}

/* ---- 底部 CTA ---- */
.bottom-cta {
  text-align: center;
  padding: 64px 24px 48px;
  border-radius: 20px;
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.08), rgba(6, 182, 212, 0.08));
  border: 1px solid var(--vp-c-divider);
}

.bottom-cta h2 {
  display: inline-block;
  font-size: 1.75rem;
  margin-bottom: 12px;
}

.bottom-cta p {
  font-size: 1.1rem;
  color: var(--vp-c-text-2);
  margin-bottom: 28px;
}

.bottom-cta-actions {
  display: flex;
  justify-content: center;
  align-items: center;
  flex-wrap: wrap;
  gap: 16px;
  margin-bottom: 24px;
}

.bottom-trust {
  font-size: 0.85rem;
  color: var(--vp-c-text-3) !important;
  margin-bottom: 0 !important;
  letter-spacing: 0.03em;
}

/* ---- 响应式 ---- */
@media (max-width: 960px) {
  .comparison-grid {
    grid-template-columns: 1fr;
  }

  .tech-grid {
    grid-template-columns: repeat(2, 1fr);
  }
}

@media (max-width: 768px) {
  .trust-bar {
    flex-wrap: wrap;
    gap: 4px 8px;
    font-size: 0.82rem;
  }

  .audience-grid {
    grid-template-columns: 1fr;
  }

  .tech-grid {
    grid-template-columns: 1fr;
  }

  .bottom-cta h2 {
    font-size: 1.4rem;
  }

  .bottom-cta-actions {
    flex-direction: column;
  }

  .cta-button-secondary {
    margin-left: 0;
  }

  .comparison-result {
    gap: 6px;
  }
}
</style>
