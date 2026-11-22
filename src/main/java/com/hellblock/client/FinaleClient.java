package com.hellblock.client;

import com.hellblock.HellblockMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.storage.LevelResource;

import java.lang.reflect.Method;
import java.nio.file.Path;

/**
 * Финал: экран гаснет, крик, пауза, затем нативное окно ОС с сообщением
 * (игра при этом замирает, как при настоящем зависании), после чего процесс жёстко убивается.
 */
public final class FinaleClient {
    private static volatile boolean active = false;

    private FinaleClient() {
    }

    public static boolean isActive() {
        return active;
    }

    public static void start(String title, String message, boolean deleteWorld, int delayMs) {
        if (active) {
            return;
        }
        active = true;
        Minecraft mc = Minecraft.getInstance();

        try {
            mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.ENDERMAN_SCREAM, 0.7F, 1.0F));
        } catch (Throwable t) {
            HellblockMod.LOGGER.warn("Не удалось проиграть звук финала", t);
        }

        Thread timer = new Thread(() -> {
            try {
                Thread.sleep(Math.max(0, delayMs));
            } catch (InterruptedException ignored) {
                // ничего
            }
            // выполняем в главном (рендер) потоке: окно блокирует игру, будто она зависла
            mc.execute(() -> crash(mc, title, message, deleteWorld));
        }, "hellblock-finale");
        timer.setDaemon(true);
        timer.start();
    }

    private static void crash(Minecraft mc, String title, String message, boolean deleteWorld) {
        Path worldPath = null;
        if (deleteWorld) {
            try {
                if (mc.getSingleplayerServer() != null) {
                    worldPath = mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
                }
            } catch (Throwable t) {
                HellblockMod.LOGGER.warn("Не удалось определить путь мира", t);
            }
        }

        showDialog(title, message);

        if (worldPath != null) {
            scheduleDelete(worldPath);
        }
        Runtime.getRuntime().halt(1);
    }

    /** Нативное окно ошибки: сначала tinyfd из LWJGL (через рефлексию), потом Swing. */
    private static void showDialog(String title, String message) {
        // tinyfd ломается на кавычках
        String t = title.replace('"', ' ').replace('\'', ' ');
        String m = message.replace('"', ' ').replace('\'', ' ');
        try {
            Class<?> c = Class.forName("org.lwjgl.util.tinyfd.TinyFileDialogs");
            Method method = c.getMethod("tinyfd_messageBox",
                    CharSequence.class, CharSequence.class, CharSequence.class, CharSequence.class, boolean.class);
            method.invoke(null, t, m, "ok", "error", true);
            return;
        } catch (Throwable ignored) {
            // идём дальше
        }
        try {
            System.setProperty("java.awt.headless", "false");
            javax.swing.JOptionPane.showMessageDialog(null, message, title, javax.swing.JOptionPane.ERROR_MESSAGE);
        } catch (Throwable ignored) {
            HellblockMod.LOGGER.error("{}: {}", title, message);
        }
    }

    /** Удаляет папку мира отдельным процессом уже после смерти игры. Необратимо. */
    private static void scheduleDelete(Path world) {
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            ProcessBuilder pb;
            if (os.contains("win")) {
                pb = new ProcessBuilder("cmd", "/c",
                        "ping 127.0.0.1 -n 4 > nul & rmdir /s /q \"" + world + "\"");
            } else {
                pb = new ProcessBuilder("sh", "-c", "sleep 3; rm -rf \"" + world + "\"");
            }
            pb.start();
        } catch (Throwable t) {
            HellblockMod.LOGGER.error("Не удалось запланировать удаление мира", t);
        }
    }
}
