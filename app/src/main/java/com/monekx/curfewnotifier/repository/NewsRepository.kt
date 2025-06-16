package com.monekx.curfewnotifier.repository

import android.util.Log
import com.monekx.curfewnotifier.data.RssFeed
import com.monekx.curfewnotifier.data.RssItem
import com.monekx.curfewnotifier.network.NewsApiService
import org.jsoup.Jsoup // Импортируем Jsoup
import retrofit2.Retrofit
import retrofit2.converter.simplexml.SimpleXmlConverterFactory
import java.io.IOException
import kotlinx.coroutines.Dispatchers // Для работы с корутинами
import kotlinx.coroutines.withContext // Для переключения контекста

class NewsRepository {

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://objectiv.tv/") // Базовый URL для RSS
        .addConverterFactory(SimpleXmlConverterFactory.create())
        .build()

    private val apiService = retrofit.create(NewsApiService::class.java)

    private val newsRssUrl = "https://www.objectiv.tv/uk/rss/"

    suspend fun getCurfewNews(): List<RssItem> {
        return withContext(Dispatchers.IO) { // Выполняем сетевые операции в IO-диспетчере
            try {
                Log.d("NewsRepository", "Загрузка всех новостей с URL: $newsRssUrl")
                val response = apiService.getRssFeed(newsRssUrl)
                if (response.isSuccessful) {
                    val allItems = response.body()?.channel?.items ?: emptyList()
                    Log.d("NewsRepository", "Загружено ${allItems.size} новостей с Objectiv.TV.")

                    val itemsWithImages = allItems.map { rssItem ->
                        // Для каждой новости пытаемся спарсить URL изображения с её страницы
                        val imageUrl = parseImageUrlFromHtml(rssItem.link)
                        rssItem.copy(imageUrl = imageUrl) // Создаем копию RssItem с добавленным imageUrl
                    }
                    Log.d("NewsRepository", "Завершено парсинг изображений для ${itemsWithImages.size} новостей.")
                    return@withContext itemsWithImages
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

    // Вспомогательная функция для парсинга URL изображения из HTML
    private suspend fun parseImageUrlFromHtml(newsUrl: String?): String? {
        return withContext(Dispatchers.IO) { // Убеждаемся, что парсинг HTML происходит в IO-диспетчере
            if (newsUrl.isNullOrBlank()) {
                return@withContext null
            }
            try {
                // Подключаемся к странице и парсим её
                val document = Jsoup.connect(newsUrl).get()
                // Ищем элемент <img> внутри div с классом "l-slider"
                // Как видно на скриншоте, структура такая: <div class="l-slider">...<img ...>...</div>
                val imgElement = document.select("div.l-slider img.b-slider_img").firstOrNull()
                // Извлекаем атрибут src
                val imageUrl = imgElement?.attr("src")
                Log.d("NewsRepository", "Парсинг изображения для $newsUrl: $imageUrl")
                return@withContext imageUrl
            } catch (e: Exception) {
                Log.e("NewsRepository", "Ошибка при парсинге изображения со страницы $newsUrl: ${e.message}")
                return@withContext null
            }
        }
    }
}