package data.scripts.weapons.prototypeSpecials;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.input.InputEventAPI;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.HashSet;
import java.util.List;

/**
 * Acts as a form of "breeder gun", splitting projectiles on hit: used for the Random Prototype script exclusively
 * @author Nicke535
 */
public class VassRandomPrototypeBreederScript implements EveryFrameWeaponEffectPlugin {
    private static final float BREED_INACCURACY = 30f;
    private static final int MAXIMUM_BREED_STAGES = 4;

    private String projWeapon;
    private String breedSound;
    private float breedFactor;
    public VassRandomPrototypeBreederScript(String projWeapon, String breedSound, float breedFactor) {
        this.projWeapon = projWeapon;
        this.breedSound = breedSound;
        this.breedFactor = breedFactor;
    }

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        //Keeps our projectile list somewhere we can access it stateless: also access it
        EntityList alreadyAffectedEntities = null;
        Object response = engine.getCustomData().get("VassRandomPrototypeBreederProjList");
        if (response instanceof EntityList) {
            alreadyAffectedEntities = (EntityList)response;
        } else {
            alreadyAffectedEntities = new EntityList();
            engine.getCustomData().put("VassRandomPrototypeBreederProjList", alreadyAffectedEntities);
        }

        //Find all our projectiles that aren't our "fake" projectiles
        for (CombatEntityAPI entity : CombatUtils.getEntitiesWithinRange(weapon.getLocation(), 400f)) {
            if (!(entity instanceof DamagingProjectileAPI)) {
                continue;
            }
            DamagingProjectileAPI proj = (DamagingProjectileAPI)entity;

            //Only run once per projectile
            if (alreadyAffectedEntities.contains(proj)) {
                continue;
            }

            //Ignore our fake projectiles
            if (("vass_fake_prototype_shot").equals(proj.getProjectileSpecId())) {
                continue;
            }

            //If the projectile is our own, we can do something with it
            if (proj.getWeapon() == weapon) {
                //Register that we've triggered on the projectile
                alreadyAffectedEntities.add(proj);

                //And apply our breeder plugin
                engine.addPlugin(new BreederBehaviour(proj, alreadyAffectedEntities, 0));
            }
        }

        //Also, we clean up our already triggered projectiles when they stop being loaded into the engine
        EntityList toRemove = new EntityList();
        for (CombatEntityAPI entity : alreadyAffectedEntities) {
            if (!engine.isEntityInPlay(entity)) {
                toRemove.add(entity);
            }
        }
        alreadyAffectedEntities.removeAll(toRemove);
        toRemove.clear();
    }

    //For type-safety reasons and nothing more
    private class EntityList extends HashSet<CombatEntityAPI> {}

    //Local class for managing breeder-type behaviour
    private class BreederBehaviour extends BaseEveryFrameCombatPlugin {
        private DamagingProjectileAPI proj;
        private EntityList affectedEntities;
        private int breedStage;
        private boolean hasBred = false;
        private BreederBehaviour (DamagingProjectileAPI proj, EntityList affectedEntities, int breedStage) {
            this.proj = proj;
            this.affectedEntities = affectedEntities;
            this.breedStage = breedStage;
        }

        @Override
        public void advance(float amount, List<InputEventAPI> events) {
            //To help prevent multi-breeding on one target due to one-frame issues
            if (hasBred) {
                return;
            }


            //If the projectile hit a target, check breeding
            if (proj.didDamage() && proj.getDamageTarget() != null) {
                CombatEntityAPI target = proj.getDamageTarget();

                //Already bred targets can't breed, so just cleanup
                if (affectedEntities.contains(target)) {
                    Global.getCombatEngine().removePlugin(this);
                    return;
                }

                //Destroyed outright: breed!
                if (!Global.getCombatEngine().isEntityInPlay(target)) {
                    //Register that this target cannot be bred further
                    affectedEntities.add(target);
                    hasBred = true;
                    Global.getSoundPlayer().playSound(breedSound, 1f, 1f, proj.getLocation(), new Vector2f(0f, 0f));
                    float shotsLeft = breedFactor;
                    while (Math.random() < shotsLeft) {
                        shotsLeft--;
                        DamagingProjectileAPI newProj = (DamagingProjectileAPI) Global.getCombatEngine().spawnProjectile(proj.getSource(), proj.getWeapon(),
                                projWeapon, proj.getLocation(),
                                proj.getFacing() + MathUtils.getRandomNumberInRange(-BREED_INACCURACY, BREED_INACCURACY),
                                new Vector2f(0f, 0f));
                        newProj.setFromMissile(true);
                        newProj.setDamageAmount(proj.getDamageAmount());
                        if (newProj.getAI() instanceof ProximityFuseAIAPI) {
                            ((ProximityFuseAIAPI) newProj.getAI()).updateDamage();
                        }
                        //Register that we've triggered on the projectile
                        affectedEntities.add(proj);

                        //And apply our breeder plugin, if we haven't gone past our limit
                        if (breedStage < MAXIMUM_BREED_STAGES) {
                            Global.getCombatEngine().addPlugin(new BreederBehaviour(proj, affectedEntities, breedStage+1));
                        }
                    }

                    //Also, cleanup this script
                    Global.getCombatEngine().removePlugin(this);
                    return;
                }

                //Target at low enough health to overkill: breed and hope for the best!
                if (target.getHitpoints() < proj.getDamageAmount()) {
                    //Register that this target cannot be bred further
                    affectedEntities.add(target);
                    hasBred = true;
                    Global.getSoundPlayer().playSound(breedSound, 1f, 1f, proj.getLocation(), new Vector2f(0f, 0f));
                    float shotsLeft = breedFactor;
                    while (Math.random() < shotsLeft) {
                        shotsLeft--;
                        DamagingProjectileAPI newProj = (DamagingProjectileAPI) Global.getCombatEngine().spawnProjectile(proj.getSource(), proj.getWeapon(),
                                projWeapon, proj.getLocation(),
                                proj.getFacing() + MathUtils.getRandomNumberInRange(-BREED_INACCURACY, BREED_INACCURACY),
                                new Vector2f(0f, 0f));
                        newProj.setFromMissile(true);
                        newProj.setDamageAmount(proj.getDamageAmount());
                        if (newProj.getAI() instanceof ProximityFuseAIAPI) {
                            ((ProximityFuseAIAPI) newProj.getAI()).updateDamage();
                        }
                        //Register that we've triggered on the projectile
                        affectedEntities.add(proj);

                        //And apply our breeder plugin, if we haven't gone past our limit
                        if (breedStage < MAXIMUM_BREED_STAGES) {
                            Global.getCombatEngine().addPlugin(new BreederBehaviour(proj, affectedEntities, breedStage+1));
                        }
                    }

                    //Also, cleanup this script
                    Global.getCombatEngine().removePlugin(this);
                    return;
                }
            }

            //Projectile can no longer hit anything: clean up our script
            if (!Global.getCombatEngine().isEntityInPlay(proj)) {
                Global.getCombatEngine().removePlugin(this);
            }
        }
    }
}