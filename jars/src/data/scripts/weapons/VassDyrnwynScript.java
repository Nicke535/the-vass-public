//By Nicke535, speeds up the firerate of a weapon depending on a ship's time mult
package data.scripts.weapons;

import com.fs.starfarer.api.combat.*;
import data.scripts.utils.VassTimeDistortionProjScript;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.CombatUtils;

import java.util.ArrayList;
import java.util.List;

public class VassDyrnwynScript implements EveryFrameWeaponEffectPlugin {

    //Maximum firerate bonus achievable with the weapon
    static final float MAX_BONUS_FIRERATE = 0.65f;

    //"Absolute" time mult at which maximum firerate is achieved (0.5f = 2f "absolute")
    static final float MAX_TIME_MULT_FOR_BONUS = 1.8f;

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        //Calculates our "absolute" time mult
        float timeMult = weapon.getShip().getMutableStats().getTimeMult().getModifiedValue();
        timeMult = Math.max(timeMult, 1f/timeMult);

        //Calculates our bonus firerate right now
        float bonusThisFrame = Math.min(MAX_BONUS_FIRERATE, MAX_BONUS_FIRERATE * (1f - timeMult) / (1f - MAX_TIME_MULT_FOR_BONUS));

        //If our current remaining cooldown is above our new, improved max cooldown, bring it down to the new cooldown
        if (weapon.getCooldownRemaining() > (weapon.getCooldown() / (1f + bonusThisFrame))) {
            weapon.setRemainingCooldownTo(weapon.getCooldown() / (1f + bonusThisFrame));
        }
    }
}