package com.flash.registry;

import com.flash.FlashMod;
import com.flash.worldgen.FlashMuseumPiece;
import com.flash.worldgen.FlashMuseumStructure;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.structure.StructureType;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** Worldgen registrations: the Flash Museum structure and its piece. */
public final class ModStructures {

    public static final DeferredRegister<StructureType<?>> STRUCTURE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_TYPE, FlashMod.MODID);
    public static final DeferredRegister<StructurePieceType> PIECE_TYPES =
            DeferredRegister.create(Registries.STRUCTURE_PIECE, FlashMod.MODID);

    public static final RegistryObject<StructureType<FlashMuseumStructure>> FLASH_MUSEUM =
            STRUCTURE_TYPES.register("flash_museum", () -> () -> FlashMuseumStructure.CODEC);

    public static final RegistryObject<StructurePieceType> FLASH_MUSEUM_PIECE =
            PIECE_TYPES.register("flash_museum", () -> (context, tag) -> new FlashMuseumPiece(tag));

    public static final RegistryObject<StructureType<com.flash.worldgen.KryptonianShipStructure>> KRYPTONIAN_SHIP =
            STRUCTURE_TYPES.register("kryptonian_ship", () -> () -> com.flash.worldgen.KryptonianShipStructure.CODEC);

    public static final RegistryObject<StructurePieceType> KRYPTONIAN_SHIP_PIECE =
            PIECE_TYPES.register("kryptonian_ship", () -> (context, tag) -> new com.flash.worldgen.KryptonianShipPiece(tag));

    private ModStructures() {
    }
}
