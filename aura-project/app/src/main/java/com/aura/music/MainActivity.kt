package com.aura.music
import android.content.ComponentName
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture

private data class Song(val uri:Uri,val name:String)
class MainActivity:ComponentActivity(){
 private var future:ListenableFuture<MediaController>?=null
 private var player:MediaController?=null
 override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState)
 future=MediaController.Builder(this,SessionToken(this,ComponentName(this,PlaybackService::class.java))).buildAsync()
 future?.addListener({player=future?.get()},mainExecutor)
 setContent { val songs= remember { mutableStateListOf<Song>() }; var current by remember { mutableIntStateOf(-1) }; var playing by remember { mutableStateOf(false) }
 val pick=rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()){uris->uris.forEach{u->runCatching{contentResolver.takePersistableUriPermission(u,1)};if(songs.none{it.uri==u})songs.add(Song(u,u.lastPathSegment?.substringAfterLast("/")?:"Audio"))}}
 MaterialTheme(colorScheme=darkColorScheme(primary=Color(0xFF9D8CFF),background=Color(0xFF080A12),surface=Color(0xFF171A29))){Column(Modifier.fillMaxSize().background(Color(0xFF080A12)).padding(18.dp)){
 Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("A U R A",color=Color(0xFF9D8CFF),letterSpacing=5.sp);Text("Your sound. Your space.",style=MaterialTheme.typography.headlineSmall,color=Color.White)};IconButton(onClick={pick.launch(arrayOf("audio/*"))}){Icon(Icons.Default.Add,"Add music",tint=Color.White)}}
 Spacer(Modifier.height(22.dp));Box(Modifier.fillMaxWidth().height(170.dp).background(Color(0xFF282343),RoundedCornerShape(24.dp)),contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally){Icon(Icons.Default.GraphicEq,null,tint=Color.White,modifier=Modifier.size(48.dp));Text("NOW PLAYING",color=Color.LightGray);Text(songs.getOrNull(current)?.name?:"Choose your first song",color=Color.White)}}
 Spacer(Modifier.height(18.dp));Row(verticalAlignment=Alignment.CenterVertically){Text("Your library",color=Color.White,style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f));Text("${songs.size} tracks",color=Color.LightGray)}
 if(songs.isEmpty()){Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center){Button(onClick={pick.launch(arrayOf("audio/*"))}){Text("Choose music from phone")}}}else{LazyColumn(Modifier.weight(1f)){itemsIndexed(songs){i,s->Row(Modifier.fillMaxWidth().padding(vertical=5.dp).background(Color(0xFF171A29),RoundedCornerShape(14.dp)).clickable{current=i;player?.setMediaItems(songs.map{MediaItem.Builder().setUri(it.uri).setMediaMetadata(MediaMetadata.Builder().setTitle(it.name).build()).build()},i,0);player?.prepare();player?.play();playing=true}.padding(14.dp),verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.MusicNote,null,tint=Color(0xFF9D8CFF));Spacer(Modifier.width(12.dp));Text(s.name,color=Color.White)}}}}
 Row(Modifier.fillMaxWidth().background(Color(0xFF171A29),RoundedCornerShape(20.dp)),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically){IconButton(onClick={player?.seekToPreviousMediaItem()}){Icon(Icons.Default.SkipPrevious,null,tint=Color.White)};IconButton(onClick={if(playing)player?.pause() else player?.play();playing=!playing}){Icon(if(playing)Icons.Default.Pause else Icons.Default.PlayArrow,null,tint=Color(0xFF9D8CFF),modifier=Modifier.size(38.dp))};IconButton(onClick={player?.seekToNextMediaItem()}){Icon(Icons.Default.SkipNext,null,tint=Color.White)}}
 Spacer(Modifier.height(10.dp));Text("Offline playback • Lock-screen controls",color=Color.Gray,style=MaterialTheme.typography.labelSmall,modifier=Modifier.align(Alignment.CenterHorizontally))
 }} }
 }
 override fun onDestroy(){future?.let{MediaController.releaseFuture(it)};player=null;super.onDestroy()}
}
