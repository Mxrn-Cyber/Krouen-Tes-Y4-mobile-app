package com.kroeungtes.app.ui.fragments

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.kroeungtes.app.R
import com.kroeungtes.app.adapters.RecipeCardAdapter
import com.kroeungtes.app.data.model.RecipeCardItem
import com.kroeungtes.app.databinding.FragmentSearchBinding
import com.kroeungtes.app.ui.activities.MealDetailesActivity
import com.kroeungtes.app.ui.common.Motion
import com.kroeungtes.app.ui.common.fadeVisible
import com.kroeungtes.app.ui.fragments.HomeFragment.Companion.MEAL_ID
import com.kroeungtes.app.ui.fragments.HomeFragment.Companion.MEAL_STR
import com.kroeungtes.app.ui.fragments.HomeFragment.Companion.MEAL_THUMB
import com.kroeungtes.app.viewmodel.DetailsMVVM
import com.kroeungtes.app.viewmodel.SearchMVVM

/** Search as you type: results refresh 400 ms after the user stops typing. */
class SearchFragment : Fragment() {

    private var _binding: FragmentSearchBinding? = null
    private val binding get() = _binding!!
    private lateinit var searchMvvm: SearchMVVM
    private lateinit var detailsMvvm: DetailsMVVM
    private lateinit var resultsAdapter: RecipeCardAdapter

    private val handler = Handler(Looper.getMainLooper())
    private var pendingSearch: Runnable? = null
    private var lastQuery = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        searchMvvm = ViewModelProviders.of(this)[SearchMVVM::class.java]
        detailsMvvm = ViewModelProviders.of(this)[DetailsMVVM::class.java]
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSearchBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        resultsAdapter = RecipeCardAdapter(
            onClick = { openRecipe(it) },
            onSaveToggle = { item, saved ->
                if (saved) detailsMvvm.quickSave(item.id, item.name, item.image)
                else detailsMvvm.deleteMealById(item.id)
            }
        )
        binding.rvSearch.layoutManager =
            GridLayoutManager(requireContext(), resources.getInteger(R.integer.wishlist_columns))
        binding.rvSearch.adapter = resultsAdapter

        binding.btnSearchBack.setOnClickListener { findNavController().navigateUp() }
        binding.btnClear.setOnClickListener { binding.edSearch.setText("") }
        binding.edSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                val query = s?.toString().orEmpty()
                binding.btnClear.visibility = if (query.isEmpty()) View.GONE else View.VISIBLE
                scheduleSearch(query)
            }
        })
        binding.edSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                scheduleSearch(binding.edSearch.text.toString(), delayMs = 0)
                hideKeyboard()
                true
            } else false
        }

        searchMvvm.observeLoading().observe(viewLifecycleOwner) { loading ->
            binding.searchProgress.visibility = if (loading == true) View.VISIBLE else View.INVISIBLE
        }
        searchMvvm.observeResults().observe(viewLifecycleOwner) { results ->
            val list = results.orEmpty()
            resultsAdapter.submit(list.map { RecipeCardItem.from(it) })
            binding.rvSearch.scheduleLayoutAnimation()
            binding.tvSearchState.text = if (lastQuery.isBlank()) getString(R.string.search_start)
            else getString(R.string.no_results, lastQuery)
            binding.tvSearchState.fadeVisible(list.isEmpty())
        }
        detailsMvvm.observeSaveMeal().observe(viewLifecycleOwner) { saved ->
            resultsAdapter.setSavedIds(saved.orEmpty().map { it.mealId.toString() }.toSet())
        }

        // Open the keyboard straight away
        binding.edSearch.requestFocus()
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val field = binding.edSearch
        field.post { imm.showSoftInput(field, InputMethodManager.SHOW_IMPLICIT) }
    }

    private fun scheduleSearch(query: String, delayMs: Long = 400) {
        pendingSearch?.let { handler.removeCallbacks(it) }
        val task = Runnable {
            lastQuery = query.trim()
            searchMvvm.search(lastQuery)
        }
        pendingSearch = task
        handler.postDelayed(task, delayMs)
    }

    private fun openRecipe(item: RecipeCardItem) {
        hideKeyboard()
        Motion.open(requireActivity(), Intent(requireContext(), MealDetailesActivity::class.java).apply {
            putExtra(MEAL_ID, item.id)
            putExtra(MEAL_STR, item.name)
            putExtra(MEAL_THUMB, item.image.orEmpty())
        })
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.edSearch.windowToken, 0)
    }

    override fun onDestroyView() {
        pendingSearch?.let { handler.removeCallbacks(it) }
        super.onDestroyView()
        _binding = null
    }
}
