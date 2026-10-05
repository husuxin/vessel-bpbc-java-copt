import copt.*;
import java.util.*;

/** Algorithm 1 inside an explicit best-bound tree; no solver callback is required. */
public final class Bpbc {
    public static final class Limit extends RuntimeException {}
    public static final class Budget {
        private final long deadline;
        int lpSolves,cuts,vesselColumns,pilotColumns;
        public Budget(double seconds) { if(!(seconds>0) || !Double.isFinite(seconds)) throw new IllegalArgumentException("Positive time limit required"); deadline=System.nanoTime()+(long)(seconds*1e9); }
        public void check() { if(System.nanoTime()>=deadline) throw new Limit(); }
        double remaining() { check(); return Math.max(.001,(deadline-System.nanoTime())/1e9); }
    }
    public record NodeSolution(Master.Solution master,PilotProblem.Solution pilot,List<Routes.Cut> cuts) {}
    public record Result(String status,double lowerBound,double upperBound,double rootLp,int nodes,
            List<Routes.Vessel> vessels,List<Routes.Pilot> pilots,int lpSolves,int cuts,int vesselColumns,int pilotColumns,double seconds) {
        public double gap() { return Double.isFinite(upperBound)?Math.max(0,upperBound-lowerBound)/Math.max(1,Math.abs(upperBound)):Double.NaN; }
    }
    private record Pending(Restrictions restrictions,double bound,long id) {}
    private final Envr env;
    private final Instance d;
    private final VesselPricing.Rows rows;
    private final Budget budget;
    private final boolean verbose;

    public Bpbc(Envr env,Instance d,double seconds,boolean verbose) {
        this.env=env; this.d=d; this.rows=new VesselPricing.Rows(d); this.budget=new Budget(seconds); this.verbose=verbose;
    }
    public NodeSolution solveNode(Restrictions r) throws CoptException {
        try(Master master=new Master(env,d,r,rows,budget)) {
            while(true) {
                Master.Solution m=master.solveCg();
                if(m==null) return null;
                PilotProblem.Solution p;
                try(PilotProblem pilot=new PilotProblem(env,d,r,m.demand(),budget)) { p=pilot.solve(); }
                Routes.Cut cut=new Routes.Cut(p.delta(),!p.feasible());
                if(!p.feasible() || p.objective()-m.eta()>1e-7*Math.max(1,Math.abs(m.objective()))) master.addCut(cut);
                else return new NodeSolution(m,p,List.copyOf(master.cuts));
            }
        }
    }
    public Result solve() throws CoptException { return solve(new Restrictions(d)); }
    public Result solve(Restrictions initial) throws CoptException {
        long start=System.nanoTime(),id=0;
        PriorityQueue<Pending> queue=new PriorityQueue<>(Comparator.comparingDouble(Pending::bound).thenComparingLong(Pending::id));
        queue.add(new Pending(initial,0,id++));
        double ub=Double.POSITIVE_INFINITY,root=Double.NaN,unfinished=Double.POSITIVE_INFINITY;
        List<Routes.Vessel> bestV=List.of(); List<Routes.Pilot> bestP=List.of();
        int nodes=0; boolean timeout=false;
        while(!queue.isEmpty()) {
            Pending node=queue.remove();
            if(node.bound()>=ub-1e-7*Math.max(1,Math.abs(ub)) && Double.isFinite(ub)) continue;
            try {
                budget.check(); NodeSolution solved=solveNode(node.restrictions()); nodes++;
                if(solved==null) continue;
                double bound=solved.master().objective();
                if(nodes==1) root=bound;
                if(verbose && (nodes<=3 || nodes%10==0))
                    System.out.printf(Locale.ROOT,"node=%d LB=%.6f vesselCols=%d pilotCols=%d cuts=%d%n",nodes,bound,
                            solved.master().routes().size(),solved.pilot().routes().size(),solved.cuts().size());
                if(Double.isFinite(ub) && bound>=ub-1e-7*Math.max(1,Math.abs(ub))) continue;
                Restrictions.Decision decision=node.restrictions().choose(solved.master().routes(),solved.master().values(),
                        solved.pilot().routes(),solved.pilot().values());
                if(decision==null) {
                    List<Routes.Vessel> selectedV=select(solved.master().routes(),solved.master().values());
                    List<Routes.Pilot> selectedP=select(solved.pilot().routes(),solved.pilot().values());
                    validate(selectedV,selectedP,node.restrictions());
                    double actual=selectedV.stream().mapToDouble(Routes.Vessel::cost).sum()+selectedP.stream().mapToDouble(Routes.Pilot::cost).sum();
                    if(actual<ub) { ub=actual; bestV=selectedV; bestP=selectedP; }
                } else {
                    queue.add(new Pending(node.restrictions().child(decision,false),bound,id++));
                    queue.add(new Pending(node.restrictions().child(decision,true),bound,id++));
                }
            } catch(Limit limit) { unfinished=node.bound(); timeout=true; break; }
        }
        double lb;
        if(timeout) lb=Math.min(unfinished,queue.isEmpty()?Double.POSITIVE_INFINITY:queue.peek().bound());
        else lb=Double.isFinite(ub)?ub:Double.POSITIVE_INFINITY;
        if(Double.isFinite(ub)) lb=Math.min(lb,ub);
        String status=timeout?"TIME_LIMIT":Double.isFinite(ub)?"OPTIMAL":"INFEASIBLE";
        return new Result(status,lb,ub,root,nodes,bestV,bestP,budget.lpSolves,budget.cuts,budget.vesselColumns,budget.pilotColumns,(System.nanoTime()-start)/1e9);
    }
    private static <T> List<T> select(List<T> routes,double[] values) {
        List<T> selected=new ArrayList<>();
        for(int n=0;n<values.length;n++) {
            double v=values[n];
            if(Math.abs(v-Math.rint(v))>1e-6) throw new IllegalStateException("Fractional routes remain after aggregate branching");
            if(v>.5) selected.add(routes.get(n));
        }
        return selected;
    }
    private void validate(List<Routes.Vessel> vessels,List<Routes.Pilot> pilots,Restrictions r) {
        int[] assignment=new int[d.K],use=new int[rows.size()],vDemand=new int[d.size()],pDemand=new int[d.size()];
        for(Routes.Vessel v:vessels) {
            if(!r.accepts(v)) throw new IllegalStateException("Vessel branch violation");
            assignment[v.vessel()]++;
            for(int row:rows.coefficients(v)) use[row]++;
            vDemand[d.key(v.vessel(),v.in())]++; vDemand[d.key(v.vessel()+d.K,v.out())]++;
        }
        for(int count:assignment) if(count!=1) throw new IllegalStateException("Vessel assignment violation");
        for(int count:use) if(count>1) throw new IllegalStateException("Berth/traffic violation");
        for(Routes.Pilot p:pilots) {
            if(!r.accepts(p)) throw new IllegalStateException("Pilot branch violation");
            for(Routes.Task t:p.tasks()) pDemand[d.key(t.task(),t.time())]++;
        }
        if(!Arrays.equals(vDemand,pDemand)) throw new IllegalStateException("Vessel/pilot coupling violation");
    }
    static Model newModel(Envr env,String name,Budget budget) throws CoptException {
        budget.check(); Model m=env.createModel(name);
        m.setIntParam(IntParam.Logging,0); m.setIntParam(IntParam.Threads,1);
        m.setIntParam(IntParam.LpMethod,1); m.setIntParam(IntParam.Presolve,0);
        m.setDblParam(DblParam.FeasTol,1e-8); m.setDblParam(DblParam.DualTol,1e-8);
        return m;
    }
    static void optimise(Model model,Budget budget) throws CoptException {
        model.setDblParam(DblParam.TimeLimit,budget.remaining()); model.solve(); budget.lpSolves++;
        int status=model.getIntAttr(IntAttr.LpStatus);
        if(status==Status.TIMEOUT) throw new Limit();
        if(status!=Status.OPTIMAL) {
            budget.check();
            throw new IllegalStateException("LP did not reach optimal status: "+status);
        }
    }
}
