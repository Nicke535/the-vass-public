package data.scripts.hullmods;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShieldAPI;
import com.fs.starfarer.api.combat.ShieldAPI.ShieldType;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.MissileAPI;
import org.lazywizard.lazylib.combat.CombatUtils;

public class VassReactiveChronalDissonator extends BaseHullMod {
  public static final float TIME_MULT = 0.65f;
  public static final float TIME_MULT_SUPERCLOSE = 0.25f;
  public static final float EFFECT_RANGE = 200f;
  public static final float EFFECT_RANGE_CLOSE = 100f;
    
	
	//Changes the ships time mult at every "advanceInCombat", in order to make sure the global time mult is correct in relation to the player ship
	@Override
	public void advanceInCombat(ShipAPI ship, float amount) {
		if (ship == Global.getCombatEngine().getPlayerShip() && !ship.isHulk() && !ship.getSystem().isOn()) {
      boolean shouldSlowTime = false;
      boolean shouldSlowTimeAlot = false;
      
      for (DamagingProjectileAPI proj : CombatUtils.getProjectilesWithinRange(ship.getLocation(), EFFECT_RANGE)) {
        if (proj.getSource().getOwner() != ship.getOwner()) {
          shouldSlowTime = true;
          break;
        }
      }      
      for (MissileAPI proj : CombatUtils.getMissilesWithinRange(ship.getLocation(), EFFECT_RANGE)) {
        if (proj.getSource().getOwner() != ship.getOwner() || shouldSlowTime) {
          shouldSlowTime = true;
          break;
        }
      }
      for (DamagingProjectileAPI proj : CombatUtils.getProjectilesWithinRange(ship.getLocation(), EFFECT_RANGE_CLOSE)) {
        if (proj.getSource().getOwner() != ship.getOwner() && proj.getDamageAmount() >= ship.getHullLevel() * ship.getMaxHitpoints() * 0.5f) {
          shouldSlowTimeAlot = true;
          break;
        }
      }
      for (MissileAPI proj : CombatUtils.getMissilesWithinRange(ship.getLocation(), EFFECT_RANGE_CLOSE)) {
        if ((proj.getSource().getOwner() != ship.getOwner() && proj.getDamageAmount() >= ship.getHullLevel() * ship.getMaxHitpoints() * 0.5f) || shouldSlowTimeAlot) {
          shouldSlowTimeAlot = true;
          break;
        }
      }
    
			if (shouldSlowTimeAlot) {
				float timeMult = 1f + (TIME_MULT_SUPERCLOSE - 1f);
				Global.getCombatEngine().getTimeMult().modifyMult("VassReactiveChronalDissonatorDebugID", timeMult);
			} else if (shouldSlowTime) {
				float timeMult = 1f + (TIME_MULT - 1f);
				Global.getCombatEngine().getTimeMult().modifyMult("VassReactiveChronalDissonatorDebugID", timeMult);
			} else {
				Global.getCombatEngine().getTimeMult().unmodify("VassReactiveChronalDissonatorDebugID");
			}
		} else {
			Global.getCombatEngine().getTimeMult().unmodify("VassReactiveChronalDissonatorDebugID");
		}
	}

	//Prevents the hullmod from being put on ships
	@Override
	public boolean isApplicableToShip(ShipAPI ship) {
		boolean canBeApplied = false;
		return canBeApplied;
	}
}
