package data.scripts.hullmods;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.impl.campaign.ids.Stats;

import java.util.ArrayList;

public class VassPatternMounts extends BaseHullMod {

    private final float OP_COST_MULT = 0.001f;

    //Makes all weapons free on the ship
    @Override
    public void applyEffectsBeforeShipCreation(HullSize hullSize, MutableShipStatsAPI stats, String id) {
        stats.getDynamic().getMod(Stats.SMALL_MISSILE_MOD).modifyMult(id, OP_COST_MULT);
        stats.getDynamic().getMod(Stats.SMALL_ENERGY_MOD).modifyMult(id, OP_COST_MULT);
        stats.getDynamic().getMod(Stats.SMALL_BALLISTIC_MOD).modifyMult(id, OP_COST_MULT);
        stats.getDynamic().getMod(Stats.MEDIUM_MISSILE_MOD).modifyMult(id, OP_COST_MULT);
        stats.getDynamic().getMod(Stats.MEDIUM_ENERGY_MOD).modifyMult(id, OP_COST_MULT);
        stats.getDynamic().getMod(Stats.MEDIUM_BALLISTIC_MOD).modifyMult(id, OP_COST_MULT);
        stats.getDynamic().getMod(Stats.LARGE_MISSILE_MOD).modifyMult(id, OP_COST_MULT);
        stats.getDynamic().getMod(Stats.LARGE_ENERGY_MOD).modifyMult(id, OP_COST_MULT);
        stats.getDynamic().getMod(Stats.LARGE_BALLISTIC_MOD).modifyMult(id, OP_COST_MULT);
    }

    //Removes any non-Vass weapons on the ship
    @Override
    public void applyEffectsAfterShipCreation(ShipAPI ship, String id){

        ArrayList<String> removeList = new ArrayList<String>();

        for(WeaponAPI w : ship.getAllWeapons()){
            if(!w.getSpec().getWeaponId().contains("vass_")){
                removeList.add(w.getSlot().getId());
            }
        }

        for (String s : removeList) {
            ship.getVariant().clearSlot(s);
        }

        removeList.clear();
    }

    @Override
    public boolean affectsOPCosts() {
        return true;
    }

    @Override
    public boolean isApplicableToShip(ShipAPI ship) {
        return (ship != null && ship.getHullSpec().getHullId().contains("vass_"));
    }

    public String getUnapplicableReason(ShipAPI ship) {
        return "How did this happen?";
    }

    @Override
    public String getDescriptionParam(int index, HullSize hullSize) {
        if (index == 0) {
            return "cannot mount normal weapons";
        } else if (index == 1) {
            return "can mount Vass-developed weapons";
        }
        return null;
    }
}
