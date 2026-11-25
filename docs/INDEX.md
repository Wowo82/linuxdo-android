# 文档索引

## 第一次看

- **[README](../README.md)** — 这个项目是什么、怎么构建、功能清单
- **[ARCHITECTURE](ARCHITECTURE.md)** — 代码怎么组织的、为什么这么分层

## 要改代码

- **[CONTRIBUTING](CONTRIBUTING.md)** — 编码规范、提交规范、调试方法
- **[API](API.md)** — Discourse 端点参考（含实际响应示例）
- **[ROADMAP](ROADMAP.md)** — 接下来做什么、按优先级排

## 要理解为什么

- **[REVERSE-ENGINEERING](REVERSE-ENGINEERING.md)** — iOS 版样本逆向结论，
  解释了为什么安卓侧这样取舍（不做 App Attest、不移植微应用等）

---

## 快速定位

| 我想… | 看这里 |
|---|---|
| 加一个新页面 | CONTRIBUTING「新增功能的流程」 |
| 知道某个 API 返回什么 | API.md 对应章节 |
| 搞清楚 cookie 怎么存的 | ARCHITECTURE「网络层 → Cookie」 |
| 知道为什么用 TextView 不用 WebView | ARCHITECTURE「HTML 渲染」 |
| 知道微应用为什么不移植 | REVERSE-ENGINEERING「微应用平台」+ ROADMAP P3 |
| 找下一个该做什么 | ROADMAP 的 P0 段 |
| 修一个构建错误 | CONTRIBUTING「环境」+「调试」 |
| 知道有哪些已知问题 | ROADMAP「技术债」段 |

---

## 文档维护规则

- 改了架构 → 同时改 ARCHITECTURE.md
- 加了新端点 → 同时改 API.md
- 做完一项 ROADMAP 任务 → 从 ROADMAP 删除，或挪到「已完成」
- 发现新的技术债 → 加到 ROADMAP 的表格里

**文档和代码一起提交**，不要单独一个 `docs: update` commit。
