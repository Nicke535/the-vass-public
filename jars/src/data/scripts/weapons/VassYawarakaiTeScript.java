package data.scripts.weapons;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.listeners.DamageDealtModifier;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.util.Misc;
import org.magiclib.plugins.MagicTrailPlugin;
import data.scripts.utils.VassUtils;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Handles all the strange mechanics involved in the workings of the over-scripted Yawarakai-Te
 * @author Nicke535
 */
public class VassYawarakaiTeScript implements EveryFrameWeaponEffectPlugin {
    //Base size of the visual trail that indicates us attacking a target
    private static final float TRAIL_START_WIDTH = 14f;
    private static final float TRAIL_END_WIDTH = 9f;

    //Ranges that the weapon start dealing less damage, and where it (theoretically) would deal 0 damage
    private static final float EFFECTIVE_RANGE = 250f;
    private static final float MIN_DAMAGE_RANGE = 500f;

    //Minimum damage dealt from missiles being too far away
    private static final float MINIMUM_DAMAGE_MULT = 0.33f;

    //Chance to fully disable a missile instead of just flaming it out
    private static final float FULL_DISABLE_CHANCE = 0.75f;

    //Interval for periodic pulses
    private static final float PULSE_TIME = 0.23f;

    //Our hashmap to keep track of damage dealt to each missile
    private MissileStatusHashMap missileStatusMap = null;

    //Counter to run periodically
    private float counter = 0f;

    //Unique ID counter for all script instances, and the ID for this instance
    private static int uidCounter = 0;
    private static Integer ourUID = null;

    @Override
    public void advance(float amount, CombatEngineAPI engine, WeaponAPI weapon) {
        //Get an ID
        if (ourUID == null) {
            ourUID = uidCounter;
            uidCounter++;
        }

        //To enable automatic cleanup, yet to keep the semblance of a statically saved hashmap, we store it in the
        //engine's customData and load it in for each script
        if (missileStatusMap == null) {
            if (engine.getCustomData().get("VassYawarakaiTeEffectID") instanceof MissileStatusHashMap) {
                //Unchecked conversion, but we know what we're doing in this rare instance
                missileStatusMap = (MissileStatusHashMap) engine.getCustomData().get("VassYawarakaiTeEffectID");
            } else {
                missileStatusMap = new MissileStatusHashMap();
                engine.getCustomData().put("VassYawarakaiTeEffectID", missileStatusMap);
            }
        }

        //We don't run at all if we have no damage (honestly, this is mostly just to counteract the issues with the Periodic Breaker)
        if (weapon.getDamage().computeDamageDealt(1f) <= 0f) { return; }

        //Only run while firing, and run in pulses
        if (weapon.getChargeLevel() >= 1f) {
            counter+=amount;
            if (counter > PULSE_TIME) {
                counter -= PULSE_TIME;

                float effectiveRange = EFFECTIVE_RANGE;
                float minDamageRange = MIN_DAMAGE_RANGE;

                //Check for some stats we might get from hullmods and such
                float damageMultToMissiles = weapon.getShip().getMutableStats().getDamageToMissiles().getModifiedValue();
                boolean ignoresFlares = false;
                if (weapon.getShip().getMutableStats().getDynamic().getMod(Stats.PD_IGNORES_FLARES) != null &&
                        weapon.getShip().getMutableStats().getDynamic().getMod(Stats.PD_IGNORES_FLARES).flatBonus >= 1f) {
                    ignoresFlares = true;
                }
                Object hasPerturbaBonus = Global.getCombatEngine().getCustomData().get("VassPerturbaPeriodicPlatingBonus" + weapon.getShip().getId());
                if (hasPerturbaBonus instanceof Boolean && (Boolean)hasPerturbaBonus) {
                    damageMultToMissiles *= 1.2f;
                    effectiveRange += 150f;
                    minDamageRange += 150f;
                }
                List<DamageDealtModifier> listeners = new ArrayList<>();
                if (weapon.getShip() != null) {
                    if (weapon.getShip().getListeners(DamageDealtModifier.class) != null) {
                        listeners.addAll(weapon.getShip().getListeners(DamageDealtModifier.class));
                    }
                }

                //Then, we do the real part of the script: find nearby missiles so we can do stuff
                float missileCount = 0f;
                List<MissileAPI> missilesInArc = new ArrayList<>();
                float mostFarAwayMissile = 0f;
                for (MissileAPI msl : CombatUtils.getMissilesWithinRange(weapon.getLocation(), weapon.getRange())) {
                    //Ignore friendlies
                    if (msl.getOwner() == weapon.getShip().getOwner()) {
                        continue;
                    }
                    if (ignoresFlares && msl.isFlare()) {
                        continue;
                    }

                    //Register the missile as something to affect if it's within our arc
                    if (weapon.distanceFromArc(msl.getLocation()) <= 0f) {

                        //Also, ensure that our status map has the missile in it to simplify later logic
                        if (missileStatusMap.get(msl) == null) {
                            missileStatusMap.put(msl, 0f);
                        } else {
                            //Also also, ignore targets that are already flamed out by us
                            if (missileStatusMap.get(msl) > msl.getHitpoints()) {
                                continue;
                            }
                        }

                        missileCount++;
                        missilesInArc.add(msl);

                        //Used for tracking if we should play a more silent sound or not
                        mostFarAwayMissile = Math.max(MathUtils.getDistance(weapon.getLocation(), msl.getLocation()), mostFarAwayMissile);
                    }
                }

                //If there were no missiles in arc, we refund our flux costs while firing and also don't show VFX/SFX
                if (missileCount <= 0f) {
                    weapon.getShip().getMutableStats().getFluxDissipation().modifyFlat("VassYawarakaiTeFluxRefund"+ourUID, weapon.getDerivedStats().getFluxPerSecond());
                    return;
                } else {
                    //Sound and visual effects if we have any missiles in range
                    weapon.getShip().getMutableStats().getFluxDissipation().unmodify("VassYawarakaiTeFluxRefund"+ourUID);
                    float rangeMultiplier = Math.min(1f, Math.max(MINIMUM_DAMAGE_MULT, 1f - ((mostFarAwayMissile-effectiveRange) / (minDamageRange-effectiveRange))));
                    Global.getSoundPlayer().playSound("vass_yawaratai_te_pulse", 1f, rangeMultiplier, weapon.getLocation(), new Vector2f(Misc.ZERO));
                    engine.spawnExplosion(weapon.getLocation(), weapon.getShip().getVelocity(),
                            VassUtils.getFamilyColor(VassUtils.VASS_FAMILY.PERTURBA, rangeMultiplier), 6f*rangeMultiplier, PULSE_TIME);
                }

                //Then, we deal "fake damage" to the missiles; degradation damage, which triggers a flameout when built up
                for (MissileAPI msl : missilesInArc) {
                    //Penalty for far-away targets (beyond 250 SU, maxes at about 550 SU): not massive, but a bit of a reduction to combat long-range-hullmod spammability
                    float distanceToMissile = MathUtils.getDistance(msl, weapon.getLocation());
                    float extraPenaltyForRange = Math.min(1f, Math.max(MINIMUM_DAMAGE_MULT, 1f - ((distanceToMissile-effectiveRange) / (minDamageRange-effectiveRange))));

                    //Use all the damage-dealt listeners we have, in case we need to to modify our damage
                    DamageAPI dmg = weapon.getDamage().clone();
                    for (DamageDealtModifier lstnr : listeners) {
                        lstnr.modifyDamageDealt(null, msl, dmg, msl.getLocation(), false);
                    }
                    float addedDamage = dmg.computeDamageDealt(PULSE_TIME) * extraPenaltyForRange * damageMultToMissiles / missileCount;

                    float newDamage = missileStatusMap.get(msl)+addedDamage;
                    if (msl.getHitpoints() <= newDamage) {
                        //The final flame-out-pulse has distinctly more power, to be more noticeable. Also play a sound and spawn minor SFX
                        spawnVFX(msl, weapon, 2f * extraPenaltyForRange/missileCount, false);
                        Global.getSoundPlayer().playSound("vass_yawaratai_te_disable", 1f, Math.min(1f, msl.getHitpoints()/200f), new Vector2f(msl.getLocation()), new Vector2f(msl.getVelocity()));
                        engine.spawnExplosion(msl.getLocation(), msl.getVelocity(),
                                VassUtils.getFamilyColor(VassUtils.VASS_FAMILY.PERTURBA, 0.7f), 4f, PULSE_TIME);

                        //Disable the missile, and make it completely inert if we've disabled it properly
                        msl.flameOut();
                        if (Math.random() < FULL_DISABLE_CHANCE) { msl.setArmingTime(9999f); }
                    } else {
                        spawnVFX(msl, weapon, 1f * extraPenaltyForRange/missileCount, true);
                    }

                    missileStatusMap.put(msl, newDamage);
                }
            }
        } else {
            weapon.getShip().getMutableStats().getFluxDissipation().unmodify("VassYawarakaiTeFluxRefund"+ourUID);
        }
    }

    /**
     * Spawns VFX for a single effected missile
     * @param target missile to spawn VFX for
     * @param weapon source weapon of the VFX
     * @param effectivePower from 0f to 1f, how much relative power this weapon is directing at this missile
     * @param fadeFaster if true, the effect fades faster if the player fires it and has a lot of time mult
     */
    private void spawnVFX(MissileAPI target, WeaponAPI weapon, float effectivePower, boolean fadeFaster) {
        float distanceToTarget = MathUtils.getDistance(weapon.getLocation(), target.getLocation());
        List<Vector2f> pointsForArc =
                VassUtils.getFancyArcPoints(
                        weapon.getLocation(),
                        target.getLocation(),
                        MathUtils.getRandomNumberInRange(-1f, 1f) * distanceToTarget * 0.05f,
                        (int)Math.floor(Math.max(15f, distanceToTarget/40f)));

        float idForTrail = MagicTrailPlugin.getUniqueID();
        SpriteAPI spriteToUse = Global.getSettings().getSprite("vass_fx","projectile_trail_zappy");

        float effectDuration = PULSE_TIME;
        if (fadeFaster && weapon.getShip().equals(Global.getCombatEngine().getPlayerShip())) {
            effectDuration /= weapon.getShip().getMutableStats().getTimeMult().modified;
        }

        //Start actually rendering the trail : note that we render one point shorter than the actual trail, to always have a valid direction to next point
        for (int i = 0; i < pointsForArc.size()-1; i++) {
            float opacity = effectivePower;
            float extraWidthMult = 1f;

            //Past 25% of range, the trails start fading out in width and opacity
            if (i > pointsForArc.size()/4f) {
                opacity *= 1f - ((i-(pointsForArc.size()/4f)) / (pointsForArc.size()*3f/4f));
                extraWidthMult = 1f - ((i-(pointsForArc.size()/4f)) / (pointsForArc.size()*3f/4f));
            }
            opacity = (float)Math.sqrt(opacity) * 0.7f + 0.3f;
            extraWidthMult = (float)Math.sqrt(extraWidthMult) * 0.7f + 0.3f;

            //Gets the current point, next point, and direction between them
            Vector2f currPoint = pointsForArc.get(i);
            Vector2f nextPoint = pointsForArc.get(i+1);
            float angleToNextPoint = VectorUtils.getAngle(currPoint, nextPoint);

            //Calculates an offset velocity based on distance from target and source
            Vector2f actualOffsetVelocity = Misc.interpolateVector(new Vector2f(weapon.getShip().getVelocity()), new Vector2f(target.getVelocity()), (float)i/pointsForArc.size());

            MagicTrailPlugin.addTrailMemberAdvanced(
                    target,
                    idForTrail,
                    spriteToUse,
                    currPoint,
                    0f, 0f,
                    angleToNextPoint,
                    0f, 0f,
                    TRAIL_START_WIDTH*effectivePower*extraWidthMult, TRAIL_END_WIDTH*effectivePower*extraWidthMult,
                    VassUtils.getFamilyColor(VassUtils.VASS_FAMILY.PERTURBA, 1f), VassUtils.getFamilyColor(VassUtils.VASS_FAMILY.PERTURBA, 1f),
                    opacity, 0f, 0f, effectDuration,
                    true, -1f, 0f, 0,
                    actualOffsetVelocity,
                    null, CombatEngineLayers.BELOW_INDICATORS_LAYER,
                    1f);
        }
    }

    /**
     * A type that's really only here to solve typecasting safety issues
     */
    private class MissileStatusHashMap extends HashMap<MissileAPI, Float> {}
}