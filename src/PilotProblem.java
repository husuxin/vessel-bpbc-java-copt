import copt.*;
import java.util.*;

/** PBSP: sparse-network pricing followed by full-network dual certification. */
public final class PilotProblem implements AutoCloseable {
    public record Solution(double objective,double[] delta,List<Routes.Pilot> routes,double[] values,boolean feasible) {}
    private final Instance d;
    private final Restrictions r;
    private final Bpbc.Budget budget;
    private final Model model;
    private final Constraint[] cover;
    private final List<Var> artificial=new ArrayList<>(),vars=new ArrayList<>();
    private final List<Routes.Pilot> routes=new ArrayList<>();
    private final Set<String> seen=new HashSet<>();
    private final double[] demand;

    public PilotProblem(Envr env,Instance d,Restrictions r,double[] demand,Bpbc.Budget budget) throws CoptException {
        this.d=d; this.r=r; this.demand=demand; this.budget=budget;
        model=Bpbc.newModel(env,"PBSP",budget); cover=new Constraint[d.size()];
        for(int i=0;i<d.I;i++) for(int t=d.window[i][0];t<=d.window[i][1];t++) if(d.allowed[i][t]) {
            int key=d.key(i,t);
            if(demand[key]<-1e-7 || demand[key]>1+1e-7) throw new IllegalStateException("Demand outside [0,1]");
            Expr e=new Expr(); cover[key]=model.addConstr(e,Consts.EQUAL,Math.max(0,demand[key]),"task"+key); e.dispose();
            for(int sign:new int[]{1,-1}) {
                Column c=new Column(); c.addTerm(cover[key],sign);
                artificial.add(model.addVar(0,Consts.INFINITY,1,Consts.CONTINUOUS,c,"slack"+key+"_"+sign)); c.dispose();
            }
        }
    }
    public Solution solve() throws CoptException {
        double[] delta=generate(true);
        if(model.getDblAttr(DblAttr.LpObjVal)>1e-6)
            return new Solution(model.getDblAttr(DblAttr.LpObjVal),delta,List.copyOf(routes),values(),false);
        for(Var v:artificial) { v.set(DblInfo.UB,0); v.set(DblInfo.Obj,0); }
        for(int n=0;n<vars.size();n++) vars.get(n).set(DblInfo.Obj,routes.get(n).cost());
        delta=generate(false);
        double obj=model.getDblAttr(DblAttr.LpObjVal),dualObj=0;
        for(int key=0;key<delta.length;key++) dualObj+=delta[key]*demand[key];
        if(Math.abs(obj-dualObj)>1e-6*Math.max(1,Math.abs(obj))) throw new IllegalStateException("PBSP primal/dual mismatch");
        return new Solution(obj,delta,List.copyOf(routes),values(),true);
    }
    private double[] generate(boolean phase) throws CoptException {
        while(true) {
            Bpbc.optimise(model,budget);
            double[] delta=new double[d.size()];
            for(int key=0;key<cover.length;key++) if(cover[key]!=null) delta[key]=cover[key].get(DblInfo.Dual);
            List<Routes.Pilot> incoming=price(delta,phase,!phase);
            // Sparse LP duals must satisfy every full-network column before a cut is used.
            if(incoming.isEmpty() && !phase) incoming=price(delta,false,false);
            if(incoming.isEmpty()) return delta;
            for(Routes.Pilot p:incoming) {
                if(!seen.add(p.key())) throw new IllegalStateException("Existing pilot column has negative reduced cost");
                Column c=new Column();
                for(Routes.Task node:p.tasks()) c.addTerm(cover[d.key(node.task(),node.time())],1);
                // μ<=1 is implied by a nonempty route's unit-demand cover row; ζ=0 is valid.
                vars.add(model.addVar(0,Consts.INFINITY,phase?0:p.cost(),Consts.CONTINUOUS,c,"p"+routes.size()));
                c.dispose(); routes.add(p); budget.pilotColumns++;
            }
        }
    }
    private List<Routes.Pilot> price(double[] delta,boolean phase,boolean sparse) {
        budget.check(); List<Routes.Pilot> incoming=new ArrayList<>();
        for(int s=0;s<d.S;s++) {
            Routes.Pilot p=PilotPricing.price(d,r,s,delta,phase,demand,sparse);
            if(p!=null && PilotPricing.reducedCost(d,p,delta,phase)<-VesselPricing.EPS) incoming.add(p);
        }
        return incoming;
    }
    private double[] values() throws CoptException {
        double[] v=new double[vars.size()]; for(int n=0;n<v.length;n++) v[n]=vars.get(n).get(DblInfo.Value); return v;
    }
    @Override public void close() {
        for(Var v:vars) v.dispose(); for(Var v:artificial) v.dispose();
        for(Constraint c:cover) if(c!=null) c.dispose(); model.dispose();
    }
}
