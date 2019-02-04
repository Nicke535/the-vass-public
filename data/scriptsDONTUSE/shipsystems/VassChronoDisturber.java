package data.scripts.shipsystems;

import java.awt.Color;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.util.IntervalUtil;

public class VassChronoDisturber extends BaseShipSystemScript {
    public static final float TIME_MULT_CHANGE = 0.90f;
    public static final float TIME_MULT_MAX = 0.7f;
    public static final float TIME_MULT_MAX_EX = 0.4f;
    public static final float TIME_MULT_SELF_EX = 0.75f;
	
	public static final float FLUX_DISSIPATION_BONUS_STABILIZED = 0.3f;
	
	public static final float MOBILITY_BONUS_ACCELERATED = 0.3f;

    public static final Color JITTER_COLOR = new Color(200, 255, 0, 45);
    public static final Color JITTER_UNDER_COLOR = new Color(200, 255, 0, 125);
	
    public static final Color JITTER_COLOR_ENEMY = new Color(200, 255, 0, 15);
    public static final Color JITTER_UNDER_COLOR_ENEMY = new Color(200, 255, 0, 25);


    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        ShipAPI ship = null;
        if (stats.getEntity() instanceof ShipAPI) {
            ship = (ShipAPI) stats.getEntity();
            id = id + "_" + ship.getId();
        } else {
            return;
        }

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
        effectLevel *= effectLevel;

        ship.setJitter(this, JITTER_COLOR, jitterLevel, 3, 0, 0 + jitterRangeBonus);
        ship.setJitterUnder(this, JITTER_UNDER_COLOR, jitterLevel, 25, 0f, 7f + jitterRangeBonus);

        //active_range is the range at which the AOE benefits are applied. Should match the radius from the other script.
        float active_range = 800f;

        boolean playerIsAffected = false;
        boolean playerIsAffectedSecondary = false;
		
		//Checks the current slow increase for this frame
		float currentFrameTimeMultChange = (float)Math.pow(TIME_MULT_CHANGE, Global.getCombatEngine().getElapsedInLastFrame() * effectLevel);
		
		float timeMultActualMax = TIME_MULT_MAX;
		//If we have an Experimental Reactor, slow the main ship as well, and increase the maximum time mult
		if (ship.getVariant().getHullMods().contains("vass_experimental_reactor")){
			ship.getMutableStats().getTimeMult().modifyMult(id, 1f + (TIME_MULT_SELF_EX - 1f) * effectLevel);
			if (ship == Global.getCombatEngine().getPlayerShip()) {
				playerIsAffectedSecondary = true;
			}
			timeMultActualMax = TIME_MULT_MAX_EX;
		}
		
        //Iterates through all ships on the map.
        for (ShipAPI shp : Global.getCombatEngine().getShips()) {
            //We don't care about this ship if it's disabled
            if (shp.isHulk()) {
                continue;
            }

			//If the distance to the ship is less than or equal to the active_range...
            if (MathUtils.getDistance(shp, ship) <= (active_range)) {
                //If the owner of shp and ship is not on same side, change the enemy time mult
                if (shp.getOwner() != ship.getOwner()) {
					//First increase the current change in time mult, in case we haven't reached the max
					if (shp.getMutableStats().getDynamic().getStat("VassChronoDisturberTracker" + id).getModifiedValue() > timeMultActualMax) {
						shp.getMutableStats().getDynamic().getStat("VassChronoDisturberTracker" + id).modifyMult(id, shp.getMutableStats().getDynamic().getStat("VassChronoDisturberTracker" + id).getModifiedValue() * currentFrameTimeMultChange);
					} else {
						shp.getMutableStats().getDynamic().getStat("VassChronoDisturberTracker" + id).modifyMult(id, timeMultActualMax);
					}
					if (shp == Global.getCombatEngine().getPlayerShip()) {
						playerIsAffected = true;
					}
					
					//Applies time mult...
					shp.getMutableStats().getTimeMult().modifyMult(id, shp.getMutableStats().getDynamic().getStat("VassChronoDisturberTracker" + id).getModifiedValue());
					
					//And jitter, to indicate they are affected
					shp.setJitter(this, JITTER_COLOR_ENEMY, jitterLevel * ((1 - shp.getMutableStats().getDynamic().getStat("VassChronoDisturberTracker" + id).getModifiedValue()) / (1 - TIME_MULT_MAX)), 3, 0, 0 + jitterRangeBonus);
					shp.setJitterUnder(this, JITTER_UNDER_COLOR_ENEMY, jitterLevel * ((1 - shp.getMutableStats().getDynamic().getStat("VassChronoDisturberTracker" + id).getModifiedValue()) / (1 - TIME_MULT_MAX)), 25, 0f, 7f + jitterRangeBonus);
					
					//Finally, change the global time mult if the player is affected
					if (shp == Global.getCombatEngine().getPlayerShip()) {
						playerIsAffected = true;
						Global.getCombatEngine().getTimeMult().modifyMult(id, 1f / shp.getMutableStats().getDynamic().getStat("VassChronoDisturberTracker" + id).getModifiedValue());
					}
                }
            }
			//Otherwise, unmodify the ships time mult so no extra ships are affected
			else {
				shp.getMutableStats().getTimeMult().unmodify(id);
				shp.getMutableStats().getDynamic().getStat("VassChronoDisturberTracker" + id).unmodify(id);
			}
        }
		
		//Alters global time mult to match the player's
		if (playerIsAffectedSecondary) {
			Global.getCombatEngine().getTimeMult().modifyMult(id, 1f / (1f + (TIME_MULT_SELF_EX - 1f) * effectLevel));
		} else if (!playerIsAffected) {
			Global.getCombatEngine().getTimeMult().unmodify(id);
		}

        ship.getEngineController().fadeToOtherColor(this, JITTER_COLOR, new Color(0,0,0,0), 1.0f, 1.0f);
        ship.getEngineController().extendFlame(this, -0.25f, -0.25f, -0.25f);
		
		if (ship.getVariant().getHullMods().contains("vass_chronostabilized_reactor")) {
			stats.getFluxDissipation().modifyMult(id, 1 + (FLUX_DISSIPATION_BONUS_STABILIZED * effectLevel));
		}

		//Check for Chronoaccelerated Thrusters, and increase mobility if found
		if (ship.getVariant().getHullMods().contains("vass_chronoaccelerated_thrusters")) {
			stats.getAcceleration().modifyMult(id, 1f + MOBILITY_BONUS_ACCELERATED * effectLevel);
			stats.getDeceleration().modifyMult(id, 1f + MOBILITY_BONUS_ACCELERATED * effectLevel);
			stats.getMaxTurnRate().modifyMult(id, 1f + MOBILITY_BONUS_ACCELERATED * effectLevel);
			stats.getTurnAcceleration().modifyMult(id, 1f + MOBILITY_BONUS_ACCELERATED * effectLevel);
			stats.getMaxSpeed().modifyMult(id, 1f + MOBILITY_BONUS_ACCELERATED * effectLevel);
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
		
		stats.getAcceleration().unmodify(id);
		stats.getDeceleration().unmodify(id);
		stats.getMaxTurnRate().unmodify(id);
		stats.getTurnAcceleration().unmodify(id);
		stats.getMaxSpeed().unmodify(id);
		
        //Iterates through all ships on the map, and unapplies the time mult and time mult tracker
        for (ShipAPI shp : Global.getCombatEngine().getShips()) {
			shp.getMutableStats().getDynamic().getStat("VassChronoDisturberTracker" + id).unmodify(id);
			shp.getMutableStats().getTimeMult().unmodify(id);
        }
		
        Global.getCombatEngine().getTimeMult().unmodify(id);
		stats.getFluxDissipation().unmodify(id);
    }

    public StatusData getStatusData(int index, State state, float effectLevel) {
        if (index == 0) {
            return new StatusData("slowing nearby enemy ships", false);
        }
//		if (index == 1) {
//			return new StatusData("beam weapons are useless now", false);
//		}
//		if (index == 2) {
//			return new StatusData("increased acceleration", false);
//		}
        return null;
    }
}