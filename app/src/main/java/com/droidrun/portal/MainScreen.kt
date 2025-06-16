package com.droidrun.portal

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun MainScreen(viewModel: MainViewModel, onOpenAccessibilitySettings: () -> Unit) {
    val status = viewModel.status
    val accessibilityStatus = viewModel.accessibilityStatus
    val accessibilityIndicatorColor = viewModel.accessibilityIndicatorColor
    val response = viewModel.response
    val overlayVisible = viewModel.overlayVisible
    val offset = viewModel.offset
    val onFetch = viewModel::onFetchButtonClicked
    val onRetrigger = viewModel::onRetriggerButtonClicked
    val onToggleOverlay = viewModel::onOverlaySwitchToggled
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        // Header mit AppBar und Status
        TopAppBar(
            title = {
                Text(
                    stringResource(id = R.string.app_name),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
            },
            colors = TopAppBarColors(
                titleContentColor = Color.Transparent,
                containerColor = Color.Transparent,
                actionIconContentColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
                navigationIconContentColor = Color.Transparent
            ),
            navigationIcon = {
                Icon(
                    painter = painterResource(id = R.mipmap.ic_launcher),
                    contentDescription = null,
                    modifier = Modifier
                        .size(50.dp)
                        .padding(end = 16.dp),
                    tint = Color.Unspecified
                )
            },
        )
        Column {
            Card(
                modifier = Modifier
                    .fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = droidrunPrimary)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = android.R.drawable.ic_dialog_info),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = Color.White
                    )
                    Text(
                        text = status,
                        color = Color.White,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
            }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = droidrunSecondary)
            ) {
                Row(
                    modifier = Modifier
                        .padding(12.dp)
                        .clickable {
                            viewModel.updateAccessibilityStatusIndicator()
                            onOpenAccessibilitySettings()
                        },
                    verticalAlignment = Alignment.CenterVertically,

                    ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically

                    ) {
                        Icon(
                            painter = painterResource(id = android.R.drawable.ic_menu_manage),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = Color.White
                        )
                        Text(
                            text = "Accessibility Service",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                    Row(
                        modifier = Modifier
                            .background(color = Color.Black, shape = RoundedCornerShape(8.dp))
                            .border(border = BorderStroke(width = 1.dp, color = droidrunTextSecondary), shape = RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(accessibilityIndicatorColor)
                        )
                        Text(
                            text = accessibilityStatus,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(20.dp))

        // Controls Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = droidrunSurface)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Icon(
                        painter = painterResource(id = android.R.drawable.ic_menu_manage),
                        contentDescription = null,
                        modifier = Modifier.size(32.dp).padding(end = 12.dp),
                        tint = droidrunPrimary
                    )
                    Text(
                        text = "Controls",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                }
                // Button Row
                Row(
                    modifier = Modifier.padding(bottom = 20.dp)
                ) {
                    Button(
                        onClick = onFetch,
                        modifier = Modifier.weight(1f).height(56.dp).padding(end = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = droidrunPrimary)
                    ) {
                        Icon(
                            painter = painterResource(id = android.R.drawable.ic_menu_search),
                            contentDescription = null,
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Fetch Element Data", color = Color.White)
                    }
                    Button(
                        onClick = onRetrigger,
                        modifier = Modifier.weight(1f).height(56.dp).padding(start = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = droidrunPrimary)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text("Retrigger Elements", color = Color.White, fontSize = 13.sp)
                    }
                }
                // Switch
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2A332F)),
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Show Element Rectangles",
                            fontSize = 16.sp,
                            color = droidrunTextSecondary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = overlayVisible,
                            onCheckedChange = onToggleOverlay,
                            modifier = Modifier.weight(1f),
                            enabled = true
                        )
                    }
                }
                // Offset Input
                Text(
                    text = "Position Offset",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                OutlinedTextField(
                    value = offset.toString(),
                    onValueChange = { viewModel.onOffsetChanged(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    label = {
                        Text(text = "Enter offset value",
                            color = droidrunPrimary
                        ) },

                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = droidrunTextPrimary,
                        unfocusedTextColor = droidrunTextSecondary
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                // Offset Slider Card
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2A332F)),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("-256", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = droidrunTextSecondary)
                        Slider(
                            value = offset.toFloat(),
                            onValueChange = { viewModel.onOffsetChanged(it.toInt().toString()) },
                            valueRange = -256f..256f,
                            modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
                        )
                        Text("+256", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = droidrunTextSecondary)
                    }
                }
            }
        }
        // Results Card
        Card(
            modifier = Modifier
                .fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = droidrunSurface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(20.dp)
                ) {
                    Icon(
                        painter = painterResource(id = android.R.drawable.ic_menu_info_details),
                        contentDescription = null,
                        modifier = Modifier.size(32.dp).padding(end = 12.dp),
                        tint = droidrunPrimary
                    )
                    Text(
                        text = "Element Data",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.weight(1f).padding(16.dp)
                    )
                }
                Divider(color = droidrunDivider, thickness = 1.dp)
                Card(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2A332F))
                ) {
                    Box(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = response,
                            fontSize = 14.sp,
                            color = droidrunTextSecondary,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}

val droidrunPrimary = Color(0xFF00BFA5)
val droidrunSecondary = Color(0xFF00BFA5)
val droidrunBackground = Color(0xFF1A1E1C)
val droidrunSurface = Color(0xFF252A27)
val droidrunCard = Color(0xFF2A332F)
val droidrunTextPrimary = Color(0xFFFFFFFF)
val droidrunTextSecondary = Color(0xFFB7F0DA)
val droidrunDivider = Color(0xFF3C5A4F)



@Composable
@Preview(showBackground = true)
@OptIn(ExperimentalMaterial3Api::class)
fun MainScreenPreview() {
    val status = "Visualization overlays disabled"
    val accessibilityStatus = "DISABLED"
    val accessibilityIndicatorColor = Color.Red
    val response = ""
    val overlayVisible = true
    val offset = "-128"
    val onFetch = {}
    val onRetrigger = {}
    val onToggleOverlay = { _: Boolean -> }
    val onOffsetChanged = { _: String -> }
    val onOpenAccessibilitySettings = {}
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        // Header mit AppBar und Status
        TopAppBar(
            title = {
                Text(
                    stringResource(id = R.string.app_name),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
            },
            colors = TopAppBarColors(
                titleContentColor = Color.Transparent,
                containerColor = Color.Transparent,
                actionIconContentColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
                navigationIconContentColor = Color.Transparent
            ),
            navigationIcon = {
                Icon(
                    painter = painterResource(id = R.mipmap.ic_launcher),
                    contentDescription = null,
                    modifier = Modifier
                        .size(50.dp)
                        .padding(end = 16.dp),
                    tint = Color.Unspecified
                )
            },
        )
        Column {
            Card(
                modifier = Modifier
                    .fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = droidrunPrimary)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painter = painterResource(id = android.R.drawable.ic_dialog_info),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = Color.White
                    )
                    Text(
                        text = status,
                        color = Color.White,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(start = 16.dp)
                    )
                }
            }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = droidrunSecondary)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp)
                        .clickable {
                            onToggleOverlay(true)
                        },
                    verticalAlignment = Alignment.CenterVertically,

                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically

                    ) {
                        Icon(
                            painter = painterResource(id = android.R.drawable.ic_menu_manage),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = Color.White
                        )
                        Text(
                            text = "Accessibility Service",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                    Row(
                        modifier = Modifier
                            .background(color = Color.Black, shape = RoundedCornerShape(8.dp))
                            .border(border = BorderStroke(width = 1.dp, color = droidrunTextSecondary), shape = RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(accessibilityIndicatorColor)
                        )
                        Text(
                            text = accessibilityStatus,
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(20.dp))

        // Controls Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = droidrunSurface)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Icon(
                        painter = painterResource(id = android.R.drawable.ic_menu_manage),
                        contentDescription = null,
                        modifier = Modifier.size(32.dp).padding(end = 12.dp),
                        tint = droidrunPrimary
                    )
                    Text(
                        text = "Controls",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                }
                // Button Row
                Row(
                    modifier = Modifier.padding(bottom = 20.dp)
                ) {
                    Button(
                        onClick = onFetch,
                        modifier = Modifier.weight(1f).height(56.dp).padding(end = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = droidrunPrimary)
                    ) {
                        Icon(
                            painter = painterResource(id = android.R.drawable.ic_menu_search),
                            contentDescription = null,
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Fetch Element Data", color = Color.White)
                    }
                    Button(
                        onClick = onRetrigger,
                        modifier = Modifier.weight(1f).height(56.dp).padding(start = 8.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = droidrunPrimary)
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text("Retrigger Elements", color = Color.White, fontSize = 13.sp)
                    }
                }
                // Switch
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2A332F)),
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Show Element Rectangles",
                            fontSize = 16.sp,
                            color = droidrunTextSecondary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = overlayVisible,
                            onCheckedChange = onToggleOverlay,
                            modifier = Modifier.weight(1f),
                            enabled = true
                        )
                    }
                }
                // Offset Input
                Text(
                    text = "Position Offset",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                OutlinedTextField(
                    value = offset.toString(),
                    onValueChange = { onOffsetChanged(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    label = {
                        Text(text = "Enter offset value",
                            color = droidrunPrimary
                        ) },

                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = droidrunTextPrimary,
                        unfocusedTextColor = droidrunTextSecondary
                    ),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                // Offset Slider Card
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2A332F)),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("-256", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = droidrunTextSecondary)
                        Slider(
                            value = offset.toFloat(),
                            onValueChange = { onOffsetChanged(it.toInt().toString()) },
                            valueRange = -256f..256f,
                            modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
                        )
                        Text("+256", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = droidrunTextSecondary)
                    }
                }
            }
        }
        // Results Card
        Card(
            modifier = Modifier
                .fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = droidrunSurface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(20.dp)
                ) {
                    Icon(
                        painter = painterResource(id = android.R.drawable.ic_menu_info_details),
                        contentDescription = null,
                        modifier = Modifier.size(32.dp).padding(end = 12.dp),
                        tint = droidrunPrimary
                    )
                    Text(
                        text = "Element Data",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.weight(1f).padding(16.dp)
                    )
                }
                Divider(color = droidrunDivider, thickness = 1.dp)
                Card(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2A332F))
                ) {
                    Box(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = response,
                            fontSize = 14.sp,
                            color = droidrunTextSecondary,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}