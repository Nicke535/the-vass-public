package data.scripts.shipsystems;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.util.IntervalUtil;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.util.vector.Vector2f;

import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * @deprecated
 */
public class VassFalseFuture extends BaseShipSystemScript {

    public static final Color JITTER_COLOR = new Color(255, 255, 255, 45);
    public static final Color JITTER_UNDER_COLOR = new Color(255, 255, 255, 125);

    private boolean hasTriggered = false;
    private Map<CombatEntityAPI, Vector2f> dataMap = new HashMap<>();
    private ArrayList<ShipAPI> shipList = new ArrayList<ShipAPI>();
    private ArrayList<CombatEntityAPI> dataList = new ArrayList<CombatEntityAPI>();

    //A small tracker to handle afterimages
    private float counter = 0;
    private float minAfterimageDelay = 0.002f;

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

        float jitterLevel = 0f;
        float jitterRangeBonus = 0;
        float maxRangeBonus = 5f;
        if (state == State.OUT) {
            jitterLevel = 1 - effectLevel;
            jitterRangeBonus = jitterLevel * maxRangeBonus;
        }
        jitterLevel = (float) Math.sqrt(jitterLevel);
        effectLevel *= effectLevel;

        ship.setJitter(this, JITTER_COLOR, jitterLevel, 3, 0, 0 + jitterRangeBonus);
        ship.setJitterUnder(this, JITTER_UNDER_COLOR, jitterLevel, 25, 0f, 7f + jitterRangeBonus);

        if (state == State.IN) {
            hasTriggered = true;

            //Find all ships
            for (ShipAPI targetShip : Global.getCombatEngine().getShips()) {
                if (targetShip.getLocation() != null) {
                    dataMap.put(targetShip, new Vector2f(targetShip.getLocation().x, targetShip.getLocation().y));
                    shipList.add(targetShip);
                    Global.getCombatEngine().addFloatingText(targetShip.getLocation(), "Ship position adjusted", 40f, Color.WHITE, targetShip, 1f, 5f);
                }
            }

            //Find all missiles
            for (MissileAPI targetMissile : Global.getCombatEngine().getMissiles()) {
                if (targetMissile.getLocation() != null) {
                    dataMap.put(targetMissile, new Vector2f(targetMissile.getLocation().x, targetMissile.getLocation().y));
                    dataList.add(targetMissile);
                }
            }

            //Find all projectiles
            for (DamagingProjectileAPI targetProjectile : Global.getCombatEngine().getProjectiles()) {
                if (targetProjectile.getLocation() != null) {
                    dataMap.put(targetProjectile, new Vector2f(targetProjectile.getLocation().x, targetProjectile.getLocation().y));
                    dataList.add(targetProjectile);
                }
            }
        } else if (state == State.OUT) {
            Global.getCombatEngine().getTimeMult().modifyMult(id, 0.01f);
            counter += Global.getCombatEngine().getElapsedInLastFrame();
            if (counter > minAfterimageDelay * (1 - effectLevel)) {
                counter = 0;
                for (ShipAPI targetShip : shipList) {
                    if (targetShip != null) {
                        targetShip.addAfterimage(new Color(130, 130, 130), 0f, 0f, (dataMap.get(targetShip).x - targetShip.getLocation().x) * 100f, (dataMap.get(targetShip).y - targetShip.getLocation().y) * 100f,
                                0f, 0f, 0f, 0.01f, true, false, true);
                    }
                }
            }
        }
    }


    public void unapply(MutableShipStatsAPI stats, String id) {
        ShipAPI ship = null;
        if (stats.getEntity() instanceof ShipAPI) {
            ship = (ShipAPI) stats.getEntity();
            id = id + "_" + ship.getId();
        } else {
            return;
        }
		
        Global.getCombatEngine().getTimeMult().unmodify(id);

        hasTriggered = false;

        for (CombatEntityAPI entity : dataList) {
            if (entity != null) {
                entity.getLocation().x = dataMap.get(entity).x;
                entity.getLocation().y = dataMap.get(entity).y;
            }
        }
        for (ShipAPI targetShip : shipList) {
            if (targetShip != null) {
                Global.getCombatEngine().addFloatingText(targetShip.getLocation(), "" + (int)dataMap.get(targetShip).x + " and " + (int)targetShip.getLocation().x, 40f, Color.WHITE, targetShip, 1f, 5f);
                //-------------------------------------------------------------------------------------------------------------------------------------------------------------------THIS DOES NOT WORK, BUT I DONT KNOW WHY
                targetShip.getLocation().x = dataMap.get(targetShip).x;
                targetShip.getLocation().y = dataMap.get(targetShip).y;
                //------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------
            }
        }

        dataList.clear();
        dataMap.clear();
        shipList.clear();
    }

    public StatusData getStatusData(int index, State state, float effectLevel) {
        if (index == 0 && state != State.OUT) {
            return new StatusData("Preparing for mass time-reversal", true);
        } else if (index == 0) {
            return new StatusData("The present is melting away...", false);
        }
        return null;
    }
}