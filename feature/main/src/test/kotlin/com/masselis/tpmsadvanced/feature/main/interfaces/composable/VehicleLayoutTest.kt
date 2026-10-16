package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.masselis.tpmsadvanced.core.test.MainDispatcherRule
import com.masselis.tpmsadvanced.core.ui.LocalReadability
import com.masselis.tpmsadvanced.core.ui.Readability
import com.masselis.tpmsadvanced.data.vehicle.model.Location
import com.masselis.tpmsadvanced.data.vehicle.model.Vehicle.Kind
import com.masselis.tpmsadvanced.feature.main.interfaces.viewmodel.TyreIconViewModel
import com.masselis.tpmsadvanced.feature.main.usecase.TyreIconStateFlow
import com.masselis.tpmsadvanced.feature.main.usecase.TyreStatsStateFlow.State
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import kotlin.test.assertEquals

internal class VehicleLayoutTest {
    @get:Rule val dispatcher = MainDispatcherRule()
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig(screenWidth = 360, screenHeight = 640,
        density = com.android.resources.Density.MEDIUM, softButtons = false),
        theme = "android:Theme.Material3.DayNight.NoActionBar")
    private val icon = object : TyreIconViewModel {
        override val stateFlow = MutableStateFlow<TyreIconStateFlow.State>(TyreIconStateFlow.State.NotDetected)
    }

    @Test fun carDiagramSurvivesLargeSystemFont() = renderDiagram(Kind.CAR)
    @Test fun vanDiagramSurvivesEnlargedReadouts() = renderDiagram(Kind.VAN, Readability(scale = 1.5f))
    @Test fun uteDiagramSurvivesHighVisibility() = renderDiagram(Kind.UTE, Readability(highVisibility = true))
    @Test fun shortPortraitContentIsNotLandscape() = renderDiagram(Kind.CAR, height = 300)

    private fun renderDiagram(kind: Kind, readability: Readability = Readability(), height: Int = 460) {
        val rendered = mutableSetOf<Location>()
        paparazzi.snapshot {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.3f),
                LocalReadability provides readability) {
                MaterialTheme(colorScheme = if (readability.highVisibility) darkColorScheme() else lightColorScheme()) {
                    Surface(Modifier.fillMaxSize()) { Box {
                        VehicleLayout(kind, Modifier.fillMaxWidth().height(height.dp),
                            cards = { error("Portrait diagram unexpectedly replaced with cards") },
                            diagram = { modifier -> FourWheelVehicleLayout(kind, modifier,
                                tyre = { location, tyreModifier -> Tyre(location, SnackbarHostState(), tyreModifier,
                                    vehicleComponent = lazy { error("Supplied view model") }, viewModel = icon) },
                                reading = { location, readingModifier ->
                                    rendered += location
                                    ReadingValues(location, State.NotDetected, readingModifier)
                                },
                                binding = { _, bindingModifier ->
                                    TextButton({}, bindingModifier) { Text("+") }
                                },
                            ) },
                        )
                    } }
                }
            }
        }
        assertEquals(kind.locations, rendered)
    }

    @Test fun portraitCardsAreExplicitlySelectable() = renderCards(Kind.CAR, preferCards = true)
    @Test fun landscapeStillUsesCards() = renderCards(Kind.CAR, landscape = true)
    @Test fun carWithSpareStillUsesCards() = renderCards(Kind.CAR_WITH_SPARE)
    @Test fun monowheelStillUsesCards() = renderCards(Kind.MONOWHEEL)

    private fun renderCards(kind: Kind, preferCards: Boolean = false, landscape: Boolean = false) {
        var cardsRendered = false
        paparazzi.snapshot {
            val configuration = Configuration(LocalConfiguration.current).apply {
                orientation = if (landscape) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT
            }
            CompositionLocalProvider(LocalConfiguration provides configuration,
                LocalReadability provides Readability(readingCards = preferCards)) {
                MaterialTheme { Surface(Modifier.fillMaxSize()) {
                    VehicleLayout(kind, Modifier.fillMaxSize(),
                        diagram = { error("Expected reading cards for this view") },
                        cards = { modifier ->
                            cardsRendered = true
                            LandscapeReadings(kind.locations, modifier) {
                                ReadingValues(it, State.NotDetected, size = ReadingSize.CARD)
                            }
                        },
                    )
                } }
            }
        }
        assertEquals(true, cardsRendered)
    }
}
