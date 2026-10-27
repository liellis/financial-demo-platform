export function modelErrorHint(code: string, lang: string): string {
  const hints: Record<string, [string, string]> = {
    ADVICE_STALE: ["建议已过期、已审批或版本发生变化，请重新生成建议；未重复执行。", "Advice expired, was approved or changed. Generate fresh advice; nothing was replayed."],
    ADVICE_GENERATING: ["模型正在生成该任务的建议，请等待或稍后返回查看。", "Advice is already being generated. Wait or return later."],
    ADVICE_PARENT_BUSY: ["父任务仍在执行或不可操作，请等待执行结束或暂停后生成建议。", "The parent is busy or unavailable. Wait for it to settle or pause it."],
    ADVICE_NO_FAILED_SUBTASKS: ["当前没有失败、隔离或未知子任务，无需生成恢复建议。", "No failed, quarantined or unknown subtasks need recovery advice."],
    ADVICE_OUTPUT_INVALID: ["模型建议不符合约定结构或引用了错误的诊断组，未接受且未执行。", "Advice has an invalid structure or diagnostic group reference. Nothing was accepted or executed."],
    ADVICE_RESULT_UNKNOWN: ["诊断请求结果未知；未自动重发，子任务保持原状。", "Diagnosis outcome is unknown; it was not replayed and subtasks are unchanged."],
    ADVICE_TOO_MANY_SUBTASKS: ["一次集中诊断最多100个失败子任务，当前数量超限。", "A diagnosis supports up to 100 failed subtasks; this job exceeds the limit."],
    MAPPING_EVIDENCE_REQUIRED: ["规则的证据未落在关联代码实体内部。可生成模型处理建议，批准后带修正提示重新抽取；不能直接接受无映射证据的规则。", "Rule evidence does not fall within its linked code entity. Generate advice and approve a guided retry; missing mapping evidence cannot be accepted."],
    MODEL_VALIDATION_STALE: ["模型能力验证已过期或配置已变化。请到“配置中心 → 模型连接与验证”重新验证；已校验子任务结果保留。", "Model validation expired or configuration changed. Revalidate in Configuration center → Model connections & validation; validated subtask output is retained."],
    MODEL_VALIDATION_REQUIRED: ["请先到“配置中心 → 模型连接与验证”完成能力验证，再重试子任务。", "Complete capability validation in Configuration center → Model connections & validation before retrying subtasks."],
    SUBTASK_REVISION_CONFLICT: ["子任务状态已被执行器或其他操作更新。请刷新后重新选择，未重复排队。", "The subtask changed in the executor or another action. Refresh and select it again; it was not queued twice."],
    SUBTASK_INVALID_TRANSITION: ["该状态不允许此操作。成功或运行中的子任务不能重试；请刷新状态。", "This action is not allowed in the current state. Successful or in-flight subtasks cannot be retried. Refresh the state."],
    SUBTASK_LEGACY_UPGRADE_REQUIRED: ["旧版任务只读，请到父任务创建恢复任务以启用独立子任务管理。", "Legacy jobs are read-only. Create a recovery job from the parent to enable subtask management."],
    SUBTASK_PARENT_NOT_AVAILABLE: ["父任务已取消或正在处理暂停/取消请求，暂不接受子任务操作。", "The parent is cancelled or handling a pause/cancel request. Subtask controls are unavailable."],
    DUPLICATE_SUBTASKS: ["同一子任务不能在一次操作中重复选择。", "A subtask cannot appear twice in the same command."],
    BATCHES_REQUIRE_ATTENTION: [
      "任务已遍历可执行批次，仍有失败、校验隔离或未知批次。已校验结果保留；请查看逐批诊断，重试时不会重发计划与配置一致的成功批次。",
      "Available batches have been processed, but failed, invalid or unknown batches remain. Validated output is preserved. Inspect batch diagnostics; successful batches are reused for the same plan and model.",
    ],
    MODEL_CONNECT_TIMEOUT: ["连接模型服务超时，尚未发送请求；新版任务会按退避策略有限重试。", "Connecting timed out before sending the request. New jobs use bounded backoff retries."],
    MODEL_TEMPORARILY_UNAVAILABLE: ["模型服务临时不可用，新版任务会有限重试；不会丢弃已完成批次。", "The model service is temporarily unavailable. New jobs retry within limits and preserve completed batches."],
    MODEL_RESPONSE_INTERRUPTED: ["响应途中连接中断，提供方处理结果未知。本批次不会自动重发，其他批次可以继续。", "The response connection was interrupted; provider processing is unknown. This batch is not replayed automatically; other batches can continue."],
    UNKNOWN_REPLAY_CONFIRMATION_REQUIRED: ["重放未知请求可能重复计费。请先明确勾选包含未知批次；计划变更时也需要此确认。", "Unknown requests may charge again. Explicitly select inclusion of unknown batches; this also applies when changing plans."],
    EVIDENCE_QUOTE_INVALID: [
      "模型引用未与对应源码逐字匹配，候选未被接受。仅允许对当前批次内唯一的原文引用校正行号；改写、缩略或重复歧义的引用仍会拒绝。可在配置中心缩小行数，再创建重试任务。",
      "The quote does not match the cited source verbatim. Candidates were not accepted. Only unique exact quotes within the batch can have line numbers corrected; rewritten, abbreviated or ambiguous quotes remain invalid. Reduce the line cap in Configuration center and create a retry job.",
    ],
    MODEL_INPUT_BUDGET_EXCEEDED: [
      "组装的请求超过单次输入预算，尚未发送给模型。旧任务请创建重试任务，系统将先计算分批计划。",
      "The request exceeded the per-call input budget and was not sent. Create a retry job to compute a batch plan.",
    ],
    MODEL_INPUT_UNIT_TOO_LARGE: [
      "单个完整方法或单行文本超过预算，未截断或发送源码。请根据文件和行号缩小输入范围，或新建更大单次预算的模型配置并验证。",
      "A complete method or single line exceeds the budget. Source was not truncated or sent. Narrow the scope or validate a model configuration with a larger per-call budget.",
    ],
    EVIDENCE_OUTSIDE_BATCH: [
      "模型引用了本批次未提供的源码范围，候选未被接受。请核对模型的原始行号引用能力。",
      "Evidence refers to source outside this batch. Candidates were not accepted. Check absolute line-number citations.",
    ],
    EXTRACTION_PLAN_CHANGED: [
      "分批计划与任务确认时不一致，请重新创建任务后核对计划。",
      "The batch plan differs from the confirmed plan. Create a new job and review the plan.",
    ],
    INVALID_CREDENTIAL_REFERENCE: [
      "这里需要环境变量名称，例如 BOS_MODEL_API_KEY，不是实际密钥。若要粘贴密钥，请切换到“直接填写 API Key”。",
      "Enter an environment variable name, e.g. BOS_MODEL_API_KEY, not the key itself. To paste a key, choose Direct API Key.",
    ],
    MODEL_API_KEY_REQUIRED: [
      "请在 API Key 密码框中填写密钥。",
      "Enter the key in the API Key password field.",
    ],
    MODEL_API_KEY_INVALID: [
      "API Key 格式无效；请检查空格或换行，不要把密钥填入变量名字段。",
      "Invalid API Key format. Check whitespace or newlines; never put a key in the variable-name field.",
    ],
    CREDENTIAL_MODE_MISMATCH: [
      "凭据方式与提交内容不一致，请重新选择凭据方式。",
      "Credential mode and submitted fields do not match. Select the intended mode.",
    ],
    CREDENTIAL_STORE_UNAVAILABLE: [
      "服务端凭据库不可用，未自动替换密钥。请检查数据目录权限及主密钥备份。",
      "The server credential vault is unavailable. No key was replaced. Check data directory permissions and the master-key backup.",
    ],
    MODEL_CREDENTIAL_MISSING: [
      "后端未找到该环境变量中的密钥。可以配置 API 与 Worker 的环境变量，或改用直接填写 API Key。",
      "The referenced environment variable has no key. Configure it for API and Worker, or use Direct API Key.",
    ],
    MODEL_AUTHENTICATION_FAILED: [
      "模型服务返回401：密钥无效或已撤销，请换用有效的新密钥重新保存配置。",
      "The provider returned 401: the key is invalid or revoked. Save a new configuration with a valid key.",
    ],
    MODEL_BALANCE_INSUFFICIENT: [
      "模型服务返回402：账户额度不足，请到提供方控制台检查余额。",
      "The provider returned 402: insufficient balance. Check the provider console.",
    ],
    MODEL_ACCESS_DENIED: [
      "模型服务返回403：当前账户或密钥没有访问权限。",
      "The provider returned 403: this account or key lacks access.",
    ],
    MODEL_NOT_FOUND: [
      "模型服务返回404：请核对 API 基础地址和模型 ID。基础地址无需包含 /chat/completions。",
      "The provider returned 404. Check the API base URL and model ID; the base URL should not include /chat/completions.",
    ],
    MODEL_RATE_LIMITED: [
      "模型服务返回429：请求受到限流，请稍后重试。",
      "The provider returned 429: rate limited. Retry later.",
    ],
    MODEL_IDENTITY_MISMATCH: [
      "响应中的模型 ID 与配置不一致，请核对提供方返回的模型名称。",
      "The response model ID differs from the configured ID. Check the provider's reported model name.",
    ],
    MODEL_OUTPUT_EMPTY: [
      "模型没有返回最终 JSON 内容。请检查提供方输出模式及输出预算。",
      "The model returned no final JSON content. Check the output mode and token budget.",
    ],
    MODEL_OUTPUT_TRUNCATED: [
      "模型输出因 Token 上限被截断，未接受为有效候选。请增加输出预算或缩小输入。",
      "Output was truncated by the token limit and was not accepted. Increase output budget or reduce input.",
    ],
    MODEL_OUTPUT_INVALID: [
      "模型返回内容不符合 JSON/抽取契约，未写入正式知识。",
      "The response does not match the JSON/extraction contract and was not accepted.",
    ],
    MODEL_TIMEOUT: [
      "模型响应超时，请检查连接和模型响应时长；系统未自动重复请求。",
      "The model request timed out. Check connectivity and response time; it was not retried automatically.",
    ],
    MODEL_CONNECTION_FAILED: [
      "无法连接模型服务，请核对网络、TLS和基础地址。",
      "Cannot connect to the model service. Check networking, TLS and the base URL.",
    ],
  };
  return hints[code]?.[lang === "zh" ? 0 : 1] || "";
}
