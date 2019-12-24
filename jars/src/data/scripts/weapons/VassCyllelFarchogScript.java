//By Nicke535, speeds up the firerate of a weapon depending on a ship's time mult
package data.scripts.weapons;

import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.util.Misc;
import data.scripts.utils.VassCyllelFarchogGuidanceScript;
import org.lazywizard.lazylib.combat.CombatUtils;

import java.util.ArrayList;
import java.util.List;

public class VassCyllelFarchogScript implements EveryFrameWeaponEffectPlugin {

    //Maximum firerate bonus achievable with the weapon
    static final float MAX_BONUS_FIRERATE = 0.65f;

    //"Absolute" time mult at which maximum firerate is achieved (0.5f = 2f "absolute")
    static final float MAX_TIME_MULT_FOR_BONUS = 1.8f;

    private List<DamagingProjectileAPI> alreadyRegisteredProjectiles = new ArrayList<>();

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
        if (weapon.getCooldownRemaining() > (weapon.getCooldown() / (1f + bonusThisFrame))) {
            weapon.setRemainingCooldownTo(weapon.getCooldown() / (1f + bonusThisFrame));
        }

        //...Also, we use fancy-pancy guidance!
        for (DamagingProjectileAPI proj : CombatUtils.getProjectilesWithinRange(weapon.getLocation(), 200f)) {
            if (proj.getWeapon() == weapon && !alreadyRegisteredProjectiles.contains(proj) && engine.isEntityInPlay(proj) && !proj.didDamage()) {
                engine.addPlugin(new VassCyllelFarchogGuidanceScript(proj, null));
                alreadyRegisteredProjectiles.add(proj);
            }
        }

        //And clean up our registered projectile list
        List<DamagingProjectileAPI> cloneList = new ArrayList<>(alreadyRegisteredProjectiles);
        for (DamagingProjectileAPI proj : cloneList) {
            if (!engine.isEntityInPlay(proj) || proj.didDamage()) {
                alreadyRegisteredProjectiles.remove(proj);
            }
        }
    }
}