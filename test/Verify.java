import java.util.*;
import java.nio.file.*;

/** Exhaustive oracles use explicit rest-time enumeration, not the label algorithm. */
public final class Verify {
    static int checks;
    public static void main(String[] args) throws Exception {
        if(args.length==2) { official(Path.of(args[0]),Path.of(args[1])); return; }
        for(int k=1;k<=3;k++) {
            Instance d=Instance.synthetic(k,1);
            require(d.K==k && d.I==2*k,"dimensions");
            for(int i=0;i<d.I;i++) { boolean any=false; for(boolean b:d.allowed[i]) any|=b; require(any,"nonempty window"); }
            pricing(d);
        }
        copt.Envr env=new copt.Envr();
        try {
            for(int k=1;k<=3;k++) algorithms(env,Instance.synthetic(k,1));
            algorithms(env,Instance.synthetic(3,-1));
            pricing(Instance.synthetic(2,-2));
            algorithms(env,Instance.synthetic(2,-2));
        } finally { env.dispose(); }
        System.out.println("PASS "+checks+" independent pricing/LP/integer/branch checks");
    }
    static void pricing(Instance d) {
        List<Restrictions> cases=new ArrayList<>();
        Restrictions root=new Restrictions(d); cases.add(root);
        cases.add(root.child(new Restrictions.Decision(Restrictions.Family.SHIFT,0,0,.5),false));
        cases.add(root.child(new Restrictions.Decision(Restrictions.Family.ARC,0,d.I,.5),true));
        cases.add(root.child(new Restrictions.Decision(Restrictions.Family.ARC,d.I+d.S,0,.5),true));
        cases.add(root.child(new Restrictions.Decision(Restrictions.Family.TIME,0,d.window[0][0],.5),true));
        Random random=new Random(1841);
        VesselPricing.Rows rows=new VesselPricing.Rows(d);
        for(Restrictions r:cases) {
            List<Routes.Vessel> allV=allVessels(d,r);
            List<Routes.Pilot> allP=allPilots(d,r);
            for(int round=0;round<15;round++) {
                boolean phase=round%2==0;
                double[] dual=new double[d.size()], demand=new double[d.size()];
                for(int key=0;key<dual.length;key++) { dual[key]=random.nextInt(21)-5; demand[key]=1; }
                for(int s=0;s<d.S;s++) {
                    Routes.Pilot p=PilotPricing.price(d,r,s,dual,phase,demand,false);
                    double exact=Double.POSITIVE_INFINITY;
                    for(Routes.Pilot candidate:allP) if(candidate.shift()==s)
                        exact=Math.min(exact,PilotPricing.reducedCost(d,candidate,dual,phase));
                    double actual=p==null?Double.POSITIVE_INFINITY:PilotPricing.reducedCost(d,p,dual,phase);
                    near(actual,exact,"pilot pricing / restrictions");
                    if(p!=null) { require(r.accepts(p),"pilot branch restrictions"); require(validPilot(d,p),"rest/setup feasibility"); }
                }
                double[] vd=new double[d.K],rd=new double[rows.size()],cd=new double[]{random.nextDouble()};
                for(int n=0;n<vd.length;n++) vd[n]=30*random.nextDouble();
                for(int n=0;n<rd.length;n++) rd[n]=-random.nextDouble();
                List<Routes.Cut> cuts=List.of(new Routes.Cut(dual,false));
                List<Routes.Vessel> chosen=VesselPricing.price(d,r,rows,vd,rd,cuts,cd,phase);
                for(int k=0;k<d.K;k++) for(int b=0;b<d.B;b++) {
                    double exact=Double.POSITIVE_INFINITY;
                    for(Routes.Vessel v:allV) if(v.vessel()==k && v.berth()==b)
                        exact=Math.min(exact,vesselRc(d,v,rows,vd,rd,cuts,cd,phase));
                    Routes.Vessel picked=null;
                    for(Routes.Vessel v:chosen) if(v.vessel()==k && v.berth()==b) picked=v;
                    if(exact<-VesselPricing.EPS) {
                        require(picked!=null,"negative vessel column");
                        near(vesselRc(d,picked,rows,vd,rd,cuts,cd,phase),exact,"vessel reduced cost");
                    } else require(picked==null,"no negative vessel column");
                }
            }
            for(Routes.Vessel v:allV) {
                int[] counts=new int[rows.size()];
                for(int id:rows.coefficients(v)) counts[id]++;
                for(int n=0;n<rows.size();n++) require(counts[n]==resource(d,v,rows.definitions.get(n)),"resource coefficient");
            }
        }
    }
    static double vesselRc(Instance d,Routes.Vessel v,VesselPricing.Rows rows,double[] vd,double[] rd,List<Routes.Cut> cuts,double[] cd,boolean phase) {
        double cost=(phase?0:v.cost())-vd[v.vessel()];
        for(int n=0;n<rd.length;n++) cost-=rd[n]*resource(d,v,rows.definitions.get(n));
        for(int n=0;n<cd.length;n++) cost+=cd[n]*cuts.get(n).coefficient(d,v);
        return cost;
    }
    static int resource(Instance d,Routes.Vessel v,VesselPricing.Rows.Resource row) {
        int time=row.time(),i=row.first(),j=row.second();
        if(row.kind()==0) return v.berth()==i && v.in()+d.duration[v.vessel()]<=time && v.out()>time?1:0;
        int a=taskTime(d,v,i),b=taskTime(d,v,j);
        if(row.kind()==1) return (a<=time && a>=time-d.headway[i][j]+1?1:0)+(b==time?1:0);
        return (a<=time && a>=time-d.duration[i]+1?1:0)+(b<=time && b>=time-d.duration[j]+1?1:0);
    }
    static int taskTime(Instance d,Routes.Vessel v,int i) {
        if(i==v.vessel()) return v.in();
        if(i==v.vessel()+d.K) return v.out();
        return -100000;
    }
    static List<Routes.Vessel> allVessels(Instance d,Restrictions r) {
        List<Routes.Vessel> all=new ArrayList<>();
        for(int k=0;k<d.K;k++) for(int b=0;b<d.B;b++)
            for(int in=d.window[k][0];in<=d.window[k][1];in++)
                for(int out=d.window[k+d.K][0];out<=d.window[k+d.K][1];out++) {
                    Routes.Vessel v=new Routes.Vessel(k,b,in,out,d.berthCost[k][b]+d.taskCost[k][in]+d.taskCost[k+d.K][out]);
                    if(r.accepts(v) && in+d.duration[k]>=d.berthReady[b] && out>=in+d.duration[k]+d.handling[k][b]) all.add(v);
                }
        return all;
    }
    static List<Routes.Pilot> allPilots(Instance d,Restrictions r) {
        Map<String,Routes.Pilot> all=new LinkedHashMap<>();
        for(int s=0;s<d.S;s++) {
            List<Routes.Task> nodes=new ArrayList<>();
            for(int i=0;i<d.I;i++) for(int t=d.window[i][0];t<=d.window[i][1];t++)
                if(r.time[i][t] && r.shift[i][s] && d.inShift(t,s)) nodes.add(new Routes.Task(i,t));
            nodes.sort(Comparator.comparingInt(Routes.Task::time).thenComparingInt(Routes.Task::task));
            enumeratePilot(d,r,s,nodes,0,new ArrayList<>(),all);
        }
        return new ArrayList<>(all.values());
    }
    static void enumeratePilot(Instance d,Restrictions r,int s,List<Routes.Task> nodes,int from,List<Routes.Task> path,Map<String,Routes.Pilot> all) {
        if(!path.isEmpty()) for(int pos=0;pos<=path.size();pos++)
            for(int rest=d.restWindow[s][0];rest<=d.restWindow[s][1];rest++) {
                Routes.Pilot p=new Routes.Pilot(s,path,pos,rest,d.pilotCost[s]);
                if(validPilot(d,p) && r.accepts(p)) all.putIfAbsent(p.key(),p);
            }
        for(int n=from;n<nodes.size();n++) {
            Routes.Task y=nodes.get(n);
            if(!path.isEmpty()) {
                Routes.Task x=path.get(path.size()-1);
                if(x.task()==y.task() || x.time()+d.duration[x.task()]+d.setup[x.task()][y.task()]>y.time()) continue;
            }
            path.add(y); enumeratePilot(d,r,s,nodes,n+1,path,all); path.remove(path.size()-1);
        }
    }
    static boolean validPilot(Instance d,Routes.Pilot p) {
        int pos=p.restPosition(),s=p.shift(),rest=p.restTime();
        if(rest<d.restWindow[s][0] || rest>d.restWindow[s][1]) return false;
        for(Routes.Task x:p.tasks()) if(!d.allowed[x.task()][x.time()] || !d.inShift(x.time(),s)) return false;
        for(int n=1;n<p.tasks().size();n++) {
            Routes.Task x=p.tasks().get(n-1),y=p.tasks().get(n);
            if(x.task()==y.task() || x.time()+d.duration[x.task()]+d.setup[x.task()][y.task()]>y.time()) return false;
        }
        if(pos>0) {
            Routes.Task x=p.tasks().get(pos-1);
            if(rest<x.time()+d.duration[x.task()]+d.setup[x.task()][d.I]) return false;
        }
        if(pos<p.tasks().size()) {
            Routes.Task y=p.tasks().get(pos);
            if(y.time()<rest+d.duration[d.I]+d.setup[d.I][y.task()]) return false;
        }
        return !p.tasks().isEmpty();
    }
    static void require(boolean truth,String message) { checks++; if(!truth) throw new AssertionError(message); }
    static void near(double a,double b,String message) {
        checks++; if(a==b) return;
        if(!Double.isFinite(a) || !Double.isFinite(b) || Math.abs(a-b)>1e-6*Math.max(1,Math.abs(b)))
            throw new AssertionError(message+": actual="+a+", exact="+b);
    }

    static void algorithms(copt.Envr env,Instance d) throws Exception {
        Restrictions r=new Restrictions(d);
        List<Routes.Vessel> allV=allVessels(d,r); List<Routes.Pilot> allP=allPilots(d,r);
        Bpbc.NodeSolution root=new Bpbc(env,d,30,false).solveNode(r);
        require(root!=null,"root feasible");
        near(root.master().objective(),fullLp(env,d,allV,allP),"root BPBC versus complete M2 LP");
        for(Routes.Cut cut:root.cuts()) for(Routes.Pilot p:allP) {
            double value=0;
            for(Routes.Task t:p.tasks()) value+=cut.delta()[d.key(t.task(),t.time())];
            require(value<=(cut.feasibility()?0:p.cost())+1e-6,"full-network dual certificate");
        }
        double exact=integerOptimum(d,r);
        Bpbc.Result result=new Bpbc(env,d,30,false).solve();
        require(result.status().equals("OPTIMAL"),"integer search terminates");
        near(result.upperBound(),exact,"BPBC versus independent integer enumeration");
        near(result.lowerBound(),exact,"final lower bound");
        for(Routes.Pilot p:result.pilots()) require(validPilot(d,p),"incumbent pilot feasibility");
        System.out.printf(Locale.ROOT,"verified K=%d rootLP=%.6f integer=%.6f nodes=%d%n",d.K,root.master().objective(),exact,result.nodes());
        if(d.K==2) {
            for(Restrictions.Decision branch:List.of(
                    new Restrictions.Decision(Restrictions.Family.BERTH,0,0,.5),
                    new Restrictions.Decision(Restrictions.Family.TIME,0,0,.5),
                    new Restrictions.Decision(Restrictions.Family.SHIFT,d.K,1,.5),
                    new Restrictions.Decision(Restrictions.Family.ARC,0,1,.5),
                    new Restrictions.Decision(Restrictions.Family.ARC,0,d.I+d.S,.5))) {
                for(boolean one:new boolean[]{false,true}) {
                    Restrictions child=r.child(branch,one);
                    double optimum=integerOptimum(d,child);
                    Bpbc.Result actual=new Bpbc(env,d,30,false).solve(child);
                    require(actual.status().equals(Double.isFinite(optimum)?"OPTIMAL":"INFEASIBLE"),"branch status "+branch);
                    near(actual.upperBound(),optimum,"forced branch integer optimum");
                }
            }
            Restrictions.Decision arc=new Restrictions.Decision(Restrictions.Family.ARC,0,1,.5);
            Restrictions contradictory=r.child(arc,false).child(arc,true);
            require(!contradictory.arc[0][1],"child cannot undo a forbidden arc");
            Bpbc.Result timeout=new Bpbc(env,d,1e-9,false).solve();
            require(timeout.status().equals("TIME_LIMIT") && timeout.lowerBound()==0 && !Double.isFinite(timeout.upperBound()),"timeout has no invented incumbent/bound");
        }
    }
    static double fullLp(copt.Envr env,Instance d,List<Routes.Vessel> allV,List<Routes.Pilot> allP) throws Exception {
        copt.Model m=Bpbc.newModel(env,"enumerated_M2",new Bpbc.Budget(60));
        List<copt.Var> allVars=new ArrayList<>(); List<copt.Constraint> constraints=new ArrayList<>();
        try {
            copt.Var[] x=new copt.Var[allV.size()],mu=new copt.Var[allP.size()];
            for(int n=0;n<x.length;n++) { x[n]=m.addVar(0,copt.Consts.INFINITY,allV.get(n).cost(),copt.Consts.CONTINUOUS,"x"+n); allVars.add(x[n]); }
            for(int n=0;n<mu.length;n++) { mu[n]=m.addVar(0,copt.Consts.INFINITY,allP.get(n).cost(),copt.Consts.CONTINUOUS,"u"+n); allVars.add(mu[n]); }
            for(int k=0;k<d.K;k++) {
                copt.Expr e=new copt.Expr();
                for(int n=0;n<x.length;n++) if(allV.get(n).vessel()==k) e.addTerm(x[n],1);
                constraints.add(m.addConstr(e,copt.Consts.EQUAL,1,"assign"+k)); e.dispose();
            }
            List<VesselPricing.Rows.Resource> resources=new ArrayList<>();
            for(int b=0;b<d.B;b++) for(int t=0;t<d.T;t++) resources.add(new VesselPricing.Rows.Resource(0,b,-1,t));
            for(int[] p:d.headwayPairs) for(int t=0;t<d.T;t++) resources.add(new VesselPricing.Rows.Resource(1,p[0],p[1],t));
            for(int[] p:d.conflictPairs) for(int t=0;t<d.T;t++) resources.add(new VesselPricing.Rows.Resource(2,p[0],p[1],t));
            for(int row=0;row<resources.size();row++) {
                copt.Expr e=new copt.Expr();
                for(int n=0;n<x.length;n++) {
                    int a=resource(d,allV.get(n),resources.get(row)); if(a!=0) e.addTerm(x[n],a);
                }
                constraints.add(m.addConstr(e,copt.Consts.LESS_EQUAL,1,"resource"+row)); e.dispose();
            }
            for(int i=0;i<d.I;i++) for(int t=d.window[i][0];t<=d.window[i][1];t++) if(d.allowed[i][t]) {
                copt.Expr e=new copt.Expr();
                for(int n=0;n<x.length;n++) if(taskTime(d,allV.get(n),i)==t) e.addTerm(x[n],-1);
                for(int n=0;n<mu.length;n++) for(Routes.Task q:allP.get(n).tasks()) if(q.task()==i && q.time()==t) e.addTerm(mu[n],1);
                constraints.add(m.addConstr(e,copt.Consts.EQUAL,0,"link"+d.key(i,t))); e.dispose();
            }
            Bpbc.optimise(m,new Bpbc.Budget(60));
            return m.getDblAttr(copt.DblAttr.LpObjVal);
        } finally {
            for(copt.Var v:allVars) v.dispose();
            for(copt.Constraint c:constraints) c.dispose(); m.dispose();
        }
    }
    static double integerOptimum(Instance d,Restrictions r) {
        return enumerateSchedule(d,allVessels(d,r),allPilots(d,r),0,new ArrayList<>(),0,Double.POSITIVE_INFINITY);
    }
    static double enumerateSchedule(Instance d,List<Routes.Vessel> allV,List<Routes.Pilot> allP,int k,List<Routes.Vessel> chosen,double cost,double best) {
        if(cost>=best) return best;
        if(k==d.K) {
            int[] times=new int[d.I];
            for(Routes.Vessel v:chosen) { times[v.vessel()]=v.in(); times[v.vessel()+d.K]=v.out(); }
            double[] prices=new double[1<<d.I]; Arrays.fill(prices,Double.POSITIVE_INFINITY);
            for(Routes.Pilot p:allP) {
                int mask=0; boolean valid=true;
                for(Routes.Task t:p.tasks()) {
                    int bit=1<<t.task();
                    if(times[t.task()]!=t.time() || (mask&bit)!=0) { valid=false; break; }
                    mask|=bit;
                }
                if(valid) prices[mask]=Math.min(prices[mask],p.cost());
            }
            double[] dp=new double[prices.length]; Arrays.fill(dp,Double.POSITIVE_INFINITY); dp[0]=0;
            for(int mask=0;mask<dp.length;mask++) if(Double.isFinite(dp[mask]))
                for(int route=1;route<prices.length;route++) if((mask&route)==0 && Double.isFinite(prices[route]))
                    dp[mask|route]=Math.min(dp[mask|route],dp[mask]+prices[route]);
            return Math.min(best,cost+dp[dp.length-1]);
        }
        for(Routes.Vessel v:allV) if(v.vessel()==k) {
            chosen.add(v);
            if(scheduleFits(d,chosen)) best=enumerateSchedule(d,allV,allP,k+1,chosen,cost+v.cost(),best);
            chosen.remove(chosen.size()-1);
        }
        return best;
    }
    static boolean scheduleFits(Instance d,List<Routes.Vessel> chosen) {
        for(int n=0;n<chosen.size();n++) for(int j=0;j<n;j++) {
            Routes.Vessel a=chosen.get(n),b=chosen.get(j);
            if(a.berth()==b.berth() && Math.max(a.in()+d.duration[a.vessel()],b.in()+d.duration[b.vessel()])<Math.min(a.out(),b.out())) return false;
        }
        int[] starts=new int[d.I]; Arrays.fill(starts,-100000);
        for(Routes.Vessel v:chosen) { starts[v.vessel()]=v.in(); starts[v.vessel()+d.K]=v.out(); }
        for(int[] p:d.headwayPairs) if(starts[p[0]]>=0 && starts[p[1]]>=0) {
            int difference=starts[p[1]]-starts[p[0]];
            if(difference>=0 && difference<d.headway[p[0]][p[1]]) return false;
        }
        for(int[] p:d.conflictPairs) if(starts[p[0]]>=0 && starts[p[1]]>=0)
            if(Math.max(starts[p[0]],starts[p[1]])<Math.min(starts[p[0]]+d.duration[p[0]],starts[p[1]]+d.duration[p[1]])) return false;
        return true;
    }


    static void official(Path data,Path log) throws Exception {
        Instance d=Instance.read(data);
        List<Routes.Vessel> vessels=new ArrayList<>(); List<Routes.Pilot> pilots=new ArrayList<>();
        java.util.regex.Pattern ship=java.util.regex.Pattern.compile("^vessel (\\d+) berth (\\d+) inward (\\d+) outward (\\d+) cost ([\\d.]+)$");
        java.util.regex.Pattern pilot=java.util.regex.Pattern.compile("^pilot shift (\\d+) tasks (.*) rest@(\\d+) position (\\d+)$");
        java.util.regex.Pattern task=java.util.regex.Pattern.compile("Task\\[task=(\\d+), time=(\\d+)\\]");
        double claimed=Double.NaN;
        for(String line:Files.readAllLines(log)) {
            if(line.startsWith("RESULT ")) {
                java.util.regex.Matcher m=java.util.regex.Pattern.compile("UB=([\\d.]+)").matcher(line);
                if(m.find()) claimed=Double.parseDouble(m.group(1));
            }
            java.util.regex.Matcher a=ship.matcher(line), b=pilot.matcher(line);
            if(a.matches()) vessels.add(new Routes.Vessel(Integer.parseInt(a.group(1)),Integer.parseInt(a.group(2)),
                    Integer.parseInt(a.group(3)),Integer.parseInt(a.group(4)),Double.parseDouble(a.group(5))));
            if(b.matches()) {
                List<Routes.Task> path=new ArrayList<>(); java.util.regex.Matcher q=task.matcher(b.group(2));
                while(q.find()) path.add(new Routes.Task(Integer.parseInt(q.group(1)),Integer.parseInt(q.group(2))));
                int s=Integer.parseInt(b.group(1));
                pilots.add(new Routes.Pilot(s,path,Integer.parseInt(b.group(4)),Integer.parseInt(b.group(3)),d.pilotCost[s]));
            }
        }
        int[] assignments=new int[d.K],cover=new int[d.I],start=new int[d.I];
        double total=0;
        for(Routes.Vessel v:vessels) {
            int k=v.vessel(),out=k+d.K;
            require(d.allowed[k][v.in()] && d.allowed[out][v.out()],"official task windows");
            require(v.in()+d.duration[k]>=d.berthReady[v.berth()],"official initial berth");
            require(v.out()>=v.in()+d.duration[k]+d.handling[k][v.berth()],"official handling time");
            double cost=d.taskCost[k][v.in()]+d.taskCost[out][v.out()]+d.berthCost[k][v.berth()];
            near(v.cost(),cost,"official vessel cost");
            total+=cost; assignments[k]++; start[k]=v.in(); start[out]=v.out();
        }
        for(int a:assignments) require(a==1,"official vessel served exactly once");
        require(scheduleFits(d,vessels),"official full berth/headway/non-simultaneity constraints");
        for(Routes.Pilot p:pilots) {
            require(validPilot(d,p),"official pilot rest/setup/shift constraints");
            total+=p.cost();
            for(Routes.Task t:p.tasks()) { cover[t.task()]++; require(start[t.task()]==t.time(),"official task start coupling"); }
        }
        for(int a:cover) require(a==1,"official task served exactly once");
        near(total,claimed,"official total objective");
        System.out.printf(Locale.ROOT,"PASS official primal check: %d vessels, %d pilots, total %.6f (%d checks)%n",vessels.size(),pilots.size(),total,checks);
    }

}
