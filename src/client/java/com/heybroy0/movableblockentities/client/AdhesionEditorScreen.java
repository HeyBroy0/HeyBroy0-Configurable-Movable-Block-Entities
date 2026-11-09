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

    private static final int PANEL_WIDTH = 520;
    private static final int PANEL_HEIGHT = 350;
    private static final int FACE_SIZE = 34;
    private static final int FACE_GAP = 4;
    private static final int NET_WIDTH = FACE_SIZE * 4 + FACE_GAP * 3;
    private static final int NET_LEFT = PANEL_WIDTH - NET_WIDTH - 24;
    private static final int NET_TOP = 145;

    private static final int FACE_ENABLED = 0xFF2F8F58;
    private static final int FACE_DISABLED = 0xFF963F45;
    private static final int FACE_ENABLED_HOVER = 0xFF43AA6A;
    private static final int FACE_DISABLED_HOVER = 0xFFB24E55;

    private final String blockId;
    private final String entryId;
    private final Page page;
    private final BlockConfig config;
    private final java.util.List<String> focusEntryIds;
    private int left;
    private int top;
    private Button backButton;
    private Button doneButton;
    private Button geometryButton;
    private Button minimumMinusButton;
    private Button minimumPlusButton;
    private Button depthMinusButton;
    private Button depthPlusButton;
    private Button orientationButton;
    private Button leadButton;

    public AdhesionEditorScreen(String blockId, Page page) {
        this(blockId, null, page, java.util.List.of());
    }

    public AdhesionEditorScreen(String blockId, String entryId, Page page) {
        this(blockId, entryId, page, java.util.List.of());
    }

    public AdhesionEditorScreen(String blockId, String entryId, Page page,
                                java.util.List<String> focusEntryIds) {
        super(Component.literal(page == Page.HITBOX ? "Hitbox Adhesion Settings" : "Advanced Adhesion Settings"));
        this.blockId = blockId;
        this.entryId = entryId;
        this.page = page;
        this.focusEntryIds = focusEntryIds == null ? java.util.List.of() : java.util.List.copyOf(focusEntryIds);
        this.config = entryId == null ? ConfigStore.get(blockId) : ConfigStore.get(blockId, entryId);
    }

    @Override
    protected void init() {
        clearWidgets();
        left = 0;
        top = 0;

        backButton = addButton("", 12, 8, 72, 22, button -> returnToMain());
        doneButton = addButton("", PANEL_WIDTH - 84, 8, 72, 22, button -> returnToMain());

        int controlX = 190;
        int controlWidth = page == Page.ADVANCED ? 132 : 220;
        geometryButton = addButton("", left + controlX, top + 86, controlWidth, 22, button -> {
            GeometrySource[] values = GeometrySource.values();
            config.geometrySource = values[(config.geometrySource.ordinal() + 1) % values.length];
            saveAndRefresh();
        });

        minimumMinusButton = addButton("", left + controlX, top + 123, 28, 22, button -> {
            config.minimumSquare = Math.max(1, config.minimumSquare - 1);
            saveAndRefresh();
        });
        minimumPlusButton = addButton("", left + controlX + controlWidth - 28, top + 123, 28, 22, button -> {
            config.minimumSquare = Math.min(16, config.minimumSquare + 1);
            saveAndRefresh();
        });

        depthMinusButton = addButton("", left + controlX, top + 160, 28, 22, button -> {
            config.depth = Math.max(1, config.depth - 1);
            saveAndRefresh();
        });
        depthPlusButton = addButton("", left + controlX + controlWidth - 28, top + 160, 28, 22, button -> {
            config.depth = Math.min(16, config.depth + 1);
            saveAndRefresh();
        });

        if (page == Page.ADVANCED) {
            orientationButton = addButton("", left + controlX, top + 197, controlWidth, 22, button -> {
                Orientation[] values = Orientation.values();
                config.orientation = values[(config.orientation.ordinal() + 1) % values.length];
                saveAndRefresh();
            });
            leadButton = addButton("", left + controlX, top + 234, controlWidth, 22, button -> {
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
        return addRenderableWidget(Button.builder(Component.empty(), action).bounds(x, y, w, h).build());
    }

    private void returnToMain() {
        minecraft.gui.setScreen(new MainConfigScreen(blockId, entryId));
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
                saveAndRefresh();
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
            ResponsiveUi.centeredText(graphics, font, faceSymbol(face), x + FACE_SIZE / 2, y + 4, 0xFFFFFFFF);
            ResponsiveUi.centeredText(graphics, font, enabled ? "ON" : "OFF", x + FACE_SIZE / 2, y + 19, 0xFFFFFFFF);
        }
    }

    private void saveAndRefresh() {
        if (!focusEntryIds.isEmpty()) ConfigStore.applyConfigToEntries(config, focusEntryIds);
        else ConfigStore.save();
        refreshLabels();
    }

    private void refreshLabels() {
        if (geometryButton == null) return;
        geometryButton.setMessage(Component.empty());
        if (orientationButton != null) {
            orientationButton.setMessage(Component.empty());
        }
        if (leadButton != null) {
            leadButton.setMessage(Component.empty());
        }
    }

    private static String pretty(String value) {
        String lower = value.toLowerCase(Locale.ROOT).replace('_', ' ');
        return lower.isEmpty() ? lower : Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
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

        ResponsiveUi.centeredText(graphics, font,
                page == Page.HITBOX ? "HITBOX ADHESION" : "ADVANCED ADHESION",
                left + PANEL_WIDTH / 2, top + 14, 0xFFFFFFFF);
        ResponsiveUi.text(graphics, font, "Block: " + blockId, left + 22, top + 52, 0xFFBBC3CC);
        ResponsiveUi.text(graphics, font, "Geometry source", left + 28, top + 93, 0xFFE5E8EB);
        ResponsiveUi.text(graphics, font, "Minimum contact square", left + 28, top + 130, 0xFFE5E8EB);
        ResponsiveUi.text(graphics, font, "Inward depth (voxels)", left + 28, top + 167, 0xFFE5E8EB);

        if (page == Page.HITBOX) {
            ResponsiveUi.text(graphics, font, "16 × 16 face grid  •  largest fully occupied square  •  green = enabled",
                    left + 28, top + 204, 0xFF9FA8B2);
        } else {
            ResponsiveUi.text(graphics, font, "Orientation handling", left + 28, top + 204, 0xFFE5E8EB);
            ResponsiveUi.text(graphics, font, "Lead direction", left + 28, top + 241, 0xFFE5E8EB);
            ResponsiveUi.text(graphics, font, "Manual face overrides", left + 330, top + 139, 0xFFBBC3CC);
            ResponsiveUi.text(graphics, font, "Green = enabled  Red = disabled",
                    left + 330, top + 151, 0xFF9FA8B2);
        }

        super.extractRenderState(graphics, logicalMouseX, logicalMouseY, a);
        renderButtonLabels(graphics);

        int valueX = page == Page.ADVANCED ? left + 225 : left + 296;
        drawValue(graphics, valueX, top + 129, Integer.toString(config.minimumSquare));
        drawValue(graphics, valueX, top + 166, Integer.toString(config.depth));

        if (page == Page.ADVANCED) {
            drawCubeNet(graphics, logicalMouseX, logicalMouseY);
            ResponsiveUi.text(graphics, font, "F Front  B Back  L Left  R Right  U Up  D Down",
                    left + 330, top + 282, 0xFF9FA8B2);
        } else {
            ResponsiveUi.text(graphics, font, focusEntryIds.isEmpty()
                    ? "Settings save automatically for this block."
                    : "Settings apply to every block in the focused folder / selector.",
                    left + 28, top + 230, 0xFF9FA8B2);
        }
        graphics.pose().popMatrix();
    }

    private void renderButtonLabels(GuiGraphicsExtractor graphics) {
        drawButtonLabel(graphics, backButton, "Back");
        drawButtonLabel(graphics, doneButton, "Done");
        drawButtonLabel(graphics, geometryButton, "‹  " + pretty(config.geometrySource.name()) + "  ›");
        drawButtonLabel(graphics, minimumMinusButton, "−");
        drawButtonLabel(graphics, minimumPlusButton, "+");
        drawButtonLabel(graphics, depthMinusButton, "−");
        drawButtonLabel(graphics, depthPlusButton, "+");
        if (orientationButton != null) {
            drawButtonLabel(graphics, orientationButton, "‹  " + pretty(config.orientation.name()) + "  ›");
        }
        if (leadButton != null) {
            drawButtonLabel(graphics, leadButton, "‹  " + pretty(config.leadDirection) + "  ›");
        }
    }

    private void drawButtonLabel(GuiGraphicsExtractor graphics, Button button, String label) {
        if (button == null || !button.visible || label == null || label.isEmpty()) return;
        ResponsiveUi.centeredText(graphics, font, label,
                button.getX() + button.getWidth() / 2,
                button.getY() + Math.max(2, (button.getHeight() - 8) / 2),
                button.active ? 0xFFFFFFFF : 0xFF777777);
    }

    private void drawValue(GuiGraphicsExtractor graphics, int x, int y, String value) {
        int width = page == Page.ADVANCED ? 60 : 130;
        graphics.fill(x, y, x + width, y + 22, 0xFF1F2327);
        graphics.outline(x, y, width, 22, 0xFF545D66);
        ResponsiveUi.centeredText(graphics, font, value, x + width / 2, y + 7, 0xFFFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
