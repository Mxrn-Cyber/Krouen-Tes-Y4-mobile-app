package com.kroeungtes.app.ui.common

import com.bumptech.glide.load.model.GlideUrl
import com.bumptech.glide.load.model.LazyHeaders

/** What to pass to Glide.load() for a recipe photo. */
object Images {
    // Wikimedia asks apps to send a clear User-Agent when loading their photos
    private const val USER_AGENT = "KrouenTes/1.0 (Android; Khmer recipe app)"

    fun model(url: String?): Any? {
        if (url.isNullOrBlank()) return null
        return if (url.contains("wikimedia.org")) {
            GlideUrl(url, LazyHeaders.Builder().addHeader("User-Agent", USER_AGENT).build())
        } else url
    }
}
