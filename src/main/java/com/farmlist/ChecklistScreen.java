package com.farmlist;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * The book GUI. Shows the checklist for the book's title, with clickable items and an Import button.
 * If no checklist matches the title, it shows the list of transcriptions to import from.
 */
public class ChecklistScreen extends Screen {

    private static final int ROW_HEIGHT = 22;
    private static final int BUTTON_WIDTH = 240;

    private String openFile = null; // e.g. "Wheat Farm.txt", or null for the import picker

    public ChecklistScreen(String bookTitle) {
        super(Component.literal("Farm Checklist"));
        if (bookTitle != null && FarmListClient.listTranscriptions().contains(bookTitle + ".txt")) {
            this.openFile = bookTitle + ".txt";
        }
    }

    @Override
    protected void init() {
        rebuild();
    }

    private void rebuild() {
        clearWidgets();
        int left = this.width / 2 - BUTTON_WIDTH / 2;

        if (openFile == null) {
            buildPicker(left);
        } else {
            buildChecklist(left);
        }
    }

    /** Shows every transcription as a button. Clicking one opens its checklist. */
    private void buildPicker(int left) {
        addRenderableWidget(Button.builder(Component.literal("Import from File"), b -> {
            String imported = FarmListClient.importFromFile();
            if (imported != null) {
                openFile = imported;
            }
            rebuild();
        }).bounds(left, 32, BUTTON_WIDTH, 20).build());

        int y = 60;
        for (String file : FarmListClient.listTranscriptions()) {
            String name = file.replace(".txt", "");
            addRenderableWidget(Button.builder(Component.literal(name), b -> {
                openFile = file;
                rebuild();
            }).bounds(left, y, BUTTON_WIDTH, 20).build());
            y += ROW_HEIGHT;
        }
    }

    /** Clickable checklist with Import, Scan, and Back. */
    private void buildChecklist(int left) {
        addRenderableWidget(Button.builder(Component.literal("Back"), b -> {
            openFile = null;
            rebuild();
        }).bounds(left, 14, 70, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Scan Inventory"), b -> {
            scanInventory();
            rebuild();
        }).bounds(left + 76, 14, 92, 20).build());

        addRenderableWidget(Button.builder(Component.literal("Import"), b ->
                FarmListClient.copyToClipboard(openFile)
        ).bounds(left + 174, 14, 66, 20).build());

        Path file = FarmListClient.TRANSCRIPTION_DIR.resolve(openFile);
        Map<String, Integer> required = FarmListClient.parseRequired(file);
        Progress progress = new Progress(openFile.replace(".txt", ""));

        int y = 40;
        for (Map.Entry<String, Integer> e : required.entrySet()) {
            if (y > this.height - 30) break; // rows that don't fit are cut off for now
            String id = e.getKey();
            boolean done = progress.isChecked(id);
            String shown = FarmListClient.BUILDING_BLOCK.equals(id)
                    ? "building block (" + displayName(FarmListClient.countedId(id, progress)) + ")"
                    : id;
            String label = (done ? "[x] " : "[ ] ") + shown + "  x" + e.getValue();
            addRenderableWidget(Button.builder(Component.literal(label), b -> {
                progress.set(id, !progress.isChecked(id));
                rebuild();
            }).bounds(left, y, FarmListClient.BUILDING_BLOCK.equals(id) ? BUTTON_WIDTH - 120 : BUTTON_WIDTH, 20).build());

            if (FarmListClient.BUILDING_BLOCK.equals(id)) {
                addRenderableWidget(Button.builder(Component.literal("<"), b -> {
                    progress.setChoice(id, previousOption(progress.getChoice(id)));
                    rebuild();
                }).bounds(left + BUTTON_WIDTH - 116, y, 28, 20).build());

                addRenderableWidget(Button.builder(Component.literal("Next >"), b -> {
                    progress.setChoice(id, nextOption(progress.getChoice(id)));
                    rebuild();
                }).bounds(left + BUTTON_WIDTH - 86, y, 86, 20).build());
            }
            y += ROW_HEIGHT;
        }
    }

    private void scanInventory() {
        Map<String, Integer> required = FarmListClient.parseRequired(
                FarmListClient.TRANSCRIPTION_DIR.resolve(openFile));
        Map<String, Integer> have = FarmListClient.countInventory();
        Progress progress = new Progress(openFile.replace(".txt", ""));
        for (Map.Entry<String, Integer> e : required.entrySet()) {
            String counted = FarmListClient.countedId(e.getKey(), progress);
            if (FarmListClient.BUILDING_BLOCK.equals(e.getKey()) && progress.getChoice(e.getKey()) == null) {
                continue; // no block picked yet, so nothing to count
            }
            if (have.getOrDefault(counted, 0) >= e.getValue()) {
                progress.set(e.getKey(), true);
            }
        }
    }

    /** Next block after the current choice, wrapping around. Starts at the first block if none chosen. */
    private static String nextOption(String current) {
        var options = FarmListClient.blockOptions();
        if (current == null) return options.get(0);
        int i = options.indexOf(current);
        return options.get((i + 1) % options.size());
    }

    /** Previous block before the current choice, wrapping around. */
    private static String previousOption(String current) {
        var options = FarmListClient.blockOptions();
        if (current == null) return options.get(options.size() - 1);
        int i = options.indexOf(current);
        return options.get((i - 1 + options.size()) % options.size());
    }

    private static String displayName(String id) {
        return id.replace('_', ' ');
    }

    @Override
    public void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        if (!FarmListClient.status.isEmpty()) {
            g.drawCenteredString(this.font, FarmListClient.status, this.width / 2, this.height - 14, 0xFFFFFF);
        }
        if (openFile == null) {
            g.drawCenteredString(this.font, "Choose a checklist to import", this.width / 2, 14, 0xFFFFFF);
            if (FarmListClient.listTranscriptions().isEmpty()) {
                g.drawCenteredString(this.font,
                        "No transcriptions yet. Add files to .minecraft/farmlist/transcriptions/",
                        this.width / 2, 60, 0xAAAAAA);
            }
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
