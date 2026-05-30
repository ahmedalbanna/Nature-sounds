package com.example

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import com.example.data.AppDatabase
import com.example.data.AppRepository
import com.example.ui.AmbientSoundsDashboard
import com.example.ui.AmbientSoundsViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Ask for Notification Permissions on API 33+ to enable foreground services
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = android.Manifest.permission.POST_NOTIFICATIONS
            if (checkSelfPermission(permission) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(permission), 101)
            }
        }
        
        // Manual DI Instantiation of Database and Repository as recommended in Kotlin constraints
        val database = AppDatabase.getDatabase(this)
        val repository = AppRepository(database.appDao())
        
        // Obtain ViewModel with factory
        val viewModelFactory = AmbientSoundsViewModel.Factory(application, repository)
        val viewModel = androidx.lifecycle.ViewModelProvider(this, viewModelFactory)[AmbientSoundsViewModel::class.java]

        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    AmbientSoundsDashboard(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}
