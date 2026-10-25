package com.kroeungtes.app.data.local

import androidx.lifecycle.LiveData
import androidx.room.*
import androidx.room.Dao
import com.kroeungtes.app.data.model.MealDB

@Dao
interface Dao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertFavorite(meal: MealDB)

    @Update
    fun updateFavorite(meal:MealDB)

    @Query("SELECT * FROM meal_information order by mealId asc")
    fun getAllSavedMeals():LiveData<List<MealDB>>

    @Query("SELECT * FROM meal_information WHERE mealId =:id")
    fun getMealById(id:String):MealDB?

    @Query("DELETE FROM meal_information WHERE mealId =:id")
    fun deleteMealById(id:String)

    @Delete
    fun deleteMeal(meal:MealDB)

    /** Rows saved from the old TheMealDB lists (positive ids); dashboard recipes are negative. */
    @Query("DELETE FROM meal_information WHERE mealId >= 0")
    fun deleteWorldDishes()




}