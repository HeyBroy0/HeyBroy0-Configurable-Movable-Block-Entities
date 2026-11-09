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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

/** Client-side persistence for the GUI prototype. No piston hooks read this yet. */
public final class ConfigStore {
    private static final Map<String, BlockConfig> BLOCKS = new TreeMap<>();
    private static final Map<String, TreeEntry> TREE_ENTRIES = new LinkedHashMap<>();
    private static volatile Map<String, EffectiveBlockConfig> BEHAVIOR_CACHE = Map.of();
    private static volatile long behaviorCacheGeneration;
    private static final EffectiveBlockConfig DEFAULT_BEHAVIOR_CONFIG =
            new BlockConfig().snapshot(null);
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
            config.blockId = id;
            String configKey = decodeConfigKey(encoded, id);
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
            BLOCKS.put(configKey, config);
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

    private static String decodeConfigKey(String encoded, String fallback) {
        try {
            return new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    public static synchronized BlockConfig get(String blockId) {
        load();
        BlockConfig config = BLOCKS.computeIfAbsent(blockId, ignored -> new BlockConfig());
        config.blockId = blockId;
        return config;
    }

    /** Gets settings belonging to one visible occurrence of a block, not every occurrence. */
    public static synchronized BlockConfig get(String blockId, String entryId) {
        load();
        if (entryId == null || entryId.isBlank()) return get(blockId);

        BlockConfig config = BLOCKS.get(entryId);
        if (config == null) {
            config = new BlockConfig();
            TreeEntry topmost = findTopmostBlockEntry(blockId);
            // Migrate an old per-block config only into the occurrence that has precedence.
            if (topmost != null && topmost.id().equals(entryId)) {
                BlockConfig legacy = BLOCKS.remove(blockId);
                if (legacy != null) config = legacy;
            }
            config.blockId = blockId;
            BLOCKS.put(entryId, config);
        } else {
            config.blockId = blockId;
        }
        return config;
    }

    /** Returns the topmost occurrence's settings, falling back to the legacy block-wide record. */
    public static synchronized BlockConfig getEffective(String blockId) {
        TreeEntry topmost = findTopmostBlockEntry(blockId);
        return topmost == null ? get(blockId) : get(blockId, topmost.id());
    }

    public static synchronized List<String> changedBlockIds() {
        load();
        TreeSet<String> result = new TreeSet<>();
        BLOCKS.values().forEach(config -> {
            if (config.isChanged() && config.blockId != null && !config.blockId.isBlank()) {
                result.add(config.blockId);
            }
        });
        return List.copyOf(result);
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
            java.util.Set<String> explicitMembers = new java.util.HashSet<>();
            for (TreeEntry entry : TREE_ENTRIES.values()) {
                if (entry.type() == TreeEntryType.BLOCK && Objects.equals(entry.parentId(), parent.id())) {
                    explicitMembers.add(entry.value());
                }
            }
            int order = 1_000_000;
            for (String blockId : blockTagMembers(parent.value())) {
                // A materialized occurrence keeps its own identity/order and replaces only
                // the generated row for this exact selector occurrence.
                if (!explicitMembers.contains(blockId)) {
                    result.add(new TreeEntry(tagBlockEntryId(parent.id(), blockId),
                            TreeEntryType.BLOCK, blockId, blockId, parent.id(), order++, false));
                }
            }
        }
        for (TreeEntry entry : TREE_ENTRIES.values()) {
            if (Objects.equals(entry.parentId(), parent.id())) result.add(entry);
        }
        result.sort(Comparator.comparingInt(TreeEntry::order)
                .thenComparing(TreeEntry::name).thenComparing(TreeEntry::id));
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
                safeParent, topOrder(safeParent), false);
        TREE_ENTRIES.put(id, entry);
        save();
        return entry;
    }

    public static synchronized TreeEntry createFolder(String name, String parentId, List<String> selectedEntryIds) {
        load();
        String safeParent = containerParentOrNull(parentId);
        String id = "node-" + UUID.randomUUID();
        String safeName = name == null || name.isBlank() ? "New folder" : name.trim();
        TreeEntry folder = new TreeEntry(id, TreeEntryType.FOLDER, safeName, "",
                safeParent, topOrder(safeParent), true);
        TREE_ENTRIES.put(id, folder);

        java.util.Set<String> selected = selectedEntryIds == null
                ? java.util.Set.of() : new java.util.LinkedHashSet<>(selectedEntryIds);
        if (!selected.isEmpty()) {
            for (String selectedId : selected) {
                if (selectedId == null || selectedId.isBlank() || selectedId.equals(id)) continue;
                TreeEntry existing = TREE_ENTRIES.get(selectedId);
                if (existing != null && selectedId.startsWith("tagblock|")
                        && existing.type() == TreeEntryType.BLOCK) {
                    String selectorId = existing.parentId();
                    // A selector membership is a distinct occurrence. Put a copy in the folder
                    // while preserving the selector's live member and its own settings.
                    if (selectorId == null || !selected.contains(selectorId)) {
                        copyBlockNode(existing.value(), existing.id(), id);
                    }
                    continue;
                }
                if (existing != null) {
                    // If both a container and its descendant are selected, moving the container
                    // carries the descendant with it. Do not detach the descendant as a sibling.
                    if (hasSelectedAncestor(existing, selected)) continue;
                    if (!Objects.equals(existing.parentId(), id)) {
                        TREE_ENTRIES.put(existing.id(), existing.withParent(id, nextOrder(id)));
                    }
                    continue;
                }

                String blockId = blockIdFromEntryId(selectedId);
                if (blockId != null) {
                    String selectorId = tagBlockParentFromEntryId(selectedId);
                    if (selectorId != null && selected.contains(selectorId)) continue;
                    // A generated selector row is copied; a generated root row is moved.
                    copyBlockNode(blockId, selectedId, id);
                }
            }
        }
        save();
        return folder;
    }

    public static synchronized TreeEntry addBlockSelector(String tagId, String parentId) {
        return addTagSelector(tagId, null, parentId);
    }

    private static void copyBlockNode(String blockId, String sourceEntryId, String parentId) {
        String id = "node-" + UUID.randomUUID();
        TREE_ENTRIES.put(id, new TreeEntry(id, TreeEntryType.BLOCK, blockId, blockId,
                parentId, nextOrder(parentId), false));

        BlockConfig source = BLOCKS.get(sourceEntryId);
        if (sourceEntryId.startsWith("block|") && source != null) {
            // Moving an implicit root row into a folder transfers its own settings.
            BLOCKS.remove(sourceEntryId);
            BLOCKS.put(id, source.copyFor(blockId));
            return;
        }
        if (source == null) {
            TreeEntry topmost = findTopmostBlockEntry(blockId);
            // Claim a legacy block-wide record only when this newly placed occurrence
            // now has precedence. Lower-priority duplicates never inherit another copy's settings.
            if (topmost != null && topmost.id().equals(id)) {
                source = BLOCKS.remove(blockId);
            }
        }
        if (source != null) BLOCKS.put(id, source.copyFor(blockId));
    }

    private static String tagBlockParentFromEntryId(String entryId) {
        if (entryId == null || !entryId.startsWith("tagblock|")) return null;
        int separator = entryId.indexOf('|', "tagblock|".length());
        return separator < 0 ? null : entryId.substring("tagblock|".length(), separator);
    }

    private static boolean hasSelectedAncestor(TreeEntry entry, java.util.Set<String> selected) {
        String parentId = entry.parentId();
        while (parentId != null) {
            if (selected.contains(parentId)) return true;
            TreeEntry parent = TREE_ENTRIES.get(parentId);
            parentId = parent == null ? null : parent.parentId();
        }
        return false;
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
        TREE_ENTRIES.put(entryId, moveToTop(entry.withName(name.trim())));
        save();
    }

    public static synchronized void toggleExpanded(String entryId) {
        load();
        TreeEntry entry = TREE_ENTRIES.get(entryId);
        if (entry == null || entry.type() == TreeEntryType.BLOCK) return;
        TREE_ENTRIES.put(entryId, moveToTop(entry.withExpanded(!entry.expanded())));
        save();
    }

    /** Materializes and moves one exact occurrence to the top of its own directory. */
    public static synchronized TreeEntry touchTreeEntry(String entryId, String blockId) {
        load();
        TreeEntry entry = TREE_ENTRIES.get(entryId);
        if (entry == null && blockId != null) {
            String parentId = null;
            if (entryId != null && entryId.startsWith("tagblock|")) {
                int separator = entryId.indexOf('|', "tagblock|".length());
                if (separator > 0) {
                    parentId = entryId.substring("tagblock|".length(), separator);
                    TreeEntry parent = TREE_ENTRIES.get(parentId);
                    if (parent == null || parent.type() != TreeEntryType.SELECTOR) return null;
                }
            } else if (entryId == null || !entryId.startsWith("block|")) {
                return null;
            }
            String safeId = entryId == null ? "node-" + UUID.randomUUID() : entryId;
            entry = new TreeEntry(safeId, TreeEntryType.BLOCK, blockId, blockId,
                    parentId, topOrder(parentId), false);
            TREE_ENTRIES.put(safeId, entry);
        }
        if (entry == null) return null;

        TreeEntry top = moveToTop(entry);
        TREE_ENTRIES.put(top.id(), top);
        save();
        return top;
    }

    public static synchronized TreeEntry findTopmostBlockEntry(String blockId) {
        load();
        for (TreeEntry root : rootEntries()) {
            TreeEntry found = findTopmostBlockEntry(root, blockId);
            if (found != null) return found;
        }
        return null;
    }

    private static TreeEntry findTopmostBlockEntry(TreeEntry entry, String blockId) {
        if (entry.type() == TreeEntryType.BLOCK && entry.value().equals(blockId)) return entry;
        for (TreeEntry child : childrenOf(entry)) {
            TreeEntry found = findTopmostBlockEntry(child, blockId);
            if (found != null) return found;
        }
        return null;
    }

    private static TreeEntry moveToTop(TreeEntry entry) {
        int minimum = TREE_ENTRIES.values().stream()
                .filter(other -> Objects.equals(other.parentId(), entry.parentId()))
                .mapToInt(TreeEntry::order).min().orElse(entry.order());
        if (entry.order() <= minimum) return entry;
        return entry.withParent(entry.parentId(), minimum - 1);
    }

    private static int topOrder(String parentId) {
        return TREE_ENTRIES.values().stream()
                .filter(entry -> Objects.equals(entry.parentId(), parentId))
                .mapToInt(TreeEntry::order).min().orElse(0) - 1;
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

    private static String containerParentOrNull(String parentId) {
        if (parentId == null) return null;
        TreeEntry parent = TREE_ENTRIES.get(parentId);
        return parent != null && (parent.type() == TreeEntryType.FOLDER
                || parent.type() == TreeEntryType.SELECTOR) ? parent.id() : null;
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

    /**
     * Rebuilds the immutable, runtime-facing block behavior snapshot from the current ordered tree.
     * This is intentionally an explicit operation: runtime checks only perform a map lookup and
     * never traverse the user-editable hierarchy.
     */
    public static synchronized void rebuildBehaviorCache() {
        load();
        Map<String, EffectiveBlockConfig> rebuilt = new LinkedHashMap<>();
        for (TreeEntry root : rootEntries()) {
            collectEffectiveConfigs(root, rebuilt);
        }

        // Preserve old block-wide entries that do not yet have an occurrence in the tree.
        BLOCKS.forEach((key, config) -> {
            String blockId = config.blockId;
            if ((blockId == null || blockId.isBlank())
                    && !key.startsWith("block|") && !key.startsWith("tagblock|")
                    && !key.startsWith("node-")) {
                blockId = key;
            }
            if (blockId != null && !blockId.isBlank()) {
                rebuilt.putIfAbsent(blockId, config.snapshot(key));
            }
        });

        BEHAVIOR_CACHE = Collections.unmodifiableMap(rebuilt);
        behaviorCacheGeneration++;
    }

    private static void collectEffectiveConfigs(TreeEntry entry,
                                                Map<String, EffectiveBlockConfig> destination) {
        if (entry.type() == TreeEntryType.BLOCK) {
            BlockConfig config = BLOCKS.get(entry.id());
            if (config == null && !destination.containsKey(entry.value())) {
                // Legacy block-wide settings are inherited only by the first occurrence.
                config = BLOCKS.get(entry.value());
            }
            destination.putIfAbsent(entry.value(),
                    config == null ? DEFAULT_BEHAVIOR_CONFIG.withSource(entry.id())
                            : config.snapshot(entry.id()));
            return;
        }

        for (TreeEntry child : childrenOf(entry)) {
            collectEffectiveConfigs(child, destination);
        }
    }

    /** O(1) runtime lookup. The returned value is immutable and safe to read on tick threads. */
    public static EffectiveBlockConfig getRuntimeConfig(String blockId) {
        return BEHAVIOR_CACHE.getOrDefault(blockId, DEFAULT_BEHAVIOR_CONFIG);
    }

    public static long getBehaviorCacheGeneration() {
        return behaviorCacheGeneration;
    }

    /** Reverts one occurrence without deleting any other copy of the same block. */
    public static synchronized void revertInstance(String blockId, String entryId) {
        load();
        if (entryId != null && !entryId.isBlank()) {
            BlockConfig removed = BLOCKS.remove(entryId);
            if (removed == null) {
                TreeEntry topmost = findTopmostBlockEntry(blockId);
                if (topmost != null && topmost.id().equals(entryId)) BLOCKS.remove(blockId);
            }
        } else {
            BLOCKS.remove(blockId);
        }
        save();
    }

    /** Legacy block-wide reset; retained for callers predating instance-scoped settings. */
    public static synchronized void revert(String blockId) {
        revertInstance(blockId, blockId);
    }

    public static synchronized void save() {
        load();
        // Persist implicit root occurrences only after they acquire non-default settings.
        for (Map.Entry<String, BlockConfig> block : BLOCKS.entrySet()) {
            String entryId = block.getKey();
            BlockConfig config = block.getValue();
            if (!config.isChanged() || !entryId.startsWith("block|") || TREE_ENTRIES.containsKey(entryId)) {
                continue;
            }
            String blockId = config.blockId;
            if (blockId != null && !blockId.isBlank()) {
                TREE_ENTRIES.put(entryId, new TreeEntry(entryId, TreeEntryType.BLOCK,
                        blockId, blockId, null, topOrder(null), false));
            }
        }
        Properties properties = new Properties();
        properties.setProperty("format.version", "3");
        BLOCKS.forEach((id, config) -> {
            if (!config.isChanged()) return;
            String encoded = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(id.getBytes(StandardCharsets.UTF_8));
            String prefix = "block." + encoded + ".";
            properties.setProperty(prefix + "id", config.blockId == null ? id : config.blockId);
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
                properties.store(output, "Configurable Movable Block Entities - GUI prototype (format v3)");
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

    public record EffectiveBlockConfig(
            Movement movement,
            ScheduledTicks scheduledTicks,
            int pushCost,
            Adhesion adhesion,
            GeometrySource geometrySource,
            int minimumSquare,
            int depth,
            Orientation orientation,
            String leadDirection,
            Map<Face, Boolean> faces,
            String sourceEntryId
    ) {
        public EffectiveBlockConfig {
            faces = Map.copyOf(faces);
        }

        public EffectiveBlockConfig withSource(String source) {
            return new EffectiveBlockConfig(movement, scheduledTicks, pushCost, adhesion,
                    geometrySource, minimumSquare, depth, orientation, leadDirection, faces, source);
        }
    }

    public enum Movement { VANILLA, MOVABLE, BREAKABLE, IMMOVABLE }
    public enum ScheduledTicks { VANILLA, TRANSFER, DUPLICATE }
    public enum Adhesion { VANILLA, ADHESIVE, GLAZED, HITBOX, ADVANCED }
    public enum GeometrySource { OUTLINE, COLLISION, VISUAL }
    public enum Orientation { AUTO, MANUAL, OMNIDIRECTIONAL }
    public enum Face { FRONT, BACK, LEFT, RIGHT, UP, DOWN }

    public static final class BlockConfig {
        public String blockId;
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

        public EffectiveBlockConfig snapshot(String sourceEntryId) {
            return new EffectiveBlockConfig(movement, scheduledTicks, pushCost, adhesion,
                    geometrySource, minimumSquare, depth, orientation, leadDirection,
                    faces, sourceEntryId);
        }

        public BlockConfig copyFor(String newBlockId) {
            BlockConfig copy = new BlockConfig();
            copy.blockId = newBlockId;
            copy.movement = movement;
            copy.scheduledTicks = scheduledTicks;
            copy.pushCost = pushCost;
            copy.adhesion = adhesion;
            copy.geometrySource = geometrySource;
            copy.minimumSquare = minimumSquare;
            copy.depth = depth;
            copy.orientation = orientation;
            copy.leadDirection = leadDirection;
            copy.faces.clear();
            copy.faces.putAll(faces);
            return copy;
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
