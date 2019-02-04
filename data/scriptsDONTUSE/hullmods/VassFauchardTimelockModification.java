package data.scripts.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShieldAPI;
import com.fs.starfarer.api.combat.ShieldAPI.ShieldType;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;

public class VassFauchardTimelockModification extends BaseHullMod {

	//Does nothing, just here to prevent use on other ships than the Zhanmadao
	@Override
	public boolean isApplicableToShip(ShipAPI ship) {
		boolean canBeApplied = false;
		return canBeApplied;
	}
}
