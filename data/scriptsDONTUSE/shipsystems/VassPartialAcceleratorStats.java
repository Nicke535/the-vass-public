package data.scripts.shipsystems;

import java.awt.Color;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;

public class VassPartialAcceleratorStats extends BaseShipSystemScript {

	public static final float ROF_BONUS = 1f;
	public static final float ROF_BONUS_EX = 2f;
	public static final float DISSIPATION_BONUS = 0.5f;
	public static final float DISSIPATION_BONUS_STABILIZED = 1f;
	
	public static final float MOBILITY_BONUS_ACCELERATED = 2f;

    public static final Color JITTER_COLOR = new Color(192,0,209,10);
    public static final Color JITTER_UNDER_COLOR = new Color(192,0,209,60);
	
	public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        ShipAPI ship = null;
        if (stats.getEntity() instanceof ShipAPI) {
            ship = (ShipAPI) stats.getEntity();
            id = id + "_" + ship.getId();
        } else {
            return;
        }
		
		//Changes the ship sprite - Removed due to issues with jitter alignment
		//ship.setSprite("vass_ship_sprite_changes", "vass_frigate_system");
		
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
		
        ship.setJitter(this, JITTER_COLOR, jitterLevel, 3, 0, 0 + jitterRangeBonus);
        ship.setJitterUnder(this, JITTER_UNDER_COLOR, jitterLevel, 25, 0f, 7f + jitterRangeBonus);
		
		//Stat Modification
		float multRof = 1f + ROF_BONUS * effectLevel;
		float multDissipation = 1f + DISSIPATION_BONUS * effectLevel;
		
		//If we have the Experimental Reactor, we increase the RoF bonus to ROF_BONUS_EX
		if (ship.getVariant().getHullMods().contains("vass_experimental_reactor")) {
			multRof = 1f + ROF_BONUS_EX * effectLevel;
			//Also disables shields if we have the Experimental Reactor
			ship.getShield().setActiveArc(0f);
		}
		
		//Check for Chronostabilized Reactor
		if (ship.getVariant().getHullMods().contains("vass_chronostabilized_reactor")) {
			multDissipation = 1f + DISSIPATION_BONUS_STABILIZED * effectLevel;
		}
		
		//Check for Chronoaccelerated Thrusters
		if (ship.getVariant().getHullMods().contains("vass_chronoaccelerated_thrusters")) {
			stats.getAcceleration().modifyMult(id, 1f + MOBILITY_BONUS_ACCELERATED * effectLevel);
			stats.getDeceleration().modifyMult(id, 1f + MOBILITY_BONUS_ACCELERATED * effectLevel);
			stats.getMaxTurnRate().modifyMult(id, 1f + MOBILITY_BONUS_ACCELERATED * effectLevel);
			stats.getTurnAcceleration().modifyMult(id, 1f + MOBILITY_BONUS_ACCELERATED * effectLevel);
		}
		
		stats.getBallisticRoFMult().modifyMult(id, multRof);
		stats.getFluxDissipation().modifyMult(id, multDissipation);
	}
	
	public void unapply(MutableShipStatsAPI stats, String id) {
        ShipAPI ship = null;
        if (stats.getEntity() instanceof ShipAPI) {
            ship = (ShipAPI) stats.getEntity();
            id = id + "_" + ship.getId();
        } else {
            return;
        }
		//Re-adjusts the ship sprite - Removed due to issues with jitter alignment
		//ship.setSprite("vass_ship_sprite_changes", "vass_frigate_no_system");
	
		stats.getBallisticRoFMult().unmodify(id);
		stats.getFluxDissipation().unmodify(id);
		stats.getAcceleration().unmodify(id);
		stats.getDeceleration().unmodify(id);
		stats.getMaxTurnRate().unmodify(id);
		stats.getTurnAcceleration().unmodify(id);
	}
	
	public StatusData getStatusData(int index, State state, float effectLevel) {
		if (index == 0) {
			return new StatusData("ship systems time-accelerated", false);
		}
		return null;
	}
}