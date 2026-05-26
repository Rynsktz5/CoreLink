package com.example.corelink

import android.graphics.Color
import android.media.MediaPlayer
import android.view.*
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import java.io.File

class ChatAdapter(private val messages: MutableList<ChatLogEntry>) :
    RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_TEXT = 1
        private const val VIEW_TYPE_VOICE = 2
    }

    private var lastPosition = -1

    // Playback state variables
    private var mediaPlayer: MediaPlayer? = null
    private var playingPosition = -1
    private var isPlaying = false
    private var progressUpdater: Runnable? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val durationCache = HashMap<String, String>()

    class TextViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val text: TextView = view.findViewById(R.id.messageText)
        val senderName: TextView = view.findViewById(R.id.senderName)
    }

    class VoiceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val container: View = view.findViewById(R.id.voiceBubbleContainer)
        val playBtn: MaterialButton = view.findViewById(R.id.playBtn)
        val waveform: VoiceWaveformView = view.findViewById(R.id.voiceWaveform)
        val micIcon: ImageView = view.findViewById(R.id.voiceMicIcon)
        val timeText: TextView = view.findViewById(R.id.voiceTimeText)
        val metaText: TextView = view.findViewById(R.id.voiceMetaText)
        val senderName: TextView = view.findViewById(R.id.voiceSenderName)
    }

    override fun getItemViewType(position: Int): Int {
        val entry = messages[position]
        return if (entry.kind == "voice") VIEW_TYPE_VOICE else VIEW_TYPE_TEXT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == VIEW_TYPE_VOICE) {
            val view = inflater.inflate(R.layout.item_message_voice, parent, false)
            VoiceViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.item_message, parent, false)
            TextViewHolder(view)
        }
    }

    override fun getItemCount(): Int = messages.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val entry = messages[position]
        val context = holder.itemView.context
        val density = context.resources.displayMetrics.density
        val marginPx = (64 * density).toInt()

        val primaryColor = MaterialColors.getColor(context, com.google.android.material.R.attr.colorPrimary, Color.BLUE)
        val onPrimary = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnPrimary, Color.WHITE)
        val onSurfaceVariant = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnSurfaceVariant, Color.BLACK)
        val surfaceVariant = MaterialColors.getColor(context, com.google.android.material.R.attr.colorSurfaceVariant, Color.LTGRAY)

        val containerLayout = holder.itemView as android.widget.LinearLayout

        if (holder is TextViewHolder) {
            holder.text.text = entry.content
            val params = holder.text.layoutParams as ViewGroup.MarginLayoutParams

            if (entry.isSent) {
                containerLayout.gravity = Gravity.END
                holder.text.setBackgroundResource(R.drawable.bubble_sent)
                holder.text.setTextColor(onPrimary)
                params.marginStart = marginPx
                params.marginEnd = 0
                holder.senderName.visibility = View.GONE
            } else {
                containerLayout.gravity = Gravity.START
                holder.text.setBackgroundResource(R.drawable.bubble_received)
                holder.text.setTextColor(onSurfaceVariant)
                params.marginStart = 0
                params.marginEnd = marginPx
                holder.senderName.visibility = View.VISIBLE
                holder.senderName.text = entry.sender ?: "Peer"
            }
            holder.text.layoutParams = params

        } else if (holder is VoiceViewHolder) {
            val path = entry.content
            val file = File(path)
            val sizeKb = if (file.exists()) file.length() / 1024 else 0
            holder.metaText.text = "16 kHz • ${sizeKb} KB"
            holder.waveform.setAudioPath(path)

            val params = holder.container.layoutParams as ViewGroup.MarginLayoutParams

            if (entry.isSent) {
                containerLayout.gravity = Gravity.END
                holder.container.setBackgroundResource(R.drawable.bubble_sent)
                params.marginStart = marginPx
                params.marginEnd = 0
                holder.senderName.visibility = View.GONE

                // Sent theme colors
                holder.playBtn.setIconTintResource(com.google.android.material.R.color.material_dynamic_neutral95)
                holder.playBtn.rippleColor = android.content.res.ColorStateList.valueOf(Color.argb(40, 255, 255, 255))
                holder.waveform.activeColor = onPrimary
                holder.waveform.inactiveColor = Color.argb(120, Color.red(onPrimary), Color.green(onPrimary), Color.blue(onPrimary))
                holder.timeText.setTextColor(onPrimary)
                holder.metaText.setTextColor(onPrimary)
                holder.micIcon.setColorFilter(onPrimary)
            } else {
                containerLayout.gravity = Gravity.START
                holder.container.setBackgroundResource(R.drawable.bubble_received)
                params.marginStart = 0
                params.marginEnd = marginPx
                holder.senderName.visibility = View.VISIBLE
                holder.senderName.text = entry.sender ?: "Peer"

                // Received theme colors
                holder.playBtn.setIconTintResource(com.google.android.material.R.color.material_dynamic_primary50)
                holder.playBtn.rippleColor = android.content.res.ColorStateList.valueOf(Color.argb(40, 0, 0, 0))
                holder.waveform.activeColor = primaryColor
                holder.waveform.inactiveColor = Color.argb(120, Color.red(onSurfaceVariant), Color.green(onSurfaceVariant), Color.blue(onSurfaceVariant))
                holder.timeText.setTextColor(onSurfaceVariant)
                holder.metaText.setTextColor(onSurfaceVariant)
                holder.micIcon.setColorFilter(onSurfaceVariant)
            }
            holder.container.layoutParams = params

            // Bind seeking touch to Waveform scrubber
            holder.waveform.onSeekListener = { pct ->
                if (playingPosition == position) {
                    val mp = mediaPlayer
                    if (mp != null) {
                        mp.seekTo((pct * mp.duration).toInt())
                    }
                }
            }

            // Set play button state
            if (playingPosition == position) {
                holder.playBtn.setIconResource(if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
                val mp = mediaPlayer
                if (mp != null && mp.duration > 0) {
                    holder.waveform.progress = mp.currentPosition.toFloat() / mp.duration.toFloat()
                    holder.timeText.text = "${formatDuration(mp.currentPosition.toLong())} / ${getCachedDuration(path)}"
                } else {
                    holder.waveform.progress = 0f
                    holder.timeText.text = getCachedDuration(path)
                }
                startProgressUpdater(holder, position)
            } else {
                holder.playBtn.setIconResource(android.R.drawable.ic_media_play)
                holder.waveform.progress = 0f
                holder.timeText.text = getCachedDuration(path)
            }

            holder.playBtn.setOnClickListener {
                if (playingPosition == position) {
                    val mp = mediaPlayer
                    if (mp != null) {
                        if (isPlaying) {
                            mp.pause()
                            isPlaying = false
                            holder.playBtn.setIconResource(android.R.drawable.ic_media_play)
                        } else {
                            mp.start()
                            isPlaying = true
                            holder.playBtn.setIconResource(android.R.drawable.ic_media_pause)
                            startProgressUpdater(holder, position)
                        }
                    }
                } else {
                    playAudioFile(path, position)
                }
            }
        }

        setAnimation(holder.itemView, position)
    }

    private fun playAudioFile(path: String, position: Int) {
        stopPlayback()
        try {
            val file = File(path)
            if (!file.exists()) return

            mediaPlayer = MediaPlayer().apply {
                setDataSource(path)
                prepare()
                start()
                setOnCompletionListener {
                    stopPlayback()
                }
            }
            playingPosition = position
            isPlaying = true
            notifyItemChanged(position)
        } catch (e: Exception) {
            e.printStackTrace()
            stopPlayback()
        }
    }

    private fun startProgressUpdater(holder: VoiceViewHolder, position: Int) {
        progressUpdater?.let { handler.removeCallbacks(it) }

        val runnable = object : Runnable {
            override fun run() {
                val mp = mediaPlayer
                if (mp != null && playingPosition == position && isPlaying) {
                    val curr = mp.currentPosition
                    val dur = mp.duration
                    if (dur > 0) {
                        holder.waveform.progress = curr.toFloat() / dur.toFloat()
                        val path = messages[position].content
                        holder.timeText.text = "${formatDuration(curr.toLong())} / ${getCachedDuration(path)}"
                    }
                    handler.postDelayed(this, 100)
                }
            }
        }
        progressUpdater = runnable
        handler.post(runnable)
    }

    private fun stopPlayback() {
        progressUpdater?.let { handler.removeCallbacks(it) }
        progressUpdater = null
        try {
            mediaPlayer?.stop()
            mediaPlayer?.release()
        } catch (_: Exception) {}
        mediaPlayer = null

        val oldPos = playingPosition
        playingPosition = -1
        isPlaying = false
        if (oldPos != -1) {
            notifyItemChanged(oldPos)
        }
    }

    fun cleanup() {
        stopPlayback()
    }

    private fun getAudioDuration(path: String): String {
        return try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(path)
            val time = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            val durationMs = time?.toLong() ?: 0L
            formatDuration(durationMs)
        } catch (_: Exception) {
            "0:00"
        }
    }

    private fun getCachedDuration(path: String): String {
        return durationCache.getOrPut(path) {
            getAudioDuration(path)
        }
    }

    private fun formatDuration(ms: Long): String {
        val sec = (ms / 1000) % 60
        val min = (ms / 60000) % 60
        return String.format("%02d:%02d", min, sec)
    }

    private fun setAnimation(viewToAnimate: View, position: Int) {
        if (position > lastPosition) {
            val animation = AnimationUtils.loadAnimation(viewToAnimate.context, R.anim.chat_item_fade_in)
            viewToAnimate.startAnimation(animation)
            lastPosition = position
        }
    }
}
