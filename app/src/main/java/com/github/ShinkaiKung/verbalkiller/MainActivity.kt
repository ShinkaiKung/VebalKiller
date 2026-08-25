package com.github.ShinkaiKung.verbalkiller

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.rememberNavController
import com.github.ShinkaiKung.verbalkiller.info.ProgressViewModel
import com.github.ShinkaiKung.verbalkiller.practice.PracticeViewModel
import com.github.ShinkaiKung.verbalkiller.ui.theme.VerbalKillerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val repository = (application as VerbalKillerApplication).repository
        val practiceViewModel = ViewModelProvider(
            this,
            PracticeViewModel.Factory(repository),
        )[PracticeViewModel::class.java]
        val progressViewModel = ViewModelProvider(
            this,
            ProgressViewModel.Factory(repository),
        )[ProgressViewModel::class.java]

        setContent {
            VerbalKillerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    val navController = rememberNavController()
                    NavLayout(navController, practiceViewModel, progressViewModel)
                }
            }
        }
    }
}
