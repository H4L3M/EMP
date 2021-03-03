package com.mowakib.emp

import android.os.Bundle
import android.util.Log
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import com.google.android.exoplayer2.ui.StyledPlayerView

class MainActivity : AppCompatActivity() {

    private val urlSources =
        listOf(MPD, M3U8, MP3)
//        listOf(MPD, M3U8, MP3, OGG, MP4_S, MP4, MKV, MPEG_TS, AAC, WEBM, FLAC, FLV)

    private var position = -1

    private lateinit var emp: Emp

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val playNext = findViewById<Button>(R.id.play_next)
        val playerView = findViewById<StyledPlayerView>(R.id.player_view)
        emp = Emp.get().init(this, playerView)

        playNext.setOnClickListener {
            position++
            if (position <= 2) {
                emp.load(urlSources[position])
            } else {
                position = 0
                emp.load(urlSources[position])
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
        emp.pause()
    }

    override fun onStop() {
        super.onStop()
        emp.stop()
    }

    override fun onResume() {
        super.onResume()
        emp.resume()
    }

    override fun onDestroy() {
        super.onDestroy()
        emp.destroy()
    }
    companion object {
        private const val TAG = "MainActivity"
    }
}

