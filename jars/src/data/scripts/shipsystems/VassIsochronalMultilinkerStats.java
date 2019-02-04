package data.scripts.shipsystems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import org.lazywizard.lazylib.MathUtils;

import java.awt.*;

public class VassIsochronalMultilinkerStats extends BaseShipSystemScript {

	public static final float DISSIPATION_BONUS_STABILIZED = 4f;
	
	public static final float MOBILITY_BONUS_ACCELERATED = 4f;
	public static final float SPEED_BONUS_ACCELERATED = 100f;

    public static final int JITTER_OPACITY = 10;
    public static final int JITTER_UNDER_OPACITY = 60;
	
	public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        ShipAPI ship = null;
        if (stats.getEntity() instanceof ShipAPI) {
            ship = (ShipAPI) stats.getEntity();
            id = id + "_" + ship.getId();
        } else {
            return;
        }
		
		//Jitter-Based code
	    float jitterLevel = effectLevel;
        float jitterRangeBonus = 0;
        float maxRangeBonus = 5f;
        if (state == State.IN) {
            jitterLevel = effectLevel / (1f / ship.getSystem().getChargeUpDur());
            if (jitterLevel > 1) {
                jitterLevel = 1f;
            }
            jitterRangeBonus = jitterLevel * maxRangeBonus;
        } else if (state == State.ACTIVE) {
            jitterLevel = 1f;
            jitterRangeBonus = maxRangeBonus;
        } else if (state == State.OUT) {
            jitterRangeBonus = jitterLevel * maxRangeBonus;
        }
        jitterLevel = (float) Math.sqrt(jitterLevel);

        Color jitterColor = new Color(230 + MathUtils.getRandomNumberInRange(0, 25), 230 + MathUtils.getRandomNumberInRange(0, 25), 230 + MathUtils.getRandomNumberInRange(0, 25), JITTER_OPACITY);
		Color jitterUnderColor = new Color(230 + MathUtils.getRandomNumberInRange(0, 25), 230 + MathUtils.getRandomNumberInRange(0, 25), 230 + MathUtils.getRandomNumberInRange(0, 25), JITTER_UNDER_OPACITY);

        ship.setJitter(this, jitterColor, jitterLevel, 3, 0, 0 + jitterRangeBonus);
        ship.setJitterUnder(this, jitterUnderColor, jitterLevel, 25, 0f, 7f + jitterRangeBonus);
		
		//Check for Chronostabilized Reactor, and apply appropriate bonuses
		if (ship.getVariant().getHullMods().contains("vass_chronostabilized_reactor")) {
			stats.getFluxDissipation().modifyMult(id, 1f + DISSIPATION_BONUS_STABILIZED * effectLevel);
		}
		
		//Check for Chronoaccelerated Thrusters, and apply appropriate bonuses
		if (ship.getVariant().getHullMods().contains("vass_chronoaccelerated_thrusters")) {
			stats.getAcceleration().modifyMult(id, 1f + MOBILITY_BONUS_ACCELERATED * effectLevel);
			stats.getDeceleration().modifyMult(id, 1f + MOBILITY_BONUS_ACCELERATED * effectLevel);
			stats.getMaxTurnRate().modifyMult(id, 1f + MOBILITY_BONUS_ACCELERATED * effectLevel);
			stats.getTurnAcceleration().modifyMult(id, 1f + MOBILITY_BONUS_ACCELERATED * effectLevel);
			stats.getMaxSpeed().modifyFlat(id, SPEED_BONUS_ACCELERATED * effectLevel);
		}

		//Only charge once per code-run
		if (!(Global.getCombatEngine().getCustomData().get("VassIsochronalChargesRunOnce" + ship.getId()) instanceof Boolean) || (boolean)Global.getCombatEngine().getCustomData().get("VassIsochronalChargesRunOnce" + ship.getId())) {
			Global.getCombatEngine().getCustomData().put("VassIsochronalChargesRunOnce" + ship.getId(), false);

			//If we have the Experimental Reactor, we add 200% charge, otherwise we charge to 100%
			if (ship.getVariant().getHullMods().contains("vass_experimental_reactor")) {
				Global.getCombatEngine().getCustomData().put("VassIsochronalCharges" + ship.getId(), 200f);
				//Also disables shields if we have the Experimental Reactor
				ship.getShield().setActiveArc(0f);
			} else {
				Global.getCombatEngine().getCustomData().put("VassIsochronalCharges" + ship.getId(), 100f);
			}
		}
	}
	
	public void unapply(MutableShipStatsAPI stats, String id) {
        ShipAPI ship = null;
        if (stats.getEntity() instanceof ShipAPI) {
            ship = (ShipAPI) stats.getEntity();
            id = id + "_" + ship.getId();
        } else {
            return;
        }

		stats.getFluxDissipation().unmodify(id);
		stats.getAcceleration().unmodify(id);
		stats.getDeceleration().unmodify(id);
		stats.getMaxTurnRate().unmodify(id);
		stats.getTurnAcceleration().unmodify(id);
		stats.getMaxSpeed().unmodify(id);

		//Reset our RunOnce variable
		Global.getCombatEngine().getCustomData().put("VassIsochronalChargesRunOnce" + ship.getId(), true);
	}
	
	public StatusData getStatusData(int index, State state, float effectLevel) {
		if (index == 0) {
			return new StatusData("accessing timelines...", false);
		}
		return null;
	}
}