package com.azharkova.androidkcp

import android.os.Bundle
import com.google.android.material.snackbar.Snackbar
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import androidx.activity.compose.setContent
import com.azharkova.androidkcp.databinding.ActivityMainBinding
import com.azharkova.annotations.BindView

class MainActivity : AppCompatActivity() {

    @BindView(R.id.text_test)
    var text: TextView? = null

    @BindView(R.id.custom_test)
    var customText: CustomText? = null

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        text?.text = "It works!"
    }
}