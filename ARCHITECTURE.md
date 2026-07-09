# GlmCoder 项目框架总结

## 概述

GlmCoder 是基于 **Spring AI 1.0.0-M6 + GLM-4.7-Flash** 的企业级 Java 编码 Agent。提供代码理解、自动重构、编译验证的一站式智能编程辅助能力。

| 项目 | 值 |
|------|-----|
| 语言 | Java 17 |
| 框架 | Spring Boot 3.5.0 |
| AI 引擎 | Spring AI (OpenAI-compatible, 对接智谱 GLM-4.7-Flash) |
| 构建工具 | Maven 3.8.7 |
| 端口 | 8888 |
| 仓库 | github.com/liliangxing/GlmCoder |

---

## 架构总览

```
GlmCoder
├── agent/              # 核心 Agent 引擎
│   ├── CodingAgent         # 主编码 Agent, 接收用户任务并执行代码修改
│   └── ReflectionAgent     # 反射验证 Agent, 编译检查 + 自动修复循环
├── index/              # 代码索引模块
│   ├── CodeStructureIndex  # AST 解析器 (JavaParser), 构建类/方法/字段索引
│   ├── CallGraphBuilder    # 调用图构建器, 分析方法间调用关系
│   └── IndexService        # 索引服务, 递归扫描项目目录
├── tools/              # 工具层 (供 Agent 通过 Spring AI @Tool 调用)
│   ├── CodeUnderstandingTools   # 代码搜索/阅读/结构分析
│   ├── FileTools               # 文件列表/搜索/依赖分析
│   ├── ModificationTools        # 文件编辑/创建/Diff生成
│   ├── BuildTools              # Maven 编译检查/测试运行
│   └── DependencyAnalysisTools  # 模块依赖分析, 循环依赖检测
├── security/           # 安全沙箱
│   ├── PathValidator          # 路径白名单 (禁止访问项目外/受保护文件)
│   └── PatchApprovalService   # Patch 审批机制 (预览 → 确认 → 应用)
├── context/            # 上下文管理
│   └── ContextCompressor      # Token 压缩器, 自动裁剪超长上下文
├── project/            # 项目管理
│   └── ProjectManager         # 多项目打开/管理 (内存级)
├── attachment/         # 附件管理
│   └── AttachmentManager      # 文件上传/内容摘要
├── config/             # 配置层
│   ├── AppProperties           # 应用配置属性
│   ├── AgentConfig            # Agent System Prompt + ChatClient Bean
│   └── GlmApiConfig           # GLM API 自定义 OpenAiApi (路径纠正)
└── controller/         # Web 层
    ├── ProjectController      # /ui 页面路由
    └── AgentController        # /api REST API (索引/对话/附件/Patch)
```

---

## 核心流程

### Agent 工作流

```
用户输入任务
    │
    ▼
CodingAgent.execute()
    │
    ├──► IndexService.indexProject()          # 索引项目
    │       └── CodeStructureIndex.buildIndex()  # AST 解析
    │
    ├──► ContextCompressor.compress()         # 压缩上下文
    │
    ├──► ChatClient.prompt().user().call()    # 发送 LLM 请求
    │       │
    │       ├── 助手调用工具: searchCode/readFile/getClassStructure
    │       ├── 助手生成代码修改方案
    │       └── 助手调用 compileCheckJava 验证
    │
    └──► ReflectionAgent.reflectAndFix()      # 编译失败则自动修复
            └── 最多重试 3 次
```

### 安全沙箱

```
工具调用
    │
    ├──► PathValidator.isAllowed()
    │       ✗ 拒绝: 项目外路径 / 受保护文件 (pom.xml, .git) / 隐藏目录
    │       ✓ 放行: 项目内 .java 文件
    │
    └──► PatchApprovalService.submitPatch()
            └── 前端预览 Diff → 用户确认 → 应用修改
```

---

## REST API

| 端点 | 方法 | 说明 |
|------|------|------|
| `/ui` | GET | 主页面 (Thymeleaf) |
| `/ui/project/open` | POST | 打开项目, 返回 projectId |
| `/api/chat` | POST | 发送编码任务, 同步等待结果 |
| `/api/chat/stream` | GET | SSE 流式输出 |
| `/api/index` | POST | 索引项目代码 |
| `/api/structure` | GET | 获取代码结构总览 |
| `/api/upload` | POST | 上传附件 |
| `/api/patches` | GET | 获取待审批 Patch 列表 |
| `/api/patches/approve` | POST | 批准单个 Patch |

---

## 依赖库

| 库 | 版本 | 用途 |
|----|------|------|
| Spring Boot | 3.5.0 | 应用框架 |
| Spring AI OpenAI | 1.0.0-M6 | LLM 集成 + Tool 调用 |
| JavaParser | 3.26.3 | Java AST 解析 |
| Commons Math3 | 3.6.1 | (预留) 向量计算 |
| Guava | 33.4.7 | 通用工具 |
| Lombok | latest | 样板代码消除 |
| Thymeleaf | (Spring Boot 默认) | 服务端模板渲染 |

---

## 配置说明

```properties
# GLM API (智谱 OpenAI 兼容)
spring.ai.openai.api-key=${GLM_API_KEY}
spring.ai.openai.chat.options.model=glm-4.7-flash
spring.ai.openai.chat.options.temperature=0.1

# 自定义 OpenAiApi Bean (GlmApiConfig.java)
# 纠正路径: baseUrl + /chat/completions → open.bigmodel.cn/api/paas/v4/chat/completions
```

**关键修复**: Spring AI 默认拼接 `/v1/chat/completions`，智谱 v4 API 需要 `/v4/chat/completions`。通过 `GlmApiConfig` 注入自定义 `OpenAiApi` Bean 解决。

---

## 测试覆盖

5 个测试全部通过:

1. `contextLoads` — Spring 上下文加载
2. `shouldIndexCurrentProject` — 索引 20 个 Java 文件 (27 类 67 方法)
3. `shouldValidatePathsCorrectly` — 路径白名单/受保护文件
4. `shouldCompressContext` — 上下文压缩
5. `shouldEstimateTokens` — Token 估算

---

## 待实现功能 (v4 增强方案)

- [ ] 流式 SSE 输出 (API 已预留)
- [ ] 增量索引 (监听文件变更)
- [ ] 向量化代码搜索
- [ ] 代码 Diff 可视化 (前端)
- [ ] 用户 Patch 审批 UI
