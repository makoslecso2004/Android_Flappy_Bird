package com.example.myapplication;

import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

public class ProfileActivity extends AppCompatActivity {

    private EditText etNewUsername;
    private Button btnSaveProfile, btnBackProfile;
    private FirebaseAuth mAuth;
    private DatabaseReference mDatabase;
    private String currentUsername = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        mAuth = FirebaseAuth.getInstance();
        if (mAuth.getCurrentUser() == null) {
            finish();
            return;
        }

        String userId = mAuth.getCurrentUser().getUid();
        mDatabase = FirebaseDatabase.getInstance("https://my-flappy-bird-ced9a-default-rtdb.europe-west1.firebasedatabase.app").getReference();

        etNewUsername = findViewById(R.id.etNewUsername);
        btnSaveProfile = findViewById(R.id.btnSaveProfile);
        btnBackProfile = findViewById(R.id.btnBackProfile);

        // Load current username
        mDatabase.child("users").child(userId).child("username").addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    currentUsername = snapshot.getValue(String.class);
                    etNewUsername.setText(currentUsername);
                }
            }
            @Override
            public void onCancelled(@NonNull DatabaseError error) {}
        });

        btnSaveProfile.setOnClickListener(v -> saveProfile());
        btnBackProfile.setOnClickListener(v -> finish());
    }

    private void saveProfile() {
        String newUsername = etNewUsername.getText().toString().trim();
        if (TextUtils.isEmpty(newUsername)) {
            Toast.makeText(this, "Enter username", Toast.LENGTH_SHORT).show();
            return;
        }

        if (newUsername.equals(currentUsername)) {
            finish();
            return;
        }

        // Check if new username is unique
        mDatabase.child("usernames").child(newUsername).addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                if (snapshot.exists()) {
                    Toast.makeText(ProfileActivity.this, "Username already taken", Toast.LENGTH_SHORT).show();
                } else {
                    updateUsername(newUsername);
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(ProfileActivity.this, "Error checking username", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void updateUsername(String newUsername) {
        String userId = mAuth.getCurrentUser().getUid();
        
        Map<String, Object> childUpdates = new HashMap<>();
        childUpdates.put("/users/" + userId + "/username", newUsername);
        childUpdates.put("/usernames/" + newUsername, userId);
        if (!TextUtils.isEmpty(currentUsername)) {
            childUpdates.put("/usernames/" + currentUsername, null);
        }

        mDatabase.updateChildren(childUpdates).addOnCompleteListener(task -> {
            if (task.isSuccessful()) {
                Toast.makeText(ProfileActivity.this, "Profile updated", Toast.LENGTH_SHORT).show();
                finish();
            } else {
                Toast.makeText(ProfileActivity.this, "Update failed", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
