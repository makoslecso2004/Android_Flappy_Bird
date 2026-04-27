package com.example.myapplication;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import androidx.annotation.NonNull;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class GameView extends SurfaceView implements Runnable {

    private Thread thread;
    private boolean isPlaying;
    private SurfaceHolder holder;
    private Paint paint;

    // Bird
    private float birdY;
    private float birdX;
    private float birdVelocity = 0;
    private final float gravity = 1.5f;
    private final float jumpStrength = -25;
    private final int birdSize = 50;

    // Pipes
    private List<PipePair> pipePairs;
    private int pipeSpeed = 10;
    private final int pipeGap = 400;
    private final int pipeWidth = 150;
    private Random random;

    // Game State
    private int score = 0;
    private boolean isGameOver = false;
    private boolean hasStarted = false;
    private int screenWidth, screenHeight;

    // Pre-allocated Rects for drawing
    private final android.graphics.Rect backgroundRect = new android.graphics.Rect();
    private final android.graphics.Rect birdRect = new android.graphics.Rect();
    private final android.graphics.Rect pipeDstRect = new android.graphics.Rect();

    // Firebase
    private DatabaseReference mDatabase;
    private long currentHighScore = 0;

    // Time Tracking for steady FPS
    private long lastTime = System.nanoTime();
    private static final double TARGET_FPS = 60.0;
    private static final double NS_PER_FRAME = 1_000_000_000.0 / TARGET_FPS;

    // Background
    private Bitmap backgroundBitmap;

    // Bird Bitmaps
    private Bitmap birdUpBitmap;
    private Bitmap birdDownBitmap;

    // Pipe Bitmaps
    private Bitmap pipeBitmap;
    private Bitmap topPipeBitmap;

    public GameView(Context context) {
        super(context);
        holder = getHolder();
        paint = new Paint();
        random = new Random();
        pipePairs = new ArrayList<>();

        // Load Background Image
        backgroundBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.backgroundday);

        // Load Bird Images
        birdUpBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.yellowbirdupflap);
        birdDownBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.yellowbirddownflap);

        // Load Pipe Image and create a flipped version for the top
        pipeBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.pipegreen);
        if (pipeBitmap != null) {
            Matrix matrix = new Matrix();
            matrix.postScale(1, -1); // Flip vertically
            topPipeBitmap = Bitmap.createBitmap(pipeBitmap, 0, 0, pipeBitmap.getWidth(), pipeBitmap.getHeight(), matrix, true);
        }

        String userId = "";
        if (FirebaseAuth.getInstance().getCurrentUser() != null) {
            userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
        }
        mDatabase = FirebaseDatabase.getInstance("https://my-flappy-bird-ced9a-default-rtdb.europe-west1.firebasedatabase.app").getReference().child("users").child(userId);
        
        // Initial load of high score to compare later
        mDatabase.child("highScore").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists() && snapshot.getValue() != null) {
                    currentHighScore = (long) snapshot.getValue();
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }

    @Override
    public void run() {
        while (isPlaying) {
            long now = System.nanoTime();
            double delta = (now - lastTime) / NS_PER_FRAME;

            if (delta >= 1) {
                update();
                draw();
                lastTime = now;
            } else {
                // Yield thread if we're way ahead of schedule
                if (delta < 0.5) {
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException ignored) {}
                }
            }
        }
    }

    private void update() {
        if (!hasStarted || isGameOver) {
            // Still need to init dimensions if not done
            if (screenWidth == 0 && getHeight() > 0) {
                screenWidth = getWidth();
                screenHeight = getHeight();
                birdY = screenHeight / 2f;
                birdX = screenWidth / 4f;
            }
            return;
        }

        // Gravity
        birdVelocity += gravity;
        birdY += birdVelocity;

        // Collision with top/bottom
        if (birdY < 0 || birdY > screenHeight - birdSize) {
            gameOver();
        }

        // Pipes logic
        if (pipePairs.isEmpty() || pipePairs.get(pipePairs.size() - 1).x < screenWidth - 800) {
            // Ensure pipes are long enough by scaling them once screen height is known
            if (pipeBitmap != null && pipeBitmap.getHeight() < screenHeight) {
                pipeBitmap = Bitmap.createScaledBitmap(pipeBitmap, pipeWidth, screenHeight, true);
                
                Matrix matrix = new Matrix();
                matrix.postScale(1, -1);
                topPipeBitmap = Bitmap.createBitmap(pipeBitmap, 0, 0, pipeBitmap.getWidth(), pipeBitmap.getHeight(), matrix, true);
            }

            int maxPipeHeight = screenHeight - pipeGap - 200;
            if (maxPipeHeight <= 0) maxPipeHeight = 100; // Fallback for small screens
            int h = random.nextInt(maxPipeHeight) + 100;
            pipePairs.add(new PipePair(screenWidth, h));
        }

        for (int i = 0; i < pipePairs.size(); i++) {
            PipePair p = pipePairs.get(i);
            p.x -= pipeSpeed;

            // Collision check
            if (p.x < birdX + birdSize && p.x + pipeWidth > birdX) {
                if (birdY < p.gapTopY || birdY + birdSize > p.gapTopY + pipeGap) {
                    gameOver();
                }
            }

            // Score update
            if (!p.passed && p.x + pipeWidth < birdX) {
                score++;
                p.passed = true;
            }
        }

        // Remove off-screen pipes
        if (!pipePairs.isEmpty() && pipePairs.get(0).x < -pipeWidth) {
            pipePairs.remove(0);
        }
    }

    private void draw() {
        if (holder.getSurface().isValid()) {
            Canvas canvas = holder.lockCanvas();
            if (canvas == null) return;

            // Ensure dimensions are set even if update hasn't run yet
            if (screenWidth == 0) {
                screenWidth = getWidth();
                screenHeight = getHeight();
                birdY = screenHeight / 2f;
            }

            // Draw Background
            if (backgroundBitmap != null) {
                backgroundRect.set(0, 0, screenWidth, screenHeight);
                canvas.drawBitmap(backgroundBitmap, null, backgroundRect, null);
            } else {
                canvas.drawColor(Color.CYAN); // Background Fallback
            }

            // Draw Pipes
            if (hasStarted) {
                for (PipePair p : pipePairs) {
                    if (pipeBitmap != null && topPipeBitmap != null) {
                        // Top Pipe: Bottom of bitmap is at p.gapTopY
                        pipeDstRect.set(p.x, p.gapTopY - screenHeight, p.x + pipeWidth, p.gapTopY);
                        canvas.drawBitmap(topPipeBitmap, null, pipeDstRect, null);

                        // Bottom Pipe: Top of bitmap is at p.gapTopY + pipeGap
                        pipeDstRect.set(p.x, p.gapTopY + pipeGap, p.x + pipeWidth, p.gapTopY + pipeGap + screenHeight);
                        canvas.drawBitmap(pipeBitmap, null, pipeDstRect, null);
                    } else {
                        paint.setColor(Color.GREEN);
                        canvas.drawRect(p.x, 0, p.x + pipeWidth, p.gapTopY, paint);
                        canvas.drawRect(p.x, p.gapTopY + pipeGap, p.x + pipeWidth, screenHeight, paint);
                    }
                }
            }

            // Draw Bird
            if (hasStarted) {
                Bitmap currentBird = birdVelocity < 0 ? birdDownBitmap : birdUpBitmap;
                if (currentBird != null) {
                    birdRect.set((int) birdX, (int) birdY, (int) (birdX + birdSize), (int) (birdY + birdSize));
                    canvas.drawBitmap(currentBird, null, birdRect, null);
                } else {
                    paint.setColor(Color.YELLOW);
                    canvas.drawRect(birdX, birdY, birdX + birdSize, birdY + birdSize, paint);
                }
            }

            // Draw Score
            if (hasStarted) {
                paint.setColor(Color.BLACK);
                paint.setTextSize(100);
                canvas.drawText("Score: " + score, 50, 150, paint);
            }
            
            // Draw Best Score
            if (hasStarted) {
                paint.setTextSize(60);
                canvas.drawText("Best: " + currentHighScore, 50, 230, paint);
            }

            if (!hasStarted) {
                drawMenuOverlay(canvas, "TAP TO START");
            } else if (isGameOver) {
                drawMenuOverlay(canvas, "GAME OVER");
            }

            holder.unlockCanvasAndPost(canvas);
        }
    }

    private void drawMenuOverlay(Canvas canvas, String title) {
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(Color.BLACK);
        paint.setTextSize(150);
        canvas.drawText(title, screenWidth / 2f, screenHeight / 2f - 150, paint);

        if (title.equals("GAME OVER")) {
            paint.setTextSize(80);
            canvas.drawText("Tap to Restart", screenWidth / 2f, screenHeight / 2f, paint);
        }

        // Draw Back to Menu Button
        String menuText = "BACK TO MENU";
        paint.setTextSize(60);
        float textWidth = paint.measureText(menuText);
        float buttonWidth = textWidth + 100;
        float buttonHeight = 120;
        float buttonTop = screenHeight / 2f + 100;
        float buttonBottom = buttonTop + buttonHeight;
        float buttonLeft = screenWidth / 2f - buttonWidth / 2f;
        float buttonRight = screenWidth / 2f + buttonWidth / 2f;

        paint.setColor(Color.DKGRAY);
        canvas.drawRect(buttonLeft, buttonTop, buttonRight, buttonBottom, paint);

        paint.setColor(Color.WHITE);
        canvas.drawText(menuText, screenWidth / 2f, buttonTop + 80, paint);

        // Reset for next frame
        paint.setTextAlign(Paint.Align.LEFT);
    }

    public void resume() {
        isPlaying = true;
        thread = new Thread(this);
        thread.start();
    }

    public void pause() {
        isPlaying = false;
        try {
            thread.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            if (!hasStarted || isGameOver) {
                float x = event.getX();
                float y = event.getY();

                // Button bounds (same as in drawMenuOverlay)
                paint.setTextSize(60);
                float textWidth = paint.measureText("BACK TO MENU");
                float buttonWidth = textWidth + 100;
                float buttonTop = screenHeight / 2f + 100;
                float buttonBottom = buttonTop + 120;
                float buttonLeft = screenWidth / 2f - buttonWidth / 2f;
                float buttonRight = screenWidth / 2f + buttonWidth / 2f;

                if (x > buttonLeft && x < buttonRight && y > buttonTop && y < buttonBottom) {
                    if (getContext() instanceof android.app.Activity) {
                        ((android.app.Activity) getContext()).finish();
                    }
                } else {
                    if (!hasStarted) {
                        hasStarted = true;
                        birdVelocity = jumpStrength;
                    } else {
                        restart();
                        hasStarted = true; // Start immediately after restart
                        birdVelocity = jumpStrength; // Apply initial jump
                    }
                }
            } else {
                birdVelocity = jumpStrength;
            }
        }
        return true;
    }

    private void gameOver() {
        isGameOver = true;
        if (score > currentHighScore) {
            currentHighScore = score;
            mDatabase.child("highScore").setValue(currentHighScore);
        }
    }

    private void restart() {
        birdY = screenHeight / 2f;
        birdX = screenWidth / 4f;
        birdVelocity = 0;
        score = 0;
        pipePairs.clear();
        isGameOver = false;
        hasStarted = false;
    }

    private static class PipePair {
        int x, gapTopY;
        boolean passed = false;

        PipePair(int x, int gapTopY) {
            this.x = x;
            this.gapTopY = gapTopY;
        }
    }
}
