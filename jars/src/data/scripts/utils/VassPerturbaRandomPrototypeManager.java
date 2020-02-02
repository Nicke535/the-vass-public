package data.scripts.utils;

import com.fs.starfarer.api.combat.DamagingProjectileAPI;
import com.fs.starfarer.api.combat.EveryFrameWeaponEffectPlugin;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import data.scripts.weapons.VassDyrnwynScript;
import data.scripts.weapons.prototypeSpecials.VassRandomPrototypeBreederScript;
import data.scripts.weapons.prototypeSpecials.VassRandomPrototypeChronoFlakScript;
import data.scripts.weapons.prototypeSpecials.VassRandomPrototypeHyperDyrnwynScript;
import data.scripts.weapons.prototypeSpecials.VassRandomPrototypeSuperchargedScript;
import org.jetbrains.annotations.Nullable;
import org.lazywizard.lazylib.MathUtils;

public class VassPerturbaRandomPrototypeManager {
    //All the weapon IDs used for the prototype weapons. Put in a picker, so they can be weighted and automatically picked from
    public static final WeightedRandomPicker<String> PROTOTYPE_WEAPON_IDS = new WeightedRandomPicker<>();
    static {
        PROTOTYPE_WEAPON_IDS.add("vass_prototype_s1", 10f);
        PROTOTYPE_WEAPON_IDS.add("vass_prototype_s2", 10f);
        PROTOTYPE_WEAPON_IDS.add("vass_prototype_m1", 7f);
        PROTOTYPE_WEAPON_IDS.add("vass_prototype_m2", 7f);
    }

    //Stats for prototype weapon randomization
    private static final float RANDOM_LASER_ARCHETYPE_WEIGHT = 8f;
    private static final float SUPERCHARGED_ARCHETYPE_WEIGHT = 6f;
    private static final float DYRNWYN_ARCHETYPE_WEIGHT = 5f;
    private static final float RANDOM_CANNON_ARCHETYPE_WEIGHT = 7f;
    private static final float CHRONOFLAK_ARCHETYPE_WEIGHT = 4f;
    private static final float BREEDER_ARCHETYPE_WEIGHT = 3f;

    //Generates random prototype weapon data for the quest
    public static PrototypeWeaponData generateRandomPrototypeStats(boolean energy, boolean medium) {
        PrototypeWeaponData data = null;

        WeightedRandomPicker<String> archetypePicker = new WeightedRandomPicker<>();
        if (energy) {
            archetypePicker.add("RANDOM_LASER", RANDOM_LASER_ARCHETYPE_WEIGHT);
            archetypePicker.add("DYRNWYN", DYRNWYN_ARCHETYPE_WEIGHT);
            if (medium) {
                archetypePicker.add("SUPERCHARGED", SUPERCHARGED_ARCHETYPE_WEIGHT);
            }
        } else {
            archetypePicker.add("RANDOM_CANNON", RANDOM_CANNON_ARCHETYPE_WEIGHT);
        }
        if (medium) {
            archetypePicker.add("BREEDER", BREEDER_ARCHETYPE_WEIGHT);
        }
        archetypePicker.add("CHRONOFLAK", CHRONOFLAK_ARCHETYPE_WEIGHT);

        String archetype = archetypePicker.pick();
        if (archetype.equals("RANDOM_LASER")) {
            data = new PrototypeWeaponData(
                    MathUtils.getRandomNumberInRange(0.7f, 2f), //Damage
                    MathUtils.getRandomNumberInRange(0.1f, 1f), //Firerate
                    MathUtils.getRandomNumberInRange(0f, 12f), //Inaccuracy
                    MathUtils.getRandomNumberInRange(0f, 0.15f), //Speed variation
                    medium ? "pulselaser" : "irpulse", //Projectile weapon ID
                    Math.random() < 0.2f, //PD
                    null, //Guidance
                    null, //Script
                    medium ? "pulse_laser_fire" : "ir_pulse_laser_fire", //Fire sound
                    Math.random() < 0.2f ? MathUtils.getRandomNumberInRange(3f, 6f) : 1f); //Shotgun Factor
        } else if (archetype.equals("RANDOM_CANNON")) {
            data = new PrototypeWeaponData(
                    MathUtils.getRandomNumberInRange(0.7f, 2f), //Damage
                    MathUtils.getRandomNumberInRange(0.2f, 1f), //Firerate
                    MathUtils.getRandomNumberInRange(0f, 10f), //Inaccuracy
                    MathUtils.getRandomNumberInRange(0f, 0.15f), //Speed variation
                    medium ? "heavyac" : "lightac", //Projectile weapon ID
                    Math.random() < 0.2f, //PD
                    null, //Guidance
                    null, //Script
                    medium ? "autocannon_fire" : "light_autocannon_fire", //Fire sound
                    Math.random() < 0.2f ? MathUtils.getRandomNumberInRange(4, 7) : 1f); //Shotgun Factor : note the integer shotgunning for the cannon
        } else if (archetype.equals("DYRNWYN")) {
            data = new PrototypeWeaponData(
                    medium ? MathUtils.getRandomNumberInRange(0.45f, 0.75f) : MathUtils.getRandomNumberInRange(2f, 4f), //Damage
                    1f, //Firerate
                    medium ? MathUtils.getRandomNumberInRange(3f, 11f) : MathUtils.getRandomNumberInRange(2f, 5f), //Inaccuracy
                    medium ? 0f : MathUtils.getRandomNumberInRange(0.08f, 0.15f), //Speed variation
                    "vass_dyrnwyn", //Projectile weapon ID
                    false, //PD
                    null, //Guidance
                    medium ? new VassRandomPrototypeHyperDyrnwynScript() : new VassDyrnwynScript(), //Script
                    "vass_dyrnwyn_fire", //Fire sound
                    medium ? 1f : MathUtils.getRandomNumberInRange(2f, 5f)); //Shotgun Factor
        } else if (archetype.equals("CHRONOFLAK")) {
            data = new PrototypeWeaponData(
                    medium ? MathUtils.getRandomNumberInRange(0.8f, 1.5f) : MathUtils.getRandomNumberInRange(0.3f, 0.6f), //Damage
                    medium ? MathUtils.getRandomNumberInRange(0.3f, 0.8f) : MathUtils.getRandomNumberInRange(0.6f, 1f), //Firerate
                    MathUtils.getRandomNumberInRange(3f, 8f), //Inaccuracy
                    MathUtils.getRandomNumberInRange(0.07f, 0.14f), //Speed variation
                    "flak", //Projectile weapon ID
                    true, //PD
                    Math.random() < 0.2f ? new VassRandomPrototypeChronoFlakScript.Guidance() : null, //Guidance
                    new VassRandomPrototypeChronoFlakScript(), //Script
                    "flak_fire", //Fire sound
                    Math.random() < 0.35f ? MathUtils.getRandomNumberInRange(3, 5) : 1f); //Shotgun Factor
        } else if (archetype.equals("BREEDER")) {
            String weaponName = energy ? "heavyblaster" : "hellbore";
            String sound = energy ? "heavy_blaster_fire" : "hellbore_fire";
            data = new PrototypeWeaponData(
                    MathUtils.getRandomNumberInRange(1f, 2.3f), //Damage
                    MathUtils.getRandomNumberInRange(0.85f, 1f), //Firerate
                    MathUtils.getRandomNumberInRange(1f, 3f), //Inaccuracy
                    MathUtils.getRandomNumberInRange(0f, 0.1f), //Speed variation
                    weaponName, //Projectile weapon ID
                    Math.random() < 0.4f, //PD
                    null, //Guidance
                    new VassRandomPrototypeBreederScript(weaponName, sound, MathUtils.getRandomNumberInRange(2f, 5f)), //Script
                    sound, //Fire sound
                    1); //Shotgun Factor
        } else if (archetype.equals("SUPERCHARGED")) {
            data = new PrototypeWeaponData(
                    MathUtils.getRandomNumberInRange(1.2f, 2f), //Damage
                    999f, //Firerate
                    MathUtils.getRandomNumberInRange(1f, 2f), //Inaccuracy
                    MathUtils.getRandomNumberInRange(0f, 0.05f), //Speed variation
                    "plasma", //Projectile weapon ID
                    false, //PD
                    null, //Guidance
                    new VassRandomPrototypeSuperchargedScript(MathUtils.getRandomNumberInRange(0.3f, 0.75f),
                                                              MathUtils.getRandomNumberInRange(0.1f, 0.35f),
                                                              MathUtils.getRandomNumberInRange(0.5f, 0.99f),
                                               Math.random() < 0.25f), //Script
                    "plasma_cannon_fire", //Fire sound
                    1); //Shotgun Factor
        }

        return data;
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
        public final float shotgunFactor;

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
                                   @Nullable EveryFrameWeaponEffectPlugin weaponEffectPlugin, String sound,
                                   float shotgunFactor) {
            this.damageMult = damageMult;
            this.reloadMult = reloadMult;
            this.inaccuracy = inaccuracy;
            this.speedVariation = speedVariation;
            this.projectileWeaponId = projectileWeaponId;
            this.pd = pd;
            this.guidance = guidance;
            this.weaponEffectPlugin = weaponEffectPlugin;
            this.sound = sound;
            this.shotgunFactor = shotgunFactor;
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
