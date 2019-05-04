//By Nicke535
//Experience, The World!
package data.scripts.shipsystems;

import java.awt.Color;

import data.scripts.VassModPlugin;
import org.dark.shaders.distortion.DistortionShader;
import org.dark.shaders.distortion.RippleDistortion;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

public class VassPeriodicBreaker extends BaseShipSystemScript {
    //The time mult for the player and AI, respectively. Should be identical, for fairness sake, but if performance becomes
    //an issue lowering AI time mult slightly may help
    public static final float TIME_MULT_PLAYER = 100.0f;
    public static final float TIME_MULT_AI = 100.0f;

    //Affects beam damage and flux cost; we set these to 100%, since we don't want beams doing anything in frozen time
    public static final float BEAM_DAMAGE_PENALTY = 1.0f;
    public static final float BEAM_FLUX_PENALTY = 1.0f;

    //How much is our flux dissipation reduced in frozen time?
    public static final float FLUX_DISSIPATION_PENALTY = 1.0f;

    //Jitter and lightning color for the system
    public static final Color JITTER_COLOR = new Color(255, 26, 26,55);
    public static final Color JITTER_UNDER_COLOR = new Color(255, 0, 0,155);

    //The size of the lightning and distortions from the system
    public static final float ELECTRIC_SIZE = 80.0f;
    public static final float ELECTRIC_SIZE_SCHIAVONA = 300.0f;


    public boolean HAS_FIRED_LIGHTNING = false;

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
		
		//Jitter-based code
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
        jitterLevel *= jitterLevel;

        ship.setJitter(this, JITTER_COLOR, jitterLevel, (int)Math.ceil(4 * jitterLevel), 0, 0 + jitterRangeBonus);
        ship.setJitterUnder(this, JITTER_UNDER_COLOR, jitterLevel, (int)Math.ceil(26 * jitterLevel), 0f, 7f + jitterRangeBonus);

        //We want our effect level to scale near-exponentially
        effectLevel *= effectLevel;
        effectLevel *= effectLevel;

		//Adjusts time mult
		float shipTimeMult = 1f;
        if (player) {
            shipTimeMult = 1f + (TIME_MULT_PLAYER - 1f) * effectLevel;
            stats.getTimeMult().modifyMult(id, shipTimeMult);
            Global.getCombatEngine().getTimeMult().modifyMult(id, 1f / shipTimeMult);
        } else {
            shipTimeMult = 1f + (TIME_MULT_AI - 1f) * effectLevel;
            stats.getTimeMult().modifyMult(id, shipTimeMult);
            Global.getCombatEngine().getTimeMult().unmodify(id);
        }
		
		//Changes engine color
        ship.getEngineController().fadeToOtherColor(this, JITTER_COLOR, new Color(0,0,0,0), 1.0f, 1.0f);
        ship.getEngineController().extendFlame(this, -0.25f, -0.25f, -0.25f);
		
		//Adjusts the size of our lightning and distortions to their correct value
		float actualElectricSize = ELECTRIC_SIZE;
		float rippleDurationMod = 0.6f;
		if (ship.getHullSpec().getHullId().contains("vass_schiavona")) {
			actualElectricSize = ELECTRIC_SIZE_SCHIAVONA;
			rippleDurationMod = 1f;
		}

		
		if (effectLevel >= 0.8f) {
		    //Only triggers once per activation
			if (!HAS_FIRED_LIGHTNING) {
			    //----------------------------------------------------Distortion Handling-----------------------------------------------------------------
                if (VassModPlugin.hasShaderLib) {
                    //Outward ripple
                    RippleDistortion ripple1 = new RippleDistortion(ship.getLocation(), new Vector2f(0f, 0f));
                    ripple1.setIntensity(35f + (actualElectricSize / 6));
                    ripple1.setLifetime(2.5f * rippleDurationMod);
                    ripple1.setAutoAnimateFrameRate(120f, 50f);
                    ripple1.setSize(actualElectricSize * 3f);
                    ripple1.flip(false);
                    ripple1.fadeInSize(3f * rippleDurationMod);
                    ripple1.fadeOutIntensity(2.4f * rippleDurationMod);

                    DistortionShader.addDistortion(ripple1);

                    //Initial ripple, that goes inward
                    RippleDistortion ripple2 = new RippleDistortion(ship.getLocation(), new Vector2f(0f, 0f));
                    ripple2.setIntensity(35f + (actualElectricSize / 12));
                    ripple2.setLifetime(0.25f * rippleDurationMod);
                    ripple2.setAutoAnimateFrameRate(120f, 50f);
                    ripple2.setSize(actualElectricSize / 3);
                    ripple2.flip(true);
                    ripple2.fadeOutSize(0.25f * rippleDurationMod);
                    ripple2.fadeOutIntensity(0.5f * rippleDurationMod);

                    DistortionShader.addDistortion(ripple2);
                }

			    //----------------------------------------------------Lightning handling------------------------------------------------------------------
				HAS_FIRED_LIGHTNING = true;
				/*Lightning based code...*/
				float tempCounter = 0;
				while (tempCounter <= (6.0f / ELECTRIC_SIZE) * actualElectricSize) {
					Global.getCombatEngine().spawnEmpArc(ship,new Vector2f(ship.getLocation().x + MathUtils.getRandomNumberInRange(-actualElectricSize, actualElectricSize), ship.getLocation().y + MathUtils.getRandomNumberInRange(-actualElectricSize, actualElectricSize)), null, ship,
						DamageType.ENERGY, //Damage type
						0f, //Damage
						0f, //Emp
						100000f, //Max range
						"tachyon_lance_emp_impact",
						(10f / ELECTRIC_SIZE) * actualElectricSize, // thickness
						JITTER_COLOR, //Central color
						JITTER_UNDER_COLOR //Fringe Color
					);
				tempCounter++;
				}
				//visual effect
				Global.getCombatEngine().spawnExplosion(
					//where
					ship.getLocation(),
					//speed
					(Vector2f) new Vector2f(0,0),
					//color
					JITTER_COLOR,
					//size
					(MathUtils.getRandomNumberInRange(75f,100f) / ELECTRIC_SIZE) * actualElectricSize,
					//duration
					1.0f
				);
			}
		} else {
			HAS_FIRED_LIGHTNING = false;
		}

        //Reduces beam damage
        stats.getBeamWeaponFluxCostMult().modifyMult(id, 1 - BEAM_FLUX_PENALTY * effectLevel);
        stats.getBeamWeaponDamageMult().modifyMult(id, 1 - BEAM_DAMAGE_PENALTY * effectLevel);
		
		//USED TO check for booster hullmod; now, just reduce flux dissipation
        stats.getFluxDissipation().modifyMult(id, 1 - FLUX_DISSIPATION_PENALTY * effectLevel);
    }

    //Resets all our values to pre-activation state
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
		
		stats.getFluxDissipation().unmodify(id);
		
        Global.getCombatEngine().getTimeMult().unmodify(id);
        stats.getTimeMult().unmodify(id);

		stats.getBeamWeaponFluxCostMult().unmodify(id);
		stats.getBeamWeaponDamageMult().unmodify(id);
    
		HAS_FIRED_LIGHTNING = false;
    }

    //Shows a tooltip in the HUD
    public StatusData getStatusData(int index, State state, float effectLevel) {
        if (index == 0 && state == State.IN) {
            return new StatusData("rupturing time-space...", false);
        } else if (index == 0 && state == State.ACTIVE) {
            return new StatusData("time is at a standstill", false);
		} else if (index == 0 && state == State.OUT) {
            return new StatusData("readjusting protocols...", false);
		}
        return null;
    }
}