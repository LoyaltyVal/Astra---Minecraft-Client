package dev.astra.client.features;

import dev.astra.client.Astra;
import dev.astra.client.social.FriendsScreen;

/** Quick Join lives in the Friends menu's Servers tab; this opens it there. */
public final class QuickJoinScreen {

    private QuickJoinScreen() {
    }

    public static void open() {
        Astra.platform().openUi(FriendsScreen.servers());
    }
}
