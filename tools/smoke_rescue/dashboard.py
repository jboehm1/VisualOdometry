#!/usr/bin/env python3
"""Produce offline VECTOR VIO Smoke Rescue *RESEARCH* evidence dashboard.

No external scripts, sensor command, medical/firefighter navigation advice or
runtime network requests. Displays real source evidence and honestly labels
posthoc pseudo-reference score, unknown space and clock calibration limits.
"""
from __future__ import annotations
import argparse
from pathlib import Path
import json
import math


def load_json(path):
    value=json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value,dict):
        raise ValueError("Expected result JSON object")
    return value


def collect(short,full,marker=None,clock=None,corrected=None):
    if short.get("actual_own_estimator_executed") is not True or full.get("actual_own_estimator_executed") is not True:
        raise ValueError("Only actual executed research estimator reports accepted")
    if short.get("source_sha256")!=full.get("source_sha256"):
        raise ValueError("Source originals differ: comparison prohibited")
    if short.get("reference_used_during_estimation") is not False or full.get("reference_used_during_estimation") is not False:
        raise ValueError("Ground-truth leak not allowed")
    def clean(d):
        track=d.get("trajectory")
        cells=d.get("positive_evidence_grid",{}).get("cells",[])
        if not isinstance(track,list) or not 5<=len(track)<=50000:
            raise ValueError("Trajectory missing or unbounded")
        if not isinstance(cells,list) or len(cells)>150000:
            raise ValueError("Radar evidence grid invalid/oversized")
        for p in track:
            for x in ("x","y","t","yaw"):
                if not math.isfinite(float(p[x])):raise ValueError("Nonfinite trajectory")
        for cell in cells:
            if len(cell)!=3 or not all(isinstance(z,int) for z in cell) or cell[2]<=0:
                raise ValueError("Invalid grid cell")
        return {
            "method":d.get("mode"),
            "track":[{k:p[k] for k in ("x","y","t","yaw","new_segment")} for p in track],
            "cells":cells,"resolution_m":d.get("positive_evidence_grid",{}).get("resolution_m",.25),
            "motion":d.get("motion_observability",{}),
            "posthoc":d.get("pseudo_reference_posthoc_evaluation",{}),
            "map":d.get("sparse_positive_echo_map",{}),
            "clock":d.get("sensor_source",{}).get("gyro_clock_mode","bag arrival time"),
            "original_dataset_sha256":d["source_sha256"]
        }
    return {
        "short":clean(short),"full":clean(full),
        "marker":marker.get("score") if marker else None,
        "clock_audit":{
            "missing_tf":clock.get("sensor_frames_missing_base_link_tf",[]),
            "imu_clock":clock.get("imu_uptime_to_unix",{}),
            "tf_dynamic_warning":clock.get("potentially_dynamic_extrinsics",[])
        } if clock else None,
        "corrected":clean(corrected) if corrected else None,
        "meta":{"dataset":"UPO Smoke • Original 2.18 GB • Research Experiment 7",
                "license":"CC BY-NC-SA 3.0",
                "warning":"RECHERCHE — Pas un équipement pompier, aucune zone sécurisée démontrée.",
                "metric_note":"RMSE après alignement SE2 a posteriori avec une référence partiellement basée sur l'odométrie du robot; aucune validation indépendante absolue."}
    }


def render(data):
    blob=json.dumps(data,ensure_ascii=False,allow_nan=False,separators=(",",":"))
    # Escape HTML parser-sensitive characters even inside <script type=application/json>.
    blob=blob.replace("&","\\u0026").replace("<","\\u003c").replace(">","\\u003e").replace("\u2028","\\u2028")
    page=r"""<!DOCTYPE html><html lang="fr"><head><meta charset="utf-8"/><meta name="viewport" content="width=device-width,initial-scale=1"/>
<title>VECTOR VIO • Smoke Rescue Laboratory</title>
<style>
:root{color-scheme:dark;--bg:#08141f;--panel:#102332;--line:#294353;--text:#e8f1f5;--sub:#a2bfcd;--cyan:#5fe0d2;--orange:#ffa068;--red:#f17472;--green:#9fe3af}
*{box-sizing:border-box}html,body{margin:0;min-height:100%;background:radial-gradient(circle at 80% 0,#173b4a,#08141f 53%);color:var(--text);font:15px/1.5 system-ui,Arial,sans-serif}
main{margin:auto;max-width:1480px;padding:18px 28px 65px}.eyebrow{font-size:.72rem;font-weight:800;letter-spacing:2px;color:var(--cyan);margin:0 0 4px}.title{display:flex;justify-content:space-between;flex-wrap:wrap;align-items:end;gap:12px}
h1{font-size:clamp(26px,3vw,39px);letter-spacing:-.7px;margin:0}h2{margin:0 0 14px;font-size:1.03rem}p{margin:5px 0}.subtitle{color:var(--sub);max-width:900px}
.pill{background:#173747;color:var(--cyan);border:1px solid #316778;padding:8px 13px;border-radius:30px;font-weight:700}
.warning{margin:20px 0;border:1px solid #aa6560;background:#3c262d;color:#ffe3de;border-left:5px solid var(--red);border-radius:10px;padding:12px 17px}
.toolbar{display:flex;align-items:center;flex-wrap:wrap;gap:9px;padding:13px 0}
button,.select{color:var(--text);border:1px solid var(--line);background:#183243;padding:8px 13px;cursor:pointer;border-radius:9px;font-weight:650}
button.active{color:#07252c;background:var(--cyan);border-color:var(--cyan)}button:focus-visible,input:focus-visible{outline:2px solid #a5f5ed}
.grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:12px}.card{border:1px solid var(--line);background:#102332df;border-radius:13px;padding:16px;min-width:0}
.card small{display:block;color:var(--sub);font-size:.77rem}.number{font-size:clamp(20px,2.3vw,31px);font-weight:750;line-height:1.45;letter-spacing:-.6px}
.bad{color:var(--red)}.good{color:var(--green)}.orange{color:var(--orange)}.cyan{color:var(--cyan)}.layout{display:grid;grid-template-columns:minmax(0,2fr) minmax(260px,1fr);gap:14px;margin-top:14px}
.figure{padding:14px}.mapTop{display:flex;justify-content:space-between;flex-wrap:wrap;gap:4px;margin-bottom:10px}
canvas{width:100%;aspect-ratio:1.6;display:block;background:#091b29;border:1px solid #355364;border-radius:10px;touch-action:none}.legend{display:flex;flex-wrap:wrap;gap:18px;font-size:.79rem;color:var(--sub);margin-top:8px}.key{display:inline-block;width:11px;height:11px;border-radius:3px;margin-right:6px}
.slider{width:100%;accent-color:#5fe0d2;margin-top:12px}.tools{display:flex;align-items:center;gap:10px;flex-wrap:wrap;margin-top:5px;color:var(--sub)}
.fact{display:flex;justify-content:space-between;border-bottom:1px solid var(--line);padding:8px 0;gap:12px}.fact b{text-align:right;color:var(--text)}
.info{font-size:.81rem;color:var(--sub)}.row{display:flex;justify-content:space-between;gap:14px;align-items:center;margin:8px 0}.barOuter{background:#203b4c;border-radius:5px;height:10px;overflow:hidden}.bar{height:100%;background:var(--orange)}
footer{font-size:.76rem;color:var(--sub);margin-top:20px;word-break:break-word}
@media(max-width:950px){.grid{grid-template-columns:repeat(2,1fr)}.layout{grid-template-columns:1fr}}
@media(max-width:560px){main{padding:12px}.grid{grid-template-columns:repeat(2,1fr)}.card{padding:11px}.number{font-size:19px}canvas{aspect-ratio:1.15}}
</style></head><body><main>
<header><p class="eyebrow">VECTOR VIO / RESEARCH PLATFORM</p><div class="title"><div><h1>Smoke Rescue <span class="cyan">Laboratory</span></h1><p class="subtitle">Mesures radar réelles dans la fumée • Odométrie Doppler–IMU • Carte d'échos positifs et incertitudes.</p></div><span class="pill">OFFLINE • DONNÉES RÉELLES</span></div></header>
<div class="warning"><strong>⚠ RECHERCHE UNIQUEMENT — JAMAIS POUR GUIDER DES POMPIERS.</strong> Les zones non observées sont inconnues, pas libres. Carte non calibrée, aucune issue ni personne confirmée. Les métriques de position sont des évaluations a posteriori sur une référence partiellement odométrique.</div>
<div class="toolbar"><span class="info">Fenêtre enregistrée :</span><button class="active" id="short" type="button">60 s</button><button id="full" type="button">168 s</button><button id="corrected" type="button" style="display:none">IMU synchronisée</button><span class="info" id="source">Radar TI / gyroscope Ouster</span></div>
<section class="grid" aria-label="Indicateurs expérimentaux">
<div class="card"><small>ATE RMSE / référence partielle</small><div class="number bad" id="ate">—</div><small>Alignement rigide SE(2) après estimation</small></div>
<div class="card"><small>Référence immobile (RMSE)</small><div class="number" id="still">—</div><small id="comparison">Même protocole de mesure</small></div>
<div class="card"><small>Observabilité Doppler</small><div class="number cyan" id="accepted">—</div><small>Fenêtres de vitesse retenues</small></div>
<div class="card"><small>Échos radar projetés</small><div class="number orange" id="echoes">—</div><small id="cells">Carte positive seulement</small></div>
</section>
<div class="layout">
<section class="card figure"><div class="mapTop"><h2>Carte 2D : trajectoire calculée et retours radar</h2><small class="info">Glisser pour déplacer • molette pour zoomer</small></div>
<canvas id="map" width="1100" height="680" role="img" aria-label="Carte de laboratoire à résolution 25 centimètres : trajectoire et détections radar, sans classification du vide"></canvas>
<div class="legend"><span><i class="key" style="background:var(--orange)"></i>Échos radar accumulés (ensemble de la séquence)</span><span><i class="key" style="background:var(--cyan)"></i>Trajectoire Doppler–IMU</span><span><i class="key" style="background:white"></i>Position estimée</span></div>
<input class="slider" type="range" min="0" max="1000" value="0" id="progress" aria-label="Lire la trajectoire dans le temps"/>
<div class="tools"><button id="play" type="button" class="active">▶ Lire</button><button id="reset" type="button">↺ Recentrer</button><strong id="cursor">T +0 s</strong><span class="info">Les cellules radar correspondent à toute la séquence et ne suivent pas le curseur.</span></div>
</section>
<aside class="card">
<h2>Contrôles de qualité</h2>
<div class="fact"><span>Temps effectivement intégré</span><b id="integrated">—</b></div>
<div class="fact"><span>Distance parcourue estimée</span><b id="distance">—</b></div>
<div class="fact"><span>Échantillons de référence appariés</span><b id="pairs">—</b></div>
<div class="fact"><span>Erreur P95 posthoc</span><b id="p95">—</b></div>
<div class="fact"><span>Distance avec référence (mêmes segments)</span><b id="pathgap">—</b></div>
<div class="fact"><span>Cellules radar observées</span><b id="numcells">—</b></div>
<div class="fact"><span>Horloge IMU</span><b id="clock">—</b></div>
<div class="fact"><span>TF radar↔IMU manquant</span><b id="tf">Non vérifié</b></div>
<div class="fact"><span>Évaluation marqueurs réservés</span><b id="marker">Non disponible</b></div>
<h2 style="margin-top:22px">Interprétation</h2><p class="info" id="risk">—</p>
<p class="info" style="margin-top:12px">Aucune fusion thermique n'est évaluée par ce dataset. Aucune densité de fumée physique ou rappel des obstacles n'est démontré.</p>
</aside></div>
<div class="card" style="margin-top:14px"><h2>Comparaison mesurée</h2><div class="row"><span>Erreur de position radar + IMU (posthoc)</span><b id="radartxt">—</b></div><div class="barOuter"><div id="radarbar" class="bar"></div></div>
<div class="row"><span>Position immobile (même référence)</span><b id="statictext">—</b></div><div class="barOuter"><div id="staticbar" class="bar" style="background:#7799a9"></div></div>
<p class="info" style="margin-top:10px">Ces barres sont descriptives et relatives à la même fenêtre. Aucun score ne valide une navigation en conditions d'incendie.</p></div>
<footer><p><b>Source :</b> UPO Smoke Experiment 7 • CC BY-NC-SA 3.0 • Projet VECTOR VIO • Algorithme expérimental ouvert • données originales non incluses dans ce fichier.</p><p id="hash">SHA256 source originale : —</p><p>Carte = échos positifs géométriquement projetés sur une pose non calibrée. L'absence d'écho n'est pas la preuve d'un espace libre. Zéro garantie de sécurité.</p></footer>
</main><script type="application/json" id="data">__DATA__</script><script>
"use strict";
const R=JSON.parse(document.getElementById('data').textContent);
let mode='short',fraction=0,playing=false,view={zoom:1,dx:0,dy:0};
const $=id=>document.getElementById(id);
const fmt=(n,d=2)=>Number.isFinite(Number(n))?Number(n).toFixed(d):'—';
const cv=$('map'),ctx=cv.getContext('2d');
function current(){return R[mode]}
function setMetric(id,value){$(id).textContent=value}
function update(){
  const d=current(),p=d.posthoc||{},m=d.motion||{},cells=d.map||{};
  for(const key of ['short','full','corrected'])$(key).classList.toggle('active',key===mode);
  setMetric('ate',fmt(p.ate_rmse_m_vs_pseudo_reference)+' m');
  setMetric('still',fmt(p.zero_motion_baseline_rmse_m)+' m');
  setMetric('accepted',fmt(m.accepted_fraction*100,1)+' %');
  setMetric('echoes',Number(cells.positive_real_radar_echoes_projected||0).toLocaleString('fr-FR'));
  setMetric('cells',(cells.occupied_evidence_cells||0)+' cellules de 25 cm (observations seulement)');
  setMetric('integrated',fmt(m.active_integrated_seconds,1)+' s');
  setMetric('distance',fmt(m.estimated_traveled_path_m)+' m');
  setMetric('pairs',String(p.pairs??0));
  setMetric('p95',fmt(p.ate_p95_m_vs_pseudo_reference)+' m');
  const est=p.pathlength_over_matched_contiguous_intervals_estimated_m,gt=p.pathlength_over_matched_contiguous_intervals_pseudo_reference_m;
  setMetric('pathgap',fmt(est)+' / '+fmt(gt)+' m');
  setMetric('numcells',String(cells.occupied_evidence_cells||0));
  setMetric('clock',d.clock||'Non calibrée');
  setMetric('tf',R.clock_audit?(R.clock_audit.missing_tf.length?R.clock_audit.missing_tf.join(', '):'Chemins découverts (non validés)'):'Pas de TF vérifiée');
  const mark=R.marker||{};
  setMetric('marker',mark.heldout_aligned_rmse_m!=null?fmt(mark.heldout_aligned_rmse_m)+' m sur '+mark.heldout_pairs+' échantillons':'Aucun score exploitable');
  const fail=(p.ate_rmse_over_stationary_baseline||0)>1;
  setMetric('risk',fail?'Le radar + IMU fait PIRE qu’une position immobile sur cette fenêtre. La dérive rend la navigation non fiable.':
       'Les observations contiennent un signal de mouvement, mais la précision reste exploratoire et non certifiée.');
  setMetric('radartxt',fmt(p.ate_rmse_m_vs_pseudo_reference)+' m');
  setMetric('statictext',fmt(p.zero_motion_baseline_rmse_m)+' m');
  const max=Math.max(p.ate_rmse_m_vs_pseudo_reference||0,p.zero_motion_baseline_rmse_m||0,.01);
  $('radarbar').style.width=100*(p.ate_rmse_m_vs_pseudo_reference||0)/max+'%';
  $('staticbar').style.width=100*(p.zero_motion_baseline_rmse_m||0)/max+'%';
  $('hash').textContent='SHA256 source originale : '+d.original_dataset_sha256;
  draw();
}
function worldBounds(d){
  let x=[],y=[];
  d.track.forEach(p=>{x.push(p.x);y.push(p.y)});
  const res=d.resolution_m||.25;
  d.cells.forEach(p=>{x.push(p[0]*res);y.push(p[1]*res)});
  let minx=Math.min(...x),maxx=Math.max(...x),miny=Math.min(...y),maxy=Math.max(...y);
  let range=Math.max(maxx-minx,maxy-miny,1);
  return {minx,maxx,miny,maxy,scale:Math.min((cv.width-90)/range,(cv.height-90)/range)};
}
function draw(){
  const d=current(),c=ctx,W=cv.width,H=cv.height;
  c.clearRect(0,0,W,H);c.fillStyle='#091b29';c.fillRect(0,0,W,H);
  const b=worldBounds(d),res=d.resolution_m||.25,z=b.scale*view.zoom;
  const cx=(b.minx+b.maxx)/2,cy=(b.miny+b.maxy)/2;
  const project=(x,y)=>[W/2+(x-cx)*z+view.dx,H/2-(y-cy)*z+view.dy];
  const [oX,oY]=project(0,0);
  c.strokeStyle='#19384c';c.lineWidth=1;c.beginPath();
  const gridstep=Math.max(.5,Math.pow(2,Math.ceil(Math.log2(65/z))));
  for(let xx=Math.floor((b.minx-10)/gridstep)*gridstep;xx<=b.maxx+10;xx+=gridstep){
    const xp=project(xx,0)[0];c.moveTo(xp,0);c.lineTo(xp,H)
  }
  for(let yy=Math.floor((b.miny-10)/gridstep)*gridstep;yy<=b.maxy+10;yy+=gridstep){
    const yp=project(0,yy)[1];c.moveTo(0,yp);c.lineTo(W,yp)
  }
  c.stroke();
  c.fillStyle='#aabccb';c.font='13px system-ui';c.fillText('Recherche • mètres relatifs • aucun nord absolu',18,25);
  // Grid is accumulated from WHOLE recording: do not imply it is time-synchronized with cursor.
  for(const [ix,iy,count] of d.cells){
    const [x,y]=project(ix*res,(iy+1)*res),sz=Math.max(1.3,res*z);
    c.fillStyle='rgba(255,160,103,'+Math.min(.9,.25+Math.log1p(count)*.15)+')';c.fillRect(x,y,sz,sz)
  }
  const pts=d.track;const maxI=Math.min(pts.length-1,Math.max(0,Math.round(fraction*(pts.length-1))));
  c.lineWidth=2.4;c.strokeStyle='#59e6d4';c.beginPath();
  for(let i=0;i<=maxI;i++){
    let [x,y]=project(pts[i].x,pts[i].y);
    if(i===0||pts[i].new_segment)c.moveTo(x,y);else c.lineTo(x,y)
  }
  c.stroke();
  if(pts.length){
    const p=pts[maxI];const [x,y]=project(p.x,p.y);
    c.fillStyle='#fff';c.strokeStyle='#142637';c.lineWidth=3;c.beginPath();c.arc(x,y,6,0,2*Math.PI);c.fill();c.stroke();
    c.strokeStyle='#fff';c.lineWidth=2;c.beginPath();c.moveTo(x,y);
    c.lineTo(x+23*Math.cos(p.yaw),y-23*Math.sin(p.yaw));c.stroke();
    $('cursor').textContent='T +'+fmt(p.t-pts[0].t,1)+' s • '+(maxI+1)+' / '+pts.length+' poses';
  }
  c.fillStyle='rgba(9,23,34,.85)';c.fillRect(15,H-39,200,27);
  c.fillStyle='#d5eaf2';c.font='14px system-ui';c.fillText('0 ───── '+fmt(100/Math.max(z,1),1)+' m',28,H-20);
}
function setMode(m){if(!R[m])return;mode=m;playing=false;fraction=0;$('progress').value=0;$('play').textContent='▶ Lire';view={zoom:1,dx:0,dy:0};update()}
['short','full','corrected'].forEach(k=>$(k).addEventListener('click',()=>setMode(k)));
if(R.corrected)$('corrected').style.display='';
$('progress').addEventListener('input',e=>{fraction=Number(e.target.value)/1000;draw()});
$('play').addEventListener('click',()=>{playing=!playing;$('play').textContent=playing?'Ⅱ Pause':'▶ Lire';if(playing&&fraction>=1)fraction=0});
$('reset').addEventListener('click',()=>{view={zoom:1,dx:0,dy:0};draw()});
let last=0;
function tick(t){if(playing){const dt=last?Math.min(t-last,100):0;fraction=Math.min(1,fraction+dt/28000);$('progress').value=String(Math.round(fraction*1000));draw();if(fraction>=1){playing=false;$('play').textContent='▶ Lire'}}last=t;requestAnimationFrame(tick)}
requestAnimationFrame(tick);
cv.addEventListener('wheel',e=>{e.preventDefault();view.zoom=Math.max(.15,Math.min(14,view.zoom*(e.deltaY<0?1.13:.87)));draw()},{passive:false});
let drag=null;cv.addEventListener('pointerdown',e=>{drag=[e.clientX,e.clientY];cv.setPointerCapture(e.pointerId)});
cv.addEventListener('pointermove',e=>{if(drag){let rect=cv.getBoundingClientRect();view.dx+=(e.clientX-drag[0])*cv.width/rect.width;view.dy+=(e.clientY-drag[1])*cv.height/rect.height;drag=[e.clientX,e.clientY];draw()}});
cv.addEventListener('pointerup',()=>drag=null);
cv.addEventListener('pointercancel',()=>drag=null);
setMode('full');
</script></body></html>"""
    return page.replace("__DATA__",blob)


def main(argv=None):
    p=argparse.ArgumentParser(description="Research-only self-contained metric dashboard from authentic radar/IMU reports")
    p.add_argument("--short",required=True,type=Path)
    p.add_argument("--full",required=True,type=Path)
    p.add_argument("--marker",type=Path)
    p.add_argument("--clock",type=Path)
    p.add_argument("--corrected",type=Path)
    p.add_argument("--out",required=True,type=Path)
    a=p.parse_args(argv)
    data=collect(load_json(a.short),load_json(a.full),
                 load_json(a.marker) if a.marker else None,
                 load_json(a.clock) if a.clock else None,
                 load_json(a.corrected) if a.corrected else None)
    a.out.parent.mkdir(parents=True,exist_ok=True)
    a.out.write_text(render(data),encoding="utf-8")
    print(json.dumps({"status":"RESEARCH_HTML_COMPLETE", "output":str(a.out),
                      "source_sha256":data["full"]["original_dataset_sha256"],
                      "mode":"actual_recorded_radar_research_only"}))
    return 0


if __name__=="__main__":
    raise SystemExit(main())
