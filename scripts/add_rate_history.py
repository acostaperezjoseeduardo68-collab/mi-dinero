#!/usr/bin/env python3
import sys
from pathlib import Path

path = Path(sys.argv[1])
s = path.read_text(encoding="utf-8")
if "rateHistorySelect" in s:
    print("El historial BCV ya está presente.")
    raise SystemExit(0)

css = """
    .rate-history-grid{grid-template-columns:1fr;margin-top:18px}.rate-history-toolbar{display:flex;gap:10px;align-items:end;flex-wrap:wrap}.rate-history-toolbar .field{min-width:220px;flex:1}.rate-history-detail{display:grid;grid-template-columns:1.2fr .8fr;gap:12px;margin-top:14px}.rate-detail-box{padding:15px;border-radius:15px;background:#fafaff;border:1px solid var(--border)}.rate-detail-box strong{display:block;font-size:24px;margin-top:6px}.rate-detail-box small{display:block;font-size:11px;color:var(--muted);margin-top:5px}.rate-delta-up{color:#18864e;font-weight:800}.rate-delta-down{color:#c3394f;font-weight:800}.rate-delta-flat{color:var(--muted);font-weight:800}.rate-chart{display:grid;gap:9px;margin-top:14px}.rate-chart-row{display:grid;grid-template-columns:78px 1fr 92px;gap:10px;align-items:center}.rate-chart-date{font-size:11px;color:var(--muted)}.rate-chart-bar{height:24px;border-radius:8px;background:linear-gradient(90deg,var(--primary),var(--primary-2));min-width:3px}.rate-chart-value{text-align:right;font-size:12px;font-weight:800}.rate-history-note{font-size:11px;color:var(--muted);line-height:1.4;margin-top:10px}.rate-history-empty{padding:18px;border:1px dashed var(--border);border-radius:14px;color:var(--muted);font-size:12px;text-align:center}.history-count{font-size:12px;color:var(--muted)}
    body.dark .rate-detail-box{background:#141720;border-color:var(--border)}
"""
html = """
      <div class="grid rate-history-grid">
        <div class="card panel">
          <div class="panel-head"><h3>Historial de tasa BCV</h3><span id="rateHistorySummary" class="history-count">Cargando histórico…</span></div>
          <div class="rate-history-toolbar">
            <div class="field"><label>Consultar día</label><select id="rateHistorySelect" onchange="selectRateHistory(this.value)"></select></div>
            <button class="btn small" onclick="fetchRateHistory(true)">↻ Actualizar histórico</button>
          </div>
          <div id="rateHistoryDetail" class="rate-history-detail"></div>
          <div id="rateHistoryChart" class="rate-chart"></div>
          <div class="rate-history-note">La app guarda automáticamente la tasa publicada cada día y también carga el histórico disponible de BCV Today para que puedas consultar días anteriores.</div>
        </div>
      </div>
"""
assert 'rate-history-grid' not in s
s=s.replace('    .notice{',css+'    .notice{',1)
s=s.replace('      <div class="card panel" style="margin-top:18px"><div class="panel-head"><h3>Últimos movimientos</h3>',html+'      <div class="card panel" style="margin-top:18px"><div class="panel-head"><h3>Últimos movimientos</h3>',1)
s=s.replace("function defaultState(){return {balances:{VES:0,USD:0},transactions:[],categories:[...DEFAULT_CATEGORIES],theme:'verde',mode:'light',rate:null,profile:{username:'Mi usuario',photo:null}};}",
"function defaultState(){return {balances:{VES:0,USD:0},transactions:[],categories:[...DEFAULT_CATEGORIES],theme:'verde',mode:'light',rate:null,rateHistory:[],profile:{username:'Mi usuario',photo:null}};}",1)
s=s.replace("function loadState(){try{const saved=JSON.parse(localStorage.getItem(STORAGE)||'{}');const base=defaultState();return {...base,...saved,profile:{...base.profile,...(saved.profile||{})}};}catch{return defaultState();}}",
"function loadState(){try{const saved=JSON.parse(localStorage.getItem(STORAGE)||'{}');const base=defaultState();return {...base,...saved,rateHistory:Array.isArray(saved.rateHistory)?saved.rateHistory:[],profile:{...base.profile,...(saved.profile||{})}};}catch{return defaultState();}}",1)
s=s.replace("let movementType = 'income';","let movementType = 'income';\nlet selectedRateDate = null;\nlet lastHistoryFetchAt = 0;\nlet lastRateFetchAt = 0;",1)
helpers=r'''
function localDateKey(value){
  const s=String(value||'').trim();
  let m=s.match(/^(\d{4})[-/](\d{1,2})[-/](\d{1,2})$/);
  if(m)return m[1]+'-'+String(m[2]).padStart(2,'0')+'-'+String(m[3]).padStart(2,'0');
  m=s.match(/^(\d{1,2})[-/](\d{1,2})[-/](\d{4})$/);
  if(m)return m[3]+'-'+String(m[2]).padStart(2,'0')+'-'+String(m[1]).padStart(2,'0');
  return '';
}
function recordRateHistory(record){
  if(!record?.USD||!record?.date)return;
  state.rateHistory=Array.isArray(state.rateHistory)?state.rateHistory:[];
  const clean={date:record.date,USD:Number(record.USD),updated_at:record.updated_at||null,effective_date:record.effective_date||record.date};
  const idx=state.rateHistory.findIndex(x=>x.date===clean.date);
  if(idx>=0)state.rateHistory[idx]={...state.rateHistory[idx],...clean};else state.rateHistory.push(clean);
  state.rateHistory.sort((a,b)=>b.date.localeCompare(a.date)); state.rateHistory=state.rateHistory.slice(0,1830);
}
function formatRateDate(date){return date?new Intl.DateTimeFormat('es-VE',{day:'2-digit',month:'short',year:'numeric'}).format(new Date(date+'T12:00:00')):'—';}
function rateDelta(rec,list){
  if(!rec)return {kind:'flat',text:'—'}; const older=list.filter(x=>x.date<rec.date)[0];
  if(!older||!older.USD)return {kind:'flat',text:'Sin día anterior'}; const diff=rec.USD-Number(older.USD); const pct=(diff/Number(older.USD))*100;
  if(Math.abs(diff)<0.0000001)return {kind:'flat',text:'0,00%'}; const sign=diff>0?'▲':'▼';
  return {kind:diff>0?'up':'down',text:sign+' '+Math.abs(diff).toLocaleString('es-VE',{minimumFractionDigits:2,maximumFractionDigits:4})+' Bs ('+Math.abs(pct).toLocaleString('es-VE',{minimumFractionDigits:2,maximumFractionDigits:2})+'%)'};
}
function renderRateHistory(){
  const list=[...(state.rateHistory||[])].filter(x=>x&&x.date&&Number(x.USD)>0).sort((a,b)=>b.date.localeCompare(a.date));
  const select=document.getElementById('rateHistorySelect'),summary=document.getElementById('rateHistorySummary'),detail=document.getElementById('rateHistoryDetail'),chart=document.getElementById('rateHistoryChart');
  if(!select||!summary||!detail||!chart)return;
  if(!list.length){select.innerHTML='';summary.textContent='Sin datos';detail.innerHTML='<div class="rate-history-empty">Todavía no hay histórico guardado. Con una conexión, la app cargará los datos anteriores y seguirá guardando las nuevas tasas automáticamente.</div>';chart.innerHTML='';return;}
  const currentDate=localDateKey(state.rate?.date)||list[0].date;
  if(!selectedRateDate||!list.some(x=>x.date===selectedRateDate))selectedRateDate=currentDate&&list.some(x=>x.date===currentDate)?currentDate:list[0].date;
  select.innerHTML=list.slice(0,90).map(x=>'<option value="'+x.date+'" '+(x.date===selectedRateDate?'selected':'')+'>'+formatRateDate(x.date)+' · '+Number(x.USD).toLocaleString('es-VE',{minimumFractionDigits:2,maximumFractionDigits:4})+' Bs</option>').join('');
  summary.textContent=list.length.toLocaleString('es-VE')+' día'+(list.length===1?'':'s')+' disponible'+(list.length===1?'':'s');
  const rec=list.find(x=>x.date===selectedRateDate)||list[0],delta=rateDelta(rec,list);
  detail.innerHTML='<div class="rate-detail-box"><span class="field-hint">Tasa del '+formatRateDate(rec.date)+'</span><strong>'+Number(rec.USD).toLocaleString('es-VE',{minimumFractionDigits:2,maximumFractionDigits:4})+' Bs</strong><small>1 USD = '+Number(rec.USD).toLocaleString('es-VE',{minimumFractionDigits:2,maximumFractionDigits:4})+' Bs</small></div><div class="rate-detail-box"><span class="field-hint">Variación vs. día anterior</span><strong class="rate-delta-'+delta.kind+'">'+delta.text+'</strong><small>'+(rec.effective_date&&rec.effective_date!==rec.date?'Vigencia BCV: '+formatRateDate(rec.effective_date):'Dato histórico diario')+'</small></div>';
  const chartList=list.slice(0,14).reverse(),min=Math.min(...chartList.map(x=>Number(x.USD))),max=Math.max(...chartList.map(x=>Number(x.USD))),span=Math.max(max-min,0.0001);
  chart.innerHTML=chartList.map(x=>'<div class="rate-chart-row"><div class="rate-chart-date">'+formatRateDate(x.date)+'</div><div class="rate-chart-bar" style="width:'+(18+((Number(x.USD)-min)/span)*82)+'%"></div><div class="rate-chart-value">'+Number(x.USD).toLocaleString('es-VE',{minimumFractionDigits:2,maximumFractionDigits:2})+'</div></div>').join('');
}
function selectRateHistory(date){selectedRateDate=date;renderRateHistory();}
async function fetchRateHistory(manual=false){
  if(!manual&&Date.now()-lastHistoryFetchAt<60*60*1000&&state.rateHistory?.length)return;
  try{
    const r=await fetch('https://bcv.today/api/v1/history.json',{cache:'no-store'}); if(!r.ok)throw new Error('HTTP '+r.status); const data=await r.json();
    const rows=Array.isArray(data)?data:(Array.isArray(data?.history)?data.history:(Array.isArray(data?.data)?data.data:[]));
    for(const row of rows){const date=localDateKey(row?.date||row?.effective_date),usd=Number(row?.USD||row?.usd||0);if(date&&usd>0)recordRateHistory({date,USD:usd,updated_at:row?.updated_at||null,effective_date:localDateKey(row?.effective_date)||date});}
    lastHistoryFetchAt=Date.now();localStorage.setItem(STORAGE,JSON.stringify(state));renderRateHistory();if(manual)showToast('Histórico BCV actualizado');
  }catch(err){renderRateHistory();if(manual)showToast('No se pudo actualizar el histórico');}
}
'''
s=s.replace("async function fetchRate(manual=false){",helpers+"\nasync function fetchRate(manual=false){",1)
old="""async function fetchRate(manual=false){const status=document.getElementById('rateStatus');status.textContent='Actualizando…';try{const r=await fetch(BCV_URL,{cache:'no-store'});if(!r.ok)throw new Error('HTTP '+r.status);const data=await r.json();if(!data.USD)throw new Error('Sin USD');currentRate=Number(data.USD);state.rate={USD:currentRate,date:data.date||null,updated_at:data.updated_at||null};saveState();status.textContent='Actualizada';if(manual)showToast('Tasa BCV actualizada');}catch(err){currentRate=Number(state.rate?.USD||currentRate||0);status.textContent=currentRate?'Último valor guardado':'Sin conexión';if(manual)showToast('No se pudo actualizar la tasa');}updateRateUI();updateConversion();}"""
new="""async function fetchRate(manual=false){const status=document.getElementById('rateStatus');status.textContent='Actualizando…';lastRateFetchAt=Date.now();try{const r=await fetch(BCV_URL,{cache:'no-store'});if(!r.ok)throw new Error('HTTP '+r.status);const data=await r.json();if(!data.USD)throw new Error('Sin USD');currentRate=Number(data.USD);const date=localDateKey(data.date||data.effective_date)||new Date().toISOString().slice(0,10);state.rate={USD:currentRate,date,updated_at:data.updated_at||null,effective_date:data.effective_date||date};recordRateHistory({date,USD:currentRate,updated_at:data.updated_at||null,effective_date:localDateKey(data.effective_date)||date});saveState();status.textContent='Actualizada';if(manual)showToast('Tasa BCV actualizada');}catch(err){currentRate=Number(state.rate?.USD||currentRate||0);status.textContent=currentRate?'Último valor guardado':'Sin conexión';if(manual)showToast('No se pudo actualizar la tasa');}updateRateUI();updateConversion();renderRateHistory();fetchRateHistory(false);}"""
assert old in s
s=s.replace(old,new,1)
s=s.replace("renderCategories();renderThemes();renderProfile();updateConversion();updateRateUI();}","renderCategories();renderThemes();renderProfile();updateConversion();updateRateUI();renderRateHistory();}",1)
s=s.replace("state=defaultState();currentRate=0;render();fetchRate(false);showToast('App reiniciada');","state=defaultState();currentRate=0;selectedRateDate=null;lastHistoryFetchAt=0;render();fetchRate(false);fetchRateHistory(true);showToast('App reiniciada');",1)
s=s.replace("render();
fetchRate(false);
setInterval(()=>fetchRate(false),5*60*1000);","render();
fetchRate(false);
fetchRateHistory(false);
setInterval(()=>fetchRate(false),5*60*1000);
document.addEventListener('visibilitychange',()=>{if(document.visibilityState==='visible'&&Date.now()-lastRateFetchAt>60*1000)fetchRate(false);});",1)
path.write_text(s,encoding='utf-8')
print("patched",path)
