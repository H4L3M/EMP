package com.mowakib.emp

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import com.google.android.exoplayer2.ui.StyledPlayerView

class MainActivity : AppCompatActivity() {

    private val urlSources =
        listOf(MPD, M3U8, MP3, OGG, MP4_S, MP4, MKV, MPEG_TS, AAC, WEBM, FLAC, FLV)

    private var position = -1;

    private lateinit var emp: Emp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val playNext = findViewById<Button>(R.id.play_next)
        val playerView = findViewById<StyledPlayerView>(R.id.player_view)
        emp = Emp.with(this).init(playerView)

        playNext.setOnClickListener {
            position++
            if (position <= 11) {
                emp.load(urlSources[position])
            } else {
                position = 0;
            }
            Log.d(TAG, "onCreate: Position => $position \n ${urlSources[position]}")
        }

    }

    override fun onStart() {
        super.onStart()
        emp.start()
    }

    override fun onPause() {
        super.onPause()
        emp.release()
    }
}

private const val TAG = "MainActivity"

private const val MPD = "https://storage.googleapis.com/wvmedia/clear/h264/tears/tears.mpd"
private const val M3U8 =
    "https://cdnamd-hls-globecast.akamaized.net/live/ramdisk/al_aoula_inter/hls_snrt/al_aoula_inter.m3u8"
private const val MP3 = "https://storage.googleapis.com/exoplayer-test-media-0/play.mp3"
private const val OGG = "https://storage.googleapis.com/exoplayer-test-media-1/ogg/play.ogg"
private const val MP4_S =
    "https://playready.directtaps.net/smoothstreaming/SSWSS720H264PR/SuperSpeedway_720.ism/Manifest"
private const val MP4 = "https://html5demos.com/assets/dizzy.mp4"
private const val MKV =
    "https://storage.googleapis.com/exoplayer-test-media-1/mkv/android-screens-lavf-56.36.100-aac-avc-main-1280x720.mkv"
private const val MPEG_TS =
    "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_4x3/gear1/fileSequence0.ts"
private const val AAC =
    "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_4x3/gear0/fileSequence0.aac"
private const val WEBM =
    "https://storage.googleapis.com/wvmedia/2019/clear/av1/24/webm/llama_av1_480p_400.webm"
private const val FLAC = "https://storage.googleapis.com/exoplayer-test-media-1/flac/play.flac"
private const val FLV = "https://vod.leasewebcdn.com/bbb.flv?ri=1024&rs=150&start=0"