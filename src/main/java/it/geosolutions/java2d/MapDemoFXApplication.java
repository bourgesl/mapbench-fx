/*******************************************************************************
 * MapBench project (GPLv2 + CP)
 ******************************************************************************/
package it.geosolutions.java2d;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Label;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Ellipse;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.Shape;
import javafx.scene.text.Font;
import javafx.stage.Stage;
import org.jfree.fx.FXGraphics2D;

/**
 * Path.subtract performance issue:
 * - 1 rect with 10 ellipses:
 *   computeComplexClip(): 1.071097 ms.
 *   computeComplexClip(): 1.301656 ms.
 *   computeComplexClip(): 1.272631 ms.
 *   computeComplexClip(): 1.032191 ms.
 * - 1 rect with 10 ellipses subtracted twice:
 *   computeComplexClip(): 3609.028357 ms.
 *   computeComplexClip(): 3665.53341 ms.
 *   computeComplexClip(): 3659.093216 ms.
 *   computeComplexClip(): 3656.288927 ms.
 */
public final class MapDemoFXApplication extends Application {

    private final static Logger logger = Logger.getLogger(MapDemoFXApplication.class.getName());

    // 1800 x 900 for Full-HD
    //  976 x 640 for XGA
    final static int WIDTH = Integer.getInteger("fxdemo.width", 1600);
    final static int HEIGHT = Integer.getInteger("fxdemo.height", 900);

    private final static int MARGIN = 2 * 40;

    private final static boolean TRACE = false;

    private final static boolean DO_CLIP_EVERY_RENDER = "true".equalsIgnoreCase(System.getProperty("MapBenchFX.doClipEveryRender", "true"));
    private final static boolean USE_COMPLEX_CLIP = "true".equalsIgnoreCase(System.getProperty("MapBenchFX.useComplexClip", "false"));
    private final static boolean USE_COMPLEX_CLIP_TWICE = "true".equalsIgnoreCase(System.getProperty("MapBenchFX.useComplexClipTwice", "false"));

    final static int COMPLEX_CLIP_ELLIPSES = 10;

    static {
        System.out.println("MapBenchFX.doClipEveryRender:   " + DO_CLIP_EVERY_RENDER);
        System.out.println("MapBenchFX.useComplexClip:      " + USE_COMPLEX_CLIP);
        System.out.println("MapBenchFX.useComplexClipTwice: " + USE_COMPLEX_CLIP_TWICE);
        System.out.println("COMPLEX_CLIP_ELLIPSES:          " + COMPLEX_CLIP_ELLIPSES);
    }

    private final static boolean USE_SEED_FIXED = true; // random seed fixed for reproducibility
    private final static long SEED_FIXED = 3447667858947863824L;

    // start (from Math.random)
    static Random getRandom() {
        final long seed;
        if (USE_SEED_FIXED) {
            seed = SEED_FIXED;
        } else {
            seed = seedUniquifier() ^ System.nanoTime();
        }
        System.out.println("Random seed: " + seed);
        return new Random(seed);
    }

    private static long seedUniquifier() {
        // L'Ecuyer, "Tables of Linear Congruential Generators of
        // Different Sizes and Good Lattice Structure", 1999
        for (;;) {
            long current = seedUniquifier.get();
            long next = current * 1181783497276652981L;
            if (seedUniquifier.compareAndSet(current, next)) {
                return next;
            }
        }
    }

    private static final AtomicLong seedUniquifier
                                    = new AtomicLong(8682522807148012L);
    // end (from Math.random)

    // note: must be after seedUniquifier field (to be defined)
    private final static Random random = getRandom(); // fixed seed for reproducibility

    // members:
    private MapDemoFX bench;
    private ToolBar toolBar;
    private Label frameRate;

    private ChartCanvas canvas;

    private List<Ellipse> points = null;

    public MapDemoFXApplication() {
        super();
    }

    @Override
    public void init() throws Exception {
        super.init();

        this.bench = MapDemoFX.INSTANCE;
        this.bench.prepare();

        if (USE_COMPLEX_CLIP) {
            initPoints();
        }
    }

    @Override
    public void stop() {
        try {
            super.stop();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        } finally {
            this.bench.showResults();
        }
    }

    @Override
    public void start(Stage stage) throws Exception {
        canvas = new ChartCanvas(WIDTH, HEIGHT);

        final StackPane stackPane = new StackPane();
        stackPane.getChildren().add(canvas);
        if (false) {
            // Bind canvas size to stack pane size. 
            canvas.widthProperty().bind(stackPane.widthProperty());
            canvas.heightProperty().bind(stackPane.heightProperty());
        }

        final BorderPane root = new BorderPane();
        root.setCenter(stackPane);

        toolBar = new ToolBar();
        root.setTop(toolBar);

        {
            final Font font = new Font(40);
            // Frame rate:
            Label label = new Label("Frame rate:");
            label.setFont(font);
            frameRate = new Label();
            frameRate.setFont(font);

            toolBar.getItems().addAll(label, frameRate);
        }

        final Scene scene = new Scene(root, WIDTH + MARGIN, HEIGHT + MARGIN);
        stage.setTitle("MapDemo-FX ");
        stage.setScene(scene);
        stage.show();

        final AnimationTimer timer = new AnimationTimer() {
            private int nbFrames = 0;
            private long lastTime = System.nanoTime();
            private long lastInstant = lastTime;
            private long nextInstant = lastTime + 500_000_000L;

            @Override
            public void handle(long startNanos) {
                final long elapsed = startNanos - lastTime;
                lastTime = startNanos;

                if (render(startNanos, elapsed)) {
                    stop();
                    try {
                        MapDemoFXApplication.this.stop();
                    } catch (Exception ex) {
                        Logger.getLogger(MapDemoFXApplication.class.getName()).log(Level.SEVERE, null, ex);
                    }
                }

                nbFrames++;

                if (startNanos > nextInstant) {
                    frameRate.setText(String.format("%.3f", 5e8 * nbFrames / (startNanos - lastInstant)));

                    // reset
                    nbFrames = 0;
                    lastInstant = startNanos;
                    nextInstant = startNanos + 500_000_000L;
                }
            }
        };

        timer.start();
    }

    private boolean render(final long startNanos, final long elapsed) {
        if (DO_CLIP_EVERY_RENDER) {
            canvas.setClip();
        } else if ((bench.getState() == MapDemoFX.State.Init) && (bench.getIter() == 0)) {
            System.out.println("initClip !");
            canvas.setClip();
        }
        try {
            // clearBackground();
            return bench.render(canvas.g2, startNanos, elapsed);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "failure", e);
        }
        return true;
    }

    final class ChartCanvas extends Canvas {

        final FXGraphics2D g2;

        ChartCanvas(double w, double h) {
            super(w, h);
            this.g2 = new FXGraphics2D(getGraphicsContext2D());
            // Redraw canvas when size changes.
            widthProperty().addListener(e -> setClip());
            heightProperty().addListener(e -> setClip());
        }

        void setClip() {
            g2.setClip(0, 0, (int) getWidth(), (int) getHeight());

            if (USE_COMPLEX_CLIP) {
                final Rectangle rect = new Rectangle(0, 0, (int) getWidth(), (int) getHeight());
                rect.setFill(Color.BLACK);

                final Shape clip = computeComplexClip(rect);

                if (TRACE) {
                    System.out.println("using fx clip shape: " + clip);
                }
                // clip on fx canvas:
                super.setClip(clip);
            }
        }

        @Override
        public boolean isResizable() {
            return true;
        }

        @Override
        public double prefWidth(double width) {
            return getWidth();
        }

        @Override
        public double prefHeight(double height) {
            return getHeight();
        }
    }

    private Shape computeComplexClip(final Rectangle rect) {
        final long startTime = System.nanoTime();

        Shape clip = rect;

        final double w = rect.getWidth();
        final double h = rect.getHeight();

        for (int i = 0; i < points.size(); i++) {
            final Ellipse ellipse = points.get(i);

            final Ellipse clipEllipse = new Ellipse();
            clipEllipse.setStrokeWidth(5.0);
            clipEllipse.setCenterX(ellipse.getCenterX() * w);
            clipEllipse.setCenterY(ellipse.getCenterY() * h);
            clipEllipse.setRadiusX(ellipse.getRadiusX() * w);
            clipEllipse.setRadiusY(ellipse.getRadiusY() * h);
            clipEllipse.setFill(Color.BLACK);

            if (TRACE) {
                System.out.println("Shape.subtract(): from " + clip + " with " + clipEllipse + " before");
            }

            clip = Shape.subtract(clip, clipEllipse);
            if (USE_COMPLEX_CLIP_TWICE) {
                clip = Shape.subtract(clip, clipEllipse);
            }

            if (TRACE) {
                System.out.println("Shape.subtract(): to " + clip + " after");
            }
        }
        System.out.println("computeComplexClip(): " + ((System.nanoTime() - startTime) / 1e6) + " ms.");

        return clip;
    }

    private void initPoints() {
        points = new ArrayList<>(COMPLEX_CLIP_ELLIPSES);

        for (int i = 0; i < COMPLEX_CLIP_ELLIPSES; i++) {
            final double px = random.nextDouble();
            final double py = random.nextDouble();

            points.add(new Ellipse(px, py, 0.1, 0.05));
        }
    }

}
