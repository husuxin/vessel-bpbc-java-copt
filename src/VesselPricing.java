import java.util.*;

/** MPP_kb: exact minimisation over the two pilotage start times (paper Section 4.3). */
public final class VesselPricing {
    static final double EPS=1e-7;

    public static List<Routes.Vessel> price(Instance d, Restrictions r, Rows rows,
            double[] assignmentDual, double[] resourceDual, List<Routes.Cut> cuts,
            double[] cutDual, boolean phase) {
        double[] event=new double[d.size()];
        for(int key=0;key<event.length;key++) {
            for(int row:rows.eventRows[key]) event[key]-=resourceDual[row];
            if(!phase) event[key]+=d.taskCost[key/d.T][key%d.T];
            for(int n=0;n<cuts.size();n++) event[key]+=cutDual[n]*cuts.get(n).delta()[key];
        }
        double[][] prefix=new double[d.B][d.T+1];
        for(int b=0;b<d.B;b++) for(int t=0;t<d.T;t++)
            prefix[b][t+1]=prefix[b][t]+resourceDual[rows.berthRows[b][t]];
        List<Routes.Vessel> result=new ArrayList<>();
        for(int k=0;k<d.K;k++) for(int b=0;b<d.B;b++) if(r.berth[k][b]) {
            double best=Double.POSITIVE_INFINITY; Routes.Vessel route=null;
            for(int in=d.window[k][0];in<=d.window[k][1];in++) if(r.canStart(k,in)) {
                int arrival=in+d.duration[k];
                if(arrival<d.berthReady[b]) continue;
                int first=Math.max(d.window[k+d.K][0],arrival+d.handling[k][b]);
                for(int out=first;out<=d.window[k+d.K][1];out++) if(r.canStart(k+d.K,out)) {
                    double rc=(phase?0:d.berthCost[k][b])-assignmentDual[k]+event[d.key(k,in)]
                            +event[d.key(k+d.K,out)]-prefix[b][out]+prefix[b][arrival];
                    if(rc<best) {
                        best=rc;
                        route=new Routes.Vessel(k,b,in,out,d.berthCost[k][b]+d.taskCost[k][in]+d.taskCost[k+d.K][out]);
                    }
                }
            }
            if(best<-EPS) result.add(route);
        }
        return result;
    }

    /** Static rows (39)-(41). Rows implied by a single task's unit assignment are omitted. */
    public static final class Rows {
        public record Resource(int kind,int first,int second,int time) {} // 0 berth, 1 headway, 2 conflict
        final List<Resource> definitions=new ArrayList<>();
        final int[][] berthRows;
        final int[][] eventRows;
        private final Instance d;

        public Rows(Instance d) {
            this.d=d; berthRows=new int[d.B][d.T];
            List<List<Integer>> events=new ArrayList<>();
            for(int key=0;key<d.size();key++) events.add(new ArrayList<>());
            for(int b=0;b<d.B;b++) for(int t=0;t<d.T;t++) berthRows[b][t]=add(0,b,-1,t);
            for(int[] pair:d.headwayPairs) {
                int i=pair[0],j=pair[1],f=d.headway[i][j];
                for(int t=0;t<d.T;t++) if(d.allowed[j][t] && hasStart(i,t-f+1,t)) {
                    int row=add(1,i,j,t);
                    for(int u=Math.max(0,t-f+1);u<=t;u++) if(d.allowed[i][u]) events.get(d.key(i,u)).add(row);
                    events.get(d.key(j,t)).add(row);
                }
            }
            for(int[] pair:d.conflictPairs) {
                int i=pair[0],j=pair[1];
                for(int t=0;t<d.T;t++) if(hasStart(i,t-d.duration[i]+1,t) && hasStart(j,t-d.duration[j]+1,t)) {
                    int row=add(2,i,j,t);
                    for(int task:pair) for(int u=Math.max(0,t-d.duration[task]+1);u<=t;u++)
                        if(d.allowed[task][u]) events.get(d.key(task,u)).add(row);
                }
            }
            eventRows=new int[d.size()][];
            for(int key=0;key<d.size();key++) eventRows[key]=events.get(key).stream().mapToInt(Integer::intValue).toArray();
        }
        public int size() { return definitions.size(); }
        private int add(int kind,int first,int second,int t) {
            int n=definitions.size(); definitions.add(new Resource(kind,first,second,t)); return n;
        }
        private boolean hasStart(int i,int lo,int hi) {
            for(int t=Math.max(0,lo);t<=Math.min(d.T-1,hi);t++) if(d.allowed[i][t]) return true;
            return false;
        }
        /** Repeated row IDs represent coefficients greater than one. */
        public int[] coefficients(Routes.Vessel v) {
            int start=v.in()+d.duration[v.vessel()];
            int[] a=eventRows[d.key(v.vessel(),v.in())], b=eventRows[d.key(v.vessel()+d.K,v.out())];
            int[] ids=new int[v.out()-start+a.length+b.length]; int n=0;
            for(int t=start;t<v.out();t++) ids[n++]=berthRows[v.berth()][t];
            for(int id:a) ids[n++]=id;
            for(int id:b) ids[n++]=id;
            Arrays.sort(ids);
            return ids;
        }
    }
}
