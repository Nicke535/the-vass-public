package data.scripts.shipsystems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import data.scripts.util.MagicRender;
import data.scripts.utils.VassUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.*;

public class VassTemporalRetreat extends BaseShipSystemScript {

    //How far back does the system take us? Counted in number of 0.1-second intervals
    private static final int MAX_STEPS_BACKWARDS = 30;

    //Internal variables
    private boolean hasTriggered = false;
    private Queue<TimePointData> timePointQueue = new PriorityQueue<>();
    private float timer = 0f;

    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        ShipAPI ship = null;
        if (stats.getEntity() instanceof ShipAPI) {
            ship = (ShipAPI) stats.getEntity();
            id = id + "_" + ship.getId();
        } else {
            return;
        }

        //We don't really care about anything as long as the game is paused
        if (Global.getCombatEngine().isPaused()) {
            return;
        }

        //If the system is not active, run passive effects
        if (effectLevel <= 0f) {
            //Each 0.1 seconds, enqueue a new time point data, and dequeue the oldest one if we have too many
            timer += Global.getCombatEngine().getElapsedInLastFrame() * ship.getMutableStats().getTimeMult().getModifiedValue();
            if (timer > 0.1f) {
                timer = 0f;
                timePointQueue.add(new TimePointData(ship));

                if (timePointQueue.size() > MAX_STEPS_BACKWARDS) {
                    //When de-queueing a time point, we spawn a short clone of our ship at that location
                    TimePointData oldestTimePoint = timePointQueue.poll();
                    MagicRender.battlespace(ship.getSpriteAPI(), oldestTimePoint.position, oldestTimePoint.velocity,
                            new Vector2f(ship.getSpriteAPI().getWidth(), ship.getSpriteAPI().getHeight()),new Vector2f(0f, 0f),
                            oldestTimePoint.angle, oldestTimePoint.angularVelocity, VassUtils.getFamilyColor(VassUtils.VASS_FAMILY.RECIPRO, 0.5f),
                            true,0.04f, 0.06f, 0.1f);
                }
            }

            //Always tell the AI the location we're going to end up in next by peeking at the queue
            Global.getCombatEngine().getCustomData().put(ship.getId()+"VASS_TEMPORAL_RETREAT_KEY", timePointQueue.peek());
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
        if (index == 0) {
            return new StatusData("The present is melting away...", false);
        }
        return null;
    }


    //Stores the state of a ship at a certain point in time; stores hullpoints, armor, flux, position, angle, velocity and angular velocity
    public static class TimePointData {
        public final float hull;
        public final float[][] armor;
        public final float softFlux;
        public final float hardFlux;
        public final Vector2f position;
        public final float angle;
        public final Vector2f velocity;
        public final float angularVelocity;

        private TimePointData (final ShipAPI ship) {
            hull = ship.getHullLevel();
            armor = ship.getArmorGrid().getGrid();
            softFlux = ship.getFluxTracker().getCurrFlux() - ship.getFluxTracker().getHardFlux();
            hardFlux = ship.getFluxTracker().getHardFlux();
            position = new Vector2f(ship.getLocation());
            angle = ship.getFacing();
            velocity = new Vector2f(ship.getVelocity());
            angularVelocity = ship.getAngularVelocity();
        }
    }
}