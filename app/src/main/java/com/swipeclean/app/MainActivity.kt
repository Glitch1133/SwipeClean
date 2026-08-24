@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.swipeclean.app

import android.Manifest
import android.app.AppOpsManager
import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.ActivityNotFoundException
import android.content.ContentUris
import android.content.Intent
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.pdf.PdfRenderer
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.os.Process
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.webkit.MimeTypeMap
import android.widget.Toast
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.IntentSenderRequest
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Surface
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import android.app.usage.UsageStatsManager
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max
import kotlin.math.roundToInt

data class PhotoItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val size: Long,
    val dateAdded: Long
)

data class VideoItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val size: Long,
    val dateAdded: Long,
    val durationMs: Long
)

data class AudioItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val size: Long,
    val dateAdded: Long,
    val durationMs: Long,
    val mimeType: String?
)

data class FileItem(
    val id: Long,
    val uri: Uri,
    val name: String,
    val size: Long,
    val dateAdded: Long,
    val mimeType: String?,
    val relativePath: String?,
    val extension: String,
    val absolutePath: String
)

enum class FileCategory(
    val title: String
) {
    DOWNLOADS("Downloads"),
    DOCUMENTS("Documents"),
    OTHER_FILES("Other Files")
}

enum class InAppPreviewType {
    IMAGE,
    VIDEO,
    AUDIO,
    PDF,
    TEXT,
    EXTERNAL
}

data class Decision(
    val photo: PhotoItem,
    val kept: Boolean
)

data class VideoDecision(
    val video: VideoItem,
    val kept: Boolean
)

data class AudioDecision(
    val audio: AudioItem,
    val kept: Boolean
)

data class FileDecision(
    val file: FileItem,
    val kept: Boolean
)

data class AppItem(
    val packageName: String,
    val name: String,
    val versionName: String,
    val sizeBytes: Long,
    val firstInstallTime: Long,
    val lastUsedTime: Long
)

data class AppDecision(
    val app: AppItem,
    val kept: Boolean
)

enum class EverythingKind {
    PHOTO,
    VIDEO,
    AUDIO,
    FILE,
    APP
}

data class EverythingItem(
    val key: String,
    val kind: EverythingKind,
    val name: String,
    val sizeBytes: Long,
    val dateAdded: Long,
    val lastUsedTime: Long = 0L,
    val photo: PhotoItem? = null,
    val video: VideoItem? = null,
    val audio: AudioItem? = null,
    val file: FileItem? = null,
    val app: AppItem? = null
)

data class EverythingDecision(
    val item: EverythingItem,
    val kept: Boolean
)

enum class EverythingSortMode(
    val label: String
) {
    LARGEST_FIRST("Largest → Smallest"),
    SMALLEST_FIRST("Smallest → Largest"),
    DATE_ADDED("Date Added"),
    LAST_USED("Last Used (Apps)")
}

enum class AppSortMode(
    val label: String
) {
    LARGEST_FIRST("Largest → Smallest"),
    SMALLEST_FIRST("Smallest → Largest"),
    DATE_INSTALLED("Date Installed"),
    LAST_USED("Last Used")
}

enum class SortMode(val label: String) {
    LARGEST_FIRST("Largest → Smallest"),
    SMALLEST_FIRST("Smallest → Largest"),
    DATE_ADDED("Date Added")
}

enum class AppThemeMode {
    LIGHT,
    DARK
}

enum class TrashKind {
    PHOTO,
    VIDEO,
    AUDIO,
    FILE,
    APP
}

enum class DeletePhase {
    IDLE,
    DELETE_FILES,
    DELETE_MEDIA,
    WAIT_MEDIA,
    WAIT_LEGACY_MEDIA,
    UNINSTALL_APPS,
    WAIT_APP,
    FINISHED
}

data class TrashDeleteSummary(
    val deletedCount: Int = 0,
    val failedCount: Int = 0,
    val cancelledCount: Int = 0,
    val deletedBytes: Long = 0L
)

data class TrashItem(
    val key: String,
    val kind: TrashKind,
    val name: String,
    val sizeBytes: Long,
    val photo: PhotoItem? = null,
    val video: VideoItem? = null,
    val audio: AudioItem? = null,
    val file: FileItem? = null,
    val app: AppItem? = null
)

enum class AppScreen {
    HOME,
    EVERYTHING,
    PHOTOS,
    VIDEOS,
    SCREENSHOTS,
    AUDIO,
    DOWNLOADS,
    DOCUMENTS,
    OTHER_FILES,
    APPS,
    TRASH,
    SETTINGS,
    PLACEHOLDER
}

data class CleanupCategory(
    val title: String,
    val description: String
)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            SwipeCleanRoot()
        }
    }
}

@Composable
fun SwipeCleanRoot() {

    val context =
        LocalContext.current

    var themeMode by remember {
        mutableStateOf(
            loadThemeMode(
                context
            )
        )
    }

    val colorScheme =
        when (themeMode) {

            AppThemeMode.LIGHT ->
                lightColorScheme()

            AppThemeMode.DARK ->
                darkColorScheme()
        }

    MaterialTheme(
        colorScheme =
            colorScheme
    ) {

        Surface(
            modifier =
                Modifier.fillMaxSize(),
            color =
                MaterialTheme
                    .colorScheme
                    .background
        ) {

            SwipeCleanApp(
                themeMode =
                    themeMode,
                onThemeModeChange = {
                        newMode ->

                    themeMode =
                        newMode

                    saveThemeMode(
                        context =
                            context,
                        themeMode =
                            newMode
                    )
                }
            )
        }
    }
}

@Composable
fun SwipeCleanApp(
    themeMode: AppThemeMode,
    onThemeModeChange:
        (AppThemeMode) -> Unit
) {

    val context =
        LocalContext.current

    var currentScreen by remember {
        mutableStateOf(AppScreen.HOME)
    }

    var placeholderTitle by remember {
        mutableStateOf("")
    }

    var trashScopes by remember {
        mutableStateOf<Map<String, List<TrashItem>>>(
            loadTrashScopes(
                context
            )
        )
    }

    fun setTrashScopes(
        updatedScopes:
        Map<String, List<TrashItem>>
    ) {

        trashScopes =
            updatedScopes

        saveTrashScopes(
            context =
                context,
            trashScopes =
                updatedScopes
        )
    }

    val trashItems =
        trashScopes.values
            .flatten()
            .distinctBy {
                it.key
            }

    fun updateTrashScope(
        scope: String,
        added: List<TrashItem>,
        removedKeys: Set<String>
    ) {

        val existing =
            trashScopes[scope]
                .orEmpty()

        val updated =
            (
                    existing.filterNot {
                        it.key in removedKeys
                    } + added
                    )
                .distinctBy {
                    it.key
                }

        setTrashScopes(
            trashScopes
                .toMutableMap()
                .apply {

                    if (updated.isEmpty()) {
                        remove(scope)
                    } else {
                        this[scope] = updated
                    }
                }
        )
    }

    fun restoreTrashItem(
        key: String
    ) {

        setTrashScopes(
            trashScopes
                .mapValues {
                        (_, items) ->

                    items.filterNot {
                        it.key == key
                    }
                }
                .filterValues {
                    it.isNotEmpty()
                }
        )
    }

    fun restoreAllTrash() {

        setTrashScopes(
            emptyMap()
        )
    }

    fun permanentlyRemoveTrashKeys(
        keys: Set<String>
    ) {
        if (keys.isEmpty()) return
        setTrashScopes(
            trashScopes
                .mapValues { (_, scopeItems) ->
                    scopeItems.filterNot { it.key in keys }
                }
                .filterValues { it.isNotEmpty() }
        )
    }

    when (currentScreen) {

        AppScreen.HOME -> {
            HomeScreen(
                onOpenEverything = {
                    currentScreen = AppScreen.EVERYTHING
                },
                onOpenPhotos = {
                    currentScreen = AppScreen.PHOTOS
                },
                onOpenVideos = {
                    currentScreen = AppScreen.VIDEOS
                },
                onOpenScreenshots = {
                    currentScreen = AppScreen.SCREENSHOTS
                },
                onOpenAudio = {
                    currentScreen = AppScreen.AUDIO
                },
                onOpenDownloads = {
                    currentScreen = AppScreen.DOWNLOADS
                },
                onOpenDocuments = {
                    currentScreen = AppScreen.DOCUMENTS
                },
                onOpenOtherFiles = {
                    currentScreen = AppScreen.OTHER_FILES
                },
                onOpenApps = {
                    currentScreen = AppScreen.APPS
                },
                trashCount =
                    trashItems.size,
                trashBytes =
                    trashItems.sumOf {
                        it.sizeBytes
                    },
                onOpenTrash = {
                    currentScreen = AppScreen.TRASH
                },
                onOpenSettings = {
                    currentScreen = AppScreen.SETTINGS
                },
                onOpenPlaceholder = { title ->
                    placeholderTitle = title
                    currentScreen = AppScreen.PLACEHOLDER
                }
            )
        }

        AppScreen.EVERYTHING -> {
            EverythingSwipeScreen(
                onBack = {
                    currentScreen = AppScreen.HOME
                },
                trashedItems =
                    trashItems,
                onTrashDelta = {
                        scope,
                        added,
                        removedKeys ->

                    updateTrashScope(
                        scope,
                        added,
                        removedKeys
                    )
                }
            )
        }

        AppScreen.PHOTOS -> {
            PhotoSwipeScreen(
                onBack = {
                    currentScreen = AppScreen.HOME
                },
                trashedItems =
                    trashItems,
                onTrashDelta = {
                        scope,
                        added,
                        removedKeys ->

                    updateTrashScope(
                        scope,
                        added,
                        removedKeys
                    )
                }
            )
        }

        AppScreen.VIDEOS -> {
            VideoSwipeScreen(
                onBack = {
                    currentScreen = AppScreen.HOME
                },
                trashedItems =
                    trashItems,
                onTrashDelta = {
                        scope,
                        added,
                        removedKeys ->

                    updateTrashScope(
                        scope,
                        added,
                        removedKeys
                    )
                }
            )
        }

        AppScreen.SCREENSHOTS -> {
            ScreenshotSwipeScreen(
                onBack = {
                    currentScreen = AppScreen.HOME
                },
                trashedItems =
                    trashItems,
                onTrashDelta = {
                        scope,
                        added,
                        removedKeys ->

                    updateTrashScope(
                        scope,
                        added,
                        removedKeys
                    )
                }
            )
        }

        AppScreen.AUDIO -> {
            AudioSwipeScreen(
                onBack = {
                    currentScreen = AppScreen.HOME
                },
                trashedItems =
                    trashItems,
                onTrashDelta = {
                        scope,
                        added,
                        removedKeys ->

                    updateTrashScope(
                        scope,
                        added,
                        removedKeys
                    )
                }
            )
        }

        AppScreen.DOWNLOADS -> {
            FileSwipeScreen(
                category = FileCategory.DOWNLOADS,
                onBack = {
                    currentScreen = AppScreen.HOME
                },
                trashedItems =
                    trashItems,
                onTrashDelta = {
                        scope,
                        added,
                        removedKeys ->

                    updateTrashScope(
                        scope,
                        added,
                        removedKeys
                    )
                }
            )
        }

        AppScreen.DOCUMENTS -> {
            FileSwipeScreen(
                category = FileCategory.DOCUMENTS,
                onBack = {
                    currentScreen = AppScreen.HOME
                },
                trashedItems =
                    trashItems,
                onTrashDelta = {
                        scope,
                        added,
                        removedKeys ->

                    updateTrashScope(
                        scope,
                        added,
                        removedKeys
                    )
                }
            )
        }

        AppScreen.OTHER_FILES -> {
            FileSwipeScreen(
                category = FileCategory.OTHER_FILES,
                onBack = {
                    currentScreen = AppScreen.HOME
                },
                trashedItems =
                    trashItems,
                onTrashDelta = {
                        scope,
                        added,
                        removedKeys ->

                    updateTrashScope(
                        scope,
                        added,
                        removedKeys
                    )
                }
            )
        }

        AppScreen.APPS -> {
            AppsSwipeScreen(
                onBack = {
                    currentScreen = AppScreen.HOME
                },
                trashedItems =
                    trashItems,
                onTrashDelta = {
                        scope,
                        added,
                        removedKeys ->

                    updateTrashScope(
                        scope,
                        added,
                        removedKeys
                    )
                }
            )
        }

        AppScreen.TRASH -> {
            TrashScreen(
                items = trashItems,
                onRestore = {
                        item ->

                    restoreTrashItem(
                        item.key
                    )
                },
                onRestoreAll = {
                    restoreAllTrash()
                },
                onPermanentlyRemoved = { keys ->
                    permanentlyRemoveTrashKeys(keys)
                },
                onBack = {
                    currentScreen = AppScreen.HOME
                }
            )
        }

        AppScreen.SETTINGS -> {
            SettingsScreen(
                themeMode =
                    themeMode,
                onThemeModeChange =
                    onThemeModeChange,
                onBack = {
                    currentScreen =
                        AppScreen.HOME
                }
            )
        }

        AppScreen.PLACEHOLDER -> {
            PlaceholderCategoryScreen(
                title = placeholderTitle,
                onBack = {
                    currentScreen = AppScreen.HOME
                }
            )
        }
    }
}

@Composable
fun HomeScreen(
    onOpenEverything: () -> Unit,
    onOpenPhotos: () -> Unit,
    onOpenVideos: () -> Unit,
    onOpenScreenshots: () -> Unit,
    onOpenAudio: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenDocuments: () -> Unit,
    onOpenOtherFiles: () -> Unit,
    onOpenApps: () -> Unit,
    trashCount: Int,
    trashBytes: Long,
    onOpenTrash: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPlaceholder: (String) -> Unit
) {

    val categories = listOf(
        CleanupCategory(
            title = "Everything",
            description = "All accessible cleanup items in one queue"
        ),
        CleanupCategory(
            title = "Photos",
            description = "Pictures and saved images"
        ),
        CleanupCategory(
            title = "Videos",
            description = "Recorded and downloaded videos"
        ),
        CleanupCategory(
            title = "Screenshots",
            description = "Screenshots stored on your device"
        ),
        CleanupCategory(
            title = "Apps",
            description = "Installed apps that can be reviewed"
        ),
        CleanupCategory(
            title = "Audio",
            description = "Music, recordings, and audio files"
        ),
        CleanupCategory(
            title = "Downloads",
            description = "Files saved in Downloads"
        ),
        CleanupCategory(
            title = "Documents",
            description = "PDFs, text files, Office documents, spreadsheets, and more"
        ),
        CleanupCategory(
            title = "Other Files",
            description = "Archives, APKs, EXEs, and other accessible files"
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {

        Spacer(
            modifier = Modifier.height(10.dp)
        )

        Text(
            text = "SwipeClean",
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(
            modifier = Modifier.height(6.dp)
        )

        Text(
            text = "Pick what you want to clean.",
            fontSize = 18.sp
        )

        Spacer(
            modifier = Modifier.height(6.dp)
        )

        Text(
            text = "Swipe left to send items to Trash. Nothing is permanently deleted yet.",
            fontSize = 14.sp,
            color = Color.Gray
        )

        Spacer(
            modifier = Modifier.height(24.dp)
        )

        categories.forEach { category ->

            CategoryCard(
                title = category.title,
                description = category.description,
                enabled =
                    category.title == "Everything" ||
                            category.title == "Photos" ||
                            category.title == "Videos" ||
                            category.title == "Screenshots" ||
                            category.title == "Audio" ||
                            category.title == "Downloads" ||
                            category.title == "Documents" ||
                            category.title == "Other Files" ||
                            category.title == "Apps",
                onClick = {
                    when (category.title) {
                        "Everything" -> onOpenEverything()
                        "Photos" -> onOpenPhotos()
                        "Videos" -> onOpenVideos()
                        "Screenshots" -> onOpenScreenshots()
                        "Audio" -> onOpenAudio()
                        "Downloads" -> onOpenDownloads()
                        "Documents" -> onOpenDocuments()
                        "Other Files" -> onOpenOtherFiles()
                        "Apps" -> onOpenApps()
                        else -> onOpenPlaceholder(category.title)
                    }
                }
            )

            Spacer(
                modifier = Modifier.height(12.dp)
            )
        }

        Spacer(
            modifier = Modifier.height(8.dp)
        )

        Text(
            text = "Review",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(
            modifier = Modifier.height(10.dp)
        )

        CategoryCard(
            title = "Trash",
            description =
                if (trashCount == 0) {
                    "No items are marked for removal"
                } else {
                    "$trashCount marked • " +
                            formatFileSize(
                                LocalContext.current,
                                trashBytes
                            )
                },
            enabled = true,
            onClick = onOpenTrash
        )

        Spacer(
            modifier = Modifier.height(20.dp)
        )

        Text(
            text = "App",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(
            modifier = Modifier.height(10.dp)
        )

        CategoryCard(
            title = "Settings",
            description =
                "Appearance, feedback, and app information",
            enabled = true,
            onClick = onOpenSettings
        )

        Spacer(
            modifier = Modifier.height(20.dp)
        )

        Text(
            text = "© 2026 Gl!tch Studios",
            modifier =
                Modifier.fillMaxWidth(),
            fontSize = 12.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )

        Spacer(
            modifier = Modifier.height(20.dp)
        )
    }
}

@Composable
fun CategoryCard(
    title: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit
) {

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                onClick()
            },
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 4.dp
        )
    ) {

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {

                Text(
                    text = title,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = if (enabled) "READY" else "SOON",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (enabled) Color(0xFF2E7D32) else Color.Gray
                )
            }

            Spacer(
                modifier = Modifier.height(6.dp)
            )

            Text(
                text = description,
                fontSize = 15.sp
            )
        }
    }
}

@Composable
fun TrashScreen(
    items: List<TrashItem>,
    onRestore: (TrashItem) -> Unit,
    onRestoreAll: () -> Unit,
    onPermanentlyRemoved: (Set<String>) -> Unit,
    onBack: () -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current

    val deletionScope =
        rememberCoroutineScope()

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showDeleteResult by remember { mutableStateOf(false) }
    var deletePhase by remember { mutableStateOf(DeletePhase.IDLE) }
    var mediaQueue by remember { mutableStateOf<List<TrashItem>>(emptyList()) }
    var fileQueue by remember { mutableStateOf<List<TrashItem>>(emptyList()) }
    var appQueue by remember { mutableStateOf<List<TrashItem>>(emptyList()) }
    var activeMediaBatch by remember { mutableStateOf<List<TrashItem>>(emptyList()) }
    var activeLegacyMedia by remember { mutableStateOf<TrashItem?>(null) }
    var activeApp by remember { mutableStateOf<TrashItem?>(null) }
    var deletedKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var failedKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var cancelledKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var deletedBytes by remember { mutableLongStateOf(0L) }
    var permissionContinuationPending by remember { mutableStateOf(false) }

    val isDeleting =
        deletePhase != DeletePhase.IDLE &&
                deletePhase != DeletePhase.FINISHED

    fun recordDeleted(deletedItems: List<TrashItem>) {
        if (deletedItems.isEmpty()) return
        val fresh = deletedItems.filter { it.key !in deletedKeys }
        val keys = fresh.map { it.key }.toSet()
        deletedKeys = deletedKeys + keys
        failedKeys = failedKeys - keys
        cancelledKeys = cancelledKeys - keys
        deletedBytes += fresh.sumOf { it.sizeBytes }
        onPermanentlyRemoved(keys)
    }

    fun recordFailed(failedItems: List<TrashItem>) {
        failedKeys = failedKeys + failedItems.map { it.key }.toSet()
    }

    fun recordCancelled(cancelledItems: List<TrashItem>) {
        cancelledKeys = cancelledKeys + cancelledItems.map { it.key }.toSet()
    }

    fun beginDeletion() {
        deletedKeys = emptySet()
        failedKeys = emptySet()
        cancelledKeys = emptySet()
        deletedBytes = 0L
        activeMediaBatch = emptyList()
        activeLegacyMedia = null
        activeApp = null
        mediaQueue = items.filter {
            it.kind == TrashKind.PHOTO ||
                    it.kind == TrashKind.VIDEO ||
                    it.kind == TrashKind.AUDIO
        }
        fileQueue = items.filter { it.kind == TrashKind.FILE }
        appQueue = items.filter { it.kind == TrashKind.APP }
        deletePhase = DeletePhase.DELETE_FILES
    }

    val writePermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted && permissionContinuationPending) {
                permissionContinuationPending = false
                beginDeletion()
            } else {
                permissionContinuationPending = false
                Toast.makeText(
                    context,
                    "Storage write permission is required to delete files on this Android version.",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

    val mediaDeleteLauncher =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts
                    .StartIntentSenderForResult()
        ) { result ->

            val batch =
                activeMediaBatch

            if (
                result.resultCode ==
                Activity.RESULT_OK
            ) {

                deletionScope.launch {

                    delay(650L)

                    val verification =
                        withContext(
                            Dispatchers.IO
                        ) {

                            batch.map {
                                    item ->

                                item to
                                        trashItemActuallyGone(
                                            context,
                                            item
                                        )
                            }
                        }

                    val actuallyDeleted =
                        verification
                            .filter {
                                it.second
                            }
                            .map {
                                it.first
                            }

                    val stillPresent =
                        verification
                            .filterNot {
                                it.second
                            }
                            .map {
                                it.first
                            }

                    recordDeleted(
                        actuallyDeleted
                    )

                    recordFailed(
                        stillPresent
                    )

                    mediaQueue =
                        mediaQueue.drop(
                            batch.size
                        )

                    activeMediaBatch =
                        emptyList()

                    deletePhase =
                        DeletePhase.DELETE_MEDIA
                }

            } else {

                recordCancelled(
                    batch
                )

                mediaQueue =
                    mediaQueue.drop(
                        batch.size
                    )

                activeMediaBatch =
                    emptyList()

                deletePhase =
                    DeletePhase.DELETE_MEDIA
            }
        }

    val legacyMediaDeleteLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartIntentSenderForResult()
        ) { result ->
            val item = activeLegacyMedia
            if (item != null) {
                if (result.resultCode == Activity.RESULT_OK) {
                    val uri = resolveTrashMediaUri(context, item)
                    val deleted = if (uri != null) {
                        try {
                            context.contentResolver.delete(uri, null, null) > 0 ||
                                    !contentUriExists(context, uri)
                        } catch (_: Exception) {
                            false
                        }
                    } else false
                    if (deleted) recordDeleted(listOf(item))
                    else recordFailed(listOf(item))
                } else {
                    recordCancelled(listOf(item))
                }
                mediaQueue = mediaQueue.drop(1)
                activeLegacyMedia = null
            }
            deletePhase = DeletePhase.DELETE_MEDIA
        }

    val appUninstallLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) { result ->
            val item = activeApp
            if (item != null) {
                val packageName = item.app?.packageName
                val stillInstalled =
                    packageName?.let { isPackageInstalled(context, it) } ?: false
                if (result.resultCode == Activity.RESULT_OK || !stillInstalled) {
                    recordDeleted(listOf(item))
                } else {
                    recordCancelled(listOf(item))
                }
                appQueue = appQueue.drop(1)
                activeApp = null
            }
            deletePhase = DeletePhase.UNINSTALL_APPS
        }

    LaunchedEffect(deletePhase, fileQueue, mediaQueue, appQueue) {
        when (deletePhase) {
            DeletePhase.DELETE_FILES -> {

                val snapshot =
                    fileQueue

                if (
                    snapshot.isNotEmpty()
                ) {

                    val classified =
                        withContext(
                            Dispatchers.IO
                        ) {

                            snapshot.map {
                                    item ->

                                val mediaUri =
                                    resolveTrashMediaUri(
                                        context,
                                        item
                                    )

                                Triple(
                                    item,
                                    mediaUri,
                                    mediaUri != null
                                )
                            }
                        }

                    val mediaBackedFiles =
                        classified
                            .filter {
                                it.third
                            }
                            .map {
                                it.first
                            }

                    val regularFiles =
                        classified
                            .filterNot {
                                it.third
                            }
                            .map {
                                it.first
                            }

                    if (
                        mediaBackedFiles.isNotEmpty()
                    ) {

                        mediaQueue =
                            (
                                    mediaQueue +
                                            mediaBackedFiles
                                    )
                                .distinctBy {
                                    it.key
                                }
                    }

                    if (
                        regularFiles.isNotEmpty()
                    ) {

                        val results =
                            withContext(
                                Dispatchers.IO
                            ) {

                                regularFiles.map {
                                        item ->

                                    item to
                                            deleteSharedFileItem(
                                                context,
                                                item
                                            )
                                }
                            }

                        recordDeleted(
                            results
                                .filter {
                                    it.second
                                }
                                .map {
                                    it.first
                                }
                        )

                        recordFailed(
                            results
                                .filterNot {
                                    it.second
                                }
                                .map {
                                    it.first
                                }
                        )
                    }

                    fileQueue =
                        emptyList()
                }

                deletePhase =
                    DeletePhase.DELETE_MEDIA
            }

            DeletePhase.DELETE_MEDIA -> {
                if (mediaQueue.isEmpty()) {
                    deletePhase = DeletePhase.UNINSTALL_APPS
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val batch =
                        mediaQueue.take(
                            500
                        )

                    val resolved =
                        batch.map {
                                item ->

                            item to
                                    resolveTrashMediaUri(
                                        context,
                                        item
                                    )
                        }

                    val resolvableItems =
                        resolved
                            .filter {
                                it.second != null
                            }
                            .map {
                                it.first
                            }

                    val unresolvedItems =
                        resolved
                            .filter {
                                it.second == null
                            }
                            .map {
                                it.first
                            }

                    if (
                        unresolvedItems.isNotEmpty()
                    ) {

                        recordFailed(
                            unresolvedItems
                        )
                    }

                    if (
                        resolvableItems.isEmpty()
                    ) {

                        mediaQueue =
                            mediaQueue.drop(
                                batch.size
                            )

                    } else {

                        val uris =
                            resolvableItems
                                .mapNotNull {
                                    resolveTrashMediaUri(
                                        context,
                                        it
                                    )
                                }
                                .distinct()

                        try {

                            val pendingIntent =
                                MediaStore
                                    .createDeleteRequest(
                                        context
                                            .contentResolver,
                                        uris
                                    )

                            activeMediaBatch =
                                resolvableItems

                            mediaQueue =
                                resolvableItems +
                                        mediaQueue.drop(
                                            batch.size
                                        )

                            deletePhase =
                                DeletePhase.WAIT_MEDIA

                            mediaDeleteLauncher
                                .launch(
                                    IntentSenderRequest
                                        .Builder(
                                            pendingIntent
                                                .intentSender
                                        )
                                        .build()
                                )

                        } catch (
                            _: Exception
                        ) {

                            recordFailed(
                                resolvableItems
                            )

                            mediaQueue =
                                mediaQueue.drop(
                                    batch.size
                                )
                        }
                    }
                } else if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                    val item = mediaQueue.first()
                    val uri = resolveTrashMediaUri(context, item)
                    if (uri == null) {
                        recordFailed(listOf(item))
                        mediaQueue = mediaQueue.drop(1)
                    } else {
                        try {
                            val deleted =
                                context.contentResolver.delete(uri, null, null) > 0 ||
                                        !contentUriExists(context, uri)
                            if (deleted) recordDeleted(listOf(item))
                            else recordFailed(listOf(item))
                            mediaQueue = mediaQueue.drop(1)
                        } catch (recoverable: RecoverableSecurityException) {
                            activeLegacyMedia = item
                            deletePhase = DeletePhase.WAIT_LEGACY_MEDIA
                            legacyMediaDeleteLauncher.launch(
                                IntentSenderRequest.Builder(
                                    recoverable.userAction.actionIntent.intentSender
                                ).build()
                            )
                        } catch (_: Exception) {
                            recordFailed(listOf(item))
                            mediaQueue = mediaQueue.drop(1)
                        }
                    }
                } else {
                    val snapshot = mediaQueue
                    val results = withContext(Dispatchers.IO) {
                        snapshot.map { item ->
                            val uri = resolveTrashMediaUri(context, item)
                            val deleted = if (uri != null) {
                                try {
                                    context.contentResolver.delete(uri, null, null) > 0 ||
                                            !contentUriExists(context, uri)
                                } catch (_: Exception) {
                                    false
                                }
                            } else false
                            item to deleted
                        }
                    }
                    recordDeleted(results.filter { it.second }.map { it.first })
                    recordFailed(results.filterNot { it.second }.map { it.first })
                    mediaQueue = emptyList()
                }
            }

            DeletePhase.UNINSTALL_APPS -> {
                if (appQueue.isEmpty()) {
                    deletePhase = DeletePhase.FINISHED
                } else {
                    val item = appQueue.first()
                    val packageName = item.app?.packageName
                    if (packageName == null) {
                        recordFailed(listOf(item))
                        appQueue = appQueue.drop(1)
                    } else if (!isPackageInstalled(context, packageName)) {
                        recordDeleted(listOf(item))
                        appQueue = appQueue.drop(1)
                    } else {
                        try {
                            activeApp = item
                            deletePhase = DeletePhase.WAIT_APP
                            appUninstallLauncher.launch(
                                Intent(Intent.ACTION_UNINSTALL_PACKAGE).apply {
                                    data = Uri.parse("package:$packageName")
                                    putExtra(Intent.EXTRA_RETURN_RESULT, true)
                                }
                            )
                        } catch (_: Exception) {
                            activeApp = null
                            recordFailed(listOf(item))
                            appQueue = appQueue.drop(1)
                            deletePhase = DeletePhase.UNINSTALL_APPS
                        }
                    }
                }
            }

            DeletePhase.FINISHED -> {
                showDeleteResult = true
            }

            else -> Unit
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { if (!isDeleting) showDeleteConfirm = false },
            title = { Text("Permanently empty Trash?") },
            text = {
                Column {
                    Text(
                        "${items.size} items • " +
                                formatFileSize(context, items.sumOf { it.sizeBytes })
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "This cannot be undone. Android will ask you to confirm media deletion and each app uninstall. Shared files such as APKs, ZIPs, PDFs, documents, and EXEs will be deleted after this confirmation."
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    showDeleteConfirm = false
                    val needsLegacyWrite =
                        Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
                                items.any { it.kind != TrashKind.APP } &&
                                ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                                ) != PackageManager.PERMISSION_GRANTED
                    if (needsLegacyWrite) {
                        permissionContinuationPending = true
                        writePermissionLauncher.launch(
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                        )
                    } else {
                        beginDeletion()
                    }
                }) {
                    Text("Delete Permanently")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDeleteResult) {
        val summary = TrashDeleteSummary(
            deletedCount = deletedKeys.size,
            failedCount = failedKeys.size,
            cancelledCount = cancelledKeys.size,
            deletedBytes = deletedBytes
        )
        AlertDialog(
            onDismissRequest = {
                showDeleteResult = false
                deletePhase = DeletePhase.IDLE
            },
            title = { Text("Deletion finished") },
            text = {
                Column {
                    Text("${summary.deletedCount} deleted/uninstalled")
                    Text(formatFileSize(context, summary.deletedBytes) + " removed")
                    if (summary.cancelledCount > 0) {
                        Text("${summary.cancelledCount} cancelled and left in Trash")
                    }
                    if (summary.failedCount > 0) {
                        Text("${summary.failedCount} could not be removed and remain in Trash")
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    showDeleteResult = false
                    deletePhase = DeletePhase.IDLE
                }) { Text("Done") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = onBack, enabled = !isDeleting) { Text("← Back") }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text("Trash", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (items.isEmpty()) "Trash is empty"
                    else "${items.size} marked • " +
                            formatFileSize(context, items.sumOf { it.sizeBytes }),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(20.dp))
        Text(
            "Review everything here before permanent removal. Successfully deleted files and uninstalled apps disappear from Trash; cancelled or failed items stay here.",
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(20.dp))

        if (items.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("🗑", fontSize = 54.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("Trash is empty", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Swipe left on an item to send it here.",
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onRestoreAll,
                    enabled = !isDeleting,
                    modifier = Modifier.weight(1f)
                ) { Text("Restore All") }
                Button(
                    onClick = { showDeleteConfirm = true },
                    enabled = !isDeleting,
                    modifier = Modifier.weight(1f)
                ) { Text(if (isDeleting) "Deleting..." else "Empty Trash") }
            }

            if (isDeleting) {
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(Modifier.width(22.dp).height(22.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(deletionPhaseText(deletePhase), fontSize = 14.sp)
                }
            }

            Spacer(Modifier.height(18.dp))
            items.forEach { item ->
                TrashItemCard(
                    item = item,
                    enabled = !isDeleting,
                    onRestore = { onRestore(item) }
                )
                Spacer(Modifier.height(12.dp))
            }
        }

        Spacer(Modifier.height(18.dp))
        Text(
            "Permanent deletion cannot be undone. System-protected Android data is not included in SwipeClean's accessible cleanup list.",
            modifier = Modifier.fillMaxWidth(),
            fontSize = 12.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun TrashItemCard(
    item: TrashItem,
    enabled: Boolean = true,
    onRestore: () -> Unit
) {

    val context =
        LocalContext.current

    Card(
        modifier =
            Modifier.fillMaxWidth(),
        shape =
            RoundedCornerShape(20.dp)
    ) {

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Card(
                modifier =
                    Modifier
                        .width(92.dp)
                        .height(92.dp),
                shape =
                    RoundedCornerShape(16.dp)
            ) {

                TrashItemPreview(
                    item = item
                )
            }

            Spacer(
                modifier =
                    Modifier.width(14.dp)
            )

            Column(
                modifier =
                    Modifier.weight(1f)
            ) {

                Text(
                    text = item.name,
                    fontSize = 17.sp,
                    fontWeight =
                        FontWeight.Bold,
                    maxLines = 2,
                    overflow =
                        TextOverflow.Ellipsis
                )

                Spacer(
                    modifier =
                        Modifier.height(4.dp)
                )

                Text(
                    text =
                        trashKindLabel(
                            item.kind
                        ) +
                                " • " +
                                formatFileSize(
                                    context,
                                    item.sizeBytes
                                ),
                    fontSize = 13.sp,
                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurfaceVariant
                )
            }

            Spacer(
                modifier =
                    Modifier.width(10.dp)
            )

            OutlinedButton(
                onClick = onRestore,
                enabled = enabled
            ) {

                Text("Restore")
            }
        }
    }
}

@Composable
fun TrashItemPreview(
    item: TrashItem
) {

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                MaterialTheme
                    .colorScheme
                    .surfaceVariant
            ),
        contentAlignment =
            Alignment.Center
    ) {

        when (item.kind) {

            TrashKind.PHOTO -> {

                val photo =
                    item.photo

                if (photo != null) {

                    TrashPhotoThumbnail(
                        photo = photo
                    )

                } else {

                    TrashPreviewBadge(
                        text = "IMG"
                    )
                }
            }

            TrashKind.VIDEO -> {

                val video =
                    item.video

                if (video != null) {

                    TrashVideoThumbnail(
                        video = video
                    )

                } else {

                    TrashPreviewBadge(
                        text = "VID"
                    )
                }
            }

            TrashKind.AUDIO -> {

                val audio =
                    item.audio

                if (audio != null) {

                    TrashAudioThumbnail(
                        uri = audio.uri
                    )

                } else {

                    TrashPreviewBadge(
                        text = "♪"
                    )
                }
            }

            TrashKind.FILE -> {

                val file =
                    item.file

                if (file != null) {

                    TrashGenericFileThumbnail(
                        file = file
                    )

                } else {

                    TrashPreviewBadge(
                        text = "FILE"
                    )
                }
            }

            TrashKind.APP -> {

                val app =
                    item.app

                if (app != null) {

                    TrashAppThumbnail(
                        packageName =
                            app.packageName
                    )

                } else {

                    TrashPreviewBadge(
                        text = "APP"
                    )
                }
            }
        }
    }
}

@Composable
fun TrashPreviewBadge(
    text: String
) {

    Text(
        text = text,
        fontSize =
            if (
                text.length <= 3
            ) {
                20.sp
            } else {
                13.sp
            },
        fontWeight =
            FontWeight.Bold,
        textAlign =
            TextAlign.Center,
        modifier =
            Modifier.padding(6.dp)
    )
}

@Composable
fun TrashPhotoThumbnail(
    photo: PhotoItem
) {

    val context =
        LocalContext.current

    var imageBitmap by
    remember(photo.uri) {
        mutableStateOf<ImageBitmap?>(
            null
        )
    }

    var failed by
    remember(photo.uri) {
        mutableStateOf(false)
    }

    LaunchedEffect(
        photo.uri
    ) {

        imageBitmap = null
        failed = false

        try {

            val bitmap =
                withContext(
                    Dispatchers.IO
                ) {

                    loadHighQualityPreview(
                        context = context,
                        uri = photo.uri,
                        maxDimension = 320
                    )
                }

            if (bitmap != null) {

                imageBitmap =
                    bitmap.asImageBitmap()

            } else {

                failed = true
            }

        } catch (
            _: Exception
        ) {

            failed = true
        }
    }

    when {

        imageBitmap != null -> {

            Image(
                bitmap =
                    imageBitmap!!,
                contentDescription =
                    photo.name,
                modifier =
                    Modifier.fillMaxSize(),
                contentScale =
                    ContentScale.Crop
            )
        }

        failed -> {

            TrashPreviewBadge(
                text = "IMG"
            )
        }

        else -> {

            CircularProgressIndicator(
                modifier =
                    Modifier
                        .width(24.dp)
                        .height(24.dp)
            )
        }
    }
}

@Composable
fun TrashVideoThumbnail(
    video: VideoItem
) {

    val context =
        LocalContext.current

    var imageBitmap by
    remember(video.uri) {
        mutableStateOf<ImageBitmap?>(
            null
        )
    }

    var failed by
    remember(video.uri) {
        mutableStateOf(false)
    }

    LaunchedEffect(
        video.uri
    ) {

        imageBitmap = null
        failed = false

        try {

            val bitmap =
                withContext(
                    Dispatchers.IO
                ) {

                    loadVideoPreview(
                        context = context,
                        uri = video.uri,
                        maxDimension = 320
                    )
                }

            if (bitmap != null) {

                imageBitmap =
                    bitmap.asImageBitmap()

            } else {

                failed = true
            }

        } catch (
            _: Exception
        ) {

            failed = true
        }
    }

    Box(
        modifier =
            Modifier.fillMaxSize(),
        contentAlignment =
            Alignment.Center
    ) {

        when {

            imageBitmap != null -> {

                Image(
                    bitmap =
                        imageBitmap!!,
                    contentDescription =
                        video.name,
                    modifier =
                        Modifier.fillMaxSize(),
                    contentScale =
                        ContentScale.Crop
                )
            }

            failed -> {

                TrashPreviewBadge(
                    text = "VID"
                )
            }

            else -> {

                CircularProgressIndicator(
                    modifier =
                        Modifier
                            .width(24.dp)
                            .height(24.dp)
                )
            }
        }

        if (imageBitmap != null) {

            Text(
                text = "▶",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight =
                    FontWeight.Bold
            )
        }
    }
}

@Composable
fun TrashAudioThumbnail(
    uri: Uri
) {

    val context =
        LocalContext.current

    var artwork by
    remember(uri) {
        mutableStateOf<ImageBitmap?>(
            null
        )
    }

    var loaded by
    remember(uri) {
        mutableStateOf(false)
    }

    LaunchedEffect(
        uri
    ) {

        artwork = null
        loaded = false

        try {

            val bitmap =
                withContext(
                    Dispatchers.IO
                ) {

                    loadAudioArtworkThumbnail(
                        context = context,
                        uri = uri
                    )
                }

            if (bitmap != null) {

                artwork =
                    bitmap.asImageBitmap()
            }

        } catch (
            _: Exception
        ) {
        }

        loaded = true
    }

    when {

        artwork != null -> {

            Image(
                bitmap =
                    artwork!!,
                contentDescription =
                    "Audio artwork",
                modifier =
                    Modifier.fillMaxSize(),
                contentScale =
                    ContentScale.Crop
            )
        }

        loaded -> {

            TrashPreviewBadge(
                text = "♪"
            )
        }

        else -> {

            CircularProgressIndicator(
                modifier =
                    Modifier
                        .width(24.dp)
                        .height(24.dp)
            )
        }
    }
}

@Composable
fun TrashAppThumbnail(
    packageName: String
) {

    val context =
        LocalContext.current

    AndroidView(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(8.dp),
        factory = {
                androidContext ->

            ImageView(
                androidContext
            ).apply {

                scaleType =
                    ImageView.ScaleType
                        .FIT_CENTER
            }
        },
        update = {
                imageView ->

            try {

                imageView
                    .setImageDrawable(
                        context
                            .packageManager
                            .getApplicationIcon(
                                packageName
                            )
                    )

            } catch (
                _: Exception
            ) {

                imageView
                    .setImageDrawable(
                        null
                    )
            }
        }
    )
}

@Composable
fun TrashGenericFileThumbnail(
    file: FileItem
) {

    when (
        inAppPreviewType(
            file
        )
    ) {

        InAppPreviewType.IMAGE -> {

            TrashFileImageThumbnail(
                file = file
            )
        }

        InAppPreviewType.VIDEO -> {

            TrashFileVideoThumbnail(
                file = file
            )
        }

        InAppPreviewType.AUDIO -> {

            TrashAudioThumbnail(
                uri =
                    Uri.fromFile(
                        File(
                            file.absolutePath
                        )
                    )
            )
        }

        InAppPreviewType.PDF -> {

            TrashPdfThumbnail(
                file = file
            )
        }

        InAppPreviewType.TEXT -> {

            TrashTextThumbnail(
                file = file
            )
        }

        InAppPreviewType.EXTERNAL -> {

            if (
                file.extension
                    .lowercase() ==
                "apk"
            ) {

                TrashApkThumbnail(
                    file = file
                )

            } else {

                TrashPreviewBadge(
                    text =
                        fileTypeBadge(
                            file
                        )
                )
            }
        }
    }
}

@Composable
fun TrashFileImageThumbnail(
    file: FileItem
) {

    var imageBitmap by
    remember(
        file.absolutePath
    ) {
        mutableStateOf<ImageBitmap?>(
            null
        )
    }

    var failed by
    remember(
        file.absolutePath
    ) {
        mutableStateOf(false)
    }

    LaunchedEffect(
        file.absolutePath
    ) {

        imageBitmap = null
        failed = false

        try {

            val bitmap =
                withContext(
                    Dispatchers.IO
                ) {

                    loadImageFilePreview(
                        absolutePath =
                            file.absolutePath,
                        maxDimension = 320
                    )
                }

            if (bitmap != null) {

                imageBitmap =
                    bitmap.asImageBitmap()

            } else {

                failed = true
            }

        } catch (
            _: Exception
        ) {

            failed = true
        }
    }

    when {

        imageBitmap != null -> {

            Image(
                bitmap =
                    imageBitmap!!,
                contentDescription =
                    file.name,
                modifier =
                    Modifier.fillMaxSize(),
                contentScale =
                    ContentScale.Crop
            )
        }

        failed -> {

            TrashPreviewBadge(
                text =
                    fileTypeBadge(
                        file
                    )
            )
        }

        else -> {

            CircularProgressIndicator(
                modifier =
                    Modifier
                        .width(24.dp)
                        .height(24.dp)
            )
        }
    }
}

@Composable
fun TrashFileVideoThumbnail(
    file: FileItem
) {

    val context =
        LocalContext.current

    val uri =
        remember(
            file.absolutePath
        ) {

            Uri.fromFile(
                File(
                    file.absolutePath
                )
            )
        }

    var imageBitmap by
    remember(
        file.absolutePath
    ) {
        mutableStateOf<ImageBitmap?>(
            null
        )
    }

    var failed by
    remember(
        file.absolutePath
    ) {
        mutableStateOf(false)
    }

    LaunchedEffect(
        file.absolutePath
    ) {

        imageBitmap = null
        failed = false

        try {

            val bitmap =
                withContext(
                    Dispatchers.IO
                ) {

                    loadVideoPreview(
                        context = context,
                        uri = uri,
                        maxDimension = 320
                    )
                }

            if (bitmap != null) {

                imageBitmap =
                    bitmap.asImageBitmap()

            } else {

                failed = true
            }

        } catch (
            _: Exception
        ) {

            failed = true
        }
    }

    Box(
        modifier =
            Modifier.fillMaxSize(),
        contentAlignment =
            Alignment.Center
    ) {

        when {

            imageBitmap != null -> {

                Image(
                    bitmap =
                        imageBitmap!!,
                    contentDescription =
                        file.name,
                    modifier =
                        Modifier.fillMaxSize(),
                    contentScale =
                        ContentScale.Crop
                )
            }

            failed -> {

                TrashPreviewBadge(
                    text = "VID"
                )
            }

            else -> {

                CircularProgressIndicator(
                    modifier =
                        Modifier
                            .width(24.dp)
                            .height(24.dp)
                )
            }
        }

        if (imageBitmap != null) {

            Text(
                text = "▶",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight =
                    FontWeight.Bold
            )
        }
    }
}

@Composable
fun TrashPdfThumbnail(
    file: FileItem
) {

    var imageBitmap by
    remember(
        file.absolutePath
    ) {
        mutableStateOf<ImageBitmap?>(
            null
        )
    }

    var failed by
    remember(
        file.absolutePath
    ) {
        mutableStateOf(false)
    }

    LaunchedEffect(
        file.absolutePath
    ) {

        imageBitmap = null
        failed = false

        try {

            val bitmap =
                withContext(
                    Dispatchers.IO
                ) {

                    renderPdfThumbnail(
                        absolutePath =
                            file.absolutePath,
                        maxWidth = 320
                    )
                }

            if (bitmap != null) {

                imageBitmap =
                    bitmap.asImageBitmap()

            } else {

                failed = true
            }

        } catch (
            _: Exception
        ) {

            failed = true
        }
    }

    when {

        imageBitmap != null -> {

            Image(
                bitmap =
                    imageBitmap!!,
                contentDescription =
                    file.name,
                modifier =
                    Modifier.fillMaxSize(),
                contentScale =
                    ContentScale.Crop
            )
        }

        failed -> {

            TrashPreviewBadge(
                text = "PDF"
            )
        }

        else -> {

            CircularProgressIndicator(
                modifier =
                    Modifier
                        .width(24.dp)
                        .height(24.dp)
            )
        }
    }
}

@Composable
fun TrashTextThumbnail(
    file: FileItem
) {

    var snippet by
    remember(
        file.absolutePath
    ) {
        mutableStateOf<String?>(
            null
        )
    }

    LaunchedEffect(
        file.absolutePath
    ) {

        snippet =
            try {

                withContext(
                    Dispatchers.IO
                ) {

                    readTextThumbnailPreview(
                        file.absolutePath
                    )
                }

            } catch (
                _: Exception
            ) {

                ""
            }
    }

    if (snippet == null) {

        CircularProgressIndicator(
            modifier =
                Modifier
                    .width(24.dp)
                    .height(24.dp)
        )

    } else if (
        snippet!!.isBlank()
    ) {

        TrashPreviewBadge(
            text =
                fileTypeBadge(
                    file
                )
        )

    } else {

        Text(
            text =
                snippet!!,
            fontSize = 8.sp,
            lineHeight = 9.sp,
            maxLines = 9,
            overflow =
                TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(7.dp)
        )
    }
}

@Composable
fun TrashApkThumbnail(
    file: FileItem
) {

    val context =
        LocalContext.current

    AndroidView(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(8.dp),
        factory = {
                androidContext ->

            ImageView(
                androidContext
            ).apply {

                scaleType =
                    ImageView.ScaleType
                        .FIT_CENTER
            }
        },
        update = {
                imageView ->

            try {

                val packageManager =
                    context.packageManager

                @Suppress("DEPRECATION")
                val packageInfo =
                    packageManager
                        .getPackageArchiveInfo(
                            file.absolutePath,
                            0
                        )

                val appInfo =
                    packageInfo
                        ?.applicationInfo

                if (appInfo != null) {

                    appInfo.sourceDir =
                        file.absolutePath

                    appInfo.publicSourceDir =
                        file.absolutePath

                    imageView
                        .setImageDrawable(
                            appInfo.loadIcon(
                                packageManager
                            )
                        )

                } else {

                    imageView
                        .setImageDrawable(
                            null
                        )
                }

            } catch (
                _: Exception
            ) {

                imageView
                    .setImageDrawable(
                        null
                    )
            }
        }
    )
}

fun loadAudioArtworkThumbnail(
    context: Context,
    uri: Uri
): Bitmap? {

    val retriever =
        MediaMetadataRetriever()

    return try {

        retriever.setDataSource(
            context,
            uri
        )

        val bytes =
            retriever.embeddedPicture
                ?: return null

        val bitmap =
            BitmapFactory
                .decodeByteArray(
                    bytes,
                    0,
                    bytes.size
                )
                ?: return null

        val largest =
            max(
                bitmap.width,
                bitmap.height
            )

        if (largest <= 320) {

            bitmap

        } else {

            val scale =
                320f /
                        largest
                            .toFloat()

            val width =
                (
                        bitmap.width *
                                scale
                        )
                    .roundToInt()
                    .coerceAtLeast(1)

            val height =
                (
                        bitmap.height *
                                scale
                        )
                    .roundToInt()
                    .coerceAtLeast(1)

            val scaled =
                Bitmap
                    .createScaledBitmap(
                        bitmap,
                        width,
                        height,
                        true
                    )

            if (
                scaled !== bitmap
            ) {

                bitmap.recycle()
            }

            scaled
        }

    } finally {

        try {

            retriever.release()

        } catch (
            _: Exception
        ) {
        }
    }
}

fun renderPdfThumbnail(
    absolutePath: String,
    maxWidth: Int
): Bitmap? {

    val file =
        File(
            absolutePath
        )

    if (
        !file.exists() ||
        !file.isFile
    ) {

        return null
    }

    val descriptor =
        ParcelFileDescriptor
            .open(
                file,
                ParcelFileDescriptor
                    .MODE_READ_ONLY
            )

    try {

        val renderer =
            PdfRenderer(
                descriptor
            )

        try {

            if (
                renderer.pageCount <= 0
            ) {

                return null
            }

            val page =
                renderer.openPage(0)

            try {

                val targetWidth =
                    minOf(
                        maxWidth,
                        page.width
                            .coerceAtLeast(1)
                    )

                val scale =
                    targetWidth
                        .toFloat() /
                            page.width
                                .toFloat()

                val targetHeight =
                    (
                            page.height *
                                    scale
                            )
                        .roundToInt()
                        .coerceAtLeast(1)

                val bitmap =
                    Bitmap
                        .createBitmap(
                            targetWidth,
                            targetHeight,
                            Bitmap.Config
                                .ARGB_8888
                        )

                bitmap.eraseColor(
                    android.graphics
                        .Color.WHITE
                )

                page.render(
                    bitmap,
                    null,
                    null,
                    PdfRenderer.Page
                        .RENDER_MODE_FOR_DISPLAY
                )

                return bitmap

            } finally {

                page.close()
            }

        } finally {

            renderer.close()
        }

    } finally {

        descriptor.close()
    }
}

fun readTextThumbnailPreview(
    absolutePath: String
): String {

    val file =
        File(
            absolutePath
        )

    if (
        !file.exists() ||
        !file.isFile
    ) {

        return ""
    }

    return file
        .bufferedReader()
        .use {
                reader ->

            val buffer =
                CharArray(420)

            val read =
                reader.read(
                    buffer
                )

            if (
                read <= 0
            ) {

                ""

            } else {

                String(
                    buffer,
                    0,
                    read
                )
                    .replace(
                        "\\t",
                        " "
                    )
                    .trim()
            }
        }
}

fun trashKindBadge(
    kind: TrashKind
): String {

    return when (kind) {
        TrashKind.PHOTO -> "IMG"
        TrashKind.VIDEO -> "VID"
        TrashKind.AUDIO -> "AUD"
        TrashKind.FILE -> "FILE"
        TrashKind.APP -> "APP"
    }
}

fun trashKindLabel(
    kind: TrashKind
): String {

    return when (kind) {
        TrashKind.PHOTO -> "Photo"
        TrashKind.VIDEO -> "Video"
        TrashKind.AUDIO -> "Audio"
        TrashKind.FILE -> "File"
        TrashKind.APP -> "App"
    }
}

fun trashFileIdentity(
    absolutePath: String
): String {
    return "file:" + everythingCanonicalPath(absolutePath)
}

fun PhotoItem.toTrashItem(): TrashItem {

    return TrashItem(
        key = "media:" + uri.toString(),
        kind = TrashKind.PHOTO,
        name = name,
        sizeBytes = size,
        photo = this
    )
}

fun VideoItem.toTrashItem(): TrashItem {

    return TrashItem(
        key = "media:" + uri.toString(),
        kind = TrashKind.VIDEO,
        name = name,
        sizeBytes = size,
        video = this
    )
}

fun AudioItem.toTrashItem(): TrashItem {

    return TrashItem(
        key = "media:" + uri.toString(),
        kind = TrashKind.AUDIO,
        name = name,
        sizeBytes = size,
        audio = this
    )
}

fun FileItem.toTrashItem(): TrashItem {

    return TrashItem(
        key = trashFileIdentity(absolutePath),
        kind = TrashKind.FILE,
        name = name,
        sizeBytes = size,
        file = this
    )
}

fun AppItem.toTrashItem(): TrashItem {

    return TrashItem(
        key = "app:$packageName",
        kind = TrashKind.APP,
        name = name,
        sizeBytes = sizeBytes,
        app = this
    )
}

fun EverythingItem.toTrashItem(): TrashItem {

    return when (kind) {
        EverythingKind.PHOTO ->
            photo!!.toTrashItem()

        EverythingKind.VIDEO ->
            video!!.toTrashItem()

        EverythingKind.AUDIO ->
            audio!!.toTrashItem()

        EverythingKind.FILE ->
            file!!.toTrashItem()

        EverythingKind.APP ->
            app!!.toTrashItem()
    }
}

fun trashItemIsMediaLike(
    item: TrashItem
): Boolean {

    return when (item.kind) {

        TrashKind.PHOTO,
        TrashKind.VIDEO,
        TrashKind.AUDIO ->
            true

        TrashKind.FILE -> {

            val file =
                item.file
                    ?: return false

            when (
                inAppPreviewType(
                    file
                )
            ) {

                InAppPreviewType.IMAGE,
                InAppPreviewType.VIDEO,
                InAppPreviewType.AUDIO ->
                    true

                else ->
                    false
            }
        }

        TrashKind.APP ->
            false
    }
}

fun isMarkedForTrash(
    candidate: TrashItem,
    trashedItems:
    List<TrashItem>
): Boolean {

    if (trashedItems.isEmpty()) {
        return false
    }

    if (
        trashedItems.any {
            it.key == candidate.key
        }
    ) {
        return true
    }

    if (candidate.kind == TrashKind.APP) {

        val packageName =
            candidate.app
                ?.packageName
                ?: return false

        return trashedItems.any {
                trashed ->

            trashed.kind ==
                    TrashKind.APP &&
                    trashed.app
                        ?.packageName ==
                    packageName
        }
    }

    val candidatePath =
        candidate.file
            ?.absolutePath
            ?.takeIf {
                it.isNotBlank()
            }
            ?.let {
                everythingCanonicalPath(it)
            }

    if (candidatePath != null) {

        val samePath =
            trashedItems.any {
                    trashed ->

                trashed.file
                    ?.absolutePath
                    ?.takeIf {
                        it.isNotBlank()
                    }
                    ?.let {
                        everythingCanonicalPath(it)
                    } ==
                        candidatePath
            }

        if (samePath) {
            return true
        }
    }

    if (
        !trashItemIsMediaLike(candidate) ||
        candidate.sizeBytes <= 0L
    ) {
        return false
    }

    val mediaKey =
        everythingMediaDedupKey(
            candidate.name,
            candidate.sizeBytes
        )

    return trashedItems.any {
            trashed ->

        trashItemIsMediaLike(trashed) &&
                trashed.sizeBytes > 0L &&
                everythingMediaDedupKey(
                    trashed.name,
                    trashed.sizeBytes
                ) ==
                mediaKey
    }
}

@Composable
fun SettingsScreen(
    themeMode: AppThemeMode,
    onThemeModeChange:
        (AppThemeMode) -> Unit,
    onBack: () -> Unit
) {

    val context =
        LocalContext.current

    BackHandler {
        onBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(
                rememberScrollState()
            )
            .statusBarsPadding()
            .padding(22.dp)
    ) {

        Row(
            modifier =
                Modifier.fillMaxWidth(),
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Button(
                onClick = onBack
            ) {

                Text("← Back")
            }

            Spacer(
                modifier =
                    Modifier.width(14.dp)
            )

            Text(
                text = "Settings",
                fontSize = 30.sp,
                fontWeight =
                    FontWeight.Bold
            )
        }

        Spacer(
            modifier =
                Modifier.height(28.dp)
        )

        Text(
            text = "Appearance",
            fontSize = 22.sp,
            fontWeight =
                FontWeight.Bold
        )

        Spacer(
            modifier =
                Modifier.height(6.dp)
        )

        Text(
            text =
                "Choose how SwipeClean looks. Your choice is saved.",
            fontSize = 15.sp,
            color =
                MaterialTheme
                    .colorScheme
                    .onSurfaceVariant
        )

        Spacer(
            modifier =
                Modifier.height(16.dp)
        )

        Row(
            modifier =
                Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.spacedBy(
                    12.dp
                )
        ) {

            if (
                themeMode ==
                AppThemeMode.LIGHT
            ) {

                Button(
                    onClick = {
                        onThemeModeChange(
                            AppThemeMode.LIGHT
                        )
                    },
                    modifier =
                        Modifier.weight(1f)
                ) {

                    Text("☀ Light")
                }

            } else {

                OutlinedButton(
                    onClick = {
                        onThemeModeChange(
                            AppThemeMode.LIGHT
                        )
                    },
                    modifier =
                        Modifier.weight(1f)
                ) {

                    Text("☀ Light")
                }
            }

            if (
                themeMode ==
                AppThemeMode.DARK
            ) {

                Button(
                    onClick = {
                        onThemeModeChange(
                            AppThemeMode.DARK
                        )
                    },
                    modifier =
                        Modifier.weight(1f)
                ) {

                    Text("☾ Dark")
                }

            } else {

                OutlinedButton(
                    onClick = {
                        onThemeModeChange(
                            AppThemeMode.DARK
                        )
                    },
                    modifier =
                        Modifier.weight(1f)
                ) {

                    Text("☾ Dark")
                }
            }
        }

        Spacer(
            modifier =
                Modifier.height(34.dp)
        )

        Text(
            text = "Feedback",
            fontSize = 22.sp,
            fontWeight =
                FontWeight.Bold
        )

        Spacer(
            modifier =
                Modifier.height(6.dp)
        )

        Text(
            text =
                "Found a bug or have an idea for SwipeClean? Send feedback directly to Gl!tch Studios.",
            fontSize = 15.sp,
            color =
                MaterialTheme
                    .colorScheme
                    .onSurfaceVariant
        )

        Spacer(
            modifier =
                Modifier.height(16.dp)
        )

        Button(
            onClick = {

                sendSwipeCleanFeedback(
                    context
                )
            },
            modifier =
                Modifier.fillMaxWidth()
        ) {

            Text(
                "Send Feedback"
            )
        }

        Spacer(
            modifier =
                Modifier.height(34.dp)
        )

        Text(
            text = "About",
            fontSize = 22.sp,
            fontWeight =
                FontWeight.Bold
        )

        Spacer(
            modifier =
                Modifier.height(12.dp)
        )

        Card(
            modifier =
                Modifier.fillMaxWidth(),
            shape =
                RoundedCornerShape(
                    20.dp
                )
        ) {

            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
            ) {

                Text(
                    text = "SwipeClean",
                    fontSize = 24.sp,
                    fontWeight =
                        FontWeight.Bold
                )

                Spacer(
                    modifier =
                        Modifier.height(8.dp)
                )

                Text(
                    text =
                        "A swipe-based storage cleanup app.",
                    fontSize = 15.sp
                )

                Spacer(
                    modifier =
                        Modifier.height(20.dp)
                )

                Text(
                    text =
                        "© 2026 Gl!tch Studios",
                    modifier =
                        Modifier.fillMaxWidth(),
                    fontSize = 14.sp,
                    fontWeight =
                        FontWeight.Bold,
                    textAlign =
                        TextAlign.Center
                )

                Spacer(
                    modifier =
                        Modifier.height(4.dp)
                )

                Text(
                    text =
                        "All rights reserved.",
                    modifier =
                        Modifier.fillMaxWidth(),
                    fontSize = 12.sp,
                    color =
                        MaterialTheme
                            .colorScheme
                            .onSurfaceVariant,
                    textAlign =
                        TextAlign.Center
                )
            }
        }

        Spacer(
            modifier =
                Modifier.height(24.dp)
        )
    }
}

fun resolveTrashMediaUri(
    context: Context,
    item: TrashItem
): Uri? {

    when (item.kind) {

        TrashKind.PHOTO -> {

            return item.photo
                ?.uri
        }

        TrashKind.VIDEO -> {

            return item.video
                ?.uri
        }

        TrashKind.AUDIO -> {

            return item.audio
                ?.uri
        }

        TrashKind.FILE -> {

            val file =
                item.file
                    ?: return null

            val previewType =
                inAppPreviewType(
                    file
                )

            val collection =
                when (previewType) {

                    InAppPreviewType.IMAGE ->
                        MediaStore
                            .Images
                            .Media
                            .EXTERNAL_CONTENT_URI

                    InAppPreviewType.VIDEO ->
                        MediaStore
                            .Video
                            .Media
                            .EXTERNAL_CONTENT_URI

                    InAppPreviewType.AUDIO ->
                        MediaStore
                            .Audio
                            .Media
                            .EXTERNAL_CONTENT_URI

                    else ->
                        return null
                }

            try {

                context
                    .contentResolver
                    .query(
                        collection,
                        arrayOf(
                            MediaStore
                                .MediaColumns
                                ._ID
                        ),
                        MediaStore
                            .MediaColumns
                            .DATA +
                                " = ?",
                        arrayOf(
                            file.absolutePath
                        ),
                        null
                    )
                    ?.use {
                            cursor ->

                        if (
                            cursor.moveToFirst()
                        ) {

                            val id =
                                cursor.getLong(
                                    0
                                )

                            return ContentUris
                                .withAppendedId(
                                    collection,
                                    id
                                )
                        }
                    }

            } catch (
                _: Exception
            ) {
            }

            try {

                context
                    .contentResolver
                    .query(
                        collection,
                        arrayOf(
                            MediaStore
                                .MediaColumns
                                ._ID
                        ),
                        MediaStore
                            .MediaColumns
                            .DISPLAY_NAME +
                                " = ? AND " +
                                MediaStore
                                    .MediaColumns
                                    .SIZE +
                                " = ?",
                        arrayOf(
                            file.name,
                            file.size
                                .toString()
                        ),
                        MediaStore
                            .MediaColumns
                            .DATE_MODIFIED +
                                " DESC"
                    )
                    ?.use {
                            cursor ->

                        if (
                            cursor.moveToFirst()
                        ) {

                            val id =
                                cursor.getLong(
                                    0
                                )

                            return ContentUris
                                .withAppendedId(
                                    collection,
                                    id
                                )
                        }
                    }

            } catch (
                _: Exception
            ) {
            }

            return null
        }

        TrashKind.APP ->
            return null
    }
}

fun trashItemActuallyGone(
    context: Context,
    item: TrashItem
): Boolean {

    return when (item.kind) {

        TrashKind.PHOTO,
        TrashKind.VIDEO,
        TrashKind.AUDIO -> {

            val uri =
                resolveTrashMediaUri(
                    context,
                    item
                )
                    ?: return false

            !contentUriExists(
                context,
                uri
            )
        }

        TrashKind.FILE -> {

            val fileItem =
                item.file
                    ?: return false

            val physicalFileGone =
                !File(
                    fileItem.absolutePath
                )
                    .exists()

            val mediaUri =
                resolveTrashMediaUri(
                    context,
                    item
                )

            val mediaRowGone =
                if (
                    mediaUri != null
                ) {

                    !contentUriExists(
                        context,
                        mediaUri
                    )

                } else {

                    true
                }

            physicalFileGone &&
                    mediaRowGone
        }

        TrashKind.APP -> {

            val packageName =
                item.app
                    ?.packageName
                    ?: return false

            !isPackageInstalled(
                context,
                packageName
            )
        }
    }
}


fun deleteSharedFileItem(
    context: Context,
    item: TrashItem
): Boolean {

    val fileItem =
        item.file
            ?: return false

    val file =
        File(
            fileItem.absolutePath
        )

    if (!file.exists()) {
        return true
    }

    return try {

        val deleted =
            file.delete()

        if (deleted) {

            try {

                android.media
                    .MediaScannerConnection
                    .scanFile(
                        context,
                        arrayOf(
                            fileItem.absolutePath
                        ),
                        null,
                        null
                    )

            } catch (
                _: Exception
            ) {
            }

            !file.exists()

        } else if (
            fileItem.uri.scheme ==
            "content"
        ) {

            val resolverDeleted =
                context
                    .contentResolver
                    .delete(
                        fileItem.uri,
                        null,
                        null
                    ) > 0

            resolverDeleted ||
                    !file.exists()

        } else {

            false
        }

    } catch (
        _: Exception
    ) {

        false
    }
}

fun contentUriExists(
    context: Context,
    uri: Uri
): Boolean {
    return try {
        context.contentResolver.query(
            uri,
            arrayOf(MediaStore.MediaColumns._ID),
            null,
            null,
            null
        )?.use { it.moveToFirst() } ?: false
    } catch (_: Exception) {
        false
    }
}

fun isPackageInstalled(
    context: Context,
    packageName: String
): Boolean {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                packageName,
                PackageManager.PackageInfoFlags.of(0L)
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(packageName, 0)
        }
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    } catch (_: Exception) {
        true
    }
}

fun deletionPhaseText(phase: DeletePhase): String {
    return when (phase) {
        DeletePhase.DELETE_FILES -> "Deleting shared files..."
        DeletePhase.DELETE_MEDIA -> "Preparing media deletion..."
        DeletePhase.WAIT_MEDIA -> "Waiting for Android media confirmation..."
        DeletePhase.WAIT_LEGACY_MEDIA -> "Waiting for Android media permission..."
        DeletePhase.UNINSTALL_APPS -> "Preparing app uninstall..."
        DeletePhase.WAIT_APP -> "Waiting for Android app uninstall confirmation..."
        DeletePhase.FINISHED -> "Finishing..."
        DeletePhase.IDLE -> ""
    }
}

private const val TRASH_PREFERENCES_NAME =
    "swipeclean_trash"

private const val TRASH_PREFERENCES_KEY =
    "trash_scopes_json"

fun saveTrashScopes(
    context: Context,
    trashScopes:
    Map<String, List<TrashItem>>
) {

    try {

        val scopesArray =
            JSONArray()

        trashScopes
            .forEach {
                    (scope, items) ->

                val scopeObject =
                    JSONObject()

                scopeObject.put(
                    "scope",
                    scope
                )

                val itemsArray =
                    JSONArray()

                items.forEach {
                        item ->

                    itemsArray.put(
                        trashItemToJson(
                            item
                        )
                    )
                }

                scopeObject.put(
                    "items",
                    itemsArray
                )

                scopesArray.put(
                    scopeObject
                )
            }

        context
            .getSharedPreferences(
                TRASH_PREFERENCES_NAME,
                Context.MODE_PRIVATE
            )
            .edit()
            .putString(
                TRASH_PREFERENCES_KEY,
                scopesArray.toString()
            )
            .apply()

    } catch (
        _: Exception
    ) {
    }
}

fun loadTrashScopes(
    context: Context
): Map<String, List<TrashItem>> {

    val savedJson =
        context
            .getSharedPreferences(
                TRASH_PREFERENCES_NAME,
                Context.MODE_PRIVATE
            )
            .getString(
                TRASH_PREFERENCES_KEY,
                null
            )
            ?: return emptyMap()

    return try {

        val scopesArray =
            JSONArray(
                savedJson
            )

        val result =
            linkedMapOf<
                    String,
                    List<TrashItem>
                    >()

        for (
        scopeIndex in
        0 until scopesArray.length()
        ) {

            val scopeObject =
                scopesArray
                    .optJSONObject(
                        scopeIndex
                    )
                    ?: continue

            val scope =
                scopeObject
                    .optString(
                        "scope",
                        ""
                    )
                    .trim()

            if (
                scope.isBlank()
            ) {
                continue
            }

            val itemsArray =
                scopeObject
                    .optJSONArray(
                        "items"
                    )
                    ?: JSONArray()

            val items =
                mutableListOf<
                        TrashItem
                        >()

            for (
            itemIndex in
            0 until itemsArray.length()
            ) {

                val itemObject =
                    itemsArray
                        .optJSONObject(
                            itemIndex
                        )
                        ?: continue

                trashItemFromJson(
                    itemObject
                )
                    ?.let {
                        items.add(
                            it
                        )
                    }
            }

            if (
                items.isNotEmpty()
            ) {

                result[scope] =
                    items
                        .distinctBy {
                            it.key
                        }
            }
        }

        result

    } catch (
        _: Exception
    ) {

        emptyMap()
    }
}

fun trashItemToJson(
    item: TrashItem
): JSONObject {

    val json =
        JSONObject()

    json.put(
        "key",
        item.key
    )

    json.put(
        "kind",
        item.kind.name
    )

    json.put(
        "name",
        item.name
    )

    json.put(
        "sizeBytes",
        item.sizeBytes
    )

    when (
        item.kind
    ) {

        TrashKind.PHOTO -> {

            val photo =
                item.photo

            if (
                photo != null
            ) {

                json.put(
                    "id",
                    photo.id
                )

                json.put(
                    "uri",
                    photo.uri.toString()
                )

                json.put(
                    "dateAdded",
                    photo.dateAdded
                )
            }
        }

        TrashKind.VIDEO -> {

            val video =
                item.video

            if (
                video != null
            ) {

                json.put(
                    "id",
                    video.id
                )

                json.put(
                    "uri",
                    video.uri.toString()
                )

                json.put(
                    "dateAdded",
                    video.dateAdded
                )

                json.put(
                    "durationMs",
                    video.durationMs
                )
            }
        }

        TrashKind.AUDIO -> {

            val audio =
                item.audio

            if (
                audio != null
            ) {

                json.put(
                    "id",
                    audio.id
                )

                json.put(
                    "uri",
                    audio.uri.toString()
                )

                json.put(
                    "dateAdded",
                    audio.dateAdded
                )

                json.put(
                    "durationMs",
                    audio.durationMs
                )

                putNullableJsonString(
                    json =
                        json,
                    key =
                        "mimeType",
                    value =
                        audio.mimeType
                )
            }
        }

        TrashKind.FILE -> {

            val file =
                item.file

            if (
                file != null
            ) {

                json.put(
                    "id",
                    file.id
                )

                json.put(
                    "uri",
                    file.uri.toString()
                )

                json.put(
                    "dateAdded",
                    file.dateAdded
                )

                putNullableJsonString(
                    json =
                        json,
                    key =
                        "mimeType",
                    value =
                        file.mimeType
                )

                putNullableJsonString(
                    json =
                        json,
                    key =
                        "relativePath",
                    value =
                        file.relativePath
                )

                json.put(
                    "extension",
                    file.extension
                )

                json.put(
                    "absolutePath",
                    file.absolutePath
                )
            }
        }

        TrashKind.APP -> {

            val app =
                item.app

            if (
                app != null
            ) {

                json.put(
                    "packageName",
                    app.packageName
                )

                json.put(
                    "versionName",
                    app.versionName
                )

                json.put(
                    "firstInstallTime",
                    app.firstInstallTime
                )

                json.put(
                    "lastUsedTime",
                    app.lastUsedTime
                )
            }
        }
    }

    return json
}

fun trashItemFromJson(
    json: JSONObject
): TrashItem? {

    return try {

        val key =
            json.getString(
                "key"
            )

        val kind =
            TrashKind.valueOf(
                json.getString(
                    "kind"
                )
            )

        val name =
            json.optString(
                "name",
                "Unknown"
            )

        val sizeBytes =
            json.optLong(
                "sizeBytes",
                0L
            )

        when (
            kind
        ) {

            TrashKind.PHOTO -> {

                val uri =
                    Uri.parse(
                        json.getString(
                            "uri"
                        )
                    )

                val photo =
                    PhotoItem(
                        id =
                            json.optLong(
                                "id",
                                0L
                            ),
                        uri =
                            uri,
                        name =
                            name,
                        size =
                            sizeBytes,
                        dateAdded =
                            json.optLong(
                                "dateAdded",
                                0L
                            )
                    )

                TrashItem(
                    key =
                        key,
                    kind =
                        kind,
                    name =
                        name,
                    sizeBytes =
                        sizeBytes,
                    photo =
                        photo
                )
            }

            TrashKind.VIDEO -> {

                val uri =
                    Uri.parse(
                        json.getString(
                            "uri"
                        )
                    )

                val video =
                    VideoItem(
                        id =
                            json.optLong(
                                "id",
                                0L
                            ),
                        uri =
                            uri,
                        name =
                            name,
                        size =
                            sizeBytes,
                        dateAdded =
                            json.optLong(
                                "dateAdded",
                                0L
                            ),
                        durationMs =
                            json.optLong(
                                "durationMs",
                                0L
                            )
                    )

                TrashItem(
                    key =
                        key,
                    kind =
                        kind,
                    name =
                        name,
                    sizeBytes =
                        sizeBytes,
                    video =
                        video
                )
            }

            TrashKind.AUDIO -> {

                val uri =
                    Uri.parse(
                        json.getString(
                            "uri"
                        )
                    )

                val audio =
                    AudioItem(
                        id =
                            json.optLong(
                                "id",
                                0L
                            ),
                        uri =
                            uri,
                        name =
                            name,
                        size =
                            sizeBytes,
                        dateAdded =
                            json.optLong(
                                "dateAdded",
                                0L
                            ),
                        durationMs =
                            json.optLong(
                                "durationMs",
                                0L
                            ),
                        mimeType =
                            optionalJsonString(
                                json =
                                    json,
                                key =
                                    "mimeType"
                            )
                    )

                TrashItem(
                    key =
                        key,
                    kind =
                        kind,
                    name =
                        name,
                    sizeBytes =
                        sizeBytes,
                    audio =
                        audio
                )
            }

            TrashKind.FILE -> {

                val uri =
                    Uri.parse(
                        json.getString(
                            "uri"
                        )
                    )

                val file =
                    FileItem(
                        id =
                            json.optLong(
                                "id",
                                0L
                            ),
                        uri =
                            uri,
                        name =
                            name,
                        size =
                            sizeBytes,
                        dateAdded =
                            json.optLong(
                                "dateAdded",
                                0L
                            ),
                        mimeType =
                            optionalJsonString(
                                json =
                                    json,
                                key =
                                    "mimeType"
                            ),
                        relativePath =
                            optionalJsonString(
                                json =
                                    json,
                                key =
                                    "relativePath"
                            ),
                        extension =
                            json.optString(
                                "extension",
                                ""
                            ),
                        absolutePath =
                            json.optString(
                                "absolutePath",
                                ""
                            )
                    )

                TrashItem(
                    key =
                        key,
                    kind =
                        kind,
                    name =
                        name,
                    sizeBytes =
                        sizeBytes,
                    file =
                        file
                )
            }

            TrashKind.APP -> {

                val app =
                    AppItem(
                        packageName =
                            json.getString(
                                "packageName"
                            ),
                        name =
                            name,
                        versionName =
                            json.optString(
                                "versionName",
                                ""
                            ),
                        sizeBytes =
                            sizeBytes,
                        firstInstallTime =
                            json.optLong(
                                "firstInstallTime",
                                0L
                            ),
                        lastUsedTime =
                            json.optLong(
                                "lastUsedTime",
                                0L
                            )
                    )

                TrashItem(
                    key =
                        key,
                    kind =
                        kind,
                    name =
                        name,
                    sizeBytes =
                        sizeBytes,
                    app =
                        app
                )
            }
        }

    } catch (
        _: Exception
    ) {

        null
    }
}

fun putNullableJsonString(
    json: JSONObject,
    key: String,
    value: String?
) {

    if (
        value == null
    ) {

        json.put(
            key,
            JSONObject.NULL
        )

    } else {

        json.put(
            key,
            value
        )
    }
}

fun optionalJsonString(
    json: JSONObject,
    key: String
): String? {

    if (
        !json.has(
            key
        ) ||
        json.isNull(
            key
        )
    ) {

        return null
    }

    return json.optString(
        key,
        null
    )
}

fun loadThemeMode(
    context: Context
): AppThemeMode {

    val preferences =
        context
            .getSharedPreferences(
                "swipeclean_settings",
                Context.MODE_PRIVATE
            )

    return when (
        preferences
            .getString(
                "theme_mode",
                AppThemeMode.DARK.name
            )
    ) {

        AppThemeMode.LIGHT.name ->
            AppThemeMode.LIGHT

        else ->
            AppThemeMode.DARK
    }
}

fun saveThemeMode(
    context: Context,
    themeMode: AppThemeMode
) {

    context
        .getSharedPreferences(
            "swipeclean_settings",
            Context.MODE_PRIVATE
        )
        .edit()
        .putString(
            "theme_mode",
            themeMode.name
        )
        .apply()
}

fun sendSwipeCleanFeedback(
    context: Context
) {

    val recipient =
        "Ethanwatch2009@gmail.com"

    val subject =
        Uri.encode(
            "SwipeClean Feedback"
        )

    val body =
        Uri.encode(
            "SwipeClean feedback:\\n\\n"
        )

    val intent =
        Intent(
            Intent.ACTION_SENDTO
        ).apply {

            data =
                Uri.parse(
                    "mailto:$recipient" +
                            "?subject=$subject" +
                            "&body=$body"
                )
        }

    try {

        context.startActivity(
            intent
        )

    } catch (
        _: ActivityNotFoundException
    ) {

        Toast.makeText(
            context,
            "No email app is available.",
            Toast.LENGTH_SHORT
        ).show()
    }
}

@Composable
fun PlaceholderCategoryScreen(
    title: String,
    onBack: () -> Unit
) {

    BackHandler {
        onBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            text = title,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(
            modifier = Modifier.height(18.dp)
        )

        Text(
            text = "This category is on the home screen now, but its real scanner has not been connected yet.",
            fontSize = 18.sp,
            textAlign = TextAlign.Center
        )

        Spacer(
            modifier = Modifier.height(12.dp)
        )

        Text(
            text = "Photos, Videos, Screenshots, Audio, Downloads, Documents, Other Files, and Apps are working in this build.",
            fontSize = 15.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )

        Spacer(
            modifier = Modifier.height(28.dp)
        )

        Button(
            onClick = onBack
        ) {
            Text("Back to Categories")
        }
    }
}

@Composable
fun PhotoSwipeScreen(
    onBack: () -> Unit,
    trashedItems:
    List<TrashItem>,
    onTrashDelta:
        (String, List<TrashItem>, Set<String>) -> Unit
) {

    val context = LocalContext.current

    var permissionGranted by remember {
        mutableStateOf(hasPhotoPermission(context))
    }

    var isLoading by remember {
        mutableStateOf(false)
    }

    var scanError by remember {
        mutableStateOf<String?>(null)
    }

    var remainingPhotos by remember {
        mutableStateOf<List<PhotoItem>>(emptyList())
    }

    var keptPhotos by remember {
        mutableStateOf<List<PhotoItem>>(emptyList())
    }

    var removalPhotos by remember {
        mutableStateOf<List<PhotoItem>>(emptyList())
    }

    var previousTrashPhotoKeys by remember {
        mutableStateOf<Set<String>>(
            emptySet()
        )
    }

    LaunchedEffect(removalPhotos) {

        val currentTrashItems =
            removalPhotos.map {
                it.toTrashItem()
            }

        val currentKeys =
            currentTrashItems
                .map {
                    it.key
                }
                .toSet()

        val addedItems =
            currentTrashItems
                .filter {
                    it.key !in previousTrashPhotoKeys
                }

        val removedKeys =
            previousTrashPhotoKeys -
                    currentKeys

        if (
            addedItems.isNotEmpty() ||
            removedKeys.isNotEmpty()
        ) {

            onTrashDelta(
                "photos",
                addedItems,
                removedKeys
            )
        }

        previousTrashPhotoKeys =
            currentKeys
    }

    var history by remember {
        mutableStateOf<List<Decision>>(emptyList())
    }

    var sortMode by remember {
        mutableStateOf(SortMode.LARGEST_FIRST)
    }

    var sortMenuOpen by remember {
        mutableStateOf(false)
    }

    var offsetX by remember {
        mutableFloatStateOf(0f)
    }

    var suppressPreviewTapUntil by remember {
        mutableLongStateOf(0L)
    }

    var isFullscreen by remember {
        mutableStateOf(false)
    }

    BackHandler {
        if (isFullscreen) {
            isFullscreen = false
        } else {
            onBack()
        }
    }

    var scanVersion by remember {
        mutableIntStateOf(0)
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->
            permissionGranted = granted
        }

    LaunchedEffect(permissionGranted, scanVersion) {

        if (permissionGranted) {

            isLoading = true
            scanError = null

            try {

                val loadedPhotos = withContext(Dispatchers.IO) {
                    loadPhotos(context)
                }

                val reviewablePhotos =
                    loadedPhotos.filterNot {
                        isMarkedForTrash(
                            candidate =
                                it.toTrashItem(),
                            trashedItems =
                                trashedItems
                        )
                    }

                remainingPhotos = sortPhotos(
                    reviewablePhotos,
                    sortMode
                )

                keptPhotos = emptyList()

                previousTrashPhotoKeys =
                    emptySet()

                removalPhotos = emptyList()
                history = emptyList()
                offsetX = 0f

            } catch (exception: Exception) {

                scanError =
                    exception.message ?: "Unknown scanning error"

            } finally {

                isLoading = false
            }
        }
    }

    if (!permissionGranted) {

        PermissionScreen(
            onBack = onBack,
            onRequestPermission = {

                val permission =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        Manifest.permission.READ_MEDIA_IMAGES
                    } else {
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    }

                permissionLauncher.launch(permission)
            }
        )

        return
    }

    if (isLoading) {

        LoadingScreen(
            onBack = onBack
        )

        return
    }

    if (scanError != null) {

        ErrorScreen(
            message = scanError ?: "Unknown error",
            onBack = onBack,
            onRetry = {
                scanVersion++
            }
        )

        return
    }

    val totalPhotos =
        remainingPhotos.size +
                keptPhotos.size +
                removalPhotos.size

    val reviewedPhotos =
        keptPhotos.size +
                removalPhotos.size

    if (remainingPhotos.isEmpty()) {

        FinishedScreen(
            totalPhotos = totalPhotos,
            keptCount = keptPhotos.size,
            removalCount = removalPhotos.size,
            removalBytes = removalPhotos.sumOf { it.size },
            onBack = onBack,
            onRestart = {

                remainingPhotos = sortPhotos(
                    keptPhotos,
                    sortMode
                )

                keptPhotos = emptyList()

                previousTrashPhotoKeys =
                    emptySet()

                removalPhotos = emptyList()
                history = emptyList()
                offsetX = 0f
            },
            onRescan = {
                scanVersion++
            }
        )

        return
    }

    val currentPhoto = remainingPhotos.first()

    if (isFullscreen) {

        FullscreenPhotoViewer(
            context = context,
            photo = currentPhoto,
            positionText = "${reviewedPhotos + 1} / $totalPhotos",
            canUndo = history.isNotEmpty(),
            onClose = {
                isFullscreen = false
            },
            onUndo = {

                if (history.isNotEmpty()) {

                    val lastDecision = history.last()
                    history = history.dropLast(1)

                    if (lastDecision.kept) {
                        keptPhotos = keptPhotos.dropLast(1)
                    } else {
                        removalPhotos = removalPhotos.dropLast(1)
                    }

                    remainingPhotos =
                        listOf(lastDecision.photo) + remainingPhotos

                    offsetX = 0f
                }
            },
            onKeep = {

                keptPhotos =
                    keptPhotos + currentPhoto

                history =
                    history + Decision(
                        photo = currentPhoto,
                        kept = true
                    )

                remainingPhotos =
                    remainingPhotos.drop(1)

                offsetX = 0f
            },
            onRemove = {

                removalPhotos =
                    removalPhotos + currentPhoto

                history =
                    history + Decision(
                        photo = currentPhoto,
                        kept = false
                    )

                remainingPhotos =
                    remainingPhotos.drop(1)

                offsetX = 0f
            }
        )

        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(
                start = 18.dp,
                end = 18.dp,
                top = 54.dp,
                bottom = 2.dp
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Button(
                onClick = {
                    onBack()
                },
                modifier = Modifier.height(48.dp)
            ) {
                Text("← Categories")
            }

            Spacer(
                modifier = Modifier.width(14.dp)
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {

                Text(
                    text = "Photos",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "${reviewedPhotos + 1} / $totalPhotos",
                    fontSize = 14.sp
                )
            }
        }

        Spacer(
            modifier = Modifier.height(10.dp)
        )

        Box {

            OutlinedButton(
                onClick = {
                    sortMenuOpen = true
                }
            ) {

                Text(
                    text = sortMode.label
                )
            }

            DropdownMenu(
                expanded = sortMenuOpen,
                onDismissRequest = {
                    sortMenuOpen = false
                }
            ) {

                DropdownMenuItem(
                    text = {
                        Text("Largest → Smallest")
                    },
                    onClick = {

                        sortMode = SortMode.LARGEST_FIRST

                        remainingPhotos = sortPhotos(
                            remainingPhotos,
                            sortMode
                        )

                        offsetX = 0f
                        sortMenuOpen = false
                    }
                )

                DropdownMenuItem(
                    text = {
                        Text("Smallest → Largest")
                    },
                    onClick = {

                        sortMode = SortMode.SMALLEST_FIRST

                        remainingPhotos = sortPhotos(
                            remainingPhotos,
                            sortMode
                        )

                        offsetX = 0f
                        sortMenuOpen = false
                    }
                )

                DropdownMenuItem(
                    text = {
                        Text("Date Added")
                    },
                    onClick = {

                        sortMode = SortMode.DATE_ADDED

                        remainingPhotos = sortPhotos(
                            remainingPhotos,
                            sortMode
                        )

                        offsetX = 0f
                        sortMenuOpen = false
                    }
                )
            }
        }

        Spacer(
            modifier = Modifier.height(10.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(475.dp)
                .offset {
                    IntOffset(
                        x = offsetX.roundToInt(),
                        y = 0
                    )
                }
                .pointerInput(currentPhoto.id) {

                    detectDragGestures(

                        onDrag = { change, dragAmount ->

                            change.consume()
                            offsetX += dragAmount.x

                            if (
                                kotlin.math.abs(offsetX) > 12f
                            ) {
                                suppressPreviewTapUntil =
                                    System.currentTimeMillis() + 500L
                            }
                        },

                        onDragEnd = {

                            val threshold = 250f

                            if (offsetX > threshold) {

                                keptPhotos =
                                    keptPhotos + currentPhoto

                                history =
                                    history + Decision(
                                        photo = currentPhoto,
                                        kept = true
                                    )

                                remainingPhotos =
                                    remainingPhotos.drop(1)

                            } else if (offsetX < -threshold) {

                                removalPhotos =
                                    removalPhotos + currentPhoto

                                history =
                                    history + Decision(
                                        photo = currentPhoto,
                                        kept = false
                                    )

                                remainingPhotos =
                                    remainingPhotos.drop(1)
                            }

                            offsetX = 0f
                        }
                    )
                },
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 8.dp
            )
        ) {

            Column(
                modifier = Modifier.fillMaxSize()
            ) {

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp)
                        .background(Color.Black)
                        .clickable {

                            if (
                                System.currentTimeMillis() >=
                                suppressPreviewTapUntil
                            ) {
                                isFullscreen = true
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {

                    HighQualityPhotoPreview(
                        context = context,
                        photo = currentPhoto
                    )

                    if (offsetX < -100f) {

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Color.Red.copy(
                                        alpha = 0.28f
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {

                            Text(
                                text = "REMOVE",
                                fontSize = 38.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    if (offsetX > 100f) {

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Color.Green.copy(
                                        alpha = 0.25f
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {

                            Text(
                                text = "KEEP",
                                fontSize = 38.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {

                    Text(
                        text = currentPhoto.name,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(
                        modifier = Modifier.height(5.dp)
                    )

                    Text(
                        text = formatFileSize(
                            context,
                            currentPhoto.size
                        ),
                        fontSize = 17.sp
                    )
                }
            }
        }

        Spacer(
            modifier = Modifier.height(10.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {

            Text(
                text = "← REMOVE",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            OutlinedButton(
                enabled = history.isNotEmpty(),
                onClick = {

                    if (history.isNotEmpty()) {

                        val lastDecision =
                            history.last()

                        history =
                            history.dropLast(1)

                        if (lastDecision.kept) {

                            keptPhotos =
                                keptPhotos.dropLast(1)

                        } else {

                            removalPhotos =
                                removalPhotos.dropLast(1)
                        }

                        remainingPhotos =
                            listOf(lastDecision.photo) +
                                    remainingPhotos

                        offsetX = 0f
                    }
                }
            ) {

                Text("Undo")
            }

            Text(
                text = "KEEP →",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(
            modifier = Modifier.height(6.dp)
        )

        Text(
            text =
                "${removalPhotos.size} selected • " +
                        formatFileSize(
                            context,
                            removalPhotos.sumOf { it.size }
                        ) +
                        " marked",
            fontSize = 14.sp
        )

        Text(
            text = "Review mode — nothing will be deleted.",
            fontSize = 12.sp,
            color = Color.Gray
        )

        Spacer(
            modifier = Modifier.height(1.dp)
        )
    }
}

@Composable
fun EverythingSwipeScreen(
    onBack: () -> Unit,
    trashedItems:
    List<TrashItem>,
    onTrashDelta:
        (String, List<TrashItem>, Set<String>) -> Unit
) {

    val context =
        LocalContext.current

    var mediaAccessGranted by remember {
        mutableStateOf(
            hasEverythingMediaAccess(
                context
            )
        )
    }

    var deepFileAccessGranted by remember {
        mutableStateOf(
            hasDeepFileAccess(
                context
            )
        )
    }

    var usageAccessGranted by remember {
        mutableStateOf(
            hasUsageAccess(
                context
            )
        )
    }

    var isLoading by remember {
        mutableStateOf(false)
    }

    var scanError by remember {
        mutableStateOf<String?>(null)
    }

    var remainingItems by remember {
        mutableStateOf<List<EverythingItem>>(
            emptyList()
        )
    }

    var keptItems by remember {
        mutableStateOf<List<EverythingItem>>(
            emptyList()
        )
    }

    var removalItems by remember {
        mutableStateOf<List<EverythingItem>>(
            emptyList()
        )
    }

    var previousEverythingTrashKeys by remember {
        mutableStateOf<Set<String>>(
            emptySet()
        )
    }

    LaunchedEffect(removalItems) {

        val currentTrashItems =
            removalItems.map {
                it.toTrashItem()
            }

        val currentKeys =
            currentTrashItems
                .map {
                    it.key
                }
                .toSet()

        val addedItems =
            currentTrashItems
                .filter {
                    it.key !in previousEverythingTrashKeys
                }

        val removedKeys =
            previousEverythingTrashKeys -
                    currentKeys

        if (
            addedItems.isNotEmpty() ||
            removedKeys.isNotEmpty()
        ) {

            onTrashDelta(
                "everything",
                addedItems,
                removedKeys
            )
        }

        previousEverythingTrashKeys =
            currentKeys
    }

    var history by remember {
        mutableStateOf<List<EverythingDecision>>(
            emptyList()
        )
    }

    var sortMode by remember {
        mutableStateOf(
            EverythingSortMode
                .LARGEST_FIRST
        )
    }

    var sortMenuOpen by remember {
        mutableStateOf(false)
    }

    var offsetX by remember {
        mutableFloatStateOf(0f)
    }

    var suppressPreviewTapUntil by remember {
        mutableLongStateOf(0L)
    }

    var previewItem by remember {
        mutableStateOf<EverythingItem?>(null)
    }

    var scanVersion by remember {
        mutableIntStateOf(0)
    }

    BackHandler {

        if (previewItem != null) {

            previewItem = null

        } else {

            onBack()
        }
    }

    val mediaPermissionLauncher =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts
                    .RequestMultiplePermissions()
        ) {

            mediaAccessGranted =
                hasEverythingMediaAccess(
                    context
                )

            if (mediaAccessGranted) {
                scanVersion++
            }
        }

    val settingsLauncher =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts
                    .StartActivityForResult()
        ) {

            mediaAccessGranted =
                hasEverythingMediaAccess(
                    context
                )

            deepFileAccessGranted =
                hasDeepFileAccess(
                    context
                )

            usageAccessGranted =
                hasUsageAccess(
                    context
                )

            scanVersion++
        }

    LaunchedEffect(
        mediaAccessGranted,
        deepFileAccessGranted,
        usageAccessGranted,
        scanVersion
    ) {

        if (
            mediaAccessGranted &&
            deepFileAccessGranted
        ) {

            isLoading = true
            scanError = null

            try {

                val loadedItems =
                    withContext(
                        Dispatchers.IO
                    ) {

                        loadEverythingItems(
                            context = context,
                            usageAccessGranted =
                                usageAccessGranted
                        )
                    }

                val reviewableItems =
                    loadedItems.filterNot {
                        isMarkedForTrash(
                            candidate =
                                it.toTrashItem(),
                            trashedItems =
                                trashedItems
                        )
                    }

                remainingItems =
                    sortEverythingItems(
                        reviewableItems,
                        sortMode
                    )

                keptItems =
                    emptyList()

                previousEverythingTrashKeys =
                    emptySet()

                removalItems =
                    emptyList()

                history =
                    emptyList()

                previewItem = null
                offsetX = 0f

            } catch (
                exception: Exception
            ) {

                scanError =
                    exception.message
                        ?: "Unknown Everything scan error"

            } finally {

                isLoading = false
            }
        }
    }

    if (
        !mediaAccessGranted ||
        !deepFileAccessGranted
    ) {

        EverythingAccessScreen(
            mediaAccessGranted =
                mediaAccessGranted,
            deepFileAccessGranted =
                deepFileAccessGranted,
            usageAccessGranted =
                usageAccessGranted,
            onBack = onBack,
            onGrantMedia = {

                val permissions =
                    if (
                        Build.VERSION.SDK_INT >=
                        Build.VERSION_CODES.TIRAMISU
                    ) {

                        arrayOf(
                            Manifest.permission
                                .READ_MEDIA_IMAGES,
                            Manifest.permission
                                .READ_MEDIA_VIDEO,
                            Manifest.permission
                                .READ_MEDIA_AUDIO
                        )

                    } else {

                        arrayOf(
                            Manifest.permission
                                .READ_EXTERNAL_STORAGE
                        )
                    }

                mediaPermissionLauncher
                    .launch(
                        permissions
                    )
            },
            onGrantFiles = {

                if (
                    Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.R
                ) {

                    val appSettingsIntent =
                        Intent(
                            Settings
                                .ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse(
                                "package:${context.packageName}"
                            )
                        )

                    try {

                        settingsLauncher
                            .launch(
                                appSettingsIntent
                            )

                    } catch (
                        _: Exception
                    ) {

                        settingsLauncher
                            .launch(
                                Intent(
                                    Settings
                                        .ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                                )
                            )
                    }

                } else {

                    mediaPermissionLauncher
                        .launch(
                            arrayOf(
                                Manifest.permission
                                    .READ_EXTERNAL_STORAGE
                            )
                        )
                }
            },
            onGrantUsage = {

                settingsLauncher
                    .launch(
                        Intent(
                            Settings
                                .ACTION_USAGE_ACCESS_SETTINGS
                        )
                    )
            },
            onCheckAgain = {

                mediaAccessGranted =
                    hasEverythingMediaAccess(
                        context
                    )

                deepFileAccessGranted =
                    hasDeepFileAccess(
                        context
                    )

                usageAccessGranted =
                    hasUsageAccess(
                        context
                    )

                scanVersion++
            }
        )

        return
    }

    if (isLoading) {

        EverythingLoadingScreen(
            onBack = onBack
        )

        return
    }

    if (scanError != null) {

        ErrorScreen(
            message =
                scanError
                    ?: "Unknown error",
            onBack = onBack,
            onRetry = {
                scanVersion++
            }
        )

        return
    }

    val totalItems =
        remainingItems.size +
                keptItems.size +
                removalItems.size

    val reviewedItems =
        keptItems.size +
                removalItems.size

    if (
        remainingItems.isEmpty()
    ) {

        EverythingFinishedScreen(
            totalItems =
                totalItems,
            keptCount =
                keptItems.size,
            removalCount =
                removalItems.size,
            removalBytes =
                removalItems
                    .sumOf {
                        it.sizeBytes
                    },
            appsMarked =
                removalItems
                    .count {
                        it.kind ==
                                EverythingKind.APP
                    },
            onBack = onBack,
            onRestart = {

                remainingItems =
                    sortEverythingItems(
                        keptItems,
                        sortMode
                    )

                keptItems =
                    emptyList()

                previousEverythingTrashKeys =
                    emptySet()

                removalItems =
                    emptyList()

                history =
                    emptyList()

                previewItem = null
                offsetX = 0f
            },
            onRescan = {
                scanVersion++
            }
        )

        return
    }

    fun restoreLastDecision() {

        if (
            history.isEmpty()
        ) {
            return
        }

        val wasPreviewing =
            previewItem != null

        val lastDecision =
            history.last()

        history =
            history.dropLast(1)

        if (
            lastDecision.kept
        ) {

            keptItems =
                keptItems.dropLast(1)

        } else {

            removalItems =
                removalItems.dropLast(1)
        }

        remainingItems =
            listOf(
                lastDecision.item
            ) +
                    remainingItems

        previewItem =
            if (
                wasPreviewing &&
                lastDecision.item.kind !=
                EverythingKind.APP
            ) {

                lastDecision.item

            } else {

                null
            }

        offsetX = 0f
    }

    fun keepCurrentItem() {

        if (
            remainingItems.isEmpty()
        ) {
            return
        }

        val wasPreviewing =
            previewItem != null

        val activeItem =
            remainingItems.first()

        keptItems =
            keptItems +
                    activeItem

        history =
            history +
                    EverythingDecision(
                        item =
                            activeItem,
                        kept = true
                    )

        val nextItems =
            remainingItems.drop(1)

        remainingItems =
            nextItems

        previewItem =
            if (wasPreviewing) {

                nextItems
                    .firstOrNull()
                    ?.takeIf {
                        it.kind !=
                                EverythingKind.APP
                    }

            } else {

                null
            }

        offsetX = 0f
    }

    fun removeCurrentItem() {

        if (
            remainingItems.isEmpty()
        ) {
            return
        }

        val wasPreviewing =
            previewItem != null

        val activeItem =
            remainingItems.first()

        removalItems =
            removalItems +
                    activeItem

        history =
            history +
                    EverythingDecision(
                        item =
                            activeItem,
                        kept = false
                    )

        val nextItems =
            remainingItems.drop(1)

        remainingItems =
            nextItems

        previewItem =
            if (wasPreviewing) {

                nextItems
                    .firstOrNull()
                    ?.takeIf {
                        it.kind !=
                                EverythingKind.APP
                    }

            } else {

                null
            }

        offsetX = 0f
    }

    if (previewItem != null) {

        val item =
            previewItem!!

        when (item.kind) {

            EverythingKind.PHOTO -> {

                FullscreenPhotoViewer(
                    context = context,
                    photo = item.photo!!,
                    positionText =
                        "${reviewedItems + 1} / $totalItems",
                    canUndo =
                        history.isNotEmpty(),
                    onClose = {
                        previewItem = null
                    },
                    onUndo = {
                        restoreLastDecision()
                    },
                    onKeep = {
                        keepCurrentItem()
                    },
                    onRemove = {
                        removeCurrentItem()
                    }
                )
            }

            EverythingKind.VIDEO -> {

                FullscreenVideoViewer(
                    context = context,
                    video = item.video!!,
                    positionText =
                        "${reviewedItems + 1} / $totalItems",
                    canUndo =
                        history.isNotEmpty(),
                    onClose = {
                        previewItem = null
                    },
                    onUndo = {
                        restoreLastDecision()
                    },
                    onKeep = {
                        keepCurrentItem()
                    },
                    onRemove = {
                        removeCurrentItem()
                    }
                )
            }

            EverythingKind.AUDIO -> {

                FullscreenAudioViewer(
                    context = context,
                    audio = item.audio!!,
                    positionText =
                        "${reviewedItems + 1} / $totalItems",
                    canUndo =
                        history.isNotEmpty(),
                    onClose = {
                        previewItem = null
                    },
                    onUndo = {
                        restoreLastDecision()
                    },
                    onKeep = {
                        keepCurrentItem()
                    },
                    onRemove = {
                        removeCurrentItem()
                    }
                )
            }

            EverythingKind.FILE -> {

                FullscreenGenericFileViewer(
                    context = context,
                    file = item.file!!,
                    positionText =
                        "${reviewedItems + 1} / $totalItems",
                    canUndo =
                        history.isNotEmpty(),
                    onClose = {
                        previewItem = null
                    },
                    onUndo = {
                        restoreLastDecision()
                    },
                    onKeep = {
                        keepCurrentItem()
                    },
                    onRemove = {
                        removeCurrentItem()
                    },
                    onOpenExternally = {

                        openGenericFile(
                            context = context,
                            file = item.file
                        )
                    }
                )
            }

            EverythingKind.APP -> {

                previewItem = null
            }
        }

        return
    }

    val currentItem =
        remainingItems.first()

    val removeWord =
        if (
            currentItem.kind ==
            EverythingKind.APP
        ) {
            "UNINSTALL"
        } else {
            "REMOVE"
        }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(
                start = 18.dp,
                end = 18.dp,
                top = 54.dp,
                bottom = 2.dp
            ),
        horizontalAlignment =
            Alignment.CenterHorizontally
    ) {

        Row(
            modifier =
                Modifier.fillMaxWidth(),
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Button(
                onClick = onBack,
                modifier =
                    Modifier.height(48.dp)
            ) {

                Text(
                    "← Categories"
                )
            }

            Spacer(
                modifier =
                    Modifier.width(14.dp)
            )

            Column(
                modifier =
                    Modifier.weight(1f)
            ) {

                Text(
                    text = "Everything",
                    fontSize = 28.sp,
                    fontWeight =
                        FontWeight.Bold
                )

                Text(
                    text =
                        "${reviewedItems + 1} / $totalItems",
                    fontSize = 14.sp
                )
            }
        }

        Spacer(
            modifier =
                Modifier.height(8.dp)
        )

        Row(
            modifier =
                Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.SpaceBetween,
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Box {

                OutlinedButton(
                    onClick = {
                        sortMenuOpen = true
                    }
                ) {

                    Text(
                        sortMode.label
                    )
                }

                DropdownMenu(
                    expanded =
                        sortMenuOpen,
                    onDismissRequest = {
                        sortMenuOpen =
                            false
                    }
                ) {

                    EverythingSortMode
                        .values()
                        .forEach {
                                mode ->

                            DropdownMenuItem(
                                text = {
                                    Text(
                                        mode.label
                                    )
                                },
                                onClick = {

                                    sortMode =
                                        mode

                                    remainingItems =
                                        sortEverythingItems(
                                            remainingItems,
                                            sortMode
                                        )

                                    offsetX = 0f
                                    sortMenuOpen =
                                        false
                                }
                            )
                        }
                }
            }

            if (
                !usageAccessGranted
            ) {

                OutlinedButton(
                    onClick = {

                        settingsLauncher
                            .launch(
                                Intent(
                                    Settings
                                        .ACTION_USAGE_ACCESS_SETTINGS
                                )
                            )
                    }
                ) {

                    Text(
                        "Usage Access"
                    )
                }
            }
        }

        Spacer(
            modifier =
                Modifier.height(6.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(455.dp)
                .offset {

                    IntOffset(
                        x =
                            offsetX
                                .roundToInt(),
                        y = 0
                    )
                }
                .pointerInput(
                    currentItem.key
                ) {

                    detectDragGestures(

                        onDrag = {
                                change,
                                dragAmount ->

                            change.consume()

                            offsetX +=
                                dragAmount.x

                            if (
                                kotlin.math.abs(offsetX) > 12f
                            ) {
                                suppressPreviewTapUntil =
                                    System.currentTimeMillis() + 500L
                            }
                        },

                        onDragEnd = {

                            val threshold =
                                250f

                            if (
                                offsetX >
                                threshold
                            ) {

                                keepCurrentItem()

                            } else if (
                                offsetX <
                                -threshold
                            ) {

                                removeCurrentItem()

                            } else {

                                offsetX = 0f
                            }
                        }
                    )
                },
            shape =
                RoundedCornerShape(
                    24.dp
                ),
            elevation =
                CardDefaults
                    .cardElevation(
                        defaultElevation =
                            8.dp
                    )
        ) {

            Column(
                modifier =
                    Modifier.fillMaxSize()
            ) {

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                        .background(
                            Color.Black
                        )
                        .clickable {

                            if (
                                System.currentTimeMillis() >=
                                suppressPreviewTapUntil
                            ) {

                                when (
                                    currentItem.kind
                                ) {

                                    EverythingKind.APP -> {

                                        openInstalledApp(
                                            context =
                                                context,
                                            packageName =
                                                currentItem
                                                    .app!!
                                                    .packageName
                                        )
                                    }

                                    else -> {

                                        previewItem =
                                            currentItem
                                    }
                                }
                            }
                        },
                    contentAlignment =
                        Alignment.Center
                ) {

                    EverythingCardPreview(
                        item =
                            currentItem
                    )

                    if (
                        offsetX <
                        -100f
                    ) {

                        Box(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .background(
                                        Color.Red
                                            .copy(
                                                alpha =
                                                    0.28f
                                            )
                                    ),
                            contentAlignment =
                                Alignment.Center
                        ) {

                            Text(
                                text =
                                    removeWord,
                                fontSize =
                                    38.sp,
                                fontWeight =
                                    FontWeight.Bold,
                                color =
                                    Color.White
                            )
                        }
                    }

                    if (
                        offsetX >
                        100f
                    ) {

                        Box(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .background(
                                        Color.Green
                                            .copy(
                                                alpha =
                                                    0.25f
                                            )
                                    ),
                            contentAlignment =
                                Alignment.Center
                        ) {

                            Text(
                                text = "KEEP",
                                fontSize =
                                    38.sp,
                                fontWeight =
                                    FontWeight.Bold,
                                color =
                                    Color.White
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {

                    Text(
                        text =
                            currentItem.name,
                        fontSize = 19.sp,
                        fontWeight =
                            FontWeight.Bold,
                        maxLines = 1,
                        overflow =
                            TextOverflow.Ellipsis
                    )

                    Spacer(
                        modifier =
                            Modifier.height(3.dp)
                    )

                    Text(
                        text =
                            formatFileSize(
                                context,
                                currentItem
                                    .sizeBytes
                            ) +
                                    " • " +
                                    everythingTypeLabel(
                                        currentItem
                                    ),
                        fontSize = 15.sp
                    )

                    if (
                        currentItem.kind ==
                        EverythingKind.APP
                    ) {

                        Text(
                            text =
                                "Last used: " +
                                        formatLastUsed(
                                            currentItem
                                                .lastUsedTime
                                        ),
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        Spacer(
            modifier =
                Modifier.height(8.dp)
        )

        Row(
            modifier =
                Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.SpaceBetween,
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Text(
                text =
                    "← $removeWord",
                fontSize = 15.sp,
                fontWeight =
                    FontWeight.Bold
            )

            OutlinedButton(
                enabled =
                    history.isNotEmpty(),
                onClick = {
                    restoreLastDecision()
                }
            ) {

                Text("Undo")
            }

            Text(
                text = "KEEP →",
                fontSize = 15.sp,
                fontWeight =
                    FontWeight.Bold
            )
        }

        Spacer(
            modifier =
                Modifier.height(4.dp)
        )

        Text(
            text =
                "${removalItems.size} marked • " +
                        formatFileSize(
                            context,
                            removalItems
                                .sumOf {
                                    it.sizeBytes
                                }
                        ),
            fontSize = 13.sp
        )

        Text(
            text =
                "Swipe left to send items to Trash. Permanent removal happens only from Trash.",
            fontSize = 12.sp,
            color = Color.Gray
        )
    }
}

@Composable
fun EverythingCardPreview(
    item: EverythingItem
) {

    val context =
        LocalContext.current

    when (item.kind) {

        EverythingKind.PHOTO -> {

            HighQualityPhotoPreview(
                context = context,
                photo = item.photo!!
            )
        }

        EverythingKind.VIDEO -> {

            VideoPreview(
                context = context,
                video = item.video!!
            )
        }

        EverythingKind.AUDIO -> {

            Column(
                horizontalAlignment =
                    Alignment.CenterHorizontally
            ) {

                Text(
                    text = "♪",
                    fontSize = 92.sp,
                    color = Color.White,
                    fontWeight =
                        FontWeight.Bold
                )

                Spacer(
                    modifier =
                        Modifier.height(10.dp)
                )

                Text(
                    text =
                        "Tap to play fullscreen",
                    color =
                        Color.LightGray,
                    fontSize = 15.sp
                )
            }
        }

        EverythingKind.FILE -> {

            Column(
                horizontalAlignment =
                    Alignment.CenterHorizontally
            ) {

                Text(
                    text =
                        fileTypeBadge(
                            item.file!!
                        ),
                    color = Color.White,
                    fontSize = 54.sp,
                    fontWeight =
                        FontWeight.Bold
                )

                Spacer(
                    modifier =
                        Modifier.height(12.dp)
                )

                Text(
                    text =
                        "Tap to preview",
                    color =
                        Color.LightGray,
                    fontSize = 15.sp
                )
            }
        }

        EverythingKind.APP -> {

            Column(
                horizontalAlignment =
                    Alignment.CenterHorizontally
            ) {

                AppIcon(
                    packageName =
                        item.app!!
                            .packageName
                )

                Spacer(
                    modifier =
                        Modifier.height(14.dp)
                )

                Text(
                    text =
                        "Tap to open app",
                    color =
                        Color.LightGray,
                    fontSize = 15.sp
                )
            }
        }
    }
}

@Composable
fun EverythingAccessScreen(
    mediaAccessGranted: Boolean,
    deepFileAccessGranted: Boolean,
    usageAccessGranted: Boolean,
    onBack: () -> Unit,
    onGrantMedia: () -> Unit,
    onGrantFiles: () -> Unit,
    onGrantUsage: () -> Unit,
    onCheckAgain: () -> Unit
) {

    BackHandler {
        onBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(30.dp),
        verticalArrangement =
            Arrangement.Center,
        horizontalAlignment =
            Alignment.CenterHorizontally
    ) {

        Text(
            text =
                "Everything Access",
            fontSize = 31.sp,
            fontWeight =
                FontWeight.Bold
        )

        Spacer(
            modifier =
                Modifier.height(16.dp)
        )

        Text(
            text =
                "Everything mode combines every category SwipeClean can access into one queue.",
            fontSize = 17.sp,
            textAlign =
                TextAlign.Center
        )

        Spacer(
            modifier =
                Modifier.height(22.dp)
        )

        Text(
            text =
                if (
                    mediaAccessGranted
                ) {
                    "✓ Photos, video, and audio access"
                } else {
                    "○ Photos, video, and audio access needed"
                },
            fontSize = 16.sp
        )

        Spacer(
            modifier =
                Modifier.height(8.dp)
        )

        Text(
            text =
                if (
                    deepFileAccessGranted
                ) {
                    "✓ Deep shared-file access"
                } else {
                    "○ Deep shared-file access needed"
                },
            fontSize = 16.sp
        )

        Spacer(
            modifier =
                Modifier.height(8.dp)
        )

        Text(
            text =
                if (
                    usageAccessGranted
                ) {
                    "✓ Usage Access for app Last Used"
                } else {
                    "○ Usage Access optional"
                },
            fontSize = 16.sp
        )

        Spacer(
            modifier =
                Modifier.height(24.dp)
        )

        if (
            !mediaAccessGranted
        ) {

            Button(
                onClick =
                    onGrantMedia
            ) {

                Text(
                    "Grant Media Access"
                )
            }

            Spacer(
                modifier =
                    Modifier.height(10.dp)
            )
        }

        if (
            !deepFileAccessGranted
        ) {

            Button(
                onClick =
                    onGrantFiles
            ) {

                Text(
                    "Grant Deep File Access"
                )
            }

            Spacer(
                modifier =
                    Modifier.height(10.dp)
            )
        }

        if (
            !usageAccessGranted
        ) {

            OutlinedButton(
                onClick =
                    onGrantUsage
            ) {

                Text(
                    "Enable Usage Access"
                )
            }

            Spacer(
                modifier =
                    Modifier.height(10.dp)
            )
        }

        OutlinedButton(
            onClick =
                onCheckAgain
        ) {

            Text(
                "Check Access Again"
            )
        }

        Spacer(
            modifier =
                Modifier.height(10.dp)
        )

        OutlinedButton(
            onClick = onBack
        ) {

            Text("Back")
        }
    }
}

@Composable
fun EverythingLoadingScreen(
    onBack: () -> Unit
) {

    Column(
        modifier =
            Modifier.fillMaxSize(),
        verticalArrangement =
            Arrangement.Center,
        horizontalAlignment =
            Alignment.CenterHorizontally
    ) {

        CircularProgressIndicator()

        Spacer(
            modifier =
                Modifier.height(20.dp)
        )

        Text(
            text =
                "Building Everything queue...",
            fontSize = 20.sp
        )

        Spacer(
            modifier =
                Modifier.height(8.dp)
        )

        Text(
            text =
                "Scanning media, shared files, and apps",
            fontSize = 14.sp,
            color = Color.Gray
        )

        Spacer(
            modifier =
                Modifier.height(20.dp)
        )

        OutlinedButton(
            onClick = onBack
        ) {

            Text("Back")
        }
    }
}

@Composable
fun EverythingFinishedScreen(
    totalItems: Int,
    keptCount: Int,
    removalCount: Int,
    removalBytes: Long,
    appsMarked: Int,
    onBack: () -> Unit,
    onRestart: () -> Unit,
    onRescan: () -> Unit
) {

    val context =
        LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement =
            Arrangement.Center,
        horizontalAlignment =
            Alignment.CenterHorizontally
    ) {

        Text(
            text =
                "Everything Complete!",
            fontSize = 30.sp,
            fontWeight =
                FontWeight.Bold,
            textAlign =
                TextAlign.Center
        )

        Spacer(
            modifier =
                Modifier.height(22.dp)
        )

        Text(
            text =
                "$totalItems items reviewed",
            fontSize = 20.sp
        )

        Text(
            text =
                "Kept: $keptCount",
            fontSize = 19.sp
        )

        Text(
            text =
                "Marked: $removalCount",
            fontSize = 19.sp
        )

        if (
            appsMarked > 0
        ) {

            Text(
                text =
                    "$appsMarked apps marked for uninstall",
                fontSize = 17.sp
            )
        }

        Spacer(
            modifier =
                Modifier.height(18.dp)
        )

        Text(
            text =
                formatFileSize(
                    context,
                    removalBytes
                ) +
                        " represented by marked items",
            fontSize = 22.sp,
            fontWeight =
                FontWeight.Bold,
            textAlign =
                TextAlign.Center
        )

        Spacer(
            modifier =
                Modifier.height(12.dp)
        )

        Text(
            text =
                "Nothing is permanently removed until you confirm Empty Trash.",
            fontSize = 15.sp,
            color = Color.Gray,
            textAlign =
                TextAlign.Center
        )

        Spacer(
            modifier =
                Modifier.height(24.dp)
        )

        Button(
            onClick = onRestart
        ) {

            Text(
                "Start Everything Again"
            )
        }

        Spacer(
            modifier =
                Modifier.height(10.dp)
        )

        OutlinedButton(
            onClick = onRescan
        ) {

            Text(
                "Scan Everything Again"
            )
        }

        Spacer(
            modifier =
                Modifier.height(10.dp)
        )

        OutlinedButton(
            onClick = onBack
        ) {

            Text(
                "Back to Categories"
            )
        }
    }
}

@Composable
fun AppsSwipeScreen(
    onBack: () -> Unit,
    trashedItems:
    List<TrashItem>,
    onTrashDelta:
        (String, List<TrashItem>, Set<String>) -> Unit
) {

    BackHandler {
        onBack()
    }

    val context =
        LocalContext.current

    var usageAccessGranted by remember {
        mutableStateOf(
            hasUsageAccess(context)
        )
    }

    var isLoading by remember {
        mutableStateOf(true)
    }

    var scanError by remember {
        mutableStateOf<String?>(null)
    }

    var remainingApps by remember {
        mutableStateOf<List<AppItem>>(
            emptyList()
        )
    }

    var keptApps by remember {
        mutableStateOf<List<AppItem>>(
            emptyList()
        )
    }

    var removalApps by remember {
        mutableStateOf<List<AppItem>>(
            emptyList()
        )
    }

    var previousAppTrashKeys by remember {
        mutableStateOf<Set<String>>(
            emptySet()
        )
    }

    LaunchedEffect(removalApps) {

        val currentTrashItems =
            removalApps.map {
                it.toTrashItem()
            }

        val currentKeys =
            currentTrashItems
                .map {
                    it.key
                }
                .toSet()

        val addedItems =
            currentTrashItems
                .filter {
                    it.key !in previousAppTrashKeys
                }

        val removedKeys =
            previousAppTrashKeys -
                    currentKeys

        if (
            addedItems.isNotEmpty() ||
            removedKeys.isNotEmpty()
        ) {

            onTrashDelta(
                "apps",
                addedItems,
                removedKeys
            )
        }

        previousAppTrashKeys =
            currentKeys
    }

    var history by remember {
        mutableStateOf<List<AppDecision>>(
            emptyList()
        )
    }

    var sortMode by remember {
        mutableStateOf(
            AppSortMode.LARGEST_FIRST
        )
    }

    var sortMenuOpen by remember {
        mutableStateOf(false)
    }

    var offsetX by remember {
        mutableFloatStateOf(0f)
    }

    var suppressPreviewTapUntil by remember {
        mutableLongStateOf(0L)
    }

    var scanVersion by remember {
        mutableIntStateOf(0)
    }

    val usageAccessLauncher =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts
                    .StartActivityForResult()
        ) {

            usageAccessGranted =
                hasUsageAccess(context)

            scanVersion++
        }

    LaunchedEffect(scanVersion) {

        isLoading = true
        scanError = null

        try {

            val loaded =
                withContext(
                    Dispatchers.IO
                ) {

                    loadInstalledApps(
                        context = context,
                        usageAccessGranted =
                            usageAccessGranted
                    )
                }

            val reviewableApps =
                loaded.filterNot {
                    isMarkedForTrash(
                        candidate =
                            it.toTrashItem(),
                        trashedItems =
                            trashedItems
                    )
                }

            remainingApps =
                sortApps(
                    reviewableApps,
                    sortMode
                )

            keptApps = emptyList()

            previousAppTrashKeys =
                emptySet()

            removalApps = emptyList()
            history = emptyList()
            offsetX = 0f

        } catch (
            exception: Exception
        ) {

            scanError =
                exception.message
                    ?: "Unknown app scanning error"

        } finally {

            isLoading = false
        }
    }

    if (isLoading) {

        AppLoadingScreen(
            onBack = onBack
        )

        return
    }

    if (scanError != null) {

        ErrorScreen(
            message =
                scanError
                    ?: "Unknown error",
            onBack = onBack,
            onRetry = {
                scanVersion++
            }
        )

        return
    }

    val totalApps =
        remainingApps.size +
                keptApps.size +
                removalApps.size

    val reviewedApps =
        keptApps.size +
                removalApps.size

    if (remainingApps.isEmpty()) {

        AppFinishedScreen(
            totalApps = totalApps,
            keptCount = keptApps.size,
            removalCount =
                removalApps.size,
            removalBytes =
                removalApps.sumOf {
                    it.sizeBytes
                },
            onBack = onBack,
            onRestart = {

                remainingApps =
                    sortApps(
                        keptApps,
                        sortMode
                    )

                keptApps = emptyList()

                previousAppTrashKeys =
                    emptySet()

                removalApps = emptyList()
                history = emptyList()
                offsetX = 0f
            },
            onRescan = {
                scanVersion++
            }
        )

        return
    }

    val currentApp =
        remainingApps.first()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(
                start = 18.dp,
                end = 18.dp,
                top = 54.dp,
                bottom = 2.dp
            ),
        horizontalAlignment =
            Alignment.CenterHorizontally
    ) {

        Row(
            modifier =
                Modifier.fillMaxWidth(),
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Button(
                onClick = onBack,
                modifier =
                    Modifier.height(48.dp)
            ) {

                Text("← Categories")
            }

            Spacer(
                modifier =
                    Modifier.width(14.dp)
            )

            Column(
                modifier =
                    Modifier.weight(1f)
            ) {

                Text(
                    text = "Apps",
                    fontSize = 28.sp,
                    fontWeight =
                        FontWeight.Bold
                )

                Text(
                    text =
                        "${reviewedApps + 1} / $totalApps",
                    fontSize = 14.sp
                )
            }
        }

        Spacer(
            modifier =
                Modifier.height(8.dp)
        )

        Row(
            modifier =
                Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.SpaceBetween,
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Box {

                OutlinedButton(
                    onClick = {
                        sortMenuOpen = true
                    }
                ) {

                    Text(
                        sortMode.label
                    )
                }

                DropdownMenu(
                    expanded =
                        sortMenuOpen,
                    onDismissRequest = {
                        sortMenuOpen = false
                    }
                ) {

                    AppSortMode.values()
                        .forEach {
                                mode ->

                            DropdownMenuItem(
                                text = {
                                    Text(
                                        mode.label
                                    )
                                },
                                onClick = {

                                    sortMode = mode

                                    remainingApps =
                                        sortApps(
                                            remainingApps,
                                            sortMode
                                        )

                                    offsetX = 0f
                                    sortMenuOpen =
                                        false
                                }
                            )
                        }
                }
            }

            if (!usageAccessGranted) {

                OutlinedButton(
                    onClick = {

                        usageAccessLauncher
                            .launch(
                                Intent(
                                    Settings
                                        .ACTION_USAGE_ACCESS_SETTINGS
                                )
                            )
                    }
                ) {

                    Text("Usage Access")
                }
            }
        }

        if (!usageAccessGranted) {

            Text(
                text =
                    "Optional: enable Usage Access for Last Used sorting.",
                fontSize = 11.sp,
                color = Color.Gray,
                textAlign =
                    TextAlign.Center
            )
        }

        Spacer(
            modifier =
                Modifier.height(6.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(455.dp)
                .offset {

                    IntOffset(
                        x =
                            offsetX
                                .roundToInt(),
                        y = 0
                    )
                }
                .pointerInput(
                    currentApp.packageName
                ) {

                    detectDragGestures(

                        onDrag = {
                                change,
                                dragAmount ->

                            change.consume()

                            offsetX +=
                                dragAmount.x

                            if (
                                kotlin.math.abs(offsetX) > 12f
                            ) {
                                suppressPreviewTapUntil =
                                    System.currentTimeMillis() + 500L
                            }
                        },

                        onDragEnd = {

                            val threshold =
                                250f

                            if (
                                offsetX >
                                threshold
                            ) {

                                keptApps =
                                    keptApps +
                                            currentApp

                                history =
                                    history +
                                            AppDecision(
                                                app =
                                                    currentApp,
                                                kept = true
                                            )

                                remainingApps =
                                    remainingApps
                                        .drop(1)

                            } else if (
                                offsetX <
                                -threshold
                            ) {

                                removalApps =
                                    removalApps +
                                            currentApp

                                history =
                                    history +
                                            AppDecision(
                                                app =
                                                    currentApp,
                                                kept = false
                                            )

                                remainingApps =
                                    remainingApps
                                        .drop(1)
                            }

                            offsetX = 0f
                        }
                    )
                },
            shape =
                RoundedCornerShape(
                    24.dp
                ),
            elevation =
                CardDefaults
                    .cardElevation(
                        defaultElevation =
                            8.dp
                    )
        ) {

            Column(
                modifier =
                    Modifier.fillMaxSize()
            ) {

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(330.dp)
                        .background(
                            Color(0xFF171717)
                        )
                        .clickable {

                            if (
                                System.currentTimeMillis() >=
                                suppressPreviewTapUntil
                            ) {

                                openInstalledApp(
                                    context,
                                    currentApp
                                        .packageName
                                )
                            }
                        },
                    contentAlignment =
                        Alignment.Center
                ) {

                    Column(
                        horizontalAlignment =
                            Alignment.CenterHorizontally
                    ) {

                        AppIcon(
                            packageName =
                                currentApp
                                    .packageName
                        )

                        Spacer(
                            modifier =
                                Modifier.height(
                                    18.dp
                                )
                        )

                        Text(
                            text =
                                currentApp.name,
                            color =
                                Color.White,
                            fontSize = 27.sp,
                            fontWeight =
                                FontWeight.Bold,
                            maxLines = 2,
                            textAlign =
                                TextAlign.Center,
                            overflow =
                                TextOverflow.Ellipsis,
                            modifier =
                                Modifier.padding(
                                    horizontal =
                                        24.dp
                                )
                        )

                        Spacer(
                            modifier =
                                Modifier.height(
                                    6.dp
                                )
                        )

                        Text(
                            text =
                                "Tap to open app",
                            color =
                                Color.LightGray,
                            fontSize = 15.sp
                        )
                    }

                    if (offsetX < -100f) {

                        Box(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .background(
                                        Color.Red
                                            .copy(
                                                alpha =
                                                    0.28f
                                            )
                                    ),
                            contentAlignment =
                                Alignment.Center
                        ) {

                            Text(
                                text =
                                    "UNINSTALL",
                                fontSize = 38.sp,
                                fontWeight =
                                    FontWeight.Bold,
                                color =
                                    Color.White
                            )
                        }
                    }

                    if (offsetX > 100f) {

                        Box(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .background(
                                        Color.Green
                                            .copy(
                                                alpha =
                                                    0.25f
                                            )
                                    ),
                            contentAlignment =
                                Alignment.Center
                        ) {

                            Text(
                                text = "KEEP",
                                fontSize = 38.sp,
                                fontWeight =
                                    FontWeight.Bold,
                                color =
                                    Color.White
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp)
                ) {

                    Text(
                        text =
                            currentApp
                                .packageName,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow =
                            TextOverflow.Ellipsis
                    )

                    Text(
                        text =
                            formatFileSize(
                                context,
                                currentApp
                                    .sizeBytes
                            ) +
                                    " APK size",
                        fontSize = 15.sp,
                        fontWeight =
                            FontWeight.Bold
                    )

                    Text(
                        text =
                            "Last used: " +
                                    formatLastUsed(
                                        currentApp
                                            .lastUsedTime
                                    ),
                        fontSize = 14.sp
                    )

                    if (
                        currentApp
                            .versionName
                            .isNotBlank()
                    ) {

                        Text(
                            text =
                                "Version " +
                                        currentApp
                                            .versionName,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }

        Spacer(
            modifier =
                Modifier.height(8.dp)
        )

        Row(
            modifier =
                Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.SpaceBetween,
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Text(
                text = "← UNINSTALL",
                fontSize = 15.sp,
                fontWeight =
                    FontWeight.Bold
            )

            OutlinedButton(
                enabled =
                    history.isNotEmpty(),
                onClick = {

                    if (
                        history.isNotEmpty()
                    ) {

                        val last =
                            history.last()

                        history =
                            history.dropLast(
                                1
                            )

                        if (last.kept) {

                            keptApps =
                                keptApps
                                    .dropLast(1)

                        } else {

                            removalApps =
                                removalApps
                                    .dropLast(1)
                        }

                        remainingApps =
                            listOf(
                                last.app
                            ) +
                                    remainingApps

                        offsetX = 0f
                    }
                }
            ) {

                Text("Undo")
            }

            Text(
                text = "KEEP →",
                fontSize = 15.sp,
                fontWeight =
                    FontWeight.Bold
            )
        }

        Spacer(
            modifier =
                Modifier.height(4.dp)
        )

        Text(
            text =
                "${removalApps.size} apps marked • " +
                        formatFileSize(
                            context,
                            removalApps.sumOf {
                                it.sizeBytes
                            }
                        ),
            fontSize = 13.sp
        )

        Text(
            text =
                "Swipe left to send apps to Trash. Uninstalling requires Android confirmation.",
            fontSize = 12.sp,
            color = Color.Gray
        )
    }
}

@Composable
fun AppIcon(
    packageName: String
) {

    val context =
        LocalContext.current

    AndroidView(
        modifier =
            Modifier
                .width(120.dp)
                .height(120.dp),
        factory = {
                androidContext ->

            ImageView(
                androidContext
            ).apply {

                scaleType =
                    ImageView.ScaleType
                        .FIT_CENTER
            }
        },
        update = {
                imageView ->

            try {

                imageView
                    .setImageDrawable(
                        context
                            .packageManager
                            .getApplicationIcon(
                                packageName
                            )
                    )

            } catch (
                _: Exception
            ) {

                imageView
                    .setImageDrawable(
                        null
                    )
            }
        }
    )
}

@Composable
fun AppLoadingScreen(
    onBack: () -> Unit
) {

    Column(
        modifier =
            Modifier.fillMaxSize(),
        verticalArrangement =
            Arrangement.Center,
        horizontalAlignment =
            Alignment.CenterHorizontally
    ) {

        CircularProgressIndicator()

        Spacer(
            modifier =
                Modifier.height(20.dp)
        )

        Text(
            text =
                "Scanning installed apps...",
            fontSize = 20.sp
        )

        Spacer(
            modifier =
                Modifier.height(20.dp)
        )

        OutlinedButton(
            onClick = onBack
        ) {

            Text("Back")
        }
    }
}

@Composable
fun AppFinishedScreen(
    totalApps: Int,
    keptCount: Int,
    removalCount: Int,
    removalBytes: Long,
    onBack: () -> Unit,
    onRestart: () -> Unit,
    onRescan: () -> Unit
) {

    val context =
        LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement =
            Arrangement.Center,
        horizontalAlignment =
            Alignment.CenterHorizontally
    ) {

        Text(
            text =
                "App Cleanup Complete!",
            fontSize = 30.sp,
            fontWeight =
                FontWeight.Bold,
            textAlign =
                TextAlign.Center
        )

        Spacer(
            modifier =
                Modifier.height(24.dp)
        )

        Text(
            text =
                "$totalApps apps reviewed",
            fontSize = 20.sp
        )

        Spacer(
            modifier =
                Modifier.height(16.dp)
        )

        Text(
            text =
                "Kept: $keptCount",
            fontSize = 20.sp
        )

        Text(
            text =
                "Marked for uninstall: $removalCount",
            fontSize = 20.sp
        )

        Spacer(
            modifier =
                Modifier.height(18.dp)
        )

        Text(
            text =
                formatFileSize(
                    context,
                    removalBytes
                ) +
                        " APK size represented",
            fontSize = 22.sp,
            fontWeight =
                FontWeight.Bold,
            textAlign =
                TextAlign.Center
        )

        Spacer(
            modifier =
                Modifier.height(12.dp)
        )

        Text(
            text =
                "No apps are uninstalled until you confirm them from Trash.",
            fontSize = 15.sp,
            color = Color.Gray,
            textAlign =
                TextAlign.Center
        )

        Spacer(
            modifier =
                Modifier.height(26.dp)
        )

        Button(
            onClick = onRestart
        ) {

            Text("Start Apps Again")
        }

        Spacer(
            modifier =
                Modifier.height(10.dp)
        )

        OutlinedButton(
            onClick = onRescan
        ) {

            Text("Scan Apps Again")
        }

        Spacer(
            modifier =
                Modifier.height(10.dp)
        )

        OutlinedButton(
            onClick = onBack
        ) {

            Text("Back to Categories")
        }
    }
}

@Composable
fun FileSwipeScreen(
    category: FileCategory,
    onBack: () -> Unit,
    trashedItems:
    List<TrashItem>,
    onTrashDelta:
        (String, List<TrashItem>, Set<String>) -> Unit
) {

    val context = LocalContext.current

    var accessGranted by remember {
        mutableStateOf(hasDeepFileAccess(context))
    }

    var isLoading by remember {
        mutableStateOf(false)
    }

    var scanError by remember {
        mutableStateOf<String?>(null)
    }

    var remainingFiles by remember {
        mutableStateOf<List<FileItem>>(emptyList())
    }

    var keptFiles by remember {
        mutableStateOf<List<FileItem>>(emptyList())
    }

    var removalFiles by remember {
        mutableStateOf<List<FileItem>>(emptyList())
    }

    var previousFileTrashKeys by remember {
        mutableStateOf<Set<String>>(
            emptySet()
        )
    }

    LaunchedEffect(removalFiles) {

        val currentTrashItems =
            removalFiles.map {
                it.toTrashItem()
            }

        val currentKeys =
            currentTrashItems
                .map {
                    it.key
                }
                .toSet()

        val addedItems =
            currentTrashItems
                .filter {
                    it.key !in previousFileTrashKeys
                }

        val removedKeys =
            previousFileTrashKeys -
                    currentKeys

        if (
            addedItems.isNotEmpty() ||
            removedKeys.isNotEmpty()
        ) {

            onTrashDelta(
                "files:${category.name}",
                addedItems,
                removedKeys
            )
        }

        previousFileTrashKeys =
            currentKeys
    }

    var history by remember {
        mutableStateOf<List<FileDecision>>(emptyList())
    }

    var sortMode by remember {
        mutableStateOf(SortMode.LARGEST_FIRST)
    }

    var sortMenuOpen by remember {
        mutableStateOf(false)
    }

    var offsetX by remember {
        mutableFloatStateOf(0f)
    }

    var suppressPreviewTapUntil by remember {
        mutableLongStateOf(0L)
    }

    var previewFile by remember {
        mutableStateOf<FileItem?>(null)
    }

    BackHandler {
        if (previewFile != null) {
            previewFile = null
        } else {
            onBack()
        }
    }

    var scanVersion by remember {
        mutableIntStateOf(0)
    }

    val settingsLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.StartActivityForResult()
        ) {
            accessGranted =
                hasDeepFileAccess(context)

            if (accessGranted) {
                scanVersion++
            }
        }

    val legacyPermissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->

            accessGranted = granted

            if (granted) {
                scanVersion++
            }
        }

    LaunchedEffect(accessGranted, scanVersion, category) {

        if (accessGranted) {

            isLoading = true
            scanError = null

            try {

                val loadedFiles =
                    withContext(Dispatchers.IO) {
                        loadFilesForCategory(
                            context = context,
                            category = category
                        )
                    }

                val reviewableFiles =
                    loadedFiles.filterNot {
                        isMarkedForTrash(
                            candidate =
                                it.toTrashItem(),
                            trashedItems =
                                trashedItems
                        )
                    }

                remainingFiles =
                    sortFiles(
                        reviewableFiles,
                        sortMode
                    )

                keptFiles = emptyList()

                previousFileTrashKeys =
                    emptySet()

                removalFiles = emptyList()
                history = emptyList()
                offsetX = 0f

            } catch (exception: Exception) {

                scanError =
                    exception.message
                        ?: "Unknown file scanning error"

            } finally {

                isLoading = false
            }
        }
    }

    if (!accessGranted) {

        DeepFileAccessScreen(
            category = category,
            onBack = onBack,
            onGrantAccess = {

                if (
                    Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.R
                ) {

                    val appSettingsIntent =
                        Intent(
                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse(
                                "package:${context.packageName}"
                            )
                        )

                    try {

                        settingsLauncher.launch(
                            appSettingsIntent
                        )

                    } catch (_: Exception) {

                        settingsLauncher.launch(
                            Intent(
                                Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                            )
                        )
                    }

                } else {

                    legacyPermissionLauncher.launch(
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    )
                }
            },
            onCheckAgain = {

                accessGranted =
                    hasDeepFileAccess(context)

                if (accessGranted) {
                    scanVersion++
                }
            }
        )

        return
    }

    if (isLoading) {

        FileLoadingScreen(
            category = category,
            onBack = onBack
        )

        return
    }

    if (scanError != null) {

        ErrorScreen(
            message =
                scanError ?: "Unknown error",
            onBack = onBack,
            onRetry = {
                scanVersion++
            }
        )

        return
    }

    val totalFiles =
        remainingFiles.size +
                keptFiles.size +
                removalFiles.size

    val reviewedFiles =
        keptFiles.size +
                removalFiles.size

    if (remainingFiles.isEmpty()) {

        FileFinishedScreen(
            category = category,
            totalFiles = totalFiles,
            keptCount = keptFiles.size,
            removalCount = removalFiles.size,
            removalBytes =
                removalFiles.sumOf { it.size },
            onBack = onBack,
            onRestart = {

                remainingFiles =
                    sortFiles(
                        keptFiles,
                        sortMode
                    )

                keptFiles = emptyList()

                previousFileTrashKeys =
                    emptySet()

                removalFiles = emptyList()
                history = emptyList()
                offsetX = 0f
            },
            onRescan = {
                scanVersion++
            }
        )

        return
    }

    val currentFile =
        remainingFiles.first()

    if (previewFile != null) {

        FullscreenGenericFileViewer(
            context = context,
            file = previewFile!!,
            positionText =
                "${reviewedFiles + 1} / $totalFiles",
            canUndo = history.isNotEmpty(),
            onClose = {
                previewFile = null
            },
            onUndo = {

                if (history.isNotEmpty()) {

                    val lastDecision =
                        history.last()

                    history =
                        history.dropLast(1)

                    if (lastDecision.kept) {

                        keptFiles =
                            keptFiles.dropLast(1)

                    } else {

                        removalFiles =
                            removalFiles.dropLast(1)
                    }

                    remainingFiles =
                        listOf(lastDecision.file) +
                                remainingFiles

                    previewFile =
                        lastDecision.file

                    offsetX = 0f
                }
            },
            onKeep = {

                val activeFile =
                    previewFile!!

                keptFiles =
                    keptFiles + activeFile

                history =
                    history +
                            FileDecision(
                                file = activeFile,
                                kept = true
                            )

                val nextFiles =
                    remainingFiles.drop(1)

                remainingFiles =
                    nextFiles

                previewFile =
                    nextFiles.firstOrNull()

                offsetX = 0f
            },
            onRemove = {

                val activeFile =
                    previewFile!!

                removalFiles =
                    removalFiles + activeFile

                history =
                    history +
                            FileDecision(
                                file = activeFile,
                                kept = false
                            )

                val nextFiles =
                    remainingFiles.drop(1)

                remainingFiles =
                    nextFiles

                previewFile =
                    nextFiles.firstOrNull()

                offsetX = 0f
            },
            onOpenExternally = {
                openGenericFile(
                    context = context,
                    file = previewFile!!
                )
            }
        )

        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(
                start = 18.dp,
                end = 18.dp,
                top = 54.dp,
                bottom = 2.dp
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Button(
                onClick = {
                    onBack()
                },
                modifier = Modifier.height(48.dp)
            ) {

                Text("← Categories")
            }

            Spacer(
                modifier = Modifier.width(14.dp)
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {

                Text(
                    text = category.title,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text =
                        "${reviewedFiles + 1} / $totalFiles",
                    fontSize = 14.sp
                )
            }
        }

        Spacer(
            modifier = Modifier.height(10.dp)
        )

        Box {

            OutlinedButton(
                onClick = {
                    sortMenuOpen = true
                }
            ) {

                Text(
                    text = sortMode.label
                )
            }

            DropdownMenu(
                expanded = sortMenuOpen,
                onDismissRequest = {
                    sortMenuOpen = false
                }
            ) {

                DropdownMenuItem(
                    text = {
                        Text("Largest → Smallest")
                    },
                    onClick = {

                        sortMode =
                            SortMode.LARGEST_FIRST

                        remainingFiles =
                            sortFiles(
                                remainingFiles,
                                sortMode
                            )

                        offsetX = 0f
                        sortMenuOpen = false
                    }
                )

                DropdownMenuItem(
                    text = {
                        Text("Smallest → Largest")
                    },
                    onClick = {

                        sortMode =
                            SortMode.SMALLEST_FIRST

                        remainingFiles =
                            sortFiles(
                                remainingFiles,
                                sortMode
                            )

                        offsetX = 0f
                        sortMenuOpen = false
                    }
                )

                DropdownMenuItem(
                    text = {
                        Text("Date Added")
                    },
                    onClick = {

                        sortMode =
                            SortMode.DATE_ADDED

                        remainingFiles =
                            sortFiles(
                                remainingFiles,
                                sortMode
                            )

                        offsetX = 0f
                        sortMenuOpen = false
                    }
                )
            }
        }

        Spacer(
            modifier = Modifier.height(10.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(475.dp)
                .offset {
                    IntOffset(
                        x = offsetX.roundToInt(),
                        y = 0
                    )
                }
                .pointerInput(
                    currentFile.uri
                ) {

                    detectDragGestures(

                        onDrag = {
                                change,
                                dragAmount ->

                            change.consume()
                            offsetX +=
                                dragAmount.x

                            if (
                                kotlin.math.abs(offsetX) > 12f
                            ) {
                                suppressPreviewTapUntil =
                                    System.currentTimeMillis() + 500L
                            }
                        },

                        onDragEnd = {

                            val threshold = 250f

                            if (
                                offsetX >
                                threshold
                            ) {

                                keptFiles =
                                    keptFiles +
                                            currentFile

                                history =
                                    history +
                                            FileDecision(
                                                file =
                                                    currentFile,
                                                kept = true
                                            )

                                remainingFiles =
                                    remainingFiles
                                        .drop(1)

                            } else if (
                                offsetX <
                                -threshold
                            ) {

                                removalFiles =
                                    removalFiles +
                                            currentFile

                                history =
                                    history +
                                            FileDecision(
                                                file =
                                                    currentFile,
                                                kept = false
                                            )

                                remainingFiles =
                                    remainingFiles
                                        .drop(1)
                            }

                            offsetX = 0f
                        }
                    )
                },
            shape =
                RoundedCornerShape(24.dp),
            elevation =
                CardDefaults.cardElevation(
                    defaultElevation = 8.dp
                )
        ) {

            Column(
                modifier =
                    Modifier.fillMaxSize()
            ) {

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp)
                        .background(
                            Color(0xFF171717)
                        )
                        .clickable {

                            if (
                                System.currentTimeMillis() >=
                                suppressPreviewTapUntil
                            ) {

                                previewFile =
                                    currentFile
                            }
                        },
                    contentAlignment =
                        Alignment.Center
                ) {

                    Column(
                        horizontalAlignment =
                            Alignment.CenterHorizontally
                    ) {

                        Text(
                            text =
                                fileTypeBadge(
                                    currentFile
                                ),
                            fontSize = 54.sp,
                            color = Color.White,
                            fontWeight =
                                FontWeight.Bold
                        )

                        Spacer(
                            modifier =
                                Modifier.height(14.dp)
                        )

                        Text(
                            text =
                                currentFile.extension
                                    .uppercase()
                                    .ifBlank {
                                        "FILE"
                                    },
                            fontSize = 24.sp,
                            color = Color.White,
                            fontWeight =
                                FontWeight.Bold
                        )

                        Spacer(
                            modifier =
                                Modifier.height(10.dp)
                        )

                        Text(
                            text =
                                "Tap to preview",
                            fontSize = 18.sp,
                            color =
                                Color.LightGray
                        )
                    }

                    if (offsetX < -100f) {

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Color.Red.copy(
                                        alpha = 0.28f
                                    )
                                ),
                            contentAlignment =
                                Alignment.Center
                        ) {

                            Text(
                                text = "REMOVE",
                                fontSize = 38.sp,
                                fontWeight =
                                    FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    if (offsetX > 100f) {

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Color.Green.copy(
                                        alpha = 0.25f
                                    )
                                ),
                            contentAlignment =
                                Alignment.Center
                        ) {

                            Text(
                                text = "KEEP",
                                fontSize = 38.sp,
                                fontWeight =
                                    FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {

                    Text(
                        text = currentFile.name,
                        fontSize = 20.sp,
                        fontWeight =
                            FontWeight.Bold,
                        maxLines = 1,
                        overflow =
                            TextOverflow.Ellipsis
                    )

                    Spacer(
                        modifier =
                            Modifier.height(5.dp)
                    )

                    Text(
                        text =
                            formatFileSize(
                                context,
                                currentFile.size
                            ) +
                                    " • " +
                                    fileTypeText(
                                        currentFile
                                    ),
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow =
                            TextOverflow.Ellipsis
                    )
                }
            }
        }

        Spacer(
            modifier = Modifier.height(10.dp)
        )

        Row(
            modifier =
                Modifier.fillMaxWidth(),
            horizontalArrangement =
                Arrangement.SpaceBetween,
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Text(
                text = "← REMOVE",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            OutlinedButton(
                enabled =
                    history.isNotEmpty(),
                onClick = {

                    if (
                        history.isNotEmpty()
                    ) {

                        val lastDecision =
                            history.last()

                        history =
                            history.dropLast(1)

                        if (
                            lastDecision.kept
                        ) {

                            keptFiles =
                                keptFiles
                                    .dropLast(1)

                        } else {

                            removalFiles =
                                removalFiles
                                    .dropLast(1)
                        }

                        remainingFiles =
                            listOf(
                                lastDecision.file
                            ) +
                                    remainingFiles

                        offsetX = 0f
                    }
                }
            ) {

                Text("Undo")
            }

            Text(
                text = "KEEP →",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(
            modifier = Modifier.height(6.dp)
        )

        Text(
            text =
                "${removalFiles.size} selected • " +
                        formatFileSize(
                            context,
                            removalFiles
                                .sumOf { it.size }
                        ) +
                        " marked",
            fontSize = 14.sp
        )

        Text(
            text =
                "Review mode — nothing will be deleted.",
            fontSize = 12.sp,
            color = Color.Gray
        )

        Spacer(
            modifier = Modifier.height(1.dp)
        )
    }
}

@Composable
fun FullscreenGenericFileViewer(
    context: Context,
    file: FileItem,
    positionText: String,
    canUndo: Boolean,
    onClose: () -> Unit,
    onUndo: () -> Unit,
    onKeep: () -> Unit,
    onRemove: () -> Unit,
    onOpenExternally: () -> Unit
) {

    val previewType =
        inAppPreviewType(
            file
        )

    val swipeablePreview =
        true

    var dragOffsetX by
    remember(file.absolutePath) {
        mutableFloatStateOf(0f)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 14.dp,
                    end = 14.dp,
                    top = 10.dp,
                    bottom = 10.dp
                ),
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            Button(
                onClick = onClose
            ) {

                Text("← Back")
            }

            if (swipeablePreview) {

                Spacer(
                    modifier =
                        Modifier.width(8.dp)
                )

                Button(
                    onClick = onUndo,
                    enabled = canUndo
                ) {

                    Text("↶ Undo")
                }
            }

            Spacer(
                modifier = Modifier.width(10.dp)
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {

                Text(
                    text = file.name,
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow =
                        TextOverflow.Ellipsis
                )

                Text(
                    text =
                        formatFileSize(
                            context,
                            file.size
                        ) +
                                " • " +
                                fileTypeText(file),
                    color = Color.LightGray,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow =
                        TextOverflow.Ellipsis
                )
            }

            Text(
                text = positionText,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight =
                    FontWeight.Bold
            )

            if (
                previewType ==
                InAppPreviewType.EXTERNAL
            ) {

                Spacer(
                    modifier =
                        Modifier.width(8.dp)
                )

                OutlinedButton(
                    onClick =
                        onOpenExternally
                ) {

                    Text("Open")
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.Black)
                .then(
                    if (swipeablePreview) {

                        Modifier.pointerInput(
                            file.absolutePath
                        ) {

                            detectHorizontalDragGestures(

                                onHorizontalDrag = {
                                        change,
                                        dragAmount ->

                                    change.consume()

                                    dragOffsetX +=
                                        dragAmount
                                },

                                onDragEnd = {

                                    val threshold =
                                        250f

                                    when {

                                        dragOffsetX >
                                                threshold -> {

                                            onKeep()
                                        }

                                        dragOffsetX <
                                                -threshold -> {

                                            onRemove()
                                        }
                                    }

                                    dragOffsetX = 0f
                                },

                                onDragCancel = {

                                    dragOffsetX = 0f
                                }
                            )
                        }

                    } else {

                        Modifier
                    }
                ),
            contentAlignment =
                Alignment.Center
        ) {

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset {

                        IntOffset(
                            x =
                                dragOffsetX
                                    .roundToInt(),
                            y = 0
                        )
                    },
                contentAlignment =
                    Alignment.Center
            ) {

                when (previewType) {

                    InAppPreviewType.IMAGE -> {

                        FileImagePreview(
                            file = file
                        )
                    }

                    InAppPreviewType.VIDEO -> {

                        FileMediaPreview(
                            file = file,
                            isVideo = true
                        )
                    }

                    InAppPreviewType.AUDIO -> {

                        FileMediaPreview(
                            file = file,
                            isVideo = false
                        )
                    }

                    InAppPreviewType.PDF -> {

                        PdfFilePreview(
                            file = file
                        )
                    }

                    InAppPreviewType.TEXT -> {

                        TextFilePreview(
                            file = file
                        )
                    }

                    InAppPreviewType.EXTERNAL -> {

                        Column(
                            modifier =
                                Modifier.padding(
                                    28.dp
                                ),
                            horizontalAlignment =
                                Alignment.CenterHorizontally
                        ) {

                            Text(
                                text =
                                    fileTypeBadge(
                                        file
                                    ),
                                color = Color.White,
                                fontSize = 58.sp,
                                fontWeight =
                                    FontWeight.Bold
                            )

                            Spacer(
                                modifier =
                                    Modifier.height(
                                        18.dp
                                    )
                            )

                            Text(
                                text =
                                    "SwipeClean can review this file here, but another app is needed to open its actual contents.",
                                color =
                                    Color.LightGray,
                                fontSize = 18.sp,
                                textAlign =
                                    TextAlign.Center
                            )

                            Spacer(
                                modifier =
                                    Modifier.height(
                                        18.dp
                                    )
                            )

                            Button(
                                onClick =
                                    onOpenExternally
                            ) {

                                Text(
                                    "Open actual file"
                                )
                            }
                        }
                    }
                }
            }

            if (
                swipeablePreview &&
                dragOffsetX < -100f
            ) {

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Color.Red.copy(
                                alpha = 0.25f
                            )
                        ),
                    contentAlignment =
                        Alignment.Center
                ) {

                    Text(
                        text = "REMOVE",
                        fontSize = 44.sp,
                        fontWeight =
                            FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            if (
                swipeablePreview &&
                dragOffsetX > 100f
            ) {

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Color.Green.copy(
                                alpha = 0.22f
                            )
                        ),
                    contentAlignment =
                        Alignment.Center
                ) {

                    Text(
                        text = "KEEP",
                        fontSize = 44.sp,
                        fontWeight =
                            FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        if (swipeablePreview) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 18.dp,
                        vertical = 6.dp
                    ),
                horizontalArrangement =
                    Arrangement.SpaceBetween
            ) {

                Text(
                    text = "← REMOVE",
                    color = Color.White,
                    fontWeight =
                        FontWeight.Bold
                )

                Text(
                    text = "KEEP →",
                    color = Color.White,
                    fontWeight =
                        FontWeight.Bold
                )
            }
        }

        Text(
            text =
                "Preview only — nothing is being deleted.",
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    bottom = 10.dp
                ),
            color = Color.Gray,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun FileImagePreview(
    file: FileItem
) {

    var imageBitmap by
    remember(file.absolutePath) {
        mutableStateOf<ImageBitmap?>(
            null
        )
    }

    var failed by
    remember(file.absolutePath) {
        mutableStateOf(false)
    }

    LaunchedEffect(
        file.absolutePath
    ) {

        imageBitmap = null
        failed = false

        try {

            val bitmap =
                withContext(
                    Dispatchers.IO
                ) {

                    loadImageFilePreview(
                        absolutePath =
                            file.absolutePath,
                        maxDimension = 2400
                    )
                }

            if (bitmap != null) {

                imageBitmap =
                    bitmap.asImageBitmap()

            } else {

                failed = true
            }

        } catch (_: Exception) {

            failed = true
        }
    }

    when {

        imageBitmap != null -> {

            Image(
                bitmap =
                    imageBitmap!!,
                contentDescription =
                    file.name,
                modifier =
                    Modifier.fillMaxSize(),
                contentScale =
                    ContentScale.Fit
            )
        }

        failed -> {

            Text(
                text =
                    "Image preview unavailable",
                color = Color.White,
                fontSize = 18.sp
            )
        }

        else -> {

            CircularProgressIndicator()
        }
    }
}

@Composable
fun FileMediaPreview(
    file: FileItem,
    isVideo: Boolean
) {

    val context =
        LocalContext.current

    val player =
        remember(
            file.absolutePath
        ) {

            ExoPlayer.Builder(
                context
            )
                .build()
                .apply {

                    setMediaItem(
                        MediaItem.fromUri(
                            Uri.fromFile(
                                File(
                                    file.absolutePath
                                )
                            )
                        )
                    )

                    prepare()

                    playWhenReady = true
                }
        }

    DisposableEffect(
        player
    ) {

        onDispose {

            player.release()
        }
    }

    Column(
        modifier =
            Modifier.fillMaxSize(),
        horizontalAlignment =
            Alignment.CenterHorizontally
    ) {

        if (isVideo) {

            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                factory = {
                        androidContext ->

                    PlayerView(
                        androidContext
                    ).apply {

                        this.player =
                            player

                        useController =
                            false

                        resizeMode =
                            AspectRatioFrameLayout
                                .RESIZE_MODE_FIT

                        keepScreenOn =
                            true
                    }
                },
                update = {
                        playerView ->

                    if (
                        playerView.player !==
                        player
                    ) {

                        playerView.player =
                            player
                    }
                }
            )

        } else {

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment =
                    Alignment.Center
            ) {

                Column(
                    horizontalAlignment =
                        Alignment.CenterHorizontally
                ) {

                    Text(
                        text = "♪",
                        fontSize = 130.sp,
                        color = Color.White,
                        fontWeight =
                            FontWeight.Bold
                    )

                    Spacer(
                        modifier =
                            Modifier.height(
                                16.dp
                            )
                    )

                    Text(
                        text = file.name,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight =
                            FontWeight.Bold,
                        maxLines = 2,
                        textAlign =
                            TextAlign.Center,
                        overflow =
                            TextOverflow.Ellipsis,
                        modifier =
                            Modifier.padding(
                                horizontal =
                                    24.dp
                            )
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Color(0xFF151515)
                )
                .padding(14.dp)
        ) {

            PlaybackTimelineControls(
                player = player
            )
        }
    }
}

@Composable
fun PdfFilePreview(
    file: FileItem
) {

    var pageIndex by
    remember(file.absolutePath) {
        mutableIntStateOf(0)
    }

    var pageCount by
    remember(file.absolutePath) {
        mutableIntStateOf(0)
    }

    var pageBitmap by
    remember(
        file.absolutePath,
        pageIndex
    ) {
        mutableStateOf<ImageBitmap?>(
            null
        )
    }

    var failed by
    remember(file.absolutePath) {
        mutableStateOf(false)
    }

    LaunchedEffect(
        file.absolutePath,
        pageIndex
    ) {

        pageBitmap = null
        failed = false

        try {

            val result =
                withContext(
                    Dispatchers.IO
                ) {

                    renderPdfPage(
                        absolutePath =
                            file.absolutePath,
                        pageIndex =
                            pageIndex
                    )
                }

            pageCount =
                result.pageCount

            pageBitmap =
                result.bitmap
                    ?.asImageBitmap()

            if (
                result.bitmap == null
            ) {

                failed = true
            }

        } catch (_: Exception) {

            failed = true
        }
    }

    Column(
        modifier =
            Modifier.fillMaxSize()
    ) {

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(
                    Color(0xFF202020)
                ),
            contentAlignment =
                Alignment.Center
        ) {

            when {

                pageBitmap != null -> {

                    Image(
                        bitmap =
                            pageBitmap!!,
                        contentDescription =
                            "PDF page ${pageIndex + 1}",
                        modifier =
                            Modifier.fillMaxSize(),
                        contentScale =
                            ContentScale.Fit
                    )
                }

                failed -> {

                    Text(
                        text =
                            "PDF preview unavailable",
                        color = Color.White,
                        fontSize = 18.sp
                    )
                }

                else -> {

                    CircularProgressIndicator()
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Color(0xFF151515)
                )
                .padding(12.dp),
            verticalAlignment =
                Alignment.CenterVertically,
            horizontalArrangement =
                Arrangement.SpaceBetween
        ) {

            Button(
                enabled =
                    pageIndex > 0,
                onClick = {

                    pageIndex--
                }
            ) {

                Text("← Previous")
            }

            Text(
                text =
                    if (pageCount > 0) {
                        "Page ${pageIndex + 1} / $pageCount"
                    } else {
                        "Loading..."
                    },
                color = Color.White,
                fontSize = 14.sp
            )

            Button(
                enabled =
                    pageCount > 0 &&
                            pageIndex <
                            pageCount - 1,
                onClick = {

                    pageIndex++
                }
            ) {

                Text("Next →")
            }
        }
    }
}

@Composable
fun TextFilePreview(
    file: FileItem
) {

    var contents by
    remember(file.absolutePath) {
        mutableStateOf<String?>(null)
    }

    var failed by
    remember(file.absolutePath) {
        mutableStateOf(false)
    }

    LaunchedEffect(
        file.absolutePath
    ) {

        contents = null
        failed = false

        try {

            contents =
                withContext(
                    Dispatchers.IO
                ) {

                    readTextFilePreview(
                        absolutePath =
                            file.absolutePath
                    )
                }

        } catch (_: Exception) {

            failed = true
        }
    }

    when {

        contents != null -> {

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Color(0xFF101010)
                    )
                    .padding(18.dp)
                    .verticalScroll(
                        rememberScrollState()
                    )
            ) {

                Text(
                    text =
                        contents!!,
                    color = Color.White,
                    fontSize = 15.sp
                )
            }
        }

        failed -> {

            Text(
                text =
                    "Text preview unavailable",
                color = Color.White,
                fontSize = 18.sp
            )
        }

        else -> {

            CircularProgressIndicator()
        }
    }
}

data class PdfPageRenderResult(
    val bitmap: Bitmap?,
    val pageCount: Int
)

fun inAppPreviewType(
    file: FileItem
): InAppPreviewType {

    val mime =
        file.mimeType
            .orEmpty()
            .lowercase()

    val extension =
        file.extension
            .lowercase()

    val imageExtensions =
        setOf(
            "jpg",
            "jpeg",
            "png",
            "webp",
            "bmp",
            "gif",
            "heic",
            "heif"
        )

    val videoExtensions =
        setOf(
            "mp4",
            "m4v",
            "mkv",
            "webm",
            "3gp",
            "mov",
            "avi"
        )

    val audioExtensions =
        setOf(
            "mp3",
            "m4a",
            "aac",
            "wav",
            "ogg",
            "opus",
            "flac",
            "amr"
        )

    val textExtensions =
        setOf(
            "txt",
            "md",
            "csv",
            "json",
            "xml",
            "yaml",
            "yml",
            "log",
            "html",
            "htm",
            "css",
            "js",
            "ts",
            "kt",
            "kts",
            "java",
            "py",
            "c",
            "cpp",
            "h",
            "hpp",
            "sh",
            "ini",
            "cfg",
            "conf"
        )

    return when {

        mime.startsWith(
            "image/"
        ) ||
                extension in
                imageExtensions -> {

            InAppPreviewType.IMAGE
        }

        mime.startsWith(
            "video/"
        ) ||
                extension in
                videoExtensions -> {

            InAppPreviewType.VIDEO
        }

        mime.startsWith(
            "audio/"
        ) ||
                extension in
                audioExtensions -> {

            InAppPreviewType.AUDIO
        }

        mime ==
                "application/pdf" ||
                extension ==
                "pdf" -> {

            InAppPreviewType.PDF
        }

        mime.startsWith(
            "text/"
        ) ||
                extension in
                textExtensions -> {

            InAppPreviewType.TEXT
        }

        else -> {

            InAppPreviewType.EXTERNAL
        }
    }
}

fun loadImageFilePreview(
    absolutePath: String,
    maxDimension: Int
): Bitmap? {

    val file =
        File(
            absolutePath
        )

    if (
        !file.exists() ||
        !file.isFile
    ) {

        return null
    }

    return if (
        Build.VERSION.SDK_INT >=
        Build.VERSION_CODES.P
    ) {

        val source =
            ImageDecoder
                .createSource(
                    file
                )

        ImageDecoder
            .decodeBitmap(
                source
            ) {
                    decoder,
                    info,
                    _ ->

                val width =
                    info.size.width

                val height =
                    info.size.height

                val largest =
                    max(
                        width,
                        height
                    )

                if (
                    largest >
                    maxDimension
                ) {

                    val scale =
                        maxDimension
                            .toFloat() /
                                largest
                                    .toFloat()

                    decoder
                        .setTargetSize(
                            (
                                    width *
                                            scale
                                    )
                                .roundToInt()
                                .coerceAtLeast(
                                    1
                                ),
                            (
                                    height *
                                            scale
                                    )
                                .roundToInt()
                                .coerceAtLeast(
                                    1
                                )
                        )
                }

                decoder.allocator =
                    ImageDecoder
                        .ALLOCATOR_SOFTWARE
            }

    } else {

        val bounds =
            BitmapFactory
                .Options()
                .apply {
                    inJustDecodeBounds =
                        true
                }

        BitmapFactory
            .decodeFile(
                absolutePath,
                bounds
            )

        if (
            bounds.outWidth <= 0 ||
            bounds.outHeight <= 0
        ) {

            return null
        }

        var sampleSize = 1

        while (
            bounds.outWidth /
            sampleSize >
            maxDimension * 2 ||
            bounds.outHeight /
            sampleSize >
            maxDimension * 2
        ) {

            sampleSize *= 2
        }

        BitmapFactory
            .decodeFile(
                absolutePath,
                BitmapFactory
                    .Options()
                    .apply {

                        inSampleSize =
                            sampleSize

                        inPreferredConfig =
                            Bitmap.Config
                                .ARGB_8888
                    }
            )
    }
}

fun renderPdfPage(
    absolutePath: String,
    pageIndex: Int
): PdfPageRenderResult {

    val file =
        File(
            absolutePath
        )

    if (
        !file.exists() ||
        !file.isFile
    ) {

        return PdfPageRenderResult(
            bitmap = null,
            pageCount = 0
        )
    }

    val descriptor =
        ParcelFileDescriptor
            .open(
                file,
                ParcelFileDescriptor
                    .MODE_READ_ONLY
            )

    try {

        val renderer =
            PdfRenderer(
                descriptor
            )

        try {

            val count =
                renderer.pageCount

            if (
                count <= 0
            ) {

                return PdfPageRenderResult(
                    bitmap = null,
                    pageCount = 0
                )
            }

            val safeIndex =
                pageIndex.coerceIn(
                    0,
                    count - 1
                )

            val page =
                renderer.openPage(
                    safeIndex
                )

            try {

                val targetWidth =
                    (
                            page.width * 2
                            )
                        .coerceAtMost(
                            1800
                        )
                        .coerceAtLeast(
                            1
                        )

                val scale =
                    targetWidth.toFloat() /
                            page.width
                                .toFloat()

                val targetHeight =
                    (
                            page.height *
                                    scale
                            )
                        .roundToInt()
                        .coerceAtLeast(
                            1
                        )

                val bitmap =
                    Bitmap.createBitmap(
                        targetWidth,
                        targetHeight,
                        Bitmap.Config
                            .ARGB_8888
                    )

                bitmap.eraseColor(
                    android.graphics.Color.WHITE
                )

                page.render(
                    bitmap,
                    null,
                    null,
                    PdfRenderer.Page
                        .RENDER_MODE_FOR_DISPLAY
                )

                return PdfPageRenderResult(
                    bitmap = bitmap,
                    pageCount = count
                )

            } finally {

                page.close()
            }

        } finally {

            renderer.close()
        }

    } finally {

        descriptor.close()
    }
}

fun readTextFilePreview(
    absolutePath: String
): String {

    val file =
        File(
            absolutePath
        )

    if (
        !file.exists() ||
        !file.isFile
    ) {

        return "This file no longer exists."
    }

    val maxBytes =
        1_000_000

    val bytes =
        file.inputStream()
            .use {
                    input ->

                val buffer =
                    ByteArray(
                        maxBytes
                    )

                var total = 0

                while (
                    total <
                    maxBytes
                ) {

                    val read =
                        input.read(
                            buffer,
                            total,
                            maxBytes -
                                    total
                        )

                    if (
                        read <= 0
                    ) {
                        break
                    }

                    total += read
                }

                buffer.copyOf(
                    total
                )
            }

    val text =
        bytes.toString(
            Charsets.UTF_8
        )

    return if (
        file.length() >
        maxBytes
    ) {

        text +
                "\n\n[Preview stopped after 1 MB. The full file is larger.]"

    } else {

        text
    }
}

@Composable
fun DeepFileAccessScreen(
    category: FileCategory,
    onBack: () -> Unit,
    onGrantAccess: () -> Unit,
    onCheckAgain: () -> Unit
) {

    BackHandler {
        onBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement =
            Arrangement.Center,
        horizontalAlignment =
            Alignment.CenterHorizontally
    ) {

        Text(
            text = category.title,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(
            modifier = Modifier.height(18.dp)
        )

        Text(
            text = "SwipeClean needs deep file access to scan shared storage for downloads, documents, archives, APKs, EXEs, and other files.",
            fontSize = 18.sp,
            textAlign = TextAlign.Center
        )

        Spacer(
            modifier = Modifier.height(12.dp)
        )

        Text(
            text = "Android still protects other apps' private folders. SwipeClean will never claim those were scanned.",
            fontSize = 14.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )

        Spacer(
            modifier = Modifier.height(24.dp)
        )

        Button(
            onClick = onGrantAccess
        ) {

            Text("Grant Deep File Access")
        }

        Spacer(
            modifier = Modifier.height(10.dp)
        )

        OutlinedButton(
            onClick = onCheckAgain
        ) {

            Text("Check Access Again")
        }

        Spacer(
            modifier = Modifier.height(10.dp)
        )

        OutlinedButton(
            onClick = onBack
        ) {

            Text("Back")
        }
    }
}

@Composable
fun FileLoadingScreen(
    category: FileCategory,
    onBack: () -> Unit
) {

    Column(
        modifier =
            Modifier.fillMaxSize(),
        verticalArrangement =
            Arrangement.Center,
        horizontalAlignment =
            Alignment.CenterHorizontally
    ) {

        CircularProgressIndicator()

        Spacer(
            modifier = Modifier.height(20.dp)
        )

        Text(
            text =
                "Scanning ${category.title.lowercase()}...",
            fontSize = 20.sp
        )

        Spacer(
            modifier = Modifier.height(20.dp)
        )

        OutlinedButton(
            onClick = onBack
        ) {

            Text("Back")
        }
    }
}

@Composable
fun FileFinishedScreen(
    category: FileCategory,
    totalFiles: Int,
    keptCount: Int,
    removalCount: Int,
    removalBytes: Long,
    onBack: () -> Unit,
    onRestart: () -> Unit,
    onRescan: () -> Unit
) {

    val context =
        LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement =
            Arrangement.Center,
        horizontalAlignment =
            Alignment.CenterHorizontally
    ) {

        Text(
            text =
                "${category.title} Cleanup Complete!",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(
            modifier = Modifier.height(24.dp)
        )

        Text(
            text =
                "$totalFiles files reviewed",
            fontSize = 20.sp
        )

        Spacer(
            modifier = Modifier.height(16.dp)
        )

        Text(
            text = "Kept: $keptCount",
            fontSize = 20.sp
        )

        Text(
            text =
                "Marked for removal: $removalCount",
            fontSize = 20.sp
        )

        Spacer(
            modifier = Modifier.height(18.dp)
        )

        Text(
            text =
                formatFileSize(
                    context,
                    removalBytes
                ) +
                        " marked for removal",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(
            modifier = Modifier.height(12.dp)
        )

        Text(
            text =
                "Review mode: no files have been deleted.",
            fontSize = 15.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )

        Spacer(
            modifier = Modifier.height(26.dp)
        )

        Button(
            onClick = onRestart
        ) {

            Text(
                "Start ${category.title} Again"
            )
        }

        Spacer(
            modifier = Modifier.height(10.dp)
        )

        OutlinedButton(
            onClick = onRescan
        ) {

            Text(
                "Scan ${category.title} Again"
            )
        }

        Spacer(
            modifier = Modifier.height(10.dp)
        )

        OutlinedButton(
            onClick = onBack
        ) {

            Text("Back to Categories")
        }
    }
}

@Composable
fun AudioSwipeScreen(
    onBack: () -> Unit,
    trashedItems:
    List<TrashItem>,
    onTrashDelta:
        (String, List<TrashItem>, Set<String>) -> Unit
) {

    val context = LocalContext.current

    var permissionGranted by remember {
        mutableStateOf(hasAudioPermission(context))
    }

    var isLoading by remember {
        mutableStateOf(false)
    }

    var scanError by remember {
        mutableStateOf<String?>(null)
    }

    var remainingAudio by remember {
        mutableStateOf<List<AudioItem>>(emptyList())
    }

    var keptAudio by remember {
        mutableStateOf<List<AudioItem>>(emptyList())
    }

    var removalAudio by remember {
        mutableStateOf<List<AudioItem>>(emptyList())
    }

    var previousAudioTrashKeys by remember {
        mutableStateOf<Set<String>>(
            emptySet()
        )
    }

    LaunchedEffect(removalAudio) {

        val currentTrashItems =
            removalAudio.map {
                it.toTrashItem()
            }

        val currentKeys =
            currentTrashItems
                .map {
                    it.key
                }
                .toSet()

        val addedItems =
            currentTrashItems
                .filter {
                    it.key !in previousAudioTrashKeys
                }

        val removedKeys =
            previousAudioTrashKeys -
                    currentKeys

        if (
            addedItems.isNotEmpty() ||
            removedKeys.isNotEmpty()
        ) {

            onTrashDelta(
                "audio",
                addedItems,
                removedKeys
            )
        }

        previousAudioTrashKeys =
            currentKeys
    }

    var history by remember {
        mutableStateOf<List<AudioDecision>>(emptyList())
    }

    var sortMode by remember {
        mutableStateOf(SortMode.LARGEST_FIRST)
    }

    var sortMenuOpen by remember {
        mutableStateOf(false)
    }

    var offsetX by remember {
        mutableFloatStateOf(0f)
    }

    var suppressPreviewTapUntil by remember {
        mutableLongStateOf(0L)
    }

    var isFullscreen by remember {
        mutableStateOf(false)
    }

    BackHandler {
        if (isFullscreen) {
            isFullscreen = false
        } else {
            onBack()
        }
    }

    var scanVersion by remember {
        mutableIntStateOf(0)
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->
            permissionGranted = granted
        }

    LaunchedEffect(permissionGranted, scanVersion) {

        if (permissionGranted) {

            isLoading = true
            scanError = null

            try {

                val loadedAudio = withContext(Dispatchers.IO) {
                    loadAudio(context)
                }

                val reviewableAudio =
                    loadedAudio.filterNot {
                        isMarkedForTrash(
                            candidate =
                                it.toTrashItem(),
                            trashedItems =
                                trashedItems
                        )
                    }

                remainingAudio = sortAudio(
                    reviewableAudio,
                    sortMode
                )

                keptAudio = emptyList()

                previousAudioTrashKeys =
                    emptySet()

                removalAudio = emptyList()
                history = emptyList()
                offsetX = 0f

            } catch (exception: Exception) {

                scanError =
                    exception.message ?: "Unknown audio scanning error"

            } finally {

                isLoading = false
            }
        }
    }

    if (!permissionGranted) {

        AudioPermissionScreen(
            onBack = onBack,
            onRequestPermission = {

                val permission =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        Manifest.permission.READ_MEDIA_AUDIO
                    } else {
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    }

                permissionLauncher.launch(permission)
            }
        )

        return
    }

    if (isLoading) {

        AudioLoadingScreen(
            onBack = onBack
        )

        return
    }

    if (scanError != null) {

        ErrorScreen(
            message = scanError ?: "Unknown error",
            onBack = onBack,
            onRetry = {
                scanVersion++
            }
        )

        return
    }

    val totalAudio =
        remainingAudio.size +
                keptAudio.size +
                removalAudio.size

    val reviewedAudio =
        keptAudio.size +
                removalAudio.size

    if (remainingAudio.isEmpty()) {

        AudioFinishedScreen(
            totalAudio = totalAudio,
            keptCount = keptAudio.size,
            removalCount = removalAudio.size,
            removalBytes = removalAudio.sumOf { it.size },
            onBack = onBack,
            onRestart = {

                remainingAudio = sortAudio(
                    keptAudio,
                    sortMode
                )

                keptAudio = emptyList()

                previousAudioTrashKeys =
                    emptySet()

                removalAudio = emptyList()
                history = emptyList()
                offsetX = 0f
            },
            onRescan = {
                scanVersion++
            }
        )

        return
    }

    val currentAudio = remainingAudio.first()

    if (isFullscreen) {

        FullscreenAudioViewer(
            context = context,
            audio = currentAudio,
            positionText = "${reviewedAudio + 1} / $totalAudio",
            canUndo = history.isNotEmpty(),
            onClose = {
                isFullscreen = false
            },
            onUndo = {

                if (history.isNotEmpty()) {

                    val lastDecision = history.last()
                    history = history.dropLast(1)

                    if (lastDecision.kept) {
                        keptAudio = keptAudio.dropLast(1)
                    } else {
                        removalAudio = removalAudio.dropLast(1)
                    }

                    remainingAudio =
                        listOf(lastDecision.audio) + remainingAudio

                    offsetX = 0f
                }
            },
            onKeep = {

                keptAudio =
                    keptAudio + currentAudio

                history =
                    history + AudioDecision(
                        audio = currentAudio,
                        kept = true
                    )

                remainingAudio =
                    remainingAudio.drop(1)

                offsetX = 0f
            },
            onRemove = {

                removalAudio =
                    removalAudio + currentAudio

                history =
                    history + AudioDecision(
                        audio = currentAudio,
                        kept = false
                    )

                remainingAudio =
                    remainingAudio.drop(1)

                offsetX = 0f
            }
        )

        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(
                start = 18.dp,
                end = 18.dp,
                top = 54.dp,
                bottom = 2.dp
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Button(
                onClick = {
                    onBack()
                },
                modifier = Modifier.height(48.dp)
            ) {
                Text("← Categories")
            }

            Spacer(
                modifier = Modifier.width(14.dp)
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {

                Text(
                    text = "Audio",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "${reviewedAudio + 1} / $totalAudio",
                    fontSize = 14.sp
                )
            }
        }

        Spacer(
            modifier = Modifier.height(10.dp)
        )

        Box {

            OutlinedButton(
                onClick = {
                    sortMenuOpen = true
                }
            ) {

                Text(
                    text = sortMode.label
                )
            }

            DropdownMenu(
                expanded = sortMenuOpen,
                onDismissRequest = {
                    sortMenuOpen = false
                }
            ) {

                DropdownMenuItem(
                    text = {
                        Text("Largest → Smallest")
                    },
                    onClick = {

                        sortMode = SortMode.LARGEST_FIRST

                        remainingAudio = sortAudio(
                            remainingAudio,
                            sortMode
                        )

                        offsetX = 0f
                        sortMenuOpen = false
                    }
                )

                DropdownMenuItem(
                    text = {
                        Text("Smallest → Largest")
                    },
                    onClick = {

                        sortMode = SortMode.SMALLEST_FIRST

                        remainingAudio = sortAudio(
                            remainingAudio,
                            sortMode
                        )

                        offsetX = 0f
                        sortMenuOpen = false
                    }
                )

                DropdownMenuItem(
                    text = {
                        Text("Date Added")
                    },
                    onClick = {

                        sortMode = SortMode.DATE_ADDED

                        remainingAudio = sortAudio(
                            remainingAudio,
                            sortMode
                        )

                        offsetX = 0f
                        sortMenuOpen = false
                    }
                )
            }
        }

        Spacer(
            modifier = Modifier.height(10.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(475.dp)
                .offset {
                    IntOffset(
                        x = offsetX.roundToInt(),
                        y = 0
                    )
                }
                .pointerInput(currentAudio.id) {

                    detectDragGestures(

                        onDrag = { change, dragAmount ->

                            change.consume()
                            offsetX += dragAmount.x

                            if (
                                kotlin.math.abs(offsetX) > 12f
                            ) {
                                suppressPreviewTapUntil =
                                    System.currentTimeMillis() + 500L
                            }
                        },

                        onDragEnd = {

                            val threshold = 250f

                            if (offsetX > threshold) {

                                keptAudio =
                                    keptAudio + currentAudio

                                history =
                                    history + AudioDecision(
                                        audio = currentAudio,
                                        kept = true
                                    )

                                remainingAudio =
                                    remainingAudio.drop(1)

                            } else if (offsetX < -threshold) {

                                removalAudio =
                                    removalAudio + currentAudio

                                history =
                                    history + AudioDecision(
                                        audio = currentAudio,
                                        kept = false
                                    )

                                remainingAudio =
                                    remainingAudio.drop(1)
                            }

                            offsetX = 0f
                        }
                    )
                },
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 8.dp
            )
        ) {

            Column(
                modifier = Modifier.fillMaxSize()
            ) {

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp)
                        .background(Color(0xFF171717))
                        .clickable {

                            if (
                                System.currentTimeMillis() >=
                                suppressPreviewTapUntil
                            ) {
                                isFullscreen = true
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {

                        Text(
                            text = "♪",
                            fontSize = 94.sp,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(
                            modifier = Modifier.height(14.dp)
                        )

                        Text(
                            text = "Tap to play fullscreen",
                            fontSize = 18.sp,
                            color = Color.White
                        )

                        Spacer(
                            modifier = Modifier.height(8.dp)
                        )

                        Text(
                            text = formatDuration(
                                currentAudio.durationMs
                            ),
                            fontSize = 16.sp,
                            color = Color.LightGray
                        )
                    }

                    if (offsetX < -100f) {

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Color.Red.copy(
                                        alpha = 0.28f
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {

                            Text(
                                text = "REMOVE",
                                fontSize = 38.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    if (offsetX > 100f) {

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Color.Green.copy(
                                        alpha = 0.25f
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {

                            Text(
                                text = "KEEP",
                                fontSize = 38.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {

                    Text(
                        text = currentAudio.name,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(
                        modifier = Modifier.height(5.dp)
                    )

                    Text(
                        text =
                            formatFileSize(
                                context,
                                currentAudio.size
                            ) +
                                    " • " +
                                    formatDuration(
                                        currentAudio.durationMs
                                    ),
                        fontSize = 17.sp
                    )
                }
            }
        }

        Spacer(
            modifier = Modifier.height(10.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {

            Text(
                text = "← REMOVE",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            OutlinedButton(
                enabled = history.isNotEmpty(),
                onClick = {

                    if (history.isNotEmpty()) {

                        val lastDecision =
                            history.last()

                        history =
                            history.dropLast(1)

                        if (lastDecision.kept) {

                            keptAudio =
                                keptAudio.dropLast(1)

                        } else {

                            removalAudio =
                                removalAudio.dropLast(1)
                        }

                        remainingAudio =
                            listOf(lastDecision.audio) +
                                    remainingAudio

                        offsetX = 0f
                    }
                }
            ) {

                Text("Undo")
            }

            Text(
                text = "KEEP →",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(
            modifier = Modifier.height(6.dp)
        )

        Text(
            text =
                "${removalAudio.size} selected • " +
                        formatFileSize(
                            context,
                            removalAudio.sumOf { it.size }
                        ) +
                        " marked",
            fontSize = 14.sp
        )

        Text(
            text = "Review mode — nothing will be deleted.",
            fontSize = 12.sp,
            color = Color.Gray
        )

        Spacer(
            modifier = Modifier.height(1.dp)
        )
    }
}

@Composable
fun FullscreenAudioViewer(
    context: Context,
    audio: AudioItem,
    positionText: String,
    canUndo: Boolean,
    onClose: () -> Unit,
    onUndo: () -> Unit,
    onKeep: () -> Unit,
    onRemove: () -> Unit
) {

    var dragOffsetX by remember(audio.id) {
        mutableFloatStateOf(0f)
    }

    val player =
        remember(audio.uri) {

            ExoPlayer.Builder(context)
                .build()
                .apply {

                    setMediaItem(
                        MediaItem.fromUri(
                            audio.uri
                        )
                    )

                    prepare()

                    playWhenReady = true
                }
        }

    DisposableEffect(player) {

        onDispose {

            player.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(audio.id) {

                detectDragGestures(

                    onDrag = { change, dragAmount ->

                        change.consume()
                        dragOffsetX += dragAmount.x
                    },

                    onDragEnd = {

                        val threshold = 250f

                        when {

                            dragOffsetX > threshold -> {
                                onKeep()
                            }

                            dragOffsetX < -threshold -> {
                                onRemove()
                            }
                        }

                        dragOffsetX = 0f
                    }
                )
            }
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .offset {
                    IntOffset(
                        x = dragOffsetX.roundToInt(),
                        y = 0
                    )
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            Text(
                text = "♪",
                fontSize = 140.sp,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )

            Spacer(
                modifier = Modifier.height(26.dp)
            )

            Text(
                text = audio.name,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(
                    horizontal = 30.dp
                )
            )

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            Text(
                text =
                    formatFileSize(
                        context,
                        audio.size
                    ) +
                            " • " +
                            formatDuration(
                                audio.durationMs
                            ),
                color = Color.LightGray,
                fontSize = 16.sp
            )

            Spacer(
                modifier = Modifier.height(32.dp)
            )

            PlaybackTimelineControls(
                player = player
            )
        }

        if (dragOffsetX < -100f) {

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Color.Red.copy(
                            alpha = 0.25f
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {

                Text(
                    text = "REMOVE",
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        if (dragOffsetX > 100f) {

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Color.Green.copy(
                            alpha = 0.22f
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {

                Text(
                    text = "KEEP",
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(
                    start = 14.dp,
                    end = 14.dp,
                    top = 10.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Button(
                onClick = onClose
            ) {

                Text("← Back")
            }


            Spacer(
                modifier = Modifier.width(8.dp)
            )

            Button(
                onClick = onUndo,
                enabled = canUndo
            ) {
                Text("↶ Undo")
            }

            Spacer(
                modifier = Modifier.weight(1f)
            )
            Text(
                text = positionText,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Color.Black.copy(
                        alpha = 0.72f
                    )
                )
                .padding(
                    start = 18.dp,
                    end = 18.dp,
                    top = 12.dp,
                    bottom = 18.dp
                )
        ) {

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {

                Text(
                    text = "← REMOVE",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "KEEP →",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(
                modifier = Modifier.height(4.dp)
            )

            Text(
                text = "Review mode — nothing will be deleted.",
                color = Color.LightGray,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun AudioPermissionScreen(
    onBack: () -> Unit,
    onRequestPermission: () -> Unit
) {

    BackHandler {
        onBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            text = "Audio Access",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(
            modifier = Modifier.height(20.dp)
        )

        Text(
            text = "SwipeClean needs audio access to find music, recordings, and other audio stored on your device.",
            fontSize = 18.sp,
            textAlign = TextAlign.Center
        )

        Spacer(
            modifier = Modifier.height(24.dp)
        )

        Button(
            onClick = onRequestPermission
        ) {

            Text(
                text = "Allow Audio Access"
            )
        }

        Spacer(
            modifier = Modifier.height(12.dp)
        )

        OutlinedButton(
            onClick = onBack
        ) {

            Text("Back")
        }
    }
}

@Composable
fun AudioLoadingScreen(
    onBack: () -> Unit
) {

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        CircularProgressIndicator()

        Spacer(
            modifier = Modifier.height(20.dp)
        )

        Text(
            text = "Scanning audio...",
            fontSize = 20.sp
        )

        Spacer(
            modifier = Modifier.height(20.dp)
        )

        OutlinedButton(
            onClick = onBack
        ) {

            Text("Back")
        }
    }
}

@Composable
fun AudioFinishedScreen(
    totalAudio: Int,
    keptCount: Int,
    removalCount: Int,
    removalBytes: Long,
    onBack: () -> Unit,
    onRestart: () -> Unit,
    onRescan: () -> Unit
) {

    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            text = "Audio Cleanup Complete!",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(
            modifier = Modifier.height(24.dp)
        )

        Text(
            text = "$totalAudio audio files reviewed",
            fontSize = 20.sp
        )

        Spacer(
            modifier = Modifier.height(16.dp)
        )

        Text(
            text = "Kept: $keptCount",
            fontSize = 20.sp
        )

        Text(
            text = "Marked for removal: $removalCount",
            fontSize = 20.sp
        )

        Spacer(
            modifier = Modifier.height(18.dp)
        )

        Text(
            text =
                formatFileSize(
                    context,
                    removalBytes
                ) +
                        " marked for removal",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(
            modifier = Modifier.height(12.dp)
        )

        Text(
            text = "Review mode: no audio has been deleted.",
            fontSize = 15.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )

        Spacer(
            modifier = Modifier.height(26.dp)
        )

        Button(
            onClick = onRestart
        ) {

            Text("Start Audio Again")
        }

        Spacer(
            modifier = Modifier.height(10.dp)
        )

        OutlinedButton(
            onClick = onRescan
        ) {

            Text("Scan Audio Again")
        }

        Spacer(
            modifier = Modifier.height(10.dp)
        )

        OutlinedButton(
            onClick = onBack
        ) {

            Text("Back to Categories")
        }
    }
}

@Composable
fun ScreenshotSwipeScreen(
    onBack: () -> Unit,
    trashedItems:
    List<TrashItem>,
    onTrashDelta:
        (String, List<TrashItem>, Set<String>) -> Unit
) {

    val context = LocalContext.current

    var permissionGranted by remember {
        mutableStateOf(hasPhotoPermission(context))
    }

    var isLoading by remember {
        mutableStateOf(false)
    }

    var scanError by remember {
        mutableStateOf<String?>(null)
    }

    var remainingScreenshots by remember {
        mutableStateOf<List<PhotoItem>>(emptyList())
    }

    var keptScreenshots by remember {
        mutableStateOf<List<PhotoItem>>(emptyList())
    }

    var removalScreenshots by remember {
        mutableStateOf<List<PhotoItem>>(emptyList())
    }

    var previousScreenshotTrashKeys by remember {
        mutableStateOf<Set<String>>(
            emptySet()
        )
    }

    LaunchedEffect(removalScreenshots) {

        val currentTrashItems =
            removalScreenshots.map {
                it.toTrashItem()
            }

        val currentKeys =
            currentTrashItems
                .map {
                    it.key
                }
                .toSet()

        val addedItems =
            currentTrashItems
                .filter {
                    it.key !in previousScreenshotTrashKeys
                }

        val removedKeys =
            previousScreenshotTrashKeys -
                    currentKeys

        if (
            addedItems.isNotEmpty() ||
            removedKeys.isNotEmpty()
        ) {

            onTrashDelta(
                "screenshots",
                addedItems,
                removedKeys
            )
        }

        previousScreenshotTrashKeys =
            currentKeys
    }

    var history by remember {
        mutableStateOf<List<Decision>>(emptyList())
    }

    var sortMode by remember {
        mutableStateOf(SortMode.LARGEST_FIRST)
    }

    var sortMenuOpen by remember {
        mutableStateOf(false)
    }

    var offsetX by remember {
        mutableFloatStateOf(0f)
    }

    var suppressPreviewTapUntil by remember {
        mutableLongStateOf(0L)
    }

    var isFullscreen by remember {
        mutableStateOf(false)
    }

    BackHandler {
        if (isFullscreen) {
            isFullscreen = false
        } else {
            onBack()
        }
    }

    var scanVersion by remember {
        mutableIntStateOf(0)
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->
            permissionGranted = granted
        }

    LaunchedEffect(permissionGranted, scanVersion) {

        if (permissionGranted) {

            isLoading = true
            scanError = null

            try {

                val loadedScreenshots = withContext(Dispatchers.IO) {
                    loadScreenshots(context)
                }

                val reviewableScreenshots =
                    loadedScreenshots.filterNot {
                        isMarkedForTrash(
                            candidate =
                                it.toTrashItem(),
                            trashedItems =
                                trashedItems
                        )
                    }

                remainingScreenshots = sortPhotos(
                    reviewableScreenshots,
                    sortMode
                )

                keptScreenshots = emptyList()

                previousScreenshotTrashKeys =
                    emptySet()

                removalScreenshots = emptyList()
                history = emptyList()
                offsetX = 0f

            } catch (exception: Exception) {

                scanError =
                    exception.message ?: "Unknown scanning error"

            } finally {

                isLoading = false
            }
        }
    }

    if (!permissionGranted) {

        ScreenshotPermissionScreen(
            onBack = onBack,
            onRequestPermission = {

                val permission =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        Manifest.permission.READ_MEDIA_IMAGES
                    } else {
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    }

                permissionLauncher.launch(permission)
            }
        )

        return
    }

    if (isLoading) {

        ScreenshotLoadingScreen(
            onBack = onBack
        )

        return
    }

    if (scanError != null) {

        ErrorScreen(
            message = scanError ?: "Unknown error",
            onBack = onBack,
            onRetry = {
                scanVersion++
            }
        )

        return
    }

    val totalScreenshots =
        remainingScreenshots.size +
                keptScreenshots.size +
                removalScreenshots.size

    val reviewedScreenshots =
        keptScreenshots.size +
                removalScreenshots.size

    if (remainingScreenshots.isEmpty()) {

        ScreenshotFinishedScreen(
            totalScreenshots = totalScreenshots,
            keptCount = keptScreenshots.size,
            removalCount = removalScreenshots.size,
            removalBytes = removalScreenshots.sumOf { it.size },
            onBack = onBack,
            onRestart = {

                remainingScreenshots = sortPhotos(
                    keptScreenshots,
                    sortMode
                )

                keptScreenshots = emptyList()

                previousScreenshotTrashKeys =
                    emptySet()

                removalScreenshots = emptyList()
                history = emptyList()
                offsetX = 0f
            },
            onRescan = {
                scanVersion++
            }
        )

        return
    }

    val currentScreenshot = remainingScreenshots.first()

    if (isFullscreen) {

        FullscreenPhotoViewer(
            context = context,
            photo = currentScreenshot,
            positionText = "${reviewedScreenshots + 1} / $totalScreenshots",
            canUndo = history.isNotEmpty(),
            onClose = {
                isFullscreen = false
            },
            onUndo = {

                if (history.isNotEmpty()) {

                    val lastDecision = history.last()
                    history = history.dropLast(1)

                    if (lastDecision.kept) {
                        keptScreenshots = keptScreenshots.dropLast(1)
                    } else {
                        removalScreenshots = removalScreenshots.dropLast(1)
                    }

                    remainingScreenshots =
                        listOf(lastDecision.photo) + remainingScreenshots

                    offsetX = 0f
                }
            },
            onKeep = {

                keptScreenshots =
                    keptScreenshots + currentScreenshot

                history =
                    history + Decision(
                        photo = currentScreenshot,
                        kept = true
                    )

                remainingScreenshots =
                    remainingScreenshots.drop(1)

                offsetX = 0f
            },
            onRemove = {

                removalScreenshots =
                    removalScreenshots + currentScreenshot

                history =
                    history + Decision(
                        photo = currentScreenshot,
                        kept = false
                    )

                remainingScreenshots =
                    remainingScreenshots.drop(1)

                offsetX = 0f
            }
        )

        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(
                start = 18.dp,
                end = 18.dp,
                top = 54.dp,
                bottom = 2.dp
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Button(
                onClick = {
                    onBack()
                },
                modifier = Modifier.height(48.dp)
            ) {
                Text("← Categories")
            }

            Spacer(
                modifier = Modifier.width(14.dp)
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {

                Text(
                    text = "Screenshots",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "${reviewedScreenshots + 1} / $totalScreenshots",
                    fontSize = 14.sp
                )
            }
        }

        Spacer(
            modifier = Modifier.height(10.dp)
        )

        Box {

            OutlinedButton(
                onClick = {
                    sortMenuOpen = true
                }
            ) {

                Text(
                    text = sortMode.label
                )
            }

            DropdownMenu(
                expanded = sortMenuOpen,
                onDismissRequest = {
                    sortMenuOpen = false
                }
            ) {

                DropdownMenuItem(
                    text = {
                        Text("Largest → Smallest")
                    },
                    onClick = {

                        sortMode = SortMode.LARGEST_FIRST

                        remainingScreenshots = sortPhotos(
                            remainingScreenshots,
                            sortMode
                        )

                        offsetX = 0f
                        sortMenuOpen = false
                    }
                )

                DropdownMenuItem(
                    text = {
                        Text("Smallest → Largest")
                    },
                    onClick = {

                        sortMode = SortMode.SMALLEST_FIRST

                        remainingScreenshots = sortPhotos(
                            remainingScreenshots,
                            sortMode
                        )

                        offsetX = 0f
                        sortMenuOpen = false
                    }
                )

                DropdownMenuItem(
                    text = {
                        Text("Date Added")
                    },
                    onClick = {

                        sortMode = SortMode.DATE_ADDED

                        remainingScreenshots = sortPhotos(
                            remainingScreenshots,
                            sortMode
                        )

                        offsetX = 0f
                        sortMenuOpen = false
                    }
                )
            }
        }

        Spacer(
            modifier = Modifier.height(10.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(475.dp)
                .offset {
                    IntOffset(
                        x = offsetX.roundToInt(),
                        y = 0
                    )
                }
                .pointerInput(currentScreenshot.id) {

                    detectDragGestures(

                        onDrag = { change, dragAmount ->

                            change.consume()
                            offsetX += dragAmount.x

                            if (
                                kotlin.math.abs(offsetX) > 12f
                            ) {
                                suppressPreviewTapUntil =
                                    System.currentTimeMillis() + 500L
                            }
                        },

                        onDragEnd = {

                            val threshold = 250f

                            if (offsetX > threshold) {

                                keptScreenshots =
                                    keptScreenshots + currentScreenshot

                                history =
                                    history + Decision(
                                        photo = currentScreenshot,
                                        kept = true
                                    )

                                remainingScreenshots =
                                    remainingScreenshots.drop(1)

                            } else if (offsetX < -threshold) {

                                removalScreenshots =
                                    removalScreenshots + currentScreenshot

                                history =
                                    history + Decision(
                                        photo = currentScreenshot,
                                        kept = false
                                    )

                                remainingScreenshots =
                                    remainingScreenshots.drop(1)
                            }

                            offsetX = 0f
                        }
                    )
                },
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 8.dp
            )
        ) {

            Column(
                modifier = Modifier.fillMaxSize()
            ) {

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp)
                        .background(Color.Black)
                        .clickable {

                            if (
                                System.currentTimeMillis() >=
                                suppressPreviewTapUntil
                            ) {
                                isFullscreen = true
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {

                    HighQualityPhotoPreview(
                        context = context,
                        photo = currentScreenshot
                    )

                    if (offsetX < -100f) {

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Color.Red.copy(
                                        alpha = 0.28f
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {

                            Text(
                                text = "REMOVE",
                                fontSize = 38.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    if (offsetX > 100f) {

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Color.Green.copy(
                                        alpha = 0.25f
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {

                            Text(
                                text = "KEEP",
                                fontSize = 38.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {

                    Text(
                        text = currentScreenshot.name,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(
                        modifier = Modifier.height(5.dp)
                    )

                    Text(
                        text = formatFileSize(
                            context,
                            currentScreenshot.size
                        ),
                        fontSize = 17.sp
                    )
                }
            }
        }

        Spacer(
            modifier = Modifier.height(10.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {

            Text(
                text = "← REMOVE",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            OutlinedButton(
                enabled = history.isNotEmpty(),
                onClick = {

                    if (history.isNotEmpty()) {

                        val lastDecision =
                            history.last()

                        history =
                            history.dropLast(1)

                        if (lastDecision.kept) {

                            keptScreenshots =
                                keptScreenshots.dropLast(1)

                        } else {

                            removalScreenshots =
                                removalScreenshots.dropLast(1)
                        }

                        remainingScreenshots =
                            listOf(lastDecision.photo) +
                                    remainingScreenshots

                        offsetX = 0f
                    }
                }
            ) {

                Text("Undo")
            }

            Text(
                text = "KEEP →",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(
            modifier = Modifier.height(6.dp)
        )

        Text(
            text =
                "${removalScreenshots.size} selected • " +
                        formatFileSize(
                            context,
                            removalScreenshots.sumOf { it.size }
                        ) +
                        " marked",
            fontSize = 14.sp
        )

        Text(
            text = "Review mode — nothing will be deleted.",
            fontSize = 12.sp,
            color = Color.Gray
        )

        Spacer(
            modifier = Modifier.height(1.dp)
        )
    }
}

@Composable
fun ScreenshotPermissionScreen(
    onBack: () -> Unit,
    onRequestPermission: () -> Unit
) {

    BackHandler {
        onBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            text = "Screenshot Access",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(
            modifier = Modifier.height(20.dp)
        )

        Text(
            text = "SwipeClean needs photo access to find screenshots stored on your device.",
            fontSize = 18.sp,
            textAlign = TextAlign.Center
        )

        Spacer(
            modifier = Modifier.height(24.dp)
        )

        Button(
            onClick = onRequestPermission
        ) {
            Text("Allow Photo Access")
        }

        Spacer(
            modifier = Modifier.height(12.dp)
        )

        OutlinedButton(
            onClick = onBack
        ) {
            Text("Back")
        }
    }
}

@Composable
fun ScreenshotLoadingScreen(
    onBack: () -> Unit
) {

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        CircularProgressIndicator()

        Spacer(
            modifier = Modifier.height(20.dp)
        )

        Text(
            text = "Scanning screenshots...",
            fontSize = 20.sp
        )

        Spacer(
            modifier = Modifier.height(20.dp)
        )

        OutlinedButton(
            onClick = onBack
        ) {
            Text("Back")
        }
    }
}

@Composable
fun ScreenshotFinishedScreen(
    totalScreenshots: Int,
    keptCount: Int,
    removalCount: Int,
    removalBytes: Long,
    onBack: () -> Unit,
    onRestart: () -> Unit,
    onRescan: () -> Unit
) {

    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            text = "Screenshot Cleanup Complete!",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(
            modifier = Modifier.height(24.dp)
        )

        Text(
            text = "$totalScreenshots screenshots reviewed",
            fontSize = 20.sp
        )

        Spacer(
            modifier = Modifier.height(16.dp)
        )

        Text(
            text = "Kept: $keptCount",
            fontSize = 20.sp
        )

        Text(
            text = "Marked for removal: $removalCount",
            fontSize = 20.sp
        )

        Spacer(
            modifier = Modifier.height(18.dp)
        )

        Text(
            text =
                formatFileSize(
                    context,
                    removalBytes
                ) +
                        " marked for removal",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(
            modifier = Modifier.height(12.dp)
        )

        Text(
            text = "Review mode: no screenshots have been deleted.",
            fontSize = 15.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )

        Spacer(
            modifier = Modifier.height(26.dp)
        )

        Button(
            onClick = onRestart
        ) {
            Text("Start Screenshots Again")
        }

        Spacer(
            modifier = Modifier.height(10.dp)
        )

        OutlinedButton(
            onClick = onRescan
        ) {
            Text("Scan Screenshots Again")
        }

        Spacer(
            modifier = Modifier.height(10.dp)
        )

        OutlinedButton(
            onClick = onBack
        ) {
            Text("Back to Categories")
        }
    }
}

@Composable
fun VideoSwipeScreen(
    onBack: () -> Unit,
    trashedItems:
    List<TrashItem>,
    onTrashDelta:
        (String, List<TrashItem>, Set<String>) -> Unit
) {

    val context = LocalContext.current

    var permissionGranted by remember {
        mutableStateOf(hasVideoPermission(context))
    }

    var isLoading by remember {
        mutableStateOf(false)
    }

    var scanError by remember {
        mutableStateOf<String?>(null)
    }

    var remainingVideos by remember {
        mutableStateOf<List<VideoItem>>(emptyList())
    }

    var keptVideos by remember {
        mutableStateOf<List<VideoItem>>(emptyList())
    }

    var removalVideos by remember {
        mutableStateOf<List<VideoItem>>(emptyList())
    }

    var previousVideoTrashKeys by remember {
        mutableStateOf<Set<String>>(
            emptySet()
        )
    }

    LaunchedEffect(removalVideos) {

        val currentTrashItems =
            removalVideos.map {
                it.toTrashItem()
            }

        val currentKeys =
            currentTrashItems
                .map {
                    it.key
                }
                .toSet()

        val addedItems =
            currentTrashItems
                .filter {
                    it.key !in previousVideoTrashKeys
                }

        val removedKeys =
            previousVideoTrashKeys -
                    currentKeys

        if (
            addedItems.isNotEmpty() ||
            removedKeys.isNotEmpty()
        ) {

            onTrashDelta(
                "videos",
                addedItems,
                removedKeys
            )
        }

        previousVideoTrashKeys =
            currentKeys
    }

    var history by remember {
        mutableStateOf<List<VideoDecision>>(emptyList())
    }

    var sortMode by remember {
        mutableStateOf(SortMode.LARGEST_FIRST)
    }

    var sortMenuOpen by remember {
        mutableStateOf(false)
    }

    var offsetX by remember {
        mutableFloatStateOf(0f)
    }

    var suppressPreviewTapUntil by remember {
        mutableLongStateOf(0L)
    }

    var isFullscreen by remember {
        mutableStateOf(false)
    }

    BackHandler {
        if (isFullscreen) {
            isFullscreen = false
        } else {
            onBack()
        }
    }

    var scanVersion by remember {
        mutableIntStateOf(0)
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->
            permissionGranted = granted
        }

    LaunchedEffect(permissionGranted, scanVersion) {

        if (permissionGranted) {

            isLoading = true
            scanError = null

            try {

                val loadedVideos = withContext(Dispatchers.IO) {
                    loadVideos(context)
                }

                val reviewableVideos =
                    loadedVideos.filterNot {
                        isMarkedForTrash(
                            candidate =
                                it.toTrashItem(),
                            trashedItems =
                                trashedItems
                        )
                    }

                remainingVideos = sortVideos(
                    reviewableVideos,
                    sortMode
                )

                keptVideos = emptyList()

                previousVideoTrashKeys =
                    emptySet()

                removalVideos = emptyList()
                history = emptyList()
                offsetX = 0f

            } catch (exception: Exception) {

                scanError =
                    exception.message ?: "Unknown video scanning error"

            } finally {

                isLoading = false
            }
        }
    }

    if (!permissionGranted) {

        VideoPermissionScreen(
            onBack = onBack,
            onRequestPermission = {

                val permission =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        Manifest.permission.READ_MEDIA_VIDEO
                    } else {
                        Manifest.permission.READ_EXTERNAL_STORAGE
                    }

                permissionLauncher.launch(permission)
            }
        )

        return
    }

    if (isLoading) {

        VideoLoadingScreen(
            onBack = onBack
        )

        return
    }

    if (scanError != null) {

        ErrorScreen(
            message = scanError ?: "Unknown error",
            onBack = onBack,
            onRetry = {
                scanVersion++
            }
        )

        return
    }

    val totalVideos =
        remainingVideos.size +
                keptVideos.size +
                removalVideos.size

    val reviewedVideos =
        keptVideos.size +
                removalVideos.size

    if (remainingVideos.isEmpty()) {

        VideoFinishedScreen(
            totalVideos = totalVideos,
            keptCount = keptVideos.size,
            removalCount = removalVideos.size,
            removalBytes = removalVideos.sumOf { it.size },
            onBack = onBack,
            onRestart = {

                remainingVideos = sortVideos(
                    keptVideos,
                    sortMode
                )

                keptVideos = emptyList()

                previousVideoTrashKeys =
                    emptySet()

                removalVideos = emptyList()
                history = emptyList()
                offsetX = 0f
            },
            onRescan = {
                scanVersion++
            }
        )

        return
    }

    val currentVideo = remainingVideos.first()

    if (isFullscreen) {

        FullscreenVideoViewer(
            context = context,
            video = currentVideo,
            positionText = "${reviewedVideos + 1} / $totalVideos",
            canUndo = history.isNotEmpty(),
            onClose = {
                isFullscreen = false
            },
            onUndo = {

                if (history.isNotEmpty()) {

                    val lastDecision = history.last()
                    history = history.dropLast(1)

                    if (lastDecision.kept) {
                        keptVideos = keptVideos.dropLast(1)
                    } else {
                        removalVideos = removalVideos.dropLast(1)
                    }

                    remainingVideos =
                        listOf(lastDecision.video) + remainingVideos

                    offsetX = 0f
                }
            },
            onKeep = {

                keptVideos =
                    keptVideos + currentVideo

                history =
                    history + VideoDecision(
                        video = currentVideo,
                        kept = true
                    )

                remainingVideos =
                    remainingVideos.drop(1)

                offsetX = 0f
            },
            onRemove = {

                removalVideos =
                    removalVideos + currentVideo

                history =
                    history + VideoDecision(
                        video = currentVideo,
                        kept = false
                    )

                remainingVideos =
                    remainingVideos.drop(1)

                offsetX = 0f
            }
        )

        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(
                start = 18.dp,
                end = 18.dp,
                top = 54.dp,
                bottom = 2.dp
            ),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Button(
                onClick = {
                    onBack()
                },
                modifier = Modifier.height(48.dp)
            ) {
                Text("← Categories")
            }

            Spacer(
                modifier = Modifier.width(14.dp)
            )

            Column(
                modifier = Modifier.weight(1f)
            ) {

                Text(
                    text = "Videos",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "${reviewedVideos + 1} / $totalVideos",
                    fontSize = 14.sp
                )
            }
        }

        Spacer(
            modifier = Modifier.height(10.dp)
        )

        Box {

            OutlinedButton(
                onClick = {
                    sortMenuOpen = true
                }
            ) {

                Text(
                    text = sortMode.label
                )
            }

            DropdownMenu(
                expanded = sortMenuOpen,
                onDismissRequest = {
                    sortMenuOpen = false
                }
            ) {

                DropdownMenuItem(
                    text = {
                        Text("Largest → Smallest")
                    },
                    onClick = {

                        sortMode = SortMode.LARGEST_FIRST

                        remainingVideos = sortVideos(
                            remainingVideos,
                            sortMode
                        )

                        offsetX = 0f
                        sortMenuOpen = false
                    }
                )

                DropdownMenuItem(
                    text = {
                        Text("Smallest → Largest")
                    },
                    onClick = {

                        sortMode = SortMode.SMALLEST_FIRST

                        remainingVideos = sortVideos(
                            remainingVideos,
                            sortMode
                        )

                        offsetX = 0f
                        sortMenuOpen = false
                    }
                )

                DropdownMenuItem(
                    text = {
                        Text("Date Added")
                    },
                    onClick = {

                        sortMode = SortMode.DATE_ADDED

                        remainingVideos = sortVideos(
                            remainingVideos,
                            sortMode
                        )

                        offsetX = 0f
                        sortMenuOpen = false
                    }
                )
            }
        }

        Spacer(
            modifier = Modifier.height(10.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(475.dp)
                .offset {
                    IntOffset(
                        x = offsetX.roundToInt(),
                        y = 0
                    )
                }
                .pointerInput(currentVideo.id) {

                    detectDragGestures(

                        onDrag = { change, dragAmount ->

                            change.consume()
                            offsetX += dragAmount.x

                            if (
                                kotlin.math.abs(offsetX) > 12f
                            ) {
                                suppressPreviewTapUntil =
                                    System.currentTimeMillis() + 500L
                            }
                        },

                        onDragEnd = {

                            val threshold = 250f

                            if (offsetX > threshold) {

                                keptVideos =
                                    keptVideos + currentVideo

                                history =
                                    history + VideoDecision(
                                        video = currentVideo,
                                        kept = true
                                    )

                                remainingVideos =
                                    remainingVideos.drop(1)

                            } else if (offsetX < -threshold) {

                                removalVideos =
                                    removalVideos + currentVideo

                                history =
                                    history + VideoDecision(
                                        video = currentVideo,
                                        kept = false
                                    )

                                remainingVideos =
                                    remainingVideos.drop(1)
                            }

                            offsetX = 0f
                        }
                    )
                },
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(
                defaultElevation = 8.dp
            )
        ) {

            Column(
                modifier = Modifier.fillMaxSize()
            ) {

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp)
                        .background(Color.Black)
                        .clickable {

                            if (
                                System.currentTimeMillis() >=
                                suppressPreviewTapUntil
                            ) {
                                isFullscreen = true
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {

                    VideoPreview(
                        context = context,
                        video = currentVideo
                    )

                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(12.dp)
                            .background(
                                Color.Black.copy(alpha = 0.65f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(
                                horizontal = 10.dp,
                                vertical = 5.dp
                            )
                    ) {
                        Text(
                            text = formatDuration(currentVideo.durationMs),
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (offsetX < -100f) {

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Color.Red.copy(
                                        alpha = 0.28f
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {

                            Text(
                                text = "REMOVE",
                                fontSize = 38.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    if (offsetX > 100f) {

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Color.Green.copy(
                                        alpha = 0.25f
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {

                            Text(
                                text = "KEEP",
                                fontSize = 38.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {

                    Text(
                        text = currentVideo.name,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(
                        modifier = Modifier.height(5.dp)
                    )

                    Text(
                        text =
                            formatFileSize(
                                context,
                                currentVideo.size
                            ) +
                                    " • " +
                                    formatDuration(
                                        currentVideo.durationMs
                                    ),
                        fontSize = 17.sp
                    )
                }
            }
        }

        Spacer(
            modifier = Modifier.height(10.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {

            Text(
                text = "← REMOVE",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            OutlinedButton(
                enabled = history.isNotEmpty(),
                onClick = {

                    if (history.isNotEmpty()) {

                        val lastDecision =
                            history.last()

                        history =
                            history.dropLast(1)

                        if (lastDecision.kept) {

                            keptVideos =
                                keptVideos.dropLast(1)

                        } else {

                            removalVideos =
                                removalVideos.dropLast(1)
                        }

                        remainingVideos =
                            listOf(lastDecision.video) +
                                    remainingVideos

                        offsetX = 0f
                    }
                }
            ) {

                Text("Undo")
            }

            Text(
                text = "KEEP →",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(
            modifier = Modifier.height(6.dp)
        )

        Text(
            text =
                "${removalVideos.size} selected • " +
                        formatFileSize(
                            context,
                            removalVideos.sumOf { it.size }
                        ) +
                        " marked",
            fontSize = 14.sp
        )

        Text(
            text = "Review mode — nothing will be deleted.",
            fontSize = 12.sp,
            color = Color.Gray
        )

        Spacer(
            modifier = Modifier.height(1.dp)
        )
    }
}

@Composable
fun PlaybackTimelineControls(
    player: ExoPlayer
) {

    var currentPosition by remember(player) {
        mutableLongStateOf(0L)
    }

    var duration by remember(player) {
        mutableLongStateOf(0L)
    }

    var isPlaying by remember(player) {
        mutableStateOf(false)
    }

    LaunchedEffect(player) {

        while (true) {

            currentPosition =
                player.currentPosition.coerceAtLeast(0L)

            duration =
                player.duration
                    .takeIf { it > 0L }
                    ?: 0L

            isPlaying =
                player.isPlaying

            delay(200L)
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth()
    ) {

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Button(
                onClick = {

                    if (player.isPlaying) {
                        player.pause()
                    } else {
                        player.play()
                    }
                }
            ) {

                Text(
                    if (isPlaying) {
                        "Pause"
                    } else {
                        "Play"
                    }
                )
            }

            Spacer(
                modifier = Modifier.width(12.dp)
            )

            Text(
                text = formatDuration(currentPosition),
                color = Color.White,
                fontSize = 14.sp
            )

            Spacer(
                modifier = Modifier.weight(1f)
            )

            Text(
                text = formatDuration(duration),
                color = Color.White,
                fontSize = 14.sp
            )
        }

        Slider(
            value =
                if (duration > 0L) {

                    currentPosition
                        .coerceIn(
                            0L,
                            duration
                        )
                        .toFloat() /
                            duration.toFloat()

                } else {

                    0f
                },
            onValueChange = { fraction ->

                if (duration > 0L) {

                    val targetPosition =
                        (
                                fraction.coerceIn(
                                    0f,
                                    1f
                                ) *
                                        duration.toFloat()
                                ).toLong()

                    currentPosition =
                        targetPosition

                    player.seekTo(
                        targetPosition
                    )
                }
            },
            enabled = duration > 0L,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
fun FullscreenPhotoViewer(
    context: Context,
    photo: PhotoItem,
    positionText: String,
    canUndo: Boolean,
    onClose: () -> Unit,
    onUndo: () -> Unit,
    onKeep: () -> Unit,
    onRemove: () -> Unit
) {

    var dragOffsetX by remember(photo.id) {
        mutableFloatStateOf(0f)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(photo.id) {

                detectDragGestures(

                    onDrag = { change, dragAmount ->

                        change.consume()
                        dragOffsetX += dragAmount.x
                    },

                    onDragEnd = {

                        val threshold = 250f

                        when {
                            dragOffsetX > threshold -> onKeep()
                            dragOffsetX < -threshold -> onRemove()
                        }

                        dragOffsetX = 0f
                    }
                )
            }
    ) {

        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset {
                    IntOffset(
                        x = dragOffsetX.roundToInt(),
                        y = 0
                    )
                },
            contentAlignment = Alignment.Center
        ) {

            HighQualityPhotoPreview(
                context = context,
                photo = photo
            )

            if (dragOffsetX < -100f) {

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Color.Red.copy(
                                alpha = 0.25f
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {

                    Text(
                        text = "REMOVE",
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            if (dragOffsetX > 100f) {

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Color.Green.copy(
                                alpha = 0.22f
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {

                    Text(
                        text = "KEEP",
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(
                    start = 14.dp,
                    end = 14.dp,
                    top = 10.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Button(
                onClick = onClose
            ) {
                Text("← Back")
            }


            Spacer(
                modifier = Modifier.width(8.dp)
            )

            Button(
                onClick = onUndo,
                enabled = canUndo
            ) {
                Text("↶ Undo")
            }

            Spacer(
                modifier = Modifier.weight(1f)
            )
            Text(
                text = positionText,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Color.Black.copy(
                        alpha = 0.72f
                    )
                )
                .padding(
                    start = 18.dp,
                    end = 18.dp,
                    top = 12.dp,
                    bottom = 18.dp
                )
        ) {

            Text(
                text = photo.name,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(
                modifier = Modifier.height(4.dp)
            )

            Text(
                text = formatFileSize(
                    context,
                    photo.size
                ),
                color = Color.White,
                fontSize = 15.sp
            )

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {

                Text(
                    text = "← REMOVE",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "KEEP →",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(
                modifier = Modifier.height(4.dp)
            )

            Text(
                text = "Review mode — nothing will be deleted.",
                color = Color.LightGray,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun FullscreenVideoViewer(
    context: Context,
    video: VideoItem,
    positionText: String,
    canUndo: Boolean,
    onClose: () -> Unit,
    onUndo: () -> Unit,
    onKeep: () -> Unit,
    onRemove: () -> Unit
) {

    var dragOffsetX by remember(video.id) {
        mutableFloatStateOf(0f)
    }

    val player =
        remember(video.uri) {

            ExoPlayer.Builder(context)
                .build()
                .apply {

                    setMediaItem(
                        MediaItem.fromUri(
                            video.uri
                        )
                    )

                    prepare()

                    playWhenReady = true
                }
        }

    DisposableEffect(player) {

        onDispose {

            player.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(video.id) {

                detectDragGestures(

                    onDrag = { change, dragAmount ->

                        change.consume()

                        dragOffsetX += dragAmount.x
                    },

                    onDragEnd = {

                        val threshold = 250f

                        when {

                            dragOffsetX > threshold -> {
                                onKeep()
                            }

                            dragOffsetX < -threshold -> {
                                onRemove()
                            }
                        }

                        dragOffsetX = 0f
                    }
                )
            }
    ) {

        AndroidView(
            modifier = Modifier
                .fillMaxSize()
                .offset {

                    IntOffset(
                        x = dragOffsetX.roundToInt(),
                        y = 0
                    )
                },
            factory = { androidContext ->

                PlayerView(androidContext).apply {

                    this.player = player

                    useController = false

                    resizeMode =
                        AspectRatioFrameLayout.RESIZE_MODE_FIT

                    keepScreenOn = true

                    setShowBuffering(
                        PlayerView.SHOW_BUFFERING_WHEN_PLAYING
                    )
                }
            },
            update = { playerView ->

                if (playerView.player !== player) {

                    playerView.player = player
                }
            }
        )

        if (dragOffsetX < -100f) {

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Color.Red.copy(
                            alpha = 0.25f
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {

                Text(
                    text = "REMOVE",
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        if (dragOffsetX > 100f) {

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Color.Green.copy(
                            alpha = 0.22f
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {

                Text(
                    text = "KEEP",
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(
                    start = 14.dp,
                    end = 14.dp,
                    top = 10.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Button(
                onClick = onClose
            ) {

                Text("← Back")
            }


            Spacer(
                modifier = Modifier.width(8.dp)
            )

            Button(
                onClick = onUndo,
                enabled = canUndo
            ) {
                Text("↶ Undo")
            }

            Spacer(
                modifier = Modifier.weight(1f)
            )
            Text(
                text = positionText,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Color.Black.copy(
                        alpha = 0.72f
                    )
                )
                .padding(
                    start = 18.dp,
                    end = 18.dp,
                    top = 12.dp,
                    bottom = 18.dp
                )
        ) {

            PlaybackTimelineControls(
                player = player
            )

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            Text(
                text = video.name,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(
                modifier = Modifier.height(4.dp)
            )

            Text(
                text =
                    formatFileSize(
                        context,
                        video.size
                    ) +
                            " • " +
                            formatDuration(
                                video.durationMs
                            ),
                color = Color.White,
                fontSize = 15.sp
            )

            Spacer(
                modifier = Modifier.height(8.dp)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {

                Text(
                    text = "← REMOVE",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "KEEP →",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(
                modifier = Modifier.height(4.dp)
            )

            Text(
                text = "Review mode — nothing will be deleted.",
                color = Color.LightGray,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun VideoPreview(
    context: Context,
    video: VideoItem
) {

    var imageBitmap by remember(video.uri) {
        mutableStateOf<ImageBitmap?>(null)
    }

    var failed by remember(video.uri) {
        mutableStateOf(false)
    }

    LaunchedEffect(video.uri) {

        imageBitmap = null
        failed = false

        try {

            val bitmap =
                withContext(Dispatchers.IO) {
                    loadVideoPreview(
                        context = context,
                        uri = video.uri,
                        maxDimension = 1600
                    )
                }

            if (bitmap != null) {

                imageBitmap =
                    bitmap.asImageBitmap()

            } else {

                failed = true
            }

        } catch (_: Exception) {

            failed = true
        }
    }

    when {

        imageBitmap != null -> {

            Image(
                bitmap = imageBitmap!!,
                contentDescription = video.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }

        failed -> {

            Text(
                text = "Video preview unavailable",
                color = Color.White,
                fontSize = 18.sp
            )
        }

        else -> {

            CircularProgressIndicator()
        }
    }
}

@Composable
fun VideoPermissionScreen(
    onBack: () -> Unit,
    onRequestPermission: () -> Unit
) {

    BackHandler {
        onBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            text = "Video Access",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(
            modifier = Modifier.height(20.dp)
        )

        Text(
            text = "SwipeClean needs video access to build the Videos cleanup queue.",
            fontSize = 18.sp,
            textAlign = TextAlign.Center
        )

        Spacer(
            modifier = Modifier.height(24.dp)
        )

        Button(
            onClick = onRequestPermission
        ) {

            Text(
                text = "Allow Video Access"
            )
        }

        Spacer(
            modifier = Modifier.height(12.dp)
        )

        OutlinedButton(
            onClick = onBack
        ) {

            Text("Back")
        }
    }
}

@Composable
fun VideoLoadingScreen(
    onBack: () -> Unit
) {

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        CircularProgressIndicator()

        Spacer(
            modifier = Modifier.height(20.dp)
        )

        Text(
            text = "Scanning videos...",
            fontSize = 20.sp
        )

        Spacer(
            modifier = Modifier.height(20.dp)
        )

        OutlinedButton(
            onClick = onBack
        ) {

            Text("Back")
        }
    }
}

@Composable
fun VideoFinishedScreen(
    totalVideos: Int,
    keptCount: Int,
    removalCount: Int,
    removalBytes: Long,
    onBack: () -> Unit,
    onRestart: () -> Unit,
    onRescan: () -> Unit
) {

    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            text = "Video Cleanup Complete!",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(
            modifier = Modifier.height(24.dp)
        )

        Text(
            text = "$totalVideos videos reviewed",
            fontSize = 20.sp
        )

        Spacer(
            modifier = Modifier.height(16.dp)
        )

        Text(
            text = "Kept: $keptCount",
            fontSize = 20.sp
        )

        Text(
            text = "Marked for removal: $removalCount",
            fontSize = 20.sp
        )

        Spacer(
            modifier = Modifier.height(18.dp)
        )

        Text(
            text =
                formatFileSize(
                    context,
                    removalBytes
                ) +
                        " marked for removal",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(
            modifier = Modifier.height(12.dp)
        )

        Text(
            text = "Review mode: no videos have been deleted.",
            fontSize = 15.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )

        Spacer(
            modifier = Modifier.height(26.dp)
        )

        Button(
            onClick = onRestart
        ) {

            Text(
                text = "Start Videos Again"
            )
        }

        Spacer(
            modifier = Modifier.height(10.dp)
        )

        OutlinedButton(
            onClick = onRescan
        ) {

            Text(
                text = "Scan Videos Again"
            )
        }

        Spacer(
            modifier = Modifier.height(10.dp)
        )

        OutlinedButton(
            onClick = onBack
        ) {

            Text(
                text = "Back to Categories"
            )
        }
    }
}

@Composable
fun HighQualityPhotoPreview(
    context: Context,
    photo: PhotoItem
) {

    var imageBitmap by remember(photo.uri) {
        mutableStateOf<ImageBitmap?>(null)
    }

    var failed by remember(photo.uri) {
        mutableStateOf(false)
    }

    LaunchedEffect(photo.uri) {

        imageBitmap = null
        failed = false

        try {

            val bitmap =
                withContext(Dispatchers.IO) {
                    loadHighQualityPreview(
                        context = context,
                        uri = photo.uri,
                        maxDimension = 2200
                    )
                }

            if (bitmap != null) {

                imageBitmap =
                    bitmap.asImageBitmap()

            } else {

                failed = true
            }

        } catch (_: Exception) {

            failed = true
        }
    }

    when {

        imageBitmap != null -> {

            Image(
                bitmap = imageBitmap!!,
                contentDescription = photo.name,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        }

        failed -> {

            Text(
                text = "Preview unavailable",
                color = Color.White,
                fontSize = 18.sp
            )
        }

        else -> {

            CircularProgressIndicator()
        }
    }
}

@Composable
fun PermissionScreen(
    onBack: () -> Unit,
    onRequestPermission: () -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            text = "Photo Access",
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(
            modifier = Modifier.height(20.dp)
        )

        Text(
            text = "SwipeClean needs photo access to build the Photos cleanup queue.",
            fontSize = 18.sp,
            textAlign = TextAlign.Center
        )

        Spacer(
            modifier = Modifier.height(24.dp)
        )

        Button(
            onClick = onRequestPermission
        ) {

            Text(
                text = "Allow Photo Access"
            )
        }

        Spacer(
            modifier = Modifier.height(12.dp)
        )

        OutlinedButton(
            onClick = onBack
        ) {

            Text("Back")
        }
    }
}

@Composable
fun LoadingScreen(
    onBack: () -> Unit
) {

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        CircularProgressIndicator()

        Spacer(
            modifier = Modifier.height(20.dp)
        )

        Text(
            text = "Scanning photos...",
            fontSize = 20.sp
        )

        Spacer(
            modifier = Modifier.height(20.dp)
        )

        OutlinedButton(
            onClick = onBack
        ) {

            Text("Back")
        }
    }
}

@Composable
fun ErrorScreen(
    message: String,
    onBack: () -> Unit,
    onRetry: () -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            text = "Scan Error",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(
            modifier = Modifier.height(16.dp)
        )

        Text(
            text = message,
            textAlign = TextAlign.Center
        )

        Spacer(
            modifier = Modifier.height(24.dp)
        )

        Button(
            onClick = onRetry
        ) {

            Text(
                text = "Try Again"
            )
        }

        Spacer(
            modifier = Modifier.height(12.dp)
        )

        OutlinedButton(
            onClick = onBack
        ) {

            Text("Back")
        }
    }
}

@Composable
fun FinishedScreen(
    totalPhotos: Int,
    keptCount: Int,
    removalCount: Int,
    removalBytes: Long,
    onBack: () -> Unit,
    onRestart: () -> Unit,
    onRescan: () -> Unit
) {

    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            text = "Photo Cleanup Complete!",
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(
            modifier = Modifier.height(24.dp)
        )

        Text(
            text = "$totalPhotos photos reviewed",
            fontSize = 20.sp
        )

        Spacer(
            modifier = Modifier.height(16.dp)
        )

        Text(
            text = "Kept: $keptCount",
            fontSize = 20.sp
        )

        Text(
            text = "Marked for removal: $removalCount",
            fontSize = 20.sp
        )

        Spacer(
            modifier = Modifier.height(18.dp)
        )

        Text(
            text =
                formatFileSize(
                    context,
                    removalBytes
                ) +
                        " marked for removal",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(
            modifier = Modifier.height(12.dp)
        )

        Text(
            text = "Review mode: no files have been deleted.",
            fontSize = 15.sp,
            color = Color.Gray,
            textAlign = TextAlign.Center
        )

        Spacer(
            modifier = Modifier.height(26.dp)
        )

        Button(
            onClick = onRestart
        ) {

            Text(
                text = "Start Photos Again"
            )
        }

        Spacer(
            modifier = Modifier.height(10.dp)
        )

        OutlinedButton(
            onClick = onRescan
        ) {

            Text(
                text = "Scan Photos Again"
            )
        }

        Spacer(
            modifier = Modifier.height(10.dp)
        )

        OutlinedButton(
            onClick = onBack
        ) {

            Text(
                text = "Back to Categories"
            )
        }
    }
}

fun hasPhotoPermission(
    context: Context
): Boolean {

    val permission =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    return ContextCompat.checkSelfPermission(
        context,
        permission
    ) == PackageManager.PERMISSION_GRANTED
}

fun hasEverythingMediaAccess(
    context: Context
): Boolean {

    return if (
        Build.VERSION.SDK_INT >=
        Build.VERSION_CODES.TIRAMISU
    ) {

        ContextCompat
            .checkSelfPermission(
                context,
                Manifest.permission
                    .READ_MEDIA_IMAGES
            ) ==
                PackageManager
                    .PERMISSION_GRANTED &&
                ContextCompat
                    .checkSelfPermission(
                        context,
                        Manifest.permission
                            .READ_MEDIA_VIDEO
                    ) ==
                PackageManager
                    .PERMISSION_GRANTED &&
                ContextCompat
                    .checkSelfPermission(
                        context,
                        Manifest.permission
                            .READ_MEDIA_AUDIO
                    ) ==
                PackageManager
                    .PERMISSION_GRANTED

    } else {

        ContextCompat
            .checkSelfPermission(
                context,
                Manifest.permission
                    .READ_EXTERNAL_STORAGE
            ) ==
                PackageManager
                    .PERMISSION_GRANTED
    }
}

fun loadEverythingItems(
    context: Context,
    usageAccessGranted: Boolean
): List<EverythingItem> {

    val photos =
        loadPhotos(
            context
        )

    val videos =
        loadVideos(
            context
        )

    val audio =
        loadAudio(
            context
        )

    val files =
        loadAllSharedFiles(
            context
        )

    val apps =
        loadInstalledApps(
            context = context,
            usageAccessGranted =
                usageAccessGranted
        )

    val result =
        mutableListOf<EverythingItem>()

    photos.forEach {
            photo ->

        result.add(
            EverythingItem(
                key =
                    "photo:${photo.uri}",
                kind =
                    EverythingKind.PHOTO,
                name =
                    photo.name,
                sizeBytes =
                    photo.size,
                dateAdded =
                    photo.dateAdded,
                photo =
                    photo
            )
        )
    }

    videos.forEach {
            video ->

        result.add(
            EverythingItem(
                key =
                    "video:${video.uri}",
                kind =
                    EverythingKind.VIDEO,
                name =
                    video.name,
                sizeBytes =
                    video.size,
                dateAdded =
                    video.dateAdded,
                video =
                    video
            )
        )
    }

    audio.forEach {
            audioItem ->

        result.add(
            EverythingItem(
                key =
                    "audio:${audioItem.uri}",
                kind =
                    EverythingKind.AUDIO,
                name =
                    audioItem.name,
                sizeBytes =
                    audioItem.size,
                dateAdded =
                    audioItem.dateAdded,
                audio =
                    audioItem
            )
        )
    }

    val indexedMediaKeys =
        (
                photos.map {
                    everythingMediaDedupKey(
                        it.name,
                        it.size
                    )
                } +
                        videos.map {
                            everythingMediaDedupKey(
                                it.name,
                                it.size
                            )
                        } +
                        audio.map {
                            everythingMediaDedupKey(
                                it.name,
                                it.size
                            )
                        }
                )
            .toSet()

    files.forEach {
            file ->

        val previewType =
            inAppPreviewType(
                file
            )

        val isMediaFile =
            previewType ==
                    InAppPreviewType.IMAGE ||
                    previewType ==
                    InAppPreviewType.VIDEO ||
                    previewType ==
                    InAppPreviewType.AUDIO

        val likelyAlreadyIndexed =
            isMediaFile &&
                    indexedMediaKeys.contains(
                        everythingMediaDedupKey(
                            file.name,
                            file.size
                        )
                    )

        if (
            !likelyAlreadyIndexed
        ) {

            result.add(
                EverythingItem(
                    key =
                        "file:${everythingCanonicalPath(file.absolutePath)}",
                    kind =
                        EverythingKind.FILE,
                    name =
                        file.name,
                    sizeBytes =
                        file.size,
                    dateAdded =
                        file.dateAdded,
                    file =
                        file
                )
            )
        }
    }

    apps.forEach {
            app ->

        result.add(
            EverythingItem(
                key =
                    "app:${app.packageName}",
                kind =
                    EverythingKind.APP,
                name =
                    app.name,
                sizeBytes =
                    app.sizeBytes,
                dateAdded =
                    (
                            app.firstInstallTime /
                                    1000L
                            )
                        .coerceAtLeast(
                            0L
                        ),
                lastUsedTime =
                    app.lastUsedTime,
                app =
                    app
            )
        )
    }

    return result
        .distinctBy {
            it.key
        }
}

fun loadAllSharedFiles(
    context: Context
): List<FileItem> {

    val items =
        mutableListOf<FileItem>()

    val roots =
        getSharedStorageRoots(
            context
        )

    val visitedDirectories =
        mutableSetOf<String>()

    val stack =
        ArrayDeque<File>()

    roots.forEach {
            root ->

        if (
            root.exists() &&
            root.isDirectory
        ) {

            stack.add(
                root
            )
        }
    }

    while (
        stack.isNotEmpty()
    ) {

        val current =
            stack.removeLast()

        val canonicalPath =
            everythingCanonicalPath(
                current.absolutePath
            )

        if (
            !visitedDirectories
                .add(
                    canonicalPath
                )
        ) {

            continue
        }

        if (
            isAndroidProtectedFolder(
                current
            )
        ) {

            continue
        }

        val children =
            try {

                current.listFiles()

            } catch (
                _: Exception
            ) {

                null
            }
                ?: continue

        children.forEach {
                child ->

            try {

                if (
                    child.isDirectory
                ) {

                    if (
                        !isAndroidProtectedFolder(
                            child
                        )
                    ) {

                        stack.add(
                            child
                        )
                    }

                } else if (
                    child.isFile
                ) {

                    val name =
                        child.name

                    val extension =
                        fileExtension(
                            name
                        )

                    val mimeType =
                        MimeTypeMap
                            .getSingleton()
                            .getMimeTypeFromExtension(
                                extension
                            )

                    val absolutePath =
                        child.absolutePath

                    val relativePath =
                        sharedRelativePath(
                            roots =
                                roots,
                            file =
                                child
                        )

                    items.add(
                        FileItem(
                            id =
                                absolutePath
                                    .hashCode()
                                    .toLong(),
                            uri =
                                Uri.fromFile(
                                    child
                                ),
                            name =
                                name,
                            size =
                                child.length()
                                    .coerceAtLeast(
                                        0L
                                    ),
                            dateAdded =
                                (
                                        child
                                            .lastModified() /
                                                1000L
                                        )
                                    .coerceAtLeast(
                                        0L
                                    ),
                            mimeType =
                                mimeType,
                            relativePath =
                                relativePath,
                            extension =
                                extension,
                            absolutePath =
                                absolutePath
                        )
                    )
                }

            } catch (
                _: Exception
            ) {

                // Skip inaccessible individual items.
            }
        }
    }

    return items
        .distinctBy {
            everythingCanonicalPath(
                it.absolutePath
            )
        }
}

fun everythingCanonicalPath(
    path: String
): String {

    return try {

        File(path)
            .canonicalPath

    } catch (
        _: Exception
    ) {

        File(path)
            .absolutePath
    }
}

fun everythingMediaDedupKey(
    name: String,
    size: Long
): String {

    return name
        .trim()
        .lowercase() +
            "|" +
            size.toString()
}

fun sortEverythingItems(
    items: List<EverythingItem>,
    sortMode: EverythingSortMode
): List<EverythingItem> {

    return when (
        sortMode
    ) {

        EverythingSortMode
            .LARGEST_FIRST -> {

            items.sortedWith(
                compareByDescending<EverythingItem> {
                    it.sizeBytes
                }.thenByDescending {
                    it.dateAdded
                }
            )
        }

        EverythingSortMode
            .SMALLEST_FIRST -> {

            items.sortedWith(
                compareBy<EverythingItem> {
                    it.sizeBytes
                }.thenByDescending {
                    it.dateAdded
                }
            )
        }

        EverythingSortMode
            .DATE_ADDED -> {

            items.sortedByDescending {
                it.dateAdded
            }
        }

        EverythingSortMode
            .LAST_USED -> {

            items.sortedWith(
                compareByDescending<EverythingItem> {

                    if (
                        it.kind ==
                        EverythingKind.APP
                    ) {

                        it.lastUsedTime

                    } else {

                        Long.MIN_VALUE
                    }
                }
                    .thenByDescending {
                        it.dateAdded
                    }
            )
        }
    }
}

fun everythingTypeLabel(
    item: EverythingItem
): String {

    return when (
        item.kind
    ) {

        EverythingKind.PHOTO ->
            "PHOTO"

        EverythingKind.VIDEO ->
            "VIDEO"

        EverythingKind.AUDIO ->
            "AUDIO"

        EverythingKind.FILE ->
            fileTypeText(
                item.file!!
            )

        EverythingKind.APP ->
            "APP"
    }
}

fun hasUsageAccess(
    context: Context
): Boolean {

    val appOps =
        context.getSystemService(
            Context.APP_OPS_SERVICE
        ) as AppOpsManager

    return appOps.checkOpNoThrow(
        AppOpsManager
            .OPSTR_GET_USAGE_STATS,
        Process.myUid(),
        context.packageName
    ) ==
            AppOpsManager.MODE_ALLOWED
}

fun loadInstalledApps(
    context: Context,
    usageAccessGranted: Boolean
): List<AppItem> {

    val packageManager =
        context.packageManager

    val lastUsed =
        if (usageAccessGranted) {
            loadLastUsedMap(context)
        } else {
            emptyMap()
        }

    val applications =
        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.TIRAMISU
        ) {
            packageManager.getInstalledApplications(
                PackageManager
                    .ApplicationInfoFlags
                    .of(0L)
            )
        } else {
            @Suppress("DEPRECATION")
            packageManager
                .getInstalledApplications(0)
        }

    val apps =
        mutableListOf<AppItem>()

    for (appInfo in applications) {

        if (
            appInfo.packageName ==
            context.packageName
        ) {
            continue
        }

        val isSystem =
            (
                    appInfo.flags and
                            android.content.pm
                                .ApplicationInfo
                                .FLAG_SYSTEM
                    ) != 0

        val isUpdatedSystem =
            (
                    appInfo.flags and
                            android.content.pm
                                .ApplicationInfo
                                .FLAG_UPDATED_SYSTEM_APP
                    ) != 0

        if (
            isSystem &&
            !isUpdatedSystem
        ) {
            continue
        }

        val packageInfo =
            try {
                if (
                    Build.VERSION.SDK_INT >=
                    Build.VERSION_CODES.TIRAMISU
                ) {
                    packageManager.getPackageInfo(
                        appInfo.packageName,
                        PackageManager
                            .PackageInfoFlags
                            .of(0L)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    packageManager.getPackageInfo(
                        appInfo.packageName,
                        0
                    )
                }
            } catch (_: Exception) {
                null
            }

        val label =
            try {
                packageManager
                    .getApplicationLabel(
                        appInfo
                    )
                    .toString()
            } catch (_: Exception) {
                appInfo.packageName
            }

        var sizeBytes =
            File(
                appInfo.sourceDir
            )
                .takeIf {
                    it.exists()
                }
                ?.length()
                ?: 0L

        appInfo
            .splitSourceDirs
            ?.forEach { split ->

                sizeBytes +=
                    File(split)
                        .takeIf {
                            it.exists()
                        }
                        ?.length()
                        ?: 0L
            }

        apps.add(
            AppItem(
                packageName =
                    appInfo.packageName,
                name = label,
                versionName =
                    packageInfo
                        ?.versionName
                        ?: "",
                sizeBytes = sizeBytes,
                firstInstallTime =
                    packageInfo
                        ?.firstInstallTime
                        ?: 0L,
                lastUsedTime =
                    lastUsed[
                        appInfo.packageName
                    ] ?: 0L
            )
        )
    }

    return apps.distinctBy {
        it.packageName
    }
}

fun loadLastUsedMap(
    context: Context
): Map<String, Long> {

    val manager =
        context.getSystemService(
            Context.USAGE_STATS_SERVICE
        ) as UsageStatsManager

    val end =
        System.currentTimeMillis()

    val start =
        end -
                (
                        3650L *
                                24L *
                                60L *
                                60L *
                                1000L
                        )

    return try {
        manager
            .queryUsageStats(
                UsageStatsManager
                    .INTERVAL_BEST,
                start,
                end
            )
            .orEmpty()
            .groupBy {
                it.packageName
            }
            .mapValues { entry ->

                entry.value
                    .maxOfOrNull {
                        it.lastTimeUsed
                    }
                    ?: 0L
            }
    } catch (_: Exception) {
        emptyMap()
    }
}

fun sortApps(
    apps: List<AppItem>,
    sortMode: AppSortMode
): List<AppItem> {

    return when (sortMode) {

        AppSortMode.LARGEST_FIRST -> {
            apps.sortedWith(
                compareByDescending<AppItem> {
                    it.sizeBytes
                }.thenBy {
                    it.name.lowercase()
                }
            )
        }

        AppSortMode.SMALLEST_FIRST -> {
            apps.sortedWith(
                compareBy<AppItem> {
                    it.sizeBytes
                }.thenBy {
                    it.name.lowercase()
                }
            )
        }

        AppSortMode.DATE_INSTALLED -> {
            apps.sortedByDescending {
                it.firstInstallTime
            }
        }

        AppSortMode.LAST_USED -> {
            apps.sortedByDescending {
                it.lastUsedTime
            }
        }
    }
}

fun openInstalledApp(
    context: Context,
    packageName: String
) {

    val launchIntent =
        context.packageManager
            .getLaunchIntentForPackage(
                packageName
            )

    try {

        if (launchIntent != null) {

            launchIntent.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK
            )

            context.startActivity(
                launchIntent
            )

        } else {

            context.startActivity(
                Intent(
                    Settings
                        .ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse(
                        "package:$packageName"
                    )
                )
            )
        }

    } catch (_: Exception) {

        Toast.makeText(
            context,
            "Unable to open this app.",
            Toast.LENGTH_SHORT
        ).show()
    }
}

fun formatLastUsed(
    timeMillis: Long
): String {

    if (timeMillis <= 0L) {
        return "Unknown"
    }

    val difference =
        (
                System.currentTimeMillis() -
                        timeMillis
                )
            .coerceAtLeast(0L)

    val minute = 60_000L
    val hour = 60L * minute
    val day = 24L * hour

    return when {
        difference < minute ->
            "Just now"
        difference < hour ->
            "${difference / minute} min ago"
        difference < day ->
            "${difference / hour} hr ago"
        difference < 30L * day ->
            "${difference / day} days ago"
        else ->
            java.text.DateFormat
                .getDateInstance(
                    java.text.DateFormat.MEDIUM
                )
                .format(
                    java.util.Date(
                        timeMillis
                    )
                )
    }
}

fun hasDeepFileAccess(
    context: Context
): Boolean {

    return if (
        Build.VERSION.SDK_INT >=
        Build.VERSION_CODES.R
    ) {

        Environment
            .isExternalStorageManager()

    } else {

        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) ==
                PackageManager.PERMISSION_GRANTED
    }
}

fun loadFilesForCategory(
    context: Context,
    category: FileCategory
): List<FileItem> {

    val items =
        mutableListOf<FileItem>()

    val roots =
        getSharedStorageRoots(
            context
        )

    val visitedDirectories =
        mutableSetOf<String>()

    val stack =
        ArrayDeque<File>()

    roots.forEach { root ->

        if (
            root.exists() &&
            root.isDirectory
        ) {

            stack.add(root)
        }
    }

    while (stack.isNotEmpty()) {

        val current =
            stack.removeLast()

        val canonicalPath =
            try {
                current.canonicalPath
            } catch (_: Exception) {
                current.absolutePath
            }

        if (
            !visitedDirectories.add(
                canonicalPath
            )
        ) {
            continue
        }

        if (
            isAndroidProtectedFolder(
                current
            )
        ) {
            continue
        }

        val children =
            try {
                current.listFiles()
            } catch (_: Exception) {
                null
            } ?: continue

        children.forEach { child ->

            try {

                if (child.isDirectory) {

                    if (
                        !isAndroidProtectedFolder(
                            child
                        )
                    ) {

                        stack.add(child)
                    }

                } else if (child.isFile) {

                    val name =
                        child.name

                    val extension =
                        fileExtension(
                            name
                        )

                    val mimeType =
                        MimeTypeMap
                            .getSingleton()
                            .getMimeTypeFromExtension(
                                extension
                            )

                    val absolutePath =
                        child.absolutePath

                    val relativePath =
                        sharedRelativePath(
                            roots = roots,
                            file = child
                        )

                    val item =
                        FileItem(
                            id =
                                absolutePath
                                    .hashCode()
                                    .toLong(),
                            uri =
                                Uri.fromFile(
                                    child
                                ),
                            name = name,
                            size =
                                child.length()
                                    .coerceAtLeast(
                                        0L
                                    ),
                            dateAdded =
                                (
                                        child.lastModified() /
                                                1000L
                                        ).coerceAtLeast(
                                        0L
                                    ),
                            mimeType =
                                mimeType,
                            relativePath =
                                relativePath,
                            extension =
                                extension,
                            absolutePath =
                                absolutePath
                        )

                    if (
                        shouldIncludeFile(
                            item = item,
                            category = category
                        )
                    ) {

                        items.add(item)
                    }
                }

            } catch (_: Exception) {

                // Skip only the inaccessible item and continue scanning.
            }
        }
    }

    return items
        .distinctBy {
            it.absolutePath
        }
}

fun getSharedStorageRoots(
    context: Context
): List<File> {

    val roots =
        mutableListOf<File>()

    @Suppress("DEPRECATION")
    val primaryRoot =
        Environment
            .getExternalStorageDirectory()

    roots.add(primaryRoot)

    context
        .getExternalFilesDirs(null)
        .filterNotNull()
        .forEach { appExternalDir ->

            val fullPath =
                appExternalDir
                    .absolutePath

            val androidDataIndex =
                fullPath.indexOf(
                    "/Android/data/"
                )

            if (androidDataIndex > 0) {

                val rootPath =
                    fullPath.substring(
                        0,
                        androidDataIndex
                    )

                roots.add(
                    File(rootPath)
                )
            }
        }

    return roots
        .mapNotNull { root ->

            try {

                root.canonicalFile

            } catch (_: Exception) {

                root.absoluteFile
            }
        }
        .distinctBy {
            it.absolutePath
        }
}

fun isAndroidProtectedFolder(
    file: File
): Boolean {

    val normalized =
        file.absolutePath
            .replace(
                "\\\\",
                "/"
            )
            .lowercase()

    return normalized.contains(
        "/android/data"
    ) ||
            normalized.contains(
                "/android/obb"
            )
}

fun sharedRelativePath(
    roots: List<File>,
    file: File
): String? {

    val absolute =
        file.absolutePath

    val matchingRoot =
        roots
            .map {
                it.absolutePath
            }
            .filter {
                absolute.startsWith(
                    it
                )
            }
            .maxByOrNull {
                it.length
            }
            ?: return null

    return absolute
        .removePrefix(
            matchingRoot
        )
        .trimStart(
            '/',
            '\\'
        )
}

fun shouldIncludeFile(
    item: FileItem,
    category: FileCategory
): Boolean {

    val lowerName =
        item.name.lowercase()

    val lowerPath =
        item.relativePath
            .orEmpty()
            .lowercase()

    val lowerMime =
        item.mimeType
            .orEmpty()
            .lowercase()

    val extension =
        item.extension.lowercase()

    val isDownload =
        lowerPath.contains(
            "download"
        )

    val documentExtensions =
        setOf(
            "pdf",
            "doc",
            "docx",
            "odt",
            "rtf",
            "txt",
            "md",
            "pages",
            "xls",
            "xlsx",
            "ods",
            "csv",
            "ppt",
            "pptx",
            "odp",
            "epub"
        )

    val isDocument =
        extension in
                documentExtensions ||
                lowerMime.startsWith(
                    "text/"
                ) ||
                lowerMime ==
                "application/pdf" ||
                lowerMime.contains(
                    "word"
                ) ||
                lowerMime.contains(
                    "excel"
                ) ||
                lowerMime.contains(
                    "spreadsheet"
                ) ||
                lowerMime.contains(
                    "presentation"
                ) ||
                lowerMime.contains(
                    "powerpoint"
                ) ||
                lowerMime.contains(
                    "opendocument"
                )

    val isMedia =
        lowerMime.startsWith(
            "image/"
        ) ||
                lowerMime.startsWith(
                    "video/"
                ) ||
                lowerMime.startsWith(
                    "audio/"
                )

    val isOtherCandidate =
        !isMedia &&
                !isDocument

    return when (category) {

        FileCategory.DOWNLOADS -> {
            isDownload
        }

        FileCategory.DOCUMENTS -> {
            isDocument
        }

        FileCategory.OTHER_FILES -> {
            isOtherCandidate
        }
    }
}

fun sortFiles(
    files: List<FileItem>,
    sortMode: SortMode
): List<FileItem> {

    return when (sortMode) {

        SortMode.LARGEST_FIRST -> {

            files.sortedWith(
                compareByDescending<FileItem> {
                    it.size
                }.thenByDescending {
                    it.dateAdded
                }
            )
        }

        SortMode.SMALLEST_FIRST -> {

            files.sortedWith(
                compareBy<FileItem> {
                    it.size
                }.thenByDescending {
                    it.dateAdded
                }
            )
        }

        SortMode.DATE_ADDED -> {

            files.sortedByDescending {
                it.dateAdded
            }
        }
    }
}

fun fileExtension(
    name: String
): String {

    return name
        .substringAfterLast(
            ".",
            ""
        )
        .lowercase()
}

fun fileTypeText(
    file: FileItem
): String {

    if (
        file.extension.isNotBlank()
    ) {
        return file.extension.uppercase()
    }

    return file.mimeType
        ?.substringAfterLast("/")
        ?.uppercase()
        ?.ifBlank {
            "FILE"
        }
        ?: "FILE"
}

fun fileTypeBadge(
    file: FileItem
): String {

    return when (
        file.extension.lowercase()
    ) {

        "pdf" -> "PDF"
        "doc",
        "docx",
        "odt",
        "rtf" -> "DOC"

        "xls",
        "xlsx",
        "ods",
        "csv" -> "SHEET"

        "ppt",
        "pptx",
        "odp" -> "SLIDE"

        "zip",
        "rar",
        "7z",
        "tar",
        "gz",
        "bz2",
        "xz" -> "ZIP"

        "apk",
        "apks",
        "xapk" -> "APK"

        "exe",
        "msi",
        "bat",
        "cmd" -> "EXE"

        "txt",
        "md" -> "TXT"

        else -> "FILE"
    }
}

fun openGenericFile(
    context: Context,
    file: FileItem
) {

    val physicalFile =
        File(
            file.absolutePath
        )

    if (
        !physicalFile.exists()
    ) {

        Toast.makeText(
            context,
            "This file no longer exists.",
            Toast.LENGTH_SHORT
        ).show()

        return
    }

    val openUri =
        try {

            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                physicalFile
            )

        } catch (_: Exception) {

            Toast.makeText(
                context,
                "SwipeClean could not securely open this file.",
                Toast.LENGTH_SHORT
            ).show()

            return
        }

    val resolvedMime =
        file.mimeType
            ?.takeIf {
                it.isNotBlank()
            }
            ?: MimeTypeMap
                .getSingleton()
                .getMimeTypeFromExtension(
                    file.extension
                )
            ?: "*/*"

    val intent =
        Intent(
            Intent.ACTION_VIEW
        ).apply {

            setDataAndType(
                openUri,
                resolvedMime
            )

            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }

    try {

        context.startActivity(
            Intent.createChooser(
                intent,
                "Open ${file.name}"
            )
        )

    } catch (
        _: ActivityNotFoundException
    ) {

        Toast.makeText(
            context,
            "No installed app can open this file type.",
            Toast.LENGTH_SHORT
        ).show()
    }
}

fun hasAudioPermission(
    context: Context
): Boolean {

    val permission =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    return ContextCompat.checkSelfPermission(
        context,
        permission
    ) == PackageManager.PERMISSION_GRANTED
}

fun loadAudio(
    context: Context
): List<AudioItem> {

    val audioItems =
        mutableListOf<AudioItem>()

    val collection =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

            MediaStore.Audio.Media.getContentUri(
                MediaStore.VOLUME_EXTERNAL
            )

        } else {

            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        }

    val projection =
        arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.MIME_TYPE
        )

    context.contentResolver.query(
        collection,
        projection,
        null,
        null,
        null
    )?.use { cursor ->

        val idColumn =
            cursor.getColumnIndexOrThrow(
                MediaStore.Audio.Media._ID
            )

        val nameColumn =
            cursor.getColumnIndexOrThrow(
                MediaStore.Audio.Media.DISPLAY_NAME
            )

        val sizeColumn =
            cursor.getColumnIndexOrThrow(
                MediaStore.Audio.Media.SIZE
            )

        val dateColumn =
            cursor.getColumnIndexOrThrow(
                MediaStore.Audio.Media.DATE_ADDED
            )

        val durationColumn =
            cursor.getColumnIndexOrThrow(
                MediaStore.Audio.Media.DURATION
            )

        val mimeColumn =
            cursor.getColumnIndex(
                MediaStore.Audio.Media.MIME_TYPE
            )

        while (cursor.moveToNext()) {

            val id =
                cursor.getLong(idColumn)

            val name =
                cursor.getString(nameColumn)
                    ?: "Unknown audio"

            val size =
                cursor.getLong(sizeColumn)

            val dateAdded =
                cursor.getLong(dateColumn)

            val durationMs =
                cursor.getLong(durationColumn)

            val mimeType =
                if (mimeColumn >= 0) {
                    cursor.getString(mimeColumn)
                } else {
                    null
                }

            val uri =
                ContentUris.withAppendedId(
                    collection,
                    id
                )

            audioItems.add(
                AudioItem(
                    id = id,
                    uri = uri,
                    name = name,
                    size = size,
                    dateAdded = dateAdded,
                    durationMs = durationMs,
                    mimeType = mimeType
                )
            )
        }
    }

    return audioItems.distinctBy {
        it.uri
    }
}

fun sortAudio(
    audioItems: List<AudioItem>,
    sortMode: SortMode
): List<AudioItem> {

    return when (sortMode) {

        SortMode.LARGEST_FIRST -> {

            audioItems.sortedWith(
                compareByDescending<AudioItem> {
                    it.size
                }.thenByDescending {
                    it.dateAdded
                }
            )
        }

        SortMode.SMALLEST_FIRST -> {

            audioItems.sortedWith(
                compareBy<AudioItem> {
                    it.size
                }.thenByDescending {
                    it.dateAdded
                }
            )
        }

        SortMode.DATE_ADDED -> {

            audioItems.sortedByDescending {
                it.dateAdded
            }
        }
    }
}

fun loadScreenshots(
    context: Context
): List<PhotoItem> {

    val screenshots =
        mutableListOf<PhotoItem>()

    val collection =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

            MediaStore.Images.Media.getContentUri(
                MediaStore.VOLUME_EXTERNAL
            )

        } else {

            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

    val projection =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

            arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Images.Media.RELATIVE_PATH
            )

        } else {

            arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.SIZE,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME,
                MediaStore.Images.Media.DATA
            )
        }

    context.contentResolver.query(
        collection,
        projection,
        null,
        null,
        null
    )?.use { cursor ->

        val idColumn =
            cursor.getColumnIndexOrThrow(
                MediaStore.Images.Media._ID
            )

        val nameColumn =
            cursor.getColumnIndexOrThrow(
                MediaStore.Images.Media.DISPLAY_NAME
            )

        val sizeColumn =
            cursor.getColumnIndexOrThrow(
                MediaStore.Images.Media.SIZE
            )

        val dateColumn =
            cursor.getColumnIndexOrThrow(
                MediaStore.Images.Media.DATE_ADDED
            )

        val bucketColumn =
            cursor.getColumnIndex(
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME
            )

        val pathColumn =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                cursor.getColumnIndex(
                    MediaStore.Images.Media.RELATIVE_PATH
                )
            } else {
                cursor.getColumnIndex(
                    MediaStore.Images.Media.DATA
                )
            }

        while (cursor.moveToNext()) {

            val id =
                cursor.getLong(idColumn)

            val name =
                cursor.getString(nameColumn)
                    ?: "Unknown screenshot"

            val size =
                cursor.getLong(sizeColumn)

            val dateAdded =
                cursor.getLong(dateColumn)

            val bucket =
                if (bucketColumn >= 0) {
                    cursor.getString(bucketColumn)
                } else {
                    null
                }

            val path =
                if (pathColumn >= 0) {
                    cursor.getString(pathColumn)
                } else {
                    null
                }

            if (
                isScreenshotItem(
                    name = name,
                    bucket = bucket,
                    path = path
                )
            ) {

                val uri =
                    ContentUris.withAppendedId(
                        collection,
                        id
                    )

                screenshots.add(
                    PhotoItem(
                        id = id,
                        uri = uri,
                        name = name,
                        size = size,
                        dateAdded = dateAdded
                    )
                )
            }
        }
    }

    return screenshots.distinctBy {
        it.uri
    }
}

fun isScreenshotItem(
    name: String?,
    bucket: String?,
    path: String?
): Boolean {

    val normalizedName =
        name.orEmpty().lowercase()

    val normalizedBucket =
        bucket.orEmpty().lowercase()

    val normalizedPath =
        path.orEmpty().lowercase()

    return normalizedName.contains("screenshot") ||
            normalizedBucket.contains("screenshot") ||
            normalizedPath.contains("screenshot")
}

fun loadPhotos(
    context: Context
): List<PhotoItem> {

    val photos =
        mutableListOf<PhotoItem>()

    val collection =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

            MediaStore.Images.Media.getContentUri(
                MediaStore.VOLUME_EXTERNAL
            )

        } else {

            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

    val projection =
        arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_ADDED
        )

    context.contentResolver.query(
        collection,
        projection,
        null,
        null,
        null
    )?.use { cursor ->

        val idColumn =
            cursor.getColumnIndexOrThrow(
                MediaStore.Images.Media._ID
            )

        val nameColumn =
            cursor.getColumnIndexOrThrow(
                MediaStore.Images.Media.DISPLAY_NAME
            )

        val sizeColumn =
            cursor.getColumnIndexOrThrow(
                MediaStore.Images.Media.SIZE
            )

        val dateColumn =
            cursor.getColumnIndexOrThrow(
                MediaStore.Images.Media.DATE_ADDED
            )

        while (cursor.moveToNext()) {

            val id =
                cursor.getLong(idColumn)

            val name =
                cursor.getString(nameColumn)
                    ?: "Unknown photo"

            val size =
                cursor.getLong(sizeColumn)

            val dateAdded =
                cursor.getLong(dateColumn)

            val uri =
                ContentUris.withAppendedId(
                    collection,
                    id
                )

            photos.add(
                PhotoItem(
                    id = id,
                    uri = uri,
                    name = name,
                    size = size,
                    dateAdded = dateAdded
                )
            )
        }
    }

    return photos.distinctBy {
        it.uri
    }
}

fun sortPhotos(
    photos: List<PhotoItem>,
    sortMode: SortMode
): List<PhotoItem> {

    return when (sortMode) {

        SortMode.LARGEST_FIRST -> {

            photos.sortedWith(
                compareByDescending<PhotoItem> {
                    it.size
                }.thenByDescending {
                    it.dateAdded
                }
            )
        }

        SortMode.SMALLEST_FIRST -> {

            photos.sortedWith(
                compareBy<PhotoItem> {
                    it.size
                }.thenByDescending {
                    it.dateAdded
                }
            )
        }

        SortMode.DATE_ADDED -> {

            photos.sortedByDescending {
                it.dateAdded
            }
        }
    }
}

fun loadHighQualityPreview(
    context: Context,
    uri: Uri,
    maxDimension: Int
): Bitmap? {

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {

        val source =
            ImageDecoder.createSource(
                context.contentResolver,
                uri
            )

        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->

            val sourceWidth = info.size.width
            val sourceHeight = info.size.height
            val largestSide = max(sourceWidth, sourceHeight)

            if (largestSide > maxDimension) {

                val scale =
                    maxDimension.toFloat() / largestSide.toFloat()

                val targetWidth =
                    (sourceWidth * scale)
                        .roundToInt()
                        .coerceAtLeast(1)

                val targetHeight =
                    (sourceHeight * scale)
                        .roundToInt()
                        .coerceAtLeast(1)

                decoder.setTargetSize(
                    targetWidth,
                    targetHeight
                )
            }

            decoder.allocator =
                ImageDecoder.ALLOCATOR_SOFTWARE
        }

    } else {

        val boundsOptions =
            BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }

        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(
                stream,
                null,
                boundsOptions
            )
        }

        val originalWidth =
            boundsOptions.outWidth

        val originalHeight =
            boundsOptions.outHeight

        if (originalWidth <= 0 || originalHeight <= 0) {
            return null
        }

        var sampleSize = 1

        while (
            originalWidth / sampleSize > maxDimension * 2 ||
            originalHeight / sampleSize > maxDimension * 2
        ) {
            sampleSize *= 2
        }

        val decodeOptions =
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(
                stream,
                null,
                decodeOptions
            )
        }
    }
}

fun hasVideoPermission(
    context: Context
): Boolean {

    val permission =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

    return ContextCompat.checkSelfPermission(
        context,
        permission
    ) == PackageManager.PERMISSION_GRANTED
}

fun loadVideos(
    context: Context
): List<VideoItem> {

    val videos =
        mutableListOf<VideoItem>()

    val collection =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

            MediaStore.Video.Media.getContentUri(
                MediaStore.VOLUME_EXTERNAL
            )

        } else {

            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

    val projection =
        arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DURATION
        )

    context.contentResolver.query(
        collection,
        projection,
        null,
        null,
        null
    )?.use { cursor ->

        val idColumn =
            cursor.getColumnIndexOrThrow(
                MediaStore.Video.Media._ID
            )

        val nameColumn =
            cursor.getColumnIndexOrThrow(
                MediaStore.Video.Media.DISPLAY_NAME
            )

        val sizeColumn =
            cursor.getColumnIndexOrThrow(
                MediaStore.Video.Media.SIZE
            )

        val dateColumn =
            cursor.getColumnIndexOrThrow(
                MediaStore.Video.Media.DATE_ADDED
            )

        val durationColumn =
            cursor.getColumnIndexOrThrow(
                MediaStore.Video.Media.DURATION
            )

        while (cursor.moveToNext()) {

            val id =
                cursor.getLong(idColumn)

            val name =
                cursor.getString(nameColumn)
                    ?: "Unknown video"

            val size =
                cursor.getLong(sizeColumn)

            val dateAdded =
                cursor.getLong(dateColumn)

            val durationMs =
                cursor.getLong(durationColumn)

            val uri =
                ContentUris.withAppendedId(
                    collection,
                    id
                )

            videos.add(
                VideoItem(
                    id = id,
                    uri = uri,
                    name = name,
                    size = size,
                    dateAdded = dateAdded,
                    durationMs = durationMs
                )
            )
        }
    }

    return videos.distinctBy {
        it.uri
    }
}

fun sortVideos(
    videos: List<VideoItem>,
    sortMode: SortMode
): List<VideoItem> {

    return when (sortMode) {

        SortMode.LARGEST_FIRST -> {

            videos.sortedWith(
                compareByDescending<VideoItem> {
                    it.size
                }.thenByDescending {
                    it.dateAdded
                }
            )
        }

        SortMode.SMALLEST_FIRST -> {

            videos.sortedWith(
                compareBy<VideoItem> {
                    it.size
                }.thenByDescending {
                    it.dateAdded
                }
            )
        }

        SortMode.DATE_ADDED -> {

            videos.sortedByDescending {
                it.dateAdded
            }
        }
    }
}

fun loadVideoPreview(
    context: Context,
    uri: Uri,
    maxDimension: Int
): Bitmap? {

    val retriever =
        MediaMetadataRetriever()

    return try {

        retriever.setDataSource(
            context,
            uri
        )

        val frame =
            retriever.getFrameAtTime(
                0L,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            ) ?: return null

        val largestSide =
            max(
                frame.width,
                frame.height
            )

        if (largestSide <= maxDimension) {

            frame

        } else {

            val scale =
                maxDimension.toFloat() /
                        largestSide.toFloat()

            val targetWidth =
                (frame.width * scale)
                    .roundToInt()
                    .coerceAtLeast(1)

            val targetHeight =
                (frame.height * scale)
                    .roundToInt()
                    .coerceAtLeast(1)

            val scaled =
                Bitmap.createScaledBitmap(
                    frame,
                    targetWidth,
                    targetHeight,
                    true
                )

            if (scaled !== frame) {
                frame.recycle()
            }

            scaled
        }

    } finally {

        try {
            retriever.release()
        } catch (_: Exception) {
        }
    }
}

fun formatDuration(
    durationMs: Long
): String {

    if (durationMs <= 0L) {
        return "0:00"
    }

    val totalSeconds =
        durationMs / 1000L

    val hours =
        totalSeconds / 3600L

    val minutes =
        (totalSeconds % 3600L) / 60L

    val seconds =
        totalSeconds % 60L

    return if (hours > 0L) {

        String.format(
            Locale.US,
            "%d:%02d:%02d",
            hours,
            minutes,
            seconds
        )

    } else {

        String.format(
            Locale.US,
            "%d:%02d",
            minutes,
            seconds
        )
    }
}

fun formatFileSize(
    context: Context,
    bytes: Long
): String {

    return android.text.format.Formatter.formatFileSize(
        context,
        bytes
    )
}
