package data.scripts.shipsystems;

import java.awt.Color;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import org.lazywizard.lazylib.LazyLib;
import org.lazywizard.lazylib.VectorUtils;
import org.lwjgl.util.vector.Vector2f;
import org.lazywizard.lazylib.MathUtils;

public class VassTemporalRecall extends BaseShipSystemScript {

    public static final float RECALL_SPREAD = 250f;

    public static final Color JITTER_COLOR = new Color(255,255,255,30);
    public static final Color JITTER_UNDER_COLOR = new Color(255,255,255,90);

    public boolean hasATarget = false;
    public Vector2f targetPos = new Vector2f(0f, 0f);
    public boolean runOncePerActivation = false;


    public void apply(MutableShipStatsAPI stats, String id, State state, float effectLevel) {
        ShipAPI ship = null;
        if (stats.getEntity() instanceof ShipAPI) {
            ship = (ShipAPI) stats.getEntity();
            id = id + "_" + ship.getId();
        } else {
            return;
        }

        //Teleporting code
        if (!hasATarget && state == State.IN) {
            targetPos = MathUtils.getRandomPointOnCircumference(new Vector2f(0f, 0f), RECALL_SPREAD);
            hasATarget = true;
        }

        //Plays the system sound at the arrival position, too
        if (!runOncePerActivation) {
            Global.getSoundPlayer().playSound("vass_temporal_recall", 1f, 0.5f, new Vector2f(ship.getWing().getSourceShip().getLocation().x + targetPos.x, ship.getWing().getSourceShip().getLocation().y + targetPos.y), new Vector2f(0f, 0f));
            runOncePerActivation = true;
        }

        if (effectLevel > 0.1f && hasATarget) {
			//Jitter-Based code
			float jitterLevel = effectLevel;
			float jitterRangeBonus = 3f;
			float maxRangeBonus = 15f;
			if (state == State.IN) {
				jitterRangeBonus = jitterLevel * maxRangeBonus;
			} else if (state == State.ACTIVE) {
				jitterLevel = 1f;
				jitterRangeBonus = maxRangeBonus;
			} else if (state == State.OUT) {
				jitterRangeBonus = jitterLevel * maxRangeBonus;
			}
			jitterLevel = (float)Math.sqrt(jitterLevel);

			ship.setJitter(this, JITTER_COLOR, jitterLevel, 3, 0, 2f + jitterRangeBonus);
			ship.setJitterUnder(this, JITTER_UNDER_COLOR, jitterLevel, 25, 0f, 7f + jitterRangeBonus);
            
			//Creates a copy of the ship near the carrier
            ship.setCopyLocation(new Vector2f(ship.getWing().getSourceShip().getLocation().x + targetPos.x, ship.getWing().getSourceShip().getLocation().y + targetPos.y), (effectLevel - 0.1f), ship.getFacing());

            //Actual teleportation
            if (state == State.ACTIVE && hasATarget) {
                int particleCount = 30;
                float particleSpread = 75f;
                while (particleCount > 0) {
                    Global.getCombatEngine().addSmoothParticle(MathUtils.getRandomPointInCircle(ship.getLocation(), 10), Vector2f.add(MathUtils.getRandomPointInCircle(new Vector2f(0f, 0f), particleSpread), ship.getVelocity(), new Vector2f(0f,0f)), (float)Math.random() * 40f, (float)Math.random(), (float)Math.random() * 2f, JITTER_UNDER_COLOR);
                    particleCount--;
                }

                ship.getLocation().x = ship.getWing().getSourceShip().getLocation().x + targetPos.x;
                ship.getLocation().y = ship.getWing().getSourceShip().getLocation().y + targetPos.y;
                ship.getVelocity().x = 0f;
                ship.getVelocity().y = 0f;
                hasATarget = false;
            }
        } else {
            ship.setCopyLocation(new Vector2f(0f, 0f), 0f, ship.getFacing());
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
        //Clears all saved data and sets the clone to its proper position
        targetPos = new Vector2f(0f, 0f);
        runOncePerActivation = false;
    }

    public StatusData getStatusData(int index, State state, float effectLevel) {
        return null;
    }
}
