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
            String
