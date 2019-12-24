package data.scripts.weapons;

import com.fs.starfarer.api.combat.*;
import org.lazywizard.lazylib.combat.CombatUtils;

import java.util.ArrayList;
import java.util.List;

public abstract class VassBasePerturbaMissileScript implements EveryFrameWeaponEffectPlugin {

    //Keeps track of already-affected projectiles
    private List<DamagingProjectileAPI> alreadyTriggeredProjectiles = new ArrayList<>();

    //Used for clearing out projectiles we no longer need to care about
    private List<DamagingProjectileAPI> toRemove = new ArrayList<>();

    //For handling perturba's unique regenerating ammo
    private float regenCounter = 0f;

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        //Regenerate ammo if we have the Perturba family bonus
        Object hasPerturbaBonus = engine.getCustomData().get("VassPerturbaPeriodicPlatingBonus" + weapon.getShip().getId());
        if (hasPerturbaBonus instanceof Boolean && (Boolean)hasPerturbaBonus) {
            if (weapon.getAmmo() < weapon.getMaxAmmo() - weapon.getSpec().getTurretFireOffsets().size()) {
                //Regen a full burst in 5 times the time the weapon cooldown
                regenCounter += amount * weapon.getSpec().getTurretFireOffsets().size() * weapon.getSpec().getBurstSize() * ammoRegenMult() / (weapon.getCooldown()*5f);

                //Each time we've gotten a full regen "chunk", add it to the weapon
                if (regenCounter > weapon.getSpec().getTurretFireOffsets().size()) {
                    regenCounter -= weapon.getSpec().getTurretFireOffsets().size();
                    weapon.setAmmo(weapon.getAmmo() + weapon.getSpec().getTurretFireOffsets().size());
                }
            }
        }

        for (CombatEntityAPI entity : CombatUtils.getEntitiesWithinRange(weapon.getLocation(), 400f)) {
            if (!(entity instanceof DamagingProjectileAPI)) {
                continue;
            }
            DamagingProjectileAPI proj = (DamagingProjectileAPI)entity;

            //Only run once per projectile
            if (alreadyTriggeredProjectiles.contains(proj)) {
                continue;
            }

            //If the projectile is our own, we can do something with it
            if (proj.getWeapon() == weapon) {
                //Register that we've triggered on the projectile
                alreadyTriggeredProjectiles.add(proj);

                //And apply our effect
                applyEffectOnProjectile(engine, proj);
            }
        }

        //Also, we clean up our already triggered projectiles when they stop being loaded into the engine
        for (DamagingProjectileAPI proj : alreadyTriggeredProjectiles) {
            if (!engine.isEntityInPlay(proj)) {
                toRemove.add(proj);
            }
        }
        alreadyTriggeredProjectiles.removeAll(toRemove);
        toRemove.clear();
    }

    /**
     * Overriden in each implementation: this script is run once per projectile on the weapon
     * @param engine the current combat engine
     * @param proj current projectile having effects applied
     */
    abstract protected void applyEffectOnProjectile(CombatEngineAPI engine, DamagingProjectileAPI proj);


    /**
     * How fast ammo regenerates on this weapon, compared to other Perturba missile weapons
     * Only applies if Perturba's family bonus is active
     */
    protected float ammoRegenMult() {
        return 1f;
    }
}