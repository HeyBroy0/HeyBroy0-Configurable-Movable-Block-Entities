package com.heybroy0.movableblockentities.client;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.TreeMap;
import java.util.UUID;

/** Client-side persistence for the GUI prototype. No piston hooks read this yet. */
public final class ConfigStore {
    private static final Map<String, BlockConfig> BLOCKS = new TreeMap<>();
    private static final Map<String, TreeEntry> TREE_ENTRIES = new LinkedHashMap<>();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir()
            .resolve("movable-block-entities")
            .resolve("settings.properties");
    private static boolean loaded;

    private ConfigStore() {}

    public static synchronized void load() {
        if (loaded) return;
        loaded = true;
        if (!Files.exists(FILE)) return;

        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(FILE)) {
            properties.load(input);
        } catch (IOException exception) {
            System.err.println("[Movable Block Entities] Could not read settings: " + exception.getMessage());
            return;
        }

        for (String property : properties.stringPropertyNames()) {
            if (!property.startsWith("block.") || !property.endsWith(".id")) continue;
            String encoded = property.substring("block.".length(), property.length() - ".id".length());
            String id = properties.getProperty(property, "");
            if (id.isBlank()) continue;

            String prefix = "block." + encoded + ".";
            BlockConfig config = new BlockConfig();
            config.movement = readEnum(properties, prefix + "movement", Movement.class, Movement.VANILLA);
            config.scheduledTicks = readEnum(properties, prefix + "scheduledTicks", ScheduledTicks.class, ScheduledTicks.VANILLA);
            config.pushCost = Math.max(1, readInt(properties, prefix + "pushCost", 1));
            config.adhesion = readEnum(properties, prefix + "adhesion", Adhesion.class, Adhesion.VANILLA);
            config.geometrySource = readEnum(properties, prefix + "geometrySource", GeometrySource.class, GeometrySource.OUTLINE);
            config.minimumSquare = clamp(readInt(properties, prefix + "minimumSquare", 3), 1, 16);
            config.depth = clamp(readInt(properties, prefix + "depth", 2), 1, 16);
            config.orientation = readEnum(properties, prefix + "orientation", Orientation.class, Orientation.AUTO);
            config.leadDirection = properties.getProperty(prefix + "leadDirection", "AUTO");
            for (Face face : Face.values()) {
                config.faces.put(face, Boolean.parseBoolean(properties.getProperty(prefix + "face." + face.name(), "false")));
            }
            BLOCKS.put(id, config);
        }

        for (String property : properties.stringPropertyNames()) {
            if (!property.startsWith("tree.") || !property.endsWith(".id")) continue;
            String encoded = property.substring("tree.".length(), property.length() - ".id".length());
            String prefix = "tree." + encoded + ".";
            String id = properties.getProperty(property, "");
            if (id.isBlank()) continue;
            TreeEntryType type = readEnum(properties, prefix + "type", TreeEntryType.class, TreeEntryType.FOLDER);
            String name = properties.getProperty(prefix + "name", id);
            String value = properties.getProperty(prefix + "value", "");
            String parent = properties.getProperty(prefix + "parent", "");
            int order = readInt(properties, prefix + "order", 0);
            boolean expanded = Boolean.parseBoolean(properties.getProperty(prefix + "expanded", "false"));
            TREE_ENTRIES.put(id, new TreeEntry(id, type, name, value,
                    parent.isBlank() ? null : parent, order, expanded));
        }
    }

    public static synchronized BlockConfig get(String blockId) {
        load();
        return BLOCKS.computeIfAbsent(blockId, ignored -> new BlockConfig());
    }

    public static synchronized List<String> changedBlockIds() {
        load();
        List<String> result = new ArrayList<>();
        BLOCKS.forEach((id, config) -> {
            if (config.isChanged()) result.add(id);
        });
        result.sort(Comparator.naturalOrder());
        return result;
    }

    /** Returns the active block-tag ids, matched against their full namespaced id. */
    public static synchronized List<String> blockTagIds(String filter) {
        String query = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
        return BuiltInRegistries.BLOCK.getTags()
                .map(tag -> tag.key().location().toString())
                .filter(id -> query.isEmpty() || id.toLowerCase(Locale.ROOT).contains(query))
                .sorted()
                .toList();
    }

    public static synchronized boolean isBlockTag(String tagId) {
        String normalized = normalizeTagId(tagId);
        if (normalized == null) return false;
        return BuiltInRegistries.BLOCK.getTags()
                .anyMatch(tag -> tag.key().location().toString().equals(normalized));
    }

    /** The current block members of a selector. Membership is resolved from loaded registry tags. */
    public static synchronized List<String> blockTagMembers(String tagId) {
        String normalized = normalizeTagId(tagId);
        if (normalized == null) return List.of();
        List<String> result = new ArrayList<>();
        BuiltInRegistries.BLOCK.getTags()
                .filter(tag -> tag.key().location().toString().equals(normalized))
                .findFirst()
                .ifPresent(tag -> tag.forEach(holder -> {
                    Identifier key = BuiltInRegistries.BLOCK.getKey(holder.value());
                    if (key != null) result.add(key.toString());
                }));
        result.sort(Comparator.naturalOrder());
        return List.copyOf(result);
    }

    private static String normalizeTagId(String tagId) {
        if (tagId == null) return null;
        String value = tagId.trim();
        if (value.startsWith("#")) value = value.substring(1);
        Identifier id = Identifier.tryParse(value);
        return id == null ? null : id.toString();
    }

    public static synchronized List<TreeEntry> rootEntries() {
        load();
        List<TreeEntry> result = new ArrayList<>();
        for (TreeEntry entry : TREE_ENTRIES.values()) {
            if (entry.parentId() == null) result.add(entry);
        }

        // User-created folders/selectors take their saved order. A block appears at root only
        // when it is not already explicitly placed in a folder and is not covered by a selector.
        java.util.Set<String> explicitlyPlacedBlocks = new java.util.HashSet<>();
        java.util.Set<String> selectorMembers = new java.util.HashSet<>();
        for (TreeEntry entry : TREE_ENTRIES.values()) {
            if (entry.type() == TreeEntryType.BLOCK) explicitlyPlacedBlocks.add(entry.value());
            if (entry.type() == TreeEntryType.SELECTOR) selectorMembers.addAll(blockTagMembers(entry.value()));
        }
        int generatedOrder = 1_000_000;
        for (String blockId : changedBlockIds()) {
            if (explicitlyPlacedBlocks.contains(blockId) || selectorMembers.contains(blockId)) continue;
            String entryId = blockEntryId(blockId);
            if (TREE_ENTRIES.containsKey(entryId)) continue;
            result.add(new TreeEntry(entryId, TreeEntryType.BLOCK, blockId, blockId,
                    null, generatedOrder++, false));
        }
        result.sort(Comparator.comparingInt(TreeEntry::order).thenComparing(TreeEntry::name));
        return List.copyOf(result);
    }

    public static synchronized List<TreeEntry> childrenOf(TreeEntry parent) {
        load();
        if (parent == null || parent.type() == TreeEntryType.BLOCK) return List.of();

        List<TreeEntry> result = new ArrayList<>();
        if (parent.type() == TreeEntryType.SELECTOR) {
            int order = 0;
            for (String blockId : blockTagMembers(parent.value())) {
                result.add(new TreeEntry(tagBlockEntryId(parent.id(), blockId),
                        TreeEntryType.BLOCK, blockId, blockId, parent.id(), order++, false));
            }
        }
        for (TreeEntry entry : TREE_ENTRIES.values()) {
            if (Objects.equals(entry.parentId(), parent.id())) result.add(entry);
        }
        result.sort(Comparator.comparingInt(TreeEntry::order).thenComparing(TreeEntry::name));
        return List.copyOf(result);
    }

    public static synchronized TreeEntry findTreeEntry(String entryId) {
        load();
        return TREE_ENTRIES.get(entryId);
    }

    public static synchronized boolean isUserTreeEntry(String entryId) {
        load();
        TreeEntry entry = TREE_ENTRIES.get(entryId);
        return entry != null && (entry.type() == TreeEntryType.FOLDER || entry.type() == TreeEntryType.SELECTOR);
    }

    public static synchronized TreeEntry addTagSelector(String tagId, String requestedName, String parentId) {
        load();
        String normalized = normalizeTagId(tagId);
        if (normalized == null || !isBlockTag(normalized)) return null;
        String safeParent = folderParentOrNull(parentId);
        for (TreeEntry existing : TREE_ENTRIES.values()) {
            if (existing.type() == TreeEntryType.SELECTOR
                    && existing.value().equals(normalized)
                    && Objects.equals(existing.parentId(), safeParent)) return existing;
        }
        String id = "node-" + UUID.randomUUID();
        String name = requestedName == null || requestedName.isBlank() ? "#" + normalized : requestedName.trim();
        TreeEntry entry = new TreeEntry(id, TreeEntryType.SELECTOR, name, normalized,
                safeParent, nextOrder(safeParent), false);
        TREE_ENTRIES.put(id, entry);
        save();
        return entry;
    }

    public static synchronized TreeEntry createFolder(String name, String parentId, List<String> selectedEntryIds) {
        load();
        String safeParent = folderParentOrNull(parentId);
        String id = "node-" + UUID.randomUUID();
        String safeName = name == null || name.isBlank() ? "New folder" : name.trim();
        TreeEntry folder = new TreeEntry(id, TreeEntryType.FOLDER, safeName, "",
                safeParent, nextOrder(safeParent), true);
        TREE_ENTRIES.put(id, folder);

        if (selectedEntryIds != null) {
            for (String selectedId : selectedEntryIds) {
                if (selectedId == null || selectedId.isBlank() || selectedId.equals(id)) continue;
                TreeEntry existing = TREE_ENTRIES.get(selectedId);
                if (existing != null) {
                    if (!Objects.equals(existing.parentId(), id) && !isDescendantOf(id, existing.id())) {
                        TREE_ENTRIES.put(existing.id(), existing.withParent(id, nextOrder(id)));
                    }
                    continue;
                }
                String blockId = blockIdFromEntryId(selectedId);
                if (blockId != null) moveBlockNode(blockId, id);
            }
        }
        save();
        return folder;
    }

    public static synchronized TreeEntry addBlockSelector(String tagId, String parentId) {
        return addTagSelector(tagId, null, parentId);
    }

    private static void moveBlockNode(String blockId, String parentId) {
        TreeEntry existing = TREE_ENTRIES.values().stream()
                .filter(entry -> entry.type() == TreeEntryType.BLOCK && entry.value().equals(blockId))
                .findFirst().orElse(null);
        if (existing != null) {
            TREE_ENTRIES.put(existing.id(), existing.withParent(parentId, nextOrder(parentId)));
        } else {
            String id = "node-" + UUID.randomUUID();
            TREE_ENTRIES.put(id, new TreeEntry(id, TreeEntryType.BLOCK, blockId, blockId,
                    parentId, nextOrder(parentId), false));
        }
    }

    private static String blockIdFromEntryId(String entryId) {
        if (entryId.startsWith("block|")) return entryId.substring("block|".length());
        if (entryId.startsWith("tagblock|")) {
            int separator = entryId.indexOf('|', "tagblock|".length());
            return separator < 0 ? null : entryId.substring(separator + 1);
        }
        return null;
    }

    public static synchronized void renameTreeEntry(String entryId, String name) {
        load();
        TreeEntry entry = TREE_ENTRIES.get(entryId);
        if (entry == null || (entry.type() != TreeEntryType.FOLDER && entry.type() != TreeEntryType.SELECTOR)
                || name == null || name.isBlank()) return;
        TREE_ENTRIES.put(entryId, entry.withName(name.trim()));
        save();
    }

    public static synchronized void toggleExpanded(String entryId) {
        load();
        TreeEntry entry = TREE_ENTRIES.get(entryId);
        if (entry == null || entry.type() == TreeEntryType.BLOCK) return;
        TREE_ENTRIES.put(entryId, entry.withExpanded(!entry.expanded()));
        save();
    }

    private static boolean isDescendantOf(String possibleAncestorId, String entryId) {
        TreeEntry cursor = TREE_ENTRIES.get(entryId);
        while (cursor != null && cursor.parentId() != null) {
            if (cursor.parentId().equals(possibleAncestorId)) return true;
            cursor = TREE_ENTRIES.get(cursor.parentId());
        }
        return false;
    }

    private static String folderParentOrNull(String parentId) {
        if (parentId == null) return null;
        TreeEntry parent = TREE_ENTRIES.get(parentId);
        return parent != null && parent.type() == TreeEntryType.FOLDER ? parent.id() : null;
    }

    private static int nextOrder(String parentId) {
        return TREE_ENTRIES.values().stream()
                .filter(entry -> Objects.equals(entry.parentId(), parentId))
                .mapToInt(TreeEntry::order).max().orElse(-1) + 1;
    }

    private static String blockEntryId(String blockId) {
        return "block|" + blockId;
    }

    private static String tagBlockEntryId(String selectorId, String blockId) {
        return "tagblock|" + selectorId + "|" + blockId;
    }

    public static synchronized void revert(String blockId) {
        load();
        BLOCKS.remove(blockId);
        TREE_ENTRIES.values().removeIf(entry ->
                entry.type() == TreeEntryType.BLOCK && entry.value().equals(blockId));
        save();
    }

    public static synchronized void save() {
        load();
        Properties properties = new Properties();
        properties.setProperty("format.version", "2");
        BLOCKS.forEach((id, config) -> {
            if (!config.isChanged()) return;
            String encoded = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(id.getBytes(StandardCharsets.UTF_8));
            String prefix = "block." + encoded + ".";
            properties.setProperty(prefix + "id", id);
            properties.setProperty(prefix + "movement", config.movement.name());
            properties.setProperty(prefix + "scheduledTicks", config.scheduledTicks.name());
            properties.setProperty(prefix + "pushCost", Integer.toString(config.pushCost));
            properties.setProperty(prefix + "adhesion", config.adhesion.name());
            properties.setProperty(prefix + "geometrySource", config.geometrySource.name());
            properties.setProperty(prefix + "minimumSquare", Integer.toString(config.minimumSquare));
            properties.setProperty(prefix + "depth", Integer.toString(config.depth));
            properties.setProperty(prefix + "orientation", config.orientation.name());
            properties.setProperty(prefix + "leadDirection", config.leadDirection);
            config.faces.forEach((face, enabled) ->
                    properties.setProperty(prefix + "face." + face.name(), Boolean.toString(enabled)));
        });

        TREE_ENTRIES.values().forEach(entry -> {
            String encoded = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(entry.id().getBytes(StandardCharsets.UTF_8));
            String prefix = "tree." + encoded + ".";
            properties.setProperty(prefix + "id", entry.id());
            properties.setProperty(prefix + "type", entry.type().name());
            properties.setProperty(prefix + "name", entry.name());
            properties.setProperty(prefix + "value", entry.value());
            properties.setProperty(prefix + "parent", entry.parentId() == null ? "" : entry.parentId());
            properties.setProperty(prefix + "order", Integer.toString(entry.order()));
            properties.setProperty(prefix + "expanded", Boolean.toString(entry.expanded()));
        });

        try {
            Files.createDirectories(FILE.getParent());
            Path temporary = FILE.resolveSibling(FILE.getFileName() + ".tmp");
            try (OutputStream output = Files.newOutputStream(temporary)) {
                properties.store(output, "Configurable Movable Block Entities - GUI prototype (format v2)");
            }
            try {
                Files.move(temporary, FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, FILE, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            System.err.println("[Movable Block Entities] Could not save settings: " + exception.getMessage());
        }
    }

    private static int readInt(Properties properties, String key, int fallback) {
        try { return Integer.parseInt(properties.getProperty(key, Integer.toString(fallback))); }
        catch (NumberFormatException ignored) { return fallback; }
    }

    private static <T extends Enum<T>> T readEnum(Properties properties, String key, Class<T> type, T fallback) {
        try { return Enum.valueOf(type, properties.getProperty(key, fallback.name()).toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { return fallback; }
    }

    private static int clamp(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }

    public enum TreeEntryType { FOLDER, SELECTOR, BLOCK }

    public record TreeEntry(String id, TreeEntryType type, String name, String value,
                            String parentId, int order, boolean expanded) {
        public TreeEntry withParent(String parent, int newOrder) {
            return new TreeEntry(id, type, name, value, parent, newOrder, expanded);
        }
        public TreeEntry withName(String newName) {
            return new TreeEntry(id, type, newName, value, parentId, order, expanded);
        }
        public TreeEntry withExpanded(boolean value) {
            return new TreeEntry(id, type, name, this.value, parentId, order, value);
        }
    }

    public enum Movement { VANILLA, MOVABLE, BREAKABLE, IMMOVABLE }
    public enum ScheduledTicks { VANILLA, TRANSFER, DUPLICATE }
    public enum Adhesion { VANILLA, ADHESIVE, GLAZED, HITBOX, ADVANCED }
    public enum GeometrySource { OUTLINE, COLLISION, VISUAL }
    public enum Orientation { AUTO, MANUAL, OMNIDIRECTIONAL }
    public enum Face { FRONT, BACK, LEFT, RIGHT, UP, DOWN }

    public static final class BlockConfig {
        public Movement movement = Movement.VANILLA;
        public ScheduledTicks scheduledTicks = ScheduledTicks.VANILLA;
        public int pushCost = 1;
        public Adhesion adhesion = Adhesion.VANILLA;
        public GeometrySource geometrySource = GeometrySource.OUTLINE;
        public int minimumSquare = 3;
        public int depth = 2;
        public Orientation orientation = Orientation.AUTO;
        public String leadDirection = "AUTO";
        public final Map<Face, Boolean> faces = new TreeMap<>();

        public BlockConfig() {
            for (Face face : Face.values()) faces.put(face, false);
        }

        public boolean isChanged() {
            if (movement != Movement.VANILLA || scheduledTicks != ScheduledTicks.VANILLA || pushCost != 1
                    || adhesion != Adhesion.VANILLA || geometrySource != GeometrySource.OUTLINE
                    || minimumSquare != 3 || depth != 2 || orientation != Orientation.AUTO
                    || !"AUTO".equals(leadDirection)) return true;
            return faces.values().stream().anyMatch(Boolean.TRUE::equals);
        }
    }
}
