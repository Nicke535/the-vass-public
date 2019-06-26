package data.scripts.weapons;

import com.fs.starfarer.api.combat.*;
import data.scripts.utils.VassUtils;

import java.awt.*;

public class VassDecklightsScript implements EveryFrameWeaponEffectPlugin {

    //Various stats for how fast the lights start, stop and blink
    private static final float HULK_FADE_TIME = 20f;

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

        if (ship.isPiece()) {                                       //First: are we a piece? If so, instantly lose all opacity
            currentBrightness = 0f;
        } else if (ship.isHulk()) {                                 //Second: are we a hulk? In that case, slowly fade out our color
            currentBrightness -= amount * (1f / HULK_FADE_TIME);
        } else {                                                    //If none of the above are correct, we are recharging our lights. Increase the color back to normal
            currentBrightness = 1f;
        }

        //And finally actually apply the color
        weapon.getSprite().setColor(new Color(COLORS_BASIC[0], COLORS_BASIC[1], COLORS_BASIC[2], currentBrightness));
    }
}