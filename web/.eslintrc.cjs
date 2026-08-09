// ESLint 8 traditional config (M6 3 退回 — flat config 模式不支持 directory glob, 不能跟 pnpm lint "eslint ." 兼容).
// M3 写的 eslint.config.js + ignorePatterns 是错的 (flat config 模式 require ignores 顶层, 且 file glob 行为不同).
// 退回 .eslintrc.cjs 传统模式, package.json "lint": "eslint . --ext ..." 脚本能跑.

module.exports = {
  root: true,
  env: {
    browser: true,
    es2022: true,
    node: true,
  },
  extends: [
    'eslint:recommended',
    'plugin:vue/vue3-recommended',
    'plugin:@typescript-eslint/recommended',
  ],
  parser: 'vue-eslint-parser',
  parserOptions: {
    parser: '@typescript-eslint/parser',
    ecmaVersion: 'latest',
    sourceType: 'module',
    extraFileExtensions: ['.vue'],
  },
  plugins: ['@typescript-eslint'],
  rules: {
    'vue/multi-word-component-names': 'off', // shipyard 单页 (App.vue) / 单组件可单词
    'vue/max-attributes-per-line': 'off', // M3 启用但项目代码一直不遵守, autofix 风险大
    'vue/singleline-html-element-content-newline': 'off', // 同上
    'vue/multiline-html-element-content-newline': 'off', // 同上
    'vue/html-self-closing': 'off', // 同上
    'vue/attributes-html-format': 'off', // 同上
    'vue/first-attribute-linebreak': 'off', // 同上
    'vue/html-closing-bracket-newline': 'off', // 同上
    'vue/attributes-order': 'off', // M3-M5 既有顺序风格, 不强制
    'vue/html-quotes': 'off', // M3-M5 单引号 + 双引号混用
    'vue/html-indent': 'off', // M3-M5 缩进风格不统一
    '@typescript-eslint/no-unused-vars': ['error', { argsIgnorePattern: '^_' }],
    '@typescript-eslint/no-explicit-any': 'off', // V1 项目里 `as any` 是合理逃生口 (SecretInput 强转 etc.)
    'no-console': ['warn', { allow: ['warn', 'error'] }],
  },
  ignorePatterns: ['dist/', 'node_modules/', 'coverage/', '*.config.js', '*.config.cjs', '*.config.ts'],
};
