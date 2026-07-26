import { defineConfig } from '@umijs/max';
import { BundleAnalyzerPlugin } from 'webpack-bundle-analyzer';

export default defineConfig({
  /**
   * Umi Max 路由级代码分割。
   * 配合页面/组件内的 dynamic import，可降低首屏 JS 体积。
   */
  codeSplitting: {
    jsStrategy: 'granularChunks',
  },
  /**
   * 自定义 Webpack 分包策略：
   * - 将 amis / @antv/x6 / @codemirror / amis-editor 等重型库单独拆包，
   *   避免全部打进 entry chunk 导致首屏加载过大。
   */
  chainWebpack(memo, { env }) {
    if (env === 'development') return memo;

    if (process.env.BUNDLE_ANALYZE === '1') {
      memo.plugin('bundle-analyzer').use(BundleAnalyzerPlugin, [
        {
          analyzerMode: 'static',
          openAnalyzer: false,
          reportFilename: '../bundle-analyze-report.html',
          generateStatsFile: true,
          statsFilename: '../stats.json',
        },
      ]);
    }

    // 精简 moment.js 多语言包，仅保留 zh-cn，减少 ~200 kB
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const { ContextReplacementPlugin } = require('webpack');
    memo.plugin('moment-locale').use(ContextReplacementPlugin, [
      /moment[/\\]locale$/,
      /zh-cn/,
    ]);

    memo.optimization.splitChunks({
      chunks: 'all',
      maxInitialRequests: 25,
      maxAsyncRequests: 25,
      minSize: 20000,
      cacheGroups: {
        amis: {
          name: 'amis',
          test: /[\\/]node_modules[\\/](amis|amis-ui|amis-editor|amis-editor-core)[\\/]/,
          priority: 20,
        },
        x6: {
          name: 'x6',
          test: /[\\/]node_modules[\\/]@antv[\\/]x6/,
          priority: 20,
        },
        codemirror: {
          name: 'codemirror',
          test: /[\\/]node_modules[\\/]@codemirror[\\/]/,
          priority: 20,
        },
        vendors: {
          name: 'vendors',
          test: /[\\/]node_modules[\\/]/,
          priority: 10,
        },
      },
    });

    return memo;
  },
});
