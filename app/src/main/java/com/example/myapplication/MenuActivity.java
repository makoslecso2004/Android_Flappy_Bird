package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class MenuActivity extends AppCompatActivity {

    private TextView tvHighScore;
    private Button btnPlay, btnLogout;
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_menu);

        mAuth = FirebaseAuth.getInstance();
        if (mAuth.getCurrentUser() == null) {
            startActivity(new Intent(MenuActivity.this, LoginActivity.class));
            finish();
            return;
        }

        String userId = mAuth.getCurrentUser().getUid();
        mDatabase = FirebaseDatabase.getInstance("https://my-flappy-bird-ced9a-default-rtdb.europe-west1.firebasedatabase.app").getReference().child("users").child(userId);

        tvHighScore = findViewById(R.id.tvHighScore);
        btnPlay = findViewById(R.id.btnPlay);
        btnLogout = findViewById(R.id.btnLogout);

        loadHighScore();

        btnPlay.setOnClickListener(v -> {
            startActivity(new Intent(MenuActivity.this, GameActivity.class));
        });

        btnLogout.setOnClickListener(v -> {
            mAuth.signOut();
            startActivity(new Intent(MenuActivity.this, LoginActivity.class));
            finish();
        });
    }

    private void loadHighScore() {
        mDatabase.child("highScore").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    long highScore = (long) snapshot.getValue();
                    tvHighScore.setText("High Score: " + highScore);
                } else {
                    tvHighScore.setText("High Score: 0");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Failed to read value
            }
        });
    }
}
