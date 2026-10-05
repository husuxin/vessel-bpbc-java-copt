import copt.*;
import java.nio.file.*;
import java.util.*;

public final class Main {
    public static void main(String[] args) throws Exception {
        if(args.length==0) {
            System.out.println("Usage: run.ps1 --tiny [seconds] | --branch-demo [seconds] | <author-data.txt> [seconds]");
            return;
        }
        boolean tiny=args[0].equals("--tiny"), branch=args[0].equals("--branch-demo");
        Instance d=tiny?Instance.synthetic(3,1):branch?Instance.synthetic(3,-1):Instance.read(Path.of(args[0]));
        double seconds=args.length>1?Double.parseDouble(args[1]):60;
        Envr env=new Envr();
        try {
            Bpbc.Result r=new Bpbc(env,d,seconds,true).solve();
            System.out.printf(Locale.ROOT,"RESULT status=%s LB=%.8f UB=%.8f gap=%s rootLP=%.8f nodes=%d seconds=%.3f LPs=%d cuts=%d vesselCols=%d pilotCols=%d%n",
                    r.status(),r.lowerBound(),r.upperBound(),Double.isNaN(r.gap())?"NA":String.format(Locale.ROOT,"%.6f%%",100*r.gap()),
                    r.rootLp(),r.nodes(),r.seconds(),r.lpSolves(),r.cuts(),r.vesselColumns(),r.pilotColumns());
            for(Routes.Vessel v:r.vessels()) System.out.printf(Locale.ROOT,"vessel %d berth %d inward %d outward %d cost %.2f%n",
                    v.vessel(),v.berth(),v.in(),v.out(),v.cost());
            for(Routes.Pilot p:r.pilots()) System.out.println("pilot shift "+p.shift()+" tasks "+p.tasks()+" rest@"+p.restTime()+" position "+p.restPosition());
        } finally { env.dispose(); }
    }
}
