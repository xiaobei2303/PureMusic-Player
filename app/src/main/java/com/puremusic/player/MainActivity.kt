package com.puremusic.player

import android.Manifest
import android.content.ComponentName
import android.content.pm.PackageManager
import android.net.Uri
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.puremusic.player.data.*
import com.puremusic.player.playback.MusicService
import com.puremusic.player.playback.PlaybackMode
import com.puremusic.player.playback.chooseRandomIndex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.Executor

private val Ink = Color(0xFF17181C)
private val Muted = Color(0xFF858791)
private val Paper = Color(0xFFF7F7F9)
private val Accent = Color(0xFF5268D8)

class MainActivity : ComponentActivity() {
    private var controller: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    override fun onStart() {
        super.onStart()
        val token = SessionToken(this, ComponentName(this, MusicService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync()
        controllerFuture?.addListener({
            try { controller = controllerFuture?.get() } catch (_: Exception) { }
        }, ContextCompat.getMainExecutor(this))
    }
    override fun onStop() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controller = null
        super.onStop()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PureMusicApp(getController = { controller }) }
    }
}

@Composable
private fun PureMusicApp(getController: () -> MediaController?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { MusicRepository(context) }
    val lyricsRepo = remember { LyricsRepository() }
    val dao = remember { PlaylistDb.get(context).dao() }
    var tracks by remember { mutableStateOf(emptyList<Track>()) }
    var query by remember { mutableStateOf("") }
    var tab by remember { mutableStateOf(0) }
    var selected by remember { mutableStateOf<Track?>(null) }
    var lyrics by remember { mutableStateOf("") }
    var lyricsStatus by remember { mutableStateOf("歌词尚未加载") }
    var playlistName by remember { mutableStateOf("") }
    var showCreate by remember { mutableStateOf(false) }
    var playlists by remember { mutableStateOf(emptyList<PlaylistEntity>()) }
    var openedPlaylist by remember { mutableStateOf<PlaylistEntity?>(null) }
    var playlistTracks by remember { mutableStateOf(emptyList<PlaylistTrackEntity>()) }
    var trackToAdd by remember { mutableStateOf<Track?>(null) }
    var currentPosition by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var isPlaying by remember { mutableStateOf(false) }
    val appPrefs = remember { context.getSharedPreferences("pure_music_appearance", android.content.Context.MODE_PRIVATE) }
    val playbackPrefs = remember { context.getSharedPreferences(MusicService.PREFS, android.content.Context.MODE_PRIVATE) }
    var playbackMode by remember { mutableStateOf(runCatching { PlaybackMode.valueOf(playbackPrefs.getString(MusicService.KEY_MODE, PlaybackMode.REPEAT_ALL.name)!!) }.getOrDefault(PlaybackMode.REPEAT_ALL)) }
    var showAppearance by remember { mutableStateOf(false) }
    var backgroundUri by remember { mutableStateOf(appPrefs.getString("background_uri", null)) }
    var backgroundBlur by remember { mutableFloatStateOf(appPrefs.getFloat("background_blur", 14f)) }
    var backgroundOpacity by remember { mutableFloatStateOf(appPrefs.getFloat("background_opacity", 0.42f)) }
    var backgroundBrightness by remember { mutableFloatStateOf(appPrefs.getFloat("background_brightness", 0.92f)) }
    val backgroundBitmap by produceState<android.graphics.Bitmap?>(initialValue = null, key1 = backgroundUri) {
        value = if (backgroundUri == null) null else withContext(Dispatchers.IO) {
            runCatching {
                val options = BitmapFactory.Options().apply { inSampleSize = 4 }
                context.contentResolver.openInputStream(Uri.parse(backgroundUri))?.use { BitmapFactory.decodeStream(it, null, options) }
            }.getOrNull()
        }
    }

    val permission = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    var hasPermission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        if (granted) scope.launch(Dispatchers.IO) { tracks = repository.scan() }
    }
    val lyricsPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            try {
                context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { lyrics = it.readText() }
                lyricsStatus = "已导入本地歌词"
            } catch (_: Exception) { lyricsStatus = "读取歌词失败" }
        }
    }
    val backgroundPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching { context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        backgroundUri = uri.toString()
        appPrefs.edit().putString("background_uri", backgroundUri).apply()
    }

    LaunchedEffect(Unit) {
        dao.playlists().collectLatest { playlists = it }
    }
    LaunchedEffect(playbackMode) {
        val c = getController()
        if (c != null) {
            c.shuffleModeEnabled = false
            c.repeatMode = when (playbackMode) {
                PlaybackMode.REPEAT_ALL -> androidx.media3.common.Player.REPEAT_MODE_ALL
                PlaybackMode.REPEAT_ONE -> androidx.media3.common.Player.REPEAT_MODE_ONE
                PlaybackMode.SHUFFLE -> androidx.media3.common.Player.REPEAT_MODE_OFF
            }
        }
    }
    LaunchedEffect(openedPlaylist?.id) {
        val id = openedPlaylist?.id ?: return@LaunchedEffect
        dao.tracks(id).collectLatest { playlistTracks = it }
    }
    LaunchedEffect(hasPermission) {
        if (hasPermission) withContext(Dispatchers.IO) { tracks = repository.scan() }
    }
    LaunchedEffect(selected?.id) {
        val track = selected ?: return@LaunchedEffect
        lyrics = ""
        lyricsStatus = "正在搜索歌词…"
        val found = withContext(Dispatchers.IO) { runCatching { lyricsRepo.search(track) }.getOrNull() }
        if (found != null) {
            lyrics = found.synced.ifBlank { found.plain }
            lyricsStatus = if (found.synced.isNotBlank()) "已匹配同步歌词" else "已匹配普通歌词"
        } else lyricsStatus = "未找到歌词，可手动导入"
    }
    LaunchedEffect(Unit) {
        while (true) {
            val p = getController()
            if (p != null) {
                currentPosition = p.currentPosition.coerceAtLeast(0)
                duration = p.duration.coerceAtLeast(0)
                isPlaying = p.isPlaying
            }
            kotlinx.coroutines.delay(500)
        }
    }

    MaterialTheme(colorScheme = lightColorScheme(primary = Accent, background = Paper, surface = Color.White)) {
      Box(Modifier.fillMaxSize().background(if (backgroundBitmap == null) Paper else Color.Transparent)) {
        if (backgroundBitmap != null) {
            Image(bitmap = backgroundBitmap!!.asImageBitmap(), contentDescription = "自定义背景", contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(backgroundBlur.dp), alpha = backgroundOpacity)
            Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.34f)))
            if (backgroundBrightness < 1f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = (1f - backgroundBrightness).coerceIn(0f, 1f) * 0.45f)))
            if (backgroundBrightness > 1f) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = (backgroundBrightness - 1f).coerceIn(0f, 0.4f))))
        }
        Scaffold(containerColor = Color.Transparent, bottomBar = {
            Column {
                selected?.let { track ->
                    Row(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).clickable { tab = 3 }) {
                            Text(track.displayTitle, color = Ink, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(track.displayArtist, color = Muted, fontSize = 11.sp)
                        }
                        IconButton(onClick = {
                            playbackMode = when (playbackMode) {
                                PlaybackMode.REPEAT_ALL -> PlaybackMode.SHUFFLE
                                PlaybackMode.SHUFFLE -> PlaybackMode.REPEAT_ONE
                                PlaybackMode.REPEAT_ONE -> PlaybackMode.REPEAT_ALL
                            }
                            playbackPrefs.edit().putString(MusicService.KEY_MODE, playbackMode.name).apply()
                        }) {
                            Icon(when (playbackMode) { PlaybackMode.SHUFFLE -> Icons.Default.Shuffle; PlaybackMode.REPEAT_ONE -> Icons.Default.RepeatOne; PlaybackMode.REPEAT_ALL -> Icons.Default.Repeat }, "播放模式", tint = Accent)
                        }
                        IconButton(onClick = { getController()?.seekToPreviousMediaItem() }) { Icon(Icons.Default.SkipPrevious, "上一首") }
                        IconButton(onClick = { val c = getController(); if (c?.isPlaying == true) c.pause() else c?.play() }) { Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "播放暂停", tint = Accent) }
                        IconButton(onClick = {
                            val c = getController()
                            if (c != null && c.mediaItemCount > 0) {
                                if (playbackMode == PlaybackMode.SHUFFLE) c.seekTo(chooseRandomIndex(c.mediaItemCount), 0L) else c.seekToNextMediaItem()
                            }
                        }) { Icon(Icons.Default.SkipNext, "下一首") }
                    }
                    LinearProgressIndicator(progress = { if (duration > 0) (currentPosition.toFloat() / duration).coerceIn(0f, 1f) else 0f }, modifier = Modifier.fillMaxWidth().height(2.dp), color = Accent, trackColor = Color(0xFFE7E8ED))
                }
                NavigationBar(containerColor = Color.White) {
                NavigationBarItem(selected = tab == 0, onClick = { tab = 0 }, icon = { Icon(Icons.Default.LibraryMusic, null) }, label = { Text("音乐") })
                NavigationBarItem(selected = tab == 1, onClick = { tab = 1 }, icon = { Icon(Icons.Default.QueueMusic, null) }, label = { Text("歌单") })
                NavigationBarItem(selected = tab == 2, onClick = { tab = 2 }, icon = { Icon(Icons.Default.Subtitles, null) }, label = { Text("歌词") })
                NavigationBarItem(selected = tab == 3, onClick = { tab = 3 }, icon = { Icon(Icons.Default.Album, null) }, label = { Text("播放") })
                }
            }
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)) {
                Spacer(Modifier.height(22.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("纯净音乐", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                        Text(when (tab) { 0 -> "音乐库"; 1 -> "我的歌单"; 2 -> "歌词"; else -> "正在播放" }, color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    }
                    if (tab == 0) IconButton(onClick = { scope.launch(Dispatchers.IO) { tracks = repository.scan() } }) { Icon(Icons.Default.Refresh, "重新扫描") }
                    if (tab == 1) IconButton(onClick = { showCreate = true }) { Icon(Icons.Default.Add, "新建歌单") }
                    IconButton(onClick = { showAppearance = true }) { Icon(Icons.Default.Settings, "外观设置") }
                }
                Spacer(Modifier.height(18.dp))
                when (tab) {
                    0 -> {
                        OutlinedTextField(value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), placeholder = { Text("搜索歌曲、歌手或专辑") }, leadingIcon = { Icon(Icons.Default.Search, null) }, shape = RoundedCornerShape(18.dp), singleLine = true)
                        Spacer(Modifier.height(14.dp))
                        Text("${tracks.size} 首歌曲", color = Muted, fontSize = 13.sp)
                        if (!hasPermission) EmptyState("需要访问本地音乐权限", "授权后自动扫描手机中的音乐文件") { permissionLauncher.launch(permission) }
                        else if (tracks.isEmpty()) EmptyState("还没有发现音乐", "将音乐文件保存到手机后重新扫描") { scope.launch(Dispatchers.IO) { tracks = repository.scan() } }
                        else LazyColumn(contentPadding = PaddingValues(top = 8.dp, bottom = 100.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(tracks.filter { "${it.title} ${it.artist} ${it.album}".contains(query, true) }, key = { it.id }) { track ->
                                TrackRow(track, selected?.id == track.id, onAdd = { trackToAdd = track }) {
                                    selected = track
                                    val c = getController()
                                    if (c != null) {
                                        val items = tracks.map { MediaItem.Builder().setUri(it.uri).setMediaId(it.mediaId()).setMediaMetadata(MediaMetadata.Builder().setTitle(it.displayTitle).setArtist(it.displayArtist).setAlbumTitle(it.album).build()).build() }
                                        val index = tracks.indexOfFirst { it.id == track.id }
                                        c.setMediaItems(items, index, 0); c.prepare(); c.play()
                                    }
                                }
                            }
                        }
                    }
                    1 -> {
                        val opened = openedPlaylist
                        if (opened != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { openedPlaylist = null }) { Icon(Icons.Default.ArrowBack, "返回歌单") }
                                Text(opened.name, color = Ink, fontWeight = FontWeight.Bold, fontSize = 19.sp)
                            }
                            val members = playlistTracks.mapNotNull { entry -> tracks.firstOrNull { it.id == entry.trackId } }
                            if (members.isEmpty()) EmptyState("歌单还是空的", "在音乐库歌曲右侧点添加按钮") { tab = 0 }
                            else LazyColumn(contentPadding = PaddingValues(bottom = 100.dp)) {
                                items(members, key = { it.id }) { song ->
                                    TrackRow(song, selected?.id == song.id, onAdd = { scope.launch { dao.removeTrack(opened.id, song.id) } }) {
                                        selected = song
                                        val c = getController()
                                        if (c != null) {
                                            val items = members.map { MediaItem.Builder().setUri(it.uri).setMediaId(it.mediaId()).setMediaMetadata(MediaMetadata.Builder().setTitle(it.displayTitle).setArtist(it.displayArtist).build()).build() }
                                            val index = members.indexOfFirst { it.id == song.id }
                                            c.setMediaItems(items, index, 0); c.prepare(); c.play()
                                        }
                                    }
                                }
                            }
                        } else if (playlists.isEmpty()) EmptyState("创建你的第一个歌单", "把喜欢的歌曲整理在一起") { showCreate = true }
                        else LazyColumn(contentPadding = PaddingValues(bottom = 100.dp)) {
                            items(playlists, key = { it.id }) { p ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp).clip(RoundedCornerShape(16.dp)).background(Color.White).clickable { openedPlaylist = p }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(Color(0xFFE9ECFA)), contentAlignment = Alignment.Center) { Icon(Icons.Default.QueueMusic, null, tint = Accent) }
                                    Column(Modifier.weight(1f).padding(start = 12.dp)) { Text(p.name, color = Ink, fontWeight = FontWeight.SemiBold); Text("打开歌单", color = Muted, fontSize = 12.sp) }
                                    IconButton(onClick = { scope.launch { dao.deletePlaylist(p.id) } }) { Icon(Icons.Default.DeleteOutline, null, tint = Muted) }
                                }
                            }
                        }
                    }
                    2 -> {
                        val track = selected
                        if (track == null) EmptyState("选择一首歌曲", "播放歌曲后，在这里查看歌词") { tab = 0 }
                        else {
                            Text(track.displayTitle, color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(track.displayArtist, color = Muted)
                            Spacer(Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(lyricsStatus, color = Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                TextButton(onClick = { lyricsPicker.launch(arrayOf("text/*", "application/x-subrip")) }) { Icon(Icons.Default.FileOpen, null); Spacer(Modifier.width(4.dp)); Text("导入歌词") }
                            }
                            if (lyrics.isBlank()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("暂无歌词", color = Muted) }
                            else {
                                val lyricLines = remember(lyrics) { parseLrc(lyrics) }
                                val activeLine = if (lyricLines.all { it.first == 0L }) 0 else lyricLines.indexOfLast { it.first <= currentPosition }.coerceAtLeast(0)
                                val lyricListState = androidx.compose.foundation.lazy.rememberLazyListState()
                                LaunchedEffect(activeLine) { if (lyricLines.isNotEmpty()) lyricListState.animateScrollToItem(activeLine.coerceIn(0, lyricLines.lastIndex)) }
                                LazyColumn(state = lyricListState, contentPadding = PaddingValues(top = 28.dp, bottom = 90.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    items(lyricLines.size) { index ->
                                        Text(lyricLines[index].second, color = if (index == activeLine) Accent else Muted, fontSize = if (index == activeLine) 20.sp else 16.sp, fontWeight = if (index == activeLine) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp))
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        val track = selected
                        if (track == null) EmptyState("还没有正在播放的歌曲", "从音乐库选择一首歌曲开始播放") { tab = 0 }
                        else {
                            val c = getController()
                            val total = duration.coerceAtLeast(track.duration).coerceAtLeast(1L)
                            var draggingPosition by remember(track.id) { mutableFloatStateOf(-1f) }
                            val coverBitmap by produceState<android.graphics.Bitmap?>(initialValue = null, key1 = track.artworkUri) {
                                value = track.artworkUri?.let { artwork -> withContext(Dispatchers.IO) {
                                    runCatching { context.contentResolver.openInputStream(artwork)?.use { BitmapFactory.decodeStream(it) } }.getOrNull()
                                } }
                            }
                            Column(Modifier.fillMaxSize().padding(bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceEvenly) {
                                Spacer(Modifier.height(2.dp))
                                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                                    Box(Modifier.fillMaxWidth(0.82f).aspectRatio(1f).clip(RoundedCornerShape(34.dp)).background(Color.White.copy(alpha = 0.78f)), contentAlignment = Alignment.Center) {
                                        if (coverBitmap != null) Image(bitmap = coverBitmap!!.asImageBitmap(), contentDescription = "专辑封面", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().padding(12.dp).clip(RoundedCornerShape(26.dp)))
                                        else Box(Modifier.fillMaxSize().padding(22.dp).clip(RoundedCornerShape(26.dp)).background(Color(0xFFE8EAF4)), contentAlignment = Alignment.Center) {
                                            Icon(Icons.Default.Album, null, tint = Accent.copy(alpha = 0.72f), modifier = Modifier.size(116.dp))
                                        }
                                    }
                                }
                                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(track.displayTitle, color = Ink, fontSize = 23.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Spacer(Modifier.height(5.dp))
                                    Text(track.displayArtist, color = Muted, fontSize = 14.sp, maxLines = 1)
                                    Spacer(Modifier.height(18.dp))
                                    Slider(value = (if (draggingPosition >= 0f) draggingPosition else currentPosition.toFloat()).coerceIn(0f, total.toFloat()), onValueChange = { draggingPosition = it }, onValueChangeFinished = { if (draggingPosition >= 0f) c?.seekTo(draggingPosition.toLong()); draggingPosition = -1f }, valueRange = 0f..total.toFloat(), modifier = Modifier.fillMaxWidth())
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(formatTime(if (draggingPosition >= 0f) draggingPosition.toLong() else currentPosition), color = Muted, fontSize = 12.sp)
                                        Text(formatTime(total), color = Muted, fontSize = 12.sp)
                                    }
                                    Spacer(Modifier.height(14.dp))
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = { playbackMode = when (playbackMode) { PlaybackMode.REPEAT_ALL -> PlaybackMode.SHUFFLE; PlaybackMode.SHUFFLE -> PlaybackMode.REPEAT_ONE; PlaybackMode.REPEAT_ONE -> PlaybackMode.REPEAT_ALL }; playbackPrefs.edit().putString(MusicService.KEY_MODE, playbackMode.name).apply() }) {
                                            Icon(when (playbackMode) { PlaybackMode.SHUFFLE -> Icons.Default.Shuffle; PlaybackMode.REPEAT_ONE -> Icons.Default.RepeatOne; PlaybackMode.REPEAT_ALL -> Icons.Default.Repeat }, "切换播放模式", tint = Accent, modifier = Modifier.size(25.dp))
                                        }
                                        IconButton(onClick = { c?.seekToPreviousMediaItem() }, modifier = Modifier.size(54.dp)) { Icon(Icons.Default.SkipPrevious, "上一首", tint = Ink, modifier = Modifier.size(34.dp)) }
                                        IconButton(onClick = { if (c?.isPlaying == true) c.pause() else c?.play() }, modifier = Modifier.size(68.dp).clip(CircleShape).background(Accent)) { Icon(if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, "播放或暂停", tint = Color.White, modifier = Modifier.size(38.dp)) }
                                        IconButton(onClick = { if (c != null && c.mediaItemCount > 0) { if (playbackMode == PlaybackMode.SHUFFLE) c.seekTo(chooseRandomIndex(c.mediaItemCount), 0L) else c.seekToNextMediaItem() } }, modifier = Modifier.size(54.dp)) { Icon(Icons.Default.SkipNext, "下一首", tint = Ink, modifier = Modifier.size(34.dp)) }
                                        IconButton(onClick = { tab = 2 }) { Icon(Icons.Default.Subtitles, "查看歌词", tint = Accent, modifier = Modifier.size(25.dp)) }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    Text(when (playbackMode) { PlaybackMode.SHUFFLE -> "完全随机播放"; PlaybackMode.REPEAT_ONE -> "单曲循环"; PlaybackMode.REPEAT_ALL -> "列表循环" }, color = Muted, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            }
        }
        if (trackToAdd != null) AlertDialog(
            onDismissRequest = { trackToAdd = null },
            title = { Text("添加到歌单") },
            text = {
                if (playlists.isEmpty()) Text("请先创建歌单")
                else Column { playlists.forEach { p -> TextButton(onClick = { val song = trackToAdd; if (song != null) scope.launch { dao.addTrack(PlaylistTrackEntity(p.id, song.id, 0)); trackToAdd = null } }) { Text(p.name) } } }
            },
            confirmButton = { TextButton(onClick = { trackToAdd = null }) { Text("关闭") } }
        )
        if (showCreate) AlertDialog(onDismissRequest = { showCreate = false }, title = { Text("新建歌单") }, text = { OutlinedTextField(value = playlistName, onValueChange = { playlistName = it }, label = { Text("歌单名称") }, singleLine = true) }, confirmButton = { TextButton(onClick = { val name = playlistName.trim(); if (name.isNotEmpty()) scope.launch { dao.addPlaylist(PlaylistEntity(name = name)); playlistName = ""; showCreate = false } }) { Text("创建") } }, dismissButton = { TextButton(onClick = { showCreate = false }) { Text("取消") } })
        if (showAppearance) AlertDialog(
            onDismissRequest = { showAppearance = false },
            title = { Text("播放器外观") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("自定义背景图片", fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { backgroundPicker.launch(arrayOf("image/*")) }) { Icon(Icons.Default.Image, null); Text("从相册选择") }
                        TextButton(onClick = { backgroundUri = null; appPrefs.edit().remove("background_uri").apply() }) { Text("恢复默认") }
                    }
                    Text("背景模糊 · ${backgroundBlur.toInt()}", fontSize = 13.sp)
                    Slider(value = backgroundBlur, onValueChange = { backgroundBlur = it; appPrefs.edit().putFloat("background_blur", it).apply() }, valueRange = 0f..36f)
                    Text("背景显示强度 · ${(backgroundOpacity * 100).toInt()}%", fontSize = 13.sp)
                    Slider(value = backgroundOpacity, onValueChange = { backgroundOpacity = it; appPrefs.edit().putFloat("background_opacity", it).apply() }, valueRange = 0.1f..1f)
                    Text("背景亮度 · ${(backgroundBrightness * 100).toInt()}%", fontSize = 13.sp)
                    Slider(value = backgroundBrightness, onValueChange = { backgroundBrightness = it; appPrefs.edit().putFloat("background_brightness", it).apply() }, valueRange = 0.2f..1.4f)
                }
            },
            confirmButton = { TextButton(onClick = { showAppearance = false }) { Text("完成") } }
        )
      }
    }
}

@Composable private fun TrackRow(track: Track, active: Boolean, onAdd: () -> Unit, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (active) Color(0xFFEDEFFD) else Color.Transparent).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(46.dp).clip(RoundedCornerShape(13.dp)).background(Color(0xFFE9EAF0)), contentAlignment = Alignment.Center) { Icon(Icons.Default.MusicNote, null, tint = if (active) Accent else Muted) }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(track.displayTitle, color = Ink, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${track.displayArtist} · ${track.album}", color = Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(formatTime(track.duration), color = Muted, fontSize = 12.sp)
        IconButton(onClick = onAdd) { Icon(Icons.Default.PlaylistAdd, "添加到第一个歌单", tint = Muted) }
    }
}

@Composable private fun EmptyState(title: String, subtitle: String, action: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.LibraryMusic, null, tint = Color(0xFFB6B8C2), modifier = Modifier.size(42.dp))
            Spacer(Modifier.height(12.dp)); Text(title, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
            Spacer(Modifier.height(5.dp)); Text(subtitle, color = Muted, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp)); TextButton(onClick = action) { Text("继续", color = Accent) }
        }
    }
}

private fun formatTime(ms: Long): String {
    val sec = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(sec / 60, sec % 60)
}

private fun parseLrc(raw: String): List<Pair<Long, String>> {
    val regex = Regex("\\[(\\d{2}):(\\d{2})(?:\\.(\\d{1,3}))?]")
    val result = mutableListOf<Pair<Long, String>>()
    raw.lines().forEach { line ->
        val matches = regex.findAll(line).toList()
        val text = regex.replace(line, "").trim()
        if (matches.isEmpty()) {
            if (text.isNotBlank() && !text.startsWith("[")) result += 0L to text
        } else matches.forEach { m ->
            val minute = m.groupValues[1].toLongOrNull() ?: 0L
            val second = m.groupValues[2].toLongOrNull() ?: 0L
            val fraction = m.groupValues[3].padEnd(3, '0').take(3).toLongOrNull() ?: 0L
            result += (minute * 60_000 + second * 1_000 + fraction) to text
        }
    }
    return result.sortedBy { it.first }
}
