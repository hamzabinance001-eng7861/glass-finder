package com.glassfinder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = Db(this)
        setContent {
            MaterialTheme {
                App(db)
            }
        }
    }
}

@Composable
fun App(db: Db) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var version by remember { mutableIntStateOf(0) }
    var openBox by rememberSaveable { mutableStateOf<Int?>(null) }

    BackHandler(enabled = openBox != null) { openBox = null }

    val tabs = listOf("🔍" to "Find", "📦" to "Boxes", "➕" to "Add box")

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, (icon, label) ->
                    NavigationBarItem(
                        selected = tab == i && openBox == null,
                        onClick = { tab = i; openBox = null },
                        icon = { Text(icon) },
                        label = { Text(label) }
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            val open = openBox
            if (open != null) {
                BoxDetailScreen(
                    db = db,
                    number = open,
                    onChanged = { version++ },
                    onClose = { openBox = null }
                )
            } else {
                when (tab) {
                    0 -> FindScreen(db, version) { openBox = it }
                    1 -> BoxesScreen(db, version, onOpen = { openBox = it }, onChanged = { version++ })
                    else -> AddScreen(db) { saved ->
                        version++
                        openBox = saved
                    }
                }
            }
        }
    }
}
