package com.flash.dummy;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.util.FakePlayer;

/**
 * A test dummy (HeroDummies): a player of no one, standing where it was put, with a hero's powers, looks and
 * toughness, that can be hit (a Forge fake player made mortal). When its life runs out it stands up again whole.
 */
public class HeroDummy extends FakePlayer {

    final String power;
    int hits;
    float total;

    public HeroDummy(ServerLevel level, GameProfile profile, String power) {
        super(level, profile);
        this.power = power;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        return this.isRemoved() || source.is(net.minecraft.world.damagesource.DamageTypes.FELL_OUT_OF_WORLD)
                && this.getY() > this.level().getMinBuildHeight();
    }

    @Override
    public boolean canHarmPlayer(Player other) {
        return true;
    }

    @Override
    public void die(DamageSource source) {
        HeroDummies.defeated(this, source);
    }

    @Override
    public void tick() {
        // its life goes on from HeroDummies (doTick), as a player's does from his connection
    }
}
