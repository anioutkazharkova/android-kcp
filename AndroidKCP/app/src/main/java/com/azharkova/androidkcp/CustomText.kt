package com.azharkova.androidkcp

import android.app.Application
import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.constraintlayout.widget.ConstraintLayout
import com.azharkova.androidkcp.databinding.ItemTextLayoutBinding
import com.azharkova.annotations.BindView
import com.azharkova.annotations.ToComposable

@ToComposable
class CustomText(private  var context: Context, set: AttributeSet? = null) : ConstraintLayout(context,set) {
    var binding = ItemTextLayoutBinding.inflate(LayoutInflater.from(context))

    @BindView(R.id.text)
    var text: TextView? = null


    init {
        addView(binding.root)
        text?.text = "Custom view"
    }
}

