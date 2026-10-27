package dev.astra.client.module;

public enum Category {
    HUD("HUD"),
    MECHANIC("Mechanic"),
    VISUAL("Visual"),
    /** Client-wide settings; shown on the settings page, not as a card. */
    CLIENT("Client");

    private final String displayName;

    Category(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
