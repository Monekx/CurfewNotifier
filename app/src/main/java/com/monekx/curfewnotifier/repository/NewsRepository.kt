package com.monekx.curfewnotifier.repository

import android.util.Log
import com.monekx.curfewnotifier.data.RssFeed
import com.monekx.curfewnotifier.data.RssItem
import com.monekx.curfewnotifier.network.NewsApiService
import retrofit2.Retrofit
import retrofit2.converter.simplexml.SimpleXmlConverterFactory
import java.io.IOException

class NewsRepository {

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://objectiv.tv/") // Базовый URL для RSS
        .addConverterFactory(SimpleXmlConverterFactory.create())
        .build()

    private val apiService = retrofit.create(NewsApiService::class.java)

    // RSS-лента для Objectiv.TV (все новости)
    private val newsRssUrl = "https://www.objectiv.tv/uk/rss/"

    // Список ключевых слов для фильтрации новостей по комендантскому часу (если потребуется вновь)
    // private val keywords = listOf("комендантська година", "комендантська", "година", "комендантську")

    suspend fun getCurfewNews(): List<RssItem> {
        return try {
            Log.d("NewsRepository", "Загрузка всех новостей с URL: $newsRssUrl")
            val response = apiService.getRssFeed(newsRssUrl)
            if (response.isSuccessful) {
                val allItems = response.body()?.channel?.items ?: emptyList()
                Log.d("NewsRepository", "Загружено ${allItems.size} новостей с Objectiv.TV.")
                // Если вы хотите фильтровать новости, раскомментируйте блок ниже
                // return allItems.filter { item ->
                //     item.title?.let { title ->
                //         keywords.any { keyword ->
                //             title.contains(keyword, ignoreCase = true)
                //         }
                //     } ?: false
                // }.also { filteredItems ->
                //     Log.d("NewsRepository", "Отфильтровано ${filteredItems.size} новостей по ключевым словам.")
                // }
                return allItems // Возвращаем все новости без фильтрации
            } else {
                Log.e("NewsRepository", "Ошибка при загрузке новостей: ${response.code()} - ${response.message()}")
                emptyList()
            }
        } catch (e: IOException) {
            Log.e("NewsRepository", "Ошибка сети при загрузке новостей: ${e.message}", e)
            emptyList()
        } catch (e: Exception) {
            Log.e("NewsRepository", "Неизвестная ошибка при загрузке новостей: ${e.message}", e)
            emptyList()
        }
    }
}