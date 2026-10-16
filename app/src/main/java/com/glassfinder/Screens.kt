package com.glassfinder

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------- Find

@Composable
fun FindScreen(db: Db, version: Int, onOpen: (Int) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val hits = remember(query, version) { db.search(query) }
    val grouped = remember(hits) {
        hits.groupBy { it.box }
            .toList()
            .sortedWith(compareBy({ pair -> pair.second.minOf { it.rank } }, { pair -> pair.first }))
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Find tempered glass", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Phone model (e.g. A10, Note 10)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))

        when {
            query.isBlank() ->
                Text("Type a model name and I will tell you which box to open.")
            grouped.isEmpty() ->
                Text("No box lists \"$query\". Check the spelling, or add the box in the Add box tab.")
            else -> LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(grouped, key = { it.first }) { pair ->
                    val box = pair.first
                    val list = pair.second
                    Card(Modifier.fillMaxWidth().clickable { onOpen(box) }) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("BOX", fontSize = 12.sp)
                                Text("$box", fontSize = 44.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(Modifier.width(20.dp))
                            Column {
                                list.take(4).forEach { Text(it.model) }
                                if (list.size > 4) Text("+${list.size - 4} more")
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Boxes list

@Composable
fun BoxesScreen(db: Db, version: Int, onOpen: (Int) -> Unit, onChanged: () -> Unit) {
    val boxes = remember(version) { db.allBoxes() }
    var toDelete by remember { mutableStateOf<BoxInfo?>(null) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("All boxes (${boxes.size})", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        if (boxes.isEmpty()) {
            Text("No boxes yet. Use the Add box tab to photograph your first one.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(boxes, key = { it.number }) { b ->
                    val thumb = remember(b.imagePath) { loadBitmap(b.imagePath, 200) }
                    Card(Modifier.fillMaxWidth().clickable { onOpen(b.number) }) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (thumb != null) {
                                Image(
                                    bitmap = thumb.asImageBitmap(),
                                    contentDescription = null,
                                    modifier = Modifier.size(72.dp),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Spacer(Modifier.size(72.dp))
                            }
                            Spacer(Modifier.width(16.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Box ${b.number}", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                                Text("${b.modelCount} models")
                            }
                            TextButton(onClick = { toDelete = b }) { Text("Delete") }
                        }
                    }
                }
            }
        }
    }

    val target = toDelete
    if (target != null) {
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Delete box ${target.number}?") },
            text = { Text("The photo and its model list will be removed from this phone.") },
            confirmButton = {
                TextButton(onClick = {
                    File(target.imagePath).delete()
                    db.deleteBox(target.number)
                    toDelete = null
                    onChanged()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } }
        )
    }
}

// ---------------------------------------------------------------- Box detail

@Composable
fun BoxDetailScreen(db: Db, number: Int, onChanged: () -> Unit, onClose: () -> Unit) {
    var refresh by remember { mutableStateOf(0) }
    val models = remember(number, refresh) { db.modelsOf(number) }
    val imagePath = remember(number, refresh) { db.imageOf(number) }
    val bitmap = remember(imagePath) { imagePath?.let { loadBitmap(it, 1600) } }
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose) { Text("← Back") }
        }
        Text("Take it from BOX $number", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = { editing = true }) { Text("Edit models") }
            OutlinedButton(onClick = { confirmDelete = true }) { Text("Delete box") }
        }
        Spacer(Modifier.height(12.dp))

        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Photo of box $number",
                modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp),
                contentScale = ContentScale.Fit
            )
            Spacer(Modifier.height(12.dp))
        }

        Text("Models in this box (${models.size})", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        models.forEach { Text("• $it") }
    }

    if (editing) {
        var draft by remember { mutableStateOf(models.joinToString("\n")) }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("Edit models (one per line)") },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 400.dp)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    db.setModels(number, parseModels(draft))
                    editing = false
                    refresh++
                    onChanged()
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("Cancel") } }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete box $number?") },
            text = { Text("The photo and its model list will be removed from this phone.") },
            confirmButton = {
                TextButton(onClick = {
                    imagePath?.let { File(it).delete() }
                    db.deleteBox(number)
                    confirmDelete = false
                    onChanged()
                    onClose()
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
        )
    }
}

// ---------------------------------------------------------------- Add box

@Composable
fun AddScreen(db: Db, onSaved: (Int) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()

    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    var text by rememberSaveable { mutableStateOf("") }
    var number by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    LaunchedEffect(Unit) {
        if (number.isEmpty()) number = db.nextBoxNumber().toString()
    }

    fun process(uri: Uri) {
        busy = true
        scope.launch {
            pending?.let { File(it).delete() }
            val file = File(ctx.cacheDir, "pending_${System.currentTimeMillis()}.jpg")
            val ok = withContext(Dispatchers.IO) { importImage(ctx, uri, file) }
            if (!ok) {
                busy = false
                Toast.makeText(ctx, "Could not read that image", Toast.LENGTH_SHORT).show()
                return@launch
            }
            pending = file.absolutePath
            val raw = withContext(Dispatchers.Default) { recognizeText(file) }
            text = parseModels(raw).joinToString("\n")
            busy = false
            if (text.isBlank()) {
                Toast.makeText(ctx, "No text found. Type the models yourself.", Toast.LENGTH_LONG).show()
            }
        }
    }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) process(uri)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = cameraUri
        if (ok && u != null) process(u)
    }

    val n = number.toIntOrNull()
    val exists = n != null && db.boxExists(n)
    val preview = remember(pending) { pending?.let { loadBitmap(it, 1200) } }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
    ) {
        Text("Add a box", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(enabled = !busy, onClick = {
                val f = File(ctx.cacheDir, "camera.jpg")
                val u = FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", f)
                cameraUri = u
                camera.launch(u)
            }) { Text("Take photo") }
            OutlinedButton(enabled = !busy, onClick = { gallery.launch("image/*") }) {
                Text("Pick from gallery")
            }
        }

        if (busy) {
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(24.dp))
                Spacer(Modifier.width(12.dp))
                Text("Reading models from the photo…")
            }
        }

        if (preview != null) {
            Spacer(Modifier.height(16.dp))
            Image(
                bitmap = preview.asImageBitmap(),
                contentDescription = "Photo to save",
                modifier = Modifier.fillMaxWidth().heightIn(max = 240.dp),
                contentScale = ContentScale.Fit
            )

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = number,
                onValueChange = { number = it.filter { c -> c.isDigit() }.take(5) },
                label = { Text("Box number (the number written on the box)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            if (exists) {
                Text(
                    "Box $number already exists. Saving will replace its photo and models.",
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Models found, one per line. Fix any mistakes.") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp)
            )

            Spacer(Modifier.height(16.dp))
            Button(
                enabled = !busy && n != null && n > 0,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    val src = pending
                    val num = n
                    if (src != null && num != null) {
                        val dest = File(File(ctx.filesDir, "boxes"), "box_$num.jpg")
                        dest.parentFile?.mkdirs()
                        File(src).copyTo(dest, overwrite = true)
                        File(src).delete()
                        db.saveBox(num, dest.absolutePath, parseModels(text))
                        pending = null
                        text = ""
                        number = db.nextBoxNumber().toString()
                        Toast.makeText(ctx, "Saved box $num", Toast.LENGTH_SHORT).show()
                        onSaved(num)
                    }
                }
            ) { Text("Save box") }
        } else if (!busy) {
            Spacer(Modifier.height(16.dp))
            Text(
                "1. Photograph the box so the list of phone models is readable.\n" +
                    "2. Check the models the app read.\n" +
                    "3. Enter the number you wrote on the box and save."
            )
        }
    }
}
