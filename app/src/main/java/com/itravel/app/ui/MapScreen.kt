package com.itravel.app.ui

import android.Manifest
import android.app.DatePickerDialog
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.itravel.app.AppViewModel
import com.itravel.app.data.BackupManager
import com.itravel.app.data.PhotoStorage
import com.itravel.app.data.PlaceWithPhotos
import com.itravel.app.map.MapController
import com.itravel.app.map.reverseGeocodeChinese
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.util.GeoPoint
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun TravelMapApp(vm: AppViewModel, onOpenTimeline: () -> Unit = {}) {
    val context = LocalContext.current
    val places by vm.places.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    // 导入备份的 Launcher
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        if (uri != null) {
            scope.launch {
                Toast.makeText(context, "正在导入备份…", Toast.LENGTH_SHORT).show()
                val success = BackupManager.import(context, uri)
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        if (success) "导入成功，已还原全部数据" else "导入失败，请检查文件格式",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    // selectedId = null 且 draftPoint != null  -> 新增草稿（来自地图长按）
    // selectedId != null                         -> 编辑已有地点
    // selectedId = null 且 draftPoint == null    -> 未选择
    var selectedId by remember { mutableStateOf<Long?>(null) }
    var draftPoint by remember { mutableStateOf<GeoPoint?>(null) }
    var panelExpanded by remember { mutableStateOf(true) }

    val mapController = remember {
        MapController(
            context = context,
            onLongPress = { point ->
                selectedId = null
                draftPoint = point
                panelExpanded = true
            },
            onPlaceClick = { id ->
                selectedId = id
                draftPoint = null
                panelExpanded = true
            },
            onMapTouch = {
                // 仅在非编辑状态时触摸地图才收起面板，避免编辑内容被意外收起
                if (selectedId == null && draftPoint == null) panelExpanded = false
            }
        )
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapController.onResume()
                Lifecycle.Event.ON_PAUSE -> mapController.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapController.onDetach()
        }
    }

    LaunchedEffect(places) {
        mapController.renderPlaces(places)
    }

    // 草稿点同步到地图
    LaunchedEffect(draftPoint) {
        if (draftPoint != null) {
            mapController.showDraft(draftPoint!!)
        } else {
            mapController.clearDraft()
        }
    }

    // 选中已有地点时地图居中
    LaunchedEffect(selectedId) {
        val id = selectedId ?: return@LaunchedEffect
        val item = places.firstOrNull { it.place.id == id } ?: return@LaunchedEffect
        mapController.moveTo(GeoPoint(item.place.latitude, item.place.longitude), 14.0)
    }

    val locationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            mapController.enableLocation()
            openAddAtLocation(context, mapController) { point ->
                selectedId = null
                draftPoint = point
            }
        } else {
            Toast.makeText(context, "未授予定位权限，可长按地图选点", Toast.LENGTH_LONG).show()
        }
    }

    Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {

        // ========== 上半部分：地图 ==========
        val isEditing = selectedId != null || draftPoint != null
        val mapWeight by animateFloatAsState(
            targetValue = when {
                !panelExpanded -> 0.92f   // 面板收起：地图几乎全屏
                isEditing -> 0.25f        // 编辑中：地图缩小，编辑器占大头
                else -> 0.55f             // 列表：地图略大
            },
            animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            label = "mapWeight"
        )
        Box(modifier = Modifier.weight(mapWeight)) {
            AndroidView(
                factory = { mapController.mapView },
                modifier = Modifier.fillMaxSize()
            )

            // 地图顶部工具栏
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(10.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                shadowElevation = 3.dp
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 12.dp, end = 4.dp)
                ) {
                    Text(
                        text = "iTravel 足迹",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "${places.size} 处",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IconButton(onClick = { mapController.fitAll(places) }) {
                        Icon(Icons.Filled.Map, contentDescription = "显示全部", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = onOpenTimeline) {
                        Icon(Icons.Filled.Timeline, contentDescription = "时间线", tint = MaterialTheme.colorScheme.primary)
                    }
                    // 备份/导入入口
                    var showBackupMenu by remember { mutableStateOf(false) }
                    IconButton(onClick = { showBackupMenu = true }) {
                        Icon(Icons.Filled.Backup, contentDescription = "备份", tint = MaterialTheme.colorScheme.primary)
                    }
                    if (showBackupMenu) {
                        AlertDialog(
                            onDismissRequest = { showBackupMenu = false },
                            title = { Text("备份与导入") },
                            text = {
                                Column {
                                    TextButton(
                            onClick = {
                                showBackupMenu = false
                                Toast.makeText(context, "正在导出备份…", Toast.LENGTH_SHORT).show()
                                scope.launch {
                                    val file = BackupManager.export(context, places)
                                    withContext(Dispatchers.Main) {
                                        if (file != null) {
                                            BackupManager.shareFile(context, file)
                                            Toast.makeText(context, "备份已导出", Toast.LENGTH_SHORT).show()
                                        } else {
                                            Toast.makeText(context, "导出失败", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("导出备份（分享/保存 zip）") }
                                    TextButton(
                                        onClick = {
                                            showBackupMenu = false
                                            Toast.makeText(context, "选择之前导出的备份文件", Toast.LENGTH_LONG).show()
                                            val intent = android.content.Intent(android.content.Intent.ACTION_GET_CONTENT).apply {
                                                type = "application/zip"
                                                addCategory(android.content.Intent.CATEGORY_OPENABLE)
                                            }
                                            importLauncher.launch(intent)
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("导入备份（替换现有数据）") }
                                }
                            },
                            confirmButton = {},
                            dismissButton = {
                                TextButton(onClick = { showBackupMenu = false }) { Text("取消") }
                            }
                        )
                    }
                }
            }

            // 右下角浮动按钮：记录当前位置
            androidx.compose.material3.FloatingActionButton(
                onClick = {
                    if (mapController.hasLocationPermission) {
                        mapController.enableLocation()
                        openAddAtLocation(context, mapController) { point ->
                            selectedId = null
                            draftPoint = point
                        }
                    } else {
                        locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = 12.dp)
            ) {
                Icon(Icons.Filled.MyLocation, contentDescription = "记录当前位置")
            }
        }

        // ========== 下半部分：记录面板 ==========
        RecordPanel(
            modifier = Modifier.weight(1f - mapWeight),
            expanded = panelExpanded,
            onToggleExpanded = { panelExpanded = !panelExpanded },
            places = places,
            selectedId = selectedId,
            draftPoint = draftPoint,
            onSelect = { id ->
                selectedId = id
                draftPoint = null
            },
            onNew = {
                panelExpanded = true
                selectedId = null
                if (mapController.hasLocationPermission) {
                    val cached = mapController.lastKnownLocation()
                    if (cached != null) {
                        draftPoint = cached
                        mapController.moveTo(cached, 12.0)
                    } else {
                        // 无缓存定位：先用默认点进入编辑，首个定位点到达后自动更新
                        draftPoint = GeoPoint(35.0, 106.0)
                        Toast.makeText(context, "正在获取当前定位，稍后自动更新", Toast.LENGTH_SHORT).show()
                        mapController.enableLocation()
                        mapController.onFirstFix { point ->
                            if (draftPoint != null) draftPoint = point
                        }
                    }
                } else {
                    locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                }
            },
            onClearSelection = {
                selectedId = null
                draftPoint = null
            },
            onSave = { existing, title, placeName, point, notes, visitDate, sortDate, photoPaths ->
                vm.savePlace(
                    existing = existing?.place,
                    title = title,
                    placeName = placeName,
                    latitude = point.latitude,
                    longitude = point.longitude,
                    notes = notes,
                    visitDate = visitDate,
                    sortDate = sortDate,
                    photoPaths = photoPaths
                )
                selectedId = null
                draftPoint = null
            },
            onDelete = { item ->
                vm.deletePlace(item)
                selectedId = null
                draftPoint = null
            },
            onPickFromMap = {
                Toast.makeText(context, "请在上方地图长按选择位置", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordPanel(
    modifier: Modifier = Modifier,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    places: List<PlaceWithPhotos>,
    selectedId: Long?,
    draftPoint: GeoPoint?,
    onSelect: (Long) -> Unit,
    onNew: () -> Unit,
    onClearSelection: () -> Unit,
    onSave: (PlaceWithPhotos?, String, String?, GeoPoint, String?, Long, Long, List<String>) -> Unit,
    onDelete: (PlaceWithPhotos) -> Unit,
    onPickFromMap: () -> Unit
) {
    val selectedItem = selectedId?.let { id -> places.firstOrNull { it.place.id == id } }
    val isEditing = selectedItem != null || draftPoint != null

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 面板标题栏（点击可展开/收起）
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpanded),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 2.dp
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    text = if (isEditing) "编辑地点" else "我的足迹",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                if (expanded) {
                    if (isEditing) {
                        TextButton(onClick = onClearSelection) { Text("返回列表") }
                    } else {
                        TextButton(onClick = onNew) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text(" 新增")
                        }
                    }
                } else {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "展开",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        if (expanded) {
            if (isEditing) {
                PlaceEditorInline(
                    existing = selectedItem,
                    initialPoint = draftPoint,
                    onSave = onSave,
                    onDelete = { item ->
                        onDelete(item)
                    },
                    onPickFromMap = onPickFromMap
                )
            } else {
                PlaceList(places = places, onSelect = onSelect)
            }
        }
    }
}

@Composable
private fun PlaceList(
    places: List<PlaceWithPhotos>,
    onSelect: (Long) -> Unit
) {
    if (places.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Filled.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "还没有记录\n长按地图或点击新增开始记录",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)
    ) {
        items(places, key = { it.place.id }) { item ->
            PlaceListItem(item = item, onClick = { onSelect(item.place.id) })
        }
    }
}

@Composable
private fun PlaceListItem(
    item: PlaceWithPhotos,
    onClick: () -> Unit
) {
    val place = item.place
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (!place.coverPath.isNullOrBlank()) {
                AsyncImage(
                    model = File(place.coverPath),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(10.dp))
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = place.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                if (!place.placeName.isNullOrBlank()) {
                    Text(
                        text = place.placeName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                Text(
                    text = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(place.visitDate)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

@Composable
private fun PlaceEditorInline(
    existing: PlaceWithPhotos?,
    initialPoint: GeoPoint?,
    onSave: (PlaceWithPhotos?, String, String?, GeoPoint, String?, Long, Long, List<String>) -> Unit,
    onDelete: (PlaceWithPhotos) -> Unit,
    onPickFromMap: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val photos = remember(existing?.place?.id) {
        mutableStateListOf<String>().apply {
            existing?.photos?.map { it.path }?.let { addAll(it) }
        }
    }
    var title by remember(existing?.place?.id) { mutableStateOf(existing?.place?.title.orEmpty()) }
    var placeName by remember(existing?.place?.id) { mutableStateOf(existing?.place?.placeName.orEmpty()) }
    var notes by remember(existing?.place?.id) { mutableStateOf(existing?.place?.notes.orEmpty()) }
    var visitDate by remember(existing?.place?.id) {
        mutableStateOf(existing?.place?.visitDate ?: System.currentTimeMillis())
    }
    var sortDate by remember(existing?.place?.id) {
        mutableStateOf(existing?.place?.sortDate ?: System.currentTimeMillis())
    }
    var point by remember(existing?.place?.id, initialPoint) {
        mutableStateOf(
            initialPoint ?: existing?.place?.let { GeoPoint(it.latitude, it.longitude) }
        )
    }
    var pendingCameraPath by remember { mutableStateOf<String?>(null) }
    var showPhotoPicker by remember { mutableStateOf(false) }

    // 自动填充中文地名：选点后逆地理编码；用户手动改过则不再覆盖
    var placeNameManuallyEdited by remember { mutableStateOf(false) }
    var autoPlaceName by remember(existing?.place?.id) {
        mutableStateOf(existing?.place?.placeName.orEmpty())
    }
    var geocoding by remember { mutableStateOf(false) }
    LaunchedEffect(point) {
        val p = point ?: return@LaunchedEffect
        geocoding = true
        val name = reverseGeocodeChinese(context, p.latitude, p.longitude)
        geocoding = false
        if (name != null) {
            val canOverwrite = !placeNameManuallyEdited &&
                (placeName.isBlank() || placeName == autoPlaceName)
            if (canOverwrite) placeName = name
            autoPlaceName = name
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val path = withContext(Dispatchers.IO) { PhotoStorage.copyUri(context, uri) }
                if (path != null) photos.add(path)
            }
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        val path = pendingCameraPath
        if (success && path != null) photos.add(path)
        else if (path != null) PhotoStorage.delete(path)
        pendingCameraPath = null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 坐标条（来自地图选点）
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Filled.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                val p = point
                Column(Modifier.weight(1f)) {
                    if (p != null) {
                        if (placeName.isNotBlank()) {
                            Text(
                                text = placeName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            text = when {
                                geocoding && placeName.isBlank() -> "正在获取地名…"
                                else -> String.format(Locale.US, "%.5f, %.5f", p.latitude, p.longitude)
                            },
                            style = if (placeName.isNotBlank()) MaterialTheme.typography.labelSmall
                            else MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    } else {
                        Text(
                            text = "未选择位置",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                TextButton(onClick = onPickFromMap) { Text("在地图上选") }
            }
        }

        // 照片
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            photos.take(4).forEach { path ->
                Box(modifier = Modifier.size(72.dp)) {
                    AsyncImage(
                        model = File(path),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(8.dp))
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(20.dp)
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.error)
                            .clickable { photos.remove(path) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("×", color = Color.White, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TextButton(onClick = { showPhotoPicker = true }) {
                    Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(" 添加照片")
                }
            }
            if (showPhotoPicker) {
                AlertDialog(
                    onDismissRequest = { showPhotoPicker = false },
                    title = { Text("添加照片") },
                    text = { Text("选择照片来源") },
                    confirmButton = {
                        TextButton(onClick = {
                            showPhotoPicker = false
                            galleryLauncher.launch(
                                androidx.activity.result.PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        }) {
                            Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("  从相册选择")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = {
                            showPhotoPicker = false
                            val file = PhotoStorage.newImageFile(context)
                            pendingCameraPath = file.absolutePath
                            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            cameraLauncher.launch(uri)
                        }) {
                            Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text("  拍照")
                        }
                    }
                )
            }
        }

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("标题 *") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = placeName,
            onValueChange = {
                placeNameManuallyEdited = true
                placeName = it
            },
            label = { Text("地点名称") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        // 日期
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
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Filled.CalendarToday, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(
                    text = SimpleDateFormat("yyyy年MM月dd日", Locale.getDefault()).format(Date(visitDate)),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("游记 / 备注") },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 70.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (existing != null) {
                OutlinedButton(
                    onClick = { onDelete(existing) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(" 删除")
                }
            }
            Button(
                onClick = {
                    val p = point ?: return@Button
                    onSave(existing, title.trim(), placeName.trim(), p, notes.trim(), visitDate, sortDate, photos.toList())
                },
                enabled = title.isNotBlank() && point != null,
                modifier = Modifier.weight(1f)
            ) { Text("保存") }
        }
    }
}

private fun openAddAtLocation(
    context: android.content.Context,
    mapController: MapController,
    onReady: (GeoPoint) -> Unit
) {
    val cached = mapController.lastKnownLocation()
    if (cached != null) {
        onReady(cached)
        return
    }
    Toast.makeText(context, "正在获取定位，请稍候…", Toast.LENGTH_SHORT).show()
    mapController.onFirstFix { point -> onReady(point) }
}
