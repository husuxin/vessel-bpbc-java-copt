import copt.*;
import java.util.*;

/** BMP with incremental vessel columns and Benders rows (38)-(41), (54). */
public final class Master implements AutoCloseable {
    public record Solution(double objective,double eta,List<Routes.Vessel> routes,double[] values,double[] demand) {}
    final List<Routes.Cut> cuts=new ArrayList<>();
    private final List<Routes.Vessel> routes=new ArrayList<>();
    private final List<Var> vars=new ArrayList<>(),artificial=new ArrayList<>();
    private final List<Constraint> cutRows=new ArrayList<>();
    private final Set<Routes.Vessel> seen=new HashSet<>();
    private final Constraint[] assignment,resource;
    private final Instance d;
    private final Restrictions r;
    private final VesselPricing.Rows rows;
    private final Bpbc.Budget budget;
    private final Model model;
    private final Var eta;

    public Master(Envr env,Instance d,Restrictions r,VesselPricing.Rows rows,Bpbc.Budget budget) throws CoptException {
        this.d=d; this.r=r; this.rows=rows; this.budget=budget;
        model=Bpbc.newModel(env,"BMP",budget);
        eta=model.addVar(0,Consts.INFINITY,1,Consts.CONTINUOUS,"eta");
        assignment=new Constraint[d.K]; resource=new Constraint[rows.size()];
        for(int k=0;k<d.K;k++) {
            Expr e=new Expr(); assignment[k]=model.addConstr(e,Consts.EQUAL,1,"v"+k); e.dispose();
            Column c=new Column(); c.addTerm(assignment[k],1);
            artificial.add(model.addVar(0,Consts.INFINITY,1,Consts.CONTINUOUS,c,"art"+k)); c.dispose();
        }
        for(int n=0;n<resource.length;n++) {
            Expr e=new Expr(); resource[n]=model.addConstr(e,Consts.LESS_EQUAL,1,"res"+n); e.dispose();
        }
    }
    public void addCut(Routes.Cut cut) throws CoptException {
        Expr e=new Expr();
        if(!cut.feasibility()) e.addTerm(eta,1);
        for(int n=0;n<vars.size();n++) e.addTerm(vars.get(n),-cut.coefficient(d,routes.get(n)));
        cutRows.add(model.addConstr(e,Consts.GREATER_EQUAL,0,"cut"+cuts.size())); e.dispose();
        cuts.add(cut); budget.cuts++;
    }
    /** Returns null only after Phase I and exact pricing prove the node infeasible. */
    public Solution solveCg() throws CoptException {
        configure(true);
        if(!columnGeneration(true)) throw new IllegalStateException("Unexpected BMP Phase I failure");
        if(model.getDblAttr(DblAttr.LpObjVal)>1e-6) return null;
        configure(false);
        columnGeneration(false);
        double[] x=new double[vars.size()],demand=new double[d.size()];
        for(int n=0;n<x.length;n++) {
            x[n]=vars.get(n).get(DblInfo.Value); Routes.Vessel v=routes.get(n);
            demand[d.key(v.vessel(),v.in())]+=x[n]; demand[d.key(v.vessel()+d.K,v.out())]+=x[n];
        }
        return new Solution(model.getDblAttr(DblAttr.LpObjVal),eta.get(DblInfo.Value),List.copyOf(routes),x,demand);
    }
    private void configure(boolean phase) throws CoptException {
        eta.set(DblInfo.Obj,phase?0:1);
        for(Var a:artificial) { a.set(DblInfo.UB,phase?Consts.INFINITY:0); a.set(DblInfo.Obj,phase?1:0); }
        for(int n=0;n<vars.size();n++) vars.get(n).set(DblInfo.Obj,phase?0:routes.get(n).cost());
    }
    private boolean columnGeneration(boolean phase) throws CoptException {
        while(true) {
            Bpbc.optimise(model,budget);
            double[] ad=new double[d.K],rd=new double[resource.length],cd=new double[cutRows.size()];
            for(int k=0;k<ad.length;k++) ad[k]=assignment[k].get(DblInfo.Dual);
            for(int n=0;n<rd.length;n++) rd[n]=resource[n].get(DblInfo.Dual);
            for(int n=0;n<cd.length;n++) cd[n]=cutRows.get(n).get(DblInfo.Dual);
            List<Routes.Vessel> incoming=VesselPricing.price(d,r,rows,ad,rd,cuts,cd,phase);
            if(incoming.isEmpty()) return true;
            for(Routes.Vessel v:incoming) {
                if(!seen.add(v)) throw new IllegalStateException("Existing vessel column has negative reduced cost");
                Column c=new Column(); c.addTerm(assignment[v.vessel()],1);
                int[] ids=rows.coefficients(v);
                for(int n=0;n<ids.length;) {
                    int end=n+1; while(end<ids.length && ids[end]==ids[n]) end++;
                    c.addTerm(resource[ids[n]],end-n); n=end;
                }
                for(int n=0;n<cuts.size();n++) c.addTerm(cutRows.get(n),-cuts.get(n).coefficient(d,v));
                vars.add(model.addVar(0,Consts.INFINITY,phase?0:v.cost(),Consts.CONTINUOUS,c,"w"+routes.size()));
                c.dispose(); routes.add(v); budget.vesselColumns++;
            }
        }
    }
    @Override public void close() {
        for(Var v:vars) v.dispose(); for(Var v:artificial) v.dispose(); eta.dispose();
        for(Constraint c:assignment) c.dispose(); for(Constraint c:resource) c.dispose();
        for(Constraint c:cutRows) c.dispose(); model.dispose();
    }
}
