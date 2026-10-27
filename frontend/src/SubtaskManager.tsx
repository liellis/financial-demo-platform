import {useCallback, useEffect, useMemo, useRef, useState} from 'react';
import {request, type Row} from './api';
import RecoveryAdvice from './RecoveryAdvice';
import {modelErrorHint} from './modelErrors';

type Props = {token: string; lang: string; jobs: Row[]; builder: boolean; jobId: string; onJob: (id: string) => void; onParent: (id: string) => void};
const names: Record<string, [string, string]> = {
  PENDING:['待执行','Pending'], RUNNING:['执行中','Running'], RETRY_WAIT:['退避等待','Retry scheduled'],
  SUCCEEDED:['成功','Succeeded'], INVALID:['校验隔离','Quarantined'], FAILED:['失败','Failed'],
  UNKNOWN:['结果未知','Unknown'], HELD:['已暂停','On hold'], SKIPPED:['已跳过','Skipped'],
};
const allowed: Record<string, string[]> = {RETRY:['FAILED','INVALID','UNKNOWN'], HOLD:['PENDING','RETRY_WAIT'], RESUME:['HELD'], SKIP:['PENDING','RETRY_WAIT','HELD','FAILED','INVALID'], RESTORE:['SKIPPED']};

export default function SubtaskManager({token, lang, jobs, builder, jobId, onJob, onParent}: Props) {
  const t = (zh:string,en:string) => lang === 'zh' ? zh : en;
  const [filter,setFilter] = useState(''), [query,setQuery] = useState(''), [search,setSearch] = useState('');
  const [offset,setOffset] = useState(0), [data,setData] = useState<Row>({items:[],summary:{},total:0,all_total:0});
  const [detail,setDetail] = useState<Row|null>(null), [selected,setSelected] = useState<Row[]>([]);
  const [busy,setBusy] = useState(false), [loading,setLoading] = useState(false), [error,setError] = useState(''), [message,setMessage] = useState('');
  const [allowUnknown,setAllowUnknown] = useState(false);
  const requestVersion = useRef(0), detailVersion = useRef(0);
  const parents = useMemo(() => jobs.filter(j=>j.type==='EXTRACT').sort((a,b)=>b.created_at.localeCompare(a.created_at)),[jobs]);
  const parent = parents.find(j=>j.id===jobId);
  const summary = data.summary;
  const api = useCallback((path:string,method='GET',body?:unknown)=>request(path,token,method,body),[token]);
  const fetchRows = useCallback(async () => {
    const version=++requestVersion.current;
    const params=new URLSearchParams({offset:String(offset),limit:'25',q:search});
    if(jobId)params.set('job_id',jobId);if(filter)params.set('status',filter);
    const result=await api('/extraction-subtasks?'+params);
    if(version===requestVersion.current){
      if(offset>0&&offset>=result.total){setOffset(Math.max(0,Math.floor((result.total-1)/25)*25));return;}
      setData(result);setSelected(rows=>rows.filter(r=>result.items.some((next:Row)=>next.id===r.id&&next.revision===r.revision)));
    }
  },[api,jobId,filter,offset,search]);
  const showError = useCallback((e:unknown) => {const err=e as Error&{trace?:string};setError((modelErrorHint(err.message,lang)||t('操作失败，请刷新状态后重试。','Operation failed. Refresh the state and retry.'))+' · '+err.message+(err.trace?' · Trace '+err.trace:''));},[lang]);
  useEffect(()=>{let live=true;setLoading(true);fetchRows().catch(e=>{if(live)showError(e)}).finally(()=>{if(live)setLoading(false)});const timer=setInterval(()=>fetchRows().catch(()=>{}),4000);return()=>{live=false;requestVersion.current++;clearInterval(timer)};},[fetchRows]);
  useEffect(()=>{setOffset(0);setSelected([]);setDetail(null);setAllowUnknown(false);setError('');setMessage('');detailVersion.current++;},[jobId,filter,search]);
  useEffect(()=>{if(!detail)return;let live=true;const timer=setInterval(async()=>{try{const next=await api(`/knowledge-jobs/${detail.job_id}/subtasks/${detail.index}`);if(live)setDetail(current=>current?.id===next.id?next:current);}catch{}},4000);return()=>{live=false;clearInterval(timer)};},[api,detail?.id]);
  async function inspect(row:Row){const version=++detailVersion.current;setAllowUnknown(false);setError('');setMessage('');try{const next=await api(`/knowledge-jobs/${row.job_id}/subtasks/${row.index}`);if(version===detailVersion.current)setDetail(next);}catch(e){showError(e)}}
  function can(command:string, rows:Row[]){return builder&&rows.length>0&&rows.every(r=>r.can_manage&&allowed[command].includes(r.status))&&(!rows.some(r=>r.status==='UNKNOWN')||allowUnknown);}
  async function command(action:string, rows:Row[]){
    if(busy||!can(action,rows))return;
    if(new Set(rows.map(r=>r.job_id)).size!==1){setError(t('批量操作请选择同一父任务内的子任务。','Bulk actions require subtasks from the same parent job.'));return;}
    setBusy(true);setError('');setMessage('');
    try{
      const result=await api(`/knowledge-jobs/${rows[0].job_id}/subtasks/commands`,'POST',{command:action,targets:rows.map(r=>({index:r.index,expected_revision:r.revision})),include_unknown:allowUnknown});
      setSelected([]);setAllowUnknown(false);
      setMessage(['CREATED','PAUSED'].includes(result.parent_status)?t('子任务状态已更新；父任务尚未启动或已暂停，请到父任务确认启动/继续。','Subtasks updated. Start or resume the parent job to execute queued work.'):t('子任务状态已更新。失败重试只重新排队选中的范围；成功子任务保留。','Subtasks updated. Only selected failed scopes are requeued; successful subtasks are retained.'));
      const current=detail;if(current&&result.items.some((r:Row)=>r.id===current.id))setDetail(result.items.find((r:Row)=>r.id===current.id));
      await fetchRows();
    }catch(e){showError(e);await fetchRows().catch(()=>{});if(detail)await inspect(detail);}finally{setBusy(false)}
  }
  const badge=(state:string)=><span className={'badge '+(state==='SUCCEEDED'?'good':['INVALID','FAILED','UNKNOWN','HELD','SKIPPED'].includes(state)?'warn':'')}>{names[state]?.[lang==='zh'?0:1]||state}</span>;
  const unknownSelected=selected.some(r=>r.status==='UNKNOWN')||detail?.status==='UNKNOWN';
  const rows=data.items as Row[];
  const selectable=rows.filter(r=>r.can_manage&&r.status!=='RUNNING'&&r.status!=='SUCCEEDED');
  const format=(value:string|undefined)=>value?new Date(value).toLocaleString(lang==='zh'?'zh-CN':'en-US'):'—';
  return <div className="subtask-manager">
    <section className="panel"><div className="panel-head"><h2>{t('规则抽取子任务管理','Rule extraction subtasks')}</h2><button disabled={loading||busy} onClick={()=>{setError('');fetchRows().catch(showError)}}>{t('刷新子任务','Refresh subtasks')}</button></div><div className="pad">
      <div className="subtask-filters">
        <label>{t('父任务','Parent job')}<select aria-label={t('筛选父任务','Filter parent job')} value={jobId} onChange={e=>onJob(e.target.value)}><option value="">{t('全部规则抽取任务','All extraction jobs')}</option>{parents.map(j=><option key={j.id} value={j.id}>{j.id} · {j.status}</option>)}</select></label>
        <label>{t('子任务状态','Subtask status')}<select aria-label={t('筛选子任务状态','Filter subtask status')} value={filter} onChange={e=>setFilter(e.target.value)}><option value="">{t('全部状态','All states')}</option><option value="ERRORS">{t('失败及校验隔离','Failed and quarantined')}</option>{Object.entries(names).map(([key,label])=><option key={key} value={key}>{label[lang==='zh'?0:1]}</option>)}</select></label>
        <form onSubmit={e=>{e.preventDefault();setSearch(query)}}><label>{t('搜索源码范围或错误码','Search source scope or error')}<input aria-label={t('搜索子任务','Search subtasks')} maxLength={200} value={query} onChange={e=>setQuery(e.target.value)} placeholder={t('文件名、子任务ID、错误码','File, subtask ID, error code')}/></label><button>{t('搜索','Search')}</button></form>
      </div>
      <div className="subtask-stats">
        <div><strong>{data.all_total}</strong><span>{t('范围子任务','Scope subtasks')}</span></div>
        <div><strong>{summary.SUCCEEDED||0}</strong><span>{t('成功并保留结果','Succeeded / retained')}</span></div>
        <div><strong>{(summary.FAILED||0)+(summary.INVALID||0)}</strong><span>{t('失败或校验隔离','Failed / quarantined')}</span></div>
        <div><strong>{summary.UNKNOWN||0}</strong><span>{t('结果未知','Unknown result')}</span></div>
        <div><strong>{(summary.RUNNING||0)+(summary.RETRY_WAIT||0)}</strong><span>{t('执行或退避等待','Running / backoff')}</span></div>
        <div><strong>{(summary.HELD||0)+(summary.SKIPPED||0)}</strong><span>{t('暂停或跳过','Held / skipped')}</span></div>
      </div>
      <progress aria-label={t('子任务成功进度','Subtask success progress')} value={summary.SUCCEEDED||0} max={Math.max(1,data.all_total)}/>
      <div className="toolbar"><span>{t('成功进度：','Success: ')}{summary.SUCCEEDED||0} / {data.all_total}</span>{parent&&<button onClick={()=>onParent(parent.id)}>{t('打开父任务','Open parent job')}</button>}</div>
      {parent&&!parent.execution_policy&&<p className="note">{t('旧版任务仅查看子任务投影。请到父任务创建恢复任务，以保留检查点并启用独立子任务控制。','Legacy jobs expose read-only subtask projections. Create a recovery job from the parent to retain checkpoints and enable subtask controls.')}</p>}
      <p className="muted">{t('暂停/继续作用于尚未执行或等待重试的子任务。运行中的模型请求不强制中断。跳过会保留覆盖缺口；成功结果不会通过重试按钮再次执行。','Hold/resume applies to queued or backoff subtasks. In-flight model calls are not forcibly interrupted. Skipping retains a coverage gap. Successful output cannot be replayed via retry.')}</p>
    </div></section>
    {!jobId&&parents.find(j=>(j.batch_summary?.INVALID||0)+(j.batch_summary?.FAILED||0)+(j.batch_summary?.UNKNOWN||0)>0)&&<button onClick={()=>onJob(parents.find(j=>(j.batch_summary?.INVALID||0)+(j.batch_summary?.FAILED||0)+(j.batch_summary?.UNKNOWN||0)>0)!.id)}>{t('查看最近待处理任务并获取建议','Open latest job needing recovery advice')}</button>}
    <RecoveryAdvice token={token} lang={lang} jobId={jobId} parent={parent} builder={builder} onUpdated={fetchRows} onParent={()=>jobId&&onParent(jobId)}/>
    {error&&<div role="alert" className="notice error">{error}</div>}{message&&<div role="status" className="notice success">{message}</div>}
    {unknownSelected&&<label className="check note"><input type="checkbox" checked={allowUnknown} onChange={e=>setAllowUnknown(e.target.checked)}/><span>{t('我确认重放选中的未知结果子任务，原请求可能已处理，重发可能重复计费。','I authorize replay of selected unknown subtasks. The original request may have been processed; replay can charge again.')}</span></label>}
    <div className="subtask-layout">
      <section className="panel"><div className="panel-head"><h2>{t('子任务列表','Subtask list')}</h2><span className="muted">{t('筛选结果','Filtered')} {data.total}</span></div><div className="pad">
        <div className="toolbar"><span>{t('已选择 ','Selected ')}{selected.length}</span><button disabled={busy||!can('RETRY',selected)||new Set(selected.map(r=>r.job_id)).size!==1} onClick={()=>command('RETRY',selected)}>{t('重试选中失败子任务','Retry selected failed subtasks')}</button><button disabled={busy||!can('HOLD',selected)||new Set(selected.map(r=>r.job_id)).size!==1} onClick={()=>command('HOLD',selected)}>{t('暂停选中子任务','Hold selected subtasks')}</button><button disabled={busy||!can('RESUME',selected)||new Set(selected.map(r=>r.job_id)).size!==1} onClick={()=>command('RESUME',selected)}>{t('继续选中子任务','Resume selected subtasks')}</button></div>
        <p className="muted">{t('批量操作仅限同一父任务；可按失败状态筛选后选择本页条目。','Bulk actions apply to one parent. Filter failed states, then select entries on the current page.')}</p>
        <div className="scroll"><table className="subtask-table"><thead><tr><th><input type="checkbox" aria-label={t('选择本页可操作子任务','Select actionable subtasks on this page')} disabled={!builder||selectable.length===0} checked={selectable.length>0&&selectable.every(r=>selected.some(s=>s.id===r.id))} onChange={e=>setSelected(e.target.checked?selectable:[])}/></th><th>{t('子任务 / 父任务','Subtask / parent')}</th><th>{t('状态','Status')}</th><th>{t('源码范围','Source scope')}</th><th>{t('尝试 / 规则','Attempts / rules')}</th></tr></thead><tbody>
          {rows.map(row=><tr key={row.id} className={detail?.id===row.id?'selected':''}>
            <td><input type="checkbox" aria-label={t(`选择子任务 ${row.index}`,`Select subtask ${row.index}`)+' '+row.job_id} disabled={!builder||!row.can_manage||['RUNNING','SUCCEEDED'].includes(row.status)} checked={selected.some(r=>r.id===row.id)} onChange={e=>setSelected(old=>e.target.checked?[...old.filter(r=>r.id!==row.id),row]:old.filter(r=>r.id!==row.id))}/></td>
            <td><button className="link" onClick={()=>inspect(row)}>{t('子任务 #','Subtask #')}{row.index}</button><small>{row.id.slice(0,20)}…</small><button className="link muted" onClick={()=>onParent(row.job_id)}>{row.job_id.slice(0,17)}…</button></td>
            <td>{badge(row.status)}{row.error&&<small>{row.error}</small>}</td>
            <td><span>{row.source_lines} {t('行','lines')} · {row.input_chars} {t('字符','characters')}</span>{Object.entries(row.ranges).map(([path,range])=><small key={path}>{path}:{(range as Row).start_line}–{(range as Row).end_line}</small>)}</td>
            <td>{row.reused_from?t('复用结果','Reused'):(row.total_attempts??'—')+t(' 次',' calls')} / {row.rule_count??'—'}</td>
          </tr>)}
          {!rows.length&&<tr><td colSpan={5}>{loading?t('正在加载…','Loading…'):t('当前筛选没有子任务。','No subtasks match the filters.')}</td></tr>}
        </tbody></table></div>
        <div className="toolbar subtask-pagination"><button disabled={offset===0||loading} onClick={()=>{setOffset(Math.max(0,offset-25));setSelected([])}}>{t('上一页','Previous')}</button><span>{data.total?offset+1:0}–{Math.min(offset+25,data.total)} / {data.total}</span><button disabled={offset+25>=data.total||loading} onClick={()=>{setOffset(offset+25);setSelected([])}}>{t('下一页','Next')}</button></div>
      </div></section>
      <section className="panel subtask-detail"><div className="panel-head"><h2>{t('子任务详情与恢复','Subtask details & recovery')}</h2></div><div className="pad">
        {!detail?<p className="empty">{t('选择子任务，查看范围、诊断与恢复操作。','Select a subtask to inspect its scope, diagnostics and recovery actions.')}</p>:<>
          <h3>{t('子任务 #','Subtask #')}{detail.index}</h3>{badge(detail.status)}
          <dl><dt>ID</dt><dd>{detail.id}</dd><dt>{t('父任务','Parent')}</dt><dd><button className="link" onClick={()=>onParent(detail.job_id)}>{detail.job_id}</button></dd><dt>{t('模型 / 修订','Model / revision')}</dt><dd>{detail.model_id} / {detail.revision}</dd><dt>{t('最近开始','Last started')}</dt><dd>{format(detail.last_started_at)}</dd><dt>{t('最近结束','Last finished')}</dt><dd>{format(detail.last_finished_at)}</dd><dt>{t('下次重试','Next retry')}</dt><dd>{detail.status==='RETRY_WAIT'?format(detail.next_retry_at):'—'}</dd></dl>
          <p>{detail.source_lines} {t('行','lines')} · {detail.input_chars} {t('输入字符','input characters')} · {t('累计尝试 ','Total attempts ')}{detail.total_attempts??'—'} · {t('有效规则 ','Validated rules ')}{detail.rule_count??'—'}</p>
          <div className="toolbar">{(['RETRY','HOLD','RESUME','SKIP','RESTORE'] as const).map(action=><button key={action} disabled={busy||!can(action,[detail])} onClick={()=>command(action,[detail])}>{({RETRY:t('重试此子任务','Retry this subtask'),HOLD:t('暂停此子任务','Hold this subtask'),RESUME:t('继续此子任务','Resume this subtask'),SKIP:t('跳过此子任务','Skip this subtask'),RESTORE:t('恢复已跳过子任务','Restore skipped subtask')})[action]}</button>)}</div>
          {detail.policy&&<p className="note">{t('手动重试沿用父任务固定的源码与模型配置；每轮最多额外重试 ','Manual retries retain the pinned source and model. Each cycle permits up to ')}{detail.policy.transient_retries}{t(' 次临时网络故障。重试可能产生费用。父任务暂停或尚未启动时只排队；已结束时仅调度选中范围。',' additional transient retries. Calls may incur charges. Paused/unstarted parents only queue work; finished parents dispatch selected scopes only.')}</p>}
          {!detail.can_manage&&<p className="note">{t('该父任务为旧版本或已取消，仅供查看。请从父任务创建恢复任务。','This parent is legacy or cancelled and is read-only. Create a recovery job from its parent page.')}</p>}
          {detail.recovery_guidance&&<div className="note"><strong>{t('已批准的修正提示','Approved corrective guidance')}</strong><p>{detail.recovery_guidance}</p></div>}
          {detail.error&&<div className="note"><strong>{detail.error}</strong><p>{modelErrorHint(detail.error,lang)}</p><pre>{JSON.stringify(detail.error_params,null,2)}</pre></div>}
          <details open><summary>{t('固定源码范围','Pinned source ranges')}</summary>{Object.entries(detail.ranges).map(([path,range])=><p key={path} className="subtask-path">{path}:{(range as Row).start_line}–{(range as Row).end_line}</p>)}<small>{t('范围摘要：','Scope digest: ')}{detail.source_digest}</small></details>
          <details open><summary>{t('模型调用尝试记录','Model call attempts')} ({detail.attempt_history.length})</summary>{!detail.attempt_history.length?<p className="muted">{t('尚无本任务调用记录；继承的旧检查点可能只有汇总结果。','No attempts recorded here. Inherited legacy checkpoints may contain aggregate output only.')}</p>:<ol className="subtask-history">{detail.attempt_history.map((attempt:Row,i:number)=><li key={i}><strong>{attempt.status}</strong><p>{format(attempt.at)} → {format(attempt.finished_at)}</p><small>{t('重试轮次 ','Retry cycle ')}{attempt.cycle??0} · {t('轮内尝试 ','Attempt in cycle ')}{attempt.attempt??'—'}</small></li>)}</ol>}</details>
          <details><summary>{t('人工操作审计','Manual control history')} ({detail.control_history.length})</summary><ol className="subtask-history">{detail.control_history.map((entry:Row,i:number)=><li key={i}><strong>{entry.command}</strong><p>{entry.from_status} → {entry.to_status}</p><small>{format(entry.at)} · {entry.actor}</small></li>)}</ol></details>
        </>}
      </div></section>
    </div>
  </div>;
}
