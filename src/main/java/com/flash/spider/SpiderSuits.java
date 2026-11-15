package com.flash.spider;

import net.minecraft.network.chat.Component;

/**
 * Spider-Man's suits (Legends: Superheroes' models, converted by tools/legends_mesh_to_geo.py): models
 * geo/spider/&lt;id&gt;.geo.json, textures textures/spider_suits/&lt;id&gt;/ (layer_0 top, layer_1 trousers).
 */
public final class SpiderSuits {

    /** In the order of the K menu. Index 0 is the classic one. */
    public static final String[] IDS = {"spider_man", "spider_man_amazing_fantasy", "spider_man_sam_raimi",
            "spider_man_tasm_v1", "spider_man_tasm_v2", "spider_man_homecoming", "spider_man_upgraded",
            "spider_man_integrated", "spider_man_advanced2", "spider_man_advanced2_redblack", "spider_man_spectacular",
            "spider_man_brand_new_day", "spider_man_web_of_shadows", "spider_man2099", "spider_man2099_anad",
            "spider_man2099_black", "symbiote_spiderman", "symbiote_spiderman_sam_raimi"};

    private SpiderSuits() {
    }

    public static int clamp(int index) {
        return index < 0 || index >= IDS.length ? 0 : index;
    }

    public static String id(int index) {
        return IDS[clamp(index)];
    }

    /** A suit of his (by its id): drawn by the suit layer from geo/spider. */
    public static boolean isSpider(String id) {
        return id.startsWith("spider_man") || id.startsWith("symbiote_spiderman");
    }

    public static Component name(int index) {
        return Component.translatable("suit.flash." + id(index));
    }
}
