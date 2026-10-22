package com.farmlist;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Farm Checklist client mod. The only way in is holding a finalized Checklist Book and right-clicking it.
 */
public class FarmListClient implements ClientModInitializer {

    static final Path TRANSCRIPTION_DIR =
            FabricLoader.getInstance().getGameDir().resolve("farmlist").resolve("transcriptions");

    @Override
    public void onInitializeClient() {
        BookInteraction.register();
    }

    /** Parses lines like "wheat_seeds 64", skipping the title, comments, and blank lines. */
    static Map<String, Integer> parseRequired(Path file) {
        Map<String, Integer> required = new HashMap<>();
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split("\\s+");
                if (parts.length != 2) continue;
                try {
                    required.put(normalize(parts[0]), Integer.parseInt(parts[1]));
                } catch (NumberFormatException ignored) {
                }
            }
        } catch (IOException ignored) {
        }
        return required;
    }

    /** Counts item IDs in the player's inventory. */
    static Map<String, Integer> countInventory() {
        Map<String, Integer> counts = new HashMap<>();
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return counts;
        var inv = mc.player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (stack.isEmpty()) continue;
            String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
            counts.merge(id, stack.getCount(), Integer::sum);
        }
        return counts;
    }

    /** Row ID for a flexible building-block row, e.g. "building_block 256" in the transcription. */
    static final String BUILDING_BLOCK = "building_block";

    private static List<String> blockOptions = null;

    /** Every block item in the game, sorted by name. Built once from the item registry. */
    static List<String> blockOptions() {
        if (blockOptions == null) {
            List<String> ids = new ArrayList<>();
            for (var item : BuiltInRegistries.ITEM) {
                if (item instanceof net.minecraft.world.item.BlockItem) {
                    String id = BuiltInRegistries.ITEM.getKey(item).getPath();
                    if (id.endsWith("_stairs") || id.endsWith("_slab")) continue; // skipped on purpose
                    ids.add(id);
                }
            }
            ids.sort(null);
            blockOptions = ids;
        }
        return blockOptions;
    }

    /** Item ID that actually gets counted for a row: the chosen block, or the row ID itself. */
    static String countedId(String rowId, Progress progress) {
        if (BUILDING_BLOCK.equals(rowId)) {
            String choice = progress.getChoice(rowId);
            return choice != null ? choice : rowId;
        }
        return rowId;
    }

    static String normalize(String id) {
        return id.replace("minecraft:", "").toLowerCase();
    }

    static List<String> listTranscriptions() {
        List<String> names = new ArrayList<>();
        if (!Files.isDirectory(TRANSCRIPTION_DIR)) return names;
        try (Stream<Path> s = Files.list(TRANSCRIPTION_DIR)) {
            s.map(p -> p.getFileName().toString())
                    .filter(n -> n.endsWith(".txt"))
                    .sorted()
                    .forEach(names::add);
        } catch (IOException ignored) {
        }
        return names;
    }

    /** Copies the transcription text to the clipboard so it can be pasted into the Checklist Blank. */
    static void copyToClipboard(String fileName) {
        try {
            String text = Files.readString(TRANSCRIPTION_DIR.resolve(fileName), StandardCharsets.UTF_8);
            Minecraft.getInstance().keyboardHandler.setClipboard(text);
            message("Copied " + fileName + ". Paste it into your Checklist Blank, then sign.");
        } catch (IOException e) {
            message("Could not read " + fileName);
        }
    }

    /**
     * Opens the system file window, lets the player pick a .txt transcription, saves a copy into the
     * transcriptions folder, and copies its text to the clipboard. Returns the saved file name, or null.
     */
    static String importFromFile() {
        java.awt.FileDialog dialog = new java.awt.FileDialog((java.awt.Frame) null, "Choose a checklist", java.awt.FileDialog.LOAD);
        dialog.setFilenameFilter((dir, name) -> name.toLowerCase().endsWith(".txt"));
        dialog.setVisible(true);

        String dir = dialog.getDirectory();
        String name = dialog.getFile();
        if (dir == null || name == null) return null; // player cancelled

        try {
            Path source = Path.of(dir, name);
            Files.createDirectories(TRANSCRIPTION_DIR);
            Path saved = TRANSCRIPTION_DIR.resolve(name);
            if (!Files.exists(saved)) {
                Files.copy(source, saved);
            }
            copyToClipboard(name);
            return name;
        } catch (IOException e) {
            message("Could not import " + name + ": " + e.getMessage());
            return null;
        }
    }

    /** Latest status line, shown on the checklist screen. */
    static String status = "";

    static void message(String text) {
        status = text;
    }
}
