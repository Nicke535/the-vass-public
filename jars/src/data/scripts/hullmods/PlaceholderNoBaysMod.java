package data.scripts.hullmods;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;

import java.util.HashMap;
import java.util.Map;

//You should obviously rename PlaceHolderNoBaysMod to what you want it to be named
public class PlaceholderNoBaysMod extends BaseHullMod {

    //The magical map which saves how many bays each ship should lose
    private Map<String, Integer> savedData = new HashMap<>();

    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        //Reads the data we saved (later?) in the code: this works for an unknown reason, as it should only work the *second* time the hullmod has apply() triggered, but it works the first time too.
        //I suspect this is because the refit screen runs all apply() twice for good measure, or something like that. Since "id" is completely unique to the ship/hullmod in question, i use that as identifier.
        if (savedData.get(id) != null) {
            int numToRemove = savedData.get(id);
            stats.getNumFighterBays().modifyFlat(id, -numToRemove);

            /*
             ------------------   Any code related to stat changes should sit here: numToRemove is an integer equal to how many bays were removed   ------------------
             */
        }
    }

    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id){
        //Finds how many bays we should remove, and adds it to our saved data: we will read that afterwards
        int BAYS_TO_REMOVE = (int)ship.getMutableStats().getNumFighterBays().getBaseValue() - (ship.getVariant().getWings().size() - ship.getVariant().getNonBuiltInWings().size());
        savedData.put(id, BAYS_TO_REMOVE);
    }

    @Override
    public boolean isApplicableToShip(ShipAPI ship) {
        //Change this, to match what you want
        return true;
    }

    public String getUnapplicableReason(ShipAPI ship) {
        //Change this, to match what you want
        return "PLACEHOLDER";
    }

    @Override
    public String getDescriptionParam(int index, HullSize hullSize) {
        //Change this, to match what you want
        return null;
    }
}