package data.scripts.weapons;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.Misc;
import data.scripts.utils.VassUtils;
import org.lazywizard.lazylib.MathUtils;

import java.awt.*;

public class VassShipLightsScript implements EveryFrameWeaponEffectPlugin {

    //Various stats for how fast the lights start, stop and blink
    private static final float RECHARGE_TIME = 0.5f;
    private static final float OVERLOAD_FADE_TIME = 0.25f;
    private static final float HULK_FADE_TIME = 40f;
    private static final float TIMER_MULT = 1.5f;

    //The basic color of the lights, when no family's color is used
    private static final float[] COLORS_BASIC = {0.31f, 1f, 0.15f};

    private float timer = 0f;
    private float currentBrightness = 0f;
    private VassUtils.VASS_FAMILY family = null;
    private boolean hasCheckedFamily = false;
    private boolean isElite = false;
    private boolean hasCheckedEliteness = false;

    //Multa has some custom stats, as their passive color is pretty weird
    private static final float MULTA_PASSIVE_MIN_COLOR_DURATION = 0.3f;
    private static final float MULTA_PASSIVE_MAX_COLOR_DURATION = 0.7f;
    private Color multaColor1 = Color.black;
    private Color multaColor2 = Color.black;
    private float multaColorProgressTime = 3f;
    private float multaColorTargetTime = MathUtils.getRandomNumberInRange(MULTA_PASSIVE_MIN_COLOR_DURATION, MULTA_PASSIVE_MAX_COLOR_DURATION);

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        ShipAPI ship = weapon.getShip();
        if (ship == null) {
            return;
        }
        if (!hasCheckedFamily) {
            try {
                family = VassUtils.getFamilyMembershipOfShip(ship);
                hasCheckedFamily = true;
            } catch (IllegalStateException e) {
                return;
            }
        }
        if (!hasCheckedEliteness) {
            try {
                isElite = VassUtils.isShipAnElite(ship);
                hasCheckedEliteness = true;
            } catch (IllegalStateException e) {
                return;
            }
        }

        ShipSystemAPI system = ship.getSystem();
        if (engine == null || !engine.isEntityInPlay(ship)) {       //Refit screen! Just use frame 1 instead of our "proper" frame
            weapon.getAnimation().setFrame(0);
            hasCheckedFamily = false;
            hasCheckedEliteness = false;
            return;
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
        currentMaxBrightness += 0.2f * (float) Math.pow(Math.sin(timer), 2f);

        //If our color is above the maximum, set it to the maximum. If it's less than 0, set it to 0
        currentBrightness = MathUtils.clamp(currentBrightness, 0f, currentMaxBrightness);

        //Then, actually set the proper opacity that we determined earlier
        weapon.getSprite().setAlphaMult(currentBrightness);

        //Now, set the color to the one we want, and include opacity
        Color colorToUse = new Color(COLORS_BASIC[0], COLORS_BASIC[1], COLORS_BASIC[2], currentBrightness);
        if (family != null && isElite) {
            if (family == VassUtils.VASS_FAMILY.MULTA) {
                //Multa's passive coloration is a bit special
                colorToUse = handlePassiveMultaColoration(amount / ship.getMutableStats().getTimeMult().getModifiedValue(), currentBrightness);
            } else {
                colorToUse = VassUtils.getFamilyColor(family, currentBrightness);
            }
        }
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
            else if (ship.getSystem().getId().contains("vass_temporal_retreat") || ship.getSystem().getId().contains("vass_temporal_recall")) {
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

        //And finally actually apply the color and switch to the right frame
        weapon.getAnimation().setFrame(1);
        weapon.getSprite().setColor(colorToUse);
    }


    //Multa's passive coloration is a bit odd, so it gets its own function
    private Color handlePassiveMultaColoration(float amount, float brightness) {
        multaColorProgressTime += amount;
        while (multaColorProgressTime > multaColorTargetTime) {
            multaColorProgressTime -= multaColorTargetTime;
            multaColorTargetTime = MathUtils.getRandomNumberInRange(MULTA_PASSIVE_MIN_COLOR_DURATION, MULTA_PASSIVE_MAX_COLOR_DURATION);
            multaColor1 = multaColor2;
            multaColor2 = VassUtils.getFamilyColor(VassUtils.VASS_FAMILY.MULTA, 1f);
        }

        float progress = multaColorProgressTime / multaColorTargetTime;
        Color colorToReturn = Misc.interpolateColor(multaColor1, multaColor2, progress);
        return new Color(colorToReturn.getRed(), colorToReturn.getGreen(), colorToReturn.getBlue(), (int) (brightness * 255f));
    }
}