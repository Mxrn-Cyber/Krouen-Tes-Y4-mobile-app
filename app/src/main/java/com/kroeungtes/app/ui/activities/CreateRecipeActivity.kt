package com.kroeungtes.app.ui.activities

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.NumberPicker
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.kroeungtes.app.R
import com.kroeungtes.app.data.local.MyRecipeStore
import com.kroeungtes.app.data.model.Ingredient
import com.kroeungtes.app.data.model.MyRecipe
import com.kroeungtes.app.databinding.ActivityCreateRecipeBinding
import com.kroeungtes.app.databinding.ItemIngredientInputBinding
import com.kroeungtes.app.ui.common.Motion
import com.kroeungtes.app.ui.common.pop
import com.kroeungtes.app.ui.common.pressScale
import com.kroeungtes.app.ui.common.riseIn

/**
 * Create recipe (slide screen): photo, name, Serves, Cook time, ingredient rows, steps.
 * Saved on the phone with [MyRecipeStore]; shows up on My Profile.
 */
class CreateRecipeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCreateRecipeBinding
    private val rows = mutableListOf<ItemIngredientInputBinding>()
    private var pickedPhoto: Uri? = null
    private var serves = 1
    private var cookMinutes = 45

    private val pickPhoto = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) showPhoto(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCreateRecipeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnClose.setOnClickListener { finish() }
        binding.photoBox.setOnClickListener { pickPhoto.launch("image/*") }
        binding.btnEditPhoto.setOnClickListener { pickPhoto.launch("image/*") }
        binding.rowServes.pressScale(0.98f)
        binding.rowCookTime.pressScale(0.98f)
        binding.rowServes.setOnClickListener {
            pickNumber(R.string.serves, 1, 20, 1, serves) { serves = it; updateValues(); binding.tvServesValue.pop() }
        }
        binding.rowCookTime.setOnClickListener {
            pickNumber(R.string.cook_time, 5, 240, 5, cookMinutes) { cookMinutes = it; updateValues(); binding.tvCookValue.pop() }
        }
        binding.btnAddIngredient.setOnClickListener { addRow(focus = true) }
        binding.btnSubmit.setOnClickListener { submit() }

        updateValues()
        // Start with two empty rows, like the slide
        addRow(focus = false)
        addRow(focus = false)
    }

    private fun showPhoto(uri: Uri) {
        pickedPhoto = uri
        binding.imgCover.visibility = View.VISIBLE
        binding.tvAddPhoto.visibility = View.GONE
        Glide.with(this).load(uri).into(binding.imgCover)
        binding.imgCover.alpha = 0f
        binding.imgCover.animate().alpha(1f).setDuration(250).start()
    }

    private fun updateValues() {
        binding.tvServesValue.text = String.format("%02d", serves)
        binding.tvCookValue.text = getString(R.string.cook_time_value, cookMinutes)
    }

    private fun addRow(focus: Boolean) {
        val row = ItemIngredientInputBinding.inflate(LayoutInflater.from(this), binding.ingredientRows, false)
        row.btnRemoveIngredient.setOnClickListener {
            if (rows.size <= 1) {
                row.edIngredientName.setText("")
                row.edIngredientQty.setText("")
            } else {
                rows.remove(row)
                binding.ingredientRows.removeView(row.root)
            }
        }
        rows.add(row)
        binding.ingredientRows.addView(row.root)
        if (focus) row.root.riseIn(distanceDp = 12f, duration = 280)
        if (focus) row.edIngredientName.requestFocus()
    }

    /** Small dialog with a number wheel (used for Serves and Cook time). */
    private fun pickNumber(title: Int, min: Int, max: Int, step: Int, current: Int, onPicked: (Int) -> Unit) {
        val values = (min..max step step).toList()
        val picker = NumberPicker(this).apply {
            minValue = 0
            maxValue = values.size - 1
            displayedValues = values.map { it.toString() }.toTypedArray()
            value = values.indexOf(current).coerceAtLeast(0)
            wrapSelectorWheel = false
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(picker)
            .setPositiveButton(R.string.pick) { _, _ -> onPicked(values[picker.value]) }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun submit() {
        val title = binding.edTitle.text?.toString()?.trim().orEmpty()
        if (title.isEmpty()) {
            binding.edTitle.error = getString(R.string.need_title)
            binding.edTitle.requestFocus()
            return
        }
        val ingredients = rows.mapNotNull { r ->
            val name = r.edIngredientName.text?.toString()?.trim().orEmpty()
            if (name.isEmpty()) null else Ingredient(name, r.edIngredientQty.text?.toString()?.trim().orEmpty())
        }
        if (ingredients.isEmpty()) {
            Snackbar.make(binding.root, R.string.need_ingredient, Snackbar.LENGTH_SHORT).show()
            rows.firstOrNull()?.root?.pop()
            return
        }
        val id = MyRecipeStore.newId()
        val photoPath = pickedPhoto?.let { MyRecipeStore.copyPhoto(this, it, id) }
        val steps = binding.edSteps.text?.toString().orEmpty()
            .lines().map { it.trim() }.filter { it.isNotEmpty() }
        val video = binding.edVideo.text?.toString()?.trim().orEmpty().ifBlank { null }

        MyRecipeStore.add(this, MyRecipe(id, title, photoPath, serves, cookMinutes, ingredients, steps, video,
            System.currentTimeMillis()))
        Toast.makeText(this, R.string.recipe_created, Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun finish() {
        super.finish()
        Motion.closeToBottom(this)
    }
}
