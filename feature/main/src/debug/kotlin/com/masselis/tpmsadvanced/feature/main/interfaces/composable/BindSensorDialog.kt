package com.masselis.tpmsadvanced.feature.main.interfaces.composable

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performScrollTo
import com.masselis.tpmsadvanced.core.androidtest.EnterExitComposable
import com.masselis.tpmsadvanced.core.androidtest.EnterExitComposable.ExitToken
import com.masselis.tpmsadvanced.core.androidtest.onEnterAndOnExit

@OptIn(ExperimentalTestApi::class)
public class BindSensorDialog private constructor(
    composeTestRule: ComposeTestRule
) :
    ComposeTestRule by composeTestRule,
    EnterExitComposable<BindSensorDialog> by onEnterAndOnExit(
        { composeTestRule.waitUntilExactlyOneExists(hasTestTag(BindSensorTags.Dialog.root)) },
        { composeTestRule.waitUntilDoesNotExist(hasTestTag(BindSensorTags.Dialog.root)) },
    ) {

    public fun assign(id: Int): ExitToken<BindSensorDialog> {
        onNodeWithTag(BindSensorTags.Dialog.manualId).performScrollTo().performTextInput(id.toUInt().toString())
        onNodeWithTag(BindSensorTags.Dialog.assign).performScrollTo().performClick()
        onNodeWithTag(BindSensorTags.Dialog.apply).performClick()
        waitUntilDoesNotExist(hasTestTag(BindSensorTags.Dialog.apply))
        onNodeWithTag(BindSensorTags.Dialog.done).performClick()
        return exitToken
    }

    public fun cancel(): ExitToken<BindSensorDialog> {
        onNodeWithTag(BindSensorTags.Dialog.done).performClick()
        return exitToken
    }

    public companion object {
        context(rule: ComposeTestRule)
        public operator fun invoke(): BindSensorDialog = BindSensorDialog(rule)
    }
}
