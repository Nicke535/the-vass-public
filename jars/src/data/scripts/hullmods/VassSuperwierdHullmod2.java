package data.scripts.hullmods;

import com.fs.starfarer.api.GameState;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import data.scripts.plugins.VassPlaceholderTracker;

public class VassSuperwierdHullmod2 extends BaseHullMod {

    //Saves info for the fighter to read later
    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id){
        //Checks if we are in a mission: if we are, we run a highly simplified version of the code ----------------------THIS DOES NOT SEEM TO WORK: CANNOT GET CHILD MODULES------------------------------------------------
        if (Global.getSector().getPlayerFleet() == null || Global.getCombatEngine().isMission()) {
            //Search our child modules (we may have multiple)
            boolean shouldBreak = false;
            for (ShipAPI child : ship.getChildModulesCopy()) {
                //We assume that the weapon slots have the names WS0001 and WS0002, and that only ONE module has weapons with those weapon slots
                if (child.getVariant().getWeaponId("WS0001") != null) {
                    VassPlaceholderTracker.missionWeapon1 = child.getVariant().getWeaponId("WS0001");
                    shouldBreak = true;
                }
                if (child.getVariant().getWeaponId("WS0002") != null) {
                    VassPlaceholderTracker.missionWeapon2 = child.getVariant().getWeaponId("WS0002");
                    shouldBreak = true;
                }

                //Breaks the loop if weapons are found: we don't need any more iterations if we already found them!
                if (shouldBreak) {
                    break;
                }
            }

            if (!shouldBreak) {
                VassPlaceholderTracker.missionWeapon1 = "hveldriver";
            }

            //Adds the wing to the ship, essentially "faking" a built-in wing
            ship.getVariant().setWingId(0, "vass_placeholder_wing");

            //Returns: this could have an "else" instead, but this is more compact
            return;
        }

        //Checks if we are an enemy ship, in which case we don't need most of the fancy code
        boolean isInPlayerFleet = false;
        for (FleetMemberAPI memberToTest : Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy()) {
            if (memberToTest.getId() == ship.getFleetMemberId()) {
                isInPlayerFleet = true;
                break;
            }
        }

        //If we ARE NOT in the player fleet, just set our Info to 0
        if (!isInPlayerFleet) {
            VassPlaceholderTracker.AddInfo(ship.getFleetMemberId(), 0);
        } else if (VassPlaceholderTracker.GetInfo(ship.getFleetMemberId()) == -1) { //Checks if to make sure we don't already have a unique ID number
            VassPlaceholderTracker.AddInfo(ship.getFleetMemberId(), VassPlaceholderTracker.GetUniqueNumber());
        }

        //If our info number is 0, we are either an enemy or have exceeded our maximum number of corvettes, so just add a normal wing
        if (VassPlaceholderTracker.GetInfo(ship.getFleetMemberId()) == 0) {
            ship.getVariant().setWingId(1, "vass_placeholder_wing");
        } else {
            //Otherwise, we need to add a special wing depending on our ID
            ship.getVariant().setWingId(1, "vass_placeholder_wing_" + VassPlaceholderTracker.GetInfo(ship.getFleetMemberId()));
        }
    }

    //Prevents the hullmod from being put on ships
    @Override
    public boolean isApplicableToShip(ShipAPI ship) {
        return false;
    }
}