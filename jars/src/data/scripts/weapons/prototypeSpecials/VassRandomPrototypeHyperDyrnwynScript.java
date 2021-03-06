package data.scripts.weapons.prototypeSpecials;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.Misc;
import data.scripts.utils.VassTimeDistortionProjScript;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.CombatUtils;

import java.util.LinkedList;

/**
 * Very similar to the Dyrnwyn script, but immensely boosted
 * @author Nicke535
 */
public class VassRandomPrototypeHyperDyrnwynScript implements EveryFrameWeaponEffectPlugin {

    //A base firerate increase over the normal gun
    private static final float BASE_BONUS_RELOAD_MULT = 0.2f;

    //Maximum firerate bonus achievable with the weapon
    private static final float MAX_BONUS_FIRERATE = 0.65f;

    //"Absolute" time mult at which maximum firerate is achieved (0.5f = 2f "absolute")
    private static final float MAX_TIME_MULT_FOR_BONUS = 1.8f;

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        //Calculates our "absolute" time mult
        float timeMult = weapon.getShip().getMutableStats().getTimeMult().getModifiedValue();
        timeMult = Math.max(timeMult, 1f/timeMult);

        //Check for unique Perturba bonus
        float maxTimeMultForBonus = MAX_TIME_MULT_FOR_BONUS;
        Object hasPerturbaBonus = engine.getCustomData().get("VassPerturbaPeriodicPlatingBonus" + weapon.getShip().getId());
        if (hasPerturbaBonus instanceof Boolean && (Boolean)hasPerturbaBonus) {
            maxTimeMultForBonus = Misc.interpolate(maxTimeMultForBonus, 1f, 0.5f);
        }

        //Calculates our bonus firerate right now
        float bonusThisFrame = Math.min(MAX_BONUS_FIRERATE, MAX_BONUS_FIRERATE * (1f - timeMult) / (1f - maxTimeMultForBonus));

        //If our current remaining cooldown is above our new, improved max cooldown, bring it down to the new cooldown
        if (weapon.getCooldownRemaining() > BASE_BONUS_RELOAD_MULT * (weapon.getCooldown() / (1f + bonusThisFrame))) {
            weapon.setRemainingCooldownTo(BASE_BONUS_RELOAD_MULT * weapon.getCooldown() / (1f + bonusThisFrame));
        }
    }
}