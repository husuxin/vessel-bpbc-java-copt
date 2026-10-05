import java.util.*;

/** EC.2: forward/backward label correcting and rest joining, O(|H_s|^2). */
public final class PilotPricing {
    private static final double INF=Double.POSITIVE_INFINITY;

    public static Routes.Pilot price(Instance d, Restrictions r, int shift, double[] dual,
            boolean phase, double[] demand, boolean sparse) {
        List<Routes.Task> nodes=new ArrayList<>();
        for(int i=0;i<d.I;i++) if(r.shift[i][shift])
            for(int t=d.window[i][0];t<=d.window[i][1];t++)
                if(r.time[i][t] && d.inShift(t,shift) && (!sparse || demand[d.key(i,t)]>1e-9))
                    nodes.add(new Routes.Task(i,t));
        nodes.sort(Comparator.comparingInt(Routes.Task::time).thenComparingInt(Routes.Task::task));
        int n=nodes.size(), rest=d.I+shift, source=d.I+d.S;
        double[] forward=new double[n], backward=new double[n];
        int[] parent=new int[n], next=new int[n];
        Arrays.fill(forward,INF); Arrays.fill(backward,INF);
        Arrays.fill(parent,-1); Arrays.fill(next,-1);
        boolean[] before=new boolean[n], after=new boolean[n];
        for(int u=0;u<n;u++) {
            Routes.Task x=nodes.get(u); int i=x.task(),t=x.time();
            before[u]=t+d.duration[i]+d.setup[i][d.I]<=d.restWindow[shift][1];
            after[u]=t>=d.restWindow[shift][0]+d.duration[d.I]+d.setup[d.I][i];
            if(before[u] && r.arc[source][i]) forward[u]=-dual[d.key(i,t)];
            if(after[u] && r.arc[i][source]) backward[u]=-dual[d.key(i,t)];
        }
        for(int u=0;u<n;u++) if(before[u] && Double.isFinite(forward[u]))
            for(int v=u+1;v<n;v++) if(before[v] && connects(d,r,nodes.get(u),nodes.get(v))) {
                Routes.Task y=nodes.get(v); double value=forward[u]-dual[d.key(y.task(),y.time())];
                if(value<forward[v]) { forward[v]=value; parent[v]=u; }
            }
        for(int v=n-1;v>=0;v--) if(after[v] && Double.isFinite(backward[v]))
            for(int u=v-1;u>=0;u--) if(after[u] && connects(d,r,nodes.get(u),nodes.get(v))) {
                Routes.Task x=nodes.get(u); double value=backward[v]-dual[d.key(x.task(),x.time())];
                if(value<backward[u]) { backward[u]=value; next[u]=v; }
            }
        double base=phase?0:d.pilotCost[shift], best=INF;
        int left=-1,right=-1,restTime=-1;
        for(int u=0;u<n;u++) {
            Routes.Task x=nodes.get(u); int i=x.task();
            int start=Math.max(d.restWindow[shift][0],x.time()+d.duration[i]+d.setup[i][d.I]);
            if(before[u] && r.arc[i][rest] && r.arc[rest][source] && base+forward[u]<best) {
                best=base+forward[u]; left=u; right=-1; restTime=start;
            }
            if(after[u] && r.arc[source][rest] && r.arc[rest][i] && base+backward[u]<best) {
                best=base+backward[u]; left=-1; right=u; restTime=d.restWindow[shift][0];
            }
        }
        for(int u=0;u<n;u++) if(before[u] && r.arc[nodes.get(u).task()][rest])
            for(int v=0;v<n;v++) if(after[v] && r.arc[rest][nodes.get(v).task()]) {
                Routes.Task x=nodes.get(u),y=nodes.get(v);
                int start=Math.max(d.restWindow[shift][0],x.time()+d.duration[x.task()]+d.setup[x.task()][d.I]);
                // Equation (22): rest cannot eliminate repositioning between the bordering tasks.
                if(x.task()!=y.task() && x.time()+d.duration[x.task()]+d.setup[x.task()][y.task()]<=y.time()
                        && start<=d.restWindow[shift][1]
                        && start+d.duration[d.I]+d.setup[d.I][y.task()]<=y.time()
                        && base+forward[u]+backward[v]<best) {
                    best=base+forward[u]+backward[v]; left=u; right=v; restTime=start;
                }
            }
        if(!Double.isFinite(best)) return null;
        List<Routes.Task> path=new ArrayList<>();
        for(int u=left;u!=-1;u=parent[u]) path.add(nodes.get(u));
        Collections.reverse(path); int position=path.size();
        for(int v=right;v!=-1;v=next[v]) path.add(nodes.get(v));
        return new Routes.Pilot(shift,path,position,restTime,d.pilotCost[shift]);
    }

    private static boolean connects(Instance d,Restrictions r,Routes.Task x,Routes.Task y) {
        return x.task()!=y.task() && r.arc[x.task()][y.task()]
                && x.time()+d.duration[x.task()]+d.setup[x.task()][y.task()]<=y.time();
    }
    public static double reducedCost(Instance d,Routes.Pilot p,double[] dual,boolean phase) {
        double value=phase?0:p.cost();
        for(Routes.Task n:p.tasks()) value-=dual[d.key(n.task(),n.time())];
        return value;
    }
}
