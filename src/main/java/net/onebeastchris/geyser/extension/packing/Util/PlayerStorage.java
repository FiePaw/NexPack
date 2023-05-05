package net.onebeastchris.geyser.extension.packing.Util;

import net.onebeastchris.geyser.extension.packing.packing;
import org.checkerframework.checker.nullness.qual.NonNull;
import org.geysermc.geyser.api.extension.ExtensionLogger;
import org.geysermc.geyser.api.packs.ResourcePack;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

import static net.onebeastchris.geyser.extension.packing.packing.loader;
import static net.onebeastchris.geyser.extension.packing.packing.storage;

public class PlayerStorage {
    public Map<String, Map<String, ResourcePack>> cache;
    ExtensionLogger logger;
    public PlayerStorage(ExtensionLogger logger) {
        this.logger = logger;
        cache = new HashMap<>();

        for (File file : Objects.requireNonNull(packing.storagePath.toFile().listFiles())) {
            cache.put(file.getName().replace(".txt", ""), FileSaveUtil.load(file.toPath()));
            logger.debug("Loading " + file.getName());
        }
    }

    public CompletableFuture<Void> setPacks(String xuid, Map<String, ResourcePack> packs) {
        cache.put(xuid, packs);
        StringBuilder packsString = new StringBuilder();
        for (ResourcePack pack : packs.values()) {
            packsString.append(pack.getManifest().getHeader().getName()).append(" ");
        }
        logger.info("Saving packs for " + xuid + ": " + packsString.toString());
        Executors.newSingleThreadExecutor().execute(() ->
                FileSaveUtil.save(packs, xuid)
        );
        return CompletableFuture.completedFuture(null);
    }

    public @NonNull Map<String, ResourcePack> getPacks(String xuid) {
        if (cache.containsKey(xuid)) {
            return cache.get(xuid);
        } else {
            return loader.OPT_OUT;
        }
    }

    public boolean hasSpecificPack(String xuid, String uuid) {
        if (cache.containsKey(xuid)) {
            return cache.get(xuid).containsKey(uuid);
        } else {
            return loader.OPT_OUT.containsKey(uuid);
        }
    }
}
