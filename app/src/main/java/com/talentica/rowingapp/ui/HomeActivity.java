package com.talentica.rowingapp.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import com.talentica.rowingapp.R;

public class HomeActivity extends Activity implements View.OnClickListener {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        findViewById(R.id.home_start).setOnClickListener(this);
        findViewById(R.id.home_settings).setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        if (v.getId() == R.id.home_start) {
            this.startActivity(new Intent(this, DashBoardActivity.class));
        } else if (v.getId() == R.id.home_settings) {
            this.startActivity(new Intent(this, PreferencesActivity.class));
        }
    }
}
