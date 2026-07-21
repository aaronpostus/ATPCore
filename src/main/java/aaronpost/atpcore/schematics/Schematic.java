package aaronpost.atpcore.schematics;

import aaronpost.atpcore.ATPCore;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Directional;
import org.bukkit.block.data.Orientable;
import org.bukkit.entity.*;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.EulerAngle;

import java.io.Serializable;
import java.util.*;

public class Schematic implements Serializable {
    private final String name;
    private final int xLength, yLength, zLength;
    private final LocationWrapper start, end;
    private final Map<String, LocationWrapper> eventBlockLocs = new HashMap<>();
    private int yOffset;

    private boolean dontCacheBlocks;

    private transient CachedBlockData[][][] cachedBlocks;
    private transient int effectiveHeight = -1;
    private transient List<CachedEntityData> cachedEntities;

    public Schematic(Block b1, Block b2, String name) {
        this.yOffset = 0;
        this.name = name;

        World world = b1.getWorld();

        int west = Math.min(b1.getX(), b2.getX());
        int east = Math.max(b1.getX(), b2.getX());
        int down = Math.min(b1.getY(), b2.getY());
        int up = Math.max(b1.getY(), b2.getY());
        int south = Math.min(b1.getZ(), b2.getZ());
        int north = Math.max(b1.getZ(), b2.getZ());

        this.start = new LocationWrapper(new Location(world, west, down, south));
        this.end = new LocationWrapper(new Location(world, east, up, north));

        this.xLength = east - west + 1;
        this.yLength = up - down + 1;
        this.zLength = north - south + 1;
        this.cachedBlocks = null;
    }

    /**
     * Constructor for compiled/in-memory schematics that have no reference world location.
     * These schematics are purely cache-based.
     */
    private Schematic(String name, int xLength, int yLength, int zLength, int yOffset,
                      CachedBlockData[][][] cachedBlocks, Map<String, LocationWrapper> eventBlocks) {
        this.name = name;
        this.xLength = xLength;
        this.yLength = yLength;
        this.zLength = zLength;
        this.yOffset = yOffset;
        this.start = null;
        this.end = null;
        this.dontCacheBlocks = false;
        this.cachedBlocks = cachedBlocks;
        this.cachedEntities = new ArrayList<>();
        if (eventBlocks != null) {
            this.eventBlockLocs.putAll(eventBlocks);
        }
        computeEffectiveHeight();
    }

    /**
     * Creates a compiled schematic that merges the base schematic with a rotated top schematic.
     * Both schematics must have a "pivotPoint" event block defined.
     * The top schematic's blocks are rotated around its pivot and placed at the base's pivot position.
     * Used for layer-by-layer construction that includes the cannon top.
     */
    public static Schematic compileWithTop(String name, Schematic base, Schematic top, int rotations) {
        if (!base.eventBlockLocs.containsKey("pivotPoint") || !top.eventBlockLocs.containsKey("pivotPoint")) {
            System.out.printf("Cannot compile schematic %s: base or top is missing pivotPoint event block.%n", name);
            return null;
        }

        Location basePivotRel = base.eventBlockLocs.get("pivotPoint").getLoc();
        int basePivotX = (int) basePivotRel.getX();
        int basePivotY = (int) basePivotRel.getY();
        int basePivotZ = (int) basePivotRel.getZ();

        Location topPivotRel = top.eventBlockLocs.get("pivotPoint").getLoc();
        int topPivotX = (int) topPivotRel.getX();
        int topPivotY = (int) topPivotRel.getY();
        int topPivotZ = (int) topPivotRel.getZ();

        // The rotation system gets the pivot world position via getEventBlockLoc, which does NOT
        // include yOffset. But the compiled schematic applies yOffset during paste. Compensate by
        // subtracting yOffset from top block Y positions so they land at the correct world height.
        int yOffsetCompensation = -base.yOffset;

        // Determine combined bounding box by checking where top blocks land in base-relative coords
        int compMaxX = base.xLength - 1;
        int compMaxY = base.yLength - 1;
        int compMaxZ = base.zLength - 1;

        for (int ty = 0; ty < top.yLength; ty++) {
            for (int tx = 0; tx < top.xLength; tx++) {
                for (int tz = 0; tz < top.zLength; tz++) {
                    boolean isAir;
                    if (top.cachedBlocks != null) {
                        isAir = top.cachedBlocks[tx][ty][tz] == null;
                    } else {
                        isAir = top.getRefBlock(tx, ty, tz).getType().isAir();
                    }
                    if (isAir) continue;

                    int dx = tx - topPivotX;
                    int dy = ty - topPivotY;
                    int dz = tz - topPivotZ;
                    int[] rotated = rotateCoords(dx, dz, rotations);

                    compMaxX = Math.max(compMaxX, basePivotX + rotated[0]);
                    compMaxY = Math.max(compMaxY, basePivotY + yOffsetCompensation + dy);
                    compMaxZ = Math.max(compMaxZ, basePivotZ + rotated[1]);
                }
            }
        }

        int compXLen = compMaxX + 1;
        int compYLen = compMaxY + 1;
        int compZLen = compMaxZ + 1;

        CachedBlockData[][][] compiled = new CachedBlockData[compXLen][compYLen][compZLen];

        // Fill with base blocks
        for (int y = 0; y < base.yLength; y++) {
            for (int x = 0; x < base.xLength; x++) {
                for (int z = 0; z < base.zLength; z++) {
                    if (base.cachedBlocks != null) {
                        compiled[x][y][z] = base.cachedBlocks[x][y][z];
                    } else {
                        Block ref = base.getRefBlock(x, y, z);
                        if (!ref.getType().isAir()) {
                            compiled[x][y][z] = new CachedBlockData(ref.getType(), ref.getBlockData().clone(), captureSignData(ref));
                        }
                    }
                }
            }
        }

        // Overlay rotated top blocks
        for (int ty = 0; ty < top.yLength; ty++) {
            for (int tx = 0; tx < top.xLength; tx++) {
                for (int tz = 0; tz < top.zLength; tz++) {
                    Material mat;
                    BlockData bd;
                    CachedSignData topSignData = null;
                    if (top.cachedBlocks != null) {
                        CachedBlockData cbd = top.cachedBlocks[tx][ty][tz];
                        if (cbd == null) continue;
                        mat = cbd.material;
                        bd = cbd.blockData.clone();
                        topSignData = cbd.signData;
                    } else {
                        Block ref = top.getRefBlock(tx, ty, tz);
                        if (ref.getType().isAir()) continue;
                        mat = ref.getType();
                        bd = ref.getBlockData().clone();
                        topSignData = captureSignData(ref);
                    }

                    int dx = tx - topPivotX;
                    int dy = ty - topPivotY;
                    int dz = tz - topPivotZ;
                    int[] rotated = rotateCoords(dx, dz, rotations);

                    int cx = basePivotX + rotated[0];
                    int cy = basePivotY + yOffsetCompensation + dy;
                    int cz = basePivotZ + rotated[1];

                    if (cx < 0 || cy < 0 || cz < 0 || cx >= compXLen || cy >= compYLen || cz >= compZLen) {
                        continue;
                    }

                    compiled[cx][cy][cz] = new CachedBlockData(mat, rotateBlockData(bd, rotations), topSignData);
                }
            }
        }

        Map<String, LocationWrapper> events = new HashMap<>(base.eventBlockLocs);
        return new Schematic(name, compXLen, compYLen, compZLen, base.yOffset, compiled, events);
    }

    // ----- getters / setters -----
    public int getXLength() { return xLength; }
    public int getYLength() { return yLength; }
    public int getZLength() { return zLength; }
    public int getYOffset() { return yOffset; }
    public int getEffectiveHeight() {
        if (effectiveHeight >= 0) return effectiveHeight;
        return yLength + yOffset;
    }
    public void setyOffset(int yOffset)
    {
        this.yOffset = yOffset;
        Schematics.s.markUpdated(this);
    }
    public String getName() { return name; }

    public boolean constructionNeedsUpdate(int layersBuilt, float percentageComplete) {
        return layersToBuild(percentageComplete) != layersBuilt;
    }

    private Block getRefBlock(int x, int y, int z) {
        Location base = start.getLoc();
        return base.getWorld().getBlockAt(
                base.getBlockX() + x,
                base.getBlockY() + y,
                base.getBlockZ() + z
        );
    }

    public void setEventBlockLoc(String index, Location loc) {
        Location origin = start.getLoc();
        double x = loc.getX() - origin.getX();
        double y = loc.getY() - origin.getY();
        double z = loc.getZ() - origin.getZ();
        this.eventBlockLocs.put(index, new LocationWrapper(new Location(loc.getWorld(), x, y, z)));
        Schematics.s.markUpdated(this);
    }

    public boolean hasEventBlock(String eventBlockId) {
        return eventBlockLocs.containsKey(eventBlockId);
    }

    public Location getEventBlockLoc(Location origin, String eventBlockId) {
        if (!eventBlockLocs.containsKey(eventBlockId)) {
            System.out.printf("Schematic %s event block with index %s was accessed and wasn't defined.%n", getName(), eventBlockId);
            return origin;
        }

        Location relativeLoc = eventBlockLocs.get(eventBlockId).getLoc();

        Location eventBlockLoc = origin.clone();
        relativeLoc.setWorld(eventBlockLoc.getWorld());
        eventBlockLoc.add(relativeLoc);
        return eventBlockLoc;
    }

    public int layersToBuild(float percentageComplete) {
        return (int) Math.ceil(percentageComplete * (yLength + yOffset));
    }

    public void pasteSchematic(Location origLoc, java.util.UUID ownerUuid) {
        pasteSchematic(origLoc, false, null, ownerUuid);
    }

    /**
     * Paste schematic. If batched == true uses your existing batched async flow.
     * When cacheBlocks==true, will use cachedBlocks to reduce reference world lookups.
     *
     * onComplete runs on main thread (called via runTask at end).
     */
    public void pasteSchematic(Location origLoc, boolean batched, Runnable onComplete) {
        pasteSchematic(origLoc, batched, onComplete, null);
    }

    public void pasteSchematic(Location origLoc, boolean batched, Runnable onComplete, java.util.UUID ownerUuid) {
        Location base = origLoc.clone().add(0, yOffset, 0);
        List<LocationWrapper> multiBlocks = new ArrayList<>();
        List<LocationWrapper> attachedBlocks = new ArrayList<>();

        if (!batched) {
            pasteAllBlocks(base, multiBlocks, attachedBlocks, ownerUuid);
            placeMultiBlocks(origLoc, multiBlocks);
            placeAttachedBlocks(origLoc, attachedBlocks, ownerUuid);
            spawnCachedEntities(origLoc);
            if (onComplete != null) onComplete.run();
            return;
        }

        PasteConfig config = Schematics.s.getPasteConfig();
        int batchSize = config.batchBlocksSize();
        int delayTicks = config.ticksBetweenBatches();

        // Batched: process blocks in batches on the main thread using a repeating task
        new BukkitRunnable() {
            int bx = 0, by = 0, bz = 0;

            @Override
            public void run() {
                int placed = 0;
                while (by < yLength && placed < batchSize) {
                    Location targetLoc = base.clone().add(bx, by, bz);

                    if (!dontCacheBlocks && cachedBlocks != null) {
                        CachedBlockData cbd = cachedBlocks[bx][by][bz];
                        Block targetBlock = targetLoc.getBlock();
                        if (cbd == null) {
                            targetBlock.setType(Material.AIR, false);
                        } else {
                            if (isBanner(cbd.material)) {
                                Material replaced = resolveBanner(cbd.material, ownerUuid);
                                if (replaced == null) {
                                    targetBlock.setType(Material.AIR, false);
                                } else {
                                    placeBanner(targetBlock, replaced, cbd.blockData, ownerUuid);
                                }
                            } else {
                                targetBlock.setType(cbd.material, false);
                                targetBlock.setBlockData(cbd.blockData.clone(), false);
                                applySignData(targetBlock, cbd.signData);
                            }
                        }
                    } else {
                        Block refBlock = getRefBlock(bx, by, bz);
                        Block targetBlock = targetLoc.getBlock();
                        if (isMultiBlock(refBlock)) {
                            multiBlocks.add(new LocationWrapper(refBlock.getLocation()));
                        } else if (isAttachedBlock(refBlock)) {
                            attachedBlocks.add(new LocationWrapper(refBlock.getLocation()));
                        } else if (isBanner(refBlock.getType())) {
                            Material replaced = resolveBanner(refBlock.getType(), ownerUuid);
                            if (replaced == null) {
                                targetBlock.setType(Material.AIR, false);
                            } else {
                                placeBanner(targetBlock, replaced, refBlock.getBlockData(), ownerUuid);
                            }
                        } else {
                            targetBlock.setType(refBlock.getType(), false);
                            targetBlock.setBlockData(refBlock.getBlockData(), false);
                            copySignText(refBlock, targetBlock);
                        }
                    }

                    placed++;
                    // Advance through z -> x -> y to match original nested loop order
                    bz++;
                    if (bz >= zLength) { bz = 0; bx++; }
                    if (bx >= xLength) { bx = 0; by++; }
                }

                if (by >= yLength) {
                    cancel();
                    placeMultiBlocks(origLoc, multiBlocks);
                    placeAttachedBlocks(origLoc, attachedBlocks, ownerUuid);
                    spawnCachedEntities(origLoc);
                    if (onComplete != null) onComplete.run();
                }
            }
        }.runTaskTimer(ATPCore.plugin, 0L, delayTicks);
    }

    /**
     * Paste all blocks (non-batched mode). Uses cachedBlocks if present; otherwise reads live blocks.
     */
    private void pasteAllBlocks(Location base, List<LocationWrapper> multiBlocks, List<LocationWrapper> attachedBlocks, java.util.UUID ownerUuid) {
        for (int y = 0; y < yLength; y++) {
            for (int x = 0; x < xLength; x++) {
                for (int z = 0; z < zLength; z++) {
                    if (!dontCacheBlocks && cachedBlocks != null) {
                        CachedBlockData cbd = cachedBlocks[x][y][z];

                        Block target = base.getWorld().getBlockAt(
                                base.getBlockX() + x,
                                base.getBlockY() + y,
                                base.getBlockZ() + z
                        );

                        if (cbd == null) { target.setType(Material.AIR,false); continue; }
                        // If this is a multi/attached block type we must defer to the multi/attached collectors,
                        // because the specialized placement needs coordinates relative to the reference block.
                        // We determine multi/attached by inspecting the cached BlockData's runtime type.
                        if (isMultiBlock(cbd.blockData)) {
                            multiBlocks.add(getRefLocationWrapper(x, y, z));
                        } else if (isAttachedBlock(cbd.material)) {
                            attachedBlocks.add(getRefLocationWrapper(x, y, z));
                        } else if (isBanner(cbd.material)) {
                            Material replaced = resolveBanner(cbd.material, ownerUuid);
                            if (replaced == null) {
                                target.setType(Material.AIR, false);
                            } else {
                                placeBanner(target, replaced, cbd.blockData, ownerUuid);
                            }
                        } else {
                            target.setType(cbd.material, false);
                            target.setBlockData(cbd.blockData.clone(), false);
                            applySignData(target, cbd.signData);
                        }
                    } else {
                        Block ref = getRefBlock(x, y, z);
                        Block target = base.getWorld().getBlockAt(
                                base.getBlockX() + x,
                                base.getBlockY() + y,
                                base.getBlockZ() + z
                        );

                        if (isMultiBlock(ref)) multiBlocks.add(new LocationWrapper(ref.getLocation()));
                        else if (isAttachedBlock(ref)) attachedBlocks.add(new LocationWrapper(ref.getLocation()));
                        else if (isBanner(ref.getType())) {
                            Material replaced = resolveBanner(ref.getType(), ownerUuid);
                            if (replaced == null) {
                                target.setType(Material.AIR, false);
                            } else {
                                placeBanner(target, replaced, ref.getBlockData(), ownerUuid);
                            }
                        }
                        else {
                            target.setType(ref.getType(), false);
                            target.setBlockData(ref.getBlockData(), false);
                            copySignText(ref, target);
                        }
                    }
                }
            }
        }
    }

    private LocationWrapper getRefLocationWrapper(int x, int y, int z) {
        Block refBlock = getRefBlock(x, y, z);
        return new LocationWrapper(refBlock.getLocation());
    }

    private boolean isMultiBlock(Block block) {
        return block.getBlockData() instanceof org.bukkit.block.data.type.Bed
                || block.getBlockData() instanceof org.bukkit.block.data.type.Door;
    }

    // overloaded version using BlockData to avoid requiring a Block (for cached flow)
    private boolean isMultiBlock(BlockData data) {
        return data instanceof org.bukkit.block.data.type.Bed
                || data instanceof org.bukkit.block.data.type.Door;
    }

    private boolean isAttachedBlock(Block block) {
        Material type = block.getType();
        return isAttachedBlock(type);
    }

    // helper using material only (cached path)
    private boolean isAttachedBlock(Material type) {
        if (type == null) return false;
        String name = type.name();
        return type == Material.LEVER || name.endsWith("_BUTTON") || name.endsWith("_WALL_BANNER");
    }

    private boolean isBanner(Material type) {
        if (type == null) return false;
        String name = type.name();
        return name.endsWith("_BANNER") || name.endsWith("_WALL_BANNER");
    }

    /**
     * Resolve the correct banner material via the installed {@link BannerResolver}.
     * With no resolver installed, banners paste unchanged.
     * Returns null if the banner should be removed.
     */
    private Material resolveBanner(Material original, java.util.UUID ownerUuid) {
        BannerResolver resolver = Schematics.s.getBannerResolver();
        if (resolver == null) return original;
        return resolver.resolveBanner(original, ownerUuid);
    }

    /**
     * Place a banner, transferring directional/rotation data from the original,
     * then let the installed {@link BannerResolver} decorate it (e.g. clan patterns).
     */
    private void placeBanner(Block targetBlock, Material resolved, BlockData originalData, java.util.UUID ownerUuid) {
        targetBlock.setType(resolved, false);
        BlockData newData = targetBlock.getBlockData();
        if (newData instanceof org.bukkit.block.data.Directional && originalData instanceof org.bukkit.block.data.Directional) {
            ((org.bukkit.block.data.Directional) newData).setFacing(((org.bukkit.block.data.Directional) originalData).getFacing());
        } else if (newData instanceof org.bukkit.block.data.Rotatable && originalData instanceof org.bukkit.block.data.Rotatable) {
            ((org.bukkit.block.data.Rotatable) newData).setRotation(((org.bukkit.block.data.Rotatable) originalData).getRotation());
        }
        targetBlock.setBlockData(newData, false);

        BannerResolver resolver = Schematics.s.getBannerResolver();
        if (resolver != null) {
            resolver.decorateBanner(targetBlock, ownerUuid);
        }
    }

    private void placeMultiBlocks(Location origin, List<LocationWrapper> blocks) {
        for (LocationWrapper wrapper : blocks) {
            Block refBlock = wrapper.getBlock();
            Location targetLoc = getTargetRelative(origin, refBlock);
            Block targetBlock = targetLoc.getBlock();

            if (refBlock.getBlockData() instanceof org.bukkit.block.data.type.Bed) {
                placeBed(refBlock, targetBlock);
            } else if (refBlock.getBlockData() instanceof org.bukkit.block.data.type.Door) {
                placeDoor(refBlock, targetBlock);
            } else {
                targetBlock.setType(refBlock.getType(), false);
                targetBlock.setBlockData(refBlock.getBlockData(), false);
            }
        }
    }

    private void placeAttachedBlocks(Location origin, List<LocationWrapper> blocks, java.util.UUID ownerUuid) {
        for (LocationWrapper wrapper : blocks) {
            Block refBlock = wrapper.getBlock();
            Location targetLoc = getTargetRelative(origin, refBlock);
            Block targetBlock = targetLoc.getBlock();

            if (isBanner(refBlock.getType())) {
                Material replaced = resolveBanner(refBlock.getType(), ownerUuid);
                if (replaced == null) {
                    targetBlock.setType(Material.AIR, false);
                } else {
                    placeBanner(targetBlock, replaced, refBlock.getBlockData(), ownerUuid);
                }
            } else {
                targetBlock.setType(refBlock.getType(), false);
                targetBlock.setBlockData(refBlock.getBlockData(), false);
            }
        }
    }

    private void placeBed(Block refBlock, Block targetBlock) {
        org.bukkit.block.data.type.Bed bedData = (org.bukkit.block.data.type.Bed) refBlock.getBlockData();
        if (bedData.getPart() == org.bukkit.block.data.type.Bed.Part.HEAD) return;
        Block head = targetBlock.getRelative(bedData.getFacing());
        targetBlock.setType(refBlock.getType(), false);
        targetBlock.setBlockData(bedData, false);
        org.bukkit.block.data.type.Bed headData = (org.bukkit.block.data.type.Bed) bedData.clone();
        headData.setPart(org.bukkit.block.data.type.Bed.Part.HEAD);
        head.setType(refBlock.getType(), false);
        head.setBlockData(headData, false);
    }

    private void placeDoor(Block refBlock, Block targetBlock) {
        org.bukkit.block.data.type.Door doorData = (org.bukkit.block.data.type.Door) refBlock.getBlockData();
        if (doorData.getHalf() == org.bukkit.block.data.type.Door.Half.TOP) return;
        Block top = targetBlock.getRelative(0, 1, 0);
        targetBlock.setType(refBlock.getType(), false);
        targetBlock.setBlockData(doorData, false);
        org.bukkit.block.data.type.Door topData = (org.bukkit.block.data.type.Door) doorData.clone();
        topData.setHalf(org.bukkit.block.data.type.Door.Half.TOP);
        top.setType(refBlock.getType(), false);
        top.setBlockData(topData, false);
    }

    // ------------------------------------------------------------
    // RESET / CONSTRUCTION FUNCTIONS
    // ------------------------------------------------------------

    public static void setCheckerboardBlock(Block targetBlock, int originX, int originZ) {
        int cellX = Math.floorDiv(targetBlock.getX() - originX, 3);
        int cellZ = Math.floorDiv(targetBlock.getZ() - originZ, 3);
        if (Math.floorMod(cellX + cellZ, 2) == 0) {
            targetBlock.getRelative(0, -1, 0).setType(Material.DIRT, false);
            targetBlock.setType(Material.MOSS_BLOCK, false);
        } else {
            targetBlock.getRelative(0, -1, 0).setType(Material.DIRT, false);
            targetBlock.setType(Material.AZALEA, false);
        }
    }

    public void resetToGrassLand(Location origLoc, Location arenaOrigin) {
        int originX = arenaOrigin.getBlockX();
        int originZ = arenaOrigin.getBlockZ();
        Location base = origLoc.clone().add(0, yOffset, 0);
        for (int y = 0; y < yLength; y++) {
            for (int x = 0; x < xLength; x++) {
                for (int z = 0; z < zLength; z++) {
                    if (!dontCacheBlocks && cachedBlocks != null) {
                        CachedBlockData cbd = cachedBlocks[x][y][z];
                        Block targetBlock = base.getWorld().getBlockAt(
                                base.getBlockX() + x,
                                base.getBlockY() + y,
                                base.getBlockZ() + z
                        );
                        if (cbd != null && isMultiBlock(cbd.blockData)) {
                            removeMultiBlock(targetBlock, cbd);
                            if (y < Math.abs(yOffset) - 1) {
                                targetBlock.setType(Material.DIRT, false);
                            }
                        } else {
                            if (y == Math.abs(yOffset) - 1) {
                                setCheckerboardBlock(targetBlock, originX, originZ);
                            } else if (y < Math.abs(yOffset) - 1) {
                                targetBlock.setType(Material.DIRT, false);
                            } else {
                                targetBlock.setType(Material.AIR, false);
                            }
                        }
                    } else {
                        Block refBlock = getRefBlock(x, y, z);
                        Block targetBlock = base.getWorld().getBlockAt(
                                base.getBlockX() + x,
                                base.getBlockY() + y,
                                base.getBlockZ() + z
                        );

                        if (isMultiBlock(refBlock)) {
                            removeMultiBlock(targetBlock, refBlock);
                            if (y < Math.abs(yOffset) - 1) {
                                targetBlock.setType(Material.DIRT, false);
                            }
                        } else {
                            if (y == Math.abs(yOffset) - 1) {
                                setCheckerboardBlock(targetBlock, originX, originZ);
                            } else if (y < Math.abs(yOffset) - 1) {
                                targetBlock.setType(Material.DIRT, false);
                            } else {
                                targetBlock.setType(Material.AIR, false);
                            }
                        }
                    }
                }
            }
        }
        removeBlockLikeEntities(base.getWorld(), base, xLength, yLength, zLength);
    }

    // helper removeMultiBlock overload for cached data
    private void removeMultiBlock(Block targetBlock, CachedBlockData referenceCached) {
        if (referenceCached.blockData instanceof org.bukkit.block.data.type.Bed bedData) {
            if (bedData.getPart() == org.bukkit.block.data.type.Bed.Part.FOOT) {
                Block head = targetBlock.getRelative(bedData.getFacing());
                head.setType(Material.AIR, false);
            }
            targetBlock.setType(Material.AIR, false);
        } else if (referenceCached.blockData instanceof org.bukkit.block.data.type.Door doorData) {
            if (doorData.getHalf() == org.bukkit.block.data.type.Door.Half.BOTTOM) {
                Block top = targetBlock.getRelative(0, 1, 0);
                top.setType(Material.AIR, false);
            }
            targetBlock.setType(Material.AIR, false);
        }
    }

    private void removeMultiBlock(Block targetBlock, Block referenceBlock) {
        if (referenceBlock.getBlockData() instanceof org.bukkit.block.data.type.Bed bedData) {
            if (bedData.getPart() == org.bukkit.block.data.type.Bed.Part.FOOT) {
                Block head = targetBlock.getRelative(bedData.getFacing());
                head.setType(Material.AIR, false);
            }
            targetBlock.setType(Material.AIR, false);
        } else if (referenceBlock.getBlockData() instanceof org.bukkit.block.data.type.Door doorData) {
            if (doorData.getHalf() == org.bukkit.block.data.type.Door.Half.BOTTOM) {
                Block top = targetBlock.getRelative(0, 1, 0);
                top.setType(Material.AIR, false);
            }
            targetBlock.setType(Material.AIR, false);
        }
    }

    private Location getTargetRelative(Location origin, Block refBlock) {
        Location s = start.getLoc();
        return origin.clone().add(
                refBlock.getX() - s.getX(),
                refBlock.getY() - s.getY() + yOffset,
                refBlock.getZ() - s.getZ()
        );
    }

    public void pasteSchematicConstruction(Location origLoc, int layersBuilt) {
        Location base = origLoc.clone().add(0, yOffset, 0);

        for (int y = 0; y < yLength; y++) {
            for (int x = 0; x < xLength; x++) {
                for (int z = 0; z < zLength; z++) {
                    Material refMat;
                    BlockData refData;
                    boolean isAirOrBarrier;
                    CachedSignData signDataForBlock = null;

                    if (!dontCacheBlocks && cachedBlocks != null) {
                        CachedBlockData cbd = cachedBlocks[x][y][z];
                        if (cbd == null) {
                            isAirOrBarrier = true;
                            refMat = null;
                            refData = null;
                        } else {
                            refMat = cbd.material;
                            refData = cbd.blockData;
                            isAirOrBarrier = (refMat == Material.BARRIER);
                            signDataForBlock = cbd.signData;
                        }
                    } else {
                        Block ref = getRefBlock(x, y, z);
                        refMat = ref.getType();
                        refData = ref.getBlockData();
                        isAirOrBarrier = refMat.isAir() || refMat == Material.BARRIER;
                        signDataForBlock = captureSignData(ref);
                    }

                    Block target = base.getWorld().getBlockAt(
                            base.getBlockX() + x,
                            base.getBlockY() + y,
                            base.getBlockZ() + z
                    );

                    if ((y) >= layersBuilt - yOffset || isAirOrBarrier) {
                        if(needsScaffolding(x,z,xLength,zLength) && y < getEffectiveHeight() - yOffset) {
                            target.setType(Material.SCAFFOLDING, false);
                        }
                    } else {
                        target.setType(refMat, false);
                        target.setBlockData(refData, false);
                        applySignData(target, signDataForBlock);
                    }
                }
            }
        }
    }

    public boolean needsScaffolding(int x, int z, int xLength, int zLength) {
        if (x < 2) return false;
        if (z < 2) return false;
        if (x+2>=xLength) return false;
        return z + 2 < zLength;
    }

    /**
     * Build the transient cache of block Material + BlockData from the reference schematic area.
     * This will perform world lookups once and store clones of the blockdata for future use.
     * Called lazily on first paste if cacheBlocks == true.
     */
    public void buildCache() {
        if (start == null) return;

        World world = start.getLoc().getWorld();
        if (world == null) return;

        // Always capture block-like entities from the reference area
        captureEntities(world);

        if(dontCacheBlocks) { ATPCore.log("Schematic " + name + " is not cached, that can affect performance. Be careful."); return; }

        cachedBlocks = new CachedBlockData[xLength][yLength][zLength];

        for (int y = 0; y < yLength; y++) {
            for (int x = 0; x < xLength; x++) {
                for (int z = 0; z < zLength; z++) {
                    Block ref = getRefBlock(x, y, z);
                    if (ref == null || ref.getType().isAir()) {
                        cachedBlocks[x][y][z] = null;
                    } else {
                        BlockData bd = ref.getBlockData();
                        // store clones to avoid accidentally mutating world BlockData references
                        BlockData bdClone = bd.clone();
                        cachedBlocks[x][y][z] = new CachedBlockData(ref.getType(), bdClone, captureSignData(ref));
                    }
                }
            }
        }
        computeEffectiveHeight();
    }

    /**
     * Compute the effective height of the schematic by finding the highest layer
     * that contains at least one non-air block. Accounts for yOffset.
     * Must be called after cachedBlocks is populated.
     */
    public void computeEffectiveHeight() {
        if (cachedBlocks == null) {
            effectiveHeight = yLength + yOffset;
            return;
        }
        int highestNonAirLayer = -1;
        for (int y = yLength - 1; y >= 0; y--) {
            boolean found = false;
            for (int x = 0; x < xLength && !found; x++) {
                for (int z = 0; z < zLength && !found; z++) {
                    if (cachedBlocks[x][y][z] != null) {
                        highestNonAirLayer = y;
                        found = true;
                    }
                }
            }
            if (found) break;
        }
        if (highestNonAirLayer < 0) {
            effectiveHeight = yLength + yOffset;
        } else {
            effectiveHeight = highestNonAirLayer + 1 + yOffset;
        }
    }

    // Simple inner class used for transient cache
    private static class CachedBlockData implements Serializable {
        private final Material material;
        private final BlockData blockData;
        private final CachedSignData signData;

        CachedBlockData(Material material, BlockData blockData, CachedSignData signData) {
            this.material = material;
            this.blockData = blockData;
            this.signData = signData;
        }
    }

    // Cached sign text data (lines, colors, glow, waxed) for all sign types including hanging signs
    private static class CachedSignData implements Serializable {
        private final String[][] lines; // [side ordinal][line index]
        private final org.bukkit.DyeColor[] colors;
        private final boolean[] glowing;
        private final boolean waxed;

        CachedSignData(String[][] lines, org.bukkit.DyeColor[] colors, boolean[] glowing, boolean waxed) {
            this.lines = lines;
            this.colors = colors;
            this.glowing = glowing;
            this.waxed = waxed;
        }
    }

    /**
     * Capture sign text data from a block if it is a sign (including hanging signs).
     * Returns null if the block is not a sign.
     */
    private static CachedSignData captureSignData(Block block) {
        if (!(block.getState() instanceof org.bukkit.block.Sign sign)) return null;
        org.bukkit.block.sign.Side[] sides = org.bukkit.block.sign.Side.values();
        String[][] lines = new String[sides.length][4];
        org.bukkit.DyeColor[] colors = new org.bukkit.DyeColor[sides.length];
        boolean[] glowing = new boolean[sides.length];
        for (int s = 0; s < sides.length; s++) {
            org.bukkit.block.sign.SignSide signSide = sign.getSide(sides[s]);
            for (int i = 0; i < 4; i++) {
                lines[s][i] = signSide.getLine(i);
            }
            colors[s] = signSide.getColor();
            glowing[s] = signSide.isGlowingText();
        }
        return new CachedSignData(lines, colors, glowing, sign.isWaxed());
    }

    /**
     * Apply cached sign data to a target block. No-op if data is null or target is not a sign.
     */
    private static void applySignData(Block target, CachedSignData data) {
        if (data == null) return;
        if (!(target.getState() instanceof org.bukkit.block.Sign sign)) return;
        org.bukkit.block.sign.Side[] sides = org.bukkit.block.sign.Side.values();
        for (int s = 0; s < sides.length; s++) {
            org.bukkit.block.sign.SignSide signSide = sign.getSide(sides[s]);
            for (int i = 0; i < 4; i++) {
                signSide.setLine(i, data.lines[s][i]);
            }
            signSide.setColor(data.colors[s]);
            signSide.setGlowingText(data.glowing[s]);
        }
        sign.setWaxed(data.waxed);
        sign.update();
    }

    /**
     * Copy sign text from a reference block to a target block.
     * No-op if either block is not a sign.
     */
    private static void copySignText(Block refBlock, Block target) {
        applySignData(target, captureSignData(refBlock));
    }

    /**
     * Stores captured data for a block-like entity (armor stand, item frame, painting)
     * found in the reference schematic area.
     */
    private static class CachedEntityData {
        final EntityType entityType;
        final double relX, relY, relZ;
        final float yaw, pitch;

        // Armor stand properties
        boolean small, visible, marker, hasArms, hasBasePlate, hasGravity;
        ItemStack helmet, chestplate, leggings, boots, mainHand, offHand;
        EulerAngle headPose, bodyPose, leftArmPose, rightArmPose, leftLegPose, rightLegPose;
        String customName;
        boolean customNameVisible;

        // Item frame properties
        ItemStack frameItem;
        Rotation frameRotation;
        boolean fixed;
        boolean frameVisible;

        // Hanging entity properties (item frames and paintings)
        BlockFace facing;

        // Painting properties
        Art art;

        CachedEntityData(EntityType type, double relX, double relY, double relZ, float yaw, float pitch) {
            this.entityType = type;
            this.relX = relX;
            this.relY = relY;
            this.relZ = relZ;
            this.yaw = yaw;
            this.pitch = pitch;
        }
    }

    // ------------------------------------------------------------
    // ENTITY SUPPORT METHODS
    // ------------------------------------------------------------

    /**
     * Checks if an entity type is a "block-like" entity supported by the schematic system.
     */
    public static boolean isBlockLikeEntity(EntityType type) {
        return type == EntityType.ARMOR_STAND ||
                type == EntityType.ITEM_FRAME ||
                type == EntityType.GLOW_ITEM_FRAME ||
                type == EntityType.PAINTING;
    }

    /**
     * Remove block-like entities (armor stands, item frames, paintings) from a world area.
     * @param world The world to scan
     * @param corner The minimum corner of the bounding box
     * @param xLen X length of the area
     * @param yLen Y length of the area
     * @param zLen Z length of the area
     */
    public static void removeBlockLikeEntities(World world, Location corner, int xLen, int yLen, int zLen) {
        if (world == null || corner == null) return;
        BoundingBox box = new BoundingBox(
                corner.getX(), corner.getY(), corner.getZ(),
                corner.getX() + xLen, corner.getY() + yLen, corner.getZ() + zLen
        );
        for (Entity entity : world.getNearbyEntities(box)) {
            if (isBlockLikeEntity(entity.getType())) {
                // Skip NPC entities (e.g. Citizens) — they are managed by their plugin and must
                // not be removed during schematic paste (e.g., flying troop armor stands
                // hovering above a building being destroyed)
                if (entity.hasMetadata("NPC")) continue;
                entity.remove();
            }
        }
    }

    /**
     * Capture block-like entities from the reference schematic area.
     */
    private void captureEntities(World world) {
        cachedEntities = new ArrayList<>();
        Location startLoc = start.getLoc();

        BoundingBox bbox = new BoundingBox(
                startLoc.getX(), startLoc.getY(), startLoc.getZ(),
                startLoc.getX() + xLength, startLoc.getY() + yLength, startLoc.getZ() + zLength
        );

        for (Entity entity : world.getNearbyEntities(bbox)) {
            if (isBlockLikeEntity(entity.getType())) {
                cachedEntities.add(captureEntity(entity, startLoc));
            }
        }
    }

    /**
     * Capture a single entity's data relative to the schematic origin.
     */
    private CachedEntityData captureEntity(Entity entity, Location refOrigin) {
        Location loc = entity.getLocation();
        double relX = loc.getX() - refOrigin.getX();
        double relY = loc.getY() - refOrigin.getY();
        double relZ = loc.getZ() - refOrigin.getZ();

        CachedEntityData data = new CachedEntityData(
                entity.getType(), relX, relY, relZ, loc.getYaw(), loc.getPitch()
        );

        if (entity instanceof ArmorStand as) {
            data.small = as.isSmall();
            data.visible = as.isVisible();
            data.marker = as.isMarker();
            data.hasArms = as.hasArms();
            data.hasBasePlate = as.hasBasePlate();
            data.hasGravity = as.hasGravity();
            data.customName = as.getCustomName();
            data.customNameVisible = as.isCustomNameVisible();
            data.headPose = as.getHeadPose();
            data.bodyPose = as.getBodyPose();
            data.leftArmPose = as.getLeftArmPose();
            data.rightArmPose = as.getRightArmPose();
            data.leftLegPose = as.getLeftLegPose();
            data.rightLegPose = as.getRightLegPose();

            EntityEquipment eq = as.getEquipment();
            if (eq != null) {
                data.helmet = cloneIfPresent(eq.getHelmet());
                data.chestplate = cloneIfPresent(eq.getChestplate());
                data.leggings = cloneIfPresent(eq.getLeggings());
                data.boots = cloneIfPresent(eq.getBoots());
                data.mainHand = cloneIfPresent(eq.getItemInMainHand());
                data.offHand = cloneIfPresent(eq.getItemInOffHand());
            }
        } else if (entity instanceof ItemFrame frame) {
            data.frameItem = cloneIfPresent(frame.getItem());
            data.frameRotation = frame.getRotation();
            data.facing = frame.getFacing();
            data.fixed = frame.isFixed();
            data.frameVisible = frame.isVisible();
        } else if (entity instanceof Painting painting) {
            data.art = painting.getArt();
            data.facing = painting.getFacing();
        }

        return data;
    }

    private static ItemStack cloneIfPresent(ItemStack item) {
        return (item != null && item.getType() != Material.AIR) ? item.clone() : null;
    }

    /**
     * Spawn cached entities at the paste target location.
     * Removes any existing block-like entities in the area first to prevent duplicates.
     */
    private void spawnCachedEntities(Location origLoc) {
        // Remove existing block-like entities in the schematic area before spawning
        Location base = origLoc.clone().add(0, yOffset, 0);
        removeBlockLikeEntities(base.getWorld(), base, xLength, yLength, zLength);

        if (cachedEntities == null || cachedEntities.isEmpty()) return;

        World world = origLoc.getWorld();
        if (world == null) return;

        for (CachedEntityData data : cachedEntities) {
            Location spawnLoc = new Location(world,
                    origLoc.getX() + data.relX,
                    origLoc.getY() + yOffset + data.relY,
                    origLoc.getZ() + data.relZ,
                    data.yaw, data.pitch);

            try {
                switch (data.entityType) {
                    case ARMOR_STAND -> world.spawn(spawnLoc, ArmorStand.class,
                            as -> applyArmorStandData(as, data));
                    case ITEM_FRAME -> world.spawn(spawnLoc, ItemFrame.class,
                            frame -> applyItemFrameData(frame, data));
                    case GLOW_ITEM_FRAME -> world.spawn(spawnLoc, GlowItemFrame.class,
                            frame -> applyItemFrameData(frame, data));
                    case PAINTING -> world.spawn(spawnLoc, Painting.class,
                            painting -> applyPaintingData(painting, data));
                    default -> {}
                }
            } catch (Exception e) {
                System.out.printf("Failed to spawn entity %s for schematic %s at [%.1f, %.1f, %.1f]: %s%n",
                        data.entityType, name, spawnLoc.getX(), spawnLoc.getY(), spawnLoc.getZ(), e.getMessage());
            }
        }
    }

    private static void applyArmorStandData(ArmorStand as, CachedEntityData data) {
        as.setSmall(data.small);
        as.setVisible(data.visible);
        as.setMarker(data.marker);
        as.setArms(data.hasArms);
        as.setBasePlate(data.hasBasePlate);
        as.setGravity(data.hasGravity);
        if (data.customName != null) {
            as.setCustomName(data.customName);
            as.setCustomNameVisible(data.customNameVisible);
        }
        if (data.headPose != null) as.setHeadPose(data.headPose);
        if (data.bodyPose != null) as.setBodyPose(data.bodyPose);
        if (data.leftArmPose != null) as.setLeftArmPose(data.leftArmPose);
        if (data.rightArmPose != null) as.setRightArmPose(data.rightArmPose);
        if (data.leftLegPose != null) as.setLeftLegPose(data.leftLegPose);
        if (data.rightLegPose != null) as.setRightLegPose(data.rightLegPose);

        EntityEquipment eq = as.getEquipment();
        if (eq != null) {
            if (data.helmet != null) eq.setHelmet(data.helmet.clone());
            if (data.chestplate != null) eq.setChestplate(data.chestplate.clone());
            if (data.leggings != null) eq.setLeggings(data.leggings.clone());
            if (data.boots != null) eq.setBoots(data.boots.clone());
            if (data.mainHand != null) eq.setItemInMainHand(data.mainHand.clone());
            if (data.offHand != null) eq.setItemInOffHand(data.offHand.clone());
        }
    }

    private static void applyItemFrameData(ItemFrame frame, CachedEntityData data) {
        if (data.facing != null) frame.setFacingDirection(data.facing, true);
        if (data.frameItem != null) frame.setItem(data.frameItem.clone());
        if (data.frameRotation != null) frame.setRotation(data.frameRotation);
        frame.setFixed(data.fixed);
        frame.setVisible(data.frameVisible);
    }

    private static void applyPaintingData(Painting painting, CachedEntityData data) {
        if (data.facing != null) painting.setFacingDirection(data.facing, true);
        if (data.art != null) painting.setArt(data.art, true);
    }

    @Override
    public String toString() {
        return "Schematic{name='" + name + "', size=" + xLength + "x" + yLength + "x" + zLength + ", dontCache=" + dontCacheBlocks + "}";
    }

    // ------------------------------------------------------------
    // ROTATION + PIVOT BASED PASTE/CLEAR METHODS
    // ------------------------------------------------------------

    /**
     * Paste this schematic at a world pivot location, with rotation.
     * The schematic's "pivotPoint" event block is aligned with worldPivotLoc.
     * Each rotation step maps (dx, dz) -> (dz, -dx), matching direction progression W->S->E->N.
     * Air blocks in the schematic are skipped to preserve underlying blocks.
     */
    public void pasteSchematicRotatedAtPivot(Location worldPivotLoc, int rotations) {
        if (!eventBlockLocs.containsKey("pivotPoint")) {
            System.out.printf("Schematic %s has no pivotPoint event block defined.%n", getName());
            return;
        }

        Location pivotRel = eventBlockLocs.get("pivotPoint").getLoc();
        int pivotX = (int) pivotRel.getX();
        int pivotY = (int) pivotRel.getY();
        int pivotZ = (int) pivotRel.getZ();

        for (int y = 0; y < yLength; y++) {
            for (int x = 0; x < xLength; x++) {
                for (int z = 0; z < zLength; z++) {
                    Material refMat;
                    BlockData refData;
                    CachedSignData signDataForBlock = null;

                    if (!dontCacheBlocks && cachedBlocks != null) {
                        CachedBlockData cbd = cachedBlocks[x][y][z];
                        if (cbd == null) continue;
                        refMat = cbd.material;
                        refData = cbd.blockData.clone();
                        signDataForBlock = cbd.signData;
                    } else {
                        Block ref = getRefBlock(x, y, z);
                        if (ref.getType().isAir()) continue;
                        refMat = ref.getType();
                        refData = ref.getBlockData();
                        signDataForBlock = captureSignData(ref);
                    }

                    int dx = x - pivotX;
                    int dy = y - pivotY;
                    int dz = z - pivotZ;

                    int[] rotated = rotateCoords(dx, dz, rotations);

                    int worldX = worldPivotLoc.getBlockX() + rotated[0];
                    int worldY = worldPivotLoc.getBlockY() + dy;
                    int worldZ = worldPivotLoc.getBlockZ() + rotated[1];

                    Block target = worldPivotLoc.getWorld().getBlockAt(worldX, worldY, worldZ);
                    target.setType(refMat, false);
                    target.setBlockData(rotateBlockData(refData, rotations), false);
                    applySignData(target, signDataForBlock);
                }
            }
        }
    }

    /**
     * Clear the area this schematic would occupy when pasted rotated at worldPivotLoc.
     * Only clears positions where the schematic has non-air blocks.
     */
    public void clearRotatedRegion(Location worldPivotLoc, int rotations) {
        if (!eventBlockLocs.containsKey("pivotPoint")) return;

        Location pivotRel = eventBlockLocs.get("pivotPoint").getLoc();
        int pivotX = (int) pivotRel.getX();
        int pivotY = (int) pivotRel.getY();
        int pivotZ = (int) pivotRel.getZ();

        for (int y = 0; y < yLength; y++) {
            for (int x = 0; x < xLength; x++) {
                for (int z = 0; z < zLength; z++) {
                    boolean isAir;
                    if (!dontCacheBlocks && cachedBlocks != null) {
                        isAir = cachedBlocks[x][y][z] == null;
                    } else {
                        isAir = getRefBlock(x, y, z).getType().isAir();
                    }
                    if (isAir) continue;

                    int dx = x - pivotX;
                    int dy = y - pivotY;
                    int dz = z - pivotZ;

                    int[] rotated = rotateCoords(dx, dz, rotations);

                    int worldX = worldPivotLoc.getBlockX() + rotated[0];
                    int worldY = worldPivotLoc.getBlockY() + dy;
                    int worldZ = worldPivotLoc.getBlockZ() + rotated[1];

                    Block target = worldPivotLoc.getWorld().getBlockAt(worldX, worldY, worldZ);
                    target.setType(Material.AIR, false);
                }
            }
        }
        // Remove block-like entities in the rotated region using a generous bounding box
        int maxHorizontal = Math.max(xLength, zLength);
        Location entityCorner = worldPivotLoc.clone().add(-maxHorizontal, -yLength, -maxHorizontal);
        removeBlockLikeEntities(worldPivotLoc.getWorld(), entityCorner,
                maxHorizontal * 2 + 1, yLength * 2, maxHorizontal * 2 + 1);
    }

    /**
     * Get an event block's world location when this schematic is pasted rotated at worldPivotLoc.
     */
    public Location getEventBlockLocRotated(Location worldPivotLoc, String eventBlockId, int rotations) {
        if (!eventBlockLocs.containsKey(eventBlockId)) {
            System.out.printf("Schematic %s event block with index %s was accessed and wasn't defined.%n", getName(), eventBlockId);
            return worldPivotLoc;
        }
        if (!eventBlockLocs.containsKey("pivotPoint")) {
            System.out.printf("Schematic %s has no pivotPoint event block defined.%n", getName());
            return worldPivotLoc;
        }

        Location eventRel = eventBlockLocs.get(eventBlockId).getLoc();
        Location pivotRel = eventBlockLocs.get("pivotPoint").getLoc();

        double dx = eventRel.getX() - pivotRel.getX();
        double dy = eventRel.getY() - pivotRel.getY();
        double dz = eventRel.getZ() - pivotRel.getZ();

        double[] rotated = rotateCoordsDouble(dx, dz, rotations);

        Location result = worldPivotLoc.clone();
        result.add(rotated[0], dy, rotated[1]);
        return result;
    }

    // --- Coordinate rotation helpers ---

    /**
     * Rotate (dx, dz) by rotations * 90° steps.
     * Each step: (dx, dz) -> (dz, -dx).
     * This matches the game's direction progression: W->S->E->N.
     */
    private static int[] rotateCoords(int dx, int dz, int rotations) {
        rotations = ((rotations % 4) + 4) % 4;
        for (int i = 0; i < rotations; i++) {
            int temp = dz;
            dz = -dx;
            dx = temp;
        }
        return new int[]{dx, dz};
    }

    private static double[] rotateCoordsDouble(double dx, double dz, int rotations) {
        rotations = ((rotations % 4) + 4) % 4;
        for (int i = 0; i < rotations; i++) {
            double temp = dz;
            dz = -dx;
            dx = temp;
        }
        return new double[]{dx, dz};
    }

    // --- Block data rotation helpers ---

    private static BlockData rotateBlockData(BlockData data, int rotations) {
        rotations = ((rotations % 4) + 4) % 4;
        if (rotations == 0) return data;

        BlockData cloned = data.clone();

        if (cloned instanceof Directional directional) {
            BlockFace face = directional.getFacing();
            BlockFace rotatedFace = rotateFace(face, rotations);
            if (directional.getFaces().contains(rotatedFace)) {
                directional.setFacing(rotatedFace);
            }
        }

        if (cloned instanceof Orientable orientable) {
            Axis axis = orientable.getAxis();
            Axis rotatedAxis = rotateAxis(axis, rotations);
            orientable.setAxis(rotatedAxis);
        }

        return cloned;
    }

    private static BlockFace rotateFace(BlockFace face, int rotations) {
        for (int i = 0; i < rotations; i++) {
            face = rotateFace90(face);
        }
        return face;
    }

    private static BlockFace rotateFace90(BlockFace face) {
        return switch (face) {
            case NORTH -> BlockFace.WEST;
            case WEST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.EAST;
            case EAST -> BlockFace.NORTH;
            case NORTH_EAST -> BlockFace.NORTH_WEST;
            case NORTH_WEST -> BlockFace.SOUTH_WEST;
            case SOUTH_WEST -> BlockFace.SOUTH_EAST;
            case SOUTH_EAST -> BlockFace.NORTH_EAST;
            default -> face;
        };
    }

    private static Axis rotateAxis(Axis axis, int rotations) {
        if (axis == Axis.Y) return Axis.Y;
        if (rotations % 2 == 0) return axis;
        return axis == Axis.X ? Axis.Z : Axis.X;
    }
}
