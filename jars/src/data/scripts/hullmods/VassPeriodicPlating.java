package data.scripts.hullmods;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.combat.BaseHullMod;
import com.fs.starfarer.api.combat.ShipAPI;
import com.fs.starfarer.api.combat.ShipAPI.HullSize;
import com.fs.starfarer.api.combat.*;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.graphics.SpriteAPI;
import com.fs.starfarer.api.mission.FleetSide;
import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.scripts.campaign.VassFamilyTrackerPlugin;
import data.scripts.util.MagicRender;
import data.scripts.utils.VassUtils;
import org.apache.log4j.Logger;
import org.lazywizard.lazylib.FastTrig;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;

/**
 * Handles all hullmod effects of the Periodic plating (or, well, most; some is outsourced)
 * @author Nicke535
 */
public class VassPeriodicPlating extends BaseHullMod {
	public static final Logger LOGGER = Global.getLogger(VassPeriodicPlating.class);

	//Stats
    public static final float TIME_MULT = 1.2f;
    public static final Color AFTERIMAGE_COLOR_STANDARD = new Color(80, 255, 38, 100);
    public static final float AFTERIMAGE_THRESHHOLD = 0.1f;

    //How much crew percentage is lost from SO, per minute spent in combat. Not actually *applied* in this script, but the data is stored here for use later
	public static final float CREW_LOST_FRACTION_PER_MINUTE = 0.07f;

	//The time spent in combat by the ship, for calculating SO crew losses. Added here, cleared and used in a campaign script
	public static Map<String, Float> timeInCombatMap = new HashMap<String, Float>();

	private WeakHashMap<ShipAPI, VassUtils.VASS_FAMILY> familyMembership = new WeakHashMap<>();
	private WeakHashMap<ShipAPI, Boolean> eliteStatus = new WeakHashMap<>();
	
	//Changes the ships time mult at every "advanceInCombat", in order to make sure the global time mult is correct in relation to the player ship
	@Override
	public void advanceInCombat(ShipAPI ship, float amount) {
		//Ensures we can properly get our family and elite-ness before running other code
		if (!familyMembership.containsKey(ship)) {
			try {
				familyMembership.put(ship, VassUtils.getFamilyMembershipOfShip(ship));
			} catch (IllegalStateException e) {
				familyMembership.remove(ship);
				return;
			}
		}
		if (!eliteStatus.containsKey(ship)) {
			try {
				eliteStatus.put(ship, VassUtils.isShipAnElite(ship));
			} catch (IllegalStateException e) {
				eliteStatus.remove(ship);
				return;
			}
		}

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

		//We might belong to a specific family; if so, we run their special advance script instead
		if (familyMembership.get(ship) != null && eliteStatus.get(ship)) {
			if (familyMembership.get(ship) == VassUtils.VASS_FAMILY.ACCEL) {
				advanceAccel(ship, amount);
			} else if (familyMembership.get(ship) == VassUtils.VASS_FAMILY.TORPOR) {
				advanceTorpor(ship, amount);
			} else if (familyMembership.get(ship) == VassUtils.VASS_FAMILY.PERTURBA) {
				advancePerturba(ship, amount);
			} else if (familyMembership.get(ship) == VassUtils.VASS_FAMILY.RECIPRO) {
				advanceRecipro(ship, amount);
			} else if (familyMembership.get(ship) == VassUtils.VASS_FAMILY.MULTA) {
				advanceMulta(ship, amount);
			}
		} else {
			//Only activate plating if allowed
			if (platingCanBeActive(ship)) {
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
					renderAfterimage(ship, familyMembership.get(ship));
					ship.getMutableStats().getDynamic().getStat("VassAfterimageTracker").modifyFlat("VassAfterimageTrackerID",ship.getMutableStats().getDynamic().getStat("VassAfterimageTracker").getModifiedValue()-AFTERIMAGE_THRESHHOLD);
				}
			} else {
				ship.getMutableStats().getTimeMult().unmodify("VassPeriodicPlatingDebugID");
				if (ship == Global.getCombatEngine().getPlayerShip()) {
					Global.getCombatEngine().getTimeMult().unmodify("VassPeriodicPlatingDebugID");
				}
			}
		}
	}

	/**
	 * Utility function: checks if the ship can currently use its periodic plating
	 * @param ship ship to check for
	 * @return whether the periodic plating of the ship is allowed to be active
	 */
	private static boolean platingCanBeActive (ShipAPI ship) {
		if (ship.getSystem().isActive() || ship.getFluxTracker().isOverloadedOrVenting() || ship.isHulk() || ship.isLanding()) {
			return false;
		}

		//Related to bug with how vanilla systems work when tagged as "weapon"
		Object chronoIllusionActive = Global.getCombatEngine().getCustomData().get("VassChronoIllusionIsActive"+ship.getId());
		if (chronoIllusionActive instanceof Boolean) {
			if ((boolean)chronoIllusionActive) {
				return false;
			}
		}

		return true;
	}

	//Prevents the hullmod from being put on ships
	@Override
	public boolean isApplicableToShip(ShipAPI ship) {
		return false;
	}

	//A whole bunch of descriptions, most unused for now
	public String getDescriptionParam(int index, HullSize hullSize) {
		if (index == 0) {
			return Math.round((TIME_MULT-1f)*100f) + "%";
		}
		return null;
	}

	//For the cool extra description section
	@Override
	public void addPostDescriptionSection(TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
		if (!isForModSpec) {
			addPostDescriptionContractBonus(tooltip, hullSize, ship, width, isForModSpec);
			addPostDescriptionHullmodSynergies(tooltip, hullSize, ship, width, isForModSpec);
		}
	}

	//For bonuses gotten from being a member of a Vass family; not really used now except for debugging
	private void addPostDescriptionContractBonus (TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
		//This does nothing if we're not a member of a family
		VassUtils.VASS_FAMILY family = null;
		try {family = VassUtils.getFamilyMembershipOfShip(ship);} catch (IllegalStateException e) {LOGGER.warn(e.getMessage());}
		if (family == null) {
			return;
		}

		float pad = 10f;
		tooltip.addSectionHeading("Family Membership Bonus", Alignment.MID, pad);

		//If we have family membership, inform the player of its benefits
		//Perturba : Weapon bonuses... This thing isn't gonna fit in the screen, is it?
		if (family == VassUtils.VASS_FAMILY.PERTURBA) {
			TooltipMakerAPI text = tooltip.beginImageWithText("graphics/hullmods/targeting_supercomputer.png", 36); //TODO: fix proper icon
			text.addPara("Perturba - Exotic weapon specialists", 0, VassUtils.getFamilyColor(VassUtils.VASS_FAMILY.PERTURBA, 1f), Misc.getHighlightColor(), "Exotic weapon specialists");
			text.addPara("Yawarakai-Te: +150 SU before damage falloff, +20%% damage", 2, Misc.getHighlightColor(),"Yawarakai-Te", "+150", "+20%");
			text.addPara("Dyrnwyn/Cyllel Farchog: Firerate bonus applies at half the normal time variation", 2, Misc.getHighlightColor(), "Dyrnwyn", "Cyllel Farchog", "half");
			text.addPara("Perturba Missile Weapons: Regenerates ammo at 20%% of reload rate", 2, Misc.getHighlightColor(), "Perturba Missile Weapons", "Regenerates", "20%");
			tooltip.addImageWithText(pad);
		}
	}

	//For various hullmods that interact strangely with the hullmod
	private void addPostDescriptionHullmodSynergies (TooltipMakerAPI tooltip, ShipAPI.HullSize hullSize, ShipAPI ship, float width, boolean isForModSpec) {
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

	//Renders a single afterimage of the ship
	private void renderAfterimage(ShipAPI ship, VassUtils.VASS_FAMILY family) {
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

		//Gets a color for the afterimage
		Color colorToUse = AFTERIMAGE_COLOR_STANDARD;

		//Elites from a family gets a different color
		if (family != null && eliteStatus.get(ship)) {
			colorToUse = VassUtils.getFamilyColor(family, 0.4f);
		}

		MagicRender.battlespace(
				Global.getSettings().getSprite(ship.getHullSpec().getSpriteName()),
				new Vector2f(ship.getLocation().getX()+trueOffsetX,ship.getLocation().getY()+trueOffsetY),
				new Vector2f(0, 0),
				new Vector2f(ship.getSpriteAPI().getWidth(), ship.getSpriteAPI().getHeight()),
				new Vector2f(0, 0),
				ship.getFacing()-90f,
				0f,
				colorToUse,
				true,
				0.01f,
				0f,
				0.35f,
				layer);
	}


	// --- FAMILY SPECIAL ADVANCE SCRIPTS! --- //
	//Accel : TODO
	private void advanceAccel(ShipAPI ship, float amount) {
		//Only activate plating if allowed
		if (platingCanBeActive(ship)) {
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
				renderAfterimage(ship, familyMembership.get(ship));
				ship.getMutableStats().getDynamic().getStat("VassAfterimageTracker").modifyFlat("VassAfterimageTrackerID",ship.getMutableStats().getDynamic().getStat("VassAfterimageTracker").getModifiedValue()-AFTERIMAGE_THRESHHOLD);
			}
		} else {
			ship.getMutableStats().getTimeMult().unmodify("VassPeriodicPlatingDebugID");
			if (ship == Global.getCombatEngine().getPlayerShip()) {
				Global.getCombatEngine().getTimeMult().unmodify("VassPeriodicPlatingDebugID");
			}
		}
	}

	//Torpor : TODO
	private void advanceTorpor(ShipAPI ship, float amount) {
		//Only activate plating if allowed
		if (platingCanBeActive(ship)) {
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
				renderAfterimage(ship, familyMembership.get(ship));
				ship.getMutableStats().getDynamic().getStat("VassAfterimageTracker").modifyFlat("VassAfterimageTrackerID",ship.getMutableStats().getDynamic().getStat("VassAfterimageTracker").getModifiedValue()-AFTERIMAGE_THRESHHOLD);
			}
		} else {
			ship.getMutableStats().getTimeMult().unmodify("VassPeriodicPlatingDebugID");
			if (ship == Global.getCombatEngine().getPlayerShip()) {
				Global.getCombatEngine().getTimeMult().unmodify("VassPeriodicPlatingDebugID");
			}
		}
	}

	//Perturba : Exotic weapon specialists
	//Gain various bonuses to certain weapons that Perturba has helped develop
	private void advancePerturba(ShipAPI ship, float amount) {
		//Registers that we should gain our weapon bonuses : the actual bonuses are handled per-weapon
		Global.getCombatEngine().getCustomData().put("VassPerturbaPeriodicPlatingBonus" + ship.getId(), true);

		//Only activate plating if allowed
		if (platingCanBeActive(ship)) {
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
				renderAfterimage(ship, familyMembership.get(ship));
				ship.getMutableStats().getDynamic().getStat("VassAfterimageTracker").modifyFlat("VassAfterimageTrackerID",ship.getMutableStats().getDynamic().getStat("VassAfterimageTracker").getModifiedValue()-AFTERIMAGE_THRESHHOLD);
			}
		} else {
			ship.getMutableStats().getTimeMult().unmodify("VassPeriodicPlatingDebugID");
			if (ship == Global.getCombatEngine().getPlayerShip()) {
				Global.getCombatEngine().getTimeMult().unmodify("VassPeriodicPlatingDebugID");
			}
		}
	}

	//Recipro : TODO
	private void advanceRecipro(ShipAPI ship, float amount) {
		//Only activate plating if allowed
		if (platingCanBeActive(ship)) {
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
				renderAfterimage(ship, familyMembership.get(ship));
				ship.getMutableStats().getDynamic().getStat("VassAfterimageTracker").modifyFlat("VassAfterimageTrackerID",ship.getMutableStats().getDynamic().getStat("VassAfterimageTracker").getModifiedValue()-AFTERIMAGE_THRESHHOLD);
			}
		} else {
			ship.getMutableStats().getTimeMult().unmodify("VassPeriodicPlatingDebugID");
			if (ship == Global.getCombatEngine().getPlayerShip()) {
				Global.getCombatEngine().getTimeMult().unmodify("VassPeriodicPlatingDebugID");
			}
		}
	}

	//Multa : TODO
	private void advanceMulta(ShipAPI ship, float amount) {
		//Only activate plating if allowed
		if (platingCanBeActive(ship)) {
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
				renderAfterimage(ship, familyMembership.get(ship));
				ship.getMutableStats().getDynamic().getStat("VassAfterimageTracker").modifyFlat("VassAfterimageTrackerID",ship.getMutableStats().getDynamic().getStat("VassAfterimageTracker").getModifiedValue()-AFTERIMAGE_THRESHHOLD);
			}
		} else {
			ship.getMutableStats().getTimeMult().unmodify("VassPeriodicPlatingDebugID");
			if (ship == Global.getCombatEngine().getPlayerShip()) {
				Global.getCombatEngine().getTimeMult().unmodify("VassPeriodicPlatingDebugID");
			}
		}
	}
}
