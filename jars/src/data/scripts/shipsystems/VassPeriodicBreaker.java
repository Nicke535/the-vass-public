package data.scripts.shipsystems;

import java.awt.Color;

import com.fs.starfarer.api.graphics.SpriteAPI;
import data.scripts.VassModPlugin;
import data.scripts.util.MagicRender;
import org.dark.shaders.distortion.DistortionShader;
import org.dark.shaders.distortion.RippleDistortion;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import org.dark.shaders.post.PostProcessShader;
import org.lazywizard.lazylib.FastTrig;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;

/**
 * The World is unstoppable!
 * @author Nicke535
 */
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
    private boolean hasResetPostProcess = true;

    private float afterimageTracker = 0f;
    private static final float AFTERIMAGE_TIME = 0.1f;

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
        ship.setJitterUnder(this, JITTER_UNDER_COLOR, jitterLevel, (int)Math.ceil(20 * jitterLevel), 0f, 5f + jitterRangeBonus);

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

        //NEW: adds fancy post-process effects (if we have shaderlib)
        if (VassModPlugin.hasShaderLib) {
            handlePostprocessing(effectLevel, player);
        }

        //NEW: spawns fancy afterimages if we're the player and the system is fully on
        if (player && effectLevel >= 1f) {
            handleAfterimages(ship);
        }
		
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

        //NEW: removes the fancy post-process effects
        if (!hasResetPostProcess) {
            PostProcessShader.resetDefaults();
            hasResetPostProcess = true;
        }
    
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


    //Handles enabling/disabling postprocess effects, depending on if we're the player and the state of our effectlevel
    private void handlePostprocessing(float effectLevel, boolean player) {
        if (effectLevel > 0f && player) {
            hasResetPostProcess = false;
            PostProcessShader.setHueShift(false, 360f * (float)Math.sqrt(Math.sqrt(effectLevel)));
            PostProcessShader.setRedHSL(false,
                    0f,
                    1f + 0.3f*effectLevel,
                    1f);
            PostProcessShader.setYellowHSL(false,
                    -0.12f * effectLevel,
                    1f - 0.2f*effectLevel,
                    1f);
            PostProcessShader.setGreenHSL(false,
                    0.12f * effectLevel,
                    1f - 0.2f*effectLevel,
                    1f);
            PostProcessShader.setTealHSL(false,
                    0f,
                    1f + 0.3f*effectLevel,
                    1f);
            PostProcessShader.setBlueHSL(false,
                    -0.12f * effectLevel,
                    1f - 0.2f*effectLevel,
                    1f);
            PostProcessShader.setMagentaHSL(false,
                    0.12f * effectLevel,
                    1f - 0.2f*effectLevel,
                    1f);
            float firstNoiseLevel = 0f;
            if ((float)Math.sqrt(Math.sqrt(effectLevel)) < 0.1f) {
                firstNoiseLevel = (float)Math.sqrt(Math.sqrt(effectLevel)) * 0.7f;
            } else {
                firstNoiseLevel = 0.7f * (1.1f - (float)Math.sqrt(Math.sqrt(effectLevel)));
            }
            PostProcessShader.setNoise(false, firstNoiseLevel + 0.15f * effectLevel);
        } else if (!hasResetPostProcess) {
            PostProcessShader.resetDefaults();
            hasResetPostProcess = true;
        }
    }


    //Spawns afterimages that persist in "true" time, meaning they basically only exist in our perception and not an outside one
    private void handleAfterimages(ShipAPI ship) {
        afterimageTracker += Global.getCombatEngine().getElapsedInLastFrame() / Global.getCombatEngine().getTimeMult().getModifiedValue();
        if (afterimageTracker > AFTERIMAGE_TIME) {
            afterimageTracker -= AFTERIMAGE_TIME;

            // Sprite offset fuckery - Don't you love trigonometry?
            SpriteAPI sprite = ship.getSpriteAPI();
            float offsetX = sprite.getWidth()/2 - sprite.getCenterX();
            float offsetY = sprite.getHeight()/2 - sprite.getCenterY();

            float trueOffsetX = (float) FastTrig.cos(Math.toRadians(ship.getFacing()-90f))*offsetX - (float)FastTrig.sin(Math.toRadians(ship.getFacing()-90f))*offsetY;
            float trueOffsetY = (float)FastTrig.sin(Math.toRadians(ship.getFacing()-90f))*offsetX + (float)FastTrig.cos(Math.toRadians(ship.getFacing()-90f))*offsetY;

            MagicRender.battlespace(
                    Global.getSettings().getSprite(ship.getHullSpec().getSpriteName()),
                    new Vector2f(ship.getLocation().getX()+trueOffsetX,ship.getLocation().getY()+trueOffsetY),
                    new Vector2f(ship.getVelocity().x * -1f, ship.getVelocity().y * -1f),
                    new Vector2f(ship.getSpriteAPI().getWidth(), ship.getSpriteAPI().getHeight()),
                    new Vector2f(0, 0),
                    ship.getFacing()-90f,
                    ship.getAngularVelocity() * -1f,
                    new Color(JITTER_UNDER_COLOR.getRed()/255f, JITTER_UNDER_COLOR.getGreen()/255f, JITTER_UNDER_COLOR.getBlue()/255f, 0.02f),
                    true,
                    0f,
                    0f,
                    0.75f * Global.getCombatEngine().getTimeMult().getModifiedValue(),
                    CombatEngineLayers.BELOW_SHIPS_LAYER);
        }
    }
}