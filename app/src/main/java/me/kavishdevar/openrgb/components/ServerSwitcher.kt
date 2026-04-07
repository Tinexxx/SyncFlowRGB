package me.kavishdevar.openrgb.components

import android.content.SharedPreferences
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import me.kavishdevar.openrgb.components.ModernServerCard
import me.kavishdevar.openrgb.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSwitcher(
    currentServer: String,
    availableServers: List<String>,
    sharedPref: SharedPreferences,
    onDismiss: () -> Unit,
    onSwitch: (String) -> Unit,
    onRefresh: () -> Unit, // Add this parameter
    isRefreshing: Boolean,
    onAddManual: () -> Unit // ✅ Add this

) {
    val sheetState = rememberModalBottomSheetState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Black.copy(alpha = 0.9f),
        dragHandle = null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Refresh button in top-right corner
            if(!isRefreshing){ IconButton(
                onClick = onRefresh,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.refresh),
                    contentDescription = "Refresh servers",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }}


            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    "Available Servers",
                    style = MaterialTheme.typography.titleLarge.copy(color = Color.White),
                    modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)
                )

                if (isRefreshing) {
                    // Show Lottie animation while refreshing
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val composition by rememberLottieComposition(
                            LottieCompositionSpec.RawRes(R.raw.kudygdpu2j) // Convert your GIF to Lottie JSON
                        )
                        val progress by animateLottieCompositionAsState(
                            composition = composition,
                            iterations = LottieConstants.IterateForever
                        )

                        LottieAnimation(
                            composition = composition,
                            progress = { progress },
                            modifier = Modifier.size(200.dp)
                        )
                    }
                } else {
                    // Current server card
                    ModernServerCard(
                        server = currentServer,
                        isCurrent = true,
                        onClick = {},
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    // Available servers
                    availableServers.filter { it != currentServer }.forEach { server ->
                        ModernServerCard(
                            server = server,
                            isCurrent = false,
                            onClick = {
                                onSwitch(server)
                                onDismiss()
                            },
                            modifier = Modifier.padding(vertical = 1.dp)
                        )
                    }

                    IconButton(
                        onClick = onAddManual,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .padding(top = 8.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.add_server),
                            contentDescription = "Add Server",
                            modifier = Modifier.size(48.dp)
                        )
                    }


                }
            }
        }
    }
}