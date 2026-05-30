package com.example.projectwatchapp.ui.launch

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.example.projectwatchapp.R
import com.example.projectwatchapp.ui.auth.LoginActivity
import com.example.projectwatchapp.ui.auth.RegisterActivity

/**
 * First impression screen with app branding and clear auth paths.
 *
 * UI sync note:
 * - Visual style and launch UX are adapted from Khensani's reference designs.
 * - Navigation targets stay aligned with this project flow (RegisterActivity/LoginActivity),
 *   so the existing ViewModel + Room business logic remains unchanged.
 */
class LaunchActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_launch)

        findViewById<Button>(R.id.btn_get_started).setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        findViewById<TextView>(R.id.tv_sign_in).setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
        }
    }
}
