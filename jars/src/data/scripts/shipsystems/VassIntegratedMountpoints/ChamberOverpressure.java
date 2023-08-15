package data.scripts.shipsystems.VassIntegratedMountpoints;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.ShipSystemAPI.SystemState;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.plugins.ShipSystemStatsScript;
import com.fs.starfarer.api.util.Misc;
import data.scripts.hullmods.VassIntegratedMountpoint;

public class ChamberOverpressure extends SubSystem {
    public static final float DAMAGE_MULT = 1.5f;
    public static final float RANGE_MULT = 1.2f;
    public static final float PROJSPEED_MULT = 3f;
    public static final float DURATION = 4f;
    public static final float IN = 0.5f;
    public static final float OUT = 0.1f;
    public static final int CHARGES = 3;
    public static final float REGEN = 0.1f;
    public static final float COOLDOWN = 2f;

    @Override
    public void apply(MutableShipStatsAPI stats, String id, ShipSystemStatsScript.State state, ShipAPI ship, boolean player, float effectLevel) {
        //Run unapply if needed
        if (effectLevel <= 0f) {
            unapply(stats, id, ship, player);
            return;
        }

        // Block other weapons from being used
        disableAllOtherWeapons(ship);

        //Unless we're on maximum effectlevel (or no effectlevel, since we're running unapply then), we're not allowed to use our main weapon either (though it can reload)
        if (effectLevel < 1f) {
            disableMainWeapon(ship);
        }

        //Disable the system as soon as we have fired
        disableOnFire(ship);

        //Stat modifiers
        stats.getBallisticWeaponDamageMult().modifyMult(id, DAMAGE_MULT);
        stats.getBallisticWeaponRangeBonus().modifyMult(id, Misc.interpolate(1f, RANGE_MULT, effectLevel));
        stats.getBallisticProjectileSpeedMult().modifyMult(id, PROJSPEED_MULT);
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id, ShipAPI ship, boolean player) {
        //Reset our stat increases
        stats.getBallisticWeaponDamageMult().unmodify(id);
        stats.getBallisticWeaponRangeBonus().unmodify(id);
        stats.getBallisticProjectileSpeedMult().unmodify(id);
    }

    @Override
    public String getDisplayName(ShipSystemStatsScript.State state, float effectLevel) {
        return "Chamber Overpressure";
    }

    @Override
    public String getTextToDisplay(ShipSystemAPI system, ShipAPI ship) {
        int ammo = system.getAmmo();
        if (system.getState().equals(SystemState.IDLE)) {
            if (ammo > 0) {
                return "Ready";
            }
        } else if (system.getState().equals(SystemState.ACTIVE)) {
            return "Active";
        }
        return null;
    }


    @Override
    public float getActiveOverride(ShipAPI ship) {
        return DURATION;
    }

    @Override
    public float getInOverride(ShipAPI ship) {
        return IN;
    }

    @Override
    public float getOutOverride(ShipAPI ship) {
        return OUT;
    }

    @Override
    public float getRegenOverride(ShipAPI ship) {
        return REGEN;
    }

    @Override
    public int getUsesOverride(ShipAPI ship) {
        return CHARGES;
    }

    //Returns (for other scripts that need it) which multiplier we have to apply to our base 10 second cooldown to get an appropriate "new" base cooldown
    public static float getSystemCooldownMult() {
        return (COOLDOWN / 10f);
    }
}
