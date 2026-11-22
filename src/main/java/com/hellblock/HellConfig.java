package com.hellblock;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Конфиг: config/hellblock.json. Создаётся при первом запуске, правится руками.
 * Все темпы и тексты можно менять без пересборки мода.
 */
public class HellConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** Сколько сломанных блоков нужно для стадий 0,1,2,3,4. */
    public int[] phaseThresholds = {0, 40, 120, 250, 400};

    /** С какой стадии появляется демон. */
    public int demonSpawnStage = 3;
    /** На каком расстоянии от игрока он появляется (блоки). */
    public double demonSpawnDistance = 48.0;
    /** Скорость подкрадывания, когда на него НЕ смотрят (блоков в тик, 0.02 = 0.4 блока/сек). */
    public double demonBaseSpeed = 0.012;
    /** На сколько блоков демон «шагает» к тебе за каждый сломанный блок (если не смотришь на него). */
    public double demonStepPerBreak = 0.5;
    /** Шанс, что из блока вместе с лутом вылезет моб. */
    public double mobChance = 0.12;

    /** Когда демон касается игрока, запускается финал. */
    public boolean finaleOnTouch = true;
    /** true = реально удалить папку мира после краша (необратимо!). false = только «страшное сообщение». */
    public boolean deleteWorldOnFinale = false;
    /** Пауза между «экран гаснет, крик» и окном с сообщением, мс. */
    public int finaleDelayMs = 2800;
    public String finaleTitle = "Minecraft - критическая ошибка";
    public String finaleMessage = "Спасибо, что ломал.\n\nПечать снята. Я больше не заперт.\n\nТвой мир удалён.\nТвой Minecraft - тоже.";

    /** Шёпот в экшн-баре на стадиях 1-2. */
    public String[] whispers = {
            "...не ломай...",
            "...он под тобой...",
            "...слышишь?..",
            "...ещё один...",
            "...я чувствую тебя...",
            "...тише..."
    };
    /** Фейковый чат на стадии 3. %player% заменяется ником. */
    public String[] fakeChatStage3 = {
            "<Сатана> %player%",
            "<Сатана> Ты слышишь, как он бьётся?",
            "<Сатана> Каждый блок - шаг ко мне.",
            "<Сатана> Перестань ломать.",
            "<Сатана> Ты ведь знаешь, что под ним?",
            "<Сатана> %player%... не оборачивайся."
    };
    /** Фейковый чат на стадии 4. */
    public String[] fakeChatStage4 = {
            "<Сатана> Я вижу тебя, %player%.",
            "<Сатана> Оглянись.",
            "<Сатана> Ты сам меня выпустил.",
            "<Сатана> Твой мир принадлежит мне.",
            "<Сатана> Не смотри. Или смотри. Я всё равно ближе.",
            "<Сатана> Ещё немного, %player%."
    };

    public static HellConfig INSTANCE = new HellConfig();

    public static void load() {
        Path file = FabricLoader.getInstance().getConfigDir().resolve("hellblock.json");
        try {
            if (Files.exists(file)) {
                try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    HellConfig loaded = GSON.fromJson(r, HellConfig.class);
                    if (loaded != null && loaded.phaseThresholds != null && loaded.phaseThresholds.length >= 5) {
                        INSTANCE = loaded;
                    }
                }
            }
            // перезаписываем, чтобы в файле появились новые поля после обновлений
            try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(INSTANCE, w);
            }
        } catch (Exception e) {
            HellblockMod.LOGGER.error("Не удалось прочитать/записать конфиг, используем значения по умолчанию", e);
        }
    }
}
