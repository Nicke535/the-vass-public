package data.scripts.hullmods;

import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShieldAPI;
import com.fs.starfarer.api.combat.ShieldAPI.ShieldType;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;

public class VassExperimentalReactor extends BaseHullMod {

	//Does nothing, just here to prevent use on other ships than Vass ones, and makes it incompatible with Chronostabilized Reactor
	@Override
	public boolean isApplicableToShip(ShipAPI ship) {
		return ship != null && ship.getHullSpec().getHullId().contains("vass_") && !ship.getVariant().getHullMods().contains("vass_chronostabilized_reactor");
	}

    public String getUnapplicableReason(ShipAPI ship) {
        if (!ship.getHullSpec().getHullId().contains("vass_")) {
            return "Can only be mounted on the Makhaira, Schiavona, Curtana or Zhanmadao";
        } else if (ship.getVariant().getHullMods().contains("vass_experimental_reactor")) {
            return "Cannot be combined with the Vass Chronostabilized Reactor";
        } else {
            return "BUG: report to mod author";
        }
    }
}
