package com.heybroy0.movableblockentities.client;

import com.mojang.blaze3d.Blaze3D;
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.Collections;
import java.util.Deque;
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
    private static final Path CONFIG_DIRECTORY = FabricLoader.getInstance().getConfigDir()
            .resolve("movable-block-entities");
    private static final Path CACHE_DIRECTORY = CONFIG_DIRECTORY.resolve("cache");
    private static final Path FILE = CONFIG_DIRECTORY.resolve("settings.properties");
    private static final int HISTORY_LIMIT = 24;
    private static final Deque<StoreSnapshot> UNDO_HISTORY = new ArrayDeque<>();
    private static final Deque<StoreSnapshot> REDO_HISTORY = new ArrayDeque<>();
    private static StoreSnapshot lastHistorySnapshot;
    private static String lastHistoryFingerprint;
    private static boolean replayingHistory;
    private static boolean loaded;

    private ConfigStore() {}

    public static synchronized void load() {
        if (loaded) return;
        loaded = true;
        if (!Files.exists(FILE)) {
            resetHistoryBaseline();
            return;
        }

        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(FILE)) {
            properties.load(input);
        } catch (IOException exception) {
            System.err.println("[Movable Block Entities] Could not read settings: " + exception.getMessage());
            resetHistoryBaseline();
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
        int recoveredEntries = repairUnreachableTreeEntries();
        resetHistoryBaseline();
        if (recoveredEntries > 0) {
            System.err.println("[Movable Block Entities] Recovered " + recoveredEntries
                    + " unreachable tree entr" + (recoveredEntries == 1 ? "y" : "ies")
                    + " by restoring their directory paths to the root.");
            // Persist the repaired hierarchy immediately so it remains accessible on next launch.
            // Treat recovery as a load-time repair, not as a user-editable undo operation.
            replayingHistory = true;
            try {
                save();
            } finally {
                replayingHistory = false;
            }
            resetHistoryBaseline();
        }
    }

    /**
     * Finds saved entries that cannot be reached from a root entry and reconnects their
     * orphaned components. Missing parents and block-as-parent references are detached
     * directly; parent-reference cycles are broken at one deterministic cycle entry.
     * All entries and their descendants are retained.
     */
    private static int repairUnreachableTreeEntries() {
        Map<String, List<TreeEntry>> storedChildren = new java.util.HashMap<>();
        Deque<TreeEntry> pending = new ArrayDeque<>();
        for (TreeEntry entry : TREE_ENTRIES.values()) {
            if (entry.parentId() == null) {
                pending.addLast(entry);
            } else {
                storedChildren.computeIfAbsent(entry.parentId(), ignored -> new ArrayList<>()).add(entry);
            }
        }

        java.util.Set<String> reachable = new java.util.HashSet<>();
        while (!pending.isEmpty()) {
            TreeEntry current = pending.removeFirst();
            if (!reachable.add(current.id()) || current.type() == TreeEntryType.BLOCK) continue;
            pending.addAll(storedChildren.getOrDefault(current.id(), List.of()));
        }

        java.util.Set<String> unreachable = new java.util.LinkedHashSet<>(TREE_ENTRIES.keySet());
        unreachable.removeAll(reachable);
        if (unreachable.isEmpty()) return 0;

        java.util.Set<String> promoteToRoot = new java.util.LinkedHashSet<>();
        for (String id : unreachable) {
            TreeEntry entry = TREE_ENTRIES.get(id);
            TreeEntry parent = entry == null ? null : TREE_ENTRIES.get(entry.parentId());
            if (parent == null || parent.type() == TreeEntryType.BLOCK) {
                promoteToRoot.add(id);
            }
        }

        // Any remaining unreachable component must contain a cycle in its parent links.
        // Pick the lexicographically first entry in each cycle as its stable root.
        java.util.Set<String> cycleCandidates = new java.util.HashSet<>(unreachable);
        cycleCandidates.removeAll(promoteToRoot);
        java.util.Set<String> processed = new java.util.HashSet<>();
        for (String start : cycleCandidates) {
            if (processed.contains(start)) continue;
            List<String> path = new ArrayList<>();
            Map<String, Integer> pathPositions = new java.util.HashMap<>();
            String cursor = start;
            while (cursor != null && cycleCandidates.contains(cursor) && !processed.contains(cursor)) {
                Integer cycleStart = pathPositions.get(cursor);
                if (cycleStart != null) {
                    String cycleRoot = path.subList(cycleStart, path.size()).stream()
                            .min(Comparator.naturalOrder()).orElse(cursor);
                    promoteToRoot.add(cycleRoot);
                    break;
                }
                pathPositions.put(cursor, path.size());
                path.add(cursor);
                TreeEntry current = TREE_ENTRIES.get(cursor);
                cursor = current == null ? null : current.parentId();
            }
            processed.addAll(path);
        }

        int nextRootOrder = TREE_ENTRIES.values().stream()
                .filter(entry -> entry.parentId() == null)
                .mapToInt(TreeEntry::order).max().orElse(-1) + 1;
        int recovered = 0;
        for (String id : promoteToRoot) {
            TreeEntry entry = TREE_ENTRIES.get(id);
            if (entry == null) continue;
            TREE_ENTRIES.put(id, entry.withParent(null, nextRootOrder++));
            recovered++;
        }
        return recovered;
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

    /**
     * Returns every currently visible tree occurrence in breadth-first (top-down) order.
     * This lets a focus snapshot order all root items before their children, rather than
     * exhausting one folder's descendants before moving to the next root item.
     */
    public static synchronized List<TreeEntry> treeEntriesTopDown() {
        load();
        List<TreeEntry> result = new ArrayList<>();
        Deque<TreeEntry> pending = new ArrayDeque<>(rootEntries());
        while (!pending.isEmpty()) {
            TreeEntry entry = pending.removeFirst();
            result.add(entry);
            for (TreeEntry child : childrenOf(entry)) pending.addLast(child);
        }
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

    /** Returns block occurrences contained by a folder/selector in display hierarchy order. */
    public static synchronized List<TreeEntry> blockEntriesUnder(String entryId) {
        load();
        TreeEntry root = TREE_ENTRIES.get(entryId);
        if (root == null) return List.of();
        List<TreeEntry> result = new ArrayList<>();
        if (root.type() == TreeEntryType.BLOCK) return List.of(root);
        Deque<TreeEntry> pending = new ArrayDeque<>();
        List<TreeEntry> first = childrenOf(root);
        for (int i = first.size() - 1; i >= 0; i--) pending.push(first.get(i));
        while (!pending.isEmpty()) {
            TreeEntry current = pending.pop();
            if (current.type() == TreeEntryType.BLOCK) {
                result.add(current);
            } else {
                List<TreeEntry> children = childrenOf(current);
                for (int i = children.size() - 1; i >= 0; i--) pending.push(children.get(i));
            }
        }
        return List.copyOf(result);
    }

    /** Deletes selected entries and every descendant, reverting each deleted block occurrence. */
    public static synchronized void deleteTreeEntries(List<String> entryIds) {
        load();
        if (entryIds == null || entryIds.isEmpty()) return;
        java.util.Set<String> removeIds = new java.util.LinkedHashSet<>();
        Deque<String> pending = new ArrayDeque<>(entryIds);
        while (!pending.isEmpty()) {
            String id = pending.pop();
            if (id == null || id.isBlank() || !removeIds.add(id)) continue;
            TreeEntry entry = TREE_ENTRIES.get(id);
            if (entry != null && entry.type() != TreeEntryType.BLOCK) {
                for (TreeEntry child : childrenOf(entry)) pending.push(child.id());
            }
        }

        for (String id : removeIds) {
            TreeEntry entry = TREE_ENTRIES.get(id);
            if (entry != null && entry.type() == TreeEntryType.BLOCK) {
                TreeEntry topmost = findTopmostBlockEntry(entry.value());
                if (topmost != null && topmost.id().equals(id)) BLOCKS.remove(entry.value());
            } else if (id.startsWith("block|") || id.startsWith("tagblock|")) {
                String blockId = blockIdFromEntryId(id);
                if (blockId != null) {
                    TreeEntry topmost = findTopmostBlockEntry(blockId);
                    if (topmost == null || topmost.id().equals(id)) BLOCKS.remove(blockId);
                }
            }
            BLOCKS.remove(id);
            TREE_ENTRIES.remove(id);
        }
        save();
    }

    /** Copies selected entries in place, preserving folder hierarchy and per-occurrence settings. */
    public static synchronized List<String> copyTreeEntries(List<String> entryIds) {
        load();
        if (entryIds == null || entryIds.isEmpty()) return List.of();
        java.util.Set<String> selected = new java.util.LinkedHashSet<>(entryIds);
        List<String> copies = new ArrayList<>();
        for (String id : entryIds) {
            TreeEntry source = TREE_ENTRIES.get(id);
            if (source != null && hasSelectedAncestor(source, selected)) continue;
            String parentId = source == null ? tagBlockParentFromEntryId(id) : source.parentId();
            String copied = duplicateTreeEntry(id, parentId);
            if (copied != null) copies.add(copied);
        }
        if (!copies.isEmpty()) save();
        return List.copyOf(copies);
    }

    private static String duplicateTreeEntry(String sourceId, String parentId) {
        TreeEntry source = TREE_ENTRIES.get(sourceId);
        if (source == null) {
            String blockId = blockIdFromEntryId(sourceId);
            if (blockId == null) return null;
            if (sourceId.startsWith("tagblock|")) {
                TreeEntry selector = TREE_ENTRIES.get(parentId);
                if (selector == null || selector.type() != TreeEntryType.SELECTOR) return null;
                // Materialize the original virtual row first; the new node then becomes
                // a second, independently configurable occurrence under the same selector.
                TREE_ENTRIES.put(sourceId, new TreeEntry(sourceId, TreeEntryType.BLOCK,
                        blockId, blockId, parentId, nextOrder(parentId), false));
            }
            return duplicateBlockOccurrence(sourceId, blockId, parentId);
        }
        String newId = "node-" + UUID.randomUUID();
        TreeEntry copy = new TreeEntry(newId, source.type(), source.name(), source.value(),
                parentId, nextOrder(parentId), source.expanded());
        TREE_ENTRIES.put(newId, copy);
        if (source.type() == TreeEntryType.BLOCK) {
            copyOccurrenceConfig(source.id(), newId, source.value());
        } else if (source.type() == TreeEntryType.FOLDER) {
            for (TreeEntry child : childrenOf(source)) {
                if (TREE_ENTRIES.containsKey(child.id())) duplicateTreeEntry(child.id(), newId);
            }
        } else if (source.type() == TreeEntryType.SELECTOR) {
            // A copied selector resolves the same live tag membership. Only explicit children
            // are copied; generated tag members are supplied by childrenOf() automatically.
            for (TreeEntry child : childrenOf(source)) {
                if (TREE_ENTRIES.containsKey(child.id())) duplicateTreeEntry(child.id(), newId);
            }
        }
        return newId;
    }

    private static String duplicateBlockOccurrence(String sourceId, String blockId, String parentId) {
        String newId = "node-" + UUID.randomUUID();
        TREE_ENTRIES.put(newId, new TreeEntry(newId, TreeEntryType.BLOCK, blockId, blockId,
                parentId, nextOrder(parentId), false));
        copyOccurrenceConfig(sourceId, newId, blockId);
        return newId;
    }

    private static void copyOccurrenceConfig(String sourceId, String targetId, String blockId) {
        BlockConfig source = BLOCKS.get(sourceId);
        if (source == null) {
            TreeEntry topmost = findTopmostBlockEntry(blockId);
            if (topmost != null && topmost.id().equals(sourceId)) source = BLOCKS.get(blockId);
        }
        if (source != null) BLOCKS.put(targetId, source.copyFor(blockId));
    }

    public static synchronized void openConfigDirectory() {
        try {
            Files.createDirectories(CONFIG_DIRECTORY);
            Files.createDirectories(CACHE_DIRECTORY);
            Blaze3D.openPath(CONFIG_DIRECTORY);
        } catch (Exception exception) {
            System.err.println("[Movable Block Entities] Could not open config directory: " + exception.getMessage());
        }
    }

    public static synchronized boolean isUserTreeEntry(String entryId) {
        load();
        TreeEntry entry = TREE_ENTRIES.get(entryId);
        return entry != null && entry.type() == TreeEntryType.FOLDER;
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
                    // A selector-derived row is covered when its selector or any containing
                    // directory is selected. Otherwise keep the live selector and copy the row.
                    if (selectorId == null || !isSelectedOrHasSelectedAncestor(selectorId, selected)) {
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
                    if (selectorId != null && isSelectedOrHasSelectedAncestor(selectorId, selected)) continue;
                    if (selectedId.startsWith("block|")) {
                        // Materialize the generated root row under the folder using the same
                        // identity so its selection and per-occurrence settings remain attached.
                        TreeEntry movedRootBlock = new TreeEntry(selectedId, TreeEntryType.BLOCK,
                                blockId, blockId, id, nextOrder(id), false);
                        TREE_ENTRIES.put(selectedId, movedRootBlock);
                    } else {
                        // A generated selector row is copied; the selector keeps its live member.
                        copyBlockNode(blockId, selectedId, id);
                    }
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

    /** True when a container itself or one of its containing directories is selected. */
    private static boolean isSelectedOrHasSelectedAncestor(String entryId,
                                                             java.util.Set<String> selected) {
        String cursor = entryId;
        while (cursor != null) {
            if (selected.contains(cursor)) return true;
            TreeEntry entry = TREE_ENTRIES.get(cursor);
            cursor = entry == null ? null : entry.parentId();
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
        if (entry == null || entry.type() != TreeEntryType.FOLDER
                || name == null || name.isBlank()) return;
        TREE_ENTRIES.put(entryId, entry.withName(name.trim()));
        save();
    }

    public static synchronized void toggleExpanded(String entryId) {
        load();
        TreeEntry entry = TREE_ENTRIES.get(entryId);
        if (entry == null || entry.type() == TreeEntryType.BLOCK) return;
        // Expansion is a view-state change only. It must never alter sibling priority.
        TREE_ENTRIES.put(entryId, entry.withExpanded(!entry.expanded()));
        save();
    }

    /** Materializes and moves one exact occurrence to the top of its own directory. */
    /** Moves the focused entry to the front of its current directory without changing its children. */
    /** Apply a focused container's edited settings to every contained block occurrence. */
    public static synchronized void applyConfigToEntries(BlockConfig source, List<String> entryIds) {
        load();
        if (source == null || entryIds == null || entryIds.isEmpty()) return;
        for (String id : entryIds) {
            TreeEntry entry = TREE_ENTRIES.get(id);
            if (entry == null && id != null && id.startsWith("tagblock|")) {
                int separator = id.lastIndexOf('|');
                if (separator > "tagblock|".length()) {
                    String selectorId = id.substring("tagblock|".length(), separator);
                    String blockId = id.substring(separator + 1);
                    TreeEntry selector = TREE_ENTRIES.get(selectorId);
                    if (selector != null && selector.type() == TreeEntryType.SELECTOR) {
                        entry = new TreeEntry(id, TreeEntryType.BLOCK, blockId, blockId,
                                selectorId, nextOrder(selectorId), false);
                        TREE_ENTRIES.put(id, entry);
                    }
                }
            }
            if (entry == null || entry.type() != TreeEntryType.BLOCK) continue;
            BLOCKS.put(id, source.copyFor(entry.value()));
        }
        save();
    }

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


    /**
     * Moves a multi-selection as one undoable operation. Selected descendants are omitted
     * when their selected ancestor is already moving, and their relative order is preserved.
     */
    public static synchronized boolean moveTreeEntries(List<TreeEntry> sourceSnapshots,
                                                       TreeEntry targetSnapshot,
                                                       boolean intoTarget, boolean afterTarget) {
        load();
        if (sourceSnapshots == null || sourceSnapshots.isEmpty()) return false;

        Map<String, TreeEntry> unique = new LinkedHashMap<>();
        for (TreeEntry entry : sourceSnapshots) {
            if (entry != null) unique.putIfAbsent(entry.id(), entry);
        }
        if (unique.isEmpty()) return false;

        java.util.Set<String> selectedIds = new java.util.LinkedHashSet<>(unique.keySet());
        List<TreeEntry> topLevel = new ArrayList<>();
        for (TreeEntry entry : unique.values()) {
            if (!hasSelectedAncestor(entry, selectedIds)) topLevel.add(entry);
        }
        if (topLevel.isEmpty()
                || targetSnapshot != null && topLevel.stream().anyMatch(entry -> entry.id().equals(targetSnapshot.id()))) {
            return false;
        }

        String destinationParent = intoTarget && targetSnapshot != null
                && targetSnapshot.type() == TreeEntryType.FOLDER
                ? targetSnapshot.id()
                : targetSnapshot == null ? null : targetSnapshot.parentId();
        if (intoTarget && (targetSnapshot == null || targetSnapshot.type() != TreeEntryType.FOLDER)) return false;
        TreeEntry destinationContainer = destinationParent == null ? null : TREE_ENTRIES.get(destinationParent);
        for (TreeEntry source : topLevel) {
            if (destinationContainer != null && destinationContainer.type() == TreeEntryType.SELECTOR
                    && !Objects.equals(source.parentId(), destinationParent)) return false;
            if (source.type() != TreeEntryType.BLOCK && destinationParent != null
                    && (destinationParent.equals(source.id())
                    || isDescendantOf(source.id(), destinationParent))) return false;
        }

        // Inserting repeatedly just after a fixed anchor reverses order, so process that
        // direction backwards. Before-anchor and folder drops remain in display order.
        if (!intoTarget && afterTarget) Collections.reverse(topLevel);

        boolean moved = false;
        for (TreeEntry entry : topLevel) {
            moved |= moveTreeEntryInternal(entry, targetSnapshot, intoTarget, afterTarget);
        }
        if (moved) save();
        return moved;
    }

    private static boolean moveTreeEntryInternal(TreeEntry sourceSnapshot, TreeEntry targetSnapshot,
                                                  boolean intoTarget, boolean afterTarget) {
        if (sourceSnapshot == null) return false;

        String destinationParent = intoTarget && targetSnapshot != null
                && targetSnapshot.type() == TreeEntryType.FOLDER
                ? targetSnapshot.id()
                : targetSnapshot == null ? null : targetSnapshot.parentId();

        if (intoTarget && (targetSnapshot == null || targetSnapshot.type() != TreeEntryType.FOLDER)) {
            return false;
        }
        if (destinationParent != null) {
            TreeEntry parent = TREE_ENTRIES.get(destinationParent);
            if (parent == null || (parent.type() != TreeEntryType.FOLDER
                    && parent.type() != TreeEntryType.SELECTOR)) return false;
        }

        TreeEntry source = TREE_ENTRIES.get(sourceSnapshot.id());
        TreeEntry destinationContainer = destinationParent == null ? null : TREE_ENTRIES.get(destinationParent);
        if (destinationContainer != null && destinationContainer.type() == TreeEntryType.SELECTOR
                && !Objects.equals(sourceSnapshot.parentId(), destinationParent)) return false;
        TreeEntry sourceParent = sourceSnapshot.parentId() == null
                ? null : TREE_ENTRIES.get(sourceSnapshot.parentId());
        boolean selectorMember = sourceSnapshot.id().startsWith("tagblock|")
                || sourceParent != null && sourceParent.type() == TreeEntryType.SELECTOR;
        boolean copySelectorMember = selectorMember
                && !Objects.equals(sourceSnapshot.parentId(), destinationParent);

        if (source == null && sourceSnapshot.type() == TreeEntryType.BLOCK && !copySelectorMember) {
            source = new TreeEntry(sourceSnapshot.id(), TreeEntryType.BLOCK,
                    sourceSnapshot.name(), sourceSnapshot.value(), sourceSnapshot.parentId(),
                    sourceSnapshot.order(), false);
            TREE_ENTRIES.put(source.id(), source);
        }

        if (copySelectorMember) {
            String newId = "node-" + UUID.randomUUID();
            source = new TreeEntry(newId, TreeEntryType.BLOCK,
                    sourceSnapshot.name(), sourceSnapshot.value(), destinationParent,
                    nextOrder(destinationParent), false);
            TREE_ENTRIES.put(newId, source);
            BlockConfig selectorConfig = BLOCKS.get(sourceSnapshot.id());
            if (selectorConfig != null) {
                BLOCKS.put(newId, selectorConfig.copyFor(sourceSnapshot.value()));
            }
        } else if (source == null) {
            return false;
        }

        if (targetSnapshot != null) {
            if (targetSnapshot.id().equals(sourceSnapshot.id())) return false;
            if (!TREE_ENTRIES.containsKey(targetSnapshot.id())
                    && targetSnapshot.type() == TreeEntryType.BLOCK) {
                TREE_ENTRIES.put(targetSnapshot.id(), targetSnapshot);
            }
        }

        if (source.type() != TreeEntryType.BLOCK && destinationParent != null
                && (destinationParent.equals(source.id())
                || isDescendantOf(source.id(), destinationParent))) {
            return false;
        }

        if (copySelectorMember) {
            // The source remains a generated or materialized child of its selector.
            // Only the new copy participates in the destination move.
            source = TREE_ENTRIES.get(source.id());
        } else {
            source = source.withParent(destinationParent, nextOrder(destinationParent));
            TREE_ENTRIES.put(source.id(), source);
        }

        if (intoTarget) {
            TREE_ENTRIES.put(source.id(), source.withParent(destinationParent, nextOrder(destinationParent)));
        } else {
            reorderSiblings(destinationParent, source.id(),
                    targetSnapshot == null ? null : targetSnapshot.id(),
                    afterTarget);
        }
        return true;
    }

    /** Makes implicit rows persistent only when a drag operation needs an explicit order. */
    private static void reorderSiblings(String parentId, String movingId,
                                        String targetId, boolean afterTarget) {
        List<TreeEntry> siblings;
        if (parentId == null) {
            siblings = new ArrayList<>(rootEntries());
        } else {
            TreeEntry parent = TREE_ENTRIES.get(parentId);
            siblings = parent == null ? new ArrayList<>() : new ArrayList<>(childrenOf(parent));
        }

        for (TreeEntry sibling : siblings) {
            if (!TREE_ENTRIES.containsKey(sibling.id()) && sibling.type() == TreeEntryType.BLOCK) {
                TREE_ENTRIES.put(sibling.id(), sibling);
            }
        }

        List<String> orderedIds = new ArrayList<>();
        for (TreeEntry sibling : siblings) orderedIds.add(sibling.id());
        if (!orderedIds.contains(movingId)) orderedIds.add(movingId);
        orderedIds.remove(movingId);

        int insertionIndex = orderedIds.size();
        if (targetId != null) {
            int targetIndex = orderedIds.indexOf(targetId);
            if (targetIndex >= 0) insertionIndex = targetIndex + (afterTarget ? 1 : 0);
        }
        insertionIndex = Math.max(0, Math.min(orderedIds.size(), insertionIndex));
        orderedIds.add(insertionIndex, movingId);

        for (int i = 0; i < orderedIds.size(); i++) {
            TreeEntry sibling = TREE_ENTRIES.get(orderedIds.get(i));
            if (sibling != null) {
                TREE_ENTRIES.put(sibling.id(), sibling.withParent(parentId, i * 10));
            }
        }
    }

    public static synchronized TreeEntry findTopmostBlockEntry(String blockId) {
        load();
        List<TreeEntry> roots = rootEntries();
        Deque<TreeEntry> pending = new ArrayDeque<>();
        for (int i = roots.size() - 1; i >= 0; i--) pending.push(roots.get(i));
        while (!pending.isEmpty()) {
            TreeEntry entry = pending.pop();
            if (entry.type() == TreeEntryType.BLOCK && entry.value().equals(blockId)) return entry;
            List<TreeEntry> children = childrenOf(entry);
            for (int i = children.size() - 1; i >= 0; i--) pending.push(children.get(i));
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

    /** Reverts one occurrence without deleting any other copy of the same block. */
    public static synchronized void revertInstance(String blockId, String entryId) {
        load();
        if (entryId != null && !entryId.isBlank()) {
            BlockConfig removed = BLOCKS.remove(entryId);
            if (removed == null) {
                TreeEntry topmost = findTopmostBlockEntry(blockId);
                if (topmost != null && topmost.id().equals(entryId)) BLOCKS.remove(blockId);
            }

            // Revert removes only this occurrence. Other copies of the same block stay in
            // their folders/selectors and retain their independent settings.
            TreeEntry occurrence = TREE_ENTRIES.get(entryId);
            if (occurrence != null && occurrence.type() == TreeEntryType.BLOCK
                    && occurrence.value().equals(blockId)) {
                TREE_ENTRIES.remove(entryId);
            }
        } else {
            BLOCKS.remove(blockId);
        }
        save();
    }

    public static synchronized boolean canUndo() {
        load();
        return !UNDO_HISTORY.isEmpty();
    }

    public static synchronized boolean canRedo() {
        load();
        return !REDO_HISTORY.isEmpty();
    }

    public static synchronized boolean undo() {
        load();
        if (UNDO_HISTORY.isEmpty()) return false;
        REDO_HISTORY.addLast(captureSnapshot());
        restoreHistorySnapshot(UNDO_HISTORY.removeLast());
        return true;
    }

    public static synchronized boolean redo() {
        load();
        if (REDO_HISTORY.isEmpty()) return false;
        UNDO_HISTORY.addLast(captureSnapshot());
        restoreHistorySnapshot(REDO_HISTORY.removeLast());
        return true;
    }

    private static void resetHistoryBaseline() {
        lastHistorySnapshot = captureSnapshot();
        lastHistoryFingerprint = stateFingerprint();
        UNDO_HISTORY.clear();
        REDO_HISTORY.clear();
    }

    private static StoreSnapshot captureSnapshot() {
        Map<String, BlockConfig> blocks = new TreeMap<>();
        BLOCKS.forEach((key, value) -> blocks.put(key,
                value.copyFor(value.blockId == null ? key : value.blockId)));
        return new StoreSnapshot(blocks, new LinkedHashMap<>(TREE_ENTRIES));
    }

    private static void restoreHistorySnapshot(StoreSnapshot snapshot) {
        BLOCKS.clear();
        snapshot.blocks().forEach((key, value) -> BLOCKS.put(key,
                value.copyFor(value.blockId == null ? key : value.blockId)));
        TREE_ENTRIES.clear();
        TREE_ENTRIES.putAll(snapshot.entries());
        replayingHistory = true;
        try {
            save();
        } finally {
            replayingHistory = false;
        }
        lastHistorySnapshot = captureSnapshot();
        lastHistoryFingerprint = stateFingerprint();
    }

    private static String stateFingerprint() {
        StringBuilder value = new StringBuilder();
        new TreeMap<>(BLOCKS).forEach((key, config) -> {
            value.append("B:").append(key).append('|').append(config.blockId).append('|')
                    .append(config.movement).append('|').append(config.scheduledTicks).append('|')
                    .append(config.pushCost).append('|').append(config.adhesion).append('|')
                    .append(config.geometrySource).append('|').append(config.minimumSquare).append('|')
                    .append(config.depth).append('|').append(config.orientation).append('|')
                    .append(config.leadDirection).append('|').append(config.faces).append('\n');
        });
        new TreeMap<>(TREE_ENTRIES).forEach((key, entry) ->
                value.append("T:").append(key).append('|').append(entry.type()).append('|')
                        .append(entry.name()).append('|').append(entry.value()).append('|')
                        .append(entry.parentId()).append('|').append(entry.order()).append('|')
                        .append(entry.expanded()).append('\n'));
        return value.toString();
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
        if (!replayingHistory) {
            String fingerprint = stateFingerprint();
            if (lastHistorySnapshot != null && lastHistoryFingerprint != null
                    && !fingerprint.equals(lastHistoryFingerprint)) {
                UNDO_HISTORY.addLast(lastHistorySnapshot);
                while (UNDO_HISTORY.size() > HISTORY_LIMIT) UNDO_HISTORY.removeFirst();
                REDO_HISTORY.clear();
            }
            lastHistorySnapshot = captureSnapshot();
            lastHistoryFingerprint = fingerprint;
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

    private record StoreSnapshot(Map<String, BlockConfig> blocks, Map<String, TreeEntry> entries) {}

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
