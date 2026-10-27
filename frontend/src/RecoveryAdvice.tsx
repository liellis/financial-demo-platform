import {useCallback, useEffect, useRef, useState} from 'react';
import {request, type Row} from './api';
import {modelErrorHint} from './modelErrors';

type Props={token:string;lang:string;jobId:string;parent?:Row;builder:boolean;onUpdated:()=>Promise<void>;onParent:()=>void};
export default function RecoveryAdvice({token,lang,jobId,parent,builder,onUpdated,onParent}:Props){
  const t=(zh:string,en:string)=>lang==='zh'?zh:en;
  const [advice,setAdvice]=useState<Row|null>(null),[chosen,setChosen]=useState<string[]>([]);
  const [busy,setBusy]=useState(false),[error,setError]=useState(''),[message,setMessage]=useState(''),[unknown,setUnknown]=useState(false);
  const version=useRef(0),autoSelected=useRef('');
  const api=useCallback((method='GET',body?:unknown,suffix='')=>request(`/knowledge-jobs/${jobId}/recovery-advice${suffix}`,token,method,body),[token,jobId]);
  const receive=useCallback((next:Row|null)=>{
    setAdvice(next);
    if(next?.status==='READY'&&autoSelected.current!==next.id){
      autoSelected.current=next.id;setChosen(next.groups.filter((g:Row)=>g.executable&&!g.approved_at&&!g.requires_unknown_confirmation).map((g:Row)=>g.group_id));
    }
  },[]);
  const hint=(e:unknown)=>{const err=e as Error&{trace?:string};return (modelErrorHint(err.message,lang)||t('建议操作失败，请刷新状态后重试。','Advice operation failed. Refresh and retry.'))+' · '+err.message+(err.trace?' · Trace '+err.trace:'')};
  useEffect(()=>{
    const current=++version.current;setAdvice(null);setChosen([]);setUnknown(false);setError('');setMessage('');setBusy(false);autoSelected.current='';
    if(!jobId)return;
    const read=()=>api().then(result=>{if(version.current===current)receive(result)}).catch(e=>{if(version.current===current)setError(hint(e))});
    void read();const timer=setInterval(read,4000);return()=>{version.current++;clearInterval(timer)};
  },[api,jobId,receive]);
  async function generate(){
    const current=version.current;setBusy(true);setError('');setMessage('');setChosen([]);setUnknown(false);
    try{const next=await api('POST',{request_id:crypto.randomUUID(),language:lang});if(current===version.current)receive(next)}
    catch(e){if(current===version.current){setError(hint(e));await api().then(next=>{if(current===version.current)receive(next)}).catch(()=>{})}}
    finally{if(current===version.current)setBusy(false)}
  }
  const expired=!!advice&&Date.now()/1000>=advice.expires_at;
  const groups=(advice?.groups||[]) as Row[];
  const selected=groups.filter(g=>chosen.includes(g.group_id)&&g.executable&&!g.approved_at);
  const count=selected.reduce((sum,g)=>sum+g.targets.length,0);
  const needsUnknown=selected.some(g=>g.requires_unknown_confirmation);
  const remaining=(parent?.batch_summary?.INVALID||0)+(parent?.batch_summary?.FAILED||0)+(parent?.batch_summary?.UNKNOWN||0);
  const available=!!parent?.execution_policy&&['PARTIAL_SUCCEEDED','NEEDS_ATTENTION','FAILED','WAITING_SUBTASKS','PAUSED','CREATED'].includes(parent?.status);
  async function approve(){
    if(!advice||!count||(needsUnknown&&!unknown))return;
    const current=version.current;setBusy(true);setError('');setMessage('');
    try{
      const result=await api('POST',{expected_revision:advice.revision,group_ids:selected.map(g=>g.group_id),include_unknown:unknown},`/${advice.id}/approve`);
      if(current!==version.current)return;
      receive(result.advice);setChosen([]);setUnknown(false);
      setMessage(['CREATED','PAUSED'].includes(result.parent_status)?t('建议已批准，修正提示已绑定到选中子任务。请到父任务确认启动或继续。','Approved. Guidance is pinned to the selected subtasks. Start or resume the parent to execute.'):t('建议已批准，仅选中子任务已带修正提示排队重试；成功结果保留，进度可在下方查看。','Approved. Only selected subtasks were queued with corrective guidance. Successful output is retained; progress appears below.'));
      await onUpdated();
    }catch(e){if(current===version.current){setError(hint(e));await api().then(next=>{if(current===version.current)receive(next)}).catch(()=>{});await onUpdated().catch(()=>{})}}
    finally{if(current===version.current)setBusy(false)}
  }
  return <section className="panel recovery-advice"><div className="panel-head"><h2>{t('模型辅助处理建议与审批','Model-assisted recovery & approval')}</h2>
    <button disabled={busy||!builder||!available||!remaining||advice?.status==='GENERATING'} onClick={generate}>{busy&&advice?.status!=='READY'?t('正在生成建议…','Generating advice…'):advice?t('重新生成模型建议','Regenerate model advice'):t('生成模型处理建议','Generate model advice')}</button>
  </div><div className="pad">
    <p className="muted">{t('先由当前任务绑定的模型集中分析错误，再按原因分组审批；不必逐个排查。每次生成最多调用模型1次，可能产生费用；仅发送错误码、状态和源码范围等诊断元数据，不发送源码正文。批准重试后会发送任务授权的原批次源码并继续严格校验证据。','The pinned model analyzes errors for grouped approval. Each generation uses at most one paid call, sending only diagnostic metadata such as error codes, states and source ranges, without source text. Approved retries send the authorized source batches and retain strict evidence validation.')}</p>
    {!jobId&&<p>{t('请先选择一个父任务。','Select a parent job first.')}</p>}
    {jobId&&!available&&<p className="note">{t('请等待父任务执行结束或暂停后生成建议；旧版任务需先升级。','Wait for the parent to settle or pause before requesting advice. Legacy jobs require upgrading.')}</p>}
    {advice?.status==='GENERATING'&&<p role="status">{t('模型正在集中诊断，结果会保存；离开页面后可返回查看。不会自动重发诊断请求。','Model diagnosis is running and will be saved. You can return later; diagnosis requests are never replayed automatically.')}</p>}
    {['FAILED','UNKNOWN'].includes(advice?.status)&&<div className="note">{t('本次建议未生成成功，子任务和成功结果未改变。可明确点击重新生成，可能再次计费。','Advice did not complete. Subtasks and successful output are unchanged. Explicit regeneration may incur another charge.')}<p>{modelErrorHint(advice?.error,lang)||advice?.error}</p></div>}
    {advice&&['READY','APPROVED'].includes(advice.status)&&<>
      {expired&&<p className="note">{t('建议已过期，请重新生成后审批。','Advice expired. Regenerate it before approval.')}</p>}
      <p><strong>{advice.summary}</strong></p><p className="muted">{t('模型：','Model: ')}{advice.model_id} · {t('建议生成时间：','Generated: ')}{new Date(advice.finished_at).toLocaleString()} · {t('审批有效期1小时；模型建议不等于事实或规则审核结论。','Valid for approval for 1 hour. Advice is not established fact or rule approval.')}</p>
      {groups.map(group=><div className="note" key={group.group_id}>
        <label className="check"><input type="checkbox" aria-label={t('审批建议组 ','Approve advice group ')+group.error} disabled={busy||!builder||!group.executable||!!group.approved_at||expired||advice.status!=='READY'} checked={chosen.includes(group.group_id)} onChange={e=>setChosen(old=>e.target.checked?[...old,group.group_id]:old.filter(id=>id!==group.group_id))}/><strong>{group.targets.length}{t(' 个子任务 · ',' subtasks · ')}{group.error} · {group.approved_at?t('已批准','Approved'):group.action==='RETRY'?t('建议带修正提示重试','Retry with corrective guidance'):group.action==='WAIT'?t('建议等待','Wait'):t('需人工处理前置条件','Manual prerequisite')}</strong></label>
        <p>{t('模型分析：','Model analysis: ')}{group.reason}</p><p>{t('处理建议：','Suggested treatment: ')}{group.guidance}</p>
        {group.requires_unknown_confirmation&&<p>{t('包含结果未知请求，需单独确认可能重复计费。','Contains unknown requests; separate duplicate-charge confirmation is required.')}</p>}
        <details><summary>{t('查看涉及子任务与源码范围','Affected subtasks and source ranges')}</summary>{group.targets.map((target:Row)=><p className="subtask-path" key={target.index}>#{target.index} · {Object.entries(target.ranges).map(([path,r])=>`${path}:${(r as Row).start_line}–${(r as Row).end_line}`).join(', ')}</p>)}</details>
      </div>)}
      {needsUnknown&&<label className="check note"><input type="checkbox" checked={unknown} onChange={e=>setUnknown(e.target.checked)}/>{t('我批准重放所选未知结果子任务，理解原请求可能已处理并可能重复计费。','I approve replay of selected unknown subtasks and understand the original requests may already have been processed and charged.')}</label>}
      <p>{t('已选 ','Selected ')}{count}{t(' 个子任务；含临时故障重试最多 ',' subtasks; at most ')}{count*(advice.retry_attempts_per_subtask||1)}{t(' 次模型调用，每次最多输出 ',' model calls including transient retries; up to ')}{advice.max_output_tokens_per_call}{t(' Token。只重试所选范围，不批准或发布规则。',' output tokens per call. Only selected scopes are retried; no rules are approved or published.')}</p>
      <div className="toolbar"><button className="primary" disabled={busy||!builder||!count||expired||advice.status!=='READY'||(needsUnknown&&!unknown)} onClick={approve}>{t(`批准建议并重试 ${count} 个子任务`,`Approve & retry ${count} subtasks`)}</button><button onClick={onParent}>{t('查看父任务','View parent job')}</button></div>
    </>}
    {error&&<div role="alert" className="notice error">{error}</div>}{message&&<div role="status" className="notice success">{message}</div>}
  </div></section>;
}
