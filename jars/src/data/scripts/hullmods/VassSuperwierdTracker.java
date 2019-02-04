package data.scripts.hullmods;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.ShipAPI;
import data.scripts.plugins.VassPlaceholderTracker;

public class VassSuperwierdTracker extends BaseHullMod {

    //This hullmod does nothing except prevents itself from being put on ships
    @Override
    public boolean isApplicableToShip(ShipAPI ship) {
        return false;
    }
}