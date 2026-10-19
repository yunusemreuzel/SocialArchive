package com.socialarchive.ui.common

import android.content.Context
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.socialarchive.R
import com.socialarchive.data.model.Category
import com.socialarchive.databinding.DialogAddCategoryBinding

object CategoryDialogHelper {

    private val POPULAR_EMOJIS = listOf(
        "📁", "🎮", "💼", "💡", "🏷️", "📌", "🚀", "📚", "🎬", 
        "🌟", "🎯", "🎨", "📝", "🛒", "💰", "🎧", "🚗", "🍔", 
        "⚽", "🌿", "💻", "🔥", "💎", "✈️"
    )

    fun showAddCategoryDialog(
        context: Context,
        onCategoryAdded: (Category) -> Unit
    ) {
        val dialog = BottomSheetDialog(context)
        val binding = DialogAddCategoryBinding.inflate(LayoutInflater.from(context))
        dialog.setContentView(binding.root)

        var selectedEmoji = "📁"
        binding.tvSelectedEmoji.text = selectedEmoji

        // Emoji chip'lerini doldur
        POPULAR_EMOJIS.forEach { emoji ->
            val tvEmoji = TextView(context).apply {
                text = emoji
                textSize = 20f
                setPadding(24, 16, 24, 16)
                setBackgroundResource(
                    if (emoji == selectedEmoji) R.drawable.bg_chip_selected
                    else R.drawable.bg_chip_unselected
                )
                val params = android.widget.LinearLayout.LayoutParams(
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT,
                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = 16
                }
                layoutParams = params
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    selectedEmoji = emoji
                    binding.tvSelectedEmoji.text = emoji

                    // Seçim stillerini güncelle
                    for (i in 0 until binding.layoutEmojiContainer.childCount) {
                        val child = binding.layoutEmojiContainer.getChildAt(i) as? TextView
                        if (child != null) {
                            if (child.text == emoji) {
                                child.setBackgroundResource(R.drawable.bg_chip_selected)
                            } else {
                                child.setBackgroundResource(R.drawable.bg_chip_unselected)
                            }
                        }
                    }
                }
            }
            binding.layoutEmojiContainer.addView(tvEmoji)
        }

        binding.btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        binding.btnSave.setOnClickListener {
            val name = binding.etCategoryName.text.toString().trim()
            if (name.isEmpty()) {
                binding.etCategoryName.error = "Lütfen bir kategori adı girin"
                return@setOnClickListener
            }

            val newCategory = Category(
                name = name,
                icon = selectedEmoji,
                isCustom = true
            )
            onCategoryAdded(newCategory)
            dialog.dismiss()
        }

        dialog.show()
    }
}
