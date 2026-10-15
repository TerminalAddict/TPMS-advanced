package com.masselis.tpmsadvanced.feature.main.ioc

import com.masselis.tpmsadvanced.data.app.interfaces.AppPreferences
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo

@ContributesTo(AppScope::class)
public interface ReadingSettingsDependencies {
    public val readingPreferences: AppPreferences
}
