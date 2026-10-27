package dev.astra.client.ui;

import dev.astra.client.Astra;
import dev.astra.client.module.HudModule;
import dev.astra.client.util.Colors;
import dev.astra.client.util.Render;

import java.util.List;

/**
 * Drag HUD modules around, scroll to resize, right-click for settings.
 * Modules snap to the screen edges and centre lines.
 */
public final class HudEditorScreen extends UiScreen {

    private static final int SNAP = 4;
    private static final int EDGE = 2;

    private HudModule dragging;
    private float offsetX;
    private float offsetY;
    private boolean snappedX;
    private boolean snappedY;
    private float snapLineX;
    private float snapLineY;

    @Override
    protected void draw(int mx, int my, float partialTicks) {
        if (Theme.dimBackground()) {
            Render.rect(0, 0, width, height, 0x30000000);
        }
        int guide = Colors.withAlpha(Theme.accent(), 0x90);

        if (dragging != null) {
            drag(mx, my);
            if (snappedX) {
                Render.rect((int) snapLineX, 0, 1, height, guide);
            }
            if (snappedY) {
                Render.rect(0, (int) snapLineY, width, 1, guide);
            }
        }

        HudModule hovered = moduleAt(mx, my);
        for (HudModule m : modules()) {
            if (!m.isShownOnHud()) {
                continue;
            }
            m.clampToScreen(width, height);
            int x = (int) m.getX();
            int y = (int) m.getY();
            int w = (int) Math.ceil(m.getScaledWidth());
            int h = (int) Math.ceil(m.getScaledHeight());
            boolean active = m == dragging || (dragging == null && m == hovered);

            Render.rect(x - 1, y - 1, w + 2, h + 2, active ? Colors.withAlpha(Theme.accent(), 0x30) : 0x18FFFFFF);
            m.draw(true);
            Render.outline(x - 1, y - 1, w + 2, h + 2, active ? Theme.accent() : 0x60FFFFFF);

            if (Theme.showHudLabels() || active) {
                String tag = active ? m.getName() + "  " + Math.round(m.getScale() * 100) + "%" : m.getName();
                int ty = y - 11 >= 0 ? y - 11 : y + h + 3;
                int tx = Math.max(1, Math.min(x, width - Render.width(tag) - 1));
                Render.text(tag, tx, ty, active ? Theme.accent() : Theme.TEXT, true);
            }
        }

        // Centre "Mods" button returns to the menu.
        int bw = 80;
        int bx = width / 2 - bw / 2;
        int by = height / 2 - 11;
        boolean hover = dragging == null && hovered == null && Render.inside(mx, my, bx, by, bw, 22);
        Render.rect(bx, by, bw, 22, hover ? Theme.BUTTON_HOVER : Theme.BUTTON);
        Render.rect(bx, by + 21, bw, 1, Theme.accent());
        Render.textCenteredScaled("MODS", width / 2f, by + 5, 1.5f, Theme.TEXT, true);

        Render.textCentered("Drag to move  |  Scroll to resize  |  Right-click for settings  |  Esc to exit",
                width / 2f, height - 14, Theme.SUBTEXT, true);
    }

    private void drag(int mx, int my) {
        HudModule m = dragging;
        float w = m.getScaledWidth();
        float h = m.getScaledHeight();
        float x = mx - offsetX;
        float y = my - offsetY;

        snappedX = false;
        snappedY = false;

        // Horizontal snapping: left edge, right edge, centre.
        if (Math.abs(x - EDGE) < SNAP) {
            x = EDGE;
            snappedX = true;
            snapLineX = EDGE;
        } else if (Math.abs(x + w - (width - EDGE)) < SNAP) {
            x = width - EDGE - w;
            snappedX = true;
            snapLineX = width - EDGE;
        } else if (Math.abs(x + w / 2 - width / 2f) < SNAP) {
            x = width / 2f - w / 2;
            snappedX = true;
            snapLineX = width / 2f;
        }

        if (Math.abs(y - EDGE) < SNAP) {
            y = EDGE;
            snappedY = true;
            snapLineY = EDGE;
        } else if (Math.abs(y + h - (height - EDGE)) < SNAP) {
            y = height - EDGE - h;
            snappedY = true;
            snapLineY = height - EDGE;
        } else if (Math.abs(y + h / 2 - height / 2f) < SNAP) {
            y = height / 2f - h / 2;
            snappedY = true;
            snapLineY = height / 2f;
        }

        m.setPosition(Math.max(0, Math.min(x, width - w)), Math.max(0, Math.min(y, height - h)));
    }

    @Override
    protected void mouseClicked(int mx, int my, int button) {
        HudModule m = moduleAt(mx, my);
        if (m == null) {
            int bw = 80;
            if (button == 0 && Render.inside(mx, my, width / 2 - bw / 2, height / 2 - 11, bw, 22)) {
                open(new ModMenuScreen());
            }
            return;
        }
        if (button == 0) {
            dragging = m;
            offsetX = mx - m.getX();
            offsetY = my - m.getY();
        } else if (button == 1) {
            open(new ModMenuScreen(m));
        }
    }

    @Override
    protected void mouseReleased(int mx, int my, int button) {
        if (button == 0 && dragging != null) {
            dragging = null;
            snappedX = snappedY = false;
            save();
        }
    }

    @Override
    protected void mouseScrolled(int mx, int my, int direction) {
        HudModule m = moduleAt(mx, my);
        if (m != null) {
            m.setScale(m.getScale() + direction * 0.1);
            m.clampToScreen(width, height);
            save();
        }
    }

    @Override
    protected void keyPressed(int keyCode) {
        if (keyCode == Astra.MENU_KEY) {
            open(new ModMenuScreen());
        }
    }

    /** Topmost enabled module under the mouse (last drawn wins). */
    private HudModule moduleAt(int mx, int my) {
        List<HudModule> list = modules();
        for (int i = list.size() - 1; i >= 0; i--) {
            HudModule m = list.get(i);
            if (m.isShownOnHud() && Render.inside(mx, my, (int) m.getX(), (int) m.getY(),
                    (int) Math.ceil(m.getScaledWidth()), (int) Math.ceil(m.getScaledHeight()))) {
                return m;
            }
        }
        return null;
    }

    private static List<HudModule> modules() {
        return Astra.get().getModules().getHudModules();
    }
}
