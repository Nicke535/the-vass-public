package data.scripts.shipsystems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.util.IntervalUtil;
import com.fs.starfarer.api.util.Misc;
import data.scripts.util.MagicRender;
import data.scripts.utils.VassUtils;
import org.lazywizard.lazylib.CollisionUtils;
import org.lazywizard.lazylib.FastTrig;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.*;
import java.util.List;

/**
 * A shipsystem that swaps between making enemy projectiles miss us and making our projectiles multiply randomly
 * @author Nicke535
 */
public class VassIsochronalField extends BaseShipSystemScript {

    //Memory key to store the ship's "offensive/defensive" mode, to be read by other scripts
    public static final String OFFENSE_MEM_KEY = "vass_isochronal_field_mode_memory_key";

    //Chance for our projectiles to multiply when in offensive mode
    private static final float MULTIPLY_CHANCE = 0.4f;

    //Timestep to check "forward in time" to determine collisions and redirections
    private static final float TIMESTEP_LENGTH_COLLISION = 0.1f;
    private static final float TIMESTEP_LENGTH_REDIRECTION = 2f;

    //Maximum angle to redirect a projectile
    private static final float REDIRECTION_MAX_ANGLE = 20f;

    //Cooldown to apply to our passive system, depending on how much we redirected the projectile
    //This is the highest possible cooldown: scales linearly with smaller redirections
    private static final float REDIRECT_COOLDOWN_MAX = 0.8f;

    //Clone and redirect SFX
    private static final String REDIRECT_SOUND = "vass_isochronal_field_down";
    private static final String CLONE_SOUND = "vass_isochronal_field_up";

    //Internal variables
    private boolean offensiveMode = false;
    private IntervalUtil cloneIntervals = new IntervalUtil(0.2f, 0.45f);
    private boolean runOnce = false;
    private float defensiveCooldownLeft = 0f;
    private Set<DamagingProjectileAPI> alreadyManagedProjectiles = new HashSet<>();
    private int generalGracePeriod = 0; //Used for some scripted weapons
    private int asiGracePeriod = 0; //Used for the Asi

    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        ShipAPI ship = null;
        boolean player = false;
        if (stats.getEntity() instanceof ShipAPI) {
            ship = (ShipAPI) stats.getEntity();
            id = id + "_" + ship.getId();
            player = Global.getCombatEngine().getPlayerShip() == ship;
        } else {
            return;
        }

        //Always flag whether we are in offensive or defensive mode
        //This is also used to manage weapon glow
        if (offensiveMode) {
            ship.setWeaponGlow(1f, VassUtils.getFamilyColor(VassUtils.VASS_FAMILY.MULTA, 1f), EnumSet.allOf(WeaponAPI.WeaponType.class));
            Global.getCombatEngine().getCustomData().put(OFFENSE_MEM_KEY, true);
        } else {
            ship.setWeaponGlow(0f, Color.BLACK, EnumSet.allOf(WeaponAPI.WeaponType.class));
            Global.getCombatEngine().getCustomData().remove(OFFENSE_MEM_KEY);
        }

        //If we are a wreck, we don't run any shipsystem stuff, except to remove our visuals
        if (ship.isHulk() || ship.isPiece()) {
            ship.setWeaponGlow(0f, Color.BLACK, EnumSet.noneOf(WeaponAPI.WeaponType.class));
            return;
        }

        //We don't really care about anything as long as the game is paused
        if (Global.getCombatEngine().isPaused()) {
            return;
        }

        //If the system is not active, run passive effects for our current mode
        if (effectLevel <= 0f) {
            //Register that our "active" effect is over, so we can run one-use effects again
            runOnce = true;

            //Decides what mode-code to run and runs it
            if (offensiveMode) {
                runOffensiveMode(Global.getCombatEngine().getElapsedInLastFrame(), ship, player);
            } else {
                runDefensiveMode(Global.getCombatEngine().getElapsedInLastFrame(), ship, player);
            }
        }

        //If the system IS active, we start applying the active effects of the system
        else if (effectLevel >= 1f) {
            //If this is the first frame of having the system active, switch mode
            if (runOnce) {
                runOnce = false;
                offensiveMode = !offensiveMode;
            }
        }
    }

    //Runs passive effects for the "defensive" mode of the system
    private void runDefensiveMode (float amount, ShipAPI ship, boolean player) {
        defensiveCooldownLeft -= amount;

        //If we're on cooldown, don't do any effects
        if (defensiveCooldownLeft > 0f) {
            return;
        }

        //Visuals: render some fancy clones if we're off cooldown in defensive mode
        cloneIntervals.advance(amount);
        if (cloneIntervals.intervalElapsed()) {
            renderMultaImage(ship);
        }

        //Actual gameplay effect: we find projectiles that would feasonably hit us soon, and are close enough
        //Then, we do *funkiness* to them
        Set<DamagingProjectileAPI> projectiles = new HashSet<>(CombatUtils.getProjectilesWithinRange(ship.getLocation(), 300f));
        projectiles.addAll(CombatUtils.getMissilesWithinRange(ship.getLocation(), 300f));
        projectiles.removeAll(alreadyManagedProjectiles);
        List<DamagingProjectileAPI> riskyProjs = new LinkedList<>(); //This list is the one with actual "risky" projectiles we could maybe deflect
        for (DamagingProjectileAPI proj : projectiles) {
            if (proj.getOwner() == ship.getOwner()
                    || proj.getCollisionClass() == CollisionClass.NONE
                    || proj.didDamage()) {
                continue;
            }

            riskyProjs.add(proj);
        }

        //Now for the trickier part: check if the projectile will collide within our timestep, and we could theoretically
        // redirect it to not do so. Also pick the closest of those, since we'll be doing fancy stuff to it
        DamagingProjectileAPI closestValidProj = null;
        float closestDistance = Float.MAX_VALUE;
        float angleIfRedirected = 0f;
        for (DamagingProjectileAPI proj : riskyProjs) {
            if (MathUtils.getDistance(proj, ship.getLocation()) < closestDistance) {
                //Ignore projectiles that won't hit us
                if (!projectileWillCollide(proj, ship, 0f, TIMESTEP_LENGTH_COLLISION)) {
                    continue;
                }

                //Checks all possible deflections (in 1/5th of maximum deflection angle intervals) and finds the smallest good one
                float angleOffset = 0f;
                while (angleOffset < REDIRECTION_MAX_ANGLE) {
                    if (!projectileWillCollide(proj, ship, angleOffset, TIMESTEP_LENGTH_REDIRECTION)) {
                        closestValidProj = proj;
                        closestDistance = MathUtils.getDistance(proj, ship.getLocation());
                        angleIfRedirected = angleOffset;
                        break;
                    } else {
                        if (angleOffset < 0f) {
                            angleOffset *= -1f;
                        } else {
                            angleOffset *= -1f;
                            angleOffset -= REDIRECTION_MAX_ANGLE/5f;
                        }
                    }

                }
            }
        }

        //If we found a projectile to redirect, we redirect it and register that we can't redirect it again
        //We also go on cooldown, depending on how big the redirection was
        if (closestValidProj != null) {
            defensiveCooldownLeft = (Math.abs(angleIfRedirected)/REDIRECTION_MAX_ANGLE) * REDIRECT_COOLDOWN_MAX;
            float trueRedirectionAngle = angleIfRedirected + Math.signum(angleIfRedirected)*REDIRECTION_MAX_ANGLE/5f; //Minor lie, but it's for better consistency
            closestValidProj.setFacing(closestValidProj.getFacing()+trueRedirectionAngle);
            closestValidProj.getVelocity().set(VectorUtils.rotate(new Vector2f(closestValidProj.getVelocity()), trueRedirectionAngle));
            alreadyManagedProjectiles.add(closestValidProj);

            //TODO: Check for missiles, they don't seem to redirect properly

            //Also: each time a projectile is redirected, spawn three afterimages and play a sound
            for (int i = 0; i < 3; i++) {
                renderMultaImage(ship);
            }
            Global.getSoundPlayer().playSound(REDIRECT_SOUND, 1f-(0.1f*(Math.abs(angleIfRedirected)/REDIRECTION_MAX_ANGLE)),
                    0.5f+(0.5f*Math.abs(angleIfRedirected)/REDIRECTION_MAX_ANGLE), closestValidProj.getLocation(), new Vector2f(0f, 0f));
        }
    }

    //Runs passive effects for the "offensive" mode of the system
    private void runOffensiveMode (float amount, ShipAPI ship, boolean player) {
        //Determine if we're gonna clone projectiles this frame
        boolean cloneThisFrame = Math.random() < MULTIPLY_CHANCE;
        if (generalGracePeriod > 0) {
            cloneThisFrame = false;
        }
        boolean hasActuallyClonedThisFrame = false;

        //Get all projectiles close to the ship, that belongs to our ship (and haven't already been managed)
        Set<DamagingProjectileAPI> allProjs = new HashSet<>(CombatUtils.getProjectilesWithinRange(ship.getLocation(), 300f));
        allProjs.addAll(CombatUtils.getMissilesWithinRange(ship.getLocation(), 300f));
        for (DamagingProjectileAPI proj : allProjs) {
            //Checks so the projectile belongs to us, and isn't already unloaded from the engine
            if (proj.getSource() != ship || !Global.getCombatEngine().isEntityInPlay(proj)) {
                continue;
            }

            //Only manage projectiles from our hardpoint-mounted weapons
            if (proj.getWeapon() == null || !proj.getWeapon().getSlot().isHardpoint()) {
                continue;
            }

            //Don't manage already-tracked projectiles
            if (alreadyManagedProjectiles.contains(proj)) {
                continue;
            }

            //Now, check if the projectile should be duplicated, and if so, duplicate
            if (cloneThisFrame && isAllowedToClone(proj)) {
                DamagingProjectileAPI newProj = (DamagingProjectileAPI)Global.getCombatEngine().spawnProjectile(ship, proj.getWeapon(),
                        proj.getWeapon().getId(), MathUtils.getRandomPointInCircle(proj.getLocation(), 10f),
                        proj.getFacing()+MathUtils.getRandomNumberInRange(-3f, 3f), ship.getVelocity());
                runCustomEffectsPostClone(newProj, proj);
                hasActuallyClonedThisFrame = true;
                alreadyManagedProjectiles.add(newProj);
            }
            //Either way, this projectile can no longer be affected again
            alreadyManagedProjectiles.add(proj);
        }

        //Play a sound if we cloned something, and register for next frame
        if (hasActuallyClonedThisFrame) {
            Global.getSoundPlayer().playSound(CLONE_SOUND, 1f, 1f, ship.getLocation(), new Vector2f(0f, 0f));
        } else {
            generalGracePeriod--;
            asiGracePeriod--;
        }
    }



    //Unapply never gets called in this script
    public void unapply(MutableShipStatsAPI stats, String id) {
        ShipAPI ship = null;
        if (stats.getEntity() instanceof ShipAPI) {
            ship = (ShipAPI) stats.getEntity();
            id = id + "_" + ship.getId();
        } else {
            return;
        }
    }

    public StatusData getStatusData(int index, State state, float effectLevel) {
        if (state.equals(State.IDLE) || state.equals(State.COOLDOWN)) {
            if (index == 0 && offensiveMode) {
                return new StatusData("Offensive Mode", true);
            } else if (index == 0 && !offensiveMode) {
                return new StatusData("Defensive Mode", false);
            }
        }
        return null;
    }


    //Returns false if the projectile is disallowed from cloning, true otherwise
    private boolean isAllowedToClone(DamagingProjectileAPI proj) {
        //Asi nonsense: we don't want multiple clonings, or cloning of the fake projectile
        if (proj.getProjectileSpecId().equals("vass_asi_shot")) {
            return false;
        } else if (asiGracePeriod > 0 && proj.getProjectileSpecId().contains("vass_asi_shot")) {
            return false;
        }

        //Other projectiles: nothing needed yet, might need a blacklist later on
        return true;
    }

    //Custom effects for special projectiles that needs it, post-cloning
    private void runCustomEffectsPostClone(DamagingProjectileAPI newProj, DamagingProjectileAPI oldProj) {
        //If it's a guided missile, we need to give it the same target as the old one (its AI will adjust it further if needed, hopefully)
        if (oldProj instanceof MissileAPI && newProj instanceof MissileAPI) {
            if (((MissileAPI) oldProj).getMissileAI() instanceof GuidedMissileAI && ((MissileAPI) newProj).getMissileAI() instanceof GuidedMissileAI) {
                ((GuidedMissileAI) ((MissileAPI) newProj).getMissileAI()).setTarget(((GuidedMissileAI) ((MissileAPI) oldProj).getMissileAI()).getTarget());
            }
        }

        if (oldProj.getProjectileSpecId().equals("vass_asi_shot_slow")) {
            asiGracePeriod = 4;
        }

        //Other effects: nothing needed yet, might need a blacklist later on
    }

    //Renders an "afterimage" with random color
    private void renderMultaImage(ShipAPI ship) {
        // Sprite offset fuckery - Don't you love trigonometry?
        SpriteAPI sprite = ship.getSpriteAPI();
        float offsetX = sprite.getWidth()/2 - sprite.getCenterX();
        float offsetY = sprite.getHeight()/2 - sprite.getCenterY();

        float trueOffsetX = (float) FastTrig.cos(Math.toRadians(ship.getFacing()-90f))*offsetX - (float)FastTrig.sin(Math.toRadians(ship.getFacing()-90f))*offsetY;
        float trueOffsetY = (float)FastTrig.sin(Math.toRadians(ship.getFacing()-90f))*offsetX + (float)FastTrig.cos(Math.toRadians(ship.getFacing()-90f))*offsetY;

        Vector2f spotToSpawnOn = MathUtils.getRandomPointInCircle(new Vector2f(ship.getLocation().getX()+trueOffsetX,ship.getLocation().getY()+trueOffsetY), ship.getCollisionRadius()*0.9f);

        //Determines a layer to render on: fighters render above ships but below fighters, while everything else render below ships
        CombatEngineLayers layer = CombatEngineLayers.BELOW_SHIPS_LAYER;
        if (ship.getHullSize().equals(ShipAPI.HullSize.FIGHTER)) {
            layer = CombatEngineLayers.CONTRAILS_LAYER;
        }

        //Gets a color for the afterimage
        Color colorToUse = VassUtils.getFamilyColor(VassUtils.VASS_FAMILY.MULTA, (float)Math.random()*0.2f);

        MagicRender.battlespace(
                Global.getSettings().getSprite(ship.getHullSpec().getSpriteName()),
                spotToSpawnOn,
                new Vector2f(0, 0),
                new Vector2f(ship.getSpriteAPI().getWidth(), ship.getSpriteAPI().getHeight()),
                new Vector2f(0, 0),
                ship.getFacing()-90f+MathUtils.getRandomNumberInRange(-15f, 15f),
                0f,
                colorToUse,
                true,
                MathUtils.getRandomNumberInRange(0.01f, 0.15f),
                0f,
                MathUtils.getRandomNumberInRange(0.15f, 0.25f),
                layer);
    }

    //Utility function: checks if a projectile will collide with a ship within a certain time, with a certain angle offset
    private boolean projectileWillCollide(DamagingProjectileAPI proj, ShipAPI ship, float angleOffset, float time) {
        Vector2f relativeVel = new Vector2f(proj.getVelocity().x-ship.getVelocity().x, proj.getVelocity().y-ship.getVelocity().y);
        Vector2f predictPoint = MathUtils.getPoint(proj.getLocation(),
                relativeVel.length()*time,
                VectorUtils.getAngle(new Vector2f(0f, 0f), relativeVel)+angleOffset);

        //Shield handling
        if (ship.getShield() != null
                && (ship.getShield().getType() == ShieldAPI.ShieldType.FRONT || ship.getShield().getType() == ShieldAPI.ShieldType.OMNI)
                && ship.getShield().isOn()) {
            //Edges
            Vector2f corner1 = MathUtils.getPoint(ship.getShield().getLocation(),
                    ship.getShield().getRadius(),
                    ship.getShield().getFacing() - (ship.getShield().getActiveArc() / 2f));
            if (CollisionUtils.getCollisionPoint(ship.getShield().getLocation(), corner1, proj.getLocation(), predictPoint) != null) {
                return true;
            }
            Vector2f corner2 = MathUtils.getPoint(ship.getShield().getLocation(),
                    ship.getShield().getRadius(),
                    ship.getShield().getFacing() + (ship.getShield().getActiveArc() / 2f));
            if (CollisionUtils.getCollisionPoint(ship.getShield().getLocation(), corner2, proj.getLocation(), predictPoint) != null) {
                return true;
            }

            //Main surface: just check the closest point on the trajectory, if that doesn't collide we'd need to go through an edge (which we've already checked!)
            Vector2f closestPointOnTrajectory = Misc.closestPointOnLineToPoint(proj.getLocation(), predictPoint, ship.getShield().getLocation());
            if (ship.getShield().isWithinArc(closestPointOnTrajectory)) {
                return true;
            }
        }

        //Hull handling
        Vector2f hullHitLocation = CollisionUtils.getCollisionPoint(proj.getLocation(), predictPoint, ship);
        if (hullHitLocation != null) {
            return true;
        }

        return false;
    }
}