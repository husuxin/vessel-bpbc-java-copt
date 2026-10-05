import java.util.*;

/** Branch decisions act on pricing networks, including all future columns. */
public final class Restrictions {
    public enum Family { BERTH, TIME, SHIFT, ARC }
    public record Decision(Family family,int first,int second,double value) {}
    final boolean[][] berth, time, shift, arc;
    private final Instance d;

    public Restrictions(Instance d) {
        this.d=d;
        berth=filled(d.K,d.B); time=new boolean[d.I][];
        for(int i=0;i<d.I;i++) time[i]=d.allowed[i].clone();
        shift=filled(d.I,d.S); arc=filled(d.I+d.S+1,d.I+d.S+1);
    }
    private Restrictions(Restrictions p) {
        d=p.d; berth=copy(p.berth); time=copy(p.time); shift=copy(p.shift); arc=copy(p.arc);
    }
    public Restrictions child(Decision q,boolean one) {
        Restrictions r=new Restrictions(this); int i=q.first(), j=q.second();
        switch(q.family()) {
            case BERTH -> fix(r.berth[i],j,one);
            case TIME -> fix(r.time[i],j,one);
            case SHIFT -> fix(r.shift[i],j,one);
            case ARC -> {
                r.arc[i][j] &= one;
                if(one) {
                    // A physical task is covered once; rest/source/sink may serve many routes.
                    if(i<d.I) for(int k=0;k<r.arc.length;k++) if(k!=j) r.arc[i][k]=false;
                    if(j<d.I) for(int k=0;k<r.arc.length;k++) if(k!=i) r.arc[k][j]=false;
                }
            }
        }
        return r;
    }
    public boolean canStart(int i,int t) {
        if(!time[i][t]) return false;
        for(int s=0;s<d.S;s++) if(shift[i][s] && d.singletonFits(i,t,s)) return true;
        return false;
    }
    public boolean accepts(Routes.Vessel v) {
        return berth[v.vessel()][v.berth()] && canStart(v.vessel(),v.in()) && canStart(v.vessel()+d.K,v.out());
    }
    public boolean accepts(Routes.Pilot p) {
        for(Routes.Task n:p.tasks()) if(!time[n.task()][n.time()] || !shift[n.task()][p.shift()] || !d.inShift(n.time(),p.shift())) return false;
        int[] a=p.activities(d);
        for(int n=1;n<a.length;n++) if(!arc[a[n-1]][a[n]]) return false;
        return true;
    }
    public Decision choose(List<Routes.Vessel> vessels,double[] x,List<Routes.Pilot> pilots,double[] mu) {
        double[][] a=new double[d.K][d.B];
        double[][] b=new double[d.I][d.T], c=new double[d.I][d.S];
        double[][] e=new double[arc.length][arc.length];
        for(int n=0;n<x.length;n++) {
            Routes.Vessel v=vessels.get(n);
            a[v.vessel()][v.berth()]+=x[n]; b[v.vessel()][v.in()]+=x[n]; b[v.vessel()+d.K][v.out()]+=x[n];
        }
        for(int n=0;n<mu.length;n++) if(mu[n]>1e-8) {
            Routes.Pilot p=pilots.get(n);
            for(Routes.Task task:p.tasks()) c[task.task()][p.shift()]+=mu[n];
            int[] path=p.activities(d);
            for(int k=1;k<path.length;k++) e[path[k-1]][path[k]]+=mu[n];
        }
        Decision q=closest(Family.BERTH,a,false);
        if(q==null) q=closest(Family.TIME,b,false);
        if(q==null) q=closest(Family.SHIFT,c,false);
        if(q==null) q=closest(Family.ARC,e,true);
        return q;
    }
    private Decision closest(Family f,double[][] a,boolean onlyTaskArcs) {
        Decision best=null; double distance=Double.POSITIVE_INFINITY;
        for(int i=0;i<a.length;i++) for(int j=0;j<a[i].length;j++) {
            if(onlyTaskArcs && i>=d.I && j>=d.I) continue;
            double v=a[i][j];
            if(v>1e-6 && v<1-1e-6 && Math.abs(v-.5)<distance) {
                best=new Decision(f,i,j,v); distance=Math.abs(v-.5);
            }
        }
        return best;
    }
    private static void fix(boolean[] a,int k,boolean one) {
        if(!one) a[k]=false;
        else for(int n=0;n<a.length;n++) if(n!=k) a[n]=false;
    }
    private static boolean[][] filled(int n,int m) {
        boolean[][] a=new boolean[n][m]; for(boolean[] row:a) Arrays.fill(row,true); return a;
    }
    private static boolean[][] copy(boolean[][] a) {
        boolean[][] b=new boolean[a.length][]; for(int n=0;n<a.length;n++) b[n]=a[n].clone(); return b;
    }
}
