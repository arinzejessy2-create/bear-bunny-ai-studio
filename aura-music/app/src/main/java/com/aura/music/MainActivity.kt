package com.aura.music

import android.Manifest
import android.content.ComponentName
import android.content.ContentUris
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.*
import android.graphics.Color
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors

data class Song(val id: Long, val title: String, val artist: String, val uri: Uri)

class MainActivity : AppCompatActivity() {
    private lateinit var root: LinearLayout
    private lateinit var list: LinearLayout
    private lateinit var mini: LinearLayout
    private lateinit var miniTitle: TextView
    private var controller: MediaController? = null
    private var controllerFuture: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null
    private val songs = mutableListOf<Song>()
    private val prefs by lazy { getSharedPreferences("aura_playback", MODE_PRIVATE) }
    private var downY = 0f
    private var fullPlayerVisible = false
    private val handler = Handler(Looper.getMainLooper())
    private val saveTask = object : Runnable {
        override fun run() { savePlaybackState(); handler.postDelayed(this, 1000L) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        requestAudioPermission()
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync()
        controllerFuture?.addListener({
            try {
                val c = controllerFuture?.get() ?: return@addListener
                controller = c
                c.addListener(object : Player.Listener {
                    override fun onMediaItemTransition(item: MediaItem?, reason: Int) { updateMini(); savePlaybackState() }
                    override fun onIsPlayingChanged(isPlaying: Boolean) { updateMini(); savePlaybackState() }
                    override fun onPlaybackStateChanged(playbackState: Int) { updateMini() }
                })
                if (hasAudioPermission()) restoreLastSong()
                handler.removeCallbacks(saveTask)
                handler.post(saveTask)
            } catch (_: Exception) {
                Toast.makeText(this, "AURA player could not start. Please reopen the app.", Toast.LENGTH_LONG).show()
            }
        }, MoreExecutors.directExecutor())
    }

    private fun buildUi() {
        root = LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setBackgroundColor(Color.rgb(9,9,15)); setPadding(24,28,24,16) }
        root.addView(TextView(this).apply { text="AURA MUSIC"; textSize=24f; setTextColor(Color.WHITE); gravity=Gravity.CENTER_VERTICAL })
        root.addView(TextView(this).apply { text="YOUR MUSIC LIBRARY"; textSize=12f; setTextColor(0xFFAAA6B8.toInt()); setPadding(0,18,0,12) })
        val scroll=ScrollView(this)
        list=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL }
        scroll.addView(list)
        root.addView(scroll,LinearLayout.LayoutParams(-1,0,1f))
        mini=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; setPadding(16,12,16,12); setBackgroundColor(0xFF211A32.toInt()); setOnClickListener { showFullPlayer() } }
        miniTitle=TextView(this).apply { text="Choose a song"; textSize=16f; setTextColor(Color.WHITE) }
        mini.addView(miniTitle)
        mini.addView(TextView(this).apply { text="TAP TO OPEN PLAYER • PLAYING CONTINUES IN BACKGROUND"; textSize=10f; setTextColor(0xFFC4B5FD.toInt()) })
        root.addView(mini,LinearLayout.LayoutParams(-1,-2))
        setContentView(root)
    }

    private fun permissionName() = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    private fun hasAudioPermission() = ContextCompat.checkSelfPermission(this, permissionName()) == PackageManager.PERMISSION_GRANTED
    private fun requestAudioPermission() {
        if (hasAudioPermission()) loadSongs() else ActivityCompat.requestPermissions(this,arrayOf(permissionName()),40)
    }
    override fun onRequestPermissionsResult(requestCode:Int, permissions:Array<out String>, results:IntArray) {
        super.onRequestPermissionsResult(requestCode,permissions,results)
        if (requestCode==40 && results.firstOrNull()==PackageManager.PERMISSION_GRANTED) { loadSongs(); restoreLastSong() }
        else if (requestCode==40) Toast.makeText(this,"Allow music access to show songs stored on your phone.",Toast.LENGTH_LONG).show()
    }
    private fun loadSongs() {
        songs.clear()
        val collection=MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection=arrayOf(MediaStore.Audio.Media._ID,MediaStore.Audio.Media.TITLE,MediaStore.Audio.Media.ARTIST)
        try {
            contentResolver.query(collection,projection,null,null,"${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC")?.use { c ->
                val idCol=c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol=c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol=c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                while(c.moveToNext()) { val id=c.getLong(idCol); songs.add(Song(id,c.getString(titleCol) ?: "Unknown",c.getString(artistCol) ?: "Unknown",ContentUris.withAppendedId(collection,id))) }
            }
        } catch (_: Exception) { Toast.makeText(this,"Could not scan the phone's audio library.",Toast.LENGTH_LONG).show() }
        list.removeAllViews()
        if(songs.isEmpty()) list.addView(TextView(this).apply { text="No audio files found. Allow AURA access to audio."; setTextColor(Color.WHITE); setPadding(0,24,0,24) })
        songs.forEachIndexed { index,song ->
            list.addView(TextView(this).apply { text="${song.title}\n${song.artist}"; textSize=16f; setTextColor(Color.WHITE); setPadding(12,16,8,16); setOnClickListener { playFrom(index) } })
            list.addView(View(this).apply { setBackgroundColor(0xFF292536.toInt()) },LinearLayout.LayoutParams(-1,1))
        }
        if(controller!=null) restoreLastSong()
    }
    private fun playFrom(index:Int) {
        val c=controller ?: return
        c.setMediaItems(songs.map { MediaItem.fromUri(it.uri) },index,0)
        c.prepare(); c.play()
        prefs.edit().putLong("last_id",songs[index].id).putInt("last_index",index).putLong("last_position",0).putBoolean("was_playing",true).apply()
        updateMini(); showFullPlayer()
    }
    private fun restoreLastSong() {
        if(!hasAudioPermission() || songs.isEmpty()) return
        val id=prefs.getLong("last_id",-1L); val savedIndex=prefs.getInt("last_index",-1)
        val index=songs.indexOfFirst { it.id==id }.takeIf { it>=0 } ?: savedIndex.takeIf { it in songs.indices } ?: -1
        if(index>=0 && controller?.mediaItemCount==0) {
            controller?.setMediaItems(songs.map { MediaItem.fromUri(it.uri) },index,prefs.getLong("last_position",0L).coerceAtLeast(0L))
            controller?.prepare()
            if(prefs.getBoolean("was_playing",false)) controller?.play()
        }
        updateMini()
    }
    private fun savePlaybackState() {
        val c=controller ?: return; val i=c.currentMediaItemIndex
        if(i in songs.indices && c.currentMediaItem!=null) prefs.edit().putLong("last_id",songs[i].id).putInt("last_index",i).putLong("last_position",c.currentPosition.coerceAtLeast(0L)).putBoolean("was_playing",c.isPlaying).apply()
    }
    private fun updateMini() {
        val c=controller ?: return; val i=c.currentMediaItemIndex
        if(i in songs.indices) miniTitle.text="${songs[i].title}  ${if(c.isPlaying) "▶" else "Ⅱ"}"
    }
    private fun showFullPlayer() {
        val c=controller ?: return
        val overlay=LinearLayout(this).apply { orientation=LinearLayout.VERTICAL; gravity=Gravity.CENTER; setPadding(28,40,28,32); setBackgroundColor(0xFF100D19.toInt()) }
        overlay.addView(TextView(this).apply { text="⌄  SWIPE DOWN"; textSize=14f; gravity=Gravity.CENTER; setTextColor(0xFFC4B5FD.toInt()); setPadding(0,0,0,30); setOnClickListener { closeFullPlayer() } })
        overlay.addView(TextView(this).apply { text="♫"; textSize=110f; gravity=Gravity.CENTER; setTextColor(0xFFC4B5FD.toInt()); setBackgroundColor(0xFF282039.toInt()) },LinearLayout.LayoutParams(-1,0,1f))
        overlay.addView(TextView(this).apply { text=songs.getOrNull(c.currentMediaItemIndex)?.title ?: "AURA Music"; textSize=23f; gravity=Gravity.CENTER; setTextColor(Color.WHITE); setPadding(0,28,0,4) })
        overlay.addView(TextView(this).apply { text=songs.getOrNull(c.currentMediaItemIndex)?.artist ?: ""; textSize=15f; gravity=Gravity.CENTER; setTextColor(0xFFB8B2C6.toInt()) })
        val controls=LinearLayout(this).apply { gravity=Gravity.CENTER; setPadding(0,32,0,0) }
        fun button(label:String, action:()->Unit) { controls.addView(Button(this).apply { text=label; setOnClickListener { action() } }) }
        button("⏮"){c.seekToPrevious();showFullPlayer()}; button(if(c.isPlaying) "Ⅱ" else "▶"){if(c.isPlaying)c.pause() else c.play();showFullPlayer()}; button("⏭"){c.seekToNext();showFullPlayer()}
        overlay.addView(controls); fullPlayerVisible=true; setContentView(overlay)
    }
    override fun dispatchTouchEvent(event:MotionEvent):Boolean {
        if(fullPlayerVisible) when(event.actionMasked) {
            MotionEvent.ACTION_DOWN -> downY=event.rawY
            MotionEvent.ACTION_UP -> if(event.rawY-downY>110f) { fullPlayerVisible=false; setContentView(root) }
        }
        return super.dispatchTouchEvent(event)
    }
    private fun closeFullPlayer(){fullPlayerVisible=false;setContentView(root)}
    override fun onStop(){savePlaybackState();super.onStop()}
    override fun onResume(){super.onResume();if(::list.isInitialized && hasAudioPermission()) loadSongs()}
    override fun onDestroy(){handler.removeCallbacks(saveTask);savePlaybackState();controllerFuture?.let { MediaController.releaseFuture(it) };super.onDestroy()}
}