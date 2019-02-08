//You can check if a ship is a fighter with ship.isFighter(). If you want to do something to a whole wing, you'd need to use ship.getWingMembers() and apply it to each of them
//Credit goes to Psiyon for his firecontrol AI script.
package data.scripts.ai;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.combat.CombatEngineAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAIScript;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.combat.ShipwideAIFlags;
import org.lazywizard.lazylib.MathUtils;
import org.lwjgl.util.vector.Vector2f;
import com.fs.starfarer.api.util.IntervalUtil;

public class VassChronoDisturberAI implements ShipSystemAIScript {

    private ShipSystemAPI system;
    private ShipAPI ship;

    //Sets an interval for once every 1-1.5 seconds. (meaning the code will only run once this interval has elapsed, not every frame)
    private final IntervalUtil tracker = new IntervalUtil(0.2f, 0.4f);

    @Override
    public void init(ShipAPI ship, ShipSystemAPI system, ShipwideAIFlags flags, CombatEngineAPI engine) {
        this.ship = ship;
        this.system = system;
    }

    @Override
    public void advance(float amount, Vector2f missileDangerDir, Vector2f collisionDangerDir, ShipAPI target) {
        tracker.advance(amount);

        //Once the interval has elapsed...
        if (tracker.intervalElapsed()) {
            //Activ_range is the range at which the AOE benefits are applied. Should match the radius from the other script.
            float active_range = 900f;

            int ships_enemy = 0;

            //Sets up a temporary ship object.

            //Iterates through all ships on the map.
            for (ShipAPI shp : Global.getCombatEngine().getShips()) {
                //We don't care about this ship if it's disabled
                if (shp.isHulk()) {
                    continue;
                }

                //If the distance to the ship is less than or equal to the active_range...
                if (MathUtils.getDistance(shp, ship) <= (active_range)) {
                    //If the owner of ship_tmp is not on same side, AND it is not a frigate, turn the system on
                    if (shp.getOwner() != ship.getOwner() && shp.getHullSize() != ShipAPI.HullSize.FIGHTER && shp.getHullSize() != ShipAPI.HullSize.FRIGATE) {
                        ships_enemy += 2;
                        break;
                    }
                    //If there are two enemy frigates in range, we still activate the system, as long as both are hostile
                    else if (shp.getOwner() != ship.getOwner() && shp.getHullSize() == ShipAPI.HullSize.FRIGATE) {
                        ships_enemy++;
                        if (ships_enemy >= 2) {
                            break;
                        }
                    }
                }
            }

            //If there's a enemy ship around AND we don't have too much hard flux, we activate the system
            if (ships_enemy >= 2 && !system.isActive() && (ship.getFluxTracker().getFluxLevel() < 0.3f || ship.getFluxTracker().getHardFlux() / (ship.getFluxTracker().getFluxLevel() * ship.getFluxTracker().getMaxFlux()) < 0.3f)) {
                activateSystem();
            } else if (ships_enemy <= 1 && system.isActive() || (ship.getFluxTracker().getFluxLevel() >= 0.8f && ship.getFluxTracker().getHardFlux() / (ship.getFluxTracker().getFluxLevel() * ship.getFluxTracker().getMaxFlux()) >= 0.8f) && system.isActive()) {
                deactivateSystem();
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
