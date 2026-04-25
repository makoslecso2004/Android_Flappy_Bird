package com.example.myapplication;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
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
    private float birdVelocity = 0;
    private final float gravity = 1.5f;
    private final float jumpStrength = -25;
    private final int birdSize = 50;

    // Pipes
    private List<Pipe> pipes;
    private int pipeSpeed = 10;
    private final int pipeGap = 400;
    private final int pipeWidth = 150;
    private Random random;

    // Game State
    private int score = 0;
    private boolean isGameOver = false;
    private int screenWidth, screenHeight;

    // Firebase
    private DatabaseReference mDatabase;
    private long currentHighScore = 0;

    // Background
    private Bitmap backgroundBitmap;

    public GameView(Context context) {
        super(context);
        holder = getHolder();
        paint = new Paint();
        random = new Random();
        pipes = new ArrayList<>();

        // Load Background Image
        backgroundBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.flappybird);

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
            update();
            draw();
            sleep();
        }
    }

    private void update() {
        if (isGameOver) return;

        // Init bird position once screen size is known
        if (birdY == 0 && getHeight() > 0) {
            birdY = getHeight() / 2f;
            screenWidth = getWidth();
            screenHeight = getHeight();
        }

        // Gravity
        birdVelocity += gravity;
        birdY += birdVelocity;

        // Collision with top/bottom
        if (birdY < 0 || birdY > screenHeight - birdSize) {
            gameOver();
        }

        // Pipes logic
        if (pipes.isEmpty() || pipes.get(pipes.size() - 1).x < screenWidth - 600) {
            int maxPipeHeight = screenHeight - pipeGap - 200;
            if (maxPipeHeight <= 0) maxPipeHeight = 100; // Fallback for small screens
            int h = random.nextInt(maxPipeHeight) + 100;
            pipes.add(new Pipe(screenWidth, h));
        }

        for (int i = 0; i < pipes.size(); i++) {
            Pipe p = pipes.get(i);
            p.x -= pipeSpeed;

            // Collision check
            if (p.x < screenWidth / 2f + birdSize && p.x + pipeWidth > screenWidth / 2f) {
                if (birdY < p.height || birdY + birdSize > p.height + pipeGap) {
                    gameOver();
                }
            }

            // Score update
            if (!p.passed && p.x + pipeWidth < screenWidth / 2f) {
                score++;
                p.passed = true;
            }
        }

        // Remove off-screen pipes
        if (!pipes.isEmpty() && pipes.get(0).x < -pipeWidth) {
            pipes.remove(0);
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
                canvas.drawBitmap(backgroundBitmap, null, 
                    new android.graphics.Rect(0, 0, screenWidth, screenHeight), null);
            } else {
                canvas.drawColor(Color.CYAN); // Background Fallback
            }

            // Draw Pipes
            paint.setColor(Color.GREEN);
            for (Pipe p : pipes) {
                canvas.drawRect(p.x, 0, p.x + pipeWidth, p.height, paint);
                canvas.drawRect(p.x, p.height + pipeGap, p.x + pipeWidth, screenHeight, paint);
            }

            // Draw Bird
            paint.setColor(Color.YELLOW);
            canvas.drawRect(screenWidth / 2f, birdY, screenWidth / 2f + birdSize, birdY + birdSize, paint);

            // Draw Score
            paint.setColor(Color.BLACK);
            paint.setTextSize(100);
            canvas.drawText("Score: " + score, 50, 150, paint);
            
            // Draw Best Score
            paint.setTextSize(60);
            canvas.drawText("Best: " + currentHighScore, 50, 230, paint);

            if (isGameOver) {
                paint.setTextSize(150);
                paint.setTextAlign(Paint.Align.CENTER);
                paint.setColor(Color.BLACK);
                canvas.drawText("GAME OVER", screenWidth / 2f, screenHeight / 2f - 150, paint);
                
                paint.setTextSize(80);
                canvas.drawText("Tap to Restart", screenWidth / 2f, screenHeight / 2f, paint);

                // Draw Menu Button
                String menuText = "BACK TO MENU";
                paint.setTextSize(60);
                float textWidth = paint.measureText(menuText);
                float buttonWidth = textWidth + 100; // Add padding
                float buttonHeight = 120;
                float buttonTop = screenHeight / 2f + 100;
                float buttonBottom = buttonTop + buttonHeight;
                float buttonLeft = screenWidth / 2f - buttonWidth / 2f;
                float buttonRight = screenWidth / 2f + buttonWidth / 2f;

                paint.setColor(Color.DKGRAY);
                canvas.drawRect(buttonLeft, buttonTop, buttonRight, buttonBottom, paint);
                
                paint.setColor(Color.WHITE);
                canvas.drawText(menuText, screenWidth / 2f, buttonTop + 80, paint);
                
                // Reset alignment for next frame
                paint.setTextAlign(Paint.Align.LEFT);
            }

            holder.unlockCanvasAndPost(canvas);
        }
    }

    private void sleep() {
        try {
            Thread.sleep(17); // ~60 FPS
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
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
            if (isGameOver) {
                float x = event.getX();
                float y = event.getY();

                // Recalculate button bounds for click detection
                String menuText = "BACK TO MENU";
                paint.setTextSize(60);
                float textWidth = paint.measureText(menuText);
                float buttonWidth = textWidth + 100;
                float buttonTop = screenHeight / 2f + 100;
                float buttonBottom = buttonTop + 120;
                float buttonLeft = screenWidth / 2f - buttonWidth / 2f;
                float buttonRight = screenWidth / 2f + buttonWidth / 2f;

                // Check if "Back to Menu" button was clicked
                if (x > buttonLeft && x < buttonRight && y > buttonTop && y < buttonBottom) {
                    if (getContext() instanceof android.app.Activity) {
                        ((android.app.Activity) getContext()).finish();
                    }
                } else {
                    restart();
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
        birdVelocity = 0;
        score = 0;
        pipes.clear();
        isGameOver = false;
    }

    private static class Pipe {
        int x, height;
        boolean passed = false;

        Pipe(int x, int height) {
            this.x = x;
            this.height = height;
        }
    }
}
