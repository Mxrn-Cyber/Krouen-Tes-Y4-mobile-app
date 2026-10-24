package com.kroeungtes.app.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.kroeungtes.app.data.model.MealDetail
import com.kroeungtes.app.data.model.RandomMealResponse
import com.kroeungtes.app.data.remote.RetrofitInstance
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

/** Live search: every query returns the full list of matches (empty list = nothing found). */
class SearchMVVM : ViewModel() {
    private val results = MutableLiveData<List<MealDetail>>()
    private val loading = MutableLiveData(false)
    private var lastCall: Call<RandomMealResponse>? = null

    fun search(query: String) {
        lastCall?.cancel()
        if (query.isBlank()) {
            results.value = emptyList()
            loading.value = false
            return
        }
        loading.value = true
        lastCall = RetrofitInstance.foodApi.getMealByName(query.trim()).also { call ->
            call.enqueue(object : Callback<RandomMealResponse> {
                override fun onResponse(call: Call<RandomMealResponse>, response: Response<RandomMealResponse>) {
                    results.value = response.body()?.meals.orEmpty()
                    loading.value = false
                }

                override fun onFailure(call: Call<RandomMealResponse>, t: Throwable) {
                    if (call.isCanceled) return
                    Log.e(TAG, t.message.toString())
                    results.value = emptyList()
                    loading.value = false
                }
            })
        }
    }

    fun observeResults(): LiveData<List<MealDetail>> = results
    fun observeLoading(): LiveData<Boolean> = loading
}
