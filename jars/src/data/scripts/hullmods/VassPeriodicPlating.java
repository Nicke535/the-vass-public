package data.scripts.hullmods;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.MutableShipStatsAPI;
import com.fs.starfarer.api.combat.ShieldAPI;
import com.fs.starfarer.api.combat.ShieldAPI.ShieldType;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.Pair;
import data.scripts.util.MagicRender;
import org.lazywizard.lazylib.FastTrig;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;

public class VassPeriodicPlating extends BaseHullMod {
    public static final float TIME_MULT = 1.2f;
    public static final Color AFTERIMAGE_COLOR = new Color(80, 255, 38, 100);
    public static final float AFTERIMAGE_THRESHHOLD = 0.1f;

    //How much crew percentage is lost from SO, per minute spent in combat. Not actually *applied* in this script, but the data is stored here for use later
	public static final float CREW_LOST_FRACTION_PER_MINUTE = 0.07f;

	//The time spent in combat by the ship, for calculating SO crew losses. Added here, cleared and used in a campaign script
	public static Map<String, Float> timeInCombatMap = new HashMap<String, Float>();
	
	//Changes the ships time mult at every "advanceInCombat", in order to make sure the global time mult is correct in relation to the player ship
	@Override
	public void advanceInCombat(ShipAPI ship, float amount) {
		//If we have SO equipped, we store our time spent in combat (only for the player fleet, in the campaign)
		if (ship.getVariant().hasHullMod("safetyoverrides") && Global.getCombatEngine().getFleetManager(ship.getOwner()) == Global.getCombatEngine().getFleetManager(FleetSide.PLAYER)) {
			FleetMemberAPI member = CombatUtils.getFleetMember(ship);
			if (member != null) {
				//We assume that if the ship is destroyed, the additional crew loss is marginal at most, so we remove it from our nice list
				if (ship.isPiece() || ship.isHulk()) {
					timeInCombatMap.remove(member.getId());
				} else {
					if (timeInCombatMap.get(member.getId()) == null) {
						timeInCombatMap.put(member.getId(), 0f);
					} else {
						timeInCombatMap.put(member.getId(), timeInCombatMap.get(member.getId()) + amount);
					}
				}
			}
		}

		//Not active during shipsystem, overload, landing, or when dead
		if (!ship.getSystem().isActive() && !ship.getFluxTracker().isOverloadedOrVenting() && !ship.isHulk() && !ship.isLanding()) {
			if (ship == Global.getCombatEngine().getPlayerShip()) {
				ship.getMutableStats().getTimeMult().modifyMult("VassPeriodicPlatingDebugID", TIME_MULT);
				Global.getCombatEngine().getTimeMult().modifyMult("VassPeriodicPlatingDebugID", 1f / TIME_MULT);
			} else {
				ship.getMutableStats().getTimeMult().modifyMult("VassPeriodicPlatingDebugID", TIME_MULT);
				Global.getCombatEngine().getTimeMult().unmodify("VassPeriodicPlatingDebugID");
			}
			
			ship.getMutableStats().getDynamic().getStat("VassAfterimageTracker").modifyFlat("VassAfterimageTrackerNullerID",-1);
			ship.getMutableStats().getDynamic().getStat("VassAfterimageTracker").modifyFlat("VassAfterimageTrackerID",ship.getMutableStats().getDynamic().getStat("VassAfterimageTracker").getModifiedValue()+amount);
			if (ship.getMutableStats().getDynamic().getStat("VassAfterimageTracker").getModifiedValue() > AFTERIMAGE_THRESHHOLD) {
				// Sprite offset fuckery - Don't you love trigonometry?
				SpriteAPI sprite = ship.getSpriteAPI();
				float offsetX = sprite.getWidth()/2 - sprite.getCenterX();
				float offsetY = sprite.getHeight()/2 - sprite.getCenterY();

				float trueOffsetX = (float)FastTrig.cos(Math.toRadians(ship.getFacing()-90f))*offsetX - (float)FastTrig.sin(Math.toRadians(ship.getFacing()-90f))*offsetY;
				float trueOffsetY = (float)FastTrig.sin(Math.toRadians(ship.getFacing()-90f))*offsetX + (float)FastTrig.cos(Math.toRadians(ship.getFacing()-90f))*offsetY;

				//Determines a layer to render on: fighters render above ships but below fighters, while everything else render below ships
				CombatEngineLayers layer = CombatEngineLayers.BELOW_SHIPS_LAYER;
				if (ship.getHullSize().equals(HullSize.FIGHTER)) {
					layer = CombatEngineLayers.CONTRAILS_LAYER;
				}

				MagicRender.battlespace(
						Global.getSettings().getSprite(ship.getHullSpec().getSpriteName()),
						new Vector2f(ship.getLocation().getX()+trueOffsetX,ship.getLocation().getY()+trueOffsetY),
						new Vector2f(0, 0),
						new Vector2f(ship.getSpriteAPI().getWidth(), ship.getSpriteAPI().getHeight()),
						new Vector2f(0, 0),
						ship.getFacing()-90f,
						0f,
						AFTERIMAGE_COLOR,
						true,
						0.01f,
						0f,
						0.35f,
						layer);
				ship.getMutableStats().getDynamic().getStat("VassAfterimageTracker").modifyFlat("VassAfterimageTrackerID",ship.getMutableStats().getDynamic().getStat("VassAfterimageTracker").getModifiedValue()-AFTERIMAGE_THRESHHOLD);
			}
		} else {
			ship.getMutableStats().getTimeMult().unmodify("VassPeriodicPlatingDebugID");
            if (ship == Global.getCombatEngine().getPlayerShip()) {
                Global.getCombatEngine().getTimeMult().unmodify("VassPeriodicPlatingDebugID");
            }
		}
	}

	//Prevents the hullmod from being put on ships
	@Override
	public boolean isApplicableToShip(ShipAPI ship) {
		return false;
	}

	//A whole bunch of descriptions, most unused for now
	public String getDescriptionParam(int index, HullSize hullSize) {
		return null;
	}

	//For the cool extra description section
	@Override
	public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
		//If we don't have any hullmod synergies, don't display the paragraph at all
		if (!ship.getVariant().hasHullMod("safetyoverrides")) {
			return;
		}

		float pad = 10f;
		tooltip.addSectionHeading("Hullmod Synergies", Alignment.MID, pad);

		//If we have Safety Overrides, inform the player of its... issues
		if (ship.getVariant().hasHullMod("safetyoverrides")) {
			float crewLossPerMinute = CREW_LOST_FRACTION_PER_MINUTE * (ship.getHullSpec().getMinCrew() * ship.getMutableStats().getMinCrewMod().getMult() + ship.getMutableStats().getMinCrewMod().getFlatBonus());
			String crewLossPerMinuteWithEventualDot = "" + (int)Math.floor(crewLossPerMinute);
			if (crewLossPerMinute < 1f) {
				crewLossPerMinuteWithEventualDot += "." + (int)Math.floor(crewLossPerMinute * 10f);
			}

			TooltipMakerAPI text = tooltip.beginImageWithText("graphics/hullmods/safety_overrides.png", 36);
			text.addPara("Safety Overrides", 0, Color.RED, "Safety Overrides");
			text.addPara("Without proper safety protocols, the dangers of the Periodic Plating become painfully obvious. The ship loses an average of " +
					crewLossPerMinuteWithEventualDot  + " combat-capable crew per minute spent in combat from mental " +
					"degradation.", 0, Color.ORANGE, crewLossPerMinuteWithEventualDot, "minute");
			//No flavor text: it gets too big
			/* text.addPara("Having overridden most if not all safety parameters on the ship, the hazardous aspects of the Periodic Plating become painfully obvious. " +
							"While cabin crew and officers are still in the relative safety of the ship's bridge, engineers and maintenance personnel are exposed directly to the " +
							"device's destructive and mind-altering properties without any form of effective protection.", Misc.getGrayColor(),0f); */
			tooltip.addImageWithText(pad);
		}
	}
}
