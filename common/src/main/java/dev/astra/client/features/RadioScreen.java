package dev.astra.client.features;

import dev.astra.client.Astra;
import dev.astra.client.social.FriendsScreen;

/** The radio lives in the Friends menu's Radio tab (see RadioPanel); this opens it there. */
public final class RadioScreen {

    private RadioScreen() {
    }

    public static void open() {
        Astra.platform().openUi(FriendsScreen.radio());
    }
}
