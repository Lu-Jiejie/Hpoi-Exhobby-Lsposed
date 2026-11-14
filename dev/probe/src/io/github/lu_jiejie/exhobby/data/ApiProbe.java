package io.github.lu_jiejie.exhobby.data;

/**
 * JVM harness for the exhobby client; runs without an emulator.
 *
 * <pre>bash dev/probe.sh 3 1617 3194</pre>
 */
public final class ApiProbe {

    private static final int[] DEFAULT_IDS = {3, 8, 9, 1617, 3194, 99999999, 180001, 200000, 2400};

    public static void main(String[] args) {
        int[] ids = DEFAULT_IDS;
        if (args.length > 0) {
            ids = new int[args.length];
            for (int i = 0; i < args.length; i++) {
                ids[i] = Integer.parseInt(args[i]);
            }
        }
        for (int id : ids) {
            long start = System.currentTimeMillis();
            ExhobbyApi.Gallery g = ExhobbyApi.load(id, "hobby", null);
            long elapsed = System.currentTimeMillis() - start;
            System.out.println("id=" + id + " images=" + g.paths.size()
                    + " truncated=" + g.truncated
                    + " error=" + g.error + " took=" + elapsed + "ms");
            System.out.println("  page=" + g.pageUrl);
            for (int i = 0; i < Math.min(3, g.paths.size()); i++) {
                System.out.println("  " + ExhobbyApi.imgUrl(g.paths.get(i), "n"));
            }
        }
    }
}
