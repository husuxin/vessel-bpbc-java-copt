import java.util.*;

/** Paper columns and dual cuts; pricing contains no solver API. */
public final class Routes {
    private Routes() {}
    public record Vessel(int vessel, int berth, int in, int out, double cost) {}
    public record Task(int task, int time) {}
    public record Pilot(int shift, List<Task> tasks, int restPosition, int restTime, double cost) {
        public Pilot { tasks=List.copyOf(tasks); }
        public String key() { return shift+":"+restPosition+":"+tasks; } // Equivalent rest times share one column.
        public int[] activities(Instance d) {
            int[] a=new int[tasks.size()+3]; a[0]=d.I+d.S;
            int p=1;
            for(int n=0;n<=tasks.size();n++) {
                if(n==restPosition) a[p++]=d.I+shift;
                if(n<tasks.size()) a[p++]=tasks.get(n).task();
            }
            a[p]=d.I+d.S;
            return a;
        }
    }
    public record Cut(double[] delta, boolean feasibility) {
        public Cut { delta=delta.clone(); }
        public double coefficient(Instance d,Vessel r) { return delta[d.key(r.vessel(),r.in())]+delta[d.key(r.vessel()+d.K,r.out())]; }
        public double value(double[] demand) { double v=0; for(int n=0;n<delta.length;n++) v+=delta[n]*demand[n]; return v; }
    }
}
