package data.scripts.weapons;

import com.fs.starfarer.api.combat.*;
import data.scripts.utils.VassUtils;
import org.lazywizard.lazylib.MathUtils;

import java.awt.*;

public class VassShipLightsScript implements EveryFrameWeaponEffectPlugin {

    //Various stats for how fast the lights start, stop and blink
    private static final float RECHARGE_TIME = 0.5f;
    private static final float OVERLOAD_FADE_TIME = 0.25f;
    private static final float HULK_FADE_TIME = 20f;
    private static final float TIMER_MULT = 1.5f;

    //The basic color of the lights, when no family's color is used
    private static final float[] COLORS_BASIC = { 0.31f, 1f, 0.15f};

    private float timer = 0f;
    private float currentBrightness = 0f;

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        ShipAPI ship = weapon.getShip();
        if (ship == null) {
            return;
        }
        ShipSystemAPI system = ship.getSystem();
        if (engine == null) {                                       //Refit screen! always use max brightness
            currentBrightness = 1f;
        } else if (ship.isPiece()) {                                //First: are we a piece? If so, instantly lose all opacity
            currentBrightness = 0f;
        } else if (ship.isHulk()) {                                 //Second: are we a hulk? In that case, slowly fade out our color
            currentBrightness -= amount * (1f / HULK_FADE_TIME);
        } else if (ship.getFluxTracker().isOverloadedOrVenting()) { //Third: are we overloading or venting? Then fade out, but pretty fast
            currentBrightness -= amount * (1f / OVERLOAD_FADE_TIME);
        } else {                                                    //If none of the above are correct, we are recharging our lights. Increase the color
            currentBrightness += amount * (1f / RECHARGE_TIME);
        }

        //Keeps track of our timer for blinking
        timer += amount * TIMER_MULT;

        //Sets our current maximum brightness
        float currentMaxBrightness = 0.7f;
        if (system.isActive() || ship.getFluxTracker().isEngineBoostActive() || ship.getTravelDrive().isActive()) {
            currentMaxBrightness += 0.1f;
        }
        if (ship.getEngineController().isFlamedOut()) {
            currentMaxBrightness -= 0.1f;
        }

        //Adds a clock-like effect to our maximum brightness
        currentMaxBrightness += 0.2f * (float)Math.pow(Math.sin(timer), 2f);

        //If our color is above the maximum, set it to the maximum. If it's less than 0, set it to 0
        currentBrightness = Math.max(0f, Math.min(currentMaxBrightness, currentBrightness));

        //Then, actually set the proper opacity that we determined earlier
        weapon.getSprite().setAlphaMult(currentBrightness);

        //Now, set the color to the one we want, and include opacity
        Color colorToUse = new Color(COLORS_BASIC[0], COLORS_BASIC[1], COLORS_BASIC[2], currentBrightness);
        if (system.isActive()) {
            //Accel shipsystem
            if (ship.getSystem().getId().contains("vass_periodic_breaker") || ship.getSystem().getId().contains("vass_periodic_skimmer")) {
                colorToUse = VassUtils.getFamilyColor(VassUtils.VASS_FAMILY.ACCEL, currentBrightness);
            }

            //Torpor shipsystem
            else if (ship.getSystem().getId().contains("vass_time_haven")) {
                colorToUse = VassUtils.getFamilyColor(VassUtils.VASS_FAMILY.TORPOR, currentBrightness);
            }

            //Recipro shipsystem
            else if (ship.getSystem().getId().contains("vass_false_future") || ship.getSystem().getId().contains("vass_temporal_recall")) {
                colorToUse = VassUtils.getFamilyColor(VassUtils.VASS_FAMILY.RECIPRO, currentBrightness);
            }

            //Perturba shipsystem
            else if (ship.getSystem().getId().contains("vass_chrono_disturber")) {
                colorToUse = VassUtils.getFamilyColor(VassUtils.VASS_FAMILY.PERTURBA, currentBrightness);
            }

            //Multa shipsystem
            else if (ship.getSystem().getId().contains("vass_isochronal_multilinker") || ship.getSystem().getId().contains("vass_chrono_jump")) {
                colorToUse = VassUtils.getFamilyColor(VassUtils.VASS_FAMILY.MULTA, currentBrightness);
            }
        }

        //And finally actually apply the color
        weapon.getSprite().setColor(colorToUse);
    }
}