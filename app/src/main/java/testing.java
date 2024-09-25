package com.example.helloworldapp;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Create a TextView programmatically
        TextView textView = new TextView(this);
        textView.setText("Hello, World!");
        textView.setTextSize(24); // Set the font size
        textView.setPadding(16, 16, 16, 16); // Set padding

        // Set the TextView as the content view
        setContentView(textView);
    }
}

