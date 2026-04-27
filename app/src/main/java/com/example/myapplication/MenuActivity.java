package com.example.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MenuActivity extends AppCompatActivity {

    private TextView tvHighScore, tvLeaderboard;
    private Button btnPlay, btnLogout, btnEditProfile, btnBugReport;
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
        tvLeaderboard = findViewById(R.id.tvLeaderboard);
        btnPlay = findViewById(R.id.btnPlay);
        btnLogout = findViewById(R.id.btnLogout);
        btnEditProfile = findViewById(R.id.btnEditProfile);
        btnBugReport = findViewById(R.id.btnBugReport);

        loadHighScore();
        loadLeaderboard();

        btnPlay.setOnClickListener(v -> {
            startActivity(new Intent(MenuActivity.this, GameActivity.class));
        });

        btnLogout.setOnClickListener(v -> {
            mAuth.signOut();
            startActivity(new Intent(MenuActivity.this, LoginActivity.class));
            finish();
        });

        btnEditProfile.setOnClickListener(v -> {
            startActivity(new Intent(MenuActivity.this, ProfileActivity.class));
        });

        btnBugReport.setOnClickListener(v -> showBugReportDialog());
    }

    private void showBugReportDialog() {
        final EditText taskEditText = new EditText(this);
        taskEditText.setHint("Describe the bug here...");
        
        new AlertDialog.Builder(this)
                .setTitle("Report a Bug")
                .setMessage("Please describe the issue you encountered:")
                .setView(taskEditText)
                .setPositiveButton("Send", (dialog, which) -> {
                    String report = taskEditText.getText().toString().trim();
                    if (!report.isEmpty()) {
                        sendEmail(report);
                    } else {
                        Toast.makeText(MenuActivity.this, "Please enter a description", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void sendEmail(String bugDescription) {
        String username = "Unknown";
        if (mAuth.getCurrentUser() != null) {
            username = mAuth.getCurrentUser().getEmail();
        }

        String subject = "Flappy Bird Bug Report from " + username;
        String body = "Bug Description:\n" + bugDescription + "\n\n" +
                      "Device Info:\n" +
                      "Model: " + android.os.Build.MODEL + "\n" +
                      "Android Version: " + android.os.Build.VERSION.RELEASE;

        Intent intent = new Intent(Intent.ACTION_SENDTO);
        intent.setData(android.net.Uri.parse("mailto:"));
        intent.putExtra(Intent.EXTRA_EMAIL, new String[]{"makoslecso2004@gmail.com"});
        intent.putExtra(Intent.EXTRA_SUBJECT, subject);
        intent.putExtra(Intent.EXTRA_TEXT, body);

        try {
            startActivity(Intent.createChooser(intent, "Send Email..."));
        } catch (android.content.ActivityNotFoundException ex) {
            Toast.makeText(this, "No email client installed.", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadHighScore() {
        mDatabase.child("highScore").addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists() && snapshot.getValue() != null) {
                    long highScore = (long) snapshot.getValue();
                    tvHighScore.setText("Your Best: " + highScore);
                } else {
                    tvHighScore.setText("Your Best: 0");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                // Failed to read value
            }
        });
    }

    private void loadLeaderboard() {
        DatabaseReference usersRef = FirebaseDatabase.getInstance("https://my-flappy-bird-ced9a-default-rtdb.europe-west1.firebasedatabase.app").getReference().child("users");
        usersRef.orderByChild("highScore").limitToLast(10).addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                StringBuilder builder = new StringBuilder();
                List<DataSnapshot> list = new ArrayList<>();
                for (DataSnapshot postSnapshot : snapshot.getChildren()) {
                    list.add(postSnapshot);
                }
                Collections.reverse(list);

                int rank = 1;
                for (DataSnapshot ds : list) {
                    String name = ds.child("username").getValue(String.class);
                    Long score = ds.child("highScore").getValue(Long.class);
                    if (name != null && score != null) {
                        builder.append(rank).append(". ").append(name).append(": ").append(score).append("\n");
                        rank++;
                    }
                }
                tvLeaderboard.setText(builder.toString());
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });
    }
}
