package pl.dawcou.astrars.gates.data;

import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

public record GateDirections(
        BlockFace out,
        BlockFace back,
        BlockFace right,
        BlockFace left,
        Block targetBlock,
        Block backBlock,
        Block rightBlock,
        Block leftBlock
) {}