package com.aura.music

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import java.util.Locale

private data class Song(
    val uri: Uri,
    val title: String,
    val artist: String,
    val duration: Long,
    val size: Long,
    val dateAdded: Long,
    val dateModified: Long
)

private enum class SortBy(val label: String) {
    DATE_ADDED("Date added"), DATE_MODIFIED("Date modified"), SIZE("File size"),
    DURATION("Duration"), NAME("Name")
}

class MainActivity : ComponentActivity() {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        controllerFuture = MediaController.Builder(
            this, SessionToken(this, ComponentName(this, PlaybackService::class.java))
        ).buildAsync()
        controllerFuture?.addListener({ runCatching { mediaController = controllerFuture?.get() } }, mainExecutor)

        setContent {
            var songs by remember { mutableStateOf<List<Song>>(emptyList()) }
            var query by remember { mutableStateOf("") }
            var sortBy by remember { mutableStateOf(SortBy.DATE_ADDED) }
            var ascending by remember { mutableStateOf(false) }
            var editMode by remember { mutableStateOf(false) }
            val selected = remember { mutableStateListOf<Uri>() }
            var settingsOpen by remember { mutableStateOf(false) }
            var hasPermission by remember { mutableStateOf(hasAudioPermission()) }
            var shuffleEnabled by remember { mutableStateOf(false) }
            var repeatEnabled by remember { mutableStateOf(false) }
            var scanMessage by remember { mutableStateOf("Scanning your music library…") }

            fun scanLibrary() {
                if (!hasAudioPermission()) {
                    scanMessage = "Allow music access to show songs on your phone."
                    return
                }
                val found = mutableListOf<Song>()
                val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                val projection = arrayOf(
                    MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.DURATION,
                    MediaStore.Audio.Media.SIZE, MediaStore.Audio.Media.DATE_ADDED,
                    MediaStore.Audio.Media.DATE_MODIFIED
                )
                val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
                runCatching {
                    contentResolver.query(collection, projection, selection, null, null)?.use { cursor ->
                        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                        val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                        val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                        val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                        val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                        val addedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                        val modifiedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
                        while (cursor.moveToNext()) {
                            val id = cursor.getLong(idCol)
                            found.add(
                                Song(
                                    Uri.withAppendedPath(collection, id.toString()),
                                    cursor.getString(titleCol) ?: "Unknown title",
                                    cursor.getString(artistCol)?.takeIf { it != "<unknown>" } ?: "Unknown artist",
                                    cursor.getLong(durationCol), cursor.getLong(sizeCol),
                                    cursor.getLong(addedCol), cursor.getLong(modifiedCol)
                                )
                            )
                        }
                    }
                }.onFailure { scanMessage = "Couldn't scan music: ${it.localizedMessage ?: "unknown error"}" }
                songs = found
                scanMessage = if (found.isEmpty()) "No music files found on this device." else "${found.size} songs found"
                selected.clear()
            }

            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { granted ->
                hasPermission = granted
                if (granted) scanLibrary() else scanMessage = "Music permission is needed to scan your library."
            }

            LaunchedEffect(Unit) {
                if (hasAudioPermission()) {
                    hasPermission = true
                    scanLibrary()
                } else {
                    permissionLauncher.launch(audioPermission())
                }
            }

            val filtered = songs.filter {
                query.isBlank() || it.title.contains(query, true) || it.artist.contains(query, true)
            }.let { list ->
                when (sortBy) {
                    SortBy.DATE_ADDED -> list.sortedBy { it.dateAdded }
                    SortBy.DATE_MODIFIED -> list.sortedBy { it.dateModified }
                    SortBy.SIZE -> list.sortedBy { it.size }
                    SortBy.DURATION -> list.sortedBy { it.duration }
                    SortBy.NAME -> list.sortedBy { it.title.lowercase(Locale.getDefault()) }
                }.let { if (ascending) it else it.reversed() }
            }

            MaterialTheme(colorScheme = darkColorScheme(
                primary = Color(0xFFB7A5FF), secondary = Color(0xFF83D7D0),
                background = Color(0xFF080A12), surface = Color(0xFF171A29)
            )) {
                if (settingsOpen) {
                    SettingsScreen(
                        shuffleEnabled, repeatEnabled,
                        onShuffle = { shuffleEnabled = it; mediaController?.shuffleModeEnabled = it },
                        onRepeat = { repeatEnabled = it; mediaController?.repeatMode =
                            if (it) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF },
                        onBack = { settingsOpen = false }
                    )
                } else {
                    Column(
                        Modifier.fillMaxSize().background(Color(0xFF080A12)).padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("A U R A", color = Color(0xFFB7A5FF), letterSpacing = 5.sp)
                                Text("Your sound. Your space.", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                            }
                            IconButton(onClick = { scanLibrary() }) { Icon(Icons.Default.Refresh, "Rescan", tint = Color.White) }
                            IconButton(onClick = { settingsOpen = true }) { Icon(Icons.Default.Settings, "Settings", tint = Color.White) }
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(
                            Modifier.fillMaxWidth().background(Color(0xFF171A29), RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Search, null, tint = Color.LightGray)
                            Spacer(Modifier.width(10.dp))
                            Box(Modifier.weight(1f)) {
                                if (query.isEmpty()) Text("Search songs or artists", color = Color.Gray)
                                BasicTextField(query, { query = it }, singleLine = true, textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White), modifier = Modifier.fillMaxWidth())
                            }
                            if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Clear", tint = Color.LightGray) }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(if (editMode) "${selected.size} selected" else "My music", color = Color.White, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                            if (editMode) {
                                TextButton(onClick = { selected.clear(); editMode = false }) { Text("Done") }
                                TextButton(onClick = { selected.addAll(filtered.map { it.uri }.filterNot { it in selected }) }) { Text("Select all") }
                            } else {
                                TextButton(onClick = { editMode = true }) { Text("Edit") }
                            }
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            var menuOpen by remember { mutableStateOf(false) }
                            Box {
                                OutlinedButton(onClick = { menuOpen = true }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                                    Icon(Icons.Default.Sort, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text("Sort: ${sortBy.label}", maxLines = 1)
                                }
                                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                    SortBy.values().forEach { option ->
                                        DropdownMenuItem(text = { Text(option.label) }, onClick = { sortBy = option; menuOpen = false })
                                    }
                                }
                            }
                            OutlinedButton(onClick = { ascending = !ascending }, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)) {
                                Icon(if (ascending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward, null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(if (ascending) "Ascending" else "Descending")
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("${filtered.size} tracks  •  $scanMessage", color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.height(8.dp))
                        if (filtered.isEmpty()) {
                            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.LibraryMusic, null, tint = Color(0xFFB7A5FF), modifier = Modifier.size(54.dp))
                                    Spacer(Modifier.height(10.dp))
                                    Text(if (hasPermission) "No matching music found" else "Give AURA music access", color = Color.White)
                                    Text("Music stored on your phone will appear here.", color = Color.Gray)
                                    if (!hasPermission) Button(onClick = { permissionLauncher.launch(audioPermission()) }) { Text("Allow access") }
                                }
                            }
                        } else {
                            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 10.dp)) {
                                items(filtered, key = { it.uri.toString() }) { song ->
                                    Row(
                                        Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                            .background(Color(0xFF171A29), RoundedCornerShape(14.dp))
                                            .clickable {
                                                if (editMode) {
                                                    if (song.uri in selected) selected.remove(song.uri) else selected.add(song.uri)
                                                } else {
                                                    val index = filtered.indexOf(song)
                                                    val mediaItems = filtered.map { item ->
                                                        MediaItem.Builder().setUri(item.uri)
                                                            .setMediaMetadata(MediaMetadata.Builder().setTitle(item.title).setArtist(item.artist).build())
                                                            .build()
                                                    }
                                                    mediaController?.setMediaItems(mediaItems, index, 0L)
                                                    mediaController?.prepare()
                                                    mediaController?.play()
                                                }
                                            }.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (editMode) Checkbox(checked = song.uri in selected, onCheckedChange = { checked ->
                                            if (checked) selected.add(song.uri) else selected.remove(song.uri)
                                        }) else Icon(Icons.Default.MusicNote, null, tint = Color(0xFFB7A5FF))
                                        Spacer(Modifier.width(10.dp))
                                        Column(Modifier.weight(1f)) {
                                            Text(song.title, color = Color.White, maxLines = 1)
                                            Text(song.artist + "  •  ${formatDuration(song.duration)}", color = Color.Gray, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                        }
                                        Text(formatSize(song.size), color = Color.Gray, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                        if (editMode && selected.isNotEmpty()) {
                            TextButton(onClick = {
                                songs = songs.filterNot { it.uri in selected }
                                selected.clear()
                                editMode = false
                            }, modifier = Modifier.align(Alignment.End)) {
                                Icon(Icons.Default.VisibilityOff, null); Spacer(Modifier.width(5.dp)); Text("Hide from this list")
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth().background(Color(0xFF171A29), RoundedCornerShape(20.dp)),
                            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(onClick = { mediaController?.seekToPreviousMediaItem() }) { Icon(Icons.Default.SkipPrevious, null, tint = Color.White) }
                            IconButton(onClick = { if (mediaController?.isPlaying == true) mediaController?.pause() else mediaController?.play() }) {
                                Icon(if (mediaController?.isPlaying == true) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color(0xFFB7A5FF), modifier = Modifier.size(38.dp))
                            }
                            IconButton(onClick = { mediaController?.seekToNextMediaItem() }) { Icon(Icons.Default.SkipNext, null, tint = Color.White) }
                        }
                        Spacer(Modifier.height(8.dp))
                        Text("Offline music  •  Background playback  •  Lock-screen controls", color = Color.Gray, style = MaterialTheme.typography.labelSmall, modifier = Modifier.align(Alignment.CenterHorizontally))
                    }
                }
            }
        }
    }

    private fun audioPermission(): String =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

    private fun hasAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, audioPermission()) == PackageManager.PERMISSION_GRANTED

    override fun onDestroy() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        mediaController = null
        super.onDestroy()
    }
}

@Composable
private fun SettingsScreen(
    shuffle: Boolean, repeat: Boolean,
    onShuffle: (Boolean) -> Unit, onRepeat: (Boolean) -> Unit, onBack: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(Color(0xFF080A12)).padding(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) }
            Text("Settings", color = Color.White, style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(Modifier.height(20.dp))
        Text("PLAYBACK", color = Color(0xFFB7A5FF), style = MaterialTheme.typography.labelMedium)
        SettingSwitch("Shuffle playback", "Play tracks in a random order", shuffle, onShuffle)
        SettingSwitch("Repeat all songs", "Repeat the current music queue", repeat, onRepeat)
        Spacer(Modifier.height(18.dp))
        Text("LIBRARY", color = Color(0xFFB7A5FF), style = MaterialTheme.typography.labelMedium)
        Text("AURA scans audio files stored on your phone. Use the refresh button on the library screen to scan again after adding music.", color = Color.LightGray, modifier = Modifier.padding(top = 10.dp))
        Spacer(Modifier.height(18.dp))
        Text("ABOUT", color = Color(0xFFB7A5FF), style = MaterialTheme.typography.labelMedium)
        Text("AURA Music 1.1", color = Color.White, modifier = Modifier.padding(top = 10.dp))
        Text("Made for your music, offline.", color = Color.Gray)
    }
}

@Composable
private fun SettingSwitch(title: String, subtitle: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White)
            Text(subtitle, color = Color.Gray, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChecked)
    }
}

private fun formatDuration(ms: Long): String {
    val total = ms / 1000
    return String.format(Locale.getDefault(), "%d:%02d", total / 60, total % 60)
}
private fun formatSize(bytes: Long): String =
    if (bytes >= 1024L * 1024L) String.format(Locale.getDefault(), "%.1f MB", bytes / (1024f * 1024f))
    else String.format(Locale.getDefault(), "%.0f KB", bytes / 1024f)
