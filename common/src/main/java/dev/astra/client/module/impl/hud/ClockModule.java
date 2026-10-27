package dev.astra.client.module.impl.hud;

import dev.astra.client.module.TextHudModule;
import dev.astra.client.module.setting.ModeSetting;

import java.text.SimpleDateFormat;
import java.util.Date;

public final class ClockModule extends TextHudModule {

    private final ModeSetting format;
    private final SimpleDateFormat twelveHour = new SimpleDateFormat("h:mm a");
    private final SimpleDateFormat twentyFourHour = new SimpleDateFormat("HH:mm");

    public ClockModule() {
        super("Clock", "Shows your local time.", false, 70, 14);
        format = setting(new ModeSetting("Format", "12 Hour", "12 Hour", "24 Hour"));
    }

    @Override
    protected String getText() {
        SimpleDateFormat f = format.is("24 Hour") ? twentyFourHour : twelveHour;
        return f.format(new Date());
    }
}
