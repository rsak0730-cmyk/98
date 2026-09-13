package com.custom.dynamicislandos

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Service
import android.content.*
import android.graphics.PixelFormat
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.*
import android.view.animation.OvershootInterpolator
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast

class DynamicIslandService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var params: WindowManager.LayoutParams

    private lateinit var compactView: LinearLayout
    private lateinit var expandedMusicView: RelativeLayout
    private lateinit var dragDropView: LinearLayout
    
    private lateinit var compactText: TextView
    private lateinit var musicTitleText: TextView
    private lateinit var musicArtistText: TextView
    private lateinit var albumArt: ImageView
    
    private lateinit var btnPlayPause: ImageView
    private lateinit var btnNext: ImageView
    private lateinit var btnPrev: ImageView

    private var isExpanded = false
    private val compactWidth = 350
    private val compactHeight = 110

    private lateinit var mediaSessionManager: MediaSessionManager
    private var activeMediaController: MediaController? = null

    private val notificationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val title = intent?.getStringExtra("title") ?: return
            val text = intent.getStringExtra("text") ?: ""
            
            compactText.text = "$title: $text"
            
            if (!isExpanded) {
                animateIsland(compactWidth, 650, compactHeight, 130, compactView)
                Handler(Looper.getMainLooper()).postDelayed({
                    if (!isExpanded) animateIsland(650, compactWidth, 130, compactHeight, compactView)
                }, 3500)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    override fun onCreate() {
        super.onCreate()

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_dynamic_island, null)

        compactView = floatingView.findViewById(R.id.compactView)
        expandedMusicView = floatingView.findViewById(R.id.expandedMusicView)
        dragDropView = floatingView.findViewById(R.id.dragDropView)
        
        compactText = floatingView.findViewById(R.id.compactText)
        musicTitleText = floatingView.findViewById(R.id.musicTitleText)
        musicArtistText = floatingView.findViewById(R.id.musicArtistText)
        albumArt = floatingView.findViewById(R.id.albumArt)
        
        btnPlayPause = floatingView.findViewById(R.id.btnPlayPause)
        btnNext = floatingView.findViewById(R.id.btnNext)
        btnPrev = floatingView.findViewById(R.id.btnPrev)

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else WindowManager.LayoutParams.TYPE_PHONE

        params = WindowManager.LayoutParams(
            compactWidth, compactHeight, layoutFlag,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 30
        }

        windowManager.addView(floatingView, params)
        setupGestures()
        setupMediaSessions()
        setupVivoDragAndGo()

        val filter = IntentFilter("com.custom.dynamicislandos.NOTIFICATION")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(notificationReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else registerReceiver(notificationReceiver, filter)
    }

    private fun setupMediaSessions() {
        mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val componentName = ComponentName(this, IslandNotificationListener::class.java)

        try {
            val controllers = mediaSessionManager.getActiveSessions(componentName)
            if (controllers.isNotEmpty()) {
                registerMediaController(controllers[0])
            }

            mediaSessionManager.addOnActiveSessionsChangedListener({ newControllers ->
                if (!newControllers.isNullOrEmpty()) {
                    registerMediaController(newControllers[0])
                }
            }, componentName)
            
        } catch (e: SecurityException) {}

        btnPlayPause.setOnClickListener {
            val state = activeMediaController?.playbackState?.state
            if (state == PlaybackState.STATE_PLAYING) {
                activeMediaController?.transportControls?.pause()
            } else {
                activeMediaController?.transportControls?.play()
            }
        }
        btnNext.setOnClickListener { activeMediaController?.transportControls?.skipToNext() }
        btnPrev.setOnClickListener { activeMediaController?.transportControls?.skipToPrevious() }
    }

    private val mediaControllerCallback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) {
            super.onMetadataChanged(metadata)
            metadata?.let {
                musicTitleText.text = it.getString(MediaMetadata.METADATA_KEY_TITLE) ?: "Unknown"
                musicArtistText.text = it.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "Unknown"
                
                val art = it.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) 
                    ?: it.getBitmap(MediaMetadata.METADATA_KEY_ART)
                if (art != null) {
                    albumArt.setImageBitmap(art)
                } else {
                    albumArt.setBackgroundColor(android.graphics.Color.DKGRAY)
                }
            }
        }

        override fun onPlaybackStateChanged(state: PlaybackState?) {
            super.onPlaybackStateChanged(state)
            if (state?.state == PlaybackState.STATE_PLAYING) {
                btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
            } else {
                btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
            }
        }
    }

    private fun registerMediaController(controller: MediaController) {
        activeMediaController?.unregisterCallback(mediaControllerCallback)
        activeMediaController = controller
        activeMediaController?.registerCallback(mediaControllerCallback)
        
        mediaControllerCallback.onMetadataChanged(controller.metadata)
        mediaControllerCallback.onPlaybackStateChanged(controller.playbackState)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupGestures() {
        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (!isExpanded) {
                    animateIsland(params.width, 850, params.height, 280, expandedMusicView)
                    isExpanded = true
                } else {
                    animateIsland(params.width, compactWidth, params.height, compactHeight, compactView)
                    isExpanded = false
                }
                return true
            }
        })

        floatingView.setOnTouchListener { _, event ->
            gestureDetector.onTouchEvent(event)
            true
        }
    }

    private fun setupVivoDragAndGo() {
        floatingView.setOnDragListener { _, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> event.clipDescription.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN)
                DragEvent.ACTION_DRAG_ENTERED -> {
                    animateIsland(params.width, 600, params.height, 180, dragDropView)
                    true
                }
                DragEvent.ACTION_DROP -> {
                    val item = event.clipData.getItemAt(0)
                    Toast.makeText(this, "Dropped: ${item.text}", Toast.LENGTH_LONG).show()
                    animateIsland(600, compactWidth, 180, compactHeight, compactView)
                    true
                }
                DragEvent.ACTION_DRAG_EXITED, DragEvent.ACTION_DRAG_ENDED -> {
                    if (params.width != compactWidth && !isExpanded) {
                        animateIsland(params.width, compactWidth, params.height, compactHeight, compactView)
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun animateIsland(startW: Int, endW: Int, startH: Int, endH: Int, targetView: View) {
        compactView.visibility = View.GONE
        expandedMusicView.visibility = View.GONE
        dragDropView.visibility = View.GONE
        targetView.visibility = View.VISIBLE

        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 450
            interpolator = OvershootInterpolator(1.2f)
            addUpdateListener { animation ->
                val fraction = animation.animatedFraction
                params.width = (startW + (endW - startW) * fraction).toInt()
                params.height = (startH + (endH - startH) * fraction).toInt()
                windowManager.updateViewLayout(floatingView, params)
            }
            start()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(notificationReceiver)
        activeMediaController?.unregisterCallback(mediaControllerCallback)
        if (::floatingView.isInitialized) windowManager.removeView(floatingView)
    }
}
