package com.surajdev.talkwise.ui.pocket

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.surajdev.talkwise.data.PocketWord
import com.surajdev.talkwise.ui.components.GlassCard
import com.surajdev.talkwise.ui.components.Glyph
import com.surajdev.talkwise.ui.components.GlyphIcon
import com.surajdev.talkwise.ui.components.IconChip
import com.surajdev.talkwise.ui.components.OrbMessage
import com.surajdev.talkwise.ui.components.Txt
import com.surajdev.talkwise.ui.components.enter
import com.surajdev.talkwise.ui.components.pressable
import com.surajdev.talkwise.ui.theme.Noc
import com.surajdev.talkwise.ui.theme.Type

@Composable
fun PocketScreen(words: List<PocketWord>, onRemove: (String) -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(
            Modifier.padding(horizontal = 24.dp, vertical = 20.dp).enter(0),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconChip(Glyph.BACK, onClick = onBack)
            Txt("Word pocket", Type.Title, Modifier.padding(start = 16.dp))
        }

        if (words.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(bottom = 80.dp).enter(1), contentAlignment = Alignment.Center) {
                OrbMessage(
                    title = "Nothing here yet",
                    detail = "When Wise uses a word worth keeping, tap it to save it here.",
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(words, key = { it.id }) { word ->
                    GlassCard(Modifier.fillMaxWidth().animateItem()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Txt(word.word, Type.Title, color = Noc.Accent)
                                Txt(word.meaning, Type.Body, Modifier.padding(top = 6.dp), color = Noc.TextDim)
                            }
                            Box(Modifier.pressable { onRemove(word.id) }.padding(8.dp)) {
                                GlyphIcon(Glyph.CLOSE, Noc.TextFaint, size = 18.dp)
                            }
                        }
                    }
                }
            }
        }
    }
}
