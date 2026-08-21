package com.jiashuai.touchlayoutapplication

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jiashuai.touchlayout.TouchItemState
import com.jiashuai.touchlayout.TouchLayout
import com.jiashuai.touchlayout.TouchLayoutConfig
import com.jiashuai.touchlayout.TouchSelectionMode
import com.jiashuai.touchlayout.rememberTouchLayoutState

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TouchLayoutDemo() }
    }
}

private data class DemoCard(
    val id: String,
    val label: String,
    val color: Color,
    val initialBounds: Rect,
)

@Composable
private fun TouchLayoutDemo() {
    val density = LocalDensity.current
    val cards = remember(density) {
        with(density) {
            listOf(
                DemoCard(
                    id = "a",
                    label = "A",
                    color = Color(0xFF536DFE),
                    initialBounds = Rect(20.dp.toPx(), 36.dp.toPx(), 148.dp.toPx(), 112.dp.toPx()),
                ),
                DemoCard(
                    id = "b",
                    label = "B",
                    color = Color(0xFFFF5252),
                    initialBounds = Rect(216.dp.toPx(), 36.dp.toPx(), 344.dp.toPx(), 112.dp.toPx()),
                ),
                DemoCard(
                    id = "c",
                    label = "C",
                    color = Color(0xFF00BFA5),
                    initialBounds = Rect(72.dp.toPx(), 180.dp.toPx(), 200.dp.toPx(), 256.dp.toPx()),
                ),
                DemoCard(
                    id = "d",
                    label = "D",
                    color = Color(0xFF7C4DFF),
                    initialBounds = Rect(204.dp.toPx(), 324.dp.toPx(), 332.dp.toPx(), 400.dp.toPx()),
                ),
            )
        }
    }
    val cardsById = remember(cards) { cards.associateBy(DemoCard::id) }
    val items = remember(cards) {
        cards.map { card ->
            TouchItemState(id = card.id, initialBounds = card.initialBounds)
        }
    }
    val state = rememberTouchLayoutState(items)
    val touchLayoutConfig = remember { TouchLayoutConfig(snapThreshold = 12.dp) }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Color(0xFF3157D5),
            background = Color(0xFFF8FAFC),
            surface = Color.White,
        ),
    ) {
        Scaffold(containerColor = MaterialTheme.colorScheme.background) { contentPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.demo_title),
                            color = Color(0xFF0F172A),
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = stringResource(R.string.demo_subtitle),
                            color = Color(0xFF64748B),
                            fontSize = 14.sp,
                        )
                    }
                    Button(onClick = state::reset) {
                        Text(stringResource(R.string.reset))
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Checkbox(
                        checked = state.isSnapEnabled,
                        onCheckedChange = state::setSnapEnabled,
                    )
                    Text(stringResource(R.string.snap))
                    Checkbox(
                        checked = state.selectionMode == TouchSelectionMode.Multiple,
                        onCheckedChange = { enabled ->
                            state.setSelectionMode(
                                if (enabled) TouchSelectionMode.Multiple else TouchSelectionMode.Single,
                            )
                        },
                    )
                    Text(stringResource(R.string.multiple_choice))
                }

                Text(
                    text = stringResource(R.string.demo_instruction),
                    color = Color(0xFF64748B),
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(10.dp))

                TouchLayout(
                    state = state,
                    config = touchLayoutConfig,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White),
                ) { item ->
                    val card = cardsById.getValue(item.id)
                    Card(
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = card.color),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    ) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = card.label,
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}
