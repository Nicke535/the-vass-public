package data.scripts.shipsystems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import data.scripts.hullmods.VassIntegratedMountpoint;
import data.scripts.hullmods.VassIntegratedMountpoint.BonusID;
import data.scripts.shipsystems.VassIntegratedMountpoints.*;

/**
 * The system attached to the Integrated Mountpoint hullmod
 * In practice, of course, this means it is in fact about a dozen or so systems, so ya know. Ouch.
 *
 * @author Nicke535
 */
public class VassIntegratedMountpointSystem extends BaseShipSystemScript {

    private SubSystem subSystem = null;


    public float getActiveOverride(ShipAPI ship) {
        ensureCorrectSubSystem(ship);
        return subSystem.getActiveOverride(ship);
    }
    public float getInOverride(ShipAPI ship) {
        ensureCorrectSubSystem(ship);
        return subSystem.getInOverride(ship);
    }
    public float getOutOverride(ShipAPI ship) {
        ensureCorrectSubSystem(ship);
        return subSystem.getOutOverride(ship);
    }
    public float getRegenOverride(ShipAPI ship) {
        ensureCorrectSubSystem(ship);
        return subSystem.getRegenOverride(ship);
    }
    public int getUsesOverride(ShipAPI ship) {
        ensureCorrectSubSystem(ship);
        return subSystem.getUsesOverride(ship);
    }
    public boolean isUsable(ShipSystemAPI system, ShipAPI ship) {
        ensureCorrectSubSystem(ship);
        return subSystem.isUsable(system, ship);
    }

    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        ShipAPI ship = null;
        boolean player = false;
        if (stats.getEntity() instanceof ShipAPI) {
            ship = (ShipAPI) stats.getEntity();
            player = ship == Global.getCombatEngine().getPlayerShip();
            id = id + "_" + ship.getId();
        } else {
            return;
        }
        ensureCorrectSubSystem(ship);
        subSystem.apply(stats, id, state, ship, player, effectLevel);
    }


    public void unapply(MutableShipStatsAPI stats, String id) {
        ShipAPI ship = null;
        boolean player = false;
        if (stats.getEntity() instanceof ShipAPI) {
            ship = (ShipAPI) stats.getEntity();
            player = ship == Global.getCombatEngine().getPlayerShip();
            id = id + "_" + ship.getId();
        } else {
            return;
        }
        ensureCorrectSubSystem(ship);
        subSystem.unapply(stats, id, ship, player);
    }

    public StatusData getStatusData(int index, State state, float effectLevel) {
        return null;
    }

    @Override
    public String getInfoText(ShipSystemAPI system, ShipAPI ship) {
        ensureCorrectSubSystem(ship);
        return subSystem.getTextToDisplay(system, ship);
    }

    @Override
    public String getDisplayNameOverride(State state, float effectLevel) {
        if (subSystem != null) {
            return subSystem.getDisplayName(state, effectLevel);
        }
        return "ERROR - DISPLAY NAME INCORRECTLY READ";
    }

    //Convenience: verifies that we have the correct sub-system script set up and ready
    private void ensureCorrectSubSystem(ShipAPI ship) {
        BonusID bonusID = VassIntegratedMountpoint.getBonusID(ship);
        SubSystem newSubsystem = new ShieldOvercharge();

        if (bonusID == BonusID.CALADBOLG) {
            newSubsystem = new ChamberOverpressure();
        } else if (bonusID == BonusID.GRAVITON_BEAM) {
            newSubsystem = new DefensiveAutotargetingSuite();
        } else if (bonusID == BonusID.TYPHOON_REAPER) {
            newSubsystem = new AuxiliaryLoadingMechanism();
        } else if (bonusID == BonusID.GENERIC_BALLISTIC) {
            newSubsystem = new RapidFeedMechanism();
        }

        if (subSystem == null ||
                subSystem.getClass() != newSubsystem.getClass()) {
            subSystem = newSubsystem;
        }
    }
}