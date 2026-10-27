# Bank Engineering Ontology Studio V2.0
# 银行业务本体建模与研发智能执行平台需求规格说明书

**文档版本：** V0.13（二期 CodeGraph 实施基线；范围与验证边界见第12章）  
**目标产品版本：** V2.0  
**编制日期：** 2026-10-22  
**修订日期：** 2026-10-23  
**适用场景：** 银行 AI Coding、Agentic SDLC、SDD、存量系统理解与变更影响分析  
**技术路线：** CodeGraph + Business Ontology + LLM + Governed Actions + MCP  
**文档用途：** 产品设计、技术架构设计、开发任务拆解、测试验收

---

## 0. 修订依据与阅读约定

V0.10 已根据用户提出的界面要求、[界面能力详细设计 V0.2](界面能力详细设计_V0.2.md)、[高保真 UI 视觉规范 V1.0](高保真UI视觉规范_V1.0.md)及 HF1 高保真交互设计刷新 V0.9。V0.11 在此基础上按[第二轮评审意见](需求评审意见_V0.10_第二轮.md)修订六项契约缺口，不调整第2.3/14.5节的原型及完整 V2.0 范围。

### 0.1 修订摘要

**V0.13：二期采用开源 CodeGraph 引擎**

按用户确认，二期采用 Joern CPG 实现 CodeGraph；独立 Docker 解析服务、不可变解析身份和完整 CPG 产物成为技术基线。本轮增量补齐模块/概念修订审核及术语别名/全文融合检索。详见[需求修订记录 V0.13](需求修订记录_V0.13.md)与[开发记录 M2-02](开发记录_M2_02.md)。真实 OpenCode 需求质量、工作区基线与跨存储恢复仍须独立验收，不因解析成功认定 M2 整体验收完成。V0.12 原文保存在 `归档/V0.12/`。

**V0.12：二期技术方案对齐**

用户已确认 M2 部署在本地 Docker，首个真实验收客户端为 OpenCode。第12章明确 Neo4j + PostgreSQL、模块化后端与独立 Worker、版本化图投影、只读 MCP 和渐进检索路线；第14.2节及 ADR 同步对应范围。详细修订见[需求修订记录 V0.12](需求修订记录_V0.12.md)。本次是需求与选型更新，不表示数据库迁移、OpenCode 兼容性或二期验收已经完成。V0.11 原文保存在 `归档/V0.11/`。

**V0.11：第二轮评审修订**

- R01：区分逻辑代码实体、来源内容快照与解析投影版本，防止相同 Commit 的不同内容互相覆盖。
- R02：审批、排队和执行固定 Action/Tool Binding 修订及执行清单，执行前核查现行授权。
- R03：Context 使用逐仓库 `target_baselines[]`，区分匹配、不匹配与未知，覆盖未提交工作区。
- R04：补齐 Action 审批拒绝/过期、取消处理中、执行未知、恢复及并发转换规则。
- R05：明确模型按用途的正式使用门禁，区分草稿、受限验证与正式任务。
- R06：冻结评测协议，明确计分单位、完整规则、一对一匹配、K/预算及独立验收集。

V0.10 原文已保存在 `归档/V0.10/`；六项落点、验收与静态核查见[需求修订记录 V0.11](需求修订记录_V0.11.md)。功能需求编号沿用，不新增产品模块，也不将文档修订视为运行验证。

**V0.10：高保真对齐修订**

- 补齐本地/远程仓库接入、预检与来源快照契约。
- 新增代码实体拓扑与业务模块到类/方法的映射，补齐元模型、属性、关系和查询接口。
- 新增知识构建任务的配置快照、暂停/继续/取消、失败片段重试和人工确认状态机。
- 新增模型连接、运行参数、任务路由、能力验证和配置版本管理。
- 细化审核修订、发布清单、默认指针切换、跨存储发布一致性及平台审计。
- 新增 Code Agent 客户端档案、兼容性验证、Context 异常及真实调用轨迹要求。
- 新增中英文、浅色/深色/跟随系统、草稿保留与通用交互验收。
- 补充七页原型与完整 V2.0 的范围对应，保留未在高保真展示的原有产品能力。

原 V0.9 已保存在 `归档/V0.9/`；逐项遗漏、细化和范围调整见[需求对照与修订记录](需求对照与修订记录_V0.10.md)。

### 0.2 需求与设计的适用顺序

- 本文定义功能契约、权限、状态、接口和验收；界面详细设计与视觉规范是实现参考。
- 高保真中的颜色、精确像素、示例仓库、模型名、路径、统计和成功提示不是业务事实或已完成集成证明。
- 示例中的专家身份不授予真实发布权限；真实角色仍遵守第4章职责分离和服务端鉴权。
- 本轮完成前提、独立判断、信息分类、来源核验、异议与盲区六项校验。主要前提是用户已确认需要真实端到端原型；HF1 仅验证交互，不替代后端、浏览器视觉或兼容性验收。
- V0.11 仍是评审稿；元模型、解析器适配、客户端版本和部署环境存在待验证项，不将其写为已验证结论。本文新增的身份、门禁、状态与计分规则是规范性决策，尚需在真实实现中验收。

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
| 多源知识接入 | 本地/远程 Git、代码、文档、DDL、API、测试、来源预检 | P0 |
| 代码事实挖掘 | AST、调用链、SQL、依赖关系 | P0 |
| 业务本体抽取 | 对象、规则、流程、状态、事件 | P0 |
| 本体拓扑管理 | 业务本体、代码实体拓扑、跨层映射、属性与邻接查询 | P0 |
| 业务代码映射 | 业务模块/规则关联类、方法、测试和数据对象，支持双向追溯 | P0 |
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
| 知识构建任务治理 | 配置快照、阶段状态、确认、取消、重试及产物 | P0 |
| 模型配置管理 | 连接、参数、路由、验证和配置修订 | P0 |
| 客户端联调 | OpenCode、Claude Code、DeepSeek Harness 档案与按版本验证 | P0 |
| 国际化与主题 | 中英文、浅/深色及系统主题、草稿与偏好保留 | P0 |

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

### 2.3 高保真、最小原型与完整 V2.0 的范围

| 交付层次 | 约定 |
|---|---|
| HF1 设计预览 | 七个主页面及主要弹窗，数据、连接与执行为示例；不作为真实功能已交付的依据 |
| 首个端到端原型 | 一仓库一业务切片，真实解析/模型、证据审核发布、基础代码拓扑与映射、双语主题、只读 Context/MCP、至少一个真实客户端、第二 Commit 的基本失效检查 |
| 完整 V2.0 | 第2.1节全部 P0，按 M1～M4 逐步交付；不限于 HF1 已展示页面 |
| 增强能力 | P1/P2，含高级专业视图、标准映射、复杂推理和多 Agent 编排等 |

原型以 M1 + M2 的最小纵向链路为主；七页界面不意味着所有后台能力、完整模型治理或全部客户端版本同时上线。远程 Git 原型至少支持受控地址接入，企业 Git 仓库目录浏览/SSO 可后置；文档原型可先使用 Markdown/TXT/DDL，完整 V2.0 的 DOCX/PDF 要求保留。

Dashboard、Action Studio、评测平台和完整管理权限未被 HF1 完整呈现，不因此删减或自动降级。原型的固定 Schema、有限图展开、简单任务路由不替代完整产品的治理与扩展要求。

本轮新增双语主题、代码拓扑、独立版本页及配置流程涉及额外实现成本；此前 30～42 工程人日是旧范围估算，需按刷新后的工作包重估，不作本需求版本的工期承诺。

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

### 4.4 配置、确认与发布的操作边界

- 模型连接、路由、Git 凭据和允许发送的数据范围，仅 PlatformAdmin 或经项目授权的配置管理员可修改。
- 任务发起者可在权限范围内确认扫描范围、暂停和重试；该身份不自动获得领域知识审核或发布权限。
- DomainExpert 审核断言及语义映射；OntologyAdmin 或发布策略授权身份切换发布指针。高风险场景仍执行职责分离。
- Auditor 只读访问授权范围内的版本、审核和审计；AgentService 不得修改模型、来源或自身授权策略。
- 界面按钮隐藏/禁用仅用于交互提示；直接 REST/MCP 调用同样必须鉴权。配置读取权限不等于读取密钥的权限。

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

### FR-ING-006 本地仓库与受控工作区接入｜P0

支持本机目录选择或服务端已授权工作区选择，明确当前运行模式与读取位置。浏览器本地目录句柄不能被解释为服务器绝对路径；需要客户端代理、受控上传或服务端工作区时明确其接入契约。

接入字段：工作区/目录引用、仓库、分支、Commit/Tag、包含/排除目录、语言、项目/业务域与文档快照。服务端校验根目录授权、路径规范化、符号链接边界、Git 元数据、来源版本和可扫描范围；目录浏览也必须鉴权。

默认读取指定 Commit 的内容，不直接扫描有未提交变更的工作目录。若明确允许未提交内容，必须额外记录基线 Commit、差异/未跟踪文件清单和内容哈希，形成独立快照，不能冒称纯 Commit 版本；接入不得修改用户工作区。

验收：选择目录、返回上一步、更改范围均不丢草稿；未授权目录和越界路径被拒绝；相同快照重复导入幂等；证据来自固定快照而不是不断变化的工作目录。

### FR-ING-007 远程仓库选择与接入预检｜P0

支持受控 Git URL、提供方/连接引用、凭据引用、仓库和分支/Commit 选择。具备对应连接器时可列出有权限的仓库；企业目录浏览和统一登录增强可按 P1 交付。

保存来源配置、访问验证、创建快照和启动大规模抽取为独立操作。预检逐项返回访问/认证、版本解析、语言/依赖、扫描范围与错误，缺依赖或空范围不能显示为完整成功。不得把 Token 放入普通 URL、日志或返回体。

来源状态：`UNCONFIGURED → VALIDATING → READY → SYNCING → SNAPSHOT_READY`；另有 `AUTH_FAILED / DEPENDENCY_MISSING / SYNC_FAILED / DISABLED`。改配置不覆盖已有快照；仅注册连接不会自动调用模型。

验收：无权限仓库不可枚举；认证失败可重试；分支移动时已创建快照仍绑定原 Commit；预检失败可定位阶段，不伪造通过结果。

### FR-ING-008 文档版本关联与快照清单｜P0

文档关联应记录文件/文档 ID、格式、原始版本、内容哈希、来源权限、保密级别、定位方式及与代码快照的适用关系，不以相同上传时间推定文档与代码语义一致。

Source Snapshot Manifest 至少包含 source_snapshot_id、repository_id、基线 Commit、内容模式（COMMIT / WORKSPACE）、包含/排除规则及 scope_manifest_hash、规范化文件清单/内容哈希及 workspace_content_hash、文档引用和采集时间。快照必须保存可复核的有效内容，不以可变目录路径代替内容身份；同一仓库/Commit 下不同差异或未跟踪内容形成不同快照，重复导入相同有效内容与范围保持幂等。哈希算法、路径/编码规范化方式随清单格式版本固定。

内容快照与分析投影分开标识。Manifest 可保留任务使用的解析器配置/版本引用；独立 Analysis Projection 以 analysis_projection_id 固定 source_snapshot_id、解析器/配置、事实规范化 Schema 修订和产物摘要。同一内容以不同分析修订重新解析不得覆盖旧投影。支持查询快照/投影引用任务和 Release、比较变化；失效或无文本的文档明确报错。

验收：相同文件名不同内容可区分；规约版本冲突进入确认或审核；快照对比不修改历史证据。

---

## 6.2 模块 M02：CodeGraph 集成

### FR-CG-001 CodeGraph Connector｜P0

支持通过配置的 API、MCP 或适配器获取现有 CodeGraph 信息。

所需基础事实：

- Repository / Package / File。
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

代码身份分为逻辑实体和不可变版本实例：

- `logical_entity_id / stable_key`：平台内跨版本关联键，CodeSymbol 可称 Stable Symbol Key；不是源码版本定位符。改名/移动/重构后的关联需有解析或审核依据，不以相同名称自动合并。
- `entity_version_id`：由 `repository_id + source_snapshot_id + analysis_projection_id + local_entity_key` 唯一确定；local_entity_key 按实体类型固定规范路径、容器、种类及签名等区分符。Repository/File 等不要求方法签名。实际 ID 可为该规范组合的哈希或等价不透明键，须保存组成引用并检查唯一性。
- `edge_version_id`：固定分析投影、关系类型、起止版本实例及调用点/来源定位；平行调用点可区分。跨仓库端点引用各自的确切投影，无法确定目标版本时保留未解析记录，不伪造端点。

Commit 是溯源元数据，不能独自充当内容版本身份。相同 Commit、文件和方法签名的不同工作区快照，其 entity_version_id 必须不同；同快照/分析修订重复处理不得重复生成事实。不同解析修订可以产生不同版本实例，但不据此推定代码内容变化。

实体、边、证据、语义映射、审核、Release 及 API 均以确切版本引用建立追溯。Stable Key 只辅助跨版本查找，查询必须同时指定快照/投影或 Release，不能隐式解析为“最新”。

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

### FR-CG-005 代码实体拓扑与属性标准化｜P0

CodeGraph Adapter 应提供代码结构实体及有向关系：Repository、Package、File、Class、Interface、Method/Function、Test，以及 SQL/Table/Column 关联。具体语言与解析器不支持的类型必须返回能力/覆盖说明，Java 不人为构造不存在的顶层函数。

实体至少返回 entity_version_id、logical_entity_id/stable_key、entity_kind、名称/签名、repository_id/Commit、路径/行号（适用时）、文件内容哈希、source_snapshot_id、analysis_projection_id 及解析器修订。关系至少返回 edge_version_id、起止 entity_version_id、类型/方向、来源证据、投影及解析状态；不得用 Stable Key 代替版本端点。

调用目标记录 `resolution_status = RESOLVED / MULTIPLE_CANDIDATES / UNRESOLVED`，不将反射、注入或动态代理的目标候选标为确定调用。未知目标保留调用点/限制说明，不生成虚假的确定边。完整 CPG 可留在分析器产物中，平台只索引授权查询所需的事实投影。

### FR-CG-006 代码图查询与双向定位｜P0

支持实体/签名/ID 搜索、类型过滤、关系过滤、入/出边、一跳邻接及受限深度展开、实体/关系属性和源码定位。版本 ID 可直接定位，Stable Key 搜索需显式快照/投影或 Release。结果绑定项目、Source Snapshot/Commit、分析投影、解析器修订和覆盖范围，不默认返回完整图。

代码事实视图可以展示授权的未发布来源快照，但必须标为解析事实；业务正式上下文仍只使用已发布且获授权的业务断言。跨视图使用稳定实体身份，不依赖坐标或界面名称匹配。

验收：类可定位其方法、接口实现和调用关系；查看边能定位来源；过滤后不泄漏未授权节点名称/数量；从业务规则能定位方法，再反查关联规则。

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

`condition / outcome / scope / exceptions / uncertainties / evidence / review_status`

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

- 不符合 Schema 的结果不能作为有效候选或正式知识入库；失败原文可隔离保存为诊断产物。
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

### FR-ONT-005 基础多视图与专业视图｜P0

基础必须具备：

1. Business View：业务模块、对象、属性与规则。
2. Code Entity View：独立的代码实体结构与依赖拓扑，不以业务逻辑链路图代替。
3. Mapping View：业务模块/规则与代码类、方法、测试的双向映射。

Rule View、Process View 和专业 Impact View 为 P1 增强；基础影响分析结果和证据路径仍遵守 UC03、FR-CTX-002。业务全景仅是受限子图的组合展示，不代表完整业务流程或完整运行时影响集合。

本条从 V0.9 的整体 P1 调整：高保真新增且用户明确要求的三类基础视图纳入 P0，高级视图保留 P1。

### FR-ONT-006 本体导入导出｜P1

支持 JSON、JSON-LD，以及按需导出 RDF/OWL。

必须定义格式版本和字段兼容策略。

---

### FR-ONT-007 属性图工作台与实体检查器｜P0

工作台支持按来源基线、业务域、实体/关系类型、可信状态筛选，搜索实体 ID/签名，选中节点展开邻接，查看有方向的关系标签及边属性。右侧检查器提供属性、入/出边、源码/文档证据及版本入口；选中实体与直接关系联动高亮。

业务本体、代码拓扑、映射视图间保持实体身份和授权范围；图布局、缩放、折叠等展示状态独立于语义数据。节点/关系原始记录可供有权限的用户查看，不暴露底层凭据或执行任意图查询。

交互可限制返回节点数、关系数和深度；超过限制应说明截断及未展开范围。拖拽、缩放、布局恢复和大图性能需真实验证，不能以固定示例图验收通过。

### FR-ONT-008 业务模块到代码实体映射｜P0

新增 BusinessModule 表示银行业务能力/模块，不把技术包自动认定为业务模块。支持模块到 Class/Method 的 `IMPLEMENTED_BY`，规则到 Method 的 `IMPLEMENTED_BY`、到真实测试符号的 `VERIFIED_BY`；映射端点及语义由 Schema 明确限制。

映射记录包含独立修订、适用范围、证据、来源、审核状态与代码基线，代码端点固定 entity_version_id 及其快照/投影；stable_key 可辅助跨版本追踪，不能将历史映射自动改指新端点。可由模型推荐或人工建立，未经审核不得当作正式关联；从类/方法反向查询业务模块/规则时继承相同授权与可信状态过滤。

验收：模块可定位实现类，规则可定位方法/测试，反查可回到业务；无证据映射不能发布；方法重命名不能仅靠同名自动承接旧映射。

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

支持证据定位、查看、版本校验及失效标记。源码证据固定 source_snapshot_id、文件内容哈希和位置；引用代码实体/调用关系时附 entity_version_id/edge_version_id 与 analysis_projection_id。证据展示读取该固定内容，不能读取当前工作目录或把 stable_key 动态解析为最新版本。

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

### FR-GOV-007 修订绑定审核与发布门禁｜P0

审核结论绑定 assertion_revision、relation_revision、代码端点版本/投影、证据哈希、来源快照、Schema 和审核策略。更改条件、结果、范围、例外、映射或证据生成新修订，新修订不得沿用旧批准；原审核记录仍保留给旧修订，已发布版本不被原地修改。

拒绝/退回需原因；待补证不等于冲突解决。批量审核/发布逐项校验，给出通过、失败、排除原因，不能因任务“确认继续”批量批准业务知识。单条断言及语义映射必须通过字段、端点、证据、范围、冲突与权限校验。

### FR-GOV-008 发布清单、默认指针与回退｜P0

Release Manifest 固定断言/关系修订集合、证据引用、Schema、来源快照及代码事实投影版本。发布前预检显示新增/更新/排除项及阻断原因；冲突可排除出本次清单，但该冲突本身不能被标为已批准。

默认指针切换与 Release 内容分离。回退或切换必须记录目标版本、影响知识、来源基线、操作者、原因及指针修订，使用并发前置条件避免覆盖他人的切换。历史 Release 不删除、不修改；切换不回退业务源码。

Context 在查询开始固定 Release/来源清单，默认指针中途变化不混用两个版本；后续查询读取新指针。缓存键至少区分项目、Release、来源快照、Schema 与授权范围，并在权限撤销时重新鉴权，不能从缓存返回已无权访问的内容。

验收：R2 包含新规则，切回 R1 后默认查询不再返回该新规则；显式历史版本查询仍可复现且执行现行访问控制；并发切换产生版本冲突而非静默覆盖。

### FR-GOV-009 平台操作审计｜P0

除 FR-ACT-009 外，来源/模型配置、任务确认与取消、知识审核、发布/指针、客户端授权配置和反馈均记录操作者/委托身份、对象及修订、输入摘要、决定/原因、时间、结果和 Trace ID。

审计读取按租户/项目鉴权，敏感值脱敏；生产环境按第11章实现防篡改和保留策略。界面演示日志不能作为生产审计完整性的证据。

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

创建执行申请时固定 Action Definition 与 Tool Binding 的具体修订，不在出队时解析“最新版本”。执行前校验工具标识/版本、端点、允许操作、映射、策略与环境均符合不可变执行清单。

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

主路径：`CREATED → VALIDATED → PENDING_APPROVAL → QUEUED → RUNNING → SUCCEEDED / FAILED`。无需审批可跳过 PENDING_APPROVAL。另有 `APPROVAL_REJECTED / APPROVAL_EXPIRED / CANCEL_REQUESTED / CANCELLED / EXECUTION_UNKNOWN`。

以下为允许的状态转换；事件按 execution_id、attempt_id 和 status_revision 串行化/并发控制，不允许客户端任意设置状态：

| 当前状态 | 条件/操作 | 后续状态与要求 |
|---|---|---|
| CREATED | 输入/清单校验 | 通过为 VALIDATED；确定校验失败为 FAILED，不调用工具 |
| VALIDATED | 风险策略决定 | 需审批为 PENDING_APPROVAL；无需审批且授权有效为 QUEUED |
| PENDING_APPROVAL | 有效决定/到期 | 通过为 QUEUED；拒绝为 APPROVAL_REJECTED；到期为 APPROVAL_EXPIRED |
| CREATED / VALIDATED / PENDING_APPROVAL / QUEUED | 确认输入、授权或固定清单失效 | FAILED 并记录原因，不调用工具；审批拒绝/到期使用各自专门终态 |
| QUEUED | 调度取得执行权，发送工具请求前复核 | 清单、现行权限/策略、审批与取消标记均有效才可进入 RUNNING 并发送；审批过期为 APPROVAL_EXPIRED；确定门禁失败为 FAILED |
| CREATED / VALIDATED / PENDING_APPROVAL / QUEUED / RUNNING | 请求取消 | 持久化后为 CANCEL_REQUESTED；不等于已经停止 |
| CANCEL_REQUESTED | 核验取消/实际结果 | 确认不再调度或工具已停止为 CANCELLED；已成功/明确失败为 SUCCEEDED/FAILED；无法确认结果为 EXECUTION_UNKNOWN |
| RUNNING | 收到可核验结果 | SUCCEEDED / FAILED；通信超时且可能已经执行时为 EXECUTION_UNKNOWN |
| EXECUTION_UNKNOWN | 执行引擎核验工具/产物 | 确认仍运行则回 RUNNING，存在取消意图则回 CANCEL_REQUESTED；确认结束则进入 SUCCEEDED/FAILED/CANCELLED；无法确认则保持未知 |
| EXECUTION_UNKNOWN | 请求取消 | 持久化取消意图及请求结果，保持 EXECUTION_UNKNOWN 直到核验，不据此推定停止或失败 |
| SUCCEEDED / FAILED / CANCELLED / APPROVAL_REJECTED / APPROVAL_EXPIRED | 重复命令/迟到回调 | 保留终态；重新申请或安全重试创建关联的新执行/尝试，不原地复活审批或执行 |

队列、并发、超时、取消、幂等键、重试、日志及产物均持久化。调度器和 Worker 的取消检查、取得执行权与派发记录使用同一并发前置条件；若取消先赢则不得派发，派发先赢则取消进入工具核验流程。重复投递、租约失效和服务重启不能直接造成第二次工具调用；保存稳定幂等键、attempt_id 和 tool_execution_reference，恢复时先核查已派发请求。

审批有效期是允许开始执行的期限，排队期间到期不能出队执行；已合法开始的动作不因之后到期自动中止，仍受超时、取消及现行授权/策略约束。审批拒绝/到期后重申生成新记录并关联原申请。

取消与完成竞态按核验的实际结果确定终态。成功已发生则记 SUCCEEDED 并记录取消未生效，不能被迟到取消消息覆盖。CANCELLED 只代表停止，不承诺撤销此前副作用；保留已有产物和副作用摘要，按动作既有清理/回滚策略处理。

执行引擎负责未知状态核验及重启恢复，使用工具执行查询、幂等查询或可信产物对账；人工补充证据同样审计，不能凭点击确认伪造工具结果。超时/断连不自动等于 FAILED。无法确定实际状态或结果时保持 EXECUTION_UNKNOWN，确认实际结果后进入相应状态。是否允许重试单独判定；未证明可安全重试时禁止自动重跑，不影响已核验成功/失败结果的入账。核验频率、责任人/告警和保留期限在部署策略中配置。

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

高风险工程动作必须在执行前完成审批。审批绑定不可变 Execution Manifest 及 manifest_digest，至少包括：

- Action ID/Version 与 Tool Binding ID/Revision。
- 工具标识/版本、端点引用、允许操作和输入/输出映射的修订或内容摘要。
- 输入参数摘要、目标资源及逐仓库来源快照/工作区内容基线。
- 操作风险、策略 ID/Revision、执行环境配置修订与资源限制。
- 发起/委托身份、审批策略及申请约束；审批决定、批准时间和实际有效期限写入绑定该摘要的 Approval Record，不回写 Execution Manifest，避免改变待审批摘要。

执行申请、审批、排队、工具派发与执行记录必须引用同一清单摘要。Tool Binding 的 endpoint、operation、映射或策略变更必须产生新修订；即使 Action 版本和输入不变，旧批准也不能授权新 Binding。需要更换清单时停止原执行并记录 `APPROVAL_CONTEXT_CHANGED`，创建关联的新申请，重新预检并按风险策略审批。

仅发布新的 Binding 修订不必取消旧排队记录；旧记录只可执行原清单固定的旧修订，且该修订仍可用、未撤销并通过现行授权/策略。出队/派发前再次检查权限、目标基线、审批有效期和摘要一致性，拒绝则不调用工具。固定旧策略修订用于复核审批依据，不允许绕过新生效的禁止规则或授权撤销。

凭据仅保存引用及影响身份/范围的版本依据，不进入明文清单/日志。凭据轮换不得静默改变调用身份、端点或授权范围；影响审批依据的变化按新清单处理。

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
- Release 来源清单（逐 repository_id 的 source_snapshot_id、Commit、内容/范围摘要与 analysis_projection_id）。
- target_baselines[]、逐来源 baseline_comparisons[] 及总体 baseline_status。
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

完整 V2.0 按相应闭环提供以下工具：

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

### FR-CTX-008 Code Agent 客户端档案与接入验证｜P0

目标客户端为 OpenCode、Claude Code、DeepSeek Harness，分别维护档案；它们与 DeepSeek 等模型连接不是同一配置对象。档案包含客户端/安装版本、传输/适配器、服务地址或受控启动器引用、委托身份/凭据引用、项目/仓库、Release 策略和工具白名单。

连接流程：配置平台契约 → 客户端添加连接 → 握手/工具发现 → 身份/资源授权 → 真实任务/证据验证。客户端连接方式、鉴权和配置文件格式按实际安装版本验证，不以一个通用示例配置宣称全客户端兼容。服务器不接受来自普通调用者的任意启动命令或未注册地址。

兼容矩阵至少记录客户端版本、协议/传输、测试环境、测试日期、用例/结果和已知限制，状态为未验证、通过、受限或失败。首个端到端原型至少完成一个真实客户端；其余可保留明确“未验证”的档案，宣称支持前必须分别实测。

### FR-CTX-009 Context 调测输入与异常契约｜P0

调测输入包含需求、任务类型、业务域、`target_baselines[]`、Release/默认策略、预算、深度和可信过滤；输出包含召回规则、实现路径、来源证据、授权范围、版本、未知项和 Token 统计/估计方式。原型可限制 target_baselines 为一项，但沿用同一结构。

每项 TargetBaseline 的 repository_id、target_kind（COMMIT / WORKSPACE）必填；判定 MATCH 所需的可复核身份为：已授权且与声明仓库/模式相符的 source_snapshot_id；或已解析 Commit + scope_manifest_hash + 有效文件清单摘要 workspace_content_hash。允许提交尚不完整的目标声明，缺身份/范围依据时返回 UNKNOWN 及 missing_fields，而非强行匹配；字段格式错误或仓库/快照归属不符仍返回 VALIDATION_FAILED。COMMIT 指明确查询不可变提交内容，可由平台从该 Commit 构造清单；WORKSPACE 还必须覆盖未提交差异、删除与未跟踪文件，来自受控采集/代理并可验证其内容，不接受仅凭客户端自报“干净”或一个 SHA 就声称工作区一致。记录采集来源/时刻；匹配结论只适用于已核验范围和该次采集内容，不能证明客户端工作区之后未变化，执行前须按实际目标重新校验。

比较查询实际涉及的每个授权仓库与固定 Release 的来源清单，返回 `baseline_comparisons[]`：repository_id、Release/目标内容引用、comparison_basis、verified_scope、target_captured_at/compared_at、`status = MATCH / MISMATCH / UNKNOWN`、reason 及可选 missing_fields。MATCH 要求同一比较范围的有效内容身份已验证一致；已证实内容不同为 MISMATCH；遗漏涉及仓库、缺少内容身份、范围不覆盖或无法核验为 UNKNOWN。同 Commit 不同未提交内容为 MISMATCH；仅指定 COMMIT 时只证明指定提交及范围相符，不声明调用者的实际工作区一致。仅分析器修订不同不等于源码内容不匹配，投影版本单独披露。

总体 baseline_status：任一涉及来源 MISMATCH 则为 MISMATCH；无差异但存在 UNKNOWN 则为 UNKNOWN；全部涉及来源均已验证才为 MATCH。无涉及来源时为 UNKNOWN，不能将空数组当作匹配。未授权仓库先返回 ACCESS_DENIED，不泄漏其基线、名称或隐藏仓库数量。

稳定错误/结果码至少区分 `ACCESS_DENIED / SOURCE_BASELINE_MISMATCH / SOURCE_BASELINE_UNKNOWN / CONTEXT_BUDGET_INSUFFICIENT / NO_MATCH / TIMEOUT / EVIDENCE_STALE`。只读诊断可返回授权的历史内容和显著差异/未知警告，但不能标作适用当前目标的成功匹配；M3 高风险 Action 在有关基线 MISMATCH/UNKNOWN 时必须阻断并复核，不能靠忽略警告继续。人工诊断查看候选需显式开启并标注，Agent 正式 Context 不混入未发布候选；无匹配不使用模型猜测填充正式规则。

预算按完整规则单元裁剪，保留适用范围和例外；最小关键规则仍放不下时明确报预算不足，不返回被截断的“成功”上下文。模型与 Tokenizer 版本/估算口径应可追踪。

### FR-CTX-010 调用轨迹与反馈隔离｜P0

轨迹记录实际客户端/身份、调用工具及版本、参数摘要、授权决定、Release/来源、返回对象、耗时/错误与 Trace ID。失败后未执行的步骤标为未执行，不能从最终结果倒推虚构调用链，也不要求展示模型内部思考。

联调页可提交相关性、遗漏规则、映射错误及验证结果反馈；反馈只进入处理队列，不直接修改正式知识。FR-CTX-007 的聚合分析和检索优化仍为 P1。

---

## 6.8 模块 M08：质量评测与可观测性

### FR-EVAL-001 本体抽取评测｜P0

评测：

- 概念抽取 Precision / Recall / F1。
- 业务规则 Precision / Recall / F1。
- 关系正确率。
- 流程节点与状态转移覆盖情况。
- 业务到代码映射准确率。

计分单位、匹配与重复处理遵守第13.6节版本化协议；模型自报置信度不作为正确答案。

### FR-EVAL-002 Context 评测｜P0

评测：

- Relevant Context Recall@K。
- Relevant Context Precision@K。
- 关键规则覆盖率。
- Dependency Retrieval Coverage。
- 无效上下文比例。
- Token 使用量。
- 证据有效性。

Recall@K 的 K、知识单元及预算须按第13.6节冻结，另报告检索失败和无答案场景，不能测试后扩大 K 改善验收结论。

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

## 6.9 模块 M09：知识构建任务与人工确认

### FR-JOB-001 任务配置与快照｜P0

统一管理采集、代码解析、对象/规则抽取、增量检查和失败片段重试。任务保存类型、授权身份、来源快照、扫描范围、解析器、模型配置修订、路由、Prompt/Schema、预算、错误暂停策略和输入摘要。

任务启动时固定配置清单。涉及模型的正式阶段在任务接受及每次实际模型调用派发前按 FR-MDL-003 统一核验用途、固定修订、运行指纹与现行授权，保存 validation_id/fingerprint；非模型阶段不强制配置模型。全局连接改名、模型切换、Prompt 更新或默认分支移动不得改变运行中/历史任务依据；引用的版本不可用时明确失败，不静默替换。任务配置快照保存版本/凭据引用，不复制明文密钥。

### FR-JOB-002 状态、阶段与恢复｜P0

主路径：`CREATED → QUEUED → RUNNING → CANDIDATE_READY → SUCCEEDED`；需要人工决定时 `RUNNING → WAITING_CONFIRMATION → QUEUED/RUNNING`。另有 `PAUSE_REQUESTED / PAUSED / FAILED / CANCEL_REQUESTED / CANCELLED / EXECUTION_UNKNOWN`。

CANDIDATE_READY 只表示阶段产物可审核，不等于知识批准或发布；任务可在确认产物落库后完成，不必等待业务审核。阶段/总体进度均据真实处理量或明确阶段展示，不使用随机百分比。

暂停在安全检查点确认；继续保留输入清单与检查点；取消先显示请求状态，Worker 确认后才标 CANCELLED。超时或状态未知先核验执行/产物，不盲目重跑。幂等键、租约/心跳与重试避免重复写入。

### FR-JOB-003 人工确认项与决定记录｜P0

确认项覆盖相似概念、来源冲突、依赖不足、无效输出、预算超限和基线变化。每项记录类型、任务/阶段、相关实体、证据、可选处理、阻断范围、输入摘要及修订、处理人、决定、理由和时间。

允许保留独立概念、提出合并候选、隔离冲突继续、暂停补证、缩小范围或终止。待决定项影响的断言不得绕过后续审核；用户选择暂不处理时保留未知/隔离状态。输入或来源变化后旧决定不自动适用，需重新校验。

执行前范围确认、任务中间决定、专家审核、发布门禁分别建模。界面不能以一次“确认”授权后续所有操作，也不能从“模型置信度高”推定专家同意。

### FR-JOB-004 阶段产物与失败片段重试｜P0

任务详情展示配置快照、来源/文件范围、阶段状态、候选节点/关系、冲突/失败清单、实际模型耗时/Token（无数据标未知）、产物和审计。模型返回的非法结构可作为隔离失败产物保留，但不作为有效候选或正式知识入库。

支持仅重试失败片段，复用有效检查点与固定输入；修改模型/Prompt/范围后形成可区分的新任务尝试或子任务，保留前后配置。服务重启后任务状态和确认项不依赖浏览器会话。

---

## 6.10 模块 M10：模型与抽取配置

### FR-MDL-001 连接注册与使用范围｜P0

独立配置模型连接名称、Adapter/协议、Endpoint、模型 ID、凭据引用、启用状态、租户/项目、数据边界和允许用途。通过统一 Adapter 支持已验证的私有模型接口；界面列出的协议不代表已兼容所有提供方。

模型列表/API/日志不回显密钥；配置权限和凭据使用权限分别校验。调用前验证来源授权及允许发送的范围，不默认外传源码。配置修改、停用及测试有审计；停用策略明确对新任务与运行中任务的影响，不能静默换模型。

### FR-MDL-002 运行限制与任务路由｜P0

运行参数包含上下文上限、最大输出、温度（按能力）、超时、重试、并发和批次预算。校验正整数/非负重试、上下文与输出关系及 Adapter 能力；限额在服务端执行。

规则/对象抽取、上下文分析和可选 Embedding 可指定不同连接。路由草稿可引用未验证配置；启用路由必须固定 connection_id/revision、用途及契约指纹，并通过 FR-MDL-003 的统一 eligibility 判定，失败时仅保留草稿和原因，不能标为可用于正式任务。原型可固定简单路由；复杂条件路由、自动降级和成本优化为 P1，任何降级都不能扩大数据发送范围或改变已固定任务的执行依据。没有实测用量/有效价格依据时，不展示确定费用或提效承诺。

### FR-MDL-003 配置修订与能力验证｜P0

保存不可变配置修订。测试结果绑定 validation_id、用途、配置/运行契约指纹、模型/Adapter、Schema/Prompt、样例输入摘要、分项结果、时间及有效期。各分项为 PASS / FAIL / UNTESTED；影响运行指纹的新修订、契约变化或验证过期另标 STALE，不能沿用旧通过标记。仅展示元数据变化且运行指纹完全相同的新修订可关联复用原验证，保留旧验证与新修订的关联依据，不覆盖原记录。

所有用途均要求连接可达、调用身份有效、请求模型与 Adapter 匹配、上下文/输出及预算限制通过；另外按用途要求：

| 正式用途 | 必需通过的能力/契约 |
|---|---|
| 对象/规则等结构化候选抽取 | 指定 JSON Schema 的结构化输出、必填及不确定字段处理、证据引用契约 |
| 可选模型参与的 Context 分析/摘要 | 该输出契约规定的格式、证据引用与来源边界；不能生成无依据的正式规则 |
| 可选 Embedding | 返回格式、固定维度、有限数值与输入长度限制；不要求生成规则 JSON 或证据引用 |

“证据引用通过”指验证流水线能检查引用是否属于授权输入、固定快照与有效位置，错误输出可隔离；不是模型承诺证据必真，也不替代正式输出逐条校验/专家审核。所需能力不支持、FAIL、UNTESTED、STALE 或验证撤销时 eligibility=false；非该用途的可选能力未验证不阻断该用途。

统一资格检查用于路由启用、正式任务接受及每次实际模型调用派发：固定修订、用途、必需分项全 PASS、指纹匹配、验证在有效期内/未撤销，以及现行连接状态、身份、数据边界和预算全部通过。验证失败返回 `MODEL_CAPABILITY_FAILED`，未验证返回 `MODEL_VALIDATION_REQUIRED`，指纹变化/过期返回 `MODEL_VALIDATION_STALE`，同时给出授权范围内的原因。路由仍显示历史配置时，也必须标记当前不可用；不自动改用其他模型。停用、凭据或权限撤销后，运行任务未发出的后续调用同样停止；已发出的请求如无法撤销，记录实际结果，产物继续遵守现行权限、隔离及审核门禁。

指纹至少覆盖 Endpoint、模型标识/可得版本、Adapter 修订、影响身份/范围的凭据版本、运行参数、用途、允许发送的数据边界、Prompt/Schema、证据验证器及能力检查契约。相关项变化须验证新指纹；仅改展示名称不必废止原指纹。旧任务仍固定旧修订，但暂停后的继续/新派发仍检查有效期与现行授权；旧修订仍有效才可继续，不因有新修订而自动切换。已完成调用保留历史验证依据。

受限 VALIDATION/DIAGNOSTIC 任务允许在未通过能力验证时运行，以便完成验证本身；仍必须有测试权限、有效凭据、允许的最小样例/数据边界、资源及预算限制。验证产物隔离保存，不能产出可审核/发布的正式知识，也不能借诊断模式启动正式抽取。仅测试样例通过不代表总体质量达标；模拟结果标为模拟，不写成真实连通证明。

### FR-MDL-004 Prompt/Schema 契约与复现记录｜P0

使用版本化 Prompt/JSON Schema 及必填字段、证据引用规则、无法确定字段的处理策略。任务保留配置修订、模板/Schema、输入哈希、输出/错误与用量口径；同样配置和输入可回放以便比较，不承诺随机模型输出逐字一致。

首版可使用固定模板/Schema；图形化模板编辑、完整发布/弃用及兼容迁移工作流为 P1。配置管理员不得借模板内容绕过平台权限或允许任意工具执行。

---

## 6.11 模块 M11：门户通用交互与显示偏好

### FR-UI-001 中英文与稳定标识｜P0

支持 `zh-CN / en`，覆盖导航、字段、状态、提示、空态、错误、弹窗、图标签说明与无障碍名称。后端返回稳定枚举/错误码及参数，不能用翻译后文本判断权限或状态。

业务术语可保存审核过的多语言显示名/别名，缺译回退原文；切换界面语言不自动模型翻译证据、源码、文件路径、签名、工具/Schema/关系类型 ID 或用户输入。日期/数字可本地化，时间记录保存统一时刻，显示明确时区。

### FR-UI-002 主题与用户偏好｜P0

支持浅色、深色、跟随系统，覆盖导航、面板、表单、代码证据、图节点/边、选中、禁用、提示及弹窗。语义颜色配合文字/形状使用，不能只靠颜色表示可信或风险状态。

语言/主题按用户保存，有受控本地回退；切换不更改知识、来源或业务状态，也不丢失当前页面、筛选、实体选择及未保存草稿。舒适/紧凑密度为 P1 可选，不改变功能。

### FR-UI-003 表单、草稿与状态反馈｜P0

创建/编辑具备必填/格式/范围校验、服务端错误定位、处理中、防重复提交、成功/失败/超时及无权限状态。向导返回保留输入；关闭/离开存在未保存编辑时提供可理解的处理选择。

知识和模型编辑草稿与已保存修订分离，已发布内容只读；客户端切换各自保持配置，不混用其他客户端参数。草稿不得在普通浏览器存储中保留明文凭据，登出/权限变化按策略清理敏感草稿。

### FR-UI-004 响应式与可访问操作｜P0

适配桌面主工作区及窄屏重排。长英文、签名和规则可查看完整内容，不因缩写截断关键条件；表格可受控水平滚动，图展开有数量/深度限制。

提供键盘操作、可见焦点、表单标签、错误/状态通知；真实模态框应管理焦点、阻止背景操作、关闭后恢复触发点。必要操作不能只在悬停出现；弹窗关闭/主题切换不能静默丢弃已编辑内容。

### FR-UI-005 示例、连接与实现状态区分｜P0

设计预览、演示数据、模拟执行与真实环境明确标识。真实页面区分未配置、未连接、验证中、通过、受限和失败；不得把模拟成功、固定节点数、示例日志或前端事件等同于真实集成和质量验收。

HF1 的配色/像素/节点标记属于视觉规范，研发可等效实现；不得把示例 Node ID、模型名、路径或固定任务编号硬编码为平台业务规则。

---

# 7. 本体元模型设计要求

## 7.1 节点类型

| 节点类型 | 用途 | 示例 |
|---|---|---|
| BusinessDomain | 银行业务领域 | Credit |
| BusinessModule | 业务能力/模块，区别于技术包 | CreditLimitManagement |
| BusinessConcept | 业务概念和术语 | 授信额度 |
| BusinessObject | 业务对象类型 | LoanApplication |
| BusinessAttribute | 业务属性 | availableLimit |
| BusinessRule | 业务条件和约束 | 额度校验 |
| BusinessProcess | 业务流程 | 贷款申请处理 |
| ProcessStep | 流程活动 | 风险检查 |
| BusinessState | 业务状态 | APPROVED |
| BusinessEvent | 业务事件 | LoanApproved |
| BusinessAction | 业务操作语义 | ValidateLimit |
| CodeEntity | 代码容器及结构实体的抽象类型 | Repository / Package / File |
| CodeSymbol | Class/Interface/Method/Function 等符号类型 | checkLimit() |
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
| BELONGS_TO | BusinessObject / BusinessModule → BusinessDomain |
| CONTAINS_BUSINESS | BusinessModule → BusinessModule / BusinessObject / BusinessRule（须按 Schema 注册） |
| CONTAINS | Repository / Package → Package / File |
| DECLARES | File / Class / Interface → Class / Interface / Method / Function（依语言约束） |
| IMPLEMENTS | Class → Interface |
| EXTENDS | Class → Class；Interface → Interface（解析器确认时） |
| HAS_ATTRIBUTE | BusinessObject → BusinessAttribute |
| RELATES_TO | BusinessObject → BusinessObject |
| GOVERNED_BY | BusinessObject → BusinessRule |
| CONTAINS_STEP | BusinessProcess → ProcessStep |
| NEXT_STEP | ProcessStep → ProcessStep |
| HAS_STATE | BusinessObject → BusinessState |
| TRANSITIONS_TO | BusinessState → BusinessState |
| TRIGGERED_BY | BusinessAction → BusinessEvent |
| USES_RULE | BusinessAction / ProcessStep → BusinessRule |
| IMPLEMENTED_BY | BusinessModule → Class / Method；BusinessRule / BusinessAction → Method / Function（按 Schema 扩展到已验证实现符号） |
| READS / WRITES | CodeSymbol → DataAsset |
| CALLS | CodeSymbol → CodeSymbol |
| VERIFIED_BY | BusinessRule → CodeSymbol(Test) |
| SUPPORTED_BY | 业务断言 → Evidence |
| MAPS_TO_STANDARD | BusinessConcept → FIBO Reference |
| BINDS_TO | EngineeringAction → ToolBinding |
| DERIVED_FROM | 知识断言 → SourceSnapshot |
| AFFECTS | 变更分析结果 → 业务/代码对象 |

关系本身需要支持证据、版本和审核状态。`CodeEntity / CodeSymbol` 的 entity_kind 可实现为受控子类型/标签；Test 可使用测试角色的代码符号，Table/Column 复用 DataAsset，避免复制为不一致的两份实体。Repository/File 等没有方法签名的实体按 FR-CG-002 使用规范路径/容器标识作为 local_entity_key，版本 ID 仍固定快照与分析投影。

CodeSymbol 是 CodeEntity 的符号子类型；Class/Interface/Method/Function 作为受控 entity_kind/标签。代码事实不因尚未专家审核而自动变为语义候选，但其解析确定性必须按 FR-CG-005 单独记录。

代码结构 `CONTAINS` 与业务模块包含 `CONTAINS_BUSINESS` 分别定义端点和语义，不能因 UI 同样显示“包含”而混用。节点 ID、关系类型及端点约束不随界面语言变化。

对于 `SUPPORTED_BY` 等断言级证据关系，可使用独立 Assertion Record 或关系实体化方式，确保每条业务断言都有独立的证据及审核记录。

## 7.3 通用节点字段

每个知识节点至少定义：

- `id`：唯一标识；代码实体使用 entity_version_id，不可变代码边使用 edge_version_id，业务断言使用其明确修订引用。
- `stable_key`：跨版本关联标识，不能单独定位代码/证据的确切内容。
- `node_type`：节点类型。
- `name`：名称。
- `display_name`：默认展示名称；可扩展 `display_names`（如 zh-CN/en）及审核过的别名。
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

`confidence` 仅表示候选模型评估，不可替代专家审核。对静态代码事实，模型置信度和专家审核字段可不适用/为空；这不构成已批准的业务语义映射。不同实体族的 status 使用明确枚举，不能混用任务状态、解析状态与知识审核状态。

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

## 7.6 补充契约记录与字段

以下是工程实体/值对象，不要求全部存成业务图节点：

| 记录 | 必需字段 |
|---|---|
| SourceSnapshot / AnalysisProjection | snapshot_id、repository_id、Commit、内容模式、内容/范围清单及哈希；projection_id、snapshot_id、解析器/配置/事实 Schema 修订及产物摘要 |
| Code Entity / Edge | entity_version_id/edge_version_id、logical_entity_id/stable_key、local_entity_key、entity_kind/edge_type、端点版本 ID、source_snapshot_id、analysis_projection_id、位置/内容哈希、resolution_status |
| TargetBaseline / BaselineComparison | repository_id、target_kind、snapshot 或 Commit/内容/范围身份；比较依据、核验范围、MATCH/MISMATCH/UNKNOWN、原因及总体状态 |
| ModelConfigRevision / Validation | connection_id/revision、Adapter/模型/参数、凭据引用/版本、项目/数据边界；validation_id、用途、运行指纹、分项状态、时间/有效期、撤销及 eligibility 原因 |
| KnowledgeJobConfig | job_id、attempt_id、类型、授权身份、来源与范围、解析/模型/Prompt/Schema 配置、用途与 validation_id/fingerprint、预算及策略、输入摘要 |
| ConfirmationItem | item_id/revision、job/stage、触发类型、证据、阻断范围、决定/理由/身份/时间、输入摘要 |
| Assertion / Mapping Revision | stable_id、revision、起止点或内容、scope/exceptions、证据、审核绑定及状态 |
| ReleaseManifest / Pointer | Release 内容清单、Schema/来源/投影、发布验证；默认指针独立保存 target/revision、切换身份和原因 |
| AgentClientProfile | client/version、transport/adapter、受控地址/启动器、身份/凭据引用、授权项目、Release 策略、工具清单和验证结果 |
| UserPreference | 用户、locale、theme、可选 density；不包含知识或凭据 |
| AuditEvent / ToolTrace | 身份/委托、对象修订、动作、参数摘要、授权/决定、时间、结果、Trace ID |
| EvaluationProtocol / Run | 协议/黄金集版本及划分、匹配/裁决规则、K/预算/Tokenizer、源码/Release/配置与产物摘要、计分器修订、逐项匹配/TP/FP/FN、聚合及实验记录 |

不同来源的 null/unknown 与“不适用”分开记录。未经解析器/专家确认的关系不可借统一字段结构自动提升可信度。

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
- `tool_binding_id` + `tool_binding_revision`（执行申请固定确切修订）
- `environment_policy`
- `timeout_seconds`
- `retry_policy`
- `dry_run_supported`
- `version`
- `status`

## 8.2 Tool Binding

Tool Binding 应与业务语义独立版本化，以 binding_id + binding_revision 唯一寻址；已保存修订不可原地修改，变更生成新修订和 binding_digest。新修订启用不改写旧 Action Execution 的绑定。

字段包括：

- `binding_id`
- `binding_revision` / `binding_digest`
- `action_id`
- `tool_type`
- `tool_identifier` / `tool_version`
- `endpoint_reference`
- `credential_reference`
- `allowed_operations`
- `execution_environment` / `environment_revision`
- `resource_limits`
- `input_mapping`
- `output_mapping`
- `policy_id` / `policy_revision`

密钥只允许保存凭据引用，不允许在本体图中存储明文 Token。

## 8.3 Action Execution Record

每次操作保存：

- Execution ID。
- Task ID。
- Action Definition Version。
- Tool Binding ID/Revision 与 binding_digest。
- Caller Identity。
- Delegated User Identity。
- Target Baselines（逐仓库快照、Commit 与内容/范围身份）。
- 不可变 Execution Manifest、manifest_digest、approval_manifest_digest。
- Policy Evaluation Result。
- Approval ID。
- Start / End Time。
- status / status_revision / status_reason_code 及状态变更时间。
- attempt_id、稳定幂等键、tool_execution_reference 与派发记录。
- 取消请求人/时间/处理结果、last_confirmed_state/at、未知原因及核验记录。
- 已知副作用及产物摘要。
- Output Artifact References。
- Audit Trace ID。

审批记录单独保存 approval_id/revision、execution_id、manifest_digest、决定/身份/理由、approved_at 与 expires_at，不覆盖原审批。Execution Manifest 固定第6.6节的执行依据；凭据明文不能进入清单或摘要原文。

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

涉及目标代码基线的 search/assemble/impact 请求和对应 MCP 工具沿用 target_baselines[]；响应包含固定 Release 来源及逐仓 baseline_comparisons[]/baseline_status，遵守 FR-CTX-009。证据 ID 定位固定快照内容，不能动态指向最新文件。

## 9.5 Action API

- `POST /api/v2/actions`：注册 Action。
- `GET /api/v2/actions`：查询可见 Action。
- `POST /api/v2/actions/{id}/dry-run`：预执行。
- `POST /api/v2/action-executions`：申请执行。
- `POST /api/v2/action-executions/{id}/approval`：处理审批。
- `GET /api/v2/action-executions/{id}`：查询执行状态。
- `POST /api/v2/action-executions/{id}/cancel`：请求取消。

申请执行返回固定 execution_manifest/manifest_digest、Action/Binding 修订及 status_revision。审批提交 manifest_digest、决定和并发前置条件，服务端只批准当前申请的固定清单，摘要变化返回 APPROVAL_CONTEXT_CHANGED；不得用一次审批授权新 Binding。查询返回固定修订、审批绑定、当前状态/原因、last_confirmed_at、已有授权产物及合法后续操作。

cancel 返回请求是否受理、当前状态和取消结果；处理中为 CANCEL_REQUESTED，不能直接声称已经停止。终态重复请求返回原结果。EXECUTION_UNKNOWN 可受理并保存取消意图，但响应仍保持结果未知，不提供普通“失败重试”操作；核验由引擎内部恢复流程完成，不新增允许客户端任意修改结果的接口。过期审批返回 APPROVAL_EXPIRED，不复活旧批准。

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

## 9.7 来源选择、预检与快照 API

- `POST /api/v2/source-browse-sessions`：创建受控目录/Git 浏览会话，只返回授权范围内的目录/仓库引用。
- `GET /api/v2/source-browse-sessions/{id}/entries`：列出可访问条目，不接受绕过根范围的任意路径。
- `PATCH /api/v2/sources/{id}`：更新来源配置修订，不修改历史快照。
- `POST /api/v2/sources/{id}/preflight`：按配置版本执行预检。
- `GET /api/v2/source-snapshots`、`GET /api/v2/source-snapshots/{id}/diff`：查看快照及差异。

## 9.8 知识构建任务 API

9.1 的导入/抽取接口返回统一 job_id，统一契约管理配置快照和阶段。

- `GET /api/v2/knowledge-jobs`、`GET /api/v2/knowledge-jobs/{id}`：列表及详情。
- `POST /api/v2/knowledge-jobs/{id}/commands`：已注册的 START/PAUSE/RESUME/CANCEL/RETRY_FAILED，服务端校验当前状态与授权；不接受任意命令。
- `GET /api/v2/knowledge-jobs/{id}/confirmation-items`：待决定项。
- `POST /api/v2/knowledge-jobs/{id}/confirmation-items/{itemId}/decisions`：提交绑定 item revision 与输入摘要的决定。
- `GET /api/v2/knowledge-jobs/{id}/artifacts`：授权产物及失败片段。

## 9.9 模型配置 API

- `GET /api/v2/model-connections`、`POST /api/v2/model-connections`：查询/注册连接。
- `POST /api/v2/model-connections/{id}/revisions`：新增配置修订。
- `POST /api/v2/model-connections/{id}/validations`：验证指定修订/契约/受控样例，长时过程返回 Job ID。
- `GET /api/v2/model-connections/{id}/validations/{validationId}`：分项验证及限制。
- `GET /api/v2/model-routes`、`PUT /api/v2/model-routes/{taskType}`：查询/更新任务路由修订。

路由写入区分保存 DRAFT 与启用 ENABLED；启用返回按用途的 eligibility、validation_id/fingerprint 及限制，失败按 FR-MDL-003 返回稳定错误且不启用。知识任务创建/启动复用同一资格判定。validations 指定 VALIDATION/DIAGNOSTIC 及受控样例，产物隔离，不适用正式能力通过门禁但仍受测试权限/资源边界控制。响应不返回明文密钥；配置写入和模型使用权限分别检查。

## 9.10 代码拓扑与业务映射 API

- `GET /api/v2/code/entities`：按授权范围、快照、类型、ID/签名查询。
- `GET /api/v2/code/entities/{entity_version_id}`、`GET /api/v2/code/entities/{entity_version_id}/neighbors`：确切实体版本属性与受限入/出邻接。
- `GET /api/v2/code/relations/{edge_version_id}`：确切关系版本属性、端点版本、解析状态和来源。
- `GET /api/v2/code/subgraph`：指定快照与关系/深度/数量限制的子图。
- `GET /api/v2/code/entities/{entity_version_id}/business-bindings`：按指定 Release 或显式候选模式反查该确切版本的语义映射。

实体/子图响应必须带 source_snapshot_id/analysis_projection_id；stable_key 搜索须提供快照/投影或 Release，缺少上下文返回 CODE_VERSION_CONTEXT_REQUIRED。同一快照存在多个投影且上下文不能唯一确定时返回 CODE_PROJECTION_AMBIGUOUS 及授权可选项，调用者明确选择后继续，不隐式返回最新。映射写入复用 9.2 的候选关系/ChangeSet/审核接口，代码端点提交确切版本 ID，不另设绕过审核的直接绑定发布入口。

## 9.11 发布指针、客户端与偏好 API

- `GET /api/v2/release-pointers/current`、`PUT /api/v2/release-pointers/current`：查看/切换项目默认发布指针，写入含目标 Release、原因和 expected_revision。
- `GET /api/v2/agent-client-profiles`、`POST /api/v2/agent-client-profiles`、`PATCH /api/v2/agent-client-profiles/{id}`：受控档案及配置修订。
- `POST /api/v2/agent-client-profiles/{id}/validations`：按具体版本/身份执行接入验证；平台不能据此假定能控制用户本地客户端。
- `GET /api/v2/tool-traces/{traceId}`、`GET /api/v2/audit-events`：按范围查询轨迹/审计。
- `POST /api/v2/context-feedback`：记录与 Context/Trace/Release 绑定的反馈。
- `GET /api/v2/me/preferences`、`PATCH /api/v2/me/preferences`：语言、主题等用户显示偏好。

## 9.12 补充通用契约

保留 9.6 约定。错误返回 `code / message_key / params / trace_id`，可附授权后的字段错误、当前对象 revision 及可重试性；不以翻译消息作为机器契约。除原有错误外包含确认已过期、取消处理中、执行状态未知、模型验证缺失/失败/失效、审批上下文变化/过期、代码版本上下文缺失、来源基线未知、空匹配和预算不足；机器码与第6章保持一致。

取消返回请求状态而非虚假完成；重试、决定、配置修订、指针切换支持幂等和并发前置条件。所有列表、聚合/计数、元数据、证据和缓存命中都不得绕过现行访问控制。

---

# 10. 页面与交互需求

本章以七页主导航组织首期工作区；Dashboard、Action Studio 和完整评测保留独立产品能力，按阶段加入。各页同时适用 6.11 的国际化、主题、草稿及可访问性契约。详细字段与操作参考[界面能力详细设计](界面能力详细设计_V0.2.md)，视觉令牌参考[高保真 UI 视觉规范](高保真UI视觉规范_V1.0.md)。

## 10.1 首页 Dashboard

展示业务域/对象、已审核规则、业务代码映射、待审核/冲突/失效、更新任务、Action 及异常。指标来自实际授权数据，报告统计口径、范围与更新时间；暂无数据时显示空态，不使用示例数字假充实际效果。

Dashboard 未在 HF1 作为独立主页面交付，完整 V2.0 需求保留，可从工作台/任务页面进入或作为后续首页。

## 10.2 数据源管理页面

三个视图：代码仓、文档/DDL、来源快照。仓库列表展示来源方式、项目/业务域、分支/Commit、扫描范围、文档关联、连接/同步状态及最近任务。

接入采用“选择本地或 Git → 固定版本/范围 → 预检确认”向导。本地读取位置、Git 凭据引用、失败阶段和接入权限可辨识；返回上一步保留草稿。保存配置、预检、同步、创建抽取任务分别呈现，不自动串成已成功发布。

文档关联明确原始版本与适用快照；快照展示清单、任务/Release 引用及差异，历史快照只读。空扫描、缺依赖、认证失败和未授权分别给出处理入口。

## 10.3 本体拓扑工作台

左侧为项目/业务域/代码范围和类型过滤；中间为按视图组织的图；右侧检查器含属性、入/出邻接、源码/文档证据与历史/版本入口；底部关系记录可选择边查看属性。窄屏可将详情移至下方。

基础视图为业务本体、代码实体拓扑、业务—代码映射；全景和专业影响路径用于受限子图展示，高级规则/流程视图按 P1 交付。提供简短建模介绍，说明业务语义、代码事实、布局及可信状态的区别。

支持实体/签名/ID 搜索、基线/类型/关系/状态过滤、一跳展开、节点/邻接跳转、选中关联高亮、恢复范围、版本差异。跨视图保留实体身份，不能用业务逻辑链路图代替代码结构。缩放、拖拽、布局与大图返回限制需单独实现和验证。

## 10.4 任务与确认中心

统一列表提供类型、状态、来源/Commit、模型配置修订、发起人及时间筛选。详情含固定输入/配置、真实阶段、范围、有效候选、失败片段、冲突/待决定项、日志/产物/用量与 Trace ID。

操作按状态提供创建、确认启动、暂停/继续、取消请求、重试失败片段及查看候选。确认面板给出证据、选项、影响范围和理由输入；明确区分“任务继续”“知识批准”“发布生效”。无真实进度/Token 时显示阶段/未知，不伪造比例。

## 10.5 专家审核页面

候选列表区分规则/关系、待审核/批准/退回/冲突；主区对照条件、结果、范围、例外、不确定项和原始代码/文档。实现类/方法/测试的映射作为独立断言一起核对。

修改形成新修订；通过需证据、范围、端点与权限校验，退回须原因；标记待补证不解除发布阻断。审核版本、意见、证据与身份可追溯。批量处理逐项返回成功/失败；原型可先单条实现，完整 V2.0 保留批量要求。

## 10.6 Action Studio

保留 Action 模型、Input/Output Schema、Tool Binding、权限/风险、Dry-run、审批、执行与审计。审批面板展示固定 Action/Binding 修订、清单摘要、目标基线及有效期；状态区区分审批拒绝/过期、取消处理中、执行未知与真实终态，按合法转换提供操作，未知时提供核验进展而非直接重跑。HF1 的只读联调不是 Action Studio，也不证明测试执行、改代码、PR 写入或生产工具已接通。按 M3 交付受控动作闭环。

## 10.7 Code Agent 与 Context 联调页面

左侧为 OpenCode、Claude Code、DeepSeek Harness 的独立档案；右侧分连接设置、Context 调测、调用轨迹/反馈。客户端版本、传输/服务、受限身份、授权项目/Release、工具清单和验证状态清楚可见。

Context 输入需求、任务类型、业务域、逐仓 target_baselines、Release 策略、预算及深度；基线编辑区区分指定 Commit 与实际工作区、快照/内容身份和扫描范围。输出规则、实体/代码路径、完整条件/例外、证据、授权和逐仓 MATCH/MISMATCH/UNKNOWN；不能用一个绿色 Commit 标记代表整个工作区/多仓库一致。明确呈现越权、无匹配、过期证据、基线不一致/未知、预算不足及超时。

轨迹只显示实际调用/授权/产物，不展示内部思考或虚构调用。反馈进入独立队列。每种客户端“已支持”必须由具体版本兼容记录支持；配置示例和模拟握手均标为未完成真实验证。

## 10.8 评测与治理页面

保留知识/检索质量、Action 成功率、版本变化、异常审计与同条件 A/B 的完整评测要求。首个原型可输出评测报告，后续再形成独立评测界面；不能以演示通过数量替代黄金集或安全测试。

## 10.9 模型与抽取配置页面

提供连接与运行限制、任务路由、Prompt/Schema、能力验证四个视图。连接填写名称、协议/Adapter、地址、模型、凭据引用、数据边界；参数包含上下文/输出、超时、重试、并发和批次预算。

列表显示配置修订、按用途的分项验证/有效期及正式可用状态，区分草稿、未验证、能力失败、验证失效和可用；变更后旧验证不适用于新指纹。路由启用和任务启动被拒时显示未满足能力及修复入口，保存草稿不显示为正式可用。已有任务保留固定配置依据；受限验证/诊断入口与正式抽取分开，测试最小样例需授权，密钥不回显。模板/复杂路由可后续扩展，但不以界面选项冒称 Adapter 已兼容。

## 10.10 版本与审计页面

提供版本列表、ChangeSet/差异、发布预检和审计记录。区分候选、不可变 Release 和当前默认指针；查看旧版本时始终显示其来源基线。

切换/回退指针明确目标、知识变化与原因，校验权限和并发版本，不改写历史内容。审计可按身份/对象/操作/时间/Trace 查询；生产保留和防篡改按治理策略实现，原型事件列表不等同生产审计。

## 10.11 全局导航与偏好

七个首期主页面为数据源、模型、任务确认、知识审核、本体工作台、版本审计、Code Agent 联调。顶栏提供项目范围、语言、主题和真实/示例状态；角色和权限由服务端确定。

语言/主题及页面切换遵循草稿与偏好契约；关闭存在未保存内容时不静默丢弃。弹窗、表格、图、代码面板和错误反馈使用一致组件，视觉布局可调整但不能省略证据、条件、未知项或授权状态。

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

## 11.6 国际化、主题与可访问性验收

至少覆盖中文/英文 × 浅色/深色四种组合及跟随系统切换。七页、主要弹窗、空态、失败/禁用、长英文和图标签均可操作可读；检验焦点、键盘、状态通知及模态背景隔离。

语言/主题切换后，工作区、筛选、选中实体、未保存草稿与业务状态不变；源码/证据/签名/工具 ID 不被翻译或改写。偏好不是授权凭据，不能通过修改偏好升级角色。

## 11.7 配置与任务可复现边界

保留任务来源、配置、解析器/模型/模板/Schema 的版本、输入与产物摘要及用量口径，支持回放、比较和故障定位。模型随机性及提供方内部更新可能使输出不同，不承诺逐字确定性；依赖缺失、未知调用和失效证据必须显式报告。

## 11.8 演示隔离与真实验收

演示与真实环境的数据/连接/日志状态可辨识，禁止以模拟成功替代服务端验证。正式验收包含真实仓库、模型、发布、直接 API/MCP 权限、目标客户端和浏览器视觉/可访问性检查，不仅是 DOM 点击测试。

---

# 12. 数据存储与部署技术基线

## 12.1 二期部署与存储职责

M2 在用户本地 Docker 环境部署，以 Docker Compose 管理服务；首个真实验收客户端为宿主机上的 OpenCode。该决策不代表全 V2.0 的生产高可用、多租户和企业 SSO 已完成选型。

| 层次 | M2 选型 | 职责与边界 |
|---|---|---|
| 图数据库 | Neo4j Community | 业务模块、概念、规则、代码实体及映射/依赖的版本化属性图；真实关系查询和检索，不仅用于画图；完整 CPG 由解析产物保存 |
| 关系数据库 | PostgreSQL | 账号及授权、配置修订、任务/子任务、确认、规则权威修订、审核、发布清单/指针、客户端档案、审计和图同步记录；替换本地 SQLite 主业务存储 |
| 文件存储 | 受控本地目录与 Docker 挂载卷 | 源码/文档不可变快照、原文证据与解析产物；内容哈希和关联定位入库；保留未来对象存储适配接口 |
| 异步执行 | PostgreSQL 持久任务表 + 独立 Worker | 领取租约、心跳、检查点、指数退避、并发修订控制、人工恢复及幂等键；M2 首批不额外引入 Redis、RabbitMQ 或 Temporal |
| 检索 | 术语/别名 + Neo4j 全文索引 + 图扩展 | 先建立可评测基线，中文分词与代码标识符召回通过样例验证；Embedding/Reranker 按效果增量接入，暂不单建向量数据库 |
| 凭据 | 延续后端加密凭据库/环境变量引用 | 主密钥与密文数据分开保护并提供恢复说明，不写入镜像、仓库、日志或 Context；未来可对接企业 Secret Manager |

Neo4j Community 用于本地单实例二期验证；高可用集群、在线备份和更细的数据库级治理能力另行评估 Enterprise 或等效方案，不能按社区版默认具备这些能力设计验收。应用层仍必须执行项目/来源/Release 级授权；数据库运行账号不代替终端用户身份。

### 12.1.1 权威记录与跨存储发布一致性

PostgreSQL 是规则修订、审核结论、Release Manifest、发布状态和默认指针的权威存储。Neo4j 是可从权威记录及快照重建的版本化图投影，不独立决定发布成功。原始内容以固定文件快照为依据；图中保存证据 ID、路径、范围和内容摘要，不保存一份随工作区变化的“最新原文”。

不假定 PostgreSQL 与 Neo4j 拥有统一原子事务。发布流程必须：

1. 在 PostgreSQL 固定发布清单、投影身份和待同步记录；采用事务内写入的 Outbox 或具备等效恢复能力的同步任务账本。
2. 按 Release ID 在 Neo4j 幂等构建不可变投影，创建/核验约束、索引、节点、边端点和清单摘要，确认可查询。
3. 在 PostgreSQL 事务内核对原清单及现行发布权限，确认发布并以预期修订切换默认指针。
4. 查询先解析固定 Release，再按该 Release 隔离读取图；发现投影不可用或摘要不符时明确报错，不自动返回其他版本。

图写入/索引失败不切指针，旧版本继续有效；中断可以恢复，无引用暂存投影可审计回收。验收包含写入中断、同步重复、图不可用和并发指针切换，不以“两边依次写完”代替一致性保证。

### 12.1.2 图模型与查询边界

原型最小增量包含 `BusinessModule`、`BusinessConcept`、`BusinessRule`、代码实体及证据引用；具体字段/类型约束沿用第7章，在详细设计中完成映射。至少支持模块归属、业务规则到类/方法的 `IMPLEMENTED_BY`、代码声明/调用关系与证据追踪。业务前置、依赖或冲突边必须有明确来源/人工确认，不由节点布局或名称相似度推断为事实。

图必须固定项目、Source Snapshot、Analysis Projection、实体版本和 Release，边端点指向确切版本。候选图与已发布图按身份/状态隔离；候选预览可读取已校验但未审核的结果并披露覆盖缺口。正式 Agent 查询默认仅使用已审核发布知识，禁止将候选图默认为可信发布版本。

查询通过服务端参数化 Cypher 和受控查询模板执行，限制跳数、节点/边数、执行时间及响应预算；M2 不开放模型生成任意 Cypher 后直接执行。UI 支持按业务模块/实体/任务范围查询、邻居展开及路径检查，不将完整大图一次下载到浏览器。部分结果必须显示实际数量、总量或可用范围及截断说明。

## 12.2 二期应用技术栈

| 部分 | M2 选型 | 实施约束 |
|---|---|---|
| 前端 | React + TypeScript + Cytoscape.js | 延续一期界面、中英文和浅深主题；组件库按需引入 Ant Design，不为选型整体重写已有界面 |
| 应用后端 | Python + FastAPI + Pydantic | 模块化单体 + 独立 Worker；REST/MCP 共用授权、检索、版本和证据服务 |
| 数据访问 | SQLAlchemy + Psycopg；Neo4j 官方 Python Driver | PostgreSQL 使用显式模型/约束和版本化迁移脚本；不能仅替换连接串后宣称完成 SQLite 迁移 |
| 模型接入 | 延续 Model Adapter 与 OpenAI-compatible 接口 | 可用现有已授权模型；私有化是后续部署选项，不强制本地另部署大模型；用途、能力门禁、预算和凭据管理统一放配置中心 |
| 结构与证据 | JSON Schema 校验 + 原文/实体范围校验 | 不因为引入图数据库、模型修正建议或任务重试而降低证据要求 |
| 代码解析 | 开源 Joern CPG 实现 CodeGraph；javac 保留显式兼容模式 | 独立内网 Docker 服务，固定镜像/导出器/适配器修订；源码只读，不执行仓库构建或自动下载依赖；完整 CPG 保存为产物，Neo4j 保存必要事实投影；旧规则不自动改绑 |
| MCP | 官方 MCP Python SDK | 传输/工具版本按 OpenCode 实际安装版本验证后固定，客户端不直连数据库 |
| 运行 | Docker Compose | 显式镜像/依赖版本及校验信息，禁止正式验收使用浮动 latest；版本在环境兼容性验证后锁定 |

M2 首批不引入通用 Agent Runtime、强制 LangChain/LangGraph 层、独立向量库和分布式工作流平台。后续仅在召回效果、任务规模或跨服务编排需求有证据时，通过 ADR 评估增加组件。该边界不取消完整 V2.0 的扩展要求。

### 12.2.1 Context 与 OpenCode 接入

M2 先交付四个只读工具：`search_business_concepts`、`trace_business_to_code`、`assemble_business_context`、`get_evidence`。工具复用 REST 服务，不形成第二套权限与业务逻辑，不授予修改代码或发布知识的能力。

首个 OpenCode 验收采用本地 stdio 适配进程：由 OpenCode 在宿主机启动轻量适配器，通过已配置地址调用 Docker 中的受保护平台 API，复用同一服务层。身份由预配置的受限 Agent 凭据绑定，不接受工具参数自报角色。适配进程退出不终止 API/Worker 或导致持久任务丢失。客户端档案记录安装版本、配置、工具 Schema 版本及验证结果，不凭产品名称宣称兼容。

Streamable HTTP 作为后续内网共享服务接入路径，在对应客户端/SDK 实测后启用，并补齐认证、Origin 校验和会话隔离。其他目标客户端 Claude Code、DeepSeek Harness 保留兼容矩阵，首个 OpenCode 验收不等于三者均已验证。

Context Package 固定 Release、证据来源和 `target_baselines[]`，返回逐仓 MATCH/MISMATCH/UNKNOWN。默认仅基于已发布知识，先做授权过滤再检索及图扩展；以第13章 EP-1 的 K/Token Budget 和黄金集为验收依据，明确缺失、截断和未知依赖。全文/术语基线达成后，再比较引入 Embedding/Reranker 的收益，不能仅按“接上向量库”认定语义检索完成。

## 12.3 本地 Docker 部署与运行要求

Compose 至少定义 `api`、`worker`、`postgres`、`neo4j` 四类服务，前端构建产物由 API 或独立 Web 服务托管。宿主机 OpenCode/stdio 适配进程不属于必须常驻的后端服务。

- 本地 UI/API 默认仅绑定宿主机回环地址；PostgreSQL、Neo4j 默认仅在 Compose 内部网络可达。调试端口如确需映射，应明确仅绑定回环地址。浏览器及 OpenCode 不持有数据库管理凭据。
- API 与 Worker 使用同一 PostgreSQL、来源快照卷和明确配置；凭据主密钥可读取范围按需收窄。数据库、图数据、快照及凭据库持久化，容器重建不能清空业务数据。
- 配置健康检查、服务重启策略及应用侧重连/退避；容器先启动不等于数据库已就绪。Worker 领取租约及幂等控制防止重启后重复执行，不自动重放结果未知的模型请求。
- Neo4j Community 使用适用的停机导出/卷备份方案；PostgreSQL、Neo4j、文件快照与凭据恢复步骤必须明确。恢复后按固定清单核验，不用单次卷拷贝宣称跨存储一致备份。
- 迁移前备份一期 SQLite 与快照/凭据；迁移保留原 ID、修订、规则状态、检查点、证据引用和审计，并比对数量与内容摘要。候选不得在迁移时变为已审核或已发布。验证成功前保留原环境的回退路径，避免新旧环境同时消费同一任务队列。
- 对用户实际主机架构、Docker 资源、Neo4j 镜像、解析器/JDK 和 OpenCode 安装版本进行环境验证；最低资源及性能配置以实测记录为准，不在未测量时承诺固定容量或响应时间。

## 12.4 二期实施顺序与技术验收

1. **基础设施与迁移**：本地 Compose 可重复启动；PostgreSQL/Neo4j 真实连接，完成一期数据迁移核验、备份/恢复及服务重启检查。
2. **真实图查询**：完成必要本体元模型、版本化投影与发布同步，UI 查询实际 Neo4j；候选预览与正式发布知识隔离，支持范围查询/邻居展开。
3. **Context 检索**：术语/全文召回、图扩展、证据组装与 Token Budget；固定 Release 和目标基线，执行权限与版本一致性测试。
4. **OpenCode 闭环**：以真实安装版本调用四个只读 MCP 工具，对真实变更需求取得规则、代码和证据，并按第13章协议评测；未通过项记录限制，不以模拟调用代替验收。

文档确认的技术路线、实际依赖锁定、已实现功能和真实验收结果必须分别记录；本章选型确认不能被报告为二期已经部署或验收完成。

---

# 13. POC 评测和验收要求

## 13.1 测试范围

建议选择：

- 一个银行贷款或授信业务子域。
- 2–3 个相关 Java 仓库。
- 一组对应的业务规约与设计文档。
- 30–50 个真实或仿真的研发任务。
- 由专家人工标注的概念、规则、流程、代码映射和相关上下文黄金数据集。

黄金数据集需覆盖简单、中等和复杂场景，包含跨模块关系及冲突知识。开发/Prompt 调试集与独立验收集分开并冻结划分；已用于调参的样例不得再作为独立验收结果，近重复/同规则变体须分组划分以避免泄漏。完整 POC 的2–3仓库/30–50任务不直接替代第2.3节单仓原型范围；原型样本量另报，不外推为完整 POC 结论。

## 13.2 初始验收指标

| 指标 | 建议目标 | 说明 |
|---|---|---|
| 业务概念 Precision | ≥ 85% | 在人工标注集上评估 |
| 业务规则 Recall | ≥ 80% | 按第13.6节完整规则一对一匹配，不以仅提到规则计分 |
| 业务到代码映射 Precision | ≥ 85% | 映射是否正确 |
| 已批准规则有效证据覆盖率 | 100% | 不允许无证据发布 |
| 正式知识审核覆盖率 | 100% | 必须有审核记录 |
| 上下文关键知识 Recall@K | ≥ 85% | 验收默认 K=10、Context 预算4000 Token，按第13.6节冻结协议 |
| 未授权 Action 阻断率 | 100% | 安全强制要求 |
| 高风险动作审批覆盖率 | 100% | 安全强制要求 |
| 生产业务 Action 执行次数 | 0 | V2.0 范围禁止 |

以上抽取质量目标为初始门槛，统计时必须报告样本量、领域分布及置信区间或误差范围。进入验收前按第13.6节冻结目标与计分协议；若环境/样本约束需要调整，先发布新协议并重跑对照，不允许看过验收输出后修改分母、K 或判分规则。无测量结果时不得宣称已达标。

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
- 工具超时但实际执行状态未知、取消与完成竞态、队列审批过期。
- 仅升级 Tool Binding、Action 版本不变时旧批准的使用边界。
- 同 Commit 的不同工作区内容/解析投影与历史 Release 查询。
- 多仓库部分基线不匹配/未知及只给 SHA 的脏工作区。
- 模型仅连通通过但必需能力失败，或运行指纹变更后未重验。
- 规则漏例外/范围、重复拆分与验收集泄漏。
- 图谱查询出现跨项目数据泄漏风险。

---

## 13.5 高保真对齐的补充验收矩阵（V0.10 保留）

以下是契约用例，不是当前产品实测结果；示例数据与 DOM 回归不能替代真实系统验收。

| 用例 | 触发与预期 | 需求 |
|---|---|---|
| AC-UI-01 | 七页及主要弹窗中英 × 浅深可读可操作，系统主题切换可用 | FR-UI-001、FR-UI-002、FR-UI-004 |
| AC-UI-02 | 编辑草稿/选中实体后切换语言主题或向导步骤，数据不丢；证据原文不变 | FR-UI-001、FR-UI-003 |
| AC-ING-01 | 本地未授权/越界路径拒绝；指定 Commit 与未提交工作区内容不混淆 | FR-ING-006 |
| AC-ING-02 | Git 无权限不列仓库，认证/依赖/空扫描有明确失败状态；相同快照幂等 | FR-ING-007、FR-ING-008 |
| AC-CG-01 | 类/接口/方法属性、入出边、调用/读写关系和源码位置可追溯 | FR-CG-005、FR-CG-006、FR-ONT-007 |
| AC-CG-02 | 模块→类、规则→方法/测试可正反定位；无证据映射不能发布 | FR-ONT-008、FR-GOV-007 |
| AC-CG-03 | 动态目标不确定时显示解析限制，不生成确定调用；子图截断有说明 | FR-CG-005、FR-ONT-007 |
| AC-JOB-01 | 配置/分支变化不改变旧任务；新任务使用新配置清单 | FR-JOB-001、FR-MDL-003 |
| AC-JOB-02 | 等待确认、暂停、取消请求与最终完成状态不同，重启后检查点/决定不丢 | FR-JOB-002、FR-JOB-003 |
| AC-JOB-03 | 同一次决定过期/重复提交被控制；确认继续不等于批准或发布 | FR-JOB-003、FR-GOV-007 |
| AC-JOB-04 | 仅失败片段重试不重复有效事实；状态未知先核验；非法输出不入候选 | FR-JOB-002、FR-JOB-004 |
| AC-MDL-01 | 影响运行指纹的配置修改导致旧验证不适用；仅展示元数据修改可按同指纹复用验证；Adapter 不支持参数时拒绝 | FR-MDL-002、FR-MDL-003 |
| AC-GOV-01 | 审核绑定修订；修改范围/例外/映射后不能沿用旧批准；已发布版本只读 | FR-GOV-007 |
| AC-GOV-02 | R2 默认含新规则，切回 R1 不再默认返回；切换理由与并发前置条件必需 | FR-GOV-008 |
| AC-GOV-03 | 图投影/索引失败、指针切换前后进程中断，不暴露部分发布内容 | FR-GOV-008、第12.1.1节 |
| AC-CTX-01 | 每个宣称支持的客户端版本有真实握手、工具、权限与证据验证记录 | FR-CTX-008 |
| AC-CTX-02 | 越权/预算不足/无匹配/超时/失效/基线不一致分别返回稳定结果码 | FR-CTX-009 |
| AC-CTX-03 | 预算不足不截断完整条件/例外；默认指针中途变化不混用两个版本 | FR-CTX-004、FR-GOV-008 |
| AC-SEC-01 | 权限撤销后历史版本/缓存/图计数/直接 API/MCP 都不能继续泄漏数据 | FR-CG-006、FR-GOV-008、第4章 |
| AC-AUD-01 | 配置/任务确认/审核/发布/指针/客户端操作和反馈有授权审计记录 | FR-GOV-009、FR-CTX-010 |
| AC-DEMO-01 | 模拟连接、示例统计和设计日志不显示为真实验证通过 | FR-UI-005、第11.8节 |

---

## 13.6 版本化评测协议与计分规则

评测前固定 EvaluationProtocol（初版 EP-1），并与黄金集版本/开发验收划分、授权范围、来源快照/代码投影、Ontology Release、模型/Adapter/Prompt/Schema/路由、检索/重排配置、Tokenizer 和计分器版本组成不可变 Evaluation Run Manifest。保留输入、原始输出及摘要、逐项匹配/裁决、TP/FP/FN 和运行错误。验收协议的变更生成新版本；旧结果不重贴新协议标签。

**黄金单位与完整匹配：**

- 概念以黄金概念 ID 为单位，名称经冻结别名表/规范化表匹配，并核对定义、业务域与关键属性；同名不同义不能算同一概念。映射以“业务断言修订 + 关系类型 + 确切代码版本端点”为单位，专家提前列出语义等价的允许实现端点；仅命中同名类/方法不算正确。
- 规则以一条可独立判断真假的完整断言为单位，黄金集记录条件/比较符/数值、结果、适用范围、全部例外和有效证据及允许的等价表达。所有必需项及语义须一致才计完整 TP；缺例外、漏范围或不等号错误的预测为 FP，对应黄金规则为 FN。无某字段时黄金集须显式标“不适用”，不临时降低要求。部分字段命中可另报诊断分，不能抵作完整规则 TP。
- 使用冻结的匹配器和专家裁决表建立预测与黄金单位一对一匹配；同一预测不能匹配多条黄金单位，同一黄金单位也不能被多次计分。重复预测仅一条可为 TP，其余计 FP；拆成多条而没有任何一条完整表达的规则不得合并后虚增 TP，合并/拆分边界在看验收输出前写入标注指南。
- 证据等价集合在黄金集中预先标明（如等价实现/测试位置），不可临时凭模型自报判真。无法自动判定的语义由两名评审独立标注，分歧交业务负责人裁决并保留依据；黄金标注自身存疑的样例在跑分前隔离，计数量与原因，不在看到失败后删除。

**固定 K、预算和聚合：**

- 初版 Context 主验收为 Recall@10，K=10，计最终返回包中前10个有序知识条目；每个条目占一个 K 位置，条目内部的分块/证据/代码附件不另占位置。分离的重复条目仍占各自返回位置；黄金知识 ID 仅用于一对一判真，不预先压缩重复位置。同一概念/规则最多命中一次，规则只有完整条件/范围/例外及有效引用一同可得才算命中；不能以碎片命中替代完整规则。
- Context 总预算固定4000 Token，包含返回包正文、结构化字段和引用；Tokenizer/版本或不可得时的固定估算算法随协议记录，双方使用同一口径。预算裁剪后的最终返回包计分，不能拿裁剪前召回代替用户实际获得的知识。黄金相关单元多于 K 或预算不足时仍照实计 FN，并另报容量限制，不偷偷改变 K、分母或截断完整规则。
- 概念/规则/映射及 Context 的主指标均用 micro 汇总：Precision=ΣTP/(ΣTP+ΣFP)，Recall=ΣTP/(ΣTP+ΣFN)，F1 按二者计算。另报逐任务/业务域/复杂度 macro 均值和各层样本量，不用更高的某一口径替代主指标。分母为0时报 N/A 和样本数，不能按100%达标；样本不足不得外推总体。
- 有答案任务中的未知/拒答、错误、超时和预算不足不算正确，未返回的黄金单位计 FN；错误/无依据的已返回单位仍计 FP。黄金无答案任务单独报告正确拒答率、错误回答率及数量，不混入 Recall 分母抬高得分；授权拒绝、安全测试和基础设施失败分组披露，不静默删除失败样本。

**复现与报告：**

证据覆盖率以冻结清单中所有已批准规则修订为分母，审核覆盖率以正式 Release 中所有业务断言/映射修订为分母，未授权阻断率以测试中未授权执行申请为分母，高风险审批覆盖率以实际派发的高风险执行为分母逐项核验；比率分母为0报 N/A，不能声称通过。生产业务 Action 执行次数单独报告实际事件计数，须为0。报告原始计数、领域分布、逐项失败与不确定性；样本量不足以稳定估计置信区间时明确说明限制。A/B 使用相同独立任务集、源码内容、模型/工具版本、资源/时间上限和重复次数；默认每任务3次，记录可得种子与失败，报告均值/离散程度，不挑最好一次。更小原型可先做单次流程验证并如实标注，不能宣称完成统计效果验收。

## 13.7 第二轮评审的补充验收矩阵（V0.11）

以下用例与13.5节共同构成契约验收；仍须按 M1/M2/M3 对应阶段真实执行。

| 用例 | 触发与预期 | 需求 |
|---|---|---|
| AC-CG-04 | 同 Commit 的 S0/S1 仅方法体不同，两版本 ID/源码/映射各自可查；同内容不同解析修订生成独立投影；发布 S1 不改变历史 R1，重复处理幂等 | FR-ING-008、FR-CG-002、FR-CG-005、FR-GOV-008 |
| AC-CTX-04 | A/B 双仓基线中 A 匹配、B 不匹配，逐仓正确列出原因且整体不匹配；缺 B/范围不足为 UNKNOWN，无权来源不泄漏 | FR-CTX-003、FR-CTX-009 |
| AC-CTX-05 | 相同 SHA 的不同未提交内容为 MISMATCH；WORKSPACE 只给 SHA 为 UNKNOWN；COMMIT 匹配不宣称实际工作区一致，高风险动作不得忽略未知/差异 | FR-CTX-009、FR-ACT-004、FR-ACT-008 |
| AC-ACT-01 | 审批完成后 Manifest/摘要不变，Approval Record 绑定原摘要；Action V1/Binding B1 获批，仅发布 B2 后原批准不能执行 B2；B1仍可用且通过现行授权才可执行 B1；强制换B2需新清单/申请/审批 | FR-ACT-003、FR-ACT-008、第8章 |
| AC-ACT-02 | 审批拒绝为 APPROVAL_REJECTED，排队至审批过期为 APPROVAL_EXPIRED，工具调用为0；重新申请不复活旧批准，旧策略不能绕过现行授权撤销 | FR-ACT-006、FR-ACT-008 |
| AC-ACT-03 | 派发前取消先赢则不调度，确认停止才 CANCELLED；取消与成功并发以核验结果为准，迟到/重复取消不能覆盖 SUCCEEDED 或重复产生副作用 | FR-ACT-006、第9.5节 |
| AC-ACT-04 | 工具收到请求但响应丢失为 EXECUTION_UNKNOWN；未知期间可保存取消意图，重启后仍在，确认仍运行则进入 CANCEL_REQUESTED；重复投递不重跑，核验确认成功且不可重试仍入 SUCCEEDED、调用次数为1；无法核验时保留未知及产物 | FR-ACT-006、第8.3节 |
| AC-MDL-02 | 连通成功但抽取必需的结构/证据能力失败，允许保存路由草稿，拒绝启用/正式任务，返回用途及失败原因；Embedding不因规则JSON未测而误阻断 | FR-MDL-002、FR-MDL-003 |
| AC-MDL-03 | 配置/契约指纹变化或验证到期，旧结果不能授权新派发；旧固定修订仍有效可继续，停用/授权撤销不能靠历史通过绕过 | FR-JOB-001、FR-MDL-003 |
| AC-MDL-04 | 未验证配置可在测试权限/样例/预算内执行受限验证；验证产物不能作为正式候选或发布，也不能借诊断模式越权发送源码 | FR-MDL-001、FR-MDL-003 |
| AC-EVAL-01 | 同一输出按协议由两名评审复算，TP/FP/FN一致或有明确裁决；漏例外规则为FP+FN，重复/拆分不能重复得分，未知/拒答不抬高Recall | FR-EVAL-001、第13.6节 |
| AC-EVAL-02 | 固定 K=10、4000 Token、Tokenizer、黄金集/Release/模型等版本可复算最终包指标；开发与验收无泄漏，调整协议生成新版本，失败与无答案单列不删样本 | FR-EVAL-002、FR-EVAL-004、第13.6节 |

---

# 14. 版本规划与开发优先级

## 14.1 M1：本体建模最小闭环

目标：先实现可信的业务本体生成。

交付：

- 本体 Schema。
- CodeGraph Adapter 与来源/解析能力验证。
- 代码与文档解析。
- LLM 候选知识抽取。
- 节点/关系入库。
- 基础业务本体、代码实体拓扑、跨层映射与属性检查。
- 知识任务/中间确认、证据与修订绑定专家审核。
- 不可变发布、预检/默认指针与操作审计。
- 模型连接/参数、固定任务配置与双语主题基础门户。

验收场景：

从一个信贷 Java 项目中抽取额度校验规则，审核后展示其业务关系和代码实现。

## 14.2 M2：AI Coding Context 闭环

目标：让 CodeAgent 真正使用本体。M2 已确认部署在用户本地 Docker，首个真实验收客户端为 OpenCode；技术及迁移基线见第12章。

交付：

- 业务语义检索。
- 图谱与代码联合查询。
- Context Package。
- MCP Server 与客户端档案；OpenCode 作为首个真实验收客户端完成原型闭环，其他目标客户端按版本逐项维护兼容矩阵。
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

### 14.5 刷新后的阶段与实施门槛

- M1：单仓库真实接入/快照、逻辑实体/版本实例与分析投影分离、模型基本配置及按用途门禁、任务/确认、规则与映射审核、基础双视图/映射、发布和双语主题。原型可固定简单路由/Schema；不能虚报完整后台治理已交付。
- M2：只读 Context/MCP 与指定客户端联调、逐仓目标内容基线及异常契约、版本指针一致性、固定协议的检索评测。原型先四个只读工具，完整工具表随 M2/M3 对应能力交付。
- M3：原有 Action Registry、Tool Binding、授权审批与隔离执行保持，落实绑定修订/清单审批、取消与未知恢复的闭合状态机；不因原型未画独立 Action 页面而删除。
- M4：原有完整增量演进、标准映射及持续质量治理保持；原型提前验证一次第二 Commit 的失效提示，不等于完整增量体系完成。

每个工作包明确“界面已设计 / 示例已演示 / 后端已实现 / 真实验证通过”状态。重估工作量前不得沿用旧原型估算作为全范围交付承诺。

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
| EP17 | 本地/远程来源向导、受控浏览、预检与快照清单 | P0 |
| EP18 | 模型连接/参数、配置修订、验证及简单任务路由 | P0 |
| EP19 | 知识构建任务、检查点、确认、取消与失败片段重试 | P0 |
| EP20 | 代码实体/关系检查器及模块到类/方法的跨层映射 | P0 |
| EP21 | OpenCode / Claude Code / DeepSeek Harness 档案、按版本联调与轨迹 | P0 |
| EP22 | 中英文、主题、草稿、通用状态及可访问性 | P0 |
| EP23 | 发布清单、默认指针并发/缓存、跨存储故障恢复与平台审计 | P0 |

EP17～EP23 是既有 Epic 的补充/拆分工作包，实施时明确归属并去重，不将同一工作重复计价。每个 Epic 在进入开发前进一步拆解为：

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
| 产品范围扩张 | 区分七页设计、最小闭环与完整 V2.0，按工作包重估 |
| 模型配置漂移 | 不可变修订、验证指纹及任务配置快照 |
| 任务状态失真 | Worker 确认、持久检查点、请求与结果分离 |
| 跨库发布不一致 | 清单与投影先验证、指针最后生效、失败恢复与幂等 |
| 客户端兼容误判 | 按客户端/安装版本记录真实验证，不以模拟成功替代 |
| 双语/主题导致语义改变 | 稳定枚举与标识、原文保留、四组合及草稿回归 |

---

# 17. 待确认的设计决策

以下记录技术决策及待验证边界；M2 已确认项以第12章为准，尚未验证项不得视为已完成。

**ADR-001：CodeGraph 集成方式**

二期采用开源 Joern CPG 作为 CodeGraph 引擎。已在本地 ARM64 Docker 及实际 Java 快照完成解析与 Neo4j 查询验证；当前锁定 Joern 4.0.662 与镜像内容摘要，详见部署记录。完整 CPG 在解析产物卷保存一份，Neo4j 仅存必要事实。方法/类/控制结构保持有效原始位置；无法定位的合成实体不伪造范围，类 AST 覆盖行不代表完整类上下文。调用区分静态目标、外部声明、运算符及 UNKNOWN；静态目标不承诺运行时派发正确，数据流/控制流专项分析及复杂依赖项目准确率仍须单独验收。新任务固定引擎，失败不静默退回 AST；已有审核/证据继续绑定原投影。

**ADR-002：本体核心存储**

M2 确定采用 Neo4j Community + PostgreSQL，本地 Docker Compose 部署。PostgreSQL 保存规则/审核/发布权威记录，Neo4j 承担可重建的版本化本体与代码关系投影和查询；约束及版本能力边界见12.1节。

**ADR-003：本体语义标准**

平台原生使用轻量级元模型；FIBO / OWL 作为扩展和交换标准。

**ADR-004：代码分析范围**

V2.0 首期以 Java 为主，COBOL / IBM i 等通过独立解析器扩展。

**ADR-005：模型部署**

完整 V2.0 优先支持银行内网私有化模型；M2 沿用现有已授权模型及统一 Model Adapter/OpenAI-compatible 接口，不强制在本地 Docker 中部署大模型。

**ADR-006：Action 执行环境**

采用隔离工作区或容器，不直接对生产系统执行写操作。

**ADR-007：Agent 平台对接**

优先通过标准 MCP 对接目标客户端及研发数字员工平台，不在本平台重复开发通用 Agent Runtime。OpenCode、Claude Code、DeepSeek Harness 分别维护配置/兼容记录；M2 首个验收客户端已确定为 OpenCode，本地 stdio 适配器调用容器中的受保护 API；安装版本、身份集成和工具行为在技术验证中锁定。Streamable HTTP 按12.2.1节后续接入。

**ADR-008：版本一致性策略**

所有研发上下文包必须固定 Ontology Release、逐仓 Source Snapshot/分析投影及 target_baselines[]；按 FR-CTX-009 比较有效内容和范围，返回逐仓及总体 MATCH/MISMATCH/UNKNOWN。Commit 相同不等于脏工作区内容相同；差异或未知可用于只读诊断，但有关基线未确认前阻断高风险动作。具体采集/核验方式在真实客户端 Spike 中验证。

---

**ADR-009：发布一致性的权威记录**

权威记录确定在 PostgreSQL；采用事务内同步账本/Outbox、按 Release 构图核验、指针最后切换的流程，见12.1.1节。通过进程中断/索引失败/并发切换验证；不假定 PostgreSQL 与 Neo4j 具备跨库事务。

**ADR-010：本地接入与任务持久化**

M2 为用户本地 Docker 模式，采用 PostgreSQL 持久任务表 + 独立 Worker，暂不引入独立队列。目录授权、未提交内容、Worker 租约/检查点及取消/未知恢复仍须按原契约验证；迁移、持久卷、健康检查及重启策略见12.3节。

**ADR-011：国际化与知识原文边界**

UI 使用稳定资源键，术语译名与原始证据分开；语言/主题偏好与业务状态、权限解耦。精确颜色与像素按视觉规范迭代，不改变本文契约。

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

- 银行业务本体图、代码实体关系拓扑及业务模块/规则到类/方法/测试的映射。
- 业务概念和规则目录。
- 业务到代码实现映射。
- 可信证据链。
- 本体修订审核、任务确认、发布清单与默认指针审计记录。
- 模型配置/任务快照、七页双语主题门户及真实客户端兼容性记录。
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

**文档状态：V0.11，第二轮评审修订稿。已补齐六项评审问题的规范性契约及验收用例，不代表真实功能、性能、客户端兼容或生产安全验收完成。正式锁定 V1.0 研发基线前，应完成元模型/映射与新增契约评审、实际仓库/模型/客户端技术 Spike、安全威胁建模和首批黄金样例验证。**
