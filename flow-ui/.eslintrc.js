module.exports = {
  extends: require.resolve('@umijs/max/eslint'),
  // react 由 @umijs/max 间接提供，根目录没有直接依赖，eslint-plugin-react 无法自动探测版本
  settings: {
    react: { version: '18.3' },
  },
};
