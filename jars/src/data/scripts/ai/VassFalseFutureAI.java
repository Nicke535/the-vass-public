//Man, is this not a good way to handle it. An overhaul is in order, but hey.
package data.scripts.ai;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAIScript;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import com.fs.starfarer.api.plugins.ShipSystemStatsScript;
import com.sun.glass.ui.EventLoop;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.util.IntervalUtil;

public class VassFalseFutureAI implements ShipSystemAIScript {

    private ShipSystemAPI system;
    private ShipAPI ship;

    private float hullAtActivation = 1f;

    //Checks every second or so while the system is off
    private final IntervalUtil slowTracker = new IntervalUtil(0.95f, 1.05f);
    //Checks quite often when active, as projectiles can be fast, and the AI has to correctly notice a dangerous situation, quickly
    private final IntervalUtil fastTracker = new IntervalUtil(0.03f, 0.05f);


    @Override
    public void init(ShipAPI ship, ShipSystemAPI system, ShipwideAIFlags flags, CombatEngineAPI engine) {
        this.ship = ship;
        this.system = system;
    }

    @Override
    public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
        fastTracker.advance(amount);
        slowTracker.advance(amount);

        //Once the interval has elapsed...
        if (fastTracker.intervalElapsed() && system.isActive()) {
            //Avoids any particularly deadly missiles
            if (missileDangerDir != null) {
                for (MissileAPI missileToTest : CombatUtils.getMissilesWithinRange(ship.getLocation(), ship.getCollisionRadius())) {
                    //The threshhold for "particularly dangerous" is 1500, equivalent to a hammer torpedo, or greater than the ship's remaining hitPoints
                    if (missileToTest.getDamageAmount() > Math.min(1500f, ship.getMaxHitpoints() * ship.getHullLevel())) {
                        deactivateSystem();
                    }
                }
            }

            //If we start taking serious damage, activate (meaning, if you take more than 20% damage from when the system activated)
            if (hullAtActivation - 0.2f > ship.getHullLevel()) {
                deactivateSystem();
            }

            //Finally, activate at critical flux levels
            if (ship.getFluxTracker().getFluxLevel() > 0.9f) {
                deactivateSystem();
            }
        } else if (slowTracker.intervalElapsed()) {
            //Only use in "combat"
            if (CombatUtils.getShipsWithinRange(ship.getLocation(), 2000f).size() > 0) {
                //Some more data, to not waste it all the time: only use in a "safe" situation (below 10% flux) or when in acute danger (below 30% hitPoints level)
                if (ship.getFluxTracker().getFluxLevel() < 0.1f || ship.getHullLevel() < 0.3f) {
                    activateSystem();
                    hullAtActivation = ship.getHullLevel();
                }
            }
        }
    }

    private void deactivateSystem() {
        if (system.isOn()) {
            ship.useSystem();
        }
    }

    private void activateSystem() {
        if (!system.isOn()) {
            ship.useSystem();
        }
    }
}
