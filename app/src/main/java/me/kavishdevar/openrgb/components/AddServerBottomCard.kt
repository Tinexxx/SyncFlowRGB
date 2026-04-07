package me.kavishdevar.openrgb.components

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddServerBottomCard(
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    var serverInput by remember { mutableStateOf("") }
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Black.copy(alpha = 0.9f),
        dragHandle = null
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                "Add New Server",
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = serverInput,
                onValueChange = { serverInput = it },
                label = { Text("IP Address", color = Color.White) },
                placeholder = { Text("e.g. 192.168.1.105", color = Color.LightGray) },
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.White,
                    unfocusedBorderColor = Color.Gray
                )
            )

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = {
                    val trimmed = serverInput.trim()
                    val ipRegex = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")

                    if (trimmed.isNotBlank() && ipRegex.matches(trimmed)) {
                        val fullAddress = "$trimmed:6742"
                        onAdd(fullAddress)
                    } else {
                        Toast.makeText(
                            context,
                            "Please enter a valid IP address (e.g. 192.168.1.105)",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                modifier = Modifier.align(Alignment.End),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6AB7F1)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Add", color = Color.White)
            }
        }
    }
}