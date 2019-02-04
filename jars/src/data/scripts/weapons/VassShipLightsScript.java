package data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import data.scripts.plugins.VassSpriteRenderManager;
import data.scripts.shipsystems.VassChronoDisturber;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.entities.SimpleEntity;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.Random;

public class VassShipLightsScript implements EveryFrameWeaponEffectPlugin {

    private static final float RECHARGE_TIME = 0.5f;
    private static final float OVERLOAD_FADE_TIME = 0.25f;
    private static final float HULK_FADE_TIME = 20f;
    private static final float TIMER_MULT = 1.5f;

    private static final float[] COLORS_BASIC = { 0.31f, 1f, 0.15f};
    private static final float[] COLORS_RECIPRO = { 1f, 1f, 1f};
    private static final float[] COLORS_ACCEL = { 1f, 0f, 0f};
    private static final float[] COLORS_TORPOR = { 0.1f, 0.4f, 1f};
    private static final float[] COLORS_PERTURBA = { 0.8f, 1f, 0f};

    private float timer = 0f;
    private float currentBrightness = 0f;

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        ShipAPI ship = weapon.getShip();
        if (ship == null) {
            return;
        }
        ShipSystemAPI system = ship.getSystem();

        if (ship.isPiece()) {                                       //First: are we a piece? If so, instantly lose all opacity
            currentBrightness = 0f;
        } else if (ship.isHulk()) {                                 //Second: are we a hulk? In that case, slowly fade out our color
            currentBrightness -= amount * (1f / HULK_FADE_TIME);
        } else if (ship.getFluxTracker().isOverloadedOrVenting()) { //Third: are we overloading or venting? Then fade out, but pretty fast
            currentBrightness -= amount * (1f / OVERLOAD_FADE_TIME);
        } else {                                                    //If none of the above are correct, we are recharging our lights. Increase the color
            currentBrightness += amount * (1f / RECHARGE_TIME);
        }

        //Keeps track of our timer
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
        if (ship.getVariant().getHullMods().contains("vass_periodic_plating_recipro") || (ship.getHullSpec().getHullId().contains("vass_estoc") && system.isActive())) {
            colorToUse = new Color(COLORS_RECIPRO[0], COLORS_RECIPRO[1], COLORS_RECIPRO[2], currentBrightness);
        } else if (ship.getVariant().getHullMods().contains("vass_periodic_plating_accel") || (system.isActive() && (ship.getHullSpec().getHullId().contains("vass_schiavona") || ship.getHullSpec().getHullId().contains("vass_katzbalger")))) {
            colorToUse = new Color(COLORS_ACCEL[0], COLORS_ACCEL[1], COLORS_ACCEL[2], currentBrightness);
        } else if (ship.getVariant().getHullMods().contains("vass_periodic_plating_multa") || ((ship.getHullSpec().getHullId().contains("vass_makhaira") || ship.getHullSpec().getHullId().contains("vass_jataghan")) && system.isActive())) {
            //Multa is wierd, in that they have a randomness to their color each frame
            colorToUse = new Color(MathUtils.getRandomNumberInRange(0.7f, 1f), MathUtils.getRandomNumberInRange(0.7f, 1f), MathUtils.getRandomNumberInRange(0.7f, 1f), currentBrightness);
        } else if (ship.getVariant().getHullMods().contains("vass_periodic_plating_torpor") || (ship.getHullSpec().getHullId().contains("vass_curtana") && system.isActive())) {
            colorToUse = new Color(COLORS_TORPOR[0], COLORS_TORPOR[1], COLORS_TORPOR[2], currentBrightness);
        } else if (ship.getVariant().getHullMods().contains("vass_periodic_plating_perturba") || (ship.getHullSpec().getHullId().contains("vass_zhanmadao") && system.isActive())) {
            colorToUse = new Color(COLORS_PERTURBA[0], COLORS_PERTURBA[1], COLORS_PERTURBA[2], currentBrightness);
        }

        //And finally actually apply the color
        weapon.getSprite().setColor(colorToUse);
    }
}