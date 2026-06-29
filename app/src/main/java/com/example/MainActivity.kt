package com.example

import android.content.res.Configuration
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.room.Room
import com.example.data.db.StreamDatabase
import com.example.data.repository.StreamRepository
import com.example.ui.StreamViewModel
import com.example.ui.StreamsListScreen
import com.example.ui.VideoPlayerScreen
import com.example.ui.theme.MyApplicationTheme

@androidx.media3.common.util.UnstableApi
class MainActivity : AppCompatActivity() {

    private val isInPipModeState = mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize Room local DB
        val db = Room.databaseBuilder(
            applicationContext,
            StreamDatabase::class.java,
            "streamflow_db"
        ).fallbackToDestructiveMigration().build()

        // Create Repository & ViewModel
        val repository = StreamRepository(db.streamDao)
        val viewModel = StreamViewModel(repository)

        setContent {
            MyApplicationTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var selectedStreamUrl by remember { mutableStateOf<String?>(null) }
                    var selectedStreamTitle by remember { mutableStateOf<String?>(null) }

                    val isInPipMode by isInPipModeState

                    if (selectedStreamUrl != null && selectedStreamTitle != null) {
                        VideoPlayerScreen(
                            streamUrl = selectedStreamUrl!!,
                            streamTitle = selectedStreamTitle!!,
                            isInPipMode = isInPipMode,
                            onBack = {
                                selectedStreamUrl = null
                                selectedStreamTitle = null
                            }
                        )
                    } else {
                        StreamsListScreen(
                            viewModel = viewModel,
                            onStreamSelected = { url, title ->
                                selectedStreamUrl = url
                                selectedStreamTitle = title
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipModeState.value = isInPictureInPictureMode
    }
}
