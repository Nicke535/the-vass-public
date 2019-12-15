//By Nicke535, edited from the vanilla shipsystem AI by Alex Mosolov
//Activates the system fairly aggressively, unless there is a better target within 1.5x of activation range. Is less conservative with the first charge of a shipsystem
package data.scripts.shipsystemAI;

import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.util.IntervalUtil;
import data.scripts.shipsystems.VassChronoJump;
import org.lazywizard.lazylib.CollisionUtils;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class VassChronoJumpAI implements ShipSystemAIScript {
    //How much "damage score" do we need to start using the system?
    private static final float SCORE_THRESHOLD = 1600f;

    //How much *additional* score is needed when we're above, say, 65% flux?
    private static final float SCORE_MODIFIER_HIGHFLUX = 3600f;

    //How much less score do we need on low (<40%) hull?
    private static final float SCORE_MODIFIER_LOWHULL = -1500f;

    //How fast must a missile be able to turn for us to consider it useless to dodge it?
    private static final float MISSILE_UNDODGEABLE_TURN_RATE = 45f;

    //How much more is actual hull damage worth compared to hardflux gain? Both for low hull and normal hull
    private static final float SCORE_MULT_HULLDAMAGE = 4f;
    private static final float SCORE_MULT_HULLDAMAGE_LOWHULL = 6f;

    //How much is engine damage score multiplied by, compared to normal hull hits?
    private static final float SCORE_MULT_ENGINEDAMAGE = 20f;

    //How much of a "margin" to treat projectiles as having when calculating threat. The projectile counts as this much further
    // back than it actually is for danger calculations, to ensure it doesn't blast long projectiles into its own behind each time
    private static final float SAFETY_MARGIN = 65f;

    //How far ahead do we check for projectile collisions? Anything higher than 2-3 seconds is pretty pointless
    private static final float CHECK_AHEAD_TIME = 0.6f;

    //How often does the script check for an arming opportunity?
    private IntervalUtil armTracker = new IntervalUtil(0.04f, 0.07f);

    //How often does the script check when armed (and waiting to trigger)?
    private IntervalUtil tracker = new IntervalUtil(0.007f, 0.013f);

    //Used in-script to simplify later calls and some other tracking
    private ShipAPI ship;
    private CombatEngineAPI engine;
    private ShipwideAIFlags flags;
    private ShipSystemAPI system;
    private float lastFrameScore;
    private float twoFramesBackScore;

    //Initialize some variables for later use
    public void init(ShipAPI ship, ShipSystemAPI system, ShipwideAIFlags flags, CombatEngineAPI engine) {
        this.ship = ship;
        this.flags = flags;
        this.engine = engine;
        this.system = system;
        this.lastFrameScore = 0f;
        this.twoFramesBackScore = 0f;
    }

    //Main advance loop
    @SuppressWarnings("unchecked")
    public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
        //Choose which advance() version to run depending on current state
        //Armed, ready to trigger
        if (system.getEffectLevel() > 0f && !system.getState().equals(ShipSystemAPI.SystemState.OUT)) {
            advanceArmed(amount, missileDangerDir, collisionDangerDir, target);
        }

        //Ready to arm
        else if (system.getState().equals(ShipSystemAPI.SystemState.IDLE) && !system.isOutOfAmmo() && system.getFluxPerUse() < (ship.getFluxTracker().getMaxFlux() - ship.getFluxTracker().getCurrFlux())) {
            advanceUnarmed(amount, missileDangerDir, collisionDangerDir, target);
        }
    }

    //advance() code if we're armed
    private void advanceArmed(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
        //If we are armed, we check incoming "avoided" damage for the last three intervals.
        tracker.advance(amount);
        if (tracker.intervalElapsed()) {
            float currentFrameScore = getDamageScore();

            // If our current interval is the worst of our last three intervals, trigger: we're most likely going to get even worse results the next interval check
            if (currentFrameScore <= lastFrameScore && currentFrameScore < twoFramesBackScore) {
                ship.useSystem();
                twoFramesBackScore = -5000f;
                lastFrameScore = -5000f;
                currentFrameScore = -5000f;
            }

            //Lastly, "shift" the scores
            twoFramesBackScore = lastFrameScore;
            lastFrameScore = currentFrameScore;
        }
    }

    //advance() code if we're not armed
    private void advanceUnarmed(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
        //If we are unarmed, we check for our damage score being high enough to trigger each armTracker interval
        armTracker.advance(amount);
        if (armTracker.intervalElapsed()) {
            float actualThreshold = SCORE_THRESHOLD;
            if (ship.getFluxLevel() > 0.65f) { actualThreshold += SCORE_MODIFIER_HIGHFLUX; }
            if (ship.getHullLevel() < 0.4f) { actualThreshold += SCORE_MODIFIER_LOWHULL; }
            if (getDamageScore() > actualThreshold) {
                ship.useSystem();
            }
        }
    }


    //Utility function for getting the relative "damage score" at a given moment: high return values mean we avoid large amounts of damage
    //by triggering, negative values mean we're increasing damage taken by using the system
    private float getDamageScore() {
        float score = 0f;

        List<DamagingProjectileAPI> allProjs = CombatUtils.getProjectilesWithinRange(ship.getLocation(),ship.getCollisionRadius()+VassChronoJump.MAX_RANGE);
        allProjs.addAll(CombatUtils.getMissilesWithinRange(ship.getLocation(),ship.getCollisionRadius()+ VassChronoJump.MAX_RANGE));
        for (DamagingProjectileAPI proj : allProjs) {
            //Ignore all shots we've shot ourselves, all friendly no-FF shots and all no-collision shots
            if (proj.getSource() == ship ||
                    ((proj.getCollisionClass().equals(CollisionClass.PROJECTILE_NO_FF) || proj.getCollisionClass().equals(CollisionClass.MISSILE_NO_FF)) && proj.getOwner() == ship.getOwner()) ||
                    proj.getCollisionClass().equals(CollisionClass.NONE)) {
                continue;
            }

            //Stores the location the projectile will end up in after jumping
            Vector2f newLoc = new Vector2f(proj.getLocation().x + proj.getVelocity().x * VassChronoJump.TIME_SKIP_AMOUNT,
                    proj.getLocation().y + proj.getVelocity().y * VassChronoJump.TIME_SKIP_AMOUNT);

            //First, check if it's a guided missile with decent turn rate. If it is, calculate a bit differently: its damage is assumed to always hit
            //since it's good at homing, but we still want to slightly discourage teleporting the projectile inside ourselves
            if (proj instanceof MissileAPI && ((MissileAPI) proj).getMaxTurnRate() > MISSILE_UNDODGEABLE_TURN_RATE) {
                if (CollisionUtils.isPointWithinBounds(newLoc,ship)) {
                    score -= proj.getDamageAmount() * 0.5f * (proj.getDamageType() == DamageType.HIGH_EXPLOSIVE ? 2f : 1f);
                }
            }

            //If it's not a missile that's homing, we compare its travel vector before and after teleporting: if it's in a better spot
            // after teleporting, we add positive score, if it's in a WORSE spot, we add negative score
            else {
                Vector2f dangerVectorOld = new Vector2f(proj.getLocation().x + proj.getVelocity().x*CHECK_AHEAD_TIME,
                        proj.getLocation().y + proj.getVelocity().y*CHECK_AHEAD_TIME);
                Vector2f dangerVectorNew = new Vector2f(newLoc.x + proj.getVelocity().x*CHECK_AHEAD_TIME,
                        newLoc.y + proj.getVelocity().y*CHECK_AHEAD_TIME);
                float hullScoreMult = ship.getHullLevel() < 0.4f ? SCORE_MULT_HULLDAMAGE_LOWHULL : SCORE_MULT_HULLDAMAGE;

                //Compares where something hits in both situations, and calculates the score to add from that
                //OLD CALCULATIONS
                // - (approximate) SHIELD HIT
                if (ship.getShield() != null && ship.getShield().getType() != ShieldAPI.ShieldType.NONE &&
                        ship.getShield().getType() != ShieldAPI.ShieldType.PHASE && ship.getShield().isOn() &&
                        ship.getShield().isWithinArc(proj.getLocation()) &&
                        CollisionUtils.getCollides(proj.getLocation(), dangerVectorOld, ship.getShield().getLocation(), ship.getShield().getRadius())) {
                    score += proj.getDamageAmount() * proj.getDamageType().getShieldMult() * ship.getShield().getFluxPerPointOfDamage();
                }
                // - HULL HIT
                else {
                    Vector2f hitLocation = CollisionUtils.getCollisionPoint(proj.getLocation(), dangerVectorOld, ship);
                    //If the hit location is null, we missed: do nothing
                    if (hitLocation != null) {
                        //Engine damage is extra EXTRA risky, so treat as higher damage than normal. Also consider EMP in this case
                        boolean hitsEngines = false;
                        for (ShipEngineControllerAPI.ShipEngineAPI engine : ship.getEngineController().getShipEngines()) {
                            if (engine.isDisabled() || engine.isPermanentlyDisabled()) {
                                continue;
                            }
                            if (MathUtils.getDistance(hitLocation, engine.getLocation()) < SAFETY_MARGIN) {
                                hitsEngines = true;
                                break;
                            }
                        }
                        if (hitsEngines) {
                            score += (proj.getDamageAmount()/2f + proj.getEmpAmount()) * SCORE_MULT_ENGINEDAMAGE;
                        }

                        //For high-armor hit locations, use armor damage type mult squared /2, since damage is non-linear against armor
                        int[] gridHit = ship.getArmorGrid().getCellAtLocation(hitLocation);
                        if (ship.getArmorGrid().getArmorFraction(gridHit[0], gridHit[1]) > 0.7f) {
                            score += proj.getDamageAmount() * proj.getDamageType().getArmorMult() * proj.getDamageType().getArmorMult() * hullScoreMult / 2;
                        }
                        //For medium-armor hit location, just use armor damage type mult
                        else if (ship.getArmorGrid().getArmorFraction(gridHit[0], gridHit[1]) > 0.3f) {
                            score += proj.getDamageAmount() * proj.getDamageType().getArmorMult() * hullScoreMult;
                        }
                        //For low-armor hit locations, use hull damage type mult
                        else {
                            score += proj.getDamageAmount() * proj.getDamageType().getHullMult() * hullScoreMult;
                        }
                    }
                }

                //NEW CALCULATIONS
                // - (approximate) SHIELD HIT
                if (ship.getShield() != null && ship.getShield().getType() != ShieldAPI.ShieldType.NONE &&
                        ship.getShield().getType() != ShieldAPI.ShieldType.PHASE && ship.getShield().isOn() &&
                        ship.getShield().isWithinArc(newLoc) &&
                        CollisionUtils.getCollides(newLoc, dangerVectorNew, ship.getShield().getLocation(), ship.getShield().getRadius())) {
                    score -= proj.getDamageAmount() * proj.getDamageType().getShieldMult() * ship.getShield().getFluxPerPointOfDamage();
                }
                // - HULL HIT
                else {
                    //We shift the new location slightly to get a hit location: this is to get our safety margin in and prevent excessive risk-taking
                    Vector2f hitLocation = CollisionUtils.getCollisionPoint(MathUtils.getPoint(newLoc, SAFETY_MARGIN, -VectorUtils.getAngle(newLoc, ship.getLocation())),
                            dangerVectorNew, ship);

                    //If the hit location is null, we missed: do nothing
                    if (hitLocation != null) {
                        //Engine damage is extra EXTRA risky, so treat as higher damage than normal. Also consider EMP in this case
                        boolean hitsEngines = false;
                        for (ShipEngineControllerAPI.ShipEngineAPI engine : ship.getEngineController().getShipEngines()) {
                            if (engine.isDisabled() || engine.isPermanentlyDisabled()) {
                                continue;
                            }
                            if (MathUtils.getDistance(hitLocation, engine.getLocation()) < SAFETY_MARGIN || MathUtils.getDistance(newLoc, engine.getLocation()) < SAFETY_MARGIN) {
                                hitsEngines = true;
                                break;
                            }
                        }
                        if (hitsEngines) {
                            score -= (proj.getDamageAmount()/2f + proj.getEmpAmount()) * SCORE_MULT_ENGINEDAMAGE;
                        }

                        //For high-armor hit locations, use armor damage type mult squared, since damage is non-linear against armor
                        int[] gridHit = ship.getArmorGrid().getCellAtLocation(hitLocation);
                        if (ship.getArmorGrid().getArmorFraction(gridHit[0], gridHit[1]) > 0.7f) {
                            score -= proj.getDamageAmount() * proj.getDamageType().getArmorMult() * proj.getDamageType().getArmorMult() * hullScoreMult / 2;
                        }
                        //For medium-armor hit location, just use armor damage type mult
                        else if (ship.getArmorGrid().getArmorFraction(gridHit[0], gridHit[1]) > 0.3f) {
                            score -= proj.getDamageAmount() * proj.getDamageType().getArmorMult() * hullScoreMult;
                        }
                        //For low-armor hit locations, use hull damage type mult
                        else {
                            score -= proj.getDamageAmount() * proj.getDamageType().getHullMult() * hullScoreMult;
                        }
                    }
                }
            }
        }

        return score;
    }
}

