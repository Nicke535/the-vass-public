package data.scripts.shipsystems;

import java.awt.Color;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.plugins.ShipSystemStatsScript;

public class VassChronoAccel extends BaseShipSystemScript {
    public static final float TIME_MULT_PLAYER = 2.0f;
    public static final float TIME_MULT_AI = 2.0f;
	
	public static final float FLUX_DISSIPATION_BONUS_STABILIZED = 1.0f;

    public static final Color JITTER_COLOR = new Color(200, 255, 0, 55);
    public static final Color JITTER_UNDER_COLOR = new Color(200, 255, 0, 155);


    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        ShipAPI ship = null;
        boolean player = false;
        if (stats.getEntity() instanceof ShipAPI) {
            ship = (ShipAPI) stats.getEntity();
            player = ship == Global.getCombatEngine().getPlayerShip();
            id = id + "_" + ship.getId();
        } else {
            return;
        }

        float jitterLevel = effectLevel;
        float jitterRangeBonus = 0;
        float maxRangeBonus = 10f;
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
        effectLevel *= effectLevel;

        ship.setJitter(this, JITTER_COLOR, jitterLevel, 3, 0, 0 + jitterRangeBonus);
        ship.setJitterUnder(this, JITTER_UNDER_COLOR, jitterLevel, 25, 0f, 7f + jitterRangeBonus);


        if (player) {
            float shipTimeMult = 1f + (TIME_MULT_PLAYER - 1f) * effectLevel;
            stats.getTimeMult().modifyMult(id, shipTimeMult);
            Global.getCombatEngine().getTimeMult().modifyMult(id, 1f / shipTimeMult);
        } else {
            float shipTimeMult = 1f + (TIME_MULT_AI - 1f) * effectLevel;
            stats.getTimeMult().modifyMult(id, shipTimeMult);
            Global.getCombatEngine().getTimeMult().unmodify(id);
        }

        ship.getEngineController().fadeToOtherColor(this, JITTER_COLOR, JITTER_COLOR, 1.0f, 1.0f);
		
		//Checks for chronostabilized reactor and applies the		
		if (ship.getVariant().getHullMods().contains("vass_chronostabilized_reactor")) {
			stats.getFluxDissipation().modifyMult(id, 1 + FLUX_DISSIPATION_BONUS_STABILIZED * effectLevel);
		}
		
		//Burndrive-related code
		if (state == ShipSystemStatsScript.State.OUT) {
			stats.getMaxSpeed().unmodify(id);
		} else {
			//Checks for Chronoaccelerated Thrusters, and applies its bonus
			if (ship.getVariant().getHullMods().contains("vass_chronoaccelerated_thrusters")) {
				stats.getMaxSpeed().modifyFlat(id, 300f * effectLevel);
			} else {
				stats.getMaxSpeed().modifyFlat(id, 200f * effectLevel);
			}
			stats.getAcceleration().modifyFlat(id, 200f * effectLevel);
		}
    }


    public void unapply(MutableShipStatsAPI stats, String id) {
        ShipAPI ship = null;
        boolean player = false;
        if (stats.getEntity() instanceof ShipAPI) {
            ship = (ShipAPI) stats.getEntity();
            player = ship == Global.getCombatEngine().getPlayerShip();
            id = id + "_" + ship.getId();
        } else {
            return;
        }

        Global.getCombatEngine().getTimeMult().unmodify(id);
        stats.getTimeMult().unmodify(id);
		
		stats.getMaxSpeed().unmodify(id);
		stats.getAcceleration().unmodify(id);
		stats.getDeceleration().unmodify(id);
		
		stats.getFluxDissipation().unmodify(id);
    }

    public StatusData getStatusData(int index, State state, float effectLevel) {
        if (index == 0) {
            return new StatusData("time flow altered", false);
        }
		if (index == 1) {
			return new StatusData("all power to thrusters", false);
		}
//		if (index == 2) {
//			return new StatusData("increased acceleration", false);
//		}
        return null;
    }
}