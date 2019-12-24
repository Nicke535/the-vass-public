package data.scripts.shipsystems;

import java.awt.Color;
import java.util.List;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.CombatEngineLayers;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.impl.combat.BaseShipSystemScript;
import com.fs.starfarer.api.util.Misc;
import data.scripts.util.MagicRender;
import data.scripts.utils.VassUtils;
import org.lazywizard.lazylib.VectorUtils;
import org.lazywizard.lazylib.combat.entities.SimpleEntity;
import org.lwjgl.util.vector.Vector2f;
import org.lazywizard.lazylib.MathUtils;

/**
 * Handles the Temporal Recall shipsystem
 * @author Nicke535
 */
public class VassTemporalRecall extends BaseShipSystemScript {

    public static final float RECALL_SPREAD = 250f;

    public static final Color JITTER_COLOR = new Color(255,255,255,20);
    public static final Color JITTER_UNDER_COLOR = new Color(255,255,255,60);
    public static final Color AFTERIMAGE_COLOR = new Color(255,255,255,140);

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
			ship.setJitter(this, JITTER_COLOR, jitterLevel, 3, 0, 2f + jitterRangeBonus);
            jitterLevel = (float)Math.sqrt(jitterLevel);

            ship.setJitterUnder(this, JITTER_UNDER_COLOR, jitterLevel, 25, 0f, 7f + jitterRangeBonus);
            
			//Creates a copy of the ship near the carrier
            ship.setCopyLocation(new Vector2f(ship.getWing().getSourceShip().getLocation().x + targetPos.x, ship.getWing().getSourceShip().getLocation().y + targetPos.y), (effectLevel * 0.4f), ship.getFacing());

            //Actual teleportation
            if (state == State.ACTIVE && hasATarget) {
                spawnAfterImageStreak(ship, 19);

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

    // Generates a bunch of copies along a fancy arc, removing the centermost ones
    private void spawnAfterImageStreak(ShipAPI ship, int numberOfCopiesPerEnd) {
        int actualPointsToGenerate = Math.max(numberOfCopiesPerEnd*2, (int)MathUtils.getDistance(ship.getLocation(), targetPos)/15);
        Vector2f actualTargetPos = Vector2f.add(targetPos, ship.getWing().getSourceShip().getLocation(), null);
        List<Vector2f> points = VassUtils.getFancyArcPoints(ship.getLocation(), actualTargetPos, MathUtils.getRandomNumberInRange(-180f, 180f), actualPointsToGenerate);

        float endRenderAngle = ship.getFacing();
        for (int i = 0; i < numberOfCopiesPerEnd; i++) {
            float progress = (float)i / (float)numberOfCopiesPerEnd;

            //Renders a points near the start of the arc
            float fakeVelocityAngle1 = VectorUtils.getAngleStrict(points.get(i+1), points.get(i));
            MagicRender.battlespace(
                    Global.getSettings().getSprite(ship.getHullSpec().getSpriteName()),
                    points.get(i),
                    new Vector2f(0, 0),
                    new Vector2f(ship.getSpriteAPI().getWidth(), ship.getSpriteAPI().getHeight()),
                    new Vector2f(0, 0),
                    (progress * fakeVelocityAngle1 + (1f - progress) * endRenderAngle) - 90f,
                    0f,
                    Misc.interpolateColor(AFTERIMAGE_COLOR, new Color(0f, 0f, 0f, 0f), progress),
                    true,
                    0.02f,
                    0.05f,
                    0.2f * progress,
                    CombatEngineLayers.ABOVE_SHIPS_LAYER);

            //Renders a point near the end of the arc
            float fakeVelocityAngle2 = VectorUtils.getAngleStrict(points.get((points.size()-1)-i), points.get((points.size()-1)-i-1));
            MagicRender.battlespace(
                    Global.getSettings().getSprite(ship.getHullSpec().getSpriteName()),
                    points.get((points.size()-1)-i),
                    new Vector2f(0, 0),
                    new Vector2f(ship.getSpriteAPI().getWidth(), ship.getSpriteAPI().getHeight()),
                    new Vector2f(0, 0),
                    (progress * fakeVelocityAngle2 + (1f - progress) * endRenderAngle) - 90f,
                    0f,
                    Misc.interpolateColor(AFTERIMAGE_COLOR, new Color(0f, 0f, 0f, 0f), progress),
                    true,
                    0.02f,
                    0.25f,
                    0.2f * (1f - progress),
                    CombatEngineLayers.ABOVE_SHIPS_LAYER);
        }
    }
}
