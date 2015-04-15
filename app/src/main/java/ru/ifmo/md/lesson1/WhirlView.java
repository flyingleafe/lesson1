package ru.ifmo.md.lesson1;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
* Created by thevery on 11/09/14.
*/
class WhirlView extends SurfaceView implements Runnable {

    static final int width = 240;
    static final int height = 320;
    int [][] field = new int[width][height], field2 = new int[width][height];
    int [] colors = new int[width * height];
    Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    Rect area;
    Random rand = new Random();
    static final int MAX_COLOR = 10;
    static final int[] palette = {0xFFFF0000, 0xFF800000, 0xFF808000, 0xFF008000, 0xFF00FF00, 0xFF008080, 0xFF0000FF, 0xFF000080, 0xFF800080, 0xFFFFFFFF};
    static final int WORKERS_COUNT = 4;
    static final TimeUnit MS = TimeUnit.MILLISECONDS;
    SurfaceHolder holder;
    Thread thread = null;
    List<Runnable> workers = new ArrayList<Runnable>(WORKERS_COUNT);
    List<Future<?>> futures = new ArrayList<Future<?>>();
    ExecutorService executor = Executors.newFixedThreadPool(WORKERS_COUNT);
    volatile boolean running = false;

    class FieldWorker implements Runnable {
        int startX;
        int startY;
        int width, height;

        FieldWorker(int startX, int startY, int width, int height) {
            this.startX = startX;
            this.startY = startY;
            this.width = width;
            this.height = height;
        }

        @Override
        public void run() {
            int wm1 = width - 1;
            for (int x=startX; x<wm1; x++) {
                for (int y=startY; y<height; y++) {

                    field2[x][y] = field[x][y];
                    int nextColor = field[x][y]+1;
                    if (nextColor == MAX_COLOR) {
                        nextColor = 0;
                    }
                    boolean out = false;
                    for (int dx=-1; !out && dx<=1; dx++) {
                        int x2 = x + dx;
                        for (int dy=-1; dy<=1; dy++) {
                            int y2 = y + dy;
                            if (nextColor == field[x2][y2]) {
                                field2[x][y] = field[x2][y2];
                                out = true;
                                break;
                            }
                        }
                    }
                }
            }
        }
    }

    class EdgeWorker implements Runnable {
        void updateCell(int x, int y) {
            field2[x][y] = field[x][y];
            boolean out = false;
            int nextColor = field[x][y]+1;
            if (nextColor == MAX_COLOR) {
                nextColor = 0;
            }
            for (int dx=-1; !out && dx<=1; dx++) {
                int x2 = x + dx;
                if (x2<0) x2 += width;
                if (x2>=width) x2 -= width;
                for (int dy=-1; dy<=1; dy++) {
                    int y2 = y + dy;
                    if (y2<0) y2 += height;
                    if (y2>=height) y2 -= height;
                    if ( nextColor == field[x2][y2]) {
                        field2[x][y] = field[x2][y2];
                        out = true;
                        break;
                    }
                }
            }
        }

        @Override
        public void run() {
            int wm1 = width - 1;
            int hm1 = height - 1;
            for(int x = 0; x < width; x++) {
                updateCell(x, 0);
                updateCell(x, hm1);
            }

            for(int y = 0; y < height; y++) {
                updateCell(0, y);
                updateCell(wm1, y);
            }
        }
    }

    public WhirlView(Context context) {
        super(context);
        holder = getHolder();
        for(int i = 0; i < WORKERS_COUNT - 1; i++) {
            int parth = (height - 2) / (WORKERS_COUNT - 1);
            int lasth = (height - 2) % parth;
            if (i == WORKERS_COUNT - 2 && lasth != 0) {
                parth = lasth;
            }
            int startX = 1;
            int startY = i * parth + 1;
            workers.add(new FieldWorker(startX, startY, width, startY + parth));
        }
        workers.add(new EdgeWorker());
    }

    public void resume() {
        running = true;
        thread = new Thread(this);
        thread.start();
    }

    public void pause() {
        running = false;
        try {
            thread.join();
        } catch (InterruptedException ignore) {}
    }

    public void run() {
        while (running) {
            if (holder.getSurface().isValid()) {
                long startTime = System.nanoTime();
                Canvas canvas = holder.lockCanvas();
                updateField();
                drawIt(canvas);
                holder.unlockCanvasAndPost(canvas);
                long finishTime = System.nanoTime();
                Log.i("FPS", "Circle: " + 1000.0 / (double)((finishTime - startTime) / 1000000));
                try {
                    Thread.sleep(16);
                } catch (InterruptedException ignore) {}
            }
        }
    }

    @Override
    public void onSizeChanged(int w, int h, int oldW, int oldH) {
        area = new Rect(0, 0, w, h);
        initField();
    }

    void initField() {
        for (int x=0; x<width; x++) {
            for (int y=0; y<height; y++) {
                field[x][y] = rand.nextInt(MAX_COLOR);
            }
        }
    }

    void updateField() {
        for(Runnable w: workers) {
            futures.add(executor.submit(w));
        }
        try {
            for (Future<?> f : futures) {
                f.get();
            }
        } catch (InterruptedException ignore) {
        } catch (ExecutionException ignore) {}
        for (int x=0; x<width; x++) {
            System.arraycopy(field2[x], 0, field[x], 0, height);
        }
    }

    public void drawIt(Canvas canvas) {
        int cnum = 0;
        for (int y=0; y<height; y++) {
            for (int x=0; x<width; x++) {
                colors[cnum] = palette[field[x][y]];
                cnum++;
            }
        }
        bitmap.setPixels(colors, 0, width, 0, 0, width, height);
        canvas.drawBitmap(bitmap, null, area, null);
    }
}
