package com.monekx.curfewnotifier

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler // Для обработки кнопки "Назад" в Compose
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.monekx.curfewnotifier.ui.theme.CurfewNotifierTheme

class WebViewActivity : ComponentActivity() {

    companion object {
        const val EXTRA_URL = "extra_url"
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @SuppressLint("SetJavaScriptEnabled") // Разрешено для WebView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra(EXTRA_URL)

        if (url.isNullOrBlank()) {
            Toast.makeText(this, "Ошибка: URL для загрузки не найден.", Toast.LENGTH_SHORT).show()
            finish() // Закрыть Activity, если URL не передан
            return
        }
        Thread.sleep(1000)
        setContent {
            CurfewNotifierTheme {
                // Состояние для WebView, чтобы управлять навигацией "Назад"
                var webView: WebView? by remember { mutableStateOf(null) }
                var isLoading by remember { mutableStateOf(true) }
                var pageTitle by remember { mutableStateOf("Загрузка...") }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(pageTitle) }, // Динамический заголовок
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                titleContentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        )
                    }
                ) { paddingValues ->
                    Box(modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)) {
                        AndroidView(
                            factory = { context ->
                                WebView(context).apply {
                                    // Сохраняем ссылку на WebView
                                    webView = this

                                    // Настройки WebView
                                    settings.javaScriptEnabled = true
                                    settings.loadWithOverviewMode = true
                                    settings.useWideViewPort = true
                                    settings.domStorageEnabled = true
                                    settings.builtInZoomControls = true // Включить зум
                                    settings.displayZoomControls = false // Скрыть контролы зума

                                    webViewClient = object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                            val newUrl = request?.url?.toString()
                                            // Логика для открытия в новом WebView:
                                            // Если URL не совпадает с исходным доменом, можно открыть во внешнем браузере
                                            // Или, если это сторонний ресурс, всегда открывать во внешнем браузере.
                                            // Для простоты: всегда загружаем в текущем WebView
                                            if (newUrl != null) {
                                                view?.loadUrl(newUrl)
                                                return true // Обработка URL в текущем WebView
                                            }
                                            return false
                                        }

                                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                            super.onPageStarted(view, url, favicon)
                                            isLoading = true
                                            pageTitle = "Загрузка..."
                                        }

                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            super.onPageFinished(view, url)
                                            isLoading = false
                                            pageTitle = view?.title ?: "Новости" // Обновляем заголовок
                                        }

                                        override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                                            super.onReceivedError(view, request, error)
                                            isLoading = false
                                            pageTitle = "Ошибка загрузки"
                                            val errorMessage = "Ошибка: ${error?.description} (${error?.errorCode})"
                                            Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                                        }
                                    }
                                    loadUrl(url)
                                }
                            },
                            update = {
                                // Если URL изменяется, его можно обновить здесь
                                // Но в данном случае URL передается один раз через Intent
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Индикатор загрузки
                        if (isLoading) {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        }
                    }
                }

                // --- ОБРАБОТКА КНОПКИ "НАЗАД" ---
                BackHandler(enabled = webView?.canGoBack() == true) { // Активируем BackHandler, если WebView может вернуться назад
                    webView?.goBack()
                }
                // --- КОНЕЦ ОБРАБОТКИ "НАЗАД" ---
            }
        }
    }
}