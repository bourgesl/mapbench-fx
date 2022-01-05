/*******************************************************************************
 * MapBench project (GPLv2 + CP)
 ******************************************************************************/
package it.geosolutions.java2d;

import static it.geosolutions.java2d.BaseTest.getSortedFiles;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import javafx.application.Application;

/**
 *
 * @author bourgesl
 */
@SuppressWarnings("UseOfSystemOutOrSystemErr")
public final class MapDemoFX extends BenchTest {

    public enum State {
        BootWarmup,
        Init,
        WarmupTest,
        Run
    }

    static MapDemoFX INSTANCE = null;

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.US);

        /*
         * Workaround to set system properties in GraalVM / Substrate VM (native images)
         */
        setDefaultSystemProperty("javafx.verbose", "true");
        setDefaultSystemProperty("prism.verbose", "true");
        setDefaultSystemProperty("javafx.animation.fullspeed", "true");

        setDefaultSystemProperty("prism.marlin.log", "true");
        setDefaultSystemProperty("prism.marlin.useRef", "hard"); // hard or soft (default) / weak references

        setDefaultSystemProperty("prism.marlin.doStats", "false");

        System.out.println("System properties:\n" + System.getProperties());

        if (!BenchTest.useSharedImage) {
            System.out.println("Please set useSharedImage = true in your profile !");
            System.exit(1);
        }

        startTests();

        // Create singleton
        INSTANCE = new MapDemoFX();

        Application.launch(MapDemoFXApplication.class, args);
    }

    private static void setDefaultSystemProperty(String key, String value) {
        if (System.getProperty(key) == null) {
            System.setProperty(key, value);
        }
    }

    // members:
    final File[] dataFiles;
    final StringBuilder sbWarm;
    final StringBuilder sbRes;

    int nTest = 0;
    double totalMed = 0.0;
    double totalPct95 = 0.0;
    double totalFps = 0.0;
    // thread score:
    final int nThPass = 0;
    final int threads = 1; // single thread
    final int[] nThTest = new int[threads];
    final double[] nThTotalMed = new double[threads];
    final double[] nThTotalPct95 = new double[threads];
    final double[] nThTotalFps = new double[threads];

    int nOps = -1;
    long[] opss, nanoss;
    Result res = null;

    double initialTime;
    int testLoops;

    final static double ANGLE_STEP = 1.0 / 512; // 2 pixels at distance = 512px

    State state = State.BootWarmup;
    private long lastStartTime = 0L;

    int warmupPass = 1;
    int nWarmup = WARMUP_LOOPS_MIN;
    int nPass = 0;
    int iter = 0;
    int iterFile = 0;
    File file = null;
    DrawingCommands commands = null;
    AffineTransform animAt = null;

    double cx, cy, hx, hy;

    MapDemoFX() {
        this.dataFiles = getSortedFiles();

        System.out.println("Files: " + Arrays.toString(dataFiles));

        this.sbWarm = new StringBuilder(8 * 1024);
        sbWarm.append(Result.toStringHeader()).append('\n');

        this.sbRes = new StringBuilder(16 * 1024);
        sbRes.append(Result.toStringHeader()).append('\n');

        System.out.println("Results format: \n" + Result.toStringHeader());
    }

    void prepare() {
    }

    void resetAnimTx() {
        // Prepare the animation affine transform:
        animAt = getProfileViewTransform(null);
        if (animAt == null) {
            animAt = new AffineTransform();
        }
        // fix transform combinations:
        cx = commands.width / 2.0;
        cy = commands.height / 2.0;

        hx = cx - MapDemoFXApplication.WIDTH / (animAt.getScaleX() * 2.0);
        hy = cy - MapDemoFXApplication.HEIGHT / (animAt.getScaleY() * 2.0);

        animAt.translate(-hx, -hy);
    }

    boolean render(Graphics2D g2, long now, long elapsed) throws IOException, ClassNotFoundException {
        // Prepare
        if (file == null) {
            file = dataFiles[iterFile];
            /*
            [../maps/CircleTests.ser, 
            ../maps/EllipseTests-fill-false.ser, 
            ../maps/EllipseTests-fill-true.ser, 
            ../maps/dc_boulder_2013-13-30-06-13-17.ser, 
            ../maps/dc_boulder_2013-13-30-06-13-20.ser, 
            ../maps/dc_shp_alllayers_2013-00-30-07-00-43.ser, 
            ../maps/dc_shp_alllayers_2013-00-30-07-00-47.ser, 
            ../maps/dc_spearfish_2013-11-30-06-11-15.ser, 
            ../maps/dc_spearfish_2013-11-30-06-11-19.ser, 
            ../maps/dc_topp:states_2013-11-30-06-11-06.ser, 
            ../maps/dc_topp:states_2013-11-30-06-11-07.ser, 
            ../maps/test_z_625k.ser]
             */

            System.out.println("Loading drawing commands from file: " + file.getAbsolutePath());
            commands = DrawingCommands.load(file);

            System.out.println("drawing[" + file.getName() + "][width = " + commands.getWidth()
                    + ", height = " + commands.getHeight() + "] ...");

            if (doGCBeforeTest) {
                cleanup();
            }

            commands.setAt(null);
            commands.prepareCommands(MapConst.doClip, MapConst.doUseWindingRule, MapConst.customWindingRule);
            commands.setAt(null);

            commands.prepareWindow(MapDemoFXApplication.WIDTH, MapDemoFXApplication.HEIGHT);

            // Prepare the animation affine transform:
            resetAnimTx();
        }

        String sRes;

        if (state == State.BootWarmup) {
            if ((nOps != -1) && (iter >= nOps)) {
                res = new Result(commands.name, 1, nOps, opss, nanoss);
                res.totalTime = Result.toMillis(now - lastStartTime);

                System.out.println("Warm up took " + res.totalTime + " ms");
                sRes = res.toString();

                System.out.println(sRes);
                sbWarm.append("<<< Warmup ").append(warmupPass).append("\n");
                sbWarm.append(sRes).append('\n');
                sbWarm.append(">>> Warmup ").append(warmupPass).append("\n");

                /*
                // Marlin stats:
                dumpRendererStats();
                 */
                if (nWarmup <= WARMUP_LOOPS_MAX / 2) {
                    nWarmup *= 2;
                    warmupPass++;
                    iter = 0;
                } else {
                    // transition to Init                
                    state = State.Init;
                    iter = 0;
                }
            }
            if (iter == 0) {
                lastStartTime = now;

                nOps = nWarmup;
                opss = new long[nOps];
                nanoss = new long[nOps];

                System.out.println("\nWarming up with " + nWarmup + " loops on " + file.getAbsolutePath());
            }
        }
        if (state == State.Init) {
            if (iter == 0) {
                lastStartTime = now;

                nOps = 3;
                opss = new long[nOps];
                nanoss = new long[nOps];

            } else if (iter >= nOps) {
                res = new Result(commands.name, 1, nOps, opss, nanoss);
                res.totalTime = Result.toMillis(now - lastStartTime);

                initialTime = Result.toMillis(res.nsPerOpMed);

                System.out.println("Initial test: " + initialTime + " ms.");

                initialTime *= 0.95d; // 5% margin
                testLoops = Math.max(WARMUP_BEFORE_TEST_MIN_LOOPS, (int) (WARMUP_BEFORE_TEST_MIN_DURATION / initialTime));

                // transition to Warmup                
                state = State.WarmupTest;
                iter = 0;
            }
        }
        if (state == State.WarmupTest) {
            if (iter == 0) {
                BaseTest.isWarmup = true;

                lastStartTime = now;

                nOps = testLoops;
                opss = new long[nOps];
                nanoss = new long[nOps];

                System.out.println("\nWarming up " + testLoops + " loops on " + file.getAbsolutePath());

            } else if (iter >= nOps) {
                BaseTest.isWarmup = false;

                res = new Result(commands.name, 1, nOps, opss, nanoss);
                res.totalTime = Result.toMillis(now - lastStartTime);

                System.out.println("Warm up took " + res.totalTime + " ms");
                sRes = res.toString();

                System.out.println(sRes);
                sbWarm.append(sRes).append('\n');

                initialTime = Result.toMillis(res.nsPerOpMed);

                System.out.println("Initial test: " + initialTime + " ms.");

                initialTime *= 0.95d; // 5% margin
                testLoops = Math.max(MIN_LOOPS, (int) (MIN_DURATION / initialTime));

                /*
                // Marlin stats:
                dumpRendererStats();
                 */
                // transition to Warmup                
                state = State.Run;
                iter = 0;
            }
        }
        if (state == State.Run) {
            if (iter == 0) {
                System.out.println("Testing file " + file.getAbsolutePath() + " for " + testLoops + " loops ...");

                lastStartTime = now;

                nOps = testLoops;
                opss = new long[nOps];
                nanoss = new long[nOps];

                // reset AT:
                resetAnimTx();

            } else if (iter >= nOps) {
                res = new Result(commands.name, 1, nOps, opss, nanoss);
                res.totalTime = Result.toMillis(now - lastStartTime);

                System.out.println(threads + " threads and " + testLoops + " loops per thread, time: " + res.totalTime + " ms");
                sRes = res.toString();

                System.out.println(sRes);
                sbRes.append(sRes).append('\n');

                nTest++;
                totalMed += res.nsPerOpMed;
                totalPct95 += res.nsPerOpPct95;
                totalFps += res.getFpsMed();

                nThTest[nThPass]++;
                nThTotalMed[nThPass] += res.nsPerOpMed;
                nThTotalPct95[nThPass] += res.nsPerOpPct95;
                nThTotalFps[nThPass] += res.getFpsMed();

                System.out.println("\n");

                // Goto next file
                if (++iterFile < dataFiles.length) {
                    file = null;

                    // transition to Init                
                    state = State.Init;
                    iter = 0;

                    return false;
                } else {
                    return true;
                }
            }
        }
        // Render
        commands.execute(g2, animAt);

        // animate graphics:
        animAt.rotate(ANGLE_STEP, cx, cy);

        /*
        // TODO: use next file

        commands.dispose();
         */
        opss[iter]++;
        // set timing:
        nanoss[iter] += elapsed;
        iter++;

        return false;
    }

    void showResults() {

        System.out.println("WARMUP results:");
        System.out.println(sbWarm.toString());
        System.out.println("TEST results:");
        System.out.println(sbRes.toString());

        // Scores:
        final StringBuilder sbScore = new StringBuilder(1024);

        sbScore.append("Tests\t");
        sbScore.append(nTest).append('\t');

        sbScore.append(nThTest[nThPass]).append('\t');

        sbScore.append("\nThreads\t");
        sbScore.append(1).append('\t');

        sbScore.append(threads).append('\t');

        // Median:
        sbScore.append("\nMed\t");
        sbScore.append(String.format("%.3f",
                Result.toMillis(totalMed / (double) nTest))).append('\t');

        sbScore.append(String.format("%.3f",
                Result.toMillis(nThTotalMed[nThPass] / (double) nThTest[nThPass]))).append('\t');

        // 95 percentile:
        sbScore.append("\nPct95\t");
        sbScore.append(String.format("%.3f",
                Result.toMillis(totalPct95 / (double) nTest))).append('\t');

        sbScore.append(String.format("%.3f",
                Result.toMillis(nThTotalPct95[nThPass] / (double) nThTest[nThPass]))).append('\t');

        // Fps:
        sbScore.append("\nFPS\t");
        sbScore.append(String.format("%.3f",
                totalFps / (double) nTest)).append('\t');

        sbScore.append(String.format("%.3f",
                nThTotalFps[nThPass] / (double) nThTest[nThPass])).append('\t');
        sbScore.append('\n');

        System.out.println("Scores:");
        System.out.println(sbScore.toString());
    }

    public State getState() {
        return state;
    }

    public File getFile() {
        return file;
    }

    public int getIter() {
        return iter;
    }

    public int getNops() {
        return nOps;
    }

}
