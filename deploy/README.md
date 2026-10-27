# M2 本地 Docker 开发环境

当前为二期第二增量，保留一期 SQLite 服务（8000）作为回退；二期独立环境默认在 http://127.0.0.1:8002 。不能同时让两套环境执行同一批业务任务。

## 启动与迁移

在项目根目录运行。需要本机 Docker、Docker Compose、项目 Python 和 OpenCode。依赖使用 `requirements.lock` 和 `frontend/package-lock.json`；基础镜像使用固定版本/内容摘要。

```sh
.venv/bin/python scripts/setup_m2.py
docker compose --env-file .data/m2/compose.env up -d postgres neo4j
docker compose --env-file .data/m2/compose.env build api codegraph
```

迁移前确认一期没有 QUEUED/RUNNING/WAITING_RETRY 等活跃任务。备份必须位于源目录之外，每次使用新的路径，不覆盖历史备份。

```sh
.venv/bin/python -m backend.migrate export --source .data --destination .backups/m2-initial-20261023
docker compose --env-file .data/m2/compose.env run --rm --no-deps -v "$PWD/.backups/m2-initial-20261023:/backup:ro" api python -m backend.migrate import --backup /backup --runtime /data
docker compose --env-file .data/m2/compose.env up -d api
docker compose --env-file .data/m2/compose.env --profile worker up -d worker
```

导入需空目标数据库或与备份完全一致的数据；逐条保留 ID、revision、状态、原始 payload，校验记录聚合摘要、快照内容及文件哈希。不同的文件、主密钥或记录均拒绝覆盖。导入前不要先启动 API，以免自动初始化管理员后使目标数据库不再为空。迁移保留一期记录表契约；领域表进一步规范化仍待后续迁移，不宣称已完成全部业务表重构。

`schema_migrations` 记录版本，PostgreSQL DDL 使用事务级锁，API/Worker 使用连接存活检查。凭据和数据放在忽略提交的 `.data/m2/` 下，备份包含敏感数据及凭据主密钥，应限制访问并另行离线保护主密钥。不得将环境文件或完整 `docker compose config` 输出发到聊天/日志。

## OpenCode

```sh
.venv/bin/python scripts/configure_opencode.py
OPENCODE_CONFIG="$PWD/.data/m2/opencode.json" opencode mcp list
OPENCODE_CONFIG="$PWD/.data/m2/opencode.json" opencode
```

本机 OpenCode v2.0.32 的后台服务按项目 `opencode.json` 加载；脚本同时生成该忽略提交的本地文件和 `.data/m2/opencode.json`，保留已有其他 MCP 条目，不修改全局配置。若后台实例默认端口冲突，使用单独 XDG 数据/状态/配置目录并设置专属服务端口，本次验证为49379。宿主机 stdio 适配器通过 8002 调用 Docker API，使用只读 Agent 令牌文件，禁止使用管理员令牌代替。四工具为 `search_business_concepts`、`trace_business_to_code`、`assemble_business_context`、`get_evidence`。MCP 工具连接验证与真实需求质量评测分别记录。

正式 Context 仅读取已审核发布 Release。原有候选规则不会在迁移时自动批准；没有发布版本时返回 `NO_PUBLISHED_KNOWLEDGE`。查询固定发布清单，证据仅读取被该版本引用的具体范围。当前为单项目授权，已实现术语别名与 Neo4j 全文融合；不能视为多租户 ACL 或语义检索质量已验收。

Token 使用 `cl100k_base` 明确口径，非 DeepSeek 等所有模型的精确原生计数；不能与不同 Tokenizer 的评测直接混用。声明的内容摘要相同不能证明当前脏工作区相同；未核验目标返回 UNKNOWN。已授权固定快照对比可 MATCH；目标内容声明不同可诊断为 MISMATCH，禁止据此执行写操作。

## 运行、重建和回退

数据库端口仅在 Compose 内部网络可达，API 仅绑定本机回环。重建应用容器保留 PostgreSQL、Neo4j 命名卷和 `.data/m2/runtime` 绑定目录；`down -v` 会删除数据库卷，禁止用于普通重启。

```sh
docker compose --env-file .data/m2/compose.env ps
docker compose --env-file .data/m2/compose.env restart api worker
```

图投影保存完整内容 JSON 和版本图 ID；每次查询按摘要核验图准备状态，不可用时明确失败。候选全图与正式 Release 隔离；邻居展开由 Neo4j 查询实际存储关系。发布同步账本记录 PENDING/READY/FAILED/COMMITTED；未完成发布不切指针，重试发布可复用已准备图。暂存图清理和自动同步恢复管理界面尚未实现。

备份：停止接收写入并暂停/停止 Worker 后，对 PostgreSQL 用 `pg_dump`，对 Community Neo4j 停止图服务后导出/备份对应卷，复制快照与加密凭据；记录共同清单/版本摘要。恢复演练应在新环境中执行，不覆盖现有卷。不能用单个卷的快照宣称已完成跨数据库一致备份。

回退：停止二期 API/Worker，使用原 8000 服务。回退只回到迁移时的一期数据；若二期已产生新写入，先导出并核对，不能直接视为双向同步。当前二期开发入口为8002；8000仅保留历史回退。

## CodeGraph 与本体建模

二期默认使用 Joern 4.0.662，官方镜像固定为 `ghcr.io/joernio/joern@sha256:11b574bc88e8fdf1d7f324cfd9c8e7b696495f5e50a48b633251fa672156a38a`，ARM64 本机已验证。共五个服务：API、Worker、PostgreSQL、Neo4j、CodeGraph。首次解析包含 JVM/脚本启动开销；解析容器上限4GB、Java堆2GB，当前每快照最多200个Java文件。源码只读，禁用项目构建、delombok和自动依赖下载；独立内部网络无互联网出口，不开放宿主端口，不挂载数据库密钥或 Docker socket。

升级已有 M2 时先运行 `scripts/setup_m2.py` 补齐内网令牌，再构建应用与 codegraph；确认无活跃任务后更新 API/Worker。配置中心 → CodeGraph 解析引擎显示真实版本/摘要并管理新任务默认引擎。任务固定引擎，旧规则和证据不自动改绑。Joern 不可用明确失败，兼容 AST 必须显式选择。

`.data/m2/codegraph/` 保存按来源/引擎/导出器哈希隔离的完整 CPG 与原始事实，需与快照、权威数据库共同备份。适配器升级生成新投影。`last-failure.log` 为最近一次失败记录，不代表当前任务状态。运算符、外部声明、无法定位实体和未知调用分开报告；完整数据流/运行时派发未验收。

本体建模与审核入口支持模块/概念定义、别名、规则/代码成员、固定投影、并发修订及独立审批。新修订重新审核，批准本体不自动批准成员规则；发布冻结定义及实际纳入的成员。术语别名与全文检索仅读取正式 Release。

隔离技术验收（需要真实 Neo4j，不调用大模型、不审批主库规则）：

```sh
docker compose --env-file .data/m2/compose.env exec -T api python - < scripts/verify_m2_graph.py
```

脚本用临时 SQLite 权威库与唯一测试 Release 图，结束只清理自己创建的图；验证真实 Neo4j 发布/检索链路，不代替主库迁移验收或真实业务质量评测。

## 尚待完成的 M2 范围

本体复杂交互与覆盖测试、专家评测集与召回质量、逐仓实际目录登记与签名采集验收、检索预算/授权边界扩大测试、正式数据备份恢复演练、三个客户端兼容矩阵。Streamable HTTP、Embedding/Reranker 按需求与实测逐步引入；本增量不等于 M2 最终验收。


## 第三增量：工作区基线和评测

配置中心 → Agent 工作区校验显示公钥状态。宿主机管理员先初始化，再按平台真实仓库ID登记目录（示例占位必须替换）：

```sh
.venv/bin/python scripts/configure_workspaces.py
.venv/bin/python scripts/configure_workspaces.py --repository-id REPOSITORY_ID --root /absolute/path/to/repository
.venv/bin/python scripts/configure_opencode.py
```

私钥为 `.data/m2/workspace-signing.pem`，目录映射为 `.data/m2/workspaces.json`；两者在 runtime 挂载之外。公钥 `.data/m2/runtime/workspace-public-key.pem` 供后端验证。显式恢复密钥时要保持公私钥匹配，脚本拒绝隐式轮换。当前已初始化密钥，尚未登记用户目录。

采集仅覆盖 .java/.md/.txt/.sql/.xml，排除 .git/.env/node_modules/target/build/.idea/.venv；上限1000个文件、单文件1MB、合计25MB、扫描20000项。有效范围内符号链接拒绝，签名观测300秒有效。未跟踪但属于范围的新文件也参与摘要；范围外文件不在校验结论内。观测之后的写入需要重新采集，MATCH 不授权修改或自动执行代码。

重新启动对应项目 OpenCode 实例加载新配置；不要终止其他项目实例。v2 使用原生 mcp.servers 与 codemode:false；不修改全局配置。工具连接和实际模型验收分开记录，当前真实模型四工具验收尚未通过，详见第三增量记录。

Code Agent 联调 → 检索评测，在人工审核发布后建立专家数据集并冻结版本。协议固定K=10、4000 Token；不调用模型，单次技术测量不等于业务质量达标。开发/验收集的相同归一化问题会被拒绝，专家仍需检查语义泄漏和覆盖度。主库无发布版本时正确阻止评测集冻结。

新增可选验收脚本 `scripts/verify_m2_opencode.py --probe-tools` 仅用本地模型探针；`--live-model` 会调用已有授权模型并可能计费，使用隔离合成知识、最多8请求，不审批主库候选。未完成四工具调用或签名MATCH时返回失败，不能以进程启动成功代替验收。


## 前三项闭环验收完成（2026-10-23）

详见 [第四增量记录](../开发记录_M2_04.md)。前述“无Release”“目录映射为空”“真实模型未通过”为前三增量当时状态：现已由用户明确批准发布3条demo规则、2个本体，实际登记demo-repo；OpenCode原生会话、DeepSeek四工具和签名MATCH均实际通过。其余786条保持候选，原候选关联旧AST版本，不改绑到Joern。

只读复查正式知识与真实工作区（变更场景仅操作临时副本，不调用模型）：

```sh
.venv/bin/python scripts/verify_demo_workspace.py
```

OpenCode验收程序显式固定原生会话location及直接函数模式，避免把预检目录与实际会话目录混用；模型代理仅暴露四个只读函数并拒绝其他返回工具调用。`--published-demo` 固定已批准Release并限制证据路径。真实数据付费验收已完成，本次授权限定目的地、内容及请求量，不自动重复运行。

浏览器查看：本体工作台 → 已发布知识 → 加载图谱 → 业务本体/业务—代码映射。Code Agent联调查询“支付校验”。浏览器组装未提供基线仍为UNKNOWN，OpenCode从宿主登记目录采集可MATCH。
