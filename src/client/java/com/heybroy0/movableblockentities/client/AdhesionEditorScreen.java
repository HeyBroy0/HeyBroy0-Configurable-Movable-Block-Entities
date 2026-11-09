package com.heybroy0.movableblockentities.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.Locale;

import static com.heybroy0.movableblockentities.client.ConfigStore.*;

/** Separate Hitbox and Advanced editor pages, both reached from the main adhesion row. */
public final class AdhesionEditorScreen extends Screen {
    public enum Page { HITBOX, ADVANCED }

    private static final int PANEL_WIDTH = 540;
    private static final int PANEL_HEIGHT = 418;
    private static final int FACE_SIZE = 34;
    private static final int FACE_GAP = 4;
    private static final int NET_WIDTH = FACE_SIZE * 4 + FACE_GAP * 3;
    private static final int NET_LEFT = (PANEL_WIDTH - NET_WIDTH) / 2;
    private static final int NET_TOP = 286;

    private static final int FACE_ENABLED = 0xFF2F8F58;
    private static final int FACE_DISABLED = 0xFF963F45;
    private static final int FACE_ENABLED_HOVER = 0xFF43AA6A;
    private static final int FACE_DISABLED_HOVER = 0xFFB24E55;

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
        left = 0;
        top = 0;

        addButton("Back", 12, 8, 72, 22, button -> returnToMain());
        addButton("Done", PANEL_WIDTH - 84, 8, 72, 22, button -> returnToMain());

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
        }

        refreshLabels();
    }

    private Button addButton(String label, int x, int y, int w, int h, Button.OnPress action) {
        return addRenderableWidget(Button.builder(Component.literal(label), action).bounds(x, y, w, h).build());
    }

    private void returnToMain() {
        minecraft.gui.setScreen(new MainConfigScreen(blockId));
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_ESCAPE) {
            returnToMain();
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        MouseButtonEvent logicalEvent = ResponsiveUi.toLogical(event, width, height, PANEL_WIDTH, PANEL_HEIGHT);
        if (page == Page.ADVANCED && logicalEvent.button() == 1) {
            Face face = faceAt((int) logicalEvent.x(), (int) logicalEvent.y());
            if (face != null) {
                config.faces.put(face, !Boolean.TRUE.equals(config.faces.get(face)));
                ConfigStore.save();
                return true;
            }
        }
        return super.mouseClicked(logicalEvent, doubleClick);
    }

    private Face faceAt(int mouseX, int mouseY) {
        for (Face face : Face.values()) {
            int x = faceX(face);
            int y = faceY(face);
            if (mouseX >= x && mouseX < x + FACE_SIZE && mouseY >= y && mouseY < y + FACE_SIZE) {
                return face;
            }
        }
        return null;
    }

    private int faceX(Face face) {
        int column = switch (face) {
            case LEFT -> 0;
            case FRONT, UP, DOWN -> 1;
            case RIGHT -> 2;
            case BACK -> 3;
        };
        return NET_LEFT + column * (FACE_SIZE + FACE_GAP);
    }

    private int faceY(Face face) {
        int row = switch (face) {
            case UP -> 0;
            case LEFT, FRONT, RIGHT, BACK -> 1;
            case DOWN -> 2;
        };
        return NET_TOP + row * (FACE_SIZE + FACE_GAP);
    }

    private String faceSymbol(Face face) {
        return switch (face) {
            case FRONT -> "F";
            case BACK -> "B";
            case LEFT -> "L";
            case RIGHT -> "R";
            case UP -> "U";
            case DOWN -> "D";
        };
    }

    private void drawCubeNet(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        for (Face face : Face.values()) {
            boolean enabled = Boolean.TRUE.equals(config.faces.get(face));
            int x = faceX(face);
            int y = faceY(face);
            boolean hovered = mouseX >= x && mouseX < x + FACE_SIZE
                    && mouseY >= y && mouseY < y + FACE_SIZE;
            int color = enabled
                    ? (hovered ? FACE_ENABLED_HOVER : FACE_ENABLED)
                    : (hovered ? FACE_DISABLED_HOVER : FACE_DISABLED);

            graphics.fill(x, y, x + FACE_SIZE, y + FACE_SIZE, color);
            graphics.outline(x, y, FACE_SIZE, FACE_SIZE, hovered ? 0xFFFFFFFF : 0xFFCBD1D8);
            graphics.centeredText(font, faceSymbol(face), x + FACE_SIZE / 2, y + 4, 0xFFFFFFFF);
            graphics.centeredText(font, enabled ? "ON" : "OFF", x + FACE_SIZE / 2, y + 19, 0xFFFFFFFF);
        }
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

    private static String pretty(String value) {
        String lower = value.toLowerCase(Locale.ROOT).replace('_', ' ');
        return lower.isEmpty() ? lower : Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        graphics.fillGradient(0, 0, width, height, 0xFF202329, 0xFF101215);
        ResponsiveUi.push(graphics, width, height, PANEL_WIDTH, PANEL_HEIGHT);
        graphics.fill(left - 3, top - 3, left + PANEL_WIDTH + 3, top + PANEL_HEIGHT + 3, 0xAA050607);
        graphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, 0xEE292D32);
        graphics.outline(left, top, PANEL_WIDTH, PANEL_HEIGHT, 0xFF555B62);
        graphics.fill(left + 1, top + 1, left + PANEL_WIDTH - 1, top + 38, 0xFF202428);
        graphics.pose().popMatrix();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
        ResponsiveUi.push(graphics, width, height, PANEL_WIDTH, PANEL_HEIGHT);
        int logicalMouseX = ResponsiveUi.toLogicalX(mouseX, width, height, PANEL_WIDTH, PANEL_HEIGHT);
        int logicalMouseY = ResponsiveUi.toLogicalY(mouseY, width, height, PANEL_WIDTH, PANEL_HEIGHT);

        graphics.centeredText(font,
                page == Page.HITBOX ? "HITBOX ADHESION" : "ADVANCED ADHESION",
                left + PANEL_WIDTH / 2, top + 14, 0xFFFFFFFF);
        graphics.text(font, "Block: " + blockId, left + 22, top + 52, 0xFFBBC3CC);
        graphics.text(font, "Geometry source", left + 28, top + 93, 0xFFE5E8EB);
        graphics.text(font, "Minimum contact square", left + 28, top + 130, 0xFFE5E8EB);
        graphics.text(font, "Inward depth (voxels)", left + 28, top + 167, 0xFFE5E8EB);

        if (page == Page.HITBOX) {
            graphics.text(font, "16 × 16 face grid  •  largest fully occupied square  •  green = enabled",
                    left + 28, top + 204, 0xFF9FA8B2);
        } else {
            graphics.text(font, "Orientation handling", left + 28, top + 204, 0xFFE5E8EB);
            graphics.text(font, "Lead direction", left + 28, top + 241, 0xFFE5E8EB);
            graphics.text(font, "Manual face overrides", left + 28, top + 267, 0xFFBBC3CC);
            graphics.text(font, "Green = adhesion enabled     Red = adhesion disabled",
                    left + 28, top + 278, 0xFF9FA8B2);
        }

        super.extractRenderState(graphics, logicalMouseX, logicalMouseY, a);

        drawValue(graphics, left + 296, top + 129, Integer.toString(config.minimumSquare));
        drawValue(graphics, left + 296, top + 166, Integer.toString(config.depth));

        if (page == Page.ADVANCED) {
            drawCubeNet(graphics, logicalMouseX, logicalMouseY);
            graphics.text(font, "F Front   B Back   L Left   R Right   U Up   D Down",
                    left + 28, top + 402, 0xFF9FA8B2);
        } else {
            graphics.text(font, "Settings save automatically for this block.",
                    left + 28, top + 230, 0xFF9FA8B2);
        }
        graphics.pose().popMatrix();
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
