package com.example.pathy.data

import androidx.annotation.StringRes
import com.example.pathy.R

/** Sort orders for trail lists. The label is a string resource so it follows the app language. */
enum class SortOption(@StringRes val labelRes: Int) {
    DISTANCE(R.string.sort_distance),
    LENGTH_DESC(R.string.sort_length_desc),
    LENGTH_ASC(R.string.sort_length_asc),
    DIFFICULTY_ASC(R.string.sort_difficulty_asc),
    DIFFICULTY_DESC(R.string.sort_difficulty_desc),
    RECOMMENDED(R.string.sort_recommended),
    NAME_ASC(R.string.sort_name)
}