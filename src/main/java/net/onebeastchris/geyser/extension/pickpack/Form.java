package net.onebeastchris.geyser.extension.pickpack;

import net.onebeastchris.geyser.extension.pickpack.util.LanguageManager;
import org.geysermc.cumulus.component.ToggleComponent;
import org.geysermc.cumulus.form.CustomForm;
import org.geysermc.cumulus.form.ModalForm;
import org.geysermc.geyser.api.connection.GeyserConnection;
import org.geysermc.geyser.api.pack.ResourcePackManifest;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.text.ChatColor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static net.onebeastchris.geyser.extension.pickpack.PickPack.config;
import static net.onebeastchris.geyser.extension.pickpack.PickPack.loader;

public class Form {
    private final String lang;
    private final GeyserConnection connection;
    public enum Filter {
        APPLIED,
        NOT_APPLIED,
        ALL
    }
    public Form(GeyserConnection connection) {
        this.connection = connection;
        this.lang = connection.locale();
    }

    public void showPackConfirmation(String requestedPackName) {
        String xuid = connection.xuid();
        String packId = null;
        String packName = null;
        
        // Find the pack ID by name (case-insensitive search)
        for (Map.Entry<String, ResourcePackManifest> entry : loader.PACKS_INFO.entrySet()) {
            String name = entry.getValue().header().name();
            if (name.equalsIgnoreCase(requestedPackName)) {
                packId = entry.getKey();
                packName = name;
                break;
            }
        }
        
        if (packId == null) {
            // Pack not found, send message to player
            connection.sendMessage(LanguageManager.getLocaleString(lang, "pack.not.found")
                    .replace("%pack%", requestedPackName));
            return;
        }
        
        boolean currentlyApplied = PickPack.storage.hasSpecificPack(xuid, packId);
        
        // Create confirmation modal form
        ModalForm.Builder form = ModalForm.builder()
                .title(LanguageManager.getLocaleString(lang, "pack.confirm.title"))
                .content(String.format(
                    LanguageManager.getLocaleString(lang, "pack.confirm.content"),
                    packName,
                    currentlyApplied ? 
                        LanguageManager.getLocaleString(lang, "pack.status.applied") : 
                        LanguageManager.getLocaleString(lang, "pack.status.not.applied")
                ))
                .button1(LanguageManager.getLocaleString(lang, "pack.confirm.apply"))
                .button2(LanguageManager.getLocaleString(lang, "pack.confirm.cancel"));
                
        final String finalPackId = packId;
        final boolean isApplied = currentlyApplied;
        
        form.validResultHandler((modalForm, response) -> {
            if (response.clickedButtonId() == 0) { // Apply button clicked
                List<String> playerPacks;
                
                if (isApplied) {
                    // Pack is already applied, so remove it
                    playerPacks = new ArrayList<>(PickPack.storage.getPackIds(xuid));
                    playerPacks.remove(finalPackId);
                    connection.sendMessage(LanguageManager.getLocaleString(lang, "pack.removed")
                            .replace("%pack%", packName));
                } else {
                    // Pack is not applied, so add it
                    playerPacks = new ArrayList<>(PickPack.storage.getPackIds(xuid));
                    playerPacks.add(finalPackId);
                    connection.sendMessage(LanguageManager.getLocaleString(lang, "pack.applied")
                            .replace("%pack%", packName));
                }
                
                CompletableFuture<Void> future = PickPack.storage.setPacks(xuid, playerPacks);
                future.thenRun(() -> handle(config.useTransferPacket()));
            } else {
                // Send feedback that operation was cancelled
                connection.sendMessage(LanguageManager.getLocaleString(lang, "pack.cancelled"));
            }
        });
        
        connection.sendForm(form.build());
    }

    public void send(String... args) {
        String xuid = connection.xuid();

        if (args != null && args.length > 0) {
            switch (args[0]) {
                case "filter" -> {
                    filterForm();
                    return;
                }
                case "clear" -> {
                    CompletableFuture<Void> future = PickPack.storage.setPacks(xuid, new ArrayList<>(loader.OPTIONAL.keySet()));
                    future.thenRun(() -> handle(config.useTransferPacket()));
                    return;
                }
            }
        }
        ModalForm.Builder form = ModalForm.builder()
                .title(LanguageManager.getLocaleString(lang, "main.menu.title"))
                .content(getPacks(xuid))
                .button1(LanguageManager.getLocaleString(lang, "main.menu.filter"))
                .button2(LanguageManager.getLocaleString(lang, "main.menu.select"));

        form.validResultHandler((modalForm, response) -> {
            switch (response.clickedButtonId()) {
                case 0 -> filterForm();
                case 1 -> packsForm(config.useTransferPacket(), config.showPackDescription(), Filter.ALL);
            }
        });
        connection.sendForm(form.build());
    }

    public void filterForm() {
        CustomForm.Builder form = CustomForm.builder()
                .title(LanguageManager.getLocaleString(lang, "filter.form.title"));

        if (PickPack.storage.getPackIds(connection.xuid()).isEmpty()) {
            form.dropdown(LanguageManager.getLocaleString(lang, "filter.button.name"), LanguageManager.getLocaleString(lang, "filter.all.packs"));
        } else {
            form.dropdown(LanguageManager.getLocaleString(lang, "filter.button.name"),
                    LanguageManager.getLocaleString(lang, "filter.all.packs"),
                    LanguageManager.getLocaleString(lang, "filter.not_applied.packs"),
                    LanguageManager.getLocaleString(lang, "filter.applied.packs"));
        }
        form.toggle(LanguageManager.getLocaleString(lang, "filter.description.toggle"), config.showPackDescription());
        form.label(LanguageManager.getLocaleString(lang, "filter.transfer.warning"));
        form.toggle(LanguageManager.getLocaleString(lang, "filter.transfer.toggle"), config.useTransferPacket());

        form.validResultHandler((customform, response) -> {
            int filterResult  = response.asDropdown(0);
            boolean description = response.asToggle(1);
            // 2 is the label
            boolean transfer = response.asToggle(3);

            switch (filterResult) {
                case 1 -> packsForm(transfer, description, Filter.NOT_APPLIED);
                case 2 -> packsForm(transfer, description, Filter.APPLIED);
                default -> packsForm(transfer, description, Filter.ALL);
            }
        });
        connection.sendForm(form.build());
    }

    public void packsForm(boolean transferPacket, boolean description, Filter filter) {
        String xuid = connection.xuid();
        Map<String, String> tempMap = new HashMap<>();
        CustomForm.Builder form = CustomForm.builder()
                .title(LanguageManager.getLocaleString(lang, "pack.form.title"));

        form.label(String.format(LanguageManager.getLocaleString(lang, "pack.form.label"), getFilterType(filter)));

        for (Map.Entry<String, ResourcePackManifest> entry : loader.PACKS_INFO.entrySet()) {
            String name = entry.getValue().header().name();
            boolean currentlyApplied = PickPack.storage.hasSpecificPack(xuid, entry.getKey());
            boolean isVisible = filter.equals(Filter.ALL) ||
                    (filter.equals(Filter.APPLIED) && currentlyApplied) ||
                    (filter.equals(Filter.NOT_APPLIED) && !currentlyApplied);
            if (isVisible) {
                form.toggle(name, currentlyApplied);
                if (description) form.label(ChatColor.ITALIC + entry.getValue().header().description() + ChatColor.RESET);
                tempMap.put(entry.getValue().header().name(), entry.getKey()); //makes it easier to get the uuid from the name later on
            }
        }

        form.closedOrInvalidResultHandler((customform, response) -> {
            filterForm(); //we cant add back buttons. But we can just send the filter form again.
        });

        form.validResultHandler((customform, response) -> {
            List<String> playerPacks = new ArrayList<>();
            customform.content().forEach((component) -> {
                if (component instanceof ToggleComponent) {
                    if (Boolean.TRUE.equals(response.next())) {
                        String uuid = tempMap.get(component.text());
                        playerPacks.add(uuid);
                    }
                }
            });

            if (filter.equals(Filter.NOT_APPLIED)) {
                //keep the old packs if we are filtering for not applied packs
                playerPacks.addAll(PickPack.storage.getPackIds(xuid));
            }

            CompletableFuture<Void> future = PickPack.storage.setPacks(xuid, playerPacks);
            future.thenRun(() -> handle(transferPacket));

            tempMap.clear();
        });
        connection.sendForm(form);
    }

    private String getPacks(String xuid) {
        StringBuilder packs = new StringBuilder();
        for (String packId : PickPack.storage.getPackIds(xuid)) {
            String name = loader.PACKS_INFO.get(packId).header().name();
            packs.append(" - ").append(name).append("\n");
        }
        if (packs.isEmpty()) packs.append(LanguageManager.getLocaleString(lang, "no_packs.warning"));
        return packs.toString();
    }

    private void handle(boolean transferPacket) {
        GeyserSession session = (GeyserSession) connection;
        if (transferPacket) {
            session.transfer(config.address(), config.port());
        } else {
            session.disconnect(LanguageManager.getLocaleString(lang, "disconnect.message"));
        }
    }

    private String getFilterType(Filter filter) {
        return switch (filter) {
            case ALL -> LanguageManager.getLocaleString(lang, "filter.all.packs");
            case APPLIED -> LanguageManager.getLocaleString(lang, "filter.applied.packs");
            case NOT_APPLIED -> LanguageManager.getLocaleString(lang, "filter.not_applied.packs");
        };
    }
}
