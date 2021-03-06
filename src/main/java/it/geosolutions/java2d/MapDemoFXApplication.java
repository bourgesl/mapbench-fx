/*******************************************************************************
 * MapBench project (GPLv2 + CP)
 ******************************************************************************/
package it.geosolutions.java2d;

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
import javafx.scene.text.Font;
import javafx.stage.Stage;
import org.jfree.fx.FXGraphics2D;

/**
 *
 * @author bourgesl
 */
public final class MapDemoFXApplication extends Application {

    private final static Logger logger = Logger.getLogger(MapDemoFXApplication.class.getName());

    // 1800 x 900 for Full-HD
    //  976 x 640 for XGA
    final static int WIDTH = Integer.getInteger("fxdemo.width", 1600);
    final static int HEIGHT = Integer.getInteger("fxdemo.height", 900);
    final static int MARGIN = 2 * 40;

    // members:
    private MapDemoFX bench;
    private ToolBar toolBar;
    private Label frameRate;

    private ChartCanvas canvas;

    @Override
    public void init() throws Exception {
        super.init();

        this.bench = MapDemoFX.INSTANCE;
        this.bench.prepare();
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
}
