/*******************************************************************************
 * MapBench project (GPLv2 + CP)
 ******************************************************************************/
package it.geosolutions.java2d;

import static it.geosolutions.java2d.BaseTest.getSortedFiles;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.PathIterator;
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
    
    public static MapDemoFX INSTANCE = null;
    
    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.US);
        
        if (!BenchTest.useSharedImage) {
            System.out.println("Please set useSharedImage = true in your profile !");
            System.exit(1);
        }
        
        startTests();

        // Create singleton
        INSTANCE = new MapDemoFX();
        
        Application.launch(MapDemoFXApplication.class, args);
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
    final int threads = 1;
    final int[] nThTest = new int[threads];
    final double[] nThTotalMed = new double[threads];
    final double[] nThTotalPct95 = new double[threads];
    final double[] nThTotalFps = new double[threads];
    
    double initialTime;
    int testLoops;
    
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
    
    final static double ANGLE_STEP = 1.0 / 512; // 2 pixels at distance = 512px

    boolean warmup = false;
    int nPass = 0;
    File file = null;
    DrawingCommands commands = null;
    AffineTransform animAt = null;
    
    double cx, cy;
    
    void render(Graphics2D g2, long startNanos) throws IOException, ClassNotFoundException {

        // Prepare
        if (file == null) {
            
            file = dataFiles[dataFiles.length - 1]; // last
            
            System.out.println("Loading drawing commands from file: " + file.getAbsolutePath());
            commands = DrawingCommands.load(file);
            
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

            // set view transform once:
            // commands.setAt(viewAT);
            System.out.println("drawing[" + file.getName() + "][width = " + commands.getWidth()
                    + ", height = " + commands.getHeight() + "] ...");
            
            if (doGCBeforeTest) {
                cleanup();
            }
            
            commands.setAt(null);
            commands.prepareCommands(MapConst.doClip, MapConst.doUseWingRuleEvenOdd, PathIterator.WIND_EVEN_ODD);
            commands.setAt(null);
            
            commands.prepareWindow(MapDemoFXApplication.WIDTH, MapDemoFXApplication.HEIGHT);

            // Prepare the animation affine transform:
            cx = (commands.width / 2.0);
            cy = (commands.height / 2.0);
            final double hx = Math.max(0, cx - MapDemoFXApplication.WIDTH / 2.0);
            final double hy = Math.max(0, cy - MapDemoFXApplication.HEIGHT / 2.0);
            
            animAt = new AffineTransform();
            animAt.translate(-hx, -hy);
            
        }

        // Render
        {
            commands.execute(g2, animAt);

            // animate graphics:
            animAt.rotate(ANGLE_STEP, cx, cy);
        }
        /*
        // TODO: use next file

        commands.dispose();

        // Marlin stats:
        dumpRendererStats();
          */
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
}
