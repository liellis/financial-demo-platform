# Bank Engineering Ontology Studio V2.0
# 银行业务本体建模与研发智能执行平台需求规格说明书

**文档版本：** V0.9（研发基线草案）  
**目标产品版本：** V2.0  
**编制日期：** 2026-10-22  
**适用场景：** 银行 AI Coding、Agentic SDLC、SDD、存量系统理解与变更影响分析  
**技术路线：** CodeGraph + Business Ontology + LLM + Governed Actions + MCP  
**文档用途：** 产品设计、技术架构设计、开发任务拆解、测试验收

---

## 1. 产品概述

### 1.1 建设背景

银行拥有大量运行多年的业务应用，其核心业务知识分散在：

- Java、COBOL 等存量业务源码中。
- 数据库表、SQL、存储过程及配置中。
- 需求规格说明书、功能设计文档、业务规约中。
- API 接口规范、业务字典、业务流程文档中。
- 开发人员、架构师和业务专家的经验中。

当前 AI Coding 在存量银行系统中存在以下典型问题：

1. AI 能够理解代码语法，但难以完整理解银行业务语义。
2. 业务规则隐藏在大量方法、条件分支、SQL 和跨系统调用中。
3. 相关业务知识分散，难以快速找到可信且适用的上下文。
4. AI 修改代码时，容易遗漏关联业务规则、影响范围和测试场景。
5. 业务知识与代码版本之间缺乏持续同步机制。
6. AI Agent 缺少统一、可授权、可审计的业务知识与研发工具接口。

因此，需要建设一套面向银行研发工程的业务本体平台，将业务知识结构化、图谱化、版本化，并与存量代码实现建立双向关联。

### 1.2 产品定位

Bank Engineering Ontology Studio 是一个面向银行软件工程的**业务语义建模和智能研发执行平台**。

平台从银行已有源码及业务知识中提取业务语义，构建可追溯的业务本体，并通过标准 API 与 MCP 为研发智能体提供业务上下文和受控研发动作。

**核心定位：让 AI 理解银行业务，并能够依据可信业务知识开展受控研发工作。**

### 1.3 产品目标

G01：建立统一的银行业务本体元模型，覆盖业务对象、规则、流程、状态、事件和业务动作。

G02：通过代码分析和 LLM，实现业务知识的半自动抽取与持续更新。

G03：实现业务本体与 CodeGraph 代码图谱的双向关联和追溯。

G04：提供图形化本体建模、冲突处理、专家审核与版本治理能力。

G05：通过 Context MCP 向 CodeAgent、BA Agent、Architect Agent、Test Agent 提供可信业务上下文。

G06：支持经过授权的工程 Action，例如变更影响分析、生成测试、执行静态分析和验证代码。

G07：建立权限、审批、工具执行、证据和审计的完整治理机制。

### 1.4 基本设计原则

- **Evidence First：** 业务知识必须具备可追溯的证据。
- **Deterministic First：** 代码结构及调用关系优先由确定性分析获得。
- **Human-in-the-Loop：** 重要业务规则和关系必须经过专家审核。
- **Ontology as Context：** 本体服务于研发任务，而不是一次性加载整张图。
- **Action with Governance：** 工程操作必须由权限和策略控制。
- **Version-aware：** 本体与代码、文档和工具版本明确绑定。
- **Private Deployment：** 支持银行内网、私有化模型和受控计算环境。

---

## 2. 产品范围

### 2.1 V2.0 范围内能力

| 能力领域 | 具体内容 | 优先级 |
|---|---|---|
| 多源知识接入 | Git、代码、文档、DDL、API、测试 | P0 |
| 代码事实挖掘 | AST、调用链、SQL、依赖关系 | P0 |
| 业务本体抽取 | 对象、规则、流程、状态、事件 | P0 |
| 本体拓扑管理 | 浏览、检索、手工编辑、关系管理 | P0 |
| 业务代码映射 | 业务节点关联代码符号和数据对象 | P0 |
| 证据及版本治理 | 溯源、审核、发布、增量更新 | P0 |
| 本体质量验证 | 约束、重复、冲突、完整性检查 | P0 |
| 本体上下文检索 | 语义检索、图遍历、上下文组装 | P0 |
| Context MCP | 业务知识检索与代码定位工具 | P0 |
| Action 模型 | 定义业务动作及工程动作 | P0 |
| 工程 Action 执行 | 只读分析、隔离环境测试和静态检查 | P0 |
| 权限审批审计 | RBAC、资源授权、操作审批、审计 | P0 |
| FIBO 概念映射 | 金融标准概念的选择性对齐 | P1 |
| BIAN 领域映射 | 银行业务服务域映射 | P1 |
| 多 Agent Workflow | 跨角色研发流程编排 | P1 |
| 指标与评测 | 知识质量、上下文质量、任务收益 | P0 |
| 复杂 OWL 推理 | 完整语义推理及规则推导 | P2 |

优先级定义：

- P0：V2.0 基础可用版本必须具备。
- P1：V2.0 增强功能，可在基础版完成后迭代。
- P2：后续版本规划，不作为 V2.0 上线阻断项。

### 2.2 不在 V2.0 范围内

1. 银行生产交易数据全量导入及实时数字孪生。
2. 自动执行生产环境放款、转账、授信审批等业务动作。
3. 替代银行现有 BPM、核心交易、风控和工作流系统。
4. 全量导入 FIBO 并实现完整 OWL 推理能力。
5. 在没有人工审核的情况下自动发布关键业务规则。
6. 自动合并、自动部署高风险银行生产代码。
7. 建设与本体无关的通用 Agent 平台或模型训练平台。

---

## 3. 整体业务架构

### 3.1 六层逻辑架构

**L1：知识与代码资产层**

- Git Repository
- 需求与设计文档
- 数据库 Schema / SQL
- OpenAPI / 接口规范
- 配置 / 测试 / 状态枚举
- 可选运行日志与流程定义

**L2：代码与知识解析层**

- Source Adapter
- Document Parser
- AST / Static Analyzer
- CodeGraph Connector
- Knowledge Chunker
- Source Version Tracker

**L3：业务本体构建层**

- Concept Extraction
- Business Rule Mining
- Process Reconstruction
- Entity Alignment
- Relationship Discovery
- Ontology Validator
- Evidence Linking

**L4：本体知识与治理层**

- Ontology Registry
- Business Graph Store
- Code Binding Registry
- Review Workflow
- Version / Release Manager
- Policy / Audit Service

**L5：Agent 服务与执行层**

- Context Retrieval Engine
- MCP Server
- Business Action Registry
- Engineering Action Registry
- Tool Binding Engine
- Policy Enforcement Point
- Action Execution Manager

**L6：研发应用层**

- CodeAgent
- BA Agent
- Architect Agent
- Test Agent
- SDD Workflow
- AI Coding IDE
- Ontology Studio Web Portal

### 3.2 核心数据链路

源码 / 业务知识  
→ 确定性解析  
→ CodeGraph / 知识片段  
→ LLM 语义抽取  
→ Candidate Ontology  
→ 证据校验与专家审核  
→ Approved Ontology  
→ Graph Store / Search Index  
→ Context MCP / Engineering Actions  
→ AI Coding 任务执行  
→ 验证结果与知识反馈

### 3.3 核心服务划分

| 服务 | 职责 |
|---|---|
| Ingestion Service | 数据源管理、导入任务、版本快照 |
| Code Intelligence Service | 复用 CodeGraph、查询代码事实 |
| Ontology Mining Service | LLM 抽取、实体识别、关系发现 |
| Ontology Core Service | 本体类型、节点、关系、校验及图谱管理 |
| Governance Service | 审核、发布、版本、权限和证据管理 |
| Context Service | 混合检索、图扩展、重排序、上下文组装 |
| Action Service | 动作定义、工具绑定、审批和执行 |
| MCP Gateway | 统一向 Agent 暴露上下文和研发工具 |
| Evaluation Service | 本体质量、检索质量和研发收益评测 |

首期允许采用模块化单体架构，不要求每个逻辑服务都成为独立微服务。

---

## 4. 用户角色及权限

### 4.1 角色定义

| 角色 | 核心职责 |
|---|---|
| PlatformAdmin | 系统配置、模型、用户、连接器、系统策略 |
| OntologyAdmin | 本体 Schema、业务域、发布与版本管理 |
| DomainExpert | 业务规则审核、术语确认、关系审批 |
| Architect | 跨系统关系确认、技术映射与影响分析 |
| Developer | 业务知识查询、代码绑定、研发 Action |
| Auditor | 查看审核历史、执行记录、版本和合规证据 |
| AgentService | 通过授权身份和 MCP 进行查询及工具调用 |

### 4.2 权限原则

采用 RBAC + ABAC。

权限粒度至少覆盖：

- 组织 / 项目 / 业务域。
- Git Repository / Branch。
- 本体节点类型与敏感等级。
- 原始源码及业务文档。
- Action 类型与执行环境。
- 发布、审核和工具执行操作。

Agent 只能在用户授权或明确委托的服务身份权限范围内访问资源。

**任何 MCP 工具不得绕过后端权限检查。**

### 4.3 职责分离

- 业务知识抽取人员不得单独批准高风险知识发布。
- Action 定义人员不得自动获得生产执行权限。
- 高风险工程操作的提交者和审批者原则上应为不同身份。
- 平台管理员不能通过普通 API 绕过审计。

---

## 5. 核心业务场景

### UC01：从存量代码自动生成本体

参与角色：开发人员、业务专家、Ontology Admin。

执行过程：

1. 用户选择系统和 Git Repository。
2. 指定分支、提交版本及业务域。
3. 系统启动源码和文档解析。
4. 调用 CodeGraph 获取调用链和数据关系。
5. LLM 提取业务对象、规则和流程候选。
6. 将候选知识与已有本体进行对齐。
7. 生成新增、更新、冲突、待确认清单。
8. 专家审核。
9. 发布本体版本。

输出：

- 业务本体图。
- 业务规则清单。
- 业务流程图。
- 业务到代码映射。
- 证据及审核报告。

### UC02：基于拓扑图理解存量业务

开发人员搜索“消费贷款额度校验”。

系统展示：

- 关联业务对象。
- 相关业务规则。
- 所属流程和状态。
- 相关实现类及方法。
- 数据表与字段。
- 证据、代码版本和审核状态。

支持点击业务节点定位代码文件和对应代码范围。

### UC03：基于本体进行需求变更影响分析

需求示例：

“消费贷款额度检查增加特定产品的豁免条件。”

系统需要：

1. 识别涉及的业务对象、规则与 Action。
2. 检索相关业务本体子图。
3. 查找相关代码、接口和数据对象。
4. 分析调用链及可能受影响的业务流程。
5. 输出候选修改点、测试范围及不确定事项。
6. 提供证据和分析依据。

不允许将低置信度推测直接标记为确定受影响范围。

### UC04：通过 Agent 执行受控研发 Action

Agent 获取本体上下文后，可以申请：

- 运行影响分析。
- 生成相关测试用例。
- 执行静态检查。
- 在隔离工作区运行单元测试。
- 在隔离工作区生成代码修改草案。

系统完成授权、参数校验、环境校验、风险判断及必要审批后，才允许执行。

### UC05：代码变更驱动本体更新

当指定 Repository 产生新的 Commit 或 PR：

1. 检测源码差异。
2. 识别关联本体节点。
3. 触发局部重新抽取。
4. 标记可能失效的规则或代码映射。
5. 形成待审核的 Ontology ChangeSet。
6. 审核后发布新版本。

### UC06：为 SDD 自动组装业务上下文

Agent 收到需求后，自动获得：

- 业务术语和概念定义。
- 适用业务规则。
- 状态及流程约束。
- 相关代码实现。
- 关联测试与验证要求。
- 不确定事项及待确认问题。

上下文包应可被用于需求规格、技术设计及测试设计。

---

# 6. 详细功能需求

## 6.1 模块 M01：多源知识接入

### FR-ING-001 Git Repository 接入｜P0

支持配置：

- Git 仓库地址和访问凭据引用。
- 项目、业务域、系统名称。
- 默认分支及指定 Commit。
- 代码语言及扫描目录。
- 包含/排除路径。
- 增量同步策略。

验收：

- 能导入指定版本的 Java Repository。
- 能保存 Repository ID、Branch、Commit SHA。
- 重复导入同一版本不会创建重复事实。
- 导入失败可定位错误并重新执行。

### FR-ING-002 业务文档解析｜P0

支持 Markdown、TXT、DOCX、PDF 和结构化业务规约。

能力要求：

- 提取正文、章节、标题及表格。
- 保留章节位置和文件版本。
- 按业务章节进行分块。
- 记录原始文件及证据定位。
- 扫描文档中的敏感内容。

扫描件 OCR 可作为后续能力。

### FR-ING-003 数据库元数据接入｜P0

支持 DDL、SQL 文件和数据库 Schema 元数据。

至少提取：

- Table、Column、主外键。
- 数据类型和注释。
- SQL 读写目标。
- 枚举与状态字段候选。

初期不要求读取生产业务数据。

### FR-ING-004 API 规范接入｜P1

支持 OpenAPI 文件和接口元数据，建立 API、参数、请求对象及响应对象关系。

### FR-ING-005 增量采集｜P0

以 Source Snapshot 为基础支持 Git Diff、文档版本差异、失败重试与断点恢复。

不得将旧版本知识静默覆盖为新版本知识。

---

## 6.2 模块 M02：CodeGraph 集成

### FR-CG-001 CodeGraph Connector｜P0

支持通过配置的 API、MCP 或适配器获取现有 CodeGraph 信息。

所需基础事实：

- Repository / File。
- Class / Interface。
- Method / Function。
- Method Call。
- Import / Dependency。
- API Endpoint。
- SQL / Table / Column。
- Test Case。
- Source Location。

验收：

- 能从业务规则定位对应代码符号。
- 能从代码符号反向查询关联业务规则。
- 结果保留代码版本与证据位置。

### FR-CG-002 代码事实标准化｜P0

定义统一 Code Symbol ID：

`repository + commit + file + symbol_signature`

同一逻辑符号跨版本的关联应由独立 Stable Symbol Key 表示。

### FR-CG-003 调用链与数据依赖查询｜P0

支持：

- 调用方与被调用方。
- 指定深度的调用链。
- SQL 读写关系。
- 跨模块和跨仓库引用。
- 关联测试查询。

跨仓库无法确定的动态依赖应明确标记为未知，而不是自动生成确定性关系。

### FR-CG-004 事实与语义分离｜P0

必须区分：

- 由解析器确认的代码事实。
- LLM 推测的业务语义。
- 专家确认的业务关联。

不同来源不得在图谱中使用完全相同的可信标记。

---

## 6.3 模块 M03：业务语义抽取

### FR-EXT-001 业务对象抽取｜P0

从源码、字段、类名、DTO、接口和文档中识别业务概念。

输出字段：

- 业务对象名称。
- 英文名称及别名。
- 业务定义。
- 属性候选。
- 归属领域。
- 对象间关系。
- 来源证据。
- 模型置信度。

### FR-EXT-002 业务规则抽取｜P0

识别：

- IF / ELSE 条件。
- 校验和异常分支。
- 金额、状态、时间及权限约束。
- 计算公式。
- 产品差异规则。
- 规则适用和例外条件。

每条规则至少包含：

`condition / outcome / scope / evidence / review_status`

缺少证据或适用范围不清的规则不得自动批准。

### FR-EXT-003 业务流程抽取｜P0

从调用链、状态变化、流程定义、事件和业务文档中重建流程候选。

流程必须支持：

- 活动节点。
- 执行顺序。
- 分支条件。
- 触发事件。
- 业务状态变化。
- 关联代码实现。
- 未知或缺失环节标注。

单纯的方法调用顺序不得直接当成完整业务流程。

### FR-EXT-004 状态机抽取｜P0

从状态枚举、状态赋值、条件分支和业务文档中提取：

- State。
- Transition。
- Transition Condition。
- Event。
- Action。

系统应检测无来源状态及不完整状态流转。

### FR-EXT-005 LLM 结构化输出｜P0

所有抽取任务使用版本化 JSON Schema 约束输出。

要求：

- 不符合 Schema 的结果不能入库。
- 支持解析失败重试。
- 记录模型、Prompt 模板版本和推理任务 ID。
- 记录输入证据集合。
- 输出模型无法确定的字段及原因。

### FR-EXT-006 候选实体对齐｜P0

支持基于术语、定义、属性、关联对象及业务域计算候选匹配关系。

处理类型：

- 同一概念。
- 上下位关系。
- 相似但不等价。
- 完全不同概念。
- 存在冲突。

概念合并必须可审核、可撤销。

### FR-EXT-007 FIBO / BIAN 对齐｜P1

支持导入选定标准概念，记录标准版本、URI、映射类型和审核状态。

标准概念不能自动覆盖银行已有业务定义。

---

## 6.4 模块 M04：本体建模工作台

### FR-ONT-001 本体 Schema 管理｜P0

支持定义：

- Object Type。
- Relation Type。
- Rule Type。
- Process Type。
- Event Type。
- Action Type。
- 属性定义。
- 关系方向与基数。
- 必填约束。
- 枚举约束。

Schema 修改必须记录版本及兼容性影响。

### FR-ONT-002 本体节点管理｜P0

支持新增、查看、编辑、废弃节点。

支持字段校验、重复检查、版本查看和变更比较。

已发布版本不得直接原地修改。

### FR-ONT-003 关系管理｜P0

支持创建、修改、审核、删除或废弃语义关系。

每条关系需要包含：

- 起点和终点。
- 关系类型。
- 业务适用范围。
- 证据及版本。
- 确认状态。

### FR-ONT-004 拓扑可视化｜P0

必须支持：

- 按业务域筛选。
- 业务节点搜索。
- 节点及关系展开。
- 指定层级的子图展示。
- 拖拽布局。
- 关系详情。
- 代码映射入口。
- 不同关系类型视觉区分。
- 按状态高亮候选、冲突和失效知识。

拓扑视图布局与本体语义数据分离保存。

### FR-ONT-005 多视图支持｜P1

支持：

1. Business View：业务对象与关系。
2. Rule View：规则与适用范围。
3. Process View：业务流程与状态。
4. Implementation View：业务到代码映射。
5. Impact View：变更影响分析。

### FR-ONT-006 本体导入导出｜P1

支持 JSON、JSON-LD，以及按需导出 RDF/OWL。

必须定义格式版本和字段兼容策略。

---

## 6.5 模块 M05：本体质量与治理

### FR-GOV-001 本体审核流程｜P0

建议状态机：

`CANDIDATE → IN_REVIEW → APPROVED → PUBLISHED`

异常状态：

`REJECTED / CONFLICTED / STALE / DEPRECATED`

要求：

- 支持批量审核与单条审核。
- 记录审核人、时间、结论和意见。
- 冲突和证据缺失可阻断发布。
- 拒绝的知识不能进入正式发布图。
- 支持重新提交和历史追踪。

### FR-GOV-002 证据管理｜P0

每个已发布的重要业务断言必须关联有效证据。

证据类型：

- SOURCE_CODE。
- BUSINESS_DOCUMENT。
- DATABASE_SCHEMA。
- API_SPEC。
- TEST_CASE。
- EXPERT_CONFIRMATION。

支持证据定位、查看、版本校验及失效标记。

### FR-GOV-003 本体质量校验｜P0

至少包含：

- Schema 合法性。
- 必填属性。
- 关系端点类型约束。
- 引用完整性。
- 重复节点。
- 冲突关系。
- 证据存在性。
- Source Version 有效性。

P1 可进一步支持 SHACL 规则和复杂语义一致性检查。

### FR-GOV-004 本体版本管理｜P0

定义：

- Ontology Workspace。
- Candidate ChangeSet。
- Published Release。
- Source Snapshot。
- Schema Version。

每个发布版本必须固定对应的知识断言版本及依赖来源。

支持版本比较、历史检索和回滚发布指针。

### FR-GOV-005 增量更新与失效传播｜P0

代码发生变更时：

1. 定位关联的业务知识。
2. 判断映射证据是否变化。
3. 标记可能失效的知识。
4. 重新抽取候选。
5. 生成 ChangeSet。
6. 审核后发布。

关键规则的唯一证据失效时，不能继续作为无警告的可信上下文返回。

### FR-GOV-006 知识反馈机制｜P1

Agent 或研发人员可反馈：

- 检索结果不正确。
- 业务规则已废弃。
- 代码映射错误。
- 缺少关联规则。
- 本体节点重复。

反馈进入待处理队列，不直接修改正式本体。

---

## 6.6 模块 M06：Action 建模与受控执行

这是 V2.0 与单纯知识图谱工具最主要的差异之一。

### 6.6.1 Action 分层

**Business Action**

表示银行业务动作的语义定义。

例如：

- ValidateCreditLimit
- SubmitLoanApplication
- ApproveLoanApplication
- CalculateRepaymentPlan

Business Action 用于描述业务行为及其约束，不代表平台拥有执行该银行业务交易的权限。

**Engineering Action**

表示研发智能体可调用的工程能力。

例如：

- AnalyzeBusinessImpact
- LocateImplementation
- GenerateTestCases
- RunStaticAnalysis
- ExecuteUnitTests
- ProposeCodeChange

**Tool Binding**

用于把 Engineering Action 映射到具体工具、API、容器任务或 MCP 工具。

### FR-ACT-001 Action Registry｜P0

支持注册 Action 定义。

主要字段：

- Action ID。
- Action Name / Description。
- Action Category。
- Input / Output Schema。
- Preconditions。
- Risk Level。
- Permissions。
- Approval Policy。
- Tool Binding。
- Timeout / Retry。
- Execution Environment。
- Version / Status。

### FR-ACT-002 Business Action 建模｜P0

业务动作支持绑定：

- 业务对象。
- 前置业务条件。
- 触发事件。
- 状态转移。
- 业务规则。
- 实现方法或 API。

默认只作为知识对象参与检索与分析。

### FR-ACT-003 Engineering Tool Binding｜P0

支持绑定以下工具类型：

- INTERNAL_API。
- MCP_TOOL。
- CODEGRAPH_QUERY。
- REGISTERED_SANDBOX_JOB。

V2.0 不允许 LLM 自由指定任意命令或未经注册的工具地址。

### FR-ACT-004 Action 参数校验｜P0

执行前必须验证：

- 工具版本。
- Input Schema。
- 输入数据来源与授权。
- Repository / Branch 权限。
- 工作区状态。
- 参数白名单。
- 超时与资源限制。

### FR-ACT-005 Action 风险分级｜P0

建议分为：

| 级别 | 描述 | 默认策略 |
|---|---|---|
| R0 | 只读知识和代码查询 | 授权后允许 |
| R1 | 只读计算、影响分析、静态扫描 | 授权后允许 |
| R2 | 隔离环境生成文件、测试、修改草案 | 审批或明确授权工作流 |
| R3 | 共享仓库写入、PR 创建等较高风险动作 | 显式审批与二次检查 |
| R4 | 生产交易、生产部署、敏感数据变更 | V2.0 禁止 |

### FR-ACT-006 Action 执行引擎｜P0

执行状态：

`CREATED → VALIDATED → PENDING_APPROVAL → QUEUED → RUNNING → SUCCEEDED / FAILED / CANCELLED`

不需要审批的动作可跳过 PENDING_APPROVAL。

执行引擎需要支持：

- 任务队列。
- 并发控制。
- 超时。
- 取消。
- 幂等键。
- 失败重试策略。
- 执行日志。
- 产出物管理。

超时或网络失败时不能未经核验就重新执行具有副作用的动作。

### FR-ACT-007 Dry-run｜P0

对可变更文件或共享状态的动作，先执行预检查或差异预览。

至少展示：

- 目标 Repository。
- 目标 Branch。
- 预计影响文件。
- 修改摘要。
- 需要的权限。
- 风险及可回退方式。

### FR-ACT-008 Approval Gate｜P0

高风险工程动作必须在执行前完成审批。

审批结果应绑定：

- Action 定义及版本。
- 输入参数摘要。
- 目标资源。
- 操作风险。
- 执行环境。
- 有效期限。

审批后如关键输入、代码基线或目标环境发生变化，必须重新校验，必要时重新审批。

### FR-ACT-009 Action Audit｜P0

记录：

- 谁或哪个 Agent 发起。
- 代理的用户身份。
- 调用了哪个工具。
- 使用什么参数及授权范围。
- 审批依据。
- 执行时间与状态。
- 输出产物及错误。
- 关联任务与代码版本。

敏感参数应按策略脱敏，不在普通日志中明文保存密钥。

### FR-ACT-010 Tool Policy｜P0

至少具备以下默认限制：

- 禁止访问生产交易 API。
- 禁止在未经允许的仓库和分支写入。
- 禁止执行任意 Shell 命令。
- 禁止工具自动扩大权限。
- 禁止未经批准的外部数据传输。
- 禁止 Agent 修改自身的授权策略。

---

## 6.7 模块 M07：Context Engine 与 MCP

### FR-CTX-001 业务语义检索｜P0

支持输入自然语言查询、业务术语、规则 ID 或代码符号。

检索渠道：

1. 精确 ID 和业务术语匹配。
2. 全文检索。
3. 向量语义检索。
4. 本体关系遍历。
5. CodeGraph 依赖查询。

支持混合召回、去重和重排序。

### FR-CTX-002 任务感知上下文组装｜P0

根据不同任务类型选择知识：

| 任务类型 | 核心上下文 |
|---|---|
| 需求分析 | 业务对象、规则、流程、术语 |
| 技术设计 | 规则、服务关系、接口、实现 |
| 代码修改 | 规则、关联代码、影响范围 |
| 测试生成 | 规则条件、异常、状态、测试 |
| 影响分析 | 业务关系、依赖路径、证据 |

### FR-CTX-003 Context Package｜P0

上下文输出必须包含：

- Query / Task ID。
- Ontology Release ID。
- Source Snapshot / Commit。
- Business Concepts。
- Business Rules。
- Processes / States。
- Related Code Symbols。
- Evidence References。
- Uncertainties。
- Authorization Scope。
- Token Usage。

未批准的知识不得冒充正式业务规则。对于存在冲突或证据失效的关键规则，返回明确警告并要求人工确认。

### FR-CTX-004 Token Budget｜P0

输入：

- 任务类型。
- 最大 Token Budget。
- 返回条数。
- 业务域过滤条件。
- 上下文深度。
- 可信状态过滤条件。

优化优先级：

1. 已批准的关键业务规则。
2. 直接相关的实现代码。
3. 影响范围及测试。
4. 支持性证据摘要。
5. 低优先级扩展知识。

不得为了节省 Token 而截断关键条件、比较符、SQL 过滤条件等信息，造成语义错误。

### FR-CTX-005 MCP 工具｜P0

首期提供以下工具：

| MCP Tool | 功能 |
|---|---|
| search_business_concepts | 查询业务对象与术语 |
| get_business_rules | 查询业务规则和约束 |
| get_process_flow | 查询业务流程及状态 |
| trace_business_to_code | 业务到源码的关联定位 |
| analyze_business_impact | 业务变更影响分析 |
| assemble_business_context | 按任务组装上下文 |
| get_evidence | 获取有权限访问的证据 |
| list_engineering_actions | 查询已授权研发动作 |
| execute_engineering_action | 经过策略校验执行研发动作 |
| get_action_status | 查询动作状态和产出 |

### FR-CTX-006 MCP 安全限制｜P0

- 工具列表可基于身份和权限过滤。
- 服务端对每次调用独立鉴权。
- 限制工具允许访问的数据源。
- 结构化返回包含错误码与追踪 ID。
- 对超量、越权及高风险调用进行拦截。
- 从源码和文档检索的内容均视为不可信数据，不能作为提升 Agent 权限的指令。

### FR-CTX-007 检索结果反馈｜P1

支持记录：

- 相关或不相关。
- 是否覆盖正确业务规则。
- 是否存在误召回。
- 是否缺少关键代码。
- 是否帮助任务通过验证。

反馈用于检索策略和本体质量优化。

---

## 6.8 模块 M08：质量评测与可观测性

### FR-EVAL-001 本体抽取评测｜P0

评测：

- 概念抽取 Precision / Recall / F1。
- 业务规则 Precision / Recall / F1。
- 关系正确率。
- 流程节点与状态转移覆盖情况。
- 业务到代码映射准确率。

### FR-EVAL-002 Context 评测｜P0

评测：

- Relevant Context Recall@K。
- Relevant Context Precision@K。
- 关键规则覆盖率。
- Dependency Retrieval Coverage。
- 无效上下文比例。
- Token 使用量。
- 证据有效性。

### FR-EVAL-003 Action 评测｜P0

评测：

- Action 执行成功率。
- 风险策略拦截率。
- 未授权调用阻断率。
- 审批覆盖率。
- 执行耗时。
- 产出验证通过率。

### FR-EVAL-004 研发效果 A/B 评测｜P0

比较两组：

Baseline：CodeAgent + CodeGraph。

Experimental：CodeAgent + CodeGraph + Approved Ontology + Context MCP。

比较：

- 任务成功率。
- 测试及质量门禁通过率。
- 变更遗漏率。
- 返工率。
- 任务完成时间。
- 上下文 Token 消耗。

---

# 7. 本体元模型设计要求

## 7.1 节点类型

| 节点类型 | 用途 | 示例 |
|---|---|---|
| BusinessDomain | 银行业务领域 | Credit |
| BusinessConcept | 业务概念和术语 | 授信额度 |
| BusinessObject | 业务对象类型 | LoanApplication |
| BusinessAttribute | 业务属性 | availableLimit |
| BusinessRule | 业务条件和约束 | 额度校验 |
| BusinessProcess | 业务流程 | 贷款申请处理 |
| ProcessStep | 流程活动 | 风险检查 |
| BusinessState | 业务状态 | APPROVED |
| BusinessEvent | 业务事件 | LoanApproved |
| BusinessAction | 业务操作语义 | ValidateLimit |
| CodeSymbol | 代码实现符号 | checkLimit() |
| DataAsset | 数据表及字段 | CREDIT_LIMIT |
| Evidence | 原始证据 | CodeRange / DocSection |
| EngineeringAction | 研发工程操作 | RunUnitTests |
| ToolBinding | 工具执行绑定 | MCP / API / Job |
| SourceSnapshot | 来源版本快照 | Git Commit |
| OntologyRelease | 已发布知识版本 | Release 1.2 |
| AgentTask | 研发任务 | TASK-001 |

V2.0 中的 BusinessObject 默认指业务对象类型或模式，不表示真实客户及其交易实例。

## 7.2 关系类型

首期至少支持：

| 关系 | 起点 → 终点 |
|---|---|
| BELONGS_TO | BusinessObject → BusinessDomain |
| HAS_ATTRIBUTE | BusinessObject → BusinessAttribute |
| RELATES_TO | BusinessObject → BusinessObject |
| GOVERNED_BY | BusinessObject → BusinessRule |
| CONTAINS_STEP | BusinessProcess → ProcessStep |
| NEXT_STEP | ProcessStep → ProcessStep |
| HAS_STATE | BusinessObject → BusinessState |
| TRANSITIONS_TO | BusinessState → BusinessState |
| TRIGGERED_BY | BusinessAction → BusinessEvent |
| USES_RULE | BusinessAction / ProcessStep → BusinessRule |
| IMPLEMENTED_BY | BusinessRule / BusinessAction → CodeSymbol |
| READS / WRITES | CodeSymbol → DataAsset |
| CALLS | CodeSymbol → CodeSymbol |
| VERIFIED_BY | BusinessRule → CodeSymbol(Test) |
| SUPPORTED_BY | 业务断言 → Evidence |
| MAPS_TO_STANDARD | BusinessConcept → FIBO Reference |
| BINDS_TO | EngineeringAction → ToolBinding |
| DERIVED_FROM | 知识断言 → SourceSnapshot |
| AFFECTS | 变更分析结果 → 业务/代码对象 |

关系本身需要支持证据、版本和审核状态。

对于 `SUPPORTED_BY` 等断言级证据关系，可使用独立 Assertion Record 或关系实体化方式，确保每条业务断言都有独立的证据及审核记录。

## 7.3 通用节点字段

每个知识节点至少定义：

- `id`：唯一标识。
- `stable_key`：跨版本稳定标识。
- `node_type`：节点类型。
- `name`：名称。
- `display_name`：展示名称。
- `description`：语义描述。
- `domain_id`：业务域。
- `project_id`：项目。
- `tenant_id`：租户。
- `status`：知识状态。
- `confidence`：抽取模型置信度。
- `source_refs`：来源引用。
- `schema_version`：Schema 版本。
- `created_at / updated_at`。
- `created_by / reviewed_by`。
- `reviewed_at`。
- `valid_from / valid_to`。
- `access_policy_id`。

`confidence` 仅表示候选模型评估，不可替代专家审核。

## 7.4 本体关系约束

必须具备：

- 类型合法性。
- 允许的起点/终点。
- 关系方向约束。
- 必要时定义关系基数。
- 跨业务域引用约束。
- 证据约束。
- 循环关系检测策略。
- 状态转换合法性规则。

例如：

`BusinessRule IMPLEMENTED_BY CodeSymbol`

不能将任意业务流程节点未经规则定义直接解释为代码实现映射。

## 7.5 本体 Schema 版本

Schema 发布要求：

- 支持 Draft / Published。
- 发布前进行兼容性检查。
- 删除类型前检查引用。
- 非兼容变更必须执行迁移。
- 已有发布版本保持可复现。
- 查询可指定 Schema 和 Ontology Release。

---

# 8. Action 数据模型和执行契约

## 8.1 Action Definition

建议数据结构：

- `action_id`
- `action_type`
- `name`
- `description`
- `input_schema`
- `output_schema`
- `required_permissions`
- `preconditions`
- `risk_level`
- `approval_policy`
- `tool_binding_id`
- `environment_policy`
- `timeout_seconds`
- `retry_policy`
- `dry_run_supported`
- `version`
- `status`

## 8.2 Tool Binding

Tool Binding 应与业务语义独立版本化。

字段包括：

- `binding_id`
- `action_id`
- `tool_type`
- `tool_identifier`
- `endpoint_reference`
- `credential_reference`
- `allowed_operations`
- `execution_environment`
- `resource_limits`
- `input_mapping`
- `output_mapping`
- `policy_id`

密钥只允许保存凭据引用，不允许在本体图中存储明文 Token。

## 8.3 Action Execution Record

每次操作保存：

- Execution ID。
- Task ID。
- Action Definition Version。
- Tool Binding Version。
- Caller Identity。
- Delegated User Identity。
- Target Repository / Commit。
- Policy Evaluation Result。
- Approval ID。
- Start / End Time。
- Final Status。
- Output Artifact References。
- Audit Trace ID。

## 8.4 执行安全与隔离

默认采用：

- 只读工具与写入工具隔离。
- 代码修改在独立工作区或容器中执行。
- 文件系统和网络访问白名单。
- 容器 CPU、内存及时间限制。
- 代码执行产物持久化。
- 失败时清理临时执行环境。
- 变更提交前提供 Diff 和验证结果。
- 不允许自动提交到受保护生产分支。

对不可逆动作，重试、回滚和幂等策略需要单独定义，不得假定所有工具操作都可以回滚。

---

# 9. API 接口需求

REST API 作为平台业务接口，MCP Server 作为 Agent 能力入口。

## 9.1 数据接入 API

- `POST /api/v2/sources`：注册数据源。
- `POST /api/v2/ingestion/jobs`：创建导入任务。
- `GET /api/v2/ingestion/jobs/{id}`：查询任务状态。
- `POST /api/v2/mining/jobs`：创建本体抽取任务。
- `GET /api/v2/mining/jobs/{id}`：获取候选抽取结果。

## 9.2 本体管理 API

- `GET /api/v2/ontology/schema`：查询 Schema。
- `POST /api/v2/ontology/nodes`：创建候选节点。
- `GET /api/v2/ontology/nodes/{id}`：获取节点详情。
- `POST /api/v2/ontology/relations`：创建候选关系。
- `GET /api/v2/ontology/subgraph`：查询子图。
- `POST /api/v2/ontology/validate`：执行质量验证。
- `POST /api/v2/ontology/changesets`：提交知识变更集。

## 9.3 审核和版本 API

- `POST /api/v2/reviews`：创建审核任务。
- `POST /api/v2/reviews/{id}/decision`：审核通过或拒绝。
- `GET /api/v2/releases`：获取发布版本列表。
- `POST /api/v2/releases`：发布本体版本。
- `GET /api/v2/releases/{id}/diff`：查询发布差异。

## 9.4 Context API

- `POST /api/v2/context/search`：混合知识检索。
- `POST /api/v2/context/assemble`：组装任务上下文。
- `POST /api/v2/impact/analyze`：执行变更影响分析。
- `GET /api/v2/evidence/{id}`：获取授权证据。

## 9.5 Action API

- `POST /api/v2/actions`：注册 Action。
- `GET /api/v2/actions`：查询可见 Action。
- `POST /api/v2/actions/{id}/dry-run`：预执行。
- `POST /api/v2/action-executions`：申请执行。
- `POST /api/v2/action-executions/{id}/approval`：处理审批。
- `GET /api/v2/action-executions/{id}`：查询执行状态。
- `POST /api/v2/action-executions/{id}/cancel`：请求取消。

## 9.6 通用接口约定

- REST API 以 OpenAPI 描述。
- 输入参数必须进行 Schema 校验。
- 写操作支持幂等键。
- 分页接口支持游标分页。
- 长时任务采用异步 Job ID 查询模式。
- 返回 Trace ID。
- 错误码至少区分身份失败、资源越权、版本冲突、证据失效、校验失败、审批拒绝、执行失败及限流。
- 变更操作支持乐观锁或版本前置条件。
- 不允许通过用户输入拼接未经校验的原生 Cypher。

所有接口必须在服务端进行租户、项目和资源授权。

---

# 10. 页面与交互需求

## 10.1 首页 Dashboard

展示：

- 业务域数量。
- 业务对象数量。
- 已审核规则数量。
- 业务到代码映射数量。
- 待审核知识。
- 冲突和失效知识。
- 本体更新任务状态。
- Action 执行和异常情况。

## 10.2 数据源管理页面

支持 Git 仓库、文档、数据库 Schema 及 API 规范的连接、同步和版本管理。

## 10.3 本体拓扑工作台

建议页面采用三栏结构：

**左侧：** 业务域、对象类型、知识分类树和搜索。

**中间：** 本体拓扑图，支持展开、筛选、关系高亮和多视图切换。

**右侧：** 节点属性、业务定义、证据、代码实现、审核状态及编辑操作。

## 10.4 本体抽取任务中心

展示：

- 抽取任务进度。
- 处理的仓库和文件。
- 新增候选节点。
- 新增候选关系。
- 重复或冲突对象。
- 抽取失败原因。
- Token 与模型调用统计。

## 10.5 专家审核页面

支持：

- 候选知识列表。
- 代码与文档证据并排查看。
- 相似本体对比。
- 批量通过/拒绝。
- 审核意见。
- 审核历史追溯。

## 10.6 Action Studio

支持：

- Action 模型定义。
- Input/Output Schema 编辑。
- Tool Binding 配置。
- 风险等级和权限配置。
- Dry-run。
- 执行及审批记录。

## 10.7 Context 调试页面

输入一条研发需求，展示：

- 命中的业务概念。
- 检索的本体子图。
- 代码关联路径。
- 最终 Context Package。
- 各内容的证据来源。
- Token 使用情况。
- 检索质量反馈。

这个页面应作为 Context Engineering 联调和效果优化的主要工具。

## 10.8 评测与治理页面

展示知识质量、检索质量、Action 成功率、版本变化和异常审计。

---

# 11. 非功能需求

以下指标为建议的 POC 验收目标，需根据银行环境、模型能力与数据规模压测调整，并非既有产品实测结果。

## 11.1 性能

| 指标 | 建议初始目标 |
|---|---|
| 本体节点详情查询 | P95 ≤ 500ms |
| 常规 1–3 跳子图查询 | P95 ≤ 2s |
| 不含 LLM 的混合检索 | P95 ≤ 3s |
| MCP 查询附加开销 | P95 ≤ 500ms |
| 长时抽取任务 | 异步执行，可查询进度 |
| 工程 Action | 支持超时、取消和配额限制 |

建议初期在约 5 万节点、20 万关系的基准数据上验证查询性能，后续逐步提升规模。

## 11.2 可靠性

- 采集和抽取任务支持幂等与重试。
- 已发布本体版本保持不可变。
- 数据库、任务队列和证据存储具备备份恢复能力。
- 发布流程出现故障不能产生部分可见的错误版本。
- Action 失败不能导致未审批的共享仓库变更。

## 11.3 安全

- 支持银行内网和私有化部署。
- 支持企业 SSO / OIDC 或同等身份接入方式。
- 支持 RBAC + ABAC。
- 数据传输加密及敏感数据静态加密。
- 秘钥由受控凭据系统管理。
- 源码和业务文档不得默认出境。
- 检索必须先完成数据范围授权，再返回内容。
- 审计日志需支持防篡改及保留策略。
- 对源码注释、文档和外部知识执行 Prompt Injection 防护。

## 11.4 模型要求

- 支持私有化部署的大模型接口。
- 模型提供方可配置。
- 支持不同抽取任务选择不同模型。
- 支持结构化 JSON 输出及校验。
- 支持 Prompt 模板版本管理。
- 支持模型调用耗时、Token 和失败原因统计。
- 模型切换后能够复现并比较抽取评测结果。

## 11.5 扩展性

- 接入层支持新增编程语言解析器。
- CodeGraph Connector 可替换。
- 图数据库访问封装为 Repository 接口。
- MCP 工具注册与 Action Registry 解耦。
- 本体 Schema 支持新增业务域和自定义类型。
- 避免将特定模型、特定银行系统的业务逻辑硬编码到平台核心。

---

# 12. 数据存储与部署建议

## 12.1 数据存储职责

| 存储类型 | 推荐技术 | 内容 |
|---|---|---|
| 图数据库 | Neo4j | 本体节点、语义关系、代码映射 |
| 关系数据库 | PostgreSQL | 用户、任务、审核、发布、Action、审计索引 |
| 文件/对象存储 | S3 兼容对象存储或受控文件服务 | 文档、证据快照、执行产物 |
| 任务队列 | Redis Streams / RabbitMQ | 抽取任务及 Action 执行任务 |
| 搜索索引 | Neo4j 全文/向量索引，或独立检索服务 | 术语和语义检索 |
| 凭据管理 | 企业 Secret Manager | Git、工具和模型凭据 |

Redis 等缓存及队列组件需要根据银行基础设施标准确定，不应将 Redis 用作业务知识的唯一持久化存储。

## 12.2 建议技术栈

前端：

- React + TypeScript。
- Ant Design。
- Cytoscape.js。
- 状态管理及图谱视图配置。

后端：

- Python + FastAPI。
- Pydantic。
- Neo4j Driver。
- PostgreSQL ORM。
- 异步任务执行框架。
- MCP SDK。

分析及 AI：

- CodeGraph。
- Java AST Parser。
- 私有化 LLM。
- Embedding Model。
- Reranker。
- JSON Schema Validator。

首期建议采用模块化单体 + 独立 Worker。

待多银行客户、多业务域、大规模并发接入时，再评估服务拆分。

---

# 13. POC 评测和验收要求

## 13.1 测试范围

建议选择：

- 一个银行贷款或授信业务子域。
- 2–3 个相关 Java 仓库。
- 一组对应的业务规约与设计文档。
- 30–50 个真实或仿真的研发任务。
- 由专家人工标注的概念、规则、流程、代码映射和相关上下文黄金数据集。

黄金数据集需覆盖简单、中等和复杂场景，包含跨模块关系及冲突知识。

## 13.2 初始验收指标

| 指标 | 建议目标 | 说明 |
|---|---|---|
| 业务概念 Precision | ≥ 85% | 在人工标注集上评估 |
| 业务规则 Recall | ≥ 80% | 规则是否被发现 |
| 业务到代码映射 Precision | ≥ 85% | 映射是否正确 |
| 已批准规则有效证据覆盖率 | 100% | 不允许无证据发布 |
| 正式知识审核覆盖率 | 100% | 必须有审核记录 |
| 上下文关键知识 Recall@K | ≥ 85% | 基于黄金任务标注 |
| 未授权 Action 阻断率 | 100% | 安全强制要求 |
| 高风险动作审批覆盖率 | 100% | 安全强制要求 |
| 生产业务 Action 执行次数 | 0 | V2.0 范围禁止 |

以上抽取质量目标为初始门槛，统计时必须报告样本量、领域分布及置信区间或误差范围。

## 13.3 AI Coding 效果验收

必须开展 Baseline 对照实验。

在同一模型、同一任务和相近工具环境下测试：

1. CodeAgent + CodeGraph。
2. CodeAgent + CodeGraph + Bank Ontology。

重点观察：

- 是否降低业务规则遗漏。
- 是否更准确定位代码修改点。
- 是否减少无关上下文。
- 是否提高代码及测试验证通过率。
- 是否减少研发返工。

V2.0 不预设具体提效百分比，以实测结果作为评估依据。

## 13.4 必须覆盖的异常测试

- 源码方法更名或删除。
- 同一业务概念存在多个别名。
- 两份业务规约互相冲突。
- 旧规约与新代码不一致。
- 规则没有足够来源证据。
- 本体版本与代码 Commit 不一致。
- 用户无权限读取某个代码仓库。
- Agent 尝试调用未注册工具。
- Action 审批后目标分支发生变化。
- LLM 生成的结构化数据不符合 Schema。
- 文档中包含试图操控 Agent 的恶意指令。
- 工具超时但实际执行状态未知。
- 图谱查询出现跨项目数据泄漏风险。

---

# 14. 版本规划与开发优先级

## 14.1 M1：本体建模最小闭环

目标：先实现可信的业务本体生成。

交付：

- 本体 Schema。
- CodeGraph Adapter。
- 代码与文档解析。
- LLM 候选知识抽取。
- 节点/关系入库。
- 本体拓扑查看。
- 证据及专家审核。
- 发布版本。

验收场景：

从一个信贷 Java 项目中抽取额度校验规则，审核后展示其业务关系和代码实现。

## 14.2 M2：AI Coding Context 闭环

目标：让 CodeAgent 真正使用本体。

交付：

- 业务语义检索。
- 图谱与代码联合查询。
- Context Package。
- MCP Server。
- Token Budget。
- 检索评测。

验收场景：

给定贷款需求变更，Agent 可以获取已审核规则、相关代码和证据，并支持影响分析。

## 14.3 M3：受控 Engineering Action 闭环

目标：实现从理解到工程操作。

交付：

- Action Registry。
- Tool Binding。
- 权限和风险策略。
- Dry-run。
- 执行引擎。
- 审批和审计。
- 隔离工作区工具执行。

验收场景：

Agent 根据业务规则定位代码并发起关联测试，获得执行结果和可追溯审计记录。

## 14.4 M4：增量演进和质量闭环

目标：长期可运营。

交付：

- Git Diff 驱动更新。
- 失效知识标记。
- 知识冲突治理。
- FIBO / BIAN 可选映射。
- Context 质量反馈。
- Agentic SDLC 场景评测。

验收场景：

开发人员修改额度校验逻辑后，平台识别关联知识需要更新，完成审核并形成新版本。

---

# 15. 开发 Epic 与初始工作包

| Epic | 工作包 | 优先级 |
|---|---|---|
| EP01 | 本体 Schema、节点/关系模型及校验 | P0 |
| EP02 | Git、文档和 DDL 接入适配器 | P0 |
| EP03 | CodeGraph Connector 及代码符号标准化 | P0 |
| EP04 | LLM 抽取 Pipeline 与 Prompt Schema | P0 |
| EP05 | 实体对齐、规则融合及冲突发现 | P0 |
| EP06 | Graph Repository 与版本存储 | P0 |
| EP07 | 本体可视化工作台 | P0 |
| EP08 | 证据、审核、发布及版本治理 | P0 |
| EP09 | Context 检索、排序和组装 | P0 |
| EP10 | MCP Server 与接口契约 | P0 |
| EP11 | Action Registry 与 Tool Binding | P0 |
| EP12 | Policy Engine 与隔离执行服务 | P0 |
| EP13 | 身份权限、审批及审计 | P0 |
| EP14 | 评测集、指标计算和实验平台 | P0 |
| EP15 | FIBO / BIAN 对齐 | P1 |
| EP16 | 多 Agent Workflow 对接 | P1 |

每个 Epic 在进入开发前进一步拆解为：

- User Story。
- API Contract。
- Data Schema。
- Frontend Task。
- Backend Task。
- Unit / Integration Test。
- Acceptance Case。

---

# 16. 关键风险及应对

| 风险 | 应对要求 |
|---|---|
| LLM 抽取不准确 | 结构化输出、证据核验、专家审核 |
| 本体与代码逐渐不一致 | 版本绑定、Git Diff 和失效传播 |
| 业务概念过度合并 | 人工审核、别名与相似关系分离 |
| 图谱关系过度复杂 | 受控元模型、按业务域分层、子图视图 |
| 本体构建成本过高 | 从单领域试点、分阶段抽取与复用 CodeGraph |
| 上下文过长 | 任务感知检索、图扩展限制、Token Budget |
| 知识来源存在恶意指令 | 数据与指令隔离、Prompt Injection 防护 |
| Agent 误执行工具 | 工具白名单、权限、审批、隔离与审计 |
| 多数据源权限不一致 | 统一身份与资源授权、来源权限继承 |
| FIBO 过度复杂 | 选择性映射，不作为首期建模前置条件 |
| 产品范围扩张 | 以银行研发工程为核心，不建设通用运营平台 |

---

# 17. 待确认的设计决策

正式进入详细设计前，需要确定以下技术决策。

**ADR-001：CodeGraph 集成方式**

优先使用已有 CodeGraph 的标准查询接口，避免重复保存完整代码图。

**ADR-002：本体核心存储**

首期以 Neo4j 属性图为核心，PostgreSQL 管理工作流和版本元数据。

**ADR-003：本体语义标准**

平台原生使用轻量级元模型；FIBO / OWL 作为扩展和交换标准。

**ADR-004：代码分析范围**

V2.0 首期以 Java 为主，COBOL / IBM i 等通过独立解析器扩展。

**ADR-005：模型部署**

首选银行内网私有化模型，通过统一 Model Adapter 调用。

**ADR-006：Action 执行环境**

采用隔离工作区或容器，不直接对生产系统执行写操作。

**ADR-007：Agent 平台对接**

优先通过标准 MCP 对接 CodeAgent 和研发数字员工平台，不在本平台重复开发通用 Agent Runtime。

**ADR-008：版本一致性策略**

所有研发上下文包必须明确 Ontology Release、Source Snapshot 和代码基线；不匹配时提示或阻断高风险动作。

---

# 18. V2.0 最终验收场景

以银行消费贷款授信额度变更为贯通场景。

**输入：**

- 贷款相关 Java 源码。
- 相关数据库 DDL 和接口。
- 额度管理业务规约。
- 一条额度校验新需求。

**平台执行：**

1. 通过 CodeGraph 获取代码事实。
2. 通过 LLM 抽取业务对象、规则及流程。
3. 将候选本体形成可视化拓扑。
4. 业务专家确认核心规则及其实现映射。
5. 发布可信本体版本。
6. CodeAgent 从 MCP 获取相关业务知识。
7. Agent 生成影响分析结果。
8. Agent 申请执行相关测试和静态检查。
9. Action Engine 完成授权、验证及执行。
10. 平台记录结果和审计信息。
11. 新的代码变更触发关联本体更新检查。

**最终验收交付物：**

- 银行业务本体拓扑图。
- 业务概念和规则目录。
- 业务到代码实现映射。
- 可信证据链。
- 本体审核及发布记录。
- Context MCP 接口。
- Engineering Action 执行记录。
- A/B 评测结果及改进分析。

## 最终产品目标

Bank Engineering Ontology Studio V2.0 不只是一个用于展示银行业务关系的图谱工具。

它应当形成三个可独立复用的核心产品能力：

**1. Business Knowledge Mining Engine**  
从银行存量软件资产中挖掘可信业务知识。

**2. Living Business Ontology**  
构建持续更新、可审核、可追溯、与代码关联的银行业务本体。

**3. Ontology-driven Agentic Engineering**  
基于业务语义和受控 Action 驱动 AI Coding、需求分析、架构设计与质量验证。

最终实现从 **Code Understanding → Business Understanding → Governed Engineering Execution** 的能力升级。

---

**文档状态：V0.9，供评审及研发详细设计使用。正式锁定 V1.0 需求基线前，应完成元模型评审、技术 Spike、安全威胁建模和首批黄金样例验证。**