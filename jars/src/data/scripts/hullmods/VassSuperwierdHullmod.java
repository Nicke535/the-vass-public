package data.scripts.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.mission.FleetSide;
import data.scripts.plugins.VassPlaceholderTracker;
import org.lazywizard.lazylib.combat.CombatUtils;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class VassSuperwierdHullmod extends BaseHullMod {

    //Gives the fighter the equipped weapon
    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id){
        //removeList is used to remove weapons
        ArrayList<String> removeList = new ArrayList<String>();

        String weaponToEquip1 = "NO_INFO";
        String weaponToEquip2 = "NO_INFO";

        //If we are in a mission, use simplified code; otherwise, proceed with the main code
        if (Global.getSector().getPlayerFleet() == null || Global.getCombatEngine().isMission()) {
            weaponToEquip1 = VassPlaceholderTracker.missionWeapon1;
            weaponToEquip2 = VassPlaceholderTracker.missionWeapon2;
        } else {
            //If this is left as 0, the ship is in fact an enemy ship and uses different methods of gaining its weapons
            int numberToSearchFor = 0;

            //Changes numberToSearchFor to the correct number, depending on hullmod. Unsure if this correctly gets the last number.
            for (String s : ship.getVariant().getHullMods()) {
                if (s.contains("vass_placeholder_tracker_")) {
                    numberToSearchFor = s.charAt(s.length()-1) - '0';
                }
            }

            //Where the magic (hopefully) happens
            if (Global.getSector().getPlayerFleet() != null && numberToSearchFor != 0) {
                //Get all fleet members in the player fleet
                for (FleetMemberAPI fleetMember : Global.getSector().getPlayerFleet().getMembersWithFightersCopy()) {
                    //Finds only the one which has the correct number compared to our number-hullmod
                    if (VassPlaceholderTracker.GetInfo(fleetMember.getId()) == numberToSearchFor) {
                        //Sets the weapon to equip into whatever weapon is ont he module mounted in WSCORV
                        weaponToEquip1 = fleetMember.getModuleVariant("WSCORV").getWeaponId("WS0001");
                        weaponToEquip2 = fleetMember.getModuleVariant("WSCORV").getWeaponId("WS0002");
                        break;
                    }
                }
            } else if (numberToSearchFor != 0) {
                weaponToEquip1 = "NO_INFO";
                weaponToEquip2 = "NO_INFO";
            } else {

            }
        }

        //If something went wrong, default to a Pulse Laser and a Vulcan, for identification
        if (weaponToEquip1.contains("NO_INFO")) {
            weaponToEquip1 = "pulselaser";
        }
        if (weaponToEquip2.contains("NO_INFO")) {
            weaponToEquip2 = "vulcan";
        }

        //Finally, applies the weapons in the correct slots
        ship.getVariant().clearSlot("WS0001");
        ship.getVariant().addWeapon("WS0001", weaponToEquip1);
        ship.getVariant().clearSlot("WS0002");
        ship.getVariant().addWeapon("WS0002", weaponToEquip2);

    }

    //Prevents the hullmod from being put on ships
    @Override
    public boolean isApplicableToShip(ShipAPI ship) {
        return false;
    }
}