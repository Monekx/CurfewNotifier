package com.monekx.curfewnotifier.ui

import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image // Для отображения изображений
import androidx.compose.foundation.background // Для заглушки фона
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource // Если будете использовать заглушку из ресурсов
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import coil.compose.rememberAsyncImagePainter
import com.google.accompanist.swiperefresh.SwipeRefresh // Импорт для SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState // Импорт для rememberSwipeRefreshState
import com.monekx.curfewnotifier.WebViewActivity
import com.monekx.curfewnotifier.data.RssItem
import com.monekx.curfewnotifier.repository.NewsRepository
import kotlinx.coroutines.launch
// import coil.compose.rememberAsyncImagePainter // Для загрузки изображений из сети (если будете парсить картинки)

@Composable
fun NewsScreen(context: Context) {
    val newsItems = remember { mutableStateListOf<RssItem>() }
    val newsRepository = remember { NewsRepository() }
    var isLoadingNews by remember { mutableStateOf(false) } // Это будет использоваться и для PullRefresh
    var newsError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val swipeRefreshState = rememberSwipeRefreshState(isRefreshing = isLoadingNews) // Состояние для SwipeRefresh

    val fetchNews: suspend () -> Unit = {
        isLoadingNews = true
        newsError = null
        val loadedNews = newsRepository.getCurfewNews()
        newsItems.clear()
        if (loadedNews.isNotEmpty()) {
            newsItems.addAll(loadedNews)
        } else {
            newsError = "Не удалось загрузить новости. Проверьте подключение к Интернету или источник новостей."
            Toast.makeText(context, newsError, Toast.LENGTH_SHORT).show()
        }
        isLoadingNews = false
    }

    LaunchedEffect(Unit) {
        fetchNews()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp), // Горизонтальные отступы
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp)) // Отступ сверху

        Text(
            text = "Последние новости",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.fillMaxWidth(), // Заголовок по всей ширине
            textAlign = TextAlign.Center // Центрируем заголовок
        )
        Spacer(modifier = Modifier.height(16.dp)) // Отступ после заголовка

        SwipeRefresh(
            state = swipeRefreshState,
            onRefresh = { scope.launch { fetchNews() } }, // Действие при pull-to-refresh
            modifier = Modifier.fillMaxSize()
        ) {
            when {
                isLoadingNews && newsItems.isEmpty() -> CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally)) // Индикатор только при первой загрузке
                newsError != null -> Text(
                    newsError!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp),
                    textAlign = TextAlign.Center
                )
                newsItems.isEmpty() -> Text(
                    "Нет новостей для отображения.\nПотяните вниз, чтобы обновить.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp),
                    textAlign = TextAlign.Center
                )
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp), // Отступ сверху/снизу для списка
                        verticalArrangement = Arrangement.spacedBy(12.dp) // Отступ между элементами
                    ) {
                        items(newsItems) { news ->
                            NewsItem(news = news, context = context)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NewsItem(news: RssItem, context: Context) {
    Card(
        modifier = Modifier
            .fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .clickable {
                    news.link?.let { url ->
                        Log.d("NewsItem", "Клик по новости. Открытие в WebView: $url")
                        try {
                            if (url.isNotBlank()) {
                                val intent = Intent(context, WebViewActivity::class.java).apply {
                                    putExtra(WebViewActivity.EXTRA_URL, url)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                ContextCompat.startActivity(context, intent, null)
                            } else {
                                Log.e("NewsItem", "Некорректный или пустой URL новости: '$url'")
                                Toast.makeText(context, "Некорректная ссылка на новость.", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Log.e("NewsItem", "Ошибка при открытии ссылки в WebView: $url", e)
                            Toast.makeText(context, "Не удалось открыть новость.", Toast.LENGTH_SHORT).show()
                        }
                    } ?: Log.e("NewsItem", "Ссылка новости равна null.")
                }
        ) {
            // --- Место для изображения новости (теперь с реальной загрузкой Coil) ---
            if (!news.imageUrl.isNullOrBlank()) {
                Image(
                    painter = rememberAsyncImagePainter(news.imageUrl),
                    contentDescription = null, // Можно добавить описание, если оно есть в данных
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp) // Высота изображения
                        .clip(MaterialTheme.shapes.small), // Скругленные углы для изображения
                    contentScale = ContentScale.Crop // Обрезаем изображение, чтобы оно заполняло область
                )
                Spacer(modifier = Modifier.height(12.dp))
            } else {
                // Заглушка, если изображения нет
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant), // Фон для заглушки
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Нет изображения",
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
            // --- Конец места для изображения ---


            Text(
                text = news.title ?: "Без заголовка",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            news.description?.let { desc ->
                val cleanedDescription = desc.replace(Regex("<.*?>"), "")
                Text(
                    text = cleanedDescription.take(200) + if (cleanedDescription.length > 200) "..." else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            news.pubDate?.let { date ->
                Text(
                    text = date,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.End)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            news.link?.let { url ->
                val annotatedText = buildAnnotatedString {
                    append("Читать далее")
                    addStyle(
                        style = SpanStyle(
                            color = MaterialTheme.colorScheme.tertiary,
                            textDecoration = TextDecoration.Underline
                        ),
                        start = 0,
                        end = length
                    )
                }
                ClickableText(
                    text = annotatedText,
                    onClick = {
                        Log.d("NewsItem", "Клик по 'Читать далее'. Открытие в WebView: $url")
                        try {
                            if (url.isNotBlank()) {
                                val intent = Intent(context, WebViewActivity::class.java).apply {
                                    putExtra(WebViewActivity.EXTRA_URL, url)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                ContextCompat.startActivity(context, intent, null)
                            } else {
                                Log.e("NewsItem", "Некорректный или пустой URL для 'Читать далее': '$url'")
                                Toast.makeText(context, "Некорректная ссылка на новость.", Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Log.e("NewsItem", "Ошибка при открытии ссылки 'Читать далее' в WebView: $url", e)
                            Toast.makeText(context, "Не удалось открыть новость.", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.align(Alignment.End),
                    style = MaterialTheme.typography.labelLarge.copy(color = MaterialTheme.colorScheme.tertiary)
                )
            }
        }
    }
}