//By Nicke535
//Adds random amounts of firerate bonus and shield resist
package data.scripts.shipsystems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.util.IntervalUtil;
import data.scripts.utils.VassUtils;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class VassIsochronalMultilinkerStats extends BaseShipSystemScript {

	//Basic jitter stats
    public static final float JITTER_OPACITY = 0.07f;
    public static final float JITTER_UNDER_OPACITY = 0.3f;

    //The highest firerate mult the system can give (converts to beam DPS on beams at 50% efficiency)
    private static final float MAX_ROF_MULT = 4f;

    //The lowest firerate mult the system can give (converts to beam DPS on beams at 50% efficiency)
    private static final float MIN_ROF_MULT = 0.35f;

    //How fast the firerate mults change for the ship
    private static final float MAX_ROF_CHANGE_PER_SECOND = 50f;

    //The shortest/longest time between "target" RoF changes
    private static final float MAX_ROF_TARGET_CHANGE_DELAY = 0.25f;
    private static final float MIN_ROF_TARGET_CHANGE_DELAY = 0.12f;

    //The lowest/highest shield damage mod obtainable
    private static final float MAX_SHIELD_RESIST_MULT = 3f;
    private static final float MIN_SHIELD_RESIST_MULT = 0.33f;


    //Internal variables
    float currentBallisticRoF = 1f;
    float currentTargetBallisticRoF = 1f;
    float currentEnergyRoF = 1f;
    float currentTargetEnergyRoF = 1f;
    IntervalUtil tracker = new IntervalUtil(MIN_ROF_TARGET_CHANGE_DELAY, MAX_ROF_TARGET_CHANGE_DELAY);

	public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        ShipAPI ship = null;
        if (stats.getEntity() instanceof ShipAPI) {
            ship = (ShipAPI) stats.getEntity();
            id = id + "_" + ship.getId();
        } else {
            return;
        }
		
		//Adds some random-color jitter
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

        //Adds multiple jitters, each with a different color
		for (int i = 0; i < 5; i++) {
			Color jitterColor = VassUtils.getFamilyColor(VassUtils.VASS_FAMILY.MULTA, JITTER_OPACITY);
			ship.setJitter(id + i, jitterColor, jitterLevel, 3, 5f, 7f + jitterRangeBonus);
		}
		for (int i = 0; i < 25; i++) {
			Color jitterUnderColor = VassUtils.getFamilyColor(VassUtils.VASS_FAMILY.MULTA, JITTER_UNDER_OPACITY);
			ship.setJitterUnder(id + i, jitterUnderColor, jitterLevel, 25, 1f, 7f + jitterRangeBonus);
		}

        //Count up our tracker, and change our "target" RoF bonus if it has passed
        float amount = Global.getCombatEngine().getElapsedInLastFrame() * stats.getTimeMult().getModifiedValue();
        tracker.advance(amount);
        if (tracker.intervalElapsed()) {
            currentTargetBallisticRoF = MathUtils.getRandomNumberInRange(MIN_ROF_MULT, MAX_ROF_MULT);
            currentTargetEnergyRoF = MathUtils.getRandomNumberInRange(MIN_ROF_MULT, MAX_ROF_MULT);
        }

        //Changes shield color each frame
        ship.getShield().setInnerColor(VassUtils.getFamilyColor(VassUtils.VASS_FAMILY.MULTA, 0.8f));
        ship.getShield().setRingColor(VassUtils.getFamilyColor(VassUtils.VASS_FAMILY.MULTA, 0.8f));

        //Swap our actual RoF mult over closer to our target RoF
        if (currentBallisticRoF < currentTargetBallisticRoF) {
            currentBallisticRoF += MAX_ROF_CHANGE_PER_SECOND * amount;
        } else {
            currentBallisticRoF -= MAX_ROF_CHANGE_PER_SECOND * amount;
        }
        if (currentEnergyRoF < currentTargetEnergyRoF) {
            currentEnergyRoF += MAX_ROF_CHANGE_PER_SECOND * amount;
        } else {
            currentEnergyRoF -= MAX_ROF_CHANGE_PER_SECOND * amount;
        }

        //Ensure it's in the right interval
        currentBallisticRoF = Math.min(MAX_ROF_MULT, Math.max(MIN_ROF_MULT, currentBallisticRoF));
        currentEnergyRoF = Math.min(MAX_ROF_MULT, Math.max(MIN_ROF_MULT, currentEnergyRoF));

        //Finally, apply our new stat boosts
        stats.getBallisticRoFMult().modifyMult(id, currentBallisticRoF);
        stats.getEnergyRoFMult().modifyMult(id, currentEnergyRoF);
        stats.getBeamWeaponDamageMult().modifyMult(id, (float)Math.sqrt(currentEnergyRoF));
        stats.getShieldDamageTakenMult().modifyMult(id, 1f / MathUtils.getRandomNumberInRange(MIN_SHIELD_RESIST_MULT, MAX_SHIELD_RESIST_MULT));
	}
	
	public void unapply(MutableShipStatsAPI stats, String id) {
        ShipAPI ship = null;
        if (stats.getEntity() instanceof ShipAPI) {
            ship = (ShipAPI) stats.getEntity();
            id = id + "_" + ship.getId();
        } else {
            return;
        }

        //Resets stats
        stats.getBallisticRoFMult().unmodify(id);
        stats.getEnergyRoFMult().unmodify(id);
        stats.getBeamWeaponDamageMult().unmodify(id);
        stats.getShieldDamageTakenMult().unmodify(id);

        //Resets shield color
        ship.getShield().setRingColor(ship.getHullSpec().getShieldSpec().getRingColor());
        ship.getShield().setInnerColor(ship.getHullSpec().getShieldSpec().getInnerColor());
	}
	
	public StatusData getStatusData(int index, State state, float effectLevel) {
		if (index == 0) {
			return new StatusData("Timeline instability established", false);
		}
		return null;
	}
}