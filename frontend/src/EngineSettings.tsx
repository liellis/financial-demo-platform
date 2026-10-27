import {useEffect,useState} from 'react';
import {request,type Row} from './api';
export default function EngineSettings({token,lang,admin}:{token:string;lang:string;admin:boolean}){
 const t=(zh:string,en:string)=>lang==='zh'?zh:en;
 const [status,setStatus]=useState<Row|null>(null),[config,setConfig]=useState<Row|null>(null),[engine,setEngine]=useState(''),[error,setError]=useState(''),[message,setMessage]=useState(''),[busy,setBusy]=useState(false);
 useEffect(()=>{Promise.all([request('/codegraph/status',token),request('/settings/analysis',token)]).then(([s,c])=>{setStatus(s);setConfig(c);setEngine(c.engine)}).catch(e=>setError(e.message))},[token]);
 return <section className="panel"><div className="panel-head"><h2>{t('CodeGraph 解析引擎','CodeGraph analysis engine')}</h2></div><div className="pad">
 <p className="note">{t('二期采用开源 Joern CPG。每个新解析任务固定所选引擎，旧投影和规则证据不自动改绑。Joern 不可用时明确失败，不静默降级为 AST。','M2 uses open-source Joern CPG. New tasks pin their engine; existing projections and rule evidence are not remapped. Joern failures do not silently fall back to AST.')}</p>
 <label>{t('新任务默认引擎','Default engine for new tasks')}<select disabled={!admin||!config||busy} value={engine} onChange={e=>setEngine(e.target.value)}><option value="joern" disabled={!status?.configured}>Joern · CodeGraph / CPG</option><option value="javac">javac · {t('兼容 AST 模式','Compatibility AST mode')}</option></select></label>
 <p>{t('服务状态：','Service status: ')}{status?.ready?t('可用','Ready'):t('不可用 / 检查中','Unavailable / checking')}</p>{status?.engine&&<pre>{JSON.stringify(status.engine,null,2)}</pre>}
 <p className="muted">{t('Joern 仅在内部 Docker 网络提供固定解析接口，源码只读挂载，不执行 Maven/Gradle 项目构建、不自动下载仓库依赖，动态目标和缺失依赖保留未知。类范围可能仅为 AST 已覆盖行，不能据此认定完整业务逻辑。','Joern exposes a fixed internal Docker endpoint with read-only sources. It runs no Maven/Gradle builds or automatic dependency downloads. Dynamic or missing targets remain unknown. Type ranges may cover only AST-known lines and do not establish complete business logic.')}</p>
 <button className="primary" disabled={busy||!admin||!config} onClick={async()=>{setBusy(true);setError('');setMessage('');try{const next=await request('/settings/analysis',token,'PUT',{engine,expected_revision:config?.revision});setConfig(next);setMessage(t('已保存，只影响后续新建解析任务。','Saved for newly created analysis jobs.'))}catch(e){setError((e as Error).message)}finally{setBusy(false)}}}>{t('保存解析设置','Save analysis settings')}</button>
 {error&&<div role="alert" className="notice error">{error}</div>}{message&&<div role="status" className="notice success">{message}</div>}</div></section>
}
