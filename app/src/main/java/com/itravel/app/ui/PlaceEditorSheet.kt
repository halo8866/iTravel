package com.itravel.app.ui

import android.app.DatePickerDialog
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.itravel.app.data.PhotoStorage
import com.itravel.app.data.PlaceWithPhotos
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.util.GeoPoint
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceEditorSheet(
    existing: PlaceWithPhotos?,
    initialPoint: GeoPoint?,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        placeName: String?,
        point: GeoPoint,
        notes: String?,
        visitDate: Long,
        photoPaths: List<String>
    ) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val photos = remember {
        mutableStateListOf<String>().apply {
            existing?.photos?.map { it.path }?.let { addAll(it) }
        }
    }
    var title by remember { mutableStateOf(existing?.place?.title.orEmpty()) }
    var placeName by remember { mutableStateOf(existing?.place?.placeName.orEmpty()) }
    var notes by remember { mutableStateOf(existing?.place?.notes.orEmpty()) }
    var visitDate by remember {
        mutableStateOf(existing?.place?.visitDate ?: System.currentTimeMillis())
    }
    var point by remember {
        mutableStateOf(
            initialPoint ?: existing?.place?.let {
                GeoPoint(it.latitude, it.longitude)
            }
        )
    }
    var pendingCameraPath by remember { mutableStateOf<String?>(null) }

    val galleryLauncher = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val path = withContext(Dispatchers.IO) { PhotoStorage.copyUri(context, uri) }
                if (path != null) photos.add(path)
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.TakePicture()
    ) { success ->
        val path = pendingCameraPath
        if (success && path != null) {
            photos.add(path)
        } else if (path != null) {
            PhotoStorage.delete(path)
        }
        pendingCameraPath = null
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = if (existing == null) "记录新地方" else "编辑地点",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            SpacerHeight(16.dp)

            // Photos
            Text(
                text = "照片（将作为地图缩略图）",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SpacerHeight(8.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                photos.forEach { path ->
                    Box(modifier = Modifier.size(96.dp)) {
                        AsyncImage(
                            model = File(path),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(12.dp))
                        )
                        Surface(
                            onClick = { photos.remove(path) },
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.9f),
                            shape = RoundedCornerShape(50),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(24.dp)
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "删除照片",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.padding(3.dp)
                            )
                        }
                    }
                }
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    AssistChip(
                        onClick = {
                            galleryLauncher.launch(
                                androidx.activity.result.PickVisualMediaRequest(
                                    androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                        },
                        label = { Text("相册") }
                    )
                    AssistChip(
                        onClick = {
                            val file = PhotoStorage.newImageFile(context)
                            pendingCameraPath = file.absolutePath
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                file
                            )
                            cameraLauncher.launch(uri)
                        },
                        leadingIcon = {
                            Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                        },
                        label = { Text("拍照") }
                    )
                }
            }

            SpacerHeight(16.dp)

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("标题 *") },
                placeholder = { Text("例如：西湖之行") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            SpacerHeight(10.dp)

            OutlinedTextField(
                value = placeName,
                onValueChange = { placeName = it },
                label = { Text("地点名称") },
                placeholder = { Text("例如：杭州市西湖风景区") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            SpacerHeight(10.dp)

            OutlinedTextField(
                value = notes,
                onValueChange = { notes = it },
                label = { Text("游记 / 备注") },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 90.dp)
            )

            SpacerHeight(16.dp)

            // Visit date
            Surface(
                onClick = {
                    val cal = Calendar.getInstance().apply { timeInMillis = visitDate }
                    DatePickerDialog(
                        context,
                        { _, year, month, day ->
                            cal.set(year, month, day)
                            visitDate = cal.timeInMillis
                        },
                        cal.get(Calendar.YEAR),
                        cal.get(Calendar.MONTH),
                        cal.get(Calendar.DAY_OF_MONTH)
                    ).show()
                },
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        Icons.Filled.CalendarToday,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "到访日期：" + SimpleDateFormat(
                            "yyyy年MM月dd日",
                            Locale.getDefault()
                        ).format(java.util.Date(visitDate)),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            SpacerHeight(10.dp)

            // Coordinates
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(Modifier.padding(14.dp)) {
                    val p = point
                    if (p != null) {
                        Text(
                            text = "位置坐标：" + String.format(
                                Locale.US,
                                "%.5f, %.5f",
                                p.latitude,
                                p.longitude
                            ),
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "来自" + (if (initialPoint != null || existing != null) "地图选点" else "GPS 定位"),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "尚未选择位置，请返回地图长按选点",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    SpacerHeight(8.dp)
                    OutlinedButton(onClick = {
                        val cached = lastKnownGeo(context)
                        if (cached != null) point = cached
                    }) {
                        Icon(Icons.Filled.MyLocation, contentDescription = null)
                        Text("  使用当前定位")
                    }
                }
            }

            SpacerHeight(20.dp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f)
                ) { Text("取消") }
                Button(
                    onClick = {
                        val p = point ?: return@Button
                        onSave(
                            title.trim(),
                            placeName.trim(),
                            p,
                            notes.trim(),
                            visitDate,
                            photos.toList()
                        )
                    },
                    enabled = title.isNotBlank() && point != null,
                    modifier = Modifier.weight(1f)
                ) { Text("保存") }
            }
        }
    }
}

@Composable
private fun SpacerHeight(dp: androidx.compose.ui.unit.Dp) {
    androidx.compose.foundation.layout.Spacer(Modifier.height(dp))
}

private fun lastKnownGeo(context: android.content.Context): GeoPoint? {
    if (androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) != android.content.pm.PackageManager.PERMISSION_GRANTED
    ) return null
    val lm = context.getSystemService(android.content.Context.LOCATION_SERVICE) as android.location.LocationManager
    for (provider in listOf(
        android.location.LocationManager.GPS_PROVIDER,
        android.location.LocationManager.NETWORK_PROVIDER
    )) {
        runCatching {
            lm.getLastKnownLocation(provider)?.let {
                return GeoPoint(it.latitude, it.longitude)
            }
        }
    }
    return null
}
