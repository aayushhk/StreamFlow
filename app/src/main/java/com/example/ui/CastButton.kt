package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory
import androidx.appcompat.view.ContextThemeWrapper

@Composable
fun CastButton(modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val themeWrapper = ContextThemeWrapper(ctx, androidx.appcompat.R.style.Theme_AppCompat)
            MediaRouteButton(themeWrapper).apply {
                CastButtonFactory.setUpMediaRouteButton(themeWrapper, this)
            }
        }
    )
}
