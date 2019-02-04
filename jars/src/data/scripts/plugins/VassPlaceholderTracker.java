package data.scripts.plugins;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.FighterLaunchBayAPI;
import com.fs.starfarer.api.combat.FighterWingAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.util.IntervalUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class VassPlaceholderTracker {
    //Info for missions
    public static String missionWeapon1 = "heavyneedler";
    public static String missionWeapon2 = "heavyneedler";

    //Info for campaign
    private static Map<String, Integer> SHIP_NUMBERS = new HashMap<>();
    private static ArrayList<Integer> USED_ID = new ArrayList<Integer>();
    private static final int MAXIMUM_IDS = 9;

    public static void AddInfo (String owner, int info) {
        SHIP_NUMBERS.put(owner, info);
    }

    public static int GetInfo (String owner) {
        if (SHIP_NUMBERS.containsKey(owner)) {
            return SHIP_NUMBERS.get(owner);
        } else {
            return -1;
        }
    }

    public static int GetUniqueNumber () {
        //First, run a function to clear the lists of unwanted entries
        ClearUnused();

        //Gives back the lowest ID not currently used
        int intToGive = 1;
        while (USED_ID.contains(intToGive)) {
            intToGive++;
            if (intToGive > MAXIMUM_IDS) {
                return 0;
            }
        }
        return intToGive;
    }

    //A function which clears any unused ID:s from our lists, to ensure we can continue to use the system once we have replaced the ships
    private static void ClearUnused () {
        //Clears any number not currently in our fleet from USED_ID
        ArrayList<Integer> numbersToKeep = new ArrayList<Integer>();
        for (FleetMemberAPI fleetMemberAPI : Global.getSector().getPlayerFleet().getMembersWithFightersCopy()) {
            if (SHIP_NUMBERS.containsKey(fleetMemberAPI.getId())) {
                numbersToKeep.add(SHIP_NUMBERS.get(fleetMemberAPI.getId()));
            }
        }
        USED_ID = numbersToKeep;

        //Removes any entries in SHIP_NUMBERS which is not in our fleet
        ArrayList<String> removeList = new ArrayList<String>();
        for (String s : SHIP_NUMBERS.keySet()) {
            //Ignore zeroes (those mean we have exceeded the limit, or that this is an enemy ship) and ensure any ship with a number NOT in our fleet gets all data removed
            if (SHIP_NUMBERS.get(s) != 0 && !USED_ID.contains(SHIP_NUMBERS.get(s))) {
                removeList.add(s);
            }
        }
        for (String s : removeList) {
            SHIP_NUMBERS.remove(s);
        }
    }
}
