"""Reviewed module/concept revisions; candidate membership is never a publication shortcut."""
import copy,uuid
from .contracts import DomainError,digest,identity

FIELDS=('kind','name','definition','aliases','projection_id','rule_ids','code_entity_ids')

def check_members(studio,item,conn=None):
    for key,version in item.get('rule_versions',{}).items():
        if studio.get('candidate',key,conn)['content_revision']!=version:raise DomainError('ONTOLOGY_MEMBERS_CHANGED',409)

def content_hash(item):return digest({k:item[k] for k in FIELDS}|{'rule_versions':item.get('rule_versions',{})})

def save(studio,data,actor,key=None):
    with studio.store.transaction() as conn:
        previous=studio.get('ontology',key,conn) if key else None
        if previous and data['expected_revision']!=previous['revision']:raise DomainError('REVISION_CONFLICT',409)
        projection=studio.get('projection',data['projection_id'],conn)
        entities={e['id'] for e in projection['entities']}
        if not data['rule_ids'] and not data['code_entity_ids']:raise DomainError('ONTOLOGY_MEMBERS_REQUIRED',422)
        if len(set(data['rule_ids']))!=len(data['rule_ids']) or len(set(data['code_entity_ids']))!=len(data['code_entity_ids']):raise DomainError('DUPLICATE_MEMBERS',422)
        if any(e not in entities for e in data['code_entity_ids']):raise DomainError('ONTOLOGY_PROJECTION_MISMATCH',422)
        for rule_id in data['rule_ids']:
            if studio.get('candidate',rule_id,conn)['projection_id']!=projection['id']:raise DomainError('ONTOLOGY_PROJECTION_MISMATCH',422)
        value={k:copy.deepcopy(data[k]) for k in FIELDS}
        value.update(id=key or 'ontology_'+uuid.uuid4().hex,status='CANDIDATE',actor=actor,snapshot_id=projection['snapshot_id'])
        value['rule_versions']={key:studio.get('candidate',key,conn)['content_revision'] for key in data['rule_ids']}
        value['content_digest']=content_hash(value)
        if previous:result=studio.store.update('ontology',value,previous['revision'],conn)
        else:result=studio.store.insert('ontology',value,conn)
        studio.audit(actor,'ONTOLOGY_SAVE',result['id'],conn,{'reason':data['reason'],'content_digest':value['content_digest']})
        return result


def decide(studio,key,data,actor):
    with studio.store.transaction() as conn:
        item=studio.get('ontology',key,conn)
        if item['revision']!=data['expected_revision']:raise DomainError('REVISION_CONFLICT',409)
        if item['status']!='CANDIDATE':raise DomainError('ONTOLOGY_NOT_REVIEWABLE',409)
        check_members(studio,item,conn)
        review=studio.store.insert('ontology_review',{'id':'ontology_review_'+uuid.uuid4().hex,'ontology_id':key,'content_digest':content_hash(item),'decision':data['decision'],'reason':data['reason'],'actor':actor},conn)
        item.update(status='APPROVED' if data['decision']=='APPROVE' else 'REJECTED',approval_id=review['id'])
        result=studio.store.update('ontology',item,item['revision'],conn)
        studio.audit(actor,'ONTOLOGY_'+data['decision'],key,conn,{'reason':data['reason']})
        return result


def graph_nodes(studio,projection_ids,rule_ids,code_ids,published=False):
    nodes=[];edges=[];frozen=[]
    for item in studio.store.all('ontology'):
        if item['projection_id'] not in projection_ids or item['status']=='REJECTED':continue
        selected_rules=set(item['rule_ids'])&set(rule_ids);selected_code=set(item['code_entity_ids'])&set(code_ids)
        if published:
            if item['status']!='APPROVED' or not (selected_rules or selected_code):continue
            check_members(studio,item)
            review=studio.get('ontology_review',item['approval_id'])
            if review['decision']!='APPROVE' or review['content_digest']!=content_hash(item):raise DomainError('ONTOLOGY_REVIEW_STALE',409)
        node_id=identity('ontology_version',[item['id'],item['revision']])
        node={k:copy.deepcopy(item[k]) for k in FIELDS}
        node.update(id=node_id,logical_id=item['id'],revision=item['revision'],status='PUBLISHED' if published else item['status'],snapshot_id=item['snapshot_id'],rule_ids=sorted(selected_rules),code_entity_ids=sorted(selected_code),preview=not published)
        nodes.append(node);frozen.append(node)
        for target in sorted(selected_rules|selected_code):
            edge={'source':node_id,'target':target,'type':'CONTAINS_RULE' if target in selected_rules else 'IMPLEMENTED_BY','preview':not published}
            edges.append({'id':identity('ontology_edge',edge),**edge})
    return nodes,edges,frozen
