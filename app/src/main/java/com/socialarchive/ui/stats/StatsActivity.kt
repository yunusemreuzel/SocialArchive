package com.socialarchive.ui.stats

import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.socialarchive.R
import com.socialarchive.data.PostRepository
import com.socialarchive.data.model.Platform
import com.socialarchive.databinding.ActivityStatsBinding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar

class StatsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityStatsBinding
    private lateinit var repository: PostRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityStatsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        repository = PostRepository(this)
        binding.btnBack.setOnClickListener { finish() }
        loadStats()
    }

    private fun loadStats() {
        lifecycleScope.launch {
            // ── Özet sayılar ──────────────────────────────────────────────
            val total     = repository.getPostCount().first()
            val read      = repository.getReadCount()
            val favorites = repository.getFavoriteCount()

            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val todayStart = cal.timeInMillis
            val today      = repository.getTodayCount(todayStart)

            cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
            val weekStart = cal.timeInMillis

            cal.set(Calendar.DAY_OF_MONTH, 1)
            val monthStart = cal.timeInMillis

            val week  = repository.getCountSince(weekStart)
            val month = repository.getCountSince(monthStart)

            binding.tvTotalCount.text = total.toString()
            binding.tvReadCount.text  = read.toString()
            binding.tvFavCount.text   = favorites.toString()
            binding.tvTodayCount.text = today.toString()
            binding.tvWeekCount.text  = week.toString()
            binding.tvMonthCount.text = month.toString()

            val readRate = if (total > 0) ((read * 100f) / total).toInt() else 0
            binding.progressReadRate.progress = readRate
            binding.tvReadRate.text = "$readRate%"

            buildPlatformStats(total)
            buildCategoryStats(total)
            buildWeeklyBars()
        }
    }

    private suspend fun buildPlatformStats(total: Int) {
        val platforms = listOf(
            Triple(Platform.TWITTER,   getColor(R.color.twitter),   "𝕏 Twitter"),
            Triple(Platform.INSTAGRAM, getColor(R.color.instagram), "📷 Instagram"),
            Triple(Platform.TIKTOK,    getColor(R.color.tiktok),    "♪ TikTok"),
            Triple(Platform.YOUTUBE,   getColor(R.color.youtube),   "▶ YouTube"),
            Triple(Platform.OTHER,     getColor(R.color.primary),   "🔗 Diğer"),
        )

        binding.layoutPlatformStats.removeAllViews()

        for ((platform, color, label) in platforms) {
            val count = repository.getCountByPlatform(platform)
            if (count == 0) continue
            val pct = if (total > 0) ((count * 100f) / total).toInt() else 0

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.bottomMargin = dp(12) }
            }

            val labelRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.bottomMargin = dp(4) }
            }
            labelRow.addView(TextView(this).apply {
                text = label
                setTextColor(getColor(R.color.text_primary))
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            labelRow.addView(TextView(this).apply {
                text = "$count  ($pct%)"
                setTextColor(color)
                textSize = 13f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            })

            // Progress bar (fill + empty space)
            val track = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(8))
                background = android.graphics.drawable.GradientDrawable().also {
                    it.setColor(getColor(R.color.surface_variant)); it.cornerRadius = dp(4).toFloat()
                }
                clipToOutline = true
            }
            track.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, pct.toFloat())
                background = android.graphics.drawable.GradientDrawable().also {
                    it.setColor(color); it.cornerRadius = dp(4).toFloat()
                }
            })
            track.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, (100 - pct).toFloat())
            })

            row.addView(labelRow)
            row.addView(track)
            binding.layoutPlatformStats.addView(row)
        }
    }

    private suspend fun buildCategoryStats(total: Int) {
        val catCounts = repository.getCategoryCounts()
        binding.layoutCategoryStats.removeAllViews()

        if (catCounts.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "Henüz kategori verisi bulunmuyor."
                setTextColor(getColor(R.color.text_secondary))
                textSize = 12f
            }
            binding.layoutCategoryStats.addView(emptyTv)
            return
        }

        val allCategories = repository.getAllCategoriesSync()
        val catIconMap = allCategories.associate { it.name to it.icon }

        val colors = listOf(
            getColor(R.color.primary),
            getColor(R.color.secondary),
            getColor(R.color.twitter),
            getColor(R.color.instagram),
            0xFF8E24AA.toInt(),
            0xFF00897B.toInt(),
            0xFFFB8C00.toInt(),
            0xFF3949AB.toInt()
        )

        catCounts.forEachIndexed { index, item ->
            val catName = item.category?.ifEmpty { "Diğer" } ?: "Diğer"
            val count = item.count
            val pct = if (total > 0) ((count * 100f) / total).toInt() else 0
            val icon = catIconMap[catName] ?: "📁"
            val color = colors[index % colors.size]

            val row = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.bottomMargin = dp(12) }
            }

            val labelRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).also { it.bottomMargin = dp(4) }
            }
            labelRow.addView(TextView(this).apply {
                text = "$icon $catName"
                setTextColor(getColor(R.color.text_primary))
                textSize = 13f
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            labelRow.addView(TextView(this).apply {
                text = "$count  ($pct%)"
                setTextColor(color)
                textSize = 13f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
            })

            val track = LinearLayout(this).apply {
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(8))
                background = android.graphics.drawable.GradientDrawable().also {
                    it.setColor(getColor(R.color.surface_variant)); it.cornerRadius = dp(4).toFloat()
                }
                clipToOutline = true
            }
            track.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, pct.toFloat())
                background = android.graphics.drawable.GradientDrawable().also {
                    it.setColor(color); it.cornerRadius = dp(4).toFloat()
                }
            })
            track.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, (100 - pct).toFloat())
            })

            row.addView(labelRow)
            row.addView(track)
            binding.layoutCategoryStats.addView(row)
        }
    }

    private suspend fun buildWeeklyBars() {
        val sevenDaysAgo = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1000L
        val dayCounts = repository.getDailyCountsSince(sevenDaysAgo)
        val dayMap = dayCounts.associate { it.dayIndex to it.cnt }
        val maxCount = (dayMap.values.maxOrNull() ?: 1).coerceAtLeast(1)
        val todayDayIdx = System.currentTimeMillis() / 86400000L
        val dayLabels = listOf("Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz")

        binding.layoutWeeklyBars.removeAllViews()
        binding.layoutWeeklyLabels.removeAllViews()

        for (i in 6 downTo 0) {
            val dayIdx = todayDayIdx - i
            val count = dayMap[dayIdx] ?: 0
            val fraction = count.toFloat() / maxCount

            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            }
            if (count > 0) col.addView(TextView(this).apply {
                text = count.toString()
                setTextColor(getColor(R.color.primary))
                textSize = 9f; gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            })

            val barH = (90 * fraction).toInt().coerceAtLeast(if (count > 0) 6 else 2)
            val barColor = if (i == 0) getColor(R.color.primary) else getColor(R.color.surface_variant)
            col.addView(View(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp(22), dp(barH)).also { it.topMargin = dp(2) }
                background = android.graphics.drawable.GradientDrawable().also {
                    it.setColor(barColor); it.cornerRadius = dp(4).toFloat()
                }
            })
            binding.layoutWeeklyBars.addView(col)

            // Day label
            val calTmp = Calendar.getInstance().also { c -> c.timeInMillis = dayIdx * 86400000L }
            val dow = calTmp.get(Calendar.DAY_OF_WEEK) // 1=Sun
            val labelText = if (i == 0) "Bugün" else dayLabels[(dow - 2).let { if (it < 0) it + 7 else it }]

            binding.layoutWeeklyLabels.addView(TextView(this).apply {
                text = labelText
                setTextColor(if (i == 0) getColor(R.color.primary) else getColor(R.color.text_hint))
                textSize = 8f; gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
