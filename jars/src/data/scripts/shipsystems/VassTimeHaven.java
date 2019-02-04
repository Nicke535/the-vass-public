package data.scripts.shipsystems;

import java.awt.Color;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import org.lazywizard.lazylib.MathUtils;

public class VassTimeHaven extends BaseShipSystemScript {
    //The time mult experienced by the ship when using the system
    public static final float TIME_MULT = 0.125f;

    //The jitter color when the system is in use
    public static final Color JITTER_COLOR = new Color(51,153,255,55);
    public static final Color JITTER_UNDER_COLOR = new Color(25,100,255,155);


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

        //Add jitter to the ship
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
        ship.setJitter(this, JITTER_COLOR, jitterLevel, 3, 0, 0 + jitterRangeBonus);
        ship.setJitterUnder(this, JITTER_UNDER_COLOR, jitterLevel, 25, 0f, 7f + jitterRangeBonus);

        //Make the scaling quadratic
        effectLevel *= effectLevel;

        //Modify our time mult
        if (player) {
            float shipTimeMult = 1f + (TIME_MULT - 1f) * effectLevel;
            stats.getTimeMult().modifyMult(id, shipTimeMult);
            Global.getCombatEngine().getTimeMult().modifyMult(id, 1f / shipTimeMult);
        } else {
            float shipTimeMult = 1f + (TIME_MULT - 1f) * effectLevel;
            stats.getTimeMult().modifyMult(id, shipTimeMult);
            Global.getCombatEngine().getTimeMult().unmodify(id);
        }

        //Turn off the engines visually
        ship.getEngineController().fadeToOtherColor(this, JITTER_COLOR, new Color(0,0,0,0), 1.0f, 1.0f);
        ship.getEngineController().extendFlame(this, -0.25f, -0.25f, -0.25f);
		
		//Completely ignores damage once the system reaches max efficiency
        stats.getShieldDamageTakenMult().modifyMult(id, 1f - (1f * (float)Math.sqrt((double)effectLevel)));
        stats.getArmorDamageTakenMult().modifyMult(id, 1f - (1f * (float)Math.sqrt((double)effectLevel)));
        stats.getHullDamageTakenMult().modifyMult(id, 1f - (1f * (float)Math.sqrt((double)effectLevel)));

		//Stop the ship when in use
        stats.getMaxSpeed().modifyMult(id, 0.0f);
        stats.getMaxTurnRate().modifyMult(id, 0.0f);
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

        stats.getShieldDamageTakenMult().unmodify(id);
        stats.getArmorDamageTakenMult().unmodify(id);
        stats.getHullDamageTakenMult().unmodify(id);
		stats.getMaxSpeed().unmodify(id);
		stats.getMaxTurnRate().unmodify(id);
		stats.getFluxDissipation().unmodify(id);
    }

    public StatusData getStatusData(int index, State state, float effectLevel) {
        if (index == 0) {
            return new StatusData("safe from the passage of time", false);
        }
        return null;
    }
}