package com.ye.webview

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class MainActivity : ComponentActivity() {
    private lateinit var store: Store
    private lateinit var browser: Browser

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        store = Store(this)
        Thread { AdBlocker.init(applicationContext) }.start()
        browser = Browser(this, store)
        browser.newTab(intent?.dataString)
        val shot = intent?.getStringExtra("shot")
        setContent {
            YeTheme {
                BrowserApp(browser, shot,
                    onSettings = { startActivity(Intent(this, SettingsActivity::class.java)) },
                    onFinish = { finish() })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.dataString?.let { browser.newTab(it) }
    }

    override fun onResume() {
        super.onResume()
        browser.adBlock = store.adBlock
        browser.shortcuts = store.shortcuts()
    }

    override fun onDestroy() {
        browser.destroyAll()
        super.onDestroy()
    }
}
