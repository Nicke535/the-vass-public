package data.scripts.shipsystems.VassIntegratedMountpoints;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.plugins.ShipSystemStatsScript;
import com.fs.starfarer.api.util.Misc;

public class RapidFeedMechanism extends SubSystem {
    public static final float COOLDOWN = 10f;
    public static final float IN = 0.5f;
    public static final float OUT = 0.5f;
    public static final float DURATION = 4f;
    public static final float FLUX_COST_MULT = 0.5f;
    public static final float FIRERATE_MULT = 3f;

    @Override
    public void apply(MutableShipStatsAPI stats, String id, ShipSystemStatsScript.State state, ShipAPI ship, boolean player, float effectLevel) {
        //Run unapply if needed
        if (effectLevel <= 0f) {
            unapply(stats, id, ship, player);
            return;
        }

        disableAllOtherWeapons(ship);
        stats.getBallisticWeaponFluxCostMod().modifyMult(id, Misc.interpolate(1f, FLUX_COST_MULT, effectLevel));
        stats.getBallisticRoFMult().modifyMult(id, Misc.interpolate(1f, FIRERATE_MULT, effectLevel));
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id, ShipAPI ship, boolean player) {
        stats.getBallisticWeaponFluxCostMod().unmodify(id);
        stats.getBallisticRoFMult().unmodify(id);
    }

    @Override
    public String getDisplayName(ShipSystemStatsScript.State state, float effectLevel) {
        return "Rapid Feed Mechanism";
    }

    @Override
    public String getTextToDisplay(ShipSystemAPI system, ShipAPI ship) {
        if (system.getState().equals(ShipSystemAPI.SystemState.IDLE)) {
            return "Ready";
        } else if (system.getState().equals(ShipSystemAPI.SystemState.ACTIVE)) {
            return "Active";
        } else {
            return null;
        }
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

    //Returns (for other scripts that need it) which multiplier we have to apply to our base 10 second cooldown to get an appropriate "new" base cooldown
    public static float getSystemCooldownMult() {
        return (COOLDOWN / 10f);
    }
}
