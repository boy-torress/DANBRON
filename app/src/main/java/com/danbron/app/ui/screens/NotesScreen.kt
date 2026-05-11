package com.danbron.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.danbron.app.data.models.Note
import com.danbron.app.ui.components.StaggeredEntrance
import com.danbron.app.ui.theme.*
import com.danbron.app.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun NotesScreen(vm: MainViewModel) {
    val notes by vm.notes.collectAsState()
    var showEditor by remember { mutableStateOf(false) }
    var editingNote by remember { mutableStateOf<Note?>(null) }
    var selectedTag by remember { mutableStateOf("all") }

    val tags = listOf(
        "all" to "Todas",
        "general" to "📝 General",
        "finance" to "💰 Finanzas",
        "goal" to "🎯 Metas",
        "idea" to "💡 Ideas"
    )

    val filteredNotes = if (selectedTag == "all") notes
        else notes.filter { it.tag == selectedTag }

    Box(Modifier.fillMaxSize().background(BgPrimary)) {
        Column(Modifier.fillMaxSize()) {
            // Header
            Row(
                Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 52.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Notas 📝", style = DanbronType.headlineMedium, color = TextPrimary)
                Box(
                    Modifier.clip(RoundedCornerShape(10.dp))
                        .background(Gold)
                        .clickable { editingNote = null; showEditor = true }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text("+ Nueva", style = DanbronType.labelSmall.copy(fontWeight = FontWeight.W700, letterSpacing = 0.sp), color = Color(0xFF0A0A0F))
                }
            }

            // Tag filters
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                items(tags, key = { it.first }) { (value, label) ->
                    val active = selectedTag == value
                    Box(
                        Modifier.clip(RoundedCornerShape(20.dp))
                            .background(if (active) GoldGlow else BgSecondary)
                            .border(1.dp, if (active) Gold else Border, RoundedCornerShape(20.dp))
                            .clickable { selectedTag = value }
                            .padding(horizontal = 14.dp, vertical = 7.dp)
                    ) {
                        Text(label, style = DanbronType.labelSmall.copy(letterSpacing = 0.sp, fontWeight = FontWeight.W500),
                            color = if (active) Gold else TextSecondary)
                    }
                }
            }

            if (filteredNotes.isEmpty()) {
                // Empty state
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📭", fontSize = 48.sp)
                        Spacer(Modifier.height(12.dp))
                        Text("Sin notas aún", style = DanbronType.bodyLarge, color = TextTertiary)
                        Spacer(Modifier.height(4.dp))
                        Text("Toca + Nueva para crear tu primera nota", style = DanbronType.bodySmall, color = TextTertiary)
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f).padding(bottom = 80.dp)
                ) {
                    items(filteredNotes, key = { it.id }) { note ->
                        NoteCard(note,
                            onClick = { editingNote = note; showEditor = true },
                            onDelete = { vm.deleteNote(note.id) }
                        )
                    }
                }
            }
        }

        // Note editor modal
        if (showEditor) {
            NoteEditorModal(
                note = editingNote,
                onSave = { title, content, tag ->
                    if (editingNote != null) {
                        vm.updateNote(editingNote!!.id, title, content, tag)
                    } else {
                        vm.addNote(title, content, tag)
                    }
                    showEditor = false
                },
                onDismiss = { showEditor = false }
            )
        }
    }
}

@Composable
private fun NoteCard(note: Note, onClick: () -> Unit, onDelete: () -> Unit) {
    val tagIcon = when (note.tag) {
        "finance" -> "💰"
        "goal" -> "🎯"
        "idea" -> "💡"
        else -> "📝"
    }
    val dateFmt = SimpleDateFormat("d MMM · HH:mm", Locale("es")).format(Date(note.updatedAt))

    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(BgSecondary)
            .border(1.dp, Border, RoundedCornerShape(14.dp))
            .padding(18.dp)
    ) {
        Row(Modifier.fillMaxWidth().clickable { onClick() }, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                Text(tagIcon, fontSize = 14.sp)
                Spacer(Modifier.width(8.dp))
                Text(note.title, style = DanbronType.bodyMedium.copy(fontWeight = FontWeight.W600), color = TextPrimary,
                    maxLines = 1)
            }
            Text("✕", color = TextTertiary, fontSize = 14.sp,
                modifier = Modifier.clickable(enabled = true, onClick = { onDelete() }).padding(4.dp))
        }
        if (note.content.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(note.content, style = DanbronType.bodySmall, color = TextSecondary,
                maxLines = 3)
        }
        Spacer(Modifier.height(8.dp))
        Text(dateFmt, style = DanbronType.caption, color = TextTertiary)
    }
}

@Composable
private fun NoteEditorModal(
    note: Note?,
    onSave: (String, String, String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(note?.title ?: "") }
    var content by remember { mutableStateOf(note?.content ?: "") }
    var selectedTag by remember { mutableStateOf(note?.tag ?: "general") }
    val keyboardController = LocalSoftwareKeyboardController.current

    val tags = listOf(
        "general" to "📝 General",
        "finance" to "💰 Finanzas",
        "goal" to "🎯 Metas",
        "idea" to "💡 Ideas"
    )

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.8f)).clickable { onDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            Modifier.fillMaxWidth()
                .fillMaxHeight(0.75f)
                .clip(RoundedCornerShape(28.dp, 28.dp, 0.dp, 0.dp))
                .background(BgSecondary)
                .clickable(enabled = false) {}
                .padding(24.dp)
        ) {
            Text(
                if (note != null) "Editar nota" else "Nueva nota",
                style = DanbronType.headlineSmall, color = TextPrimary
            )
            Spacer(Modifier.height(20.dp))

            // Tag selector
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                tags.forEach { (value, label) ->
                    val active = selectedTag == value
                    Box(
                        Modifier.clip(RoundedCornerShape(16.dp))
                            .background(if (active) GoldGlow else BgTertiary)
                            .border(1.dp, if (active) Gold else Border, RoundedCornerShape(16.dp))
                            .clickable { selectedTag = value }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(label, style = DanbronType.caption, color = if (active) Gold else TextTertiary)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Title
            OutlinedTextField(
                value = title, onValueChange = { title = it },
                placeholder = { Text("Título de la nota", color = TextTertiary) },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Gold, unfocusedBorderColor = Border,
                    cursorColor = Gold, focusedContainerColor = BgTertiary, unfocusedContainerColor = BgTertiary,
                    focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                ), singleLine = true
            )

            Spacer(Modifier.height(12.dp))

            // Content
            OutlinedTextField(
                value = content, onValueChange = { content = it },
                placeholder = { Text("Escribe tu nota aquí...", color = TextTertiary) },
                modifier = Modifier.fillMaxWidth().weight(1f), shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Gold, unfocusedBorderColor = Border,
                    cursorColor = Gold, focusedContainerColor = BgTertiary, unfocusedContainerColor = BgTertiary,
                    focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
                )
            )

            Spacer(Modifier.height(16.dp))

            // Buttons
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = BgTertiary, contentColor = TextSecondary)
                ) { Text("Cancelar") }
                Button(
                    onClick = { 
                        keyboardController?.hide()
                        if (title.isNotBlank()) onSave(title, content, selectedTag) 
                    },
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Color(0xFF0A0A0F))
                ) { Text("Guardar", style = DanbronType.labelLarge) }
            }
        }
    }
}
