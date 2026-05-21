package com.example

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import com.example.ui.TodoScreen
import com.example.ui.TodoViewModel

class MainActivity : FragmentActivity() {
    private lateinit var viewModel: TodoViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Create the view model holding application state
        viewModel = ViewModelProvider(this)[TodoViewModel::class.java]

        setContent {
            TodoScreen(viewModel = viewModel)
        }
    }
}
