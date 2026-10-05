import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;

/** Numeric instance format from the authors' data folder; all time indices are zero-based. */
public final class Instance {
    public final int K, I, S, T, B;
    public final int[] berthReady, channelBusy, initialHeadway, duration, vesselLevel;
    public final int[][] window, restWindow, shiftWindow, handling, setup, headway, headwayPairs, conflictPairs;
    public final double[][] taskCost, berthCost;
    public final double[] pilotCost;
    public final boolean[][] allowed;

    private Instance(Map<String, double[]> a) {
        K = scalar(a, "K"); I = scalar(a, "I"); S = scalar(a, "S");
        T = scalar(a, "T"); B = scalar(a, "B");
        if (K < 1 || I != 2*K || S < 1 || T < 1 || B < 1)
            throw new IllegalArgumentException("Require I=2K and positive dimensions");
        berthReady = ints(a, "BRT", B); channelBusy = ints(a, "Chaocu", B);
        initialHeadway = ints(a, "HDT", B); duration = ints(a, "D", I+1);
        vesselLevel = ints(a, "VLL", K);
        window = matrix(a, "E", I, 2); restWindow = matrix(a, "BRK", S, 2);
        shiftWindow = matrix(a, "ST", S, 2); handling = matrix(a, "H", K, B);
        setup = matrix(a, "Q", I+1, I+1); headway = matrix(a, "F", I, I);
        headwayPairs = pairs(a, "Hset", scalar(a, "HWPN"));
        conflictPairs = pairs(a, "ODS", scalar(a, "NCPN"));
        taskCost = doubles(a, "C2", I, T); berthCost = doubles(a, "C3", K, B);
        pilotCost = values(a, "C4", S);
        for (int d : duration) if (d < 1) throw new IllegalArgumentException("Durations must be positive");
        for (int[] row : handling) for (int h : row) if (h < 0) throw new IllegalArgumentException("Negative handling time");
        for (int[] row : setup) for (int q : row) if (q < 0) throw new IllegalArgumentException("Negative setup time");
        for (double[] row : taskCost) nonnegative(row);
        for (double[] row : berthCost) nonnegative(row);
        nonnegative(pilotCost);
        for (int i = 0; i < I; i++) checkWindow(window[i], 0, T-1);
        for (int s = 0; s < S; s++) {
            checkWindow(shiftWindow[s], 0, T-1);
            checkWindow(restWindow[s], shiftWindow[s][0], shiftWindow[s][1]);
        }
        for (int[][] ps : new int[][][]{headwayPairs, conflictPairs})
            for (int[] p : ps) if (p[0] < 0 || p[0] >= I || p[1] < 0 || p[1] >= I || p[0] == p[1])
                throw new IllegalArgumentException("Invalid traffic task pair");
        allowed = new boolean[I][T];
        for (int i = 0; i < I; i++) for (int t = window[i][0]; t <= window[i][1]; t++) {
            boolean ok = false;
            for (int s = 0; s < S; s++) ok |= singletonFits(i, t, s);
            // Initial channel occupation/headway is encoded separately in the author data.
            for (int b = 0; b < B; b++) {
                if (i >= K && initialHeadway[b] >= 0 && t >= berthReady[b] && t < initialHeadway[b]) ok = false;
                if (i < K && vesselLevel[i] >= 2 && channelBusy[b] >= 0 && t >= berthReady[b] && t < channelBusy[b]) ok = false;
            }
            allowed[i][t] = ok;
        }
    }

    public boolean inShift(int t, int s) { return t >= shiftWindow[s][0] && t <= shiftWindow[s][1]; }
    public boolean singletonFits(int i, int t, int s) {
        return inShift(t, s) && (t >= restWindow[s][0]+duration[I]+setup[I][i]
                || t+duration[i]+setup[i][I] <= restWindow[s][1]);
    }
    public int key(int task, int time) { return task*T+time; }
    public int size() { return I*T; }

    public static Instance read(Path path) throws IOException { return parse(Files.readString(path)); }
    public static Instance parse(String text) {
        Map<String, double[]> a = new HashMap<>();
        Pattern declaration = Pattern.compile("(?:extern\\s+)?(?:const\\s+)?(?:int|double)\\s+(\\w+)\\s*(?:\\[[^]]*\\]\\s*)*=\\s*([^;]*);");
        Matcher m = declaration.matcher(text);
        Pattern number = Pattern.compile("[-+]?(?:\\d+(?:\\.\\d*)?|\\.\\d+)(?:[eE][-+]?\\d+)?");
        while (m.find()) {
            List<Double> vals = new ArrayList<>();
            Matcher n = number.matcher(m.group(2));
            while (n.find()) vals.add(Double.parseDouble(n.group()));
            a.put(m.group(1), vals.stream().mapToDouble(Double::doubleValue).toArray());
        }
        return new Instance(a);
    }

    /** Explicitly synthetic teaching/verification data, never labelled as a paper benchmark. */
    public static Instance synthetic(int vessels, int seed) {
        if (vessels < 1 || vessels > 3) throw new IllegalArgumentException("Synthetic fixture supports 1..3 vessels");
        int k=vessels, i=2*k, t=24, b=Math.max(2,k), s=2;
        Random random = new Random(seed);
        Map<String,double[]> a = new HashMap<>();
        a.put("K", new double[]{k}); a.put("I", new double[]{i}); a.put("T", new double[]{t});
        a.put("B", new double[]{b}); a.put("S", new double[]{s});
        double[] absent=new double[b]; Arrays.fill(absent,-1);
        a.put("BRT", new double[b]); a.put("Chaocu", absent);
        a.put("HDT", absent); a.put("VLL", new double[k]);
        double[] d = new double[i+1]; Arrays.fill(d,1); d[i]=2; a.put("D",d);
        double[] e = new double[i*2], c2=new double[i*t], h=new double[k*b], c3=new double[k*b];
        for (int v=0; v<k; v++) {
            e[2*v]=seed<0?0:2*v; e[2*v+1]=seed<0?3:2*v+2;
            e[2*(v+k)]=seed==-2?2:seed<0?9:9+2*v; e[2*(v+k)+1]=seed==-2?5:seed<0?12:11+2*v;
            for (int berth=0; berth<b; berth++) { h[v*b+berth]=seed==-2?1:3+random.nextInt(2); c3[v*b+berth]=3+random.nextInt(4); }
        }
        for (int task=0; task<i; task++) for (int time=0; time<t; time++) c2[task*t+time]=Math.max(0,time-e[2*task]);
        a.put("E",e); a.put("C2",c2); a.put("H",h); a.put("C3",c3);
        a.put("ST",new double[]{0,16,7,23}); a.put("BRK",new double[]{3,6,14,18});
        a.put("C4",new double[]{8,7});
        double[] q=new double[(i+1)*(i+1)];
        for (int x=0; x<i; x++) for (int y=0; y<i; y++) q[x*(i+1)+y]=1;
        a.put("Q",q);
        List<int[]> pairs=new ArrayList<>(); double[] f=new double[i*i];
        for (int x=0; x<i; x++) for (int y=0; y<i; y++)
            if (x!=y && (x<k)==(y<k)) { pairs.add(new int[]{x,y}); f[x*i+y]=1; }
        double[] p=new double[pairs.size()*2];
        for (int n=0; n<pairs.size(); n++) { p[n]=pairs.get(n)[0]; p[pairs.size()+n]=pairs.get(n)[1]; }
        a.put("F",f); a.put("HWPN",new double[]{pairs.size()}); a.put("Hset",p);
        a.put("NCPN",new double[]{seed==-2?1:0}); a.put("ODS",seed==-2?new double[]{0,k+1}:new double[0]);
        return new Instance(a);
    }

    private static double[] values(Map<String,double[]> a,String name,int count) {
        double[] v=a.get(name);
        if (v==null || v.length!=count) throw new IllegalArgumentException(name+": expected "+count+" values");
        for (double x:v) if (!Double.isFinite(x)) throw new IllegalArgumentException(name+": nonfinite value");
        return v.clone();
    }
    private static int scalar(Map<String,double[]> a,String name) { return ints(a,name,1)[0]; }
    private static int[] ints(Map<String,double[]> a,String name,int count) {
        double[] v=values(a,name,count); int[] r=new int[count];
        for (int n=0;n<count;n++) { r[n]=(int)v[n]; if (r[n]!=v[n]) throw new IllegalArgumentException(name+": noninteger"); }
        return r;
    }
    private static int[][] matrix(Map<String,double[]> a,String name,int rows,int cols) {
        int[] v=ints(a,name,rows*cols); int[][] r=new int[rows][cols];
        for(int n=0;n<rows;n++) System.arraycopy(v,n*cols,r[n],0,cols);
        return r;
    }
    private static double[][] doubles(Map<String,double[]> a,String name,int rows,int cols) {
        double[] v=values(a,name,rows*cols); double[][] r=new double[rows][cols];
        for(int n=0;n<rows;n++) System.arraycopy(v,n*cols,r[n],0,cols);
        return r;
    }
    private static int[][] pairs(Map<String,double[]> a,String name,int count) {
        int[] v=ints(a,name,2*count); int[][] r=new int[count][2];
        for(int n=0;n<count;n++) { r[n][0]=v[n]; r[n][1]=v[count+n]; }
        return r;
    }
    private static void nonnegative(double[] a) { for(double x:a) if(x<0) throw new IllegalArgumentException("Negative cost"); }
    private static void checkWindow(int[] w,int lo,int hi) {
        if(w[0]<lo || w[1]>hi || w[0]>w[1]) throw new IllegalArgumentException("Invalid time window "+Arrays.toString(w));
    }
}
