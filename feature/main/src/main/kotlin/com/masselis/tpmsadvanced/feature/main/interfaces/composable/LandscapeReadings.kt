package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.masselis.tpmsadvanced.data.vehicle.model.Location

/** Scrollable cards keep all vehicle types readable on short screens and with large system fonts. */
@Composable
internal fun LandscapeReadings(
    locations: Set<Location>,
    modifier: Modifier = Modifier,
    binding: @Composable (Location) -> Unit = {},
    reading: @Composable (Location) -> Unit,
) {
    BoxWithConstraints(modifier) {
        val scale = androidx.compose.ui.platform.LocalDensity.current.fontScale *
            com.masselis.tpmsadvanced.core.ui.LocalReadability.current.scale
        val columns = if (maxWidth >= 480.dp * scale) 2 else 1
        val cardHeight = if (locations.size <= columns) (maxHeight - 24.dp).coerceAtLeast(0.dp) else 0.dp
        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(locations.toList(), key = { it.toString() }, span = {
                GridItemSpan(if (locations.size == 3 && it is Location.Axle) maxLineSpan else 1)
            }) { location ->
                Card {
                    Column(
                        Modifier.fillMaxWidth().heightIn(min = cardHeight).padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(buildString { appendLoc(location, withType = false, capitalized = true) },
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f))
                            binding(location)
                        }
                        reading(location)
                    }
                }
            }
        }
    }
}
