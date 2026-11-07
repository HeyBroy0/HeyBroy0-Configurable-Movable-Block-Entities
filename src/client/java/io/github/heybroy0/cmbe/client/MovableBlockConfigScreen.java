package io.github.heybroy0.cmbe.client;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.heybroy0.cmbe.config.BlockSettings;
import io.github.heybroy0.cmbe.config.ConfigManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class MovableBlockConfigScreen extends Screen {
    private enum Page { MAIN, CHANGED }

    private final Screen parent;
    private Page page = Page.MAIN;
    private String searchText = "";
    private String selectedBlockId;
    private BlockSettings working = new BlockSettings();
    private EditBox searchBox;
    private EditBox costBox;
    private List<String> suggestions = List.of();
    private int highlightedSuggestion;
    private List<String> changedIds = List.of();
    private int changedScroll;
    private String statusText = "";

    public MovableBlockConfigScreen(Screen parent) {
        super(Component.literal("Configurable Movable Block Entities"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        clearWidgets();
        if (page == Page.MAIN) initMainPage();
        else initChangedPage();
    }

    private void initMainPage() {
        int left = width / 2 - 190;
        int top = height / 2 - 150;

        searchBox = new EditBox(font, left + 12, top + 42, 272, 20, Component.literal("Block identifier or name"));
        searchBox.setMaxLength(160);
        searchBox.setValue(searchText);
        searchBox.setResponder(this::updateSuggestions);
        addRenderableWidget(searchBox);

        addRenderableWidget(Button.builder(Component.literal("Changed Blocks"), button -> {
            page = Page.CHANGED;
            changedScroll = 0;
            rebuild();
        }).bounds(left + 292, top + 42, 86, 20).build());

        if (selectedBlockId != null) {
            addRenderableWidget(Button.builder(movementLabel(), button -> {
                BlockSettings.MovementMode[] modes = BlockSettings.MovementMode.values();
                working.movement = modes[(working.movement.ordinal() + 1) % modes.length];
                button.setMessage(movementLabel());
            }).bounds(left + 12, top + 160, 178, 22).build());

            addRenderableWidget(Button.builder(tickLabel(), button -> {
                BlockSettings.ScheduledTickMode[] modes = BlockSettings.ScheduledTickMode.values();
                working.scheduledTicks = modes[(working.scheduledTicks.ordinal() + 1) % modes.length];
                button.setMessage(tickLabel());
            }).bounds(left + 200, top + 160, 178, 22).build());

            costBox = new EditBox(font, left + 12, top + 205, 100, 20, Component.literal("Push-cost denominator"));
            costBox.setMaxLength(10);
            costBox.setFilter(value -> value.isEmpty() || value.chars().allMatch(Character::isDigit));
            costBox.setValue(Integer.toString(working.pushCostDenominator));
            addRenderableWidget(costBox);

            addRenderableWidget(Button.builder(Component.literal("Save Block"), button -> saveSelectedBlock())
                .bounds(left + 122, top + 205, 100, 20).build());
        }

        addRenderableWidget(Button.builder(Component.literal("Close"), button -> onClose())
            .bounds(left + 278, top + 264, 100, 20).build());

        updateSuggestions(searchText);
    }

    private Component movementLabel() {
        return Component.literal("Movement: " + working.movement);
    }

    private Component tickLabel() {
        return Component.literal("Scheduled ticks: " + working.scheduledTicks);
    }

    private void initChangedPage() {
        changedIds = new ArrayList<>(ConfigManager.getChangedBlocks().keySet());
        changedIds.sort(String::compareTo);
        int left = width / 2 - 190;
        int top = height / 2 - 150;
        int rows = Math.min(8, Math.max(0, changedIds.size() - changedScroll));

        for (int i = 0; i < rows; i++) {
            String id = changedIds.get(changedScroll + i);
            int rowY = top + 40 + i * 25;
            addRenderableWidget(Button.builder(Component.literal("Focus"), button -> {
                page = Page.MAIN;
                selectedBlockId = id;
                searchText = id;
                working = ConfigManager.get(id);
                statusText = "";
                rebuild();
            }).bounds(left + 270, rowY, 48, 19).build());

            addRenderableWidget(Button.builder(Component.literal("Revert"), button -> {
                ConfigManager.revert(id);
                statusText = "Reverted " + id;
                rebuild();
            }).bounds(left + 324, rowY, 54, 19).build());
        }

        addRenderableWidget(Button.builder(Component.literal("Back"), button -> {
            page = Page.MAIN;
            rebuild();
        }).bounds(left + 278, top + 264, 100, 20).build());
    }

    private void rebuild() {
        clearWidgets();
        init();
    }

    private void updateSuggestions(String query) {
        searchText = query == null ? "" : query;
        highlightedSuggestion = 0;
        if (searchText.isBlank()) {
            suggestions = List.of();
            return;
        }

        String needle = searchText.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (Block block : BuiltInRegistries.BLOCK) {
            Identifier id = BuiltInRegistries.BLOCK.getKey(block);
            String identifier = id.toString();
            String displayName = block.getName().getString().toLowerCase(Locale.ROOT);
            if (identifier.toLowerCase(Locale.ROOT).contains(needle) || displayName.contains(needle)) {
                matches.add(identifier);
                if (matches.size() == 4) break;
            }
        }
        suggestions = matches;
    }

    private void selectBlock(String blockId) {
        selectedBlockId = blockId;
        searchText = blockId;
        working = ConfigManager.get(blockId);
        statusText = "";
        rebuild();
    }

    private String findExactBlock(String input) {
        try {
            Identifier id = Identifier.parse(input.trim());
            return BuiltInRegistries.BLOCK.getOptional(id).isPresent() ? id.toString() : null;
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private void acceptSuggestion(boolean preferExact) {
        if (preferExact) {
            String exact = findExactBlock(searchText);
            if (exact != null) {
                selectBlock(exact);
                return;
            }
        }
        if (!suggestions.isEmpty()) {
            selectBlock(suggestions.get(Math.min(highlightedSuggestion, suggestions.size() - 1)));
        }
    }

    private void saveSelectedBlock() {
        if (selectedBlockId == null || costBox == null) return;
        try {
            int denominator = Integer.parseInt(costBox.getValue());
            if (denominator <= 0) throw new NumberFormatException();
            working.pushCostDenominator = denominator;
            ConfigManager.set(selectedBlockId, working);
            statusText = "Saved " + selectedBlockId;
        } catch (NumberFormatException exception) {
            statusText = "Push-cost denominator must be a positive integer.";
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (page == Page.MAIN && searchBox != null && searchBox.isFocused()) {
            if (keyCode == InputConstants.KEY_DOWN && !suggestions.isEmpty()) {
                highlightedSuggestion = Math.min(highlightedSuggestion + 1, suggestions.size() - 1);
                return true;
            }
            if (keyCode == InputConstants.KEY_UP && !suggestions.isEmpty()) {
                highlightedSuggestion = Math.max(highlightedSuggestion - 1, 0);
                return true;
            }
            if (keyCode == InputConstants.KEY_TAB) {
                acceptSuggestion(false);
                return true;
            }
            if (keyCode == InputConstants.KEY_ENTER) {
                acceptSuggestion(true);
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (page == Page.MAIN && button == 0 && !suggestions.isEmpty()
            && searchBox != null && searchBox.isFocused()) {
            int panelLeft = width / 2 - 190 + 12;
            int panelTop = height / 2 - 150 + 64;
            if (mouseX >= panelLeft && mouseX <= panelLeft + 272
                && mouseY >= panelTop && mouseY < panelTop + suggestions.size() * 18) {
                selectBlock(suggestions.get((int) ((mouseY - panelTop) / 18)));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (page == Page.CHANGED && !changedIds.isEmpty()) {
            int maxScroll = Math.max(0, changedIds.size() - 8);
            changedScroll = Math.max(0, Math.min(maxScroll, changedScroll + (scrollY < 0 ? 1 : -1)));
            rebuild();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xFF15181D);
        int left = width / 2 - 190;
        int top = height / 2 - 150;
        graphics.fill(left, top, left + 390, top + 300, 0xFF292E36);
        graphics.fill(left, top, left + 390, top + 2, 0xFF657180);

        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, "Configurable Movable Block Entities", width / 2, top + 12, 0xFFF1F3F5);

        if (page == Page.MAIN) {
            graphics.drawString(font, "Block search", left + 12, top + 29, 0xFFCFD5DD);
            if (selectedBlockId != null) {
                graphics.drawString(font, displayName(selectedBlockId) + "  (" + selectedBlockId + ")", left + 12, top + 143, 0xFFF1F3F5);
                graphics.drawString(font, "Cost = 1 / denominator (positive integer)", left + 122, top + 210, 0xFFCFD5DD);
                graphics.drawString(font, "Tick modes are stored; runtime tick handling is pending.", left + 12, top + 238, 0xFFFFD27A);
            }

            if (!suggestions.isEmpty() && searchBox != null && searchBox.isFocused()) {
                int suggestionTop = top + 64;
                graphics.fill(left + 12, suggestionTop, left + 284, suggestionTop + suggestions.size() * 18, 0xFF1B1F25);
                for (int i = 0; i < suggestions.size(); i++) {
                    int color = i == highlightedSuggestion ? 0xFF46515F : 0xFF1B1F25;
                    graphics.fill(left + 12, suggestionTop + i * 18, left + 284, suggestionTop + (i + 1) * 18, color);
                    graphics.drawString(font, suggestions.get(i), left + 18, suggestionTop + i * 18 + 5, 0xFFE4E8ED);
                }
            }
        } else {
            graphics.drawString(font, "Changed blocks", left + 12, top + 27, 0xFFCFD5DD);
            int visible = Math.min(8, Math.max(0, changedIds.size() - changedScroll));
            for (int i = 0; i < visible; i++) {
                String id = changedIds.get(changedScroll + i);
                graphics.drawString(font, displayName(id) + "  (" + id + ")", left + 12, top + 43 + i * 25, 0xFFE4E8ED);
            }
            if (changedIds.isEmpty()) {
                graphics.drawString(font, "No blocks have custom settings.", left + 12, top + 56, 0xFFCFD5DD);
            }
        }

        if (!statusText.isBlank()) {
            graphics.drawString(font, statusText, left + 12, top + 282, 0xFF9FC4A9);
        }
    }

    private String displayName(String blockId) {
        try {
            Identifier id = Identifier.parse(blockId);
            Block block = BuiltInRegistries.BLOCK.getOptional(id).orElse(null);
            return block == null ? blockId : block.getName().getString();
        } catch (RuntimeException exception) {
            return blockId;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }
}
