package data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.util.Misc;
import data.scripts.campaign.barEvents.VassPerturbaWeaponTestingIntel;
import data.scripts.utils.VassCyllelFarchogGuidanceScript;
import org.jetbrains.annotations.Nullable;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Gives random bonuses to a weapon, depending on current quest setup
 * @author Nicke535
 */
public class VassRandomPrototypeScript implements EveryFrameWeaponEffectPlugin {
    private List<DamagingProjectileAPI> alreadyRegisteredProjectiles = new ArrayList<>();

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        //Failsafe : refit screen and more!
        if (engine == null || !engine.isEntityInPlay(weapon.getShip())) {
            return;
        }

        //Instantly disable in simulation or missions
        if ((engine.isSimulation() || !engine.isInCampaign()) && !weapon.isDisabled()) {
            weapon.disable(true);
            engine.addFloatingText(weapon.getLocation(), "No simulation data available!", 20f, Color.RED,
                    weapon.getShip(), 0.5f, 5f);
            return;
        }

        //Gets the randomized weapon data currently in use
        Object dataObject = Global.getSector().getMemoryWithoutUpdate().get(VassPerturbaWeaponTestingIntel.MEM_KEY_PROTOTYPE_DATA);
        if (!(dataObject instanceof PrototypeWeaponData)) {
            if (!weapon.isDisabled()) {
                weapon.disable(true);
                engine.addFloatingText(weapon.getLocation(), "Critical malfunction!", 20f, Color.RED,
                        weapon.getShip(), 0.5f, 5f);
                engine.applyDamage(weapon.getShip(), weapon.getLocation(), 200f, DamageType.HIGH_EXPLOSIVE,
                        0f, true, false, weapon.getShip(), true);
            }
            return;
        }
        PrototypeWeaponData currentData = (PrototypeWeaponData)dataObject;

        //If our current remaining cooldown is above our "actual" cooldown, bring it down to the true cooldown
        if (weapon.getCooldownRemaining() > (weapon.getCooldown()*currentData.reloadMult)) {
            weapon.setRemainingCooldownTo((weapon.getCooldown()*currentData.reloadMult));
        }

        for (DamagingProjectileAPI proj : CombatUtils.getProjectilesWithinRange(weapon.getLocation(), 200f)) {
            if (proj.getProjectileSpecId().equals("vass_fake_prototype_shot") ||
                    proj.getWeapon() == weapon && !alreadyRegisteredProjectiles.contains(proj)
                    && engine.isEntityInPlay(proj) && !proj.didDamage()) {
                //Projectile replacement! Only for our "initial" fake projectile, though
                DamagingProjectileAPI newProj = (DamagingProjectileAPI) engine.spawnProjectile(weapon.getShip(), weapon,
                        currentData.projectileWeaponId, proj.getLocation(),
                        proj.getFacing()+ MathUtils.getRandomNumberInRange(-currentData.inaccuracy, currentData.inaccuracy),
                        null);
                newProj.setDamageAmount(newProj.getDamageAmount()*currentData.damageMult);
                float speedMultThisShot = 1f + MathUtils.getRandomNumberInRange(-1f, 1f) * currentData.speedVariation;
                newProj.getVelocity().x = newProj.getVelocity().x * speedMultThisShot + weapon.getShip().getVelocity().x;
                newProj.getVelocity().y = newProj.getVelocity().y * speedMultThisShot + weapon.getShip().getVelocity().y;
                Global.getSoundPlayer().playSound(currentData.sound, 1f, 1f, proj.getLocation(), weapon.getShip().getVelocity());
                engine.removeEntity(proj);

                //We use guidance, if supplied
                if (currentData.guidance != null) {
                    currentData.guidance.applyToProjectile(newProj);
                }
                alreadyRegisteredProjectiles.add(newProj);
            }
        }

        List<DamagingProjectileAPI> cloneList = new ArrayList<>(alreadyRegisteredProjectiles);
        for (DamagingProjectileAPI proj : cloneList) {
            if (!engine.isEntityInPlay(proj) || proj.didDamage()) {
                alreadyRegisteredProjectiles.remove(proj);
            }
        }

        //Also, always run our effect script
        if (currentData.weaponEffectPlugin != null) {
            currentData.weaponEffectPlugin.advance(amount, engine, weapon);
        }
    }

    //Public class for storing prototype weapon data
    public static class PrototypeWeaponData {
        public final float damageMult;
        public final float reloadMult;
        public final float inaccuracy;
        public final float speedVariation;
        public final String projectileWeaponId;
        public final boolean pd;
        public final GuidanceApplier guidance;
        public final EveryFrameWeaponEffectPlugin weaponEffectPlugin;
        public final String sound;

        /**
         * Creates a new prototype weapon data with the given parameters
         * @param damageMult damage multiplier of the shot
         * @param reloadMult expected to be <= 1f: lower numbers mean faster firerate
         * @param inaccuracy in degrees to each side: the projectile will ween off this much to either side when firing
         * @param speedVariation how much an individual projectile may diverge from average projectile speed
         * @param projectileWeaponId ID of the weapon to take projectiles from
         * @param pd true if the weapon is to be treated as a PD weapon, false otherwise
         * @param guidance guidance script to apply to each individual projectile: could of course be part of
         *                 weaponEffectScript, but is here so it can be more easily randomized and parametrized
         * @param weaponEffectPlugin arbitrary script to apply to the weapon as its everyFrameWeaponEffect.
         *                           NOTE: expected to be "stateless" or track each weapon individually in a map
         *                           as this is a shared script instance between all the prototypes
         */
        public PrototypeWeaponData(float damageMult, float reloadMult, float inaccuracy, float speedVariation,
                                   String projectileWeaponId, boolean pd, @Nullable GuidanceApplier guidance,
                                   @Nullable EveryFrameWeaponEffectPlugin weaponEffectPlugin, String sound) {
            this.damageMult = damageMult;
            this.reloadMult = reloadMult;
            this.inaccuracy = inaccuracy;
            this.speedVariation = speedVariation;
            this.projectileWeaponId = projectileWeaponId;
            this.pd = pd;
            this.guidance = guidance;
            this.weaponEffectPlugin = weaponEffectPlugin;
            this.sound = sound;
        }
    }

    /**
     * Public class defining a simple guidance applying script for a randomized weapon: expected to
     * apply a new MagicGuidedProjectileScript to the projectiles it recieves with somewhat consistant behaviour
     */
    public static abstract class GuidanceApplier {
        public abstract void applyToProjectile(DamagingProjectileAPI proj);
    }
}