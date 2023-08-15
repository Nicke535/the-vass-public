package data.scripts.shipsystems.VassIntegratedMountpoints;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.plugins.ShipSystemStatsScript;
import com.fs.starfarer.api.util.Misc;
import data.scripts.hullmods.VassIntegratedMountpoint;

import java.awt.*;
import java.util.HashMap;
import java.util.Map;

public class AuxiliaryLoadingMechanism extends SubSystem {
    public static final float COOLDOWN = 16f;
    public static final float IN = 1f;
    public static final float OUT = 0.3f;
    public static final float DURATION = 10f;
    public static final float FIRERATE_MULT = 8f;
    public static final int ALLOWED_SHOTS = 2;

    private int usedShots = 0;
    private boolean hasBegunCoolingDown = false;
    private Map<WeaponAPI, Float> cooldownMap = new HashMap<>();

    @Override
    public void apply(MutableShipStatsAPI stats, String id, ShipSystemStatsScript.State state, ShipAPI ship, boolean player, float effectLevel) {
        //Run unapply if needed
        if (effectLevel <= 0f) {
            unapply(stats, id, ship, player);
            return;
        }

        //Non-main weapons are always disabled
        disableAllOtherWeapons(ship);

        //If our effectLevel isn't at 1, we have no effect but still disable all weapons
        if (effectLevel < 1f) {
            disableMainWeapon(ship);
            return;
        }

        // Ensure the cooldown of other missile weapons are not majorly affected
        WeaponAPI weapon = VassIntegratedMountpoint.getWeaponInSpecialSlot(ship);
        if (weapon == null) { return; }
        for (WeaponAPI otherWeapon : ship.getAllWeapons()) {
            if (otherWeapon.getType().equals(WeaponAPI.WeaponType.MISSILE)
                && otherWeapon != weapon) {
                if (cooldownMap.containsKey(otherWeapon)) {
                    float cooldownNow = otherWeapon.getCooldownRemaining();
                    float cooldownOld = cooldownMap.get(otherWeapon);

                    if (cooldownNow < cooldownOld) {
                        float intendedCooldown = cooldownOld - ((cooldownOld-cooldownNow)/FIRERATE_MULT);
                        weapon.setRemainingCooldownTo(intendedCooldown);
                        cooldownMap.put(otherWeapon, intendedCooldown);
                    }
                } else {
                    cooldownMap.put(otherWeapon, otherWeapon.getCooldownRemaining());
                }
            }
        }

        //Actual stat modification
        stats.getMissileRoFMult().modifyMult(id, Misc.interpolate(1f, FIRERATE_MULT, effectLevel));

        //Check if we need to disable the system
        if (weapon.getCooldownRemaining() < weapon.getCooldown()
            && weapon.getCooldownRemaining() > 0f) {
            // We are cooling down currently
            hasBegunCoolingDown = true;
        } else {
            // We are not currently cooling down
            if (hasBegunCoolingDown) {
                usedShots += 1;
            }
            hasBegunCoolingDown = false;
        }
        if (usedShots >= ALLOWED_SHOTS) {
            forceDisableSystem(ship);
        }

    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id, ShipAPI ship, boolean player) {
        hasBegunCoolingDown = false;
        usedShots = 0;
        stats.getMissileRoFMult().unmodify(id);
    }

    @Override
    public String getDisplayName(ShipSystemStatsScript.State state, float effectLevel) {
        return "Aux. Loading Mechanism";
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
