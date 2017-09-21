/*******************************************************************************
 * MapBench project (GPLv2 + CP)
 ******************************************************************************/
package it.geosolutions.java2d;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ToolBar;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import org.jfree.fx.FXGraphics2D;

/**
 *
 * @author bourgesl
 */
public class MapDemoFXApplication extends Application {

    // 1800 x 900 for Full-HD
    //  976 x 640 for XGA
    final static int WIDTH = 1800;
    final static int HEIGHT = 900;

    // members:
    private MapDemoFX bench;
    private ToolBar toolBar;
    private Label frameRate;

    private ChartCanvas canvas;
    private GraphicsContext gc;

    @Override
    public void init() throws Exception {
        super.init();

        this.bench = MapDemoFX.INSTANCE;
        this.bench.prepare();
    }

    @Override
    public void stop() throws Exception {
        super.stop();

        this.bench.showResults();
    }

    @Override
    public void start(Stage stage) throws Exception {
        stage.setTitle(getClass().getSimpleName());

        BorderPane root = new BorderPane();

        canvas = new ChartCanvas();
        gc = canvas.getGraphicsContext2D();

        toolBar = new ToolBar();
        root.setTop(toolBar);

        StackPane stackPane = new StackPane();
        stackPane.getChildren().add(canvas);
        // Bind canvas size to stack pane size. 
        canvas.widthProperty().bind(stackPane.widthProperty());
        canvas.heightProperty().bind(stackPane.heightProperty());

        root.setCenter(stackPane);

        {
            // Frame rate:
            Label label = new Label("Frame rate:");
            frameRate = new Label();
            frameRate.setAlignment(Pos.BASELINE_LEFT);
/*            
            frameRate.setPrefColumnCount(5);
            frameRate.setEditable(false);
*/
            toolBar.getItems().addAll(label, frameRate);
        }

        Scene scene = new Scene(root, WIDTH, HEIGHT);

        stage.setTitle("MapDemo-FX: " + BaseTest.getRenderingEngineName());
        stage.setScene(scene);
        stage.show();

        final AnimationTimer timer = new AnimationTimer() {
            private long nextSecond = 0;
            private int framesPerSecond = 0;

            @Override
            public void handle(long startNanos) {
                render(startNanos);

                framesPerSecond++;

                if (startNanos > nextSecond) {
                    // TODO: collect values
                    frameRate.setText(String.format("%d", framesPerSecond));
                    framesPerSecond = 0;
                    nextSecond = startNanos + 1_000_000_000L;
                }
            }
        };

        timer.start();
    }

    private final void clearBackground() {
        gc.setFill(Color.LIGHTGRAY);
        gc.fillRect(0, 0, WIDTH, HEIGHT);
    }

    private final void render(long startNanos) {
        try {
            // clearBackground();

            bench.render(canvas.g2, startNanos);
        } catch (IOException ex) {
            Logger.getLogger(MapDemoFXApplication.class.getName()).log(Level.SEVERE, null, ex);
        } catch (ClassNotFoundException ex) {
            Logger.getLogger(MapDemoFXApplication.class.getName()).log(Level.SEVERE, null, ex);
        }
    }

    class ChartCanvas extends Canvas {

        FXGraphics2D g2;

        public ChartCanvas() {
            this.g2 = new FXGraphics2D(getGraphicsContext2D());
            // Redraw canvas when size changes.
            widthProperty().addListener(e -> render(System.nanoTime()));
            heightProperty().addListener(e -> render(System.nanoTime()));
        }

        @Override
        public boolean isResizable() {
            return true;
        }

        @Override
        public double prefWidth(double height) {
            return getWidth();
        }

        @Override
        public double prefHeight(double width) {
            return getHeight();
        }
    }
}
