package com.heybroy0.movableblockentities.client;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;

import static com.heybroy0.movableblockentities.client.ConfigStore.*;

/** Separate Hitbox and Advanced editor pages, both reached from the main adhesion row. */
public final class AdhesionEditorScreen extends Screen {
    public enum Page { HITBOX, ADVANCED }

    private static final int PANEL_WIDTH = 540;
    private static final int PANEL_HEIGHT = 418;
    private final String blockId;
    private final Page page;
    private final BlockConfig config;
    private int left;
    private int top;
    private Button geometryButton;
    private Button orientationButton;
    private Button leadButton;

    public AdhesionEditorScreen(String blockId, Page page) {
        super(Component.literal(page == Page.HITBOX ? "Hitbox Adhesion Settings" : "Advanced Adhesion Settings"));
        this.blockId = blockId;
        this.page = page;
        this.config = ConfigStore.get(blockId);
    }

    @Override
    protected void init() {
        clearWidgets();
        left = (width - PANEL_WIDTH) / 2;
        top = Math.max(8, (height - PANEL_HEIGHT) / 2);

        geometryButton = addButton("", left + 255, top + 86, 220, 22, button -> {
            GeometrySource[] values = GeometrySource.values();
            config.geometrySource = values[(config.geometrySource.ordinal() + 1) % values.length];
            saveAndRefresh();
        });

        addButton("−", left + 255, top + 123, 28, 22, button -> {
            config.minimumSquare = Math.max(1, config.minimumSquare - 1);
            saveAndRefresh();
        });
        addButton("+", left + 447, top + 123, 28, 22, button -> {
            config.minimumSquare = Math.min(16, config.minimumSquare + 1);
            saveAndRefresh();
        });

        addButton("−", left + 255, top + 160, 28, 22, button -> {
            config.depth = Math.max(1, config.depth - 1);
            saveAndRefresh();
        });
        addButton("+", left + 447, top + 160, 28, 22, button -> {
            config.depth = Math.min(16, config.depth + 1);
            saveAndRefresh();
        });

        if (page == Page.ADVANCED) {
            orientationButton = addButton("", left + 255, top + 197, 220, 22, button -> {
                Orientation[] values = Orientation.values();
                config.orientation = values[(config.orientation.ordinal() + 1) % values.length];
                saveAndRefresh();
            });
            leadButton = addButton("", left + 255, top + 234, 220, 22, button -> {
                String[] values = {"AUTO", "UP", "DOWN", "NORTH", "SOUTH", "EAST", "WEST"};
                int current = 0;
                for (int i = 0; i < values.length; i++) if (values[i].equals(config.leadDirection)) current = i;
                config.leadDirection = values[(current + 1) % values.length];
                saveAndRefresh();
            });

            Face[] faces = Face.values();
            int faceStartY = top + 278;
            for (int i = 0; i < faces.length; i++) {
                Face face = faces[i];
                int col = i % 3;
                int row = i / 3;
                addButton(faceLabel(face), left + 28 + col * 160, faceStartY + row * 34, 145, 24, button -> {
                    config.faces.put(face, !Boolean.TRUE.equals(config.faces.get(face)));
                    ConfigStore.save();
                    button.setMessage(Component.literal(faceLabel(face)));
                });
            }
        }

        addButton("Back", left + 20, top + PANEL_HEIGHT - 42, 110, 22,
                button -> minecraft.gui.setScreen(new MainConfigScreen(blockId)));
        addButton("Done", left + PANEL_WIDTH - 130, top + PANEL_HEIGHT - 42, 110, 22,
                button -> minecraft.gui.setScreen(new MainConfigScreen(blockId)));
        refreshLabels();
    }

    private Button addButton(String label, int x, int y, int w, int h, Button.OnPress action) {
        return addRenderableWidget(Button.builder(Component.literal(label), action).bounds(x, y, w, h).build());
    }

    private void saveAndRefresh() {
        ConfigStore.save();
        refreshLabels();
    }

    private void refreshLabels() {
        if (geometryButton == null) return;
        geometryButton.setMessage(Component.literal("‹  " + pretty(config.geometrySource.name()) + "  ›"));
        if (orientationButton != null) {
            orientationButton.setMessage(Component.literal("‹  " + pretty(config.orientation.name()) + "  ›"));
        }
        if (leadButton != null) {
            leadButton.setMessage(Component.literal("‹  " + pretty(config.leadDirection) + "  ›"));
        }
    }

    private String faceLabel(Face face) {
        boolean enabled = Boolean.TRUE.equals(config.faces.get(face));
        String name = switch (face) {
            case FRONT -> "Front";
            case BACK -> "Back";
            case LEFT -> "Left";
            case RIGHT -> "Right";
            case UP -> "Up";
            case DOWN -> "Down";
        };
        return face.name().substring(0, 1) + "  " + name + ": " + (enabled ? "ON" : "OFF");
    }

    private static String pretty(String value) {
        String lower = value.toLowerCase(Locale.ROOT).replace('_', ' ');
        return lower.isEmpty() ? lower : Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        graphics.fillGradient(0, 0, width, height, 0xFF202329, 0xFF101215);
        graphics.fill(left - 3, top - 3, left + PANEL_WIDTH + 3, top + PANEL_HEIGHT + 3, 0xAA050607);
        graphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xEE292D32);
        graphics.outline(left, top, PANEL_WIDTH, PANEL_HEIGHT, 0xFF555B62);
        graphics.fill(left + 1, top + 1, left + PANEL_WIDTH - 1, top + 38, 0xFF202428);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        graphics.centeredText(font,
                page == Page.HITBOX ? "HITBOX ADHESION" : "ADVANCED ADHESION",
                left + PANEL_WIDTH / 2, top + 14, 0xFFFFFFFF);
        graphics.text(font, "Block: " + blockId, left + 22, top + 52, 0xFFBBC3CC);
        graphics.text(font, "Geometry source", left + 28, top + 93, 0xFFE5E8EB);
        graphics.text(font, "Minimum contact square", left + 28, top + 130, 0xFFE5E8EB);
        graphics.text(font, "Inward depth (voxels)", left + 28, top + 167, 0xFFE5E8EB);
        if (page == Page.HITBOX) {
            graphics.text(font, "16 × 16 face grid  •  largest fully occupied square  •  green = enabled", left + 28, top + 204, 0xFF9FA8B2);
        }

        if (page == Page.ADVANCED) {
            graphics.text(font, "Orientation handling", left + 28, top + 204, 0xFFE5E8EB);
            graphics.text(font, "Lead direction", left + 28, top + 241, 0xFFE5E8EB);
            graphics.text(font, "Manual face overrides", left + 28, top + 267, 0xFFBBC3CC);
            graphics.text(font, "F Front   B Back   L Left   R Right   U Up   D Down", left + 28, top + 345, 0xFF9FA8B2);
        }

        graphics.text(font, "Saved locally for this block. GUI preview only: piston movement and adhesion checks are not wired in.",
                left + 22, top + PANEL_HEIGHT - 66, 0xFF9FA8B2);
        super.extractRenderState(graphics, mouseX, mouseY, a);

        drawValue(graphics, left + 296, top + 129, Integer.toString(config.minimumSquare));
        drawValue(graphics, left + 296, top + 166, Integer.toString(config.depth));
    }

    private void drawValue(GuiGraphicsExtractor graphics, int x, int y, String value) {
        graphics.fill(x, y, x + 130, y + 22, 0xFF1F2327);
        graphics.outline(x, y, 130, 22, 0xFF545D66);
        graphics.centeredText(font, value, x + 65, y + 7, 0xFFFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}

