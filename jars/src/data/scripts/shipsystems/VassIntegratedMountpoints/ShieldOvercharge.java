package data.scripts.shipsystems.VassIntegratedMountpoints;

import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShieldAPI;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipSystemAPI;
import com.fs.starfarer.api.plugins.ShipSystemStatsScript;
import com.fs.starfarer.api.util.Misc;
import org.lazywizard.lazylib.CollisionUtils;

import java.awt.Color;

/**
 * This is the default sub-system used when no weapon is mounted: instead of boosting our weapon, we boost our shields.
 * Similar to the Fortress Shield, but not quite as powerful or sophisticated
 */
public class ShieldOvercharge extends SubSystem {
    public static final Color INNER_COLOR = new Color(50, 255, 210);
    public static final float SHIELD_UNFOLD_MULT = 3f;
    public static final float SHIELD_ROTATION_MULT = 3f;
    public static final float SHIELD_DAMAGE_MULT = 0.25f;

    private Color oldColor = null;

    @Override
    public void apply(MutableShipStatsAPI stats, String id, ShipSystemStatsScript.State state, ShipAPI ship, boolean player, float effectLevel) {
        //Run unapply if needed
        if (effectLevel <= 0f) {
            unapply(stats, id, ship, player);
            return;
        }

        //If we have no shields, do nothing
        if (ship.getShield() == null || ship.getShield().getType().equals(ShieldAPI.ShieldType.NONE) || ship.getShield().getType().equals(ShieldAPI.ShieldType.PHASE)) {
            return;
        }

        //Ensure our shield color is appropriate
        if (oldColor == null) {
            oldColor = ship.getShield().getInnerColor();
        }
        ship.getShield().setInnerColor(Misc.interpolateColor(oldColor, INNER_COLOR, effectLevel));

        //Apply stat changes
        stats.getShieldUnfoldRateMult().modifyMult(id, Misc.interpolate(1f, SHIELD_UNFOLD_MULT, effectLevel));
        stats.getShieldDamageTakenMult().modifyMult(id, Misc.interpolate(1f, SHIELD_DAMAGE_MULT, effectLevel));
        stats.getShieldTurnRateMult().modifyMult(id, Misc.interpolate(1f, SHIELD_ROTATION_MULT, effectLevel));
    }

    @Override
    public void unapply(MutableShipStatsAPI stats, String id, ShipAPI ship, boolean player) {
        //If we have no shields, do nothing
        if (ship.getShield() == null || ship.getShield().getType().equals(ShieldAPI.ShieldType.NONE) || ship.getShield().getType().equals(ShieldAPI.ShieldType.PHASE)) {
            return;
        }

        // Set the color back to what it should be, or save our intended color if we haven't run apply() yet
        if (oldColor != null) {
            ship.getShield().setInnerColor(oldColor);
        } else {
            oldColor = ship.getShield().getInnerColor();
        }

        //Unapply stat changes
        stats.getShieldUnfoldRateMult().unmodify(id);
        stats.getShieldDamageTakenMult().unmodify(id);
        stats.getShieldTurnRateMult().unmodify(id);
    }

    @Override
    public boolean isUsable(ShipSystemAPI system, ShipAPI ship) {
        if (ship.getShield() == null || ship.getShield().getType().equals(ShieldAPI.ShieldType.NONE) || ship.getShield().getType().equals(ShieldAPI.ShieldType.PHASE)) {
            return false;
        } else {
            return super.isUsable(system, ship);
        }
    }

    @Override
    public String getDisplayName(ShipSystemStatsScript.State state, float effectLevel) {
        return "Shield Overcharge";
    }

    @Override
    public String getTextToDisplay(ShipSystemAPI system, ShipAPI ship) {
        if (ship.getShield() == null || ship.getShield().getType().equals(ShieldAPI.ShieldType.NONE) || ship.getShield().getType().equals(ShieldAPI.ShieldType.PHASE)) {
            return "NO SHIELDS!";
        } else if (system.getState().equals(ShipSystemAPI.SystemState.IDLE)) {
            return "Ready";
        } else if (system.getState().equals(ShipSystemAPI.SystemState.ACTIVE)) {
            return "Active";
        } else {
            return null;
        }
    }
}
