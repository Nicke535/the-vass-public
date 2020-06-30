package data.scripts.shipsystems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.util.Misc;
import data.scripts.VassModPlugin;
import data.scripts.util.MagicRender;
import data.scripts.utils.VassUtils;
import org.dark.shaders.distortion.DistortionShader;
import org.dark.shaders.distortion.RippleDistortion;
import org.lwjgl.util.vector.Vector2f;

import java.util.*;

/**
 * Handles the Temporal Retreat shipsystem
 * @author Nicke535
 */
public class VassTemporalRetreat extends BaseShipSystemScript {

    //The maximum "divisor" for our time mult (IE 8 here represents a time mult of 1/8)
    private static final float MAX_TIME_MULT_DIVISOR = 30f;

    //How far back does the system take us? Counted in number of 0.1-second intervals
    private static final int MAX_STEPS_BACKWARDS = 50;

    //Internal variables
    private Queue<TimePointData> timePointQueue = new LinkedList<>();
    private float timer = 0f;
    boolean runOnce = false;

    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        ShipAPI ship = null;
        if (stats.getEntity() instanceof ShipAPI) {
            ship = (ShipAPI) stats.getEntity();
            id = id + "_" + ship.getId();
        } else {
            return;
        }

        //If we are a wreck, we don't run any shipsystem stuff
        if (ship.isHulk() || ship.isPiece()) {
            return;
        }

        //We don't really care about anything as long as the game is paused
        if (Global.getCombatEngine().isPaused()) {
            return;
        }

        //If the system is not active, run passive effects
        if (effectLevel <= 0f) {
            //Register that our effect is over, so we can run the teleport again
            runOnce = true;

            //Each 0.1 seconds (of TRUE time, not ship time), enqueue a new time point data, and dequeue the oldest one if we have too many
            timer += Global.getCombatEngine().getElapsedInLastFrame();
            if (timer > 0.1f) {
                timer = 0f;
                timePointQueue.add(new TimePointData(ship));

                if (timePointQueue.size() > MAX_STEPS_BACKWARDS) {
                    //When de-queueing a time point, we spawn a short clone of our ship at that location, if we're the player ship
                    TimePointData oldestTimePoint = timePointQueue.poll();
                    if (Global.getCombatEngine().getPlayerShip().equals(ship)) {
                        SpriteAPI spriteToUse = Global.getSettings().getSprite("graphics/vass/ships/makhaira.png");
                        //"Chasing" render
                        MagicRender.battlespace(spriteToUse, oldestTimePoint.position, oldestTimePoint.velocity,
                                new Vector2f(ship.getSpriteAPI().getWidth(), ship.getSpriteAPI().getHeight()), Misc.ZERO,
                                oldestTimePoint.angle-90f, oldestTimePoint.angularVelocity, VassUtils.getFamilyColor(VassUtils.VASS_FAMILY.RECIPRO, 0.01f),
                                true,0.04f, 0.06f, 0.1f);
                        //"Trailing" render
                        MagicRender.battlespace(spriteToUse, oldestTimePoint.position, Misc.ZERO,
                                new Vector2f(ship.getSpriteAPI().getWidth(), ship.getSpriteAPI().getHeight()),Misc.ZERO,
                                oldestTimePoint.angle-90f, 0f, VassUtils.getFamilyColor(VassUtils.VASS_FAMILY.RECIPRO, 0.04f),
                                true,0f, 0.05f, 0.45f);
                    }
                }
            }

            //Always tell the AI the location we're going to end up in next by peeking at the queue
            Global.getCombatEngine().getCustomData().put(ship.getId()+"VASS_TEMPORAL_RETREAT_KEY", timePointQueue.peek());
        }

        //If the system IS active, we start applying the active effects of the system
        else if (effectLevel > 0f) {
            //If this is the first frame of having the system active, we move back in time and clear out our time point queue
            if (!timePointQueue.isEmpty() && runOnce) {
                runOnce = false;

                //Updates the ship to match up to the time point we're jumping to
                TimePointData dataToJumpTo = timePointQueue.peek();
                ship.setHitpoints(dataToJumpTo.hitPoints);
                ship.getLocation().x = dataToJumpTo.position.x;
                ship.getLocation().y = dataToJumpTo.position.y;
                ship.getVelocity().x = dataToJumpTo.velocity.x;
                ship.getVelocity().y = dataToJumpTo.velocity.y;
                ship.setFacing(dataToJumpTo.angle);
                ship.setAngularVelocity(dataToJumpTo.angularVelocity);
                ship.getFluxTracker().setHardFlux(dataToJumpTo.hardFlux);
                ship.getFluxTracker().setCurrFlux(dataToJumpTo.softFlux+dataToJumpTo.hardFlux);

                //Armor has to be done iteratively
                for (int ix = 0; ix < (ship.getArmorGrid().getLeftOf() + ship.getArmorGrid().getRightOf()); ix++) {
                    for (int iy = 0; iy < (ship.getArmorGrid().getAbove() + ship.getArmorGrid().getBelow()); iy++) {
                        ship.getArmorGrid().setArmorValue(ix, iy, dataToJumpTo.armor[ix][iy]);
                    }
                }

                //Spawns afterimages along our "reversal path". Do this once for each element in the queue
                //The lifetime of the clones go down the further away from our "destination" we are
                float lifeTimeReducerTracker = (float)MAX_STEPS_BACKWARDS + 5f;
                while (!timePointQueue.isEmpty()) {
                    TimePointData afterimageData = timePointQueue.poll();
                    SpriteAPI spriteToUse = Global.getSettings().getSprite("graphics/vass/ships/makhaira.png");
                    float lifetimeThisImage = 0.5f * (lifeTimeReducerTracker/(float)MAX_STEPS_BACKWARDS);
                    MagicRender.battlespace(spriteToUse, afterimageData.position, Misc.ZERO,
                            new Vector2f(ship.getSpriteAPI().getWidth(), ship.getSpriteAPI().getHeight()),Misc.ZERO,
                            afterimageData.angle-90f, 0f, VassUtils.getFamilyColor(VassUtils.VASS_FAMILY.RECIPRO, 0.2f),
                            true,0f, 0f, lifetimeThisImage);
                    lifeTimeReducerTracker--;
                }

                //Finally, completely clear the time point queue if we somehow failed to do it earlier
                timePointQueue.clear();
            }

            //Modify the engine speed if we're the player ship, to give a bit of a "cooler" feeling, and to help with disorientation
            if (Global.getCombatEngine().getPlayerShip() == ship) {
                //We use inverted and cubic growth to improve feeling
                float timeEffectLevel = (float)Math.pow(1f - effectLevel, 3f);
                float timeDivisor = 1f + (timeEffectLevel * (MAX_TIME_MULT_DIVISOR - 1f));
                Global.getCombatEngine().getTimeMult().modifyMult(id, 1f / timeDivisor);
            }
        }

        //Always unapply the global time-mult if we are not the player ship, or our current state is not IN or ACTIVE
        if (ship != Global.getCombatEngine().getPlayerShip() || (!state.equals(State.IN) && !state.equals(State.ACTIVE))) {
            Global.getCombatEngine().getTimeMult().unmodify(id);
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
        if (index == 0 && effectLevel > 0f) {
            return new StatusData("The present is melting away...", false);
        }
        return null;
    }


    //Stores the state of a ship at a certain point in time; stores hullpoints, armor, flux, position, angle, velocity and angular velocity
    public static class TimePointData {
        public final float hitPoints;
        public final float[][] armor;
        public final float softFlux;
        public final float hardFlux;
        public final Vector2f position;
        public final float angle;
        public final Vector2f velocity;
        public final float angularVelocity;

        private TimePointData (final ShipAPI ship) {
            hitPoints = ship.getHitpoints();
            armor = deepCopy(ship.getArmorGrid().getGrid());
            softFlux = (ship.getFluxTracker().getCurrFlux() - ship.getFluxTracker().getHardFlux());
            hardFlux = ship.getFluxTracker().getHardFlux();
            position = new Vector2f(ship.getLocation());
            angle = ship.getFacing();
            velocity = new Vector2f(ship.getVelocity());
            angularVelocity = ship.getAngularVelocity();
        }

        //By Rorick from StackOverflow; deep-copies a 2D array
        public static float[][] deepCopy(float[][] original) {
            if (original == null) {
                return null;
            }

            final float[][] result = new float[original.length][];
            for (int i = 0; i < original.length; i++) {
                result[i] = Arrays.copyOf(original[i], original[i].length);
            }
            return result;
        }
    }
}