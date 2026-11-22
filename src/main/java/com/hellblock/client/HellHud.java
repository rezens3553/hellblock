package com.hellblock.client;

import net.minecraft.Util;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * Красный оверлей и виньетка по стадии. Работает как «небо стало красным» без правки измерения:
 * тонировка всего экрана + тёмные края, которые к финалу пульсируют вместе с сердцебиением.
 */
public final class HellHud {
    private static final int[] TINT_ALPHA = {0, 20, 42, 66, 92};
    private static final int[] VIGNETTE_ALPHA = {0, 0, 70, 120, 170};

    private HellHud() {
    }

    public static void render(GuiGraphics g, DeltaTracker tracker) {
        int w = g.guiWidth();
        int h = g.guiHeight();

        if (FinaleClient.isActive()) {
            g.fill(0, 0, w, h, 0xFF000000);
            return;
        }

        int stage = Mth.clamp(ClientState.stage, 0, 4);
        if (stage == 0) {
            return;
        }

        double t = Util.getMillis() / 1000.0;
        // пульс как сердцебиение: чем глубже стадия, тем быстрее
        double speed = 1.2 + stage * 0.6;
        float pulse = stage >= 3 ? (float) (0.75 + 0.25 * Math.sin(t * speed * Math.PI)) : 1.0F;

        int tint = (int) (TINT_ALPHA[stage] * pulse);
        g.fill(0, 0, w, h, (tint << 24) | 0x3A0000);

        int vig = (int) (VIGNETTE_ALPHA[stage] * pulse);
        if (vig > 0) {
            int edge = Math.max(20, h / 3);
            int sideEdge = Math.max(20, w / 5);
            int dark = (vig << 24) | 0x100000;
            int clear = 0x00100000;
            g.fillGradient(0, 0, w, edge, dark, clear);
            g.fillGradient(0, h - edge, w, h, clear, dark);
            g.fillGradient(0, 0, sideEdge, h, dark, clear);
            g.fillGradient(w - sideEdge, 0, w, h, clear, dark);
        }

        // на последней стадии редкие вспышки тьмы
        if (stage >= 4 && ((long) (t * 10)) % 97 == 0) {
            g.fill(0, 0, w, h, 0x66000000);
        }
    }
}
