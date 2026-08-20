package com.jiashuai.touchlayoutapplication;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;

import com.jiashuai.touchlayoutapplication.touch.ChartTouchLayout;

public class MainActivity extends AppCompatActivity {

    private ChartTouchLayout layout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        CheckBox multipleChoice = findViewById(R.id.cb_multiple_choice);
        CheckBox snapEnabled = findViewById(R.id.cb_snap_enabled);
        Button reset = findViewById(R.id.btn_reset);
        layout = findViewById(R.id.layout);

        layout.setSnapThreshold(dpToPx(12));
        multipleChoice.setOnCheckedChangeListener((buttonView, isChecked) -> layout.setMultiSelected(isChecked));
        snapEnabled.setOnCheckedChangeListener((buttonView, isChecked) -> layout.setSnapEnabled(isChecked));
        reset.setOnClickListener(view -> recreate());
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
