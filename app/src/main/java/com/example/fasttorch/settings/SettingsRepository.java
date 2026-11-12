package com.example.fasttorch.settings;

import android.content.Context;
import android.content.SharedPreferences;
import com.example.fasttorch.gesture.GestureConfig;

public final class SettingsRepository {
    private final SharedPreferences preferences;

    public SettingsRepository(Context context) {
        preferences = context.getSharedPreferences("fasttorch", Context.MODE_PRIVATE);
    }

    public GestureConfig sensitivity() {
        return GestureConfig.fromName(preferences.getString("sensitivity", "NORMAL"));
    }
    public void setSensitivity(GestureConfig config) {
        preferences.edit().putString("sensitivity", config.name()).apply();
    }
    public boolean vibration() { return preferences.getBoolean("vibration", false); }
    public void setVibration(boolean enabled) {
        preferences.edit().putBoolean("vibration", enabled).apply();
    }
}
