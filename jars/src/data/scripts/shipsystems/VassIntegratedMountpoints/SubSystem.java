package data.scripts.shipsystems.VassIntegratedMountpoints;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.plugins.ShipSystemStatsScript;
import data.scripts.hullmods.VassIntegratedMountpoint;

/**
 * Abstract class representing one sub-system in use by the Integrated Mountpoint System
 */
public abstract class SubSystem {
    //"Apply" variation
    public abstract void apply(MutableShipStatsAPI stats, String id, ShipSystemStatsScript.State state, ShipAPI ship, boolean player, float effectLevel);

    //"Unapply" variation
    public abstract void unapply(MutableShipStatsAPI stats, String id, ShipAPI ship, boolean player);

    //For returning whatever text should be displayed as the main name of the system (to the left)
    public abstract String getDisplayName(ShipSystemStatsScript.State state, float effectLevel);

    //For returning whatever status text should be displayed at a given time
    public abstract String getTextToDisplay(ShipSystemAPI system, ShipAPI ship);

    public float getInOverride(ShipAPI ship) {
        return -1;
    }
    public float getActiveOverride(ShipAPI ship) {
        return -1;
    }
    public float getOutOverride(ShipAPI ship) {
        return -1;
    }
    public float getRegenOverride(ShipAPI ship) {
        return -1;
    }
    public int getUsesOverride(ShipAPI ship) {
        return -1;
    }
    public boolean isUsable(ShipSystemAPI system, ShipAPI ship) {
        return true;
    }

    //Convenience function: disables all non-special weapons on the ship
    protected void disableAllOtherWeapons(ShipAPI ship) {
        for (WeaponAPI wep : ship.getAllWeapons()) {
            if (wep.getSlot().getId().equals(VassIntegratedMountpoint.SPECIAL_SLOT_ID)) {
                continue;
            }
            if (wep.isDecorative()) {
                continue;
            }
            wep.setForceNoFireOneFrame(true);
        }
    }

    //Convenience function: disables ONLY the special weapon on the ship
    protected void disableMainWeapon(ShipAPI ship) {
        for (WeaponAPI wep : ship.getAllWeapons()) {
            if (!wep.getSlot().getId().equals(VassIntegratedMountpoint.SPECIAL_SLOT_ID)) {
                continue;
            }
            wep.setForceNoFireOneFrame(true);
        }
    }

    //Convenience function: shuts off the system once the weapon fires
    protected void disableOnFire(ShipAPI ship) {
        for (WeaponAPI wep : ship.getAllWeapons()) {
            if (!wep.getSlot().getId().equals(VassIntegratedMountpoint.SPECIAL_SLOT_ID)) {
                continue;
            }

            // Is the weapon firing?
            if (wep.getChargeLevel() >= 1f) {
                // Shut off the system
                forceDisableSystem(ship);
            }
        }
    }

    //Convenience function: disables the system prematurely, but still allows the out-period to run as normal
    protected void forceDisableSystem(ShipAPI ship) {
        if (ship.getSystem().getEffectLevel() >= 1f) {
            ship.getSystem().forceState(ShipSystemAPI.SystemState.OUT, 0f);
        }
    }
}
