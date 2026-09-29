#!/usr/bin/env python3
import sys
from pathlib import Path

p=Path(sys.argv[1])
s=p.read_text(encoding="utf-8")
if "ID_HISTORIAL_BCV" in s:
    raise SystemExit(0)

style='''\n/* ID_HISTORIAL_BCV */\n.rate-history{margin-top:18px}.rate-history-list{display:grid;gap:8px}.rate-history-item{display:flex;justify-content:space-between;align-items:center;padding:12px 14px;border:1px solid var(--border);border-radius:13px;background:var(--card)}.rate-history-item small{color:var(--muted)}.rate-history-item strong{font-size:14px}.rate-history-current{border-color:var(--primary);box-shadow:0 0 0 2px rgba(109,74,255,.08)}\n'''
s=s.replace('</style>',style+'</style>',1)

html='''\n      <div class="card panel rate-history" id="rateHistoryPanel"><div class="panel-head"><h3>Historial de tasa BCV</h3><span id="rateHistoryCount">0 días</span></div><div id="rateHistoryList" class="rate-history-list"></div><div class="rate-history-note">La app guarda la tasa de cada día automáticamente. También puedes actualizarla manualmente.</div></div>\n'''
s=s.replace('      <div class="card panel" style="margin-top:18px"><div class="panel-head"><h3>Últimos movimientos</h3>',html+'      <div class="card panel" style="margin-top:18px"><div class="panel-head"><h3>Últimos movimientos</h3>',1)

s=s.replace("function defaultState(){return {balances:{VES:0,USD:0},transactions:[],categories:[...DEFAULT_CATEGORIES],theme:'verde',mode:'light',rate:null,profile:{username:'Mi usuario',photo:null}};}",
"function defaultState(){return {balances:{VES:0,USD:0},transactions:[],categories:[...DEFAULT_CATEGORIES],theme:'verde',mode:'light',rate:null,rateHistory:[],profile:{username:'Mi usuario',photo:null}};}",1)
s=s.replace("return {...base,...saved,profile:{...base.profile,...(saved.profile||{})}};",
"return {...base,...saved,rateHistory:Array.isArray(saved.rateHistory)?saved.rateHistory:[],profile:{...base.profile,...(saved.profile||{})}};",1)

helpers='''\nfunction bcvDate(v){return String(v||'').slice(0,10);}\nfunction saveBcvHistory(){state.rateHistory=Array.isArray(state.rateHistory)?state.rateHistory:[];if(currentRate>0){const d=bcvDate(state.rate?.date)||new Date().toISOString().slice(0,10);const i=state.rateHistory.findIndex(x=>x.date===d);const row={date:d,USD:currentRate};if(i>=0)state.rateHistory[i]=row;else state.rateHistory.push(row);state.rateHistory.sort((a,b)=>b.date.localeCompare(a.date));state.rateHistory=state.rateHistory.slice(0,365);}}\nfunction renderBcvHistory(){const el=document.getElementById('rateHistoryList'),count=document.getElementById('rateHistoryCount');if(!el||!count)return;const list=(state.rateHistory||[]).slice().sort((a,b)=>b.date.localeCompare(a.date));count.textContent=list.length+' día'+(list.length===1?'':'s');el.innerHTML=list.slice(0,30).map((x,i)=>'<div class="rate-history-item '+(i===0?'rate-history-current':'')+'"><div><strong>'+new Intl.DateTimeFormat('es-VE',{weekday:'short',day:'2-digit',month:'short',year:'numeric'}).format(new Date(x.date+'T12:00:00'))+'</strong><br><small>1 USD = tasa BCV</small></div><strong>'+Number(x.USD).toLocaleString('es-VE',{minimumFractionDigits:2,maximumFractionDigits:4})+' Bs</strong></div>').join('')||'<div class="rate-history-empty">Todavía no hay tasas guardadas.</div>';}\n'''
s=s.replace("async function fetchRate(manual=false){",helpers+"\nasync function fetchRate(manual=false){",1)

old="currentRate=Number(data.USD);state.rate={USD:currentRate,date:data.date||null,updated_at:data.updated_at||null};saveState();"
new="currentRate=Number(data.USD);state.rate={USD:currentRate,date:data.date||new Date().toISOString().slice(0,10),updated_at:data.updated_at||null};saveBcvHistory();saveState();"
s=s.replace(old,new,1)

s=s.replace("updateRateUI();updateConversion();}", "updateRateUI();updateConversion();renderBcvHistory();}",1)
s=s.replace("render();\nfetchRate(false);\nsetInterval(()=>fetchRate(false),5*60*1000);",
"render();\nfetchRate(false);\nrenderBcvHistory();\nsetInterval(()=>fetchRate(false),5*60*1000);\ndocument.addEventListener('visibilitychange',()=>{if(document.visibilityState==='visible')fetchRate(false);});",1)
p.write_text(s,encoding="utf-8")
print("ok")
