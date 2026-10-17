"""Plot measured Java replay output; never synthesize estimated poses or landmarks."""
import argparse, json, hashlib
from pathlib import Path
import numpy as np
import matplotlib
matplotlib.use('Agg')
import matplotlib.pyplot as plt


def load(folder):
    return {name: np.genfromtxt(folder/f'{name}.csv', delimiter=',', names=True) for name in ['trajectory','loops','landmarks','reconstructed_path']}


def metrics(d):
    t,p=d['trajectory'],d['landmarks'];last=d['loops'][-1]
    valid=t['metric_initialized']>0
    return dict(truth_distance_m=float(last['truth_distance_m']),estimated_distance_m=float(last['estimated_distance_m']),distance_error_pct=float(last['distance_error_pct']),closure_error_m=float(last['closure_error_m']),position_rmse_m=float(last['position_rmse_m']),landmark_median_surface_error_m=float(np.median(p['nearest_truth_surface_m'])),landmark_p90_surface_error_m=float(np.quantile(p['nearest_truth_surface_m'],.9)),truth_speed_m_s=float(np.mean(t['truth_speed_m_s'][valid])),estimated_speed_m_s=float(np.mean(t['estimated_speed_m_s'][valid])),speed_rmse_m_s=float(np.mean((t['estimated_speed_m_s'][valid]-t['truth_speed_m_s'][valid])**2)**.5),scale_initialization_s=float(t['time_s'][np.argmax(valid)]),landmarks=len(p))


def walls(config, offset):
    hx,hz,r=config['half_x']+offset,config['half_z']+offset,config['radius']+offset
    pts=[]
    for cx,cz,a in [(hx-r,-hz+r,-np.pi/2),(hx-r,hz-r,0),(-hx+r,hz-r,np.pi/2),(-hx+r,-hz+r,np.pi)]:
        for angle in np.linspace(a,a+np.pi/2,50):pts.append((cx+r*np.cos(angle),cz+r*np.sin(angle)))
    return np.array(pts+[pts[0]])


def world(x,z,c):
    co,si=np.cos(c['start_yaw']),np.sin(c['start_yaw'])
    return c['start_x']+co*x+si*z,c['start_z']-si*x+co*z


def main():
    ap=argparse.ArgumentParser();ap.add_argument('scene_a',type=Path);ap.add_argument('scene_b',type=Path);ap.add_argument('output',type=Path);ap.add_argument('--check',action='store_true');args=ap.parse_args();args.output.mkdir(parents=True,exist_ok=True)
    plt.rcParams.update({'font.size':10,'axes.spines.top':False,'axes.spines.right':False,'figure.facecolor':'#f5f7fa','axes.facecolor':'#ffffff'})
    scenes=[];summary={}
    for label,folder in [('A — mise au point',args.scene_a),('B — validation indépendante, sens inverse',args.scene_b)]:
        d=load(folder);config=json.loads((folder/'scenario.json').read_text());m=metrics(d);scenes.append((label,d,config,m));summary[label]=m
        if args.check:
            assert abs(m['distance_error_pct'])<5,(label,m)
            assert m['closure_error_m']<2,(label,m)
            assert m['position_rmse_m']<1.5,(label,m)
            assert m['speed_rmse_m_s']<.08,(label,m)
            assert m['landmark_median_surface_error_m']<.4,(label,m)
    fig,axes=plt.subplots(2,3,figsize=(15,10),constrained_layout=True)
    for row,(label,d,c,m) in enumerate(scenes):
        t,p,path=d['trajectory'],d['landmarks'],d['reconstructed_path'];tx,tz=world(t['truth_x'],t['truth_z'],c);px,pz=world(path['x'],path['z'],c);mx,mz=world(p['x'],p['z'],c)
        for col in range(3):
            ax=axes[row,col];ax.set_aspect('equal');ax.set_xlim(-c['half_x']-c['half_width']-1,c['half_x']+c['half_width']+1);ax.set_ylim(-c['half_z']-c['half_width']-1,c['half_z']+c['half_width']+1);ax.set_xlabel('X (m)');ax.set_ylabel('Z (m)');ax.grid(alpha=.15)
        ax=axes[row,0]
        for offset in [-c['half_width'],c['half_width']]:w=walls(c,offset);ax.plot(w[:,0],w[:,1],color='#274f7d',lw=2)
        ax.plot(tx[:len(tx)//3+1],tz[:len(tx)//3+1],'--',color='#356eaf',lw=1.5);ax.scatter([c['start_x']],[c['start_z']],marker='s',color='#274f7d',s=35)
        ax.set_title(f'{label}\nCarte de référence — murs + trajet')
        ax=axes[row,1];ax.scatter(mx,mz,c=p['y']+c['start_y'],cmap='viridis',s=3,alpha=.65,vmin=0,vmax=c['height']);ax.plot(px,pz,color='#db5d24',lw=1);ax.set_title('Carte créée par le programme\nNuage 3D projeté + trajectoire, 3 tours')
        ax=axes[row,2]
        for offset in [-c['half_width'],c['half_width']]:w=walls(c,offset);ax.plot(w[:,0],w[:,1],color='#b7c4d2',lw=1)
        ax.plot(tx,tz,'--',color='#274f7d',lw=2,label='Référence');ax.plot(px,pz,color='#df6b2a',lw=1.2,label='Estimée');ax.legend(loc='lower left',fontsize=9);ax.set_title(f'Trajectoires superposées\nDistance {m["distance_error_pct"]:+.2f} % · retour {m["closure_error_m"]:.2f} m')
    fig.suptitle('REPLAY JAVA EXÉCUTÉ — caméra + IMU uniquement pour l’estimateur\nAlignement par la pose initiale seulement : aucune remise à l’échelle ni recalage ajusté après le test.',fontsize=13)
    fig.savefig(args.output/'comparison_maps.png',dpi=160);plt.close(fig)
    fig=plt.figure(figsize=(13,9),layout='constrained')
    for row,(label,d,c,m) in enumerate(scenes):
        p,path=d['landmarks'],d['reconstructed_path'];mx,mz=world(p['x'],p['z'],c);px,pz=world(path['x'],path['z'],c)
        for col in range(2):
            ax=fig.add_subplot(2,2,row*2+col+1,projection='3d');ax.set_xlabel('X (m)');ax.set_ylabel('Z (m)');ax.set_zlabel('Hauteur (m)');ax.set_box_aspect((c['half_x']*2,c['half_z']*2,c['height']));ax.view_init(elev=33,azim=-55)
            if col==0:
                for offset in [-c['half_width'],c['half_width']]:
                    w=walls(c,offset)
                    for y in [0,c['height']]:ax.plot(w[:,0],w[:,1],np.full(len(w),y),color='#416991',lw=1)
                    for i in range(0,len(w),25):ax.plot([w[i,0]]*2,[w[i,1]]*2,[0,c['height']],color='#849bb5',lw=.7)
                ax.set_title(label+'\nGéométrie de référence (sol et plafond inclus)')
            else:
                ax.scatter(mx,mz,p['y']+c['start_y'],c=p['y'],s=2,cmap='viridis',alpha=.65);ax.plot(px,pz,path['y']+c['start_y'],color='#df6b2a',lw=1);ax.set_title(f'Reconstruction réelle — {len(p)} points conservés\nErreur à la surface : médiane {m["landmark_median_surface_error_m"]:.2f} m')
    fig.savefig(args.output/'comparison_3d.png',dpi=160);plt.close(fig)
    fig,axes=plt.subplots(2,2,figsize=(13,7),layout='constrained')
    for row,(label,d,c,m) in enumerate(scenes):
        t=d['trajectory'];axes[row,0].plot(t['time_s'],t['truth_distance_m'],label='Référence',color='#274f7d');axes[row,0].plot(t['time_s'],t['estimated_distance_m'],label='Estimée',color='#df6b2a');axes[row,0].set_ylabel('Distance cumulée (m)');axes[row,0].set_title(label)
        axes[row,1].plot(t['time_s'],t['truth_speed_m_s'],label='Référence',color='#274f7d');axes[row,1].plot(t['time_s'],t['estimated_speed_m_s'],label='Estimée',color='#df6b2a',lw=.8);axes[row,1].set_ylabel('Vitesse (m/s)');axes[row,1].set_ylim(0,1.7)
        for ax in axes[row]:ax.axvspan(0,m['scale_initialization_s'],color='grey',alpha=.13,label='Échelle en initialisation');ax.grid(alpha=.2);ax.set_xlabel('Temps simulé (s)');ax.legend(fontsize=8)
    fig.savefig(args.output/'distance_speed.png',dpi=160);plt.close(fig)
    summary['estimator_sha256']=hashlib.sha256(Path('app/src/main/java/dev/jboehm/vectorvio/SyntheticVision.java').read_bytes()).hexdigest()
    (args.output/'metrics.json').write_text(json.dumps(summary,indent=2,ensure_ascii=False)+'\n');print(json.dumps(summary,indent=2,ensure_ascii=False))

if __name__=='__main__':main()
