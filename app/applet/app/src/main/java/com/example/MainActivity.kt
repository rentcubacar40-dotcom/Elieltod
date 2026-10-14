package com.example

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cu.todus.download.data.AppPreferences
import cu.todus.download.data.DownloadItem
import cu.todus.download.data.DownloadRepository
import cu.todus.download.engine.DownloadEngine
import cu.todus.download.engine.TodusNetworkClient
import cu.todus.download.service.DownloadService
import cu.todus.download.ui.components.AddUrlDialog
import cu.todus.download.ui.screens.DownloadsScreen
import cu.todus.download.ui.screens.HistoryScreen
import cu.todus.download.ui.screens.SettingsScreen
import cu.todus.download.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

enum class AppTab(val title: String, val icon: ImageVector) {
    DOWNLOADS("Descargas", Icons.Default.CloudDownload),
    HISTORY("Historial", Icons.Default.Folder),
    SETTINGS("Ajustes", Icons.Default.Settings)
}

class MainActivity : ComponentActivity() {

    private lateinit var preferences: AppPreferences
    private lateinit var repository: DownloadRepository
    private lateinit var engine: DownloadEngine
    private lateinit var networkClient: TodusNetworkClient

    private var sharedUrlFromIntent by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        preferences = AppPreferences(this)
        repository = DownloadService.getRepository(this)
        engine = DownloadService.getEngine(this)
        networkClient = TodusNetworkClient(preferences)

        handleIncomingIntent(intent)

        setContent {
            TodusDownloadTheme {
                MainAppScreen()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!text.isNullOrBlank()) {
                val urlRegex = Regex("https?://[^\\s]+")
                val match = urlRegex.find(text)
                sharedUrlFromIntent = match?.value ?: text.trim()
            }
        }
    }

    private fun openFile(item: DownloadItem) {
        val file = File(item.destinationPath)
        if (!file.exists()) {
            Toast.makeText(this, "El archivo ya no existe en el almacenamiento", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val uri: Uri = FileProvider.getUriForFile(
                this,
                "$packageName.provider",
                file
            )

            val extension = file.extension.lowercase()
            val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"

            val openIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mimeType)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(Intent.createChooser(openIntent, "Abrir con"))
        } catch (e: Exception) {
            Toast.makeText(this, "No hay aplicación compatible para abrir este archivo", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareFile(item: DownloadItem) {
        val file = File(item.destinationPath)
        if (!file.exists()) {
            Toast.makeText(this, "El archivo no se encuentra disponible", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val uri: Uri = FileProvider.getUriForFile(
                this,
                "$packageName.provider",
                file
            )

            val extension = file.extension.lowercase()
            val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "*/*"

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, "Compartir archivo toDus"))
        } catch (e: Exception) {
            Toast.makeText(this, "Error al compartir archivo", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startDownloadItem(url: String, customName: String, threads: Int) {
        val id = UUID.randomUUID().toString()
        val defaultName = if (customName.isNotBlank()) {
            customName
        } else {
            url.substringBefore("?").substringAfterLast("/").ifBlank { "todus_file_${System.currentTimeMillis()}.bin" }
        }

        val item = DownloadItem(
            id = id,
            url = url,
            name = defaultName,
            threads = threads,
            status = DownloadItem.Status.QUEUED
        )

        repository.addItem(item)
        engine.startDownload(item)

        // Start Foreground Service
        val serviceIntent = Intent(this, DownloadService::class.java).apply {
            action = DownloadService.ACTION_START_DOWNLOAD
            putExtra(DownloadService.EXTRA_DOWNLOAD_ID, id)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }

    @Composable
    fun MainAppScreen() {
        var currentTab by remember { mutableStateOf(AppTab.DOWNLOADS) }
        var showAddDialog by remember { mutableStateOf(false) }
        var initialDialogUrl by remember { mutableStateOf("") }

        val downloads by repository.downloads.collectAsStateWithLifecycle()

        // Notification permission launcher for Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission()
            ) { isGranted ->
                // Handled gracefully
            }
            LaunchedEffect(Unit) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Handle shared intent
        LaunchedEffect(sharedUrlFromIntent) {
            sharedUrlFromIntent?.let {
                initialDialogUrl = it
                showAddDialog = true
                sharedUrlFromIntent = null
            }
        }

        val activeDownloadsCount = remember(downloads) {
            downloads.count { it.status == DownloadItem.Status.DOWNLOADING || it.status == DownloadItem.Status.CONNECTING }
        }

        val completedCount = remember(downloads) {
            downloads.count { it.status == DownloadItem.Status.COMPLETED }
        }

        Scaffold(
            containerColor = BgDark,
            bottomBar = {
                NavigationBar(
                    containerColor = SurfaceDark,
                    tonalElevation = 0.dp,
                    modifier = Modifier.border(
                        width = 1.dp,
                        color = SurfaceBorder,
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                    )
                ) {
                    AppTab.values().forEach { tab ->
                        val isSelected = currentTab == tab
                        NavigationBarItem(
                            selected = isSelected,
                            onClick = { currentTab = tab },
                            icon = {
                                BadgedBox(
                                    badge = {
                                        if (tab == AppTab.DOWNLOADS && activeDownloadsCount > 0) {
                                            Badge(
                                                containerColor = AccentCyan,
                                                contentColor = BgDark
                                            ) {
                                                Text(
                                                    activeDownloadsCount.toString(),
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 10.sp
                                                )
                                            }
                                        } else if (tab == AppTab.HISTORY && completedCount > 0) {
                                            Badge(
                                                containerColor = SurfaceCard,
                                                contentColor = TextSecondary
                                            ) {
                                                Text(
                                                    completedCount.toString(),
                                                    fontSize = 10.sp
                                                )
                                            }
                                        }
                                    }
                                ) {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = tab.title,
                                        tint = if (isSelected) AccentCyan else TextMuted
                                    )
                                }
                            },
                            label = {
                                Text(
                                    text = tab.title,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) AccentCyan else TextMuted
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                indicatorColor = SurfaceCard
                            ),
                            modifier = Modifier.testTag("nav_tab_${tab.name.lowercase()}")
                        )
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                when (currentTab) {
                    AppTab.DOWNLOADS -> {
                        DownloadsScreen(
                            downloads = downloads,
                            nationalIp = preferences.nationalIp,
                            onOpenAddDialog = {
                                initialDialogUrl = ""
                                showAddDialog = true
                            },
                            onPauseResume = { item ->
                                if (item.status == DownloadItem.Status.DOWNLOADING) {
                                    engine.pauseDownload(item.id)
                                } else {
                                    engine.startDownload(item)
                                    val serviceIntent = Intent(this@MainActivity, DownloadService::class.java).apply {
                                        action = DownloadService.ACTION_START_DOWNLOAD
                                        putExtra(DownloadService.EXTRA_DOWNLOAD_ID, item.id)
                                    }
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        startForegroundService(serviceIntent)
                                    } else {
                                        startService(serviceIntent)
                                    }
                                }
                            },
                            onCancel = { item ->
                                engine.cancelDownload(item.id)
                            },
                            onOpen = { item ->
                                openFile(item)
                            },
                            onShare = { item ->
                                shareFile(item)
                            },
                            onBadgeClick = {
                                currentTab = AppTab.SETTINGS
                            }
                        )
                    }

                    AppTab.HISTORY -> {
                        HistoryScreen(
                            downloads = downloads,
                            onOpen = { item ->
                                openFile(item)
                            },
                            onShare = { item ->
                                shareFile(item)
                            },
                            onDelete = { item ->
                                repository.removeItem(item.id)
                            },
                            onClearAll = {
                                repository.clearCompleted()
                            }
                        )
                    }

                    AppTab.SETTINGS -> {
                        SettingsScreen(
                            preferences = preferences,
                            onTestConnection = {
                                withContext(Dispatchers.IO) {
                                    networkClient.testConnection()
                                }
                            }
                        )
                    }
                }

                if (showAddDialog) {
                    AddUrlDialog(
                        initialUrl = initialDialogUrl,
                        defaultThreads = preferences.defaultThreads,
                        onDismiss = { showAddDialog = false },
                        onConfirm = { url, name, threads ->
                            startDownloadItem(url, name, threads)
                            showAddDialog = false
                            currentTab = AppTab.DOWNLOADS
                        }
                    )
                }
            }
        }
    }
}
