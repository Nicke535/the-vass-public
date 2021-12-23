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
import data.scripts.util.MagicRender;
import data.scripts.utils.VassUtils;
import data.scripts.weapons.MagicVectorThruster;
import org.apache.log4j.Logger;
import org.lazywizard.lazylib.FastTrig;
import org.lazywizard.lazylib.MathUtils;
import org.lazywizard.lazylib.combat.CombatUtils;
import org.lwjgl.util.vector.Vector2f;

/**
 * Handles all hullmod effects of the Periodic plating (or, well, most; some is outsourced)
 * @author Nicke535
 */
public class VassPeriodicPlating extends BaseHullMod {
	public static final Logger LOGGER = Global.getLogger(VassPeriodicPlating.class);

	//Global stats
    public static final float TIME_MULT = 1.2f;
    public static final Color AFTERIMAGE_COLOR_STANDARD = new Color(80, 255, 38, 100);
    public static final float AFTERIMAGE_THRESHHOLD = 0.1f;

    //How much crew percentage is lost from SO, per minute spent in combat. Not actually *applied* in this script, but the data is stored here for use later
	public static final float CREW_LOST_FRACTION_PER_MINUTE = 0.07f;


	//Accel stats
	public static final float ACCEL_TIME_MULT_HIGH = 1.35f;
	public static final float ACCEL_TIME_MULT_LOW = 1.1f;
	public static final int ACCEL_SYSTEM_SPEED_BOOST = 100;


	//Multa stats
	public static final float MULTA_OVERLOAD_TIME_INCREASE_PERCENT = 50f;
	public static final float MULTA_OVERLOAD_AVOID_CHANCE = 0.75f;
	public static final float MULTA_LOW_FLUX_AMOUNT = 0.1f;
	public static final float MULTA_HARDFLUX_DISSIPATION = 25f;
	public static final float MULTA_DISSIPATION_BONUS = 75f;
	public static final Map<HullSize, SoundData> MULTA_OVERLOAD_AVOID_SFX = new HashMap<>();
	static {
		MULTA_OVERLOAD_AVOID_SFX.put(HullSize.FIGHTER, new SoundData("vass_overload_cancel", 0.4f, 1f));
		MULTA_OVERLOAD_AVOID_SFX.put(HullSize.FRIGATE, new SoundData("vass_overload_cancel", 0.7f, 1f));
		MULTA_OVERLOAD_AVOID_SFX.put(HullSize.DESTROYER, new SoundData("vass_overload_cancel", 0.8f, 1f));
		MULTA_OVERLOAD_AVOID_SFX.put(HullSize.CRUISER, new SoundData("vass_overload_cancel", 0.9f, 1f));
		MULTA_OVERLOAD_AVOID_SFX.put(HullSize.CAPITAL_SHIP, new SoundData("vass_overload_cancel", 1f, 1f));
	}



	//The time spent in combat by the ship, for calculating SO crew losses. Added here, cleared and used in a campaign script
	public static Map<String, Float> timeInCombatMap = new HashMap<>();

	private WeakHashMap<ShipAPI, VassUtils.VASS_FAMILY> familyMembership = new WeakHashMap<>();
	private WeakHashMap<ShipAPI, Boolean> eliteStatus = new WeakHashMap<>();

	//ID for most effects the hullmod applies
	private static final String ID = "VassPeriodicPlatingDebugID";
	
	//Changes the ship's time mult at every "advanceInCombat", in order to make sure the global time mult is correct in relation to the player ship
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

		//If we have SO equipped, we store our time spent in combat (only for the player fleet, in the campaign, outside of sims)
		if (ship.getVariant().hasHullMod("safetyoverrides")
				&& Global.getCombatEngine().getFleetManager(ship.getOwner()) == Global.getCombatEngine().getFleetManager(FleetSide.PLAYER)
				&& !Global.getCombatEngine().isSimulation()
				&& Global.getCombatEngine().isInCampaign()) {
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
					ship.getMutableStats().getTimeMult().modifyMult(ID, TIME_MULT);
					Global.getCombatEngine().getTimeMult().modifyMult(ID, 1f / TIME_MULT);
				} else {
					ship.getMutableStats().getTimeMult().modifyMult(ID, TIME_MULT);
					Global.getCombatEngine().getTimeMult().unmodify(ID);
				}

				ship.getMutableStats().getDynamic().getStat("VassAfterimageTracker").modifyFlat("VassAfterimageTrackerNullerID",-1);
				ship.getMutableStats().getDynamic().getStat("VassAfterimageTracker").modifyFlat("VassAfterimageTrackerID",ship.getMutableStats().getDynamic().getStat("VassAfterimageTracker").getModifiedValue()+amount);
				if (ship.getMutableStats().getDynamic().getStat("VassAfterimageTracker").getModifiedValue() > AFTERIMAGE_THRESHHOLD) {
					renderAfterimage(ship, familyMembership.get(ship));
					ship.getMutableStats().getDynamic().getStat("VassAfterimageTracker").modifyFlat("VassAfterimageTrackerID",ship.getMutableStats().getDynamic().getStat("VassAfterimageTracker").getModifiedValue()-AFTERIMAGE_THRESHHOLD);
				}
			} else {
				ship.getMutableStats().getTimeMult().unmodify(ID);
				if (ship == Global.getCombatEngine().getPlayerShip()) {
					Global.getCombatEngine().getTimeMult().unmodify(ID);
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
		try { family = VassUtils.getFamilyMembershipOfShip(ship); } catch (IllegalStateException e) { LOGGER.warn("Suppressed an exception : " + e.getMessage()); }
		if (family == null) {
			return;
		}

		float pad = 10f;
		tooltip.addSectionHeading("Family Membership Bonus", Alignment.MID, pad);

		//If we have family membership, inform the player of its benefits
		//Accel : Even more time mult, but loses it once CR starts ticking down
		if (family == VassUtils.VASS_FAMILY.ACCEL) {
			TooltipMakerAPI text = tooltip.beginImageWithText("graphics/vass/hullmods/torpor_hullmod.png", 36);
			text.addPara("Accel - Periodic Mastery", 0, VassUtils.getFamilyColor(VassUtils.VASS_FAMILY.ACCEL, 1f), Misc.getHighlightColor(), "Periodic Mastery");
			text.addPara("Time instead passes "+Math.round((ACCEL_TIME_MULT_HIGH-1f)*100f)+"%% faster for the ship while CR is at maximum. However, if CR falls below maximum time passes only "+Math.round((ACCEL_TIME_MULT_LOW-1f)*100f)+"%% faster instead.", 2, Misc.getHighlightColor(),Math.round((ACCEL_TIME_MULT_HIGH-1f)*100f)+"%", Math.round((ACCEL_TIME_MULT_LOW-1f)*100f)+"%");
			if (ship.getSystem().getId().equals("vass_periodic_breaker")) {
				text.addPara("Periodic Breaker: Top speed increased by "+ACCEL_SYSTEM_SPEED_BOOST+" while system is active", 2, Misc.getHighlightColor(), "Periodic Breaker", ""+ACCEL_SYSTEM_SPEED_BOOST);
			}
			tooltip.addImageWithText(pad);
		}
		//Torpor : Immunity to most effects of negative time mult
		else if (family == VassUtils.VASS_FAMILY.TORPOR) {
			TooltipMakerAPI text = tooltip.beginImageWithText("graphics/vass/hullmods/torpor_hullmod.png", 36);
			text.addPara("Torpor - Advanced Chronostabilizers", 0, VassUtils.getFamilyColor(VassUtils.VASS_FAMILY.TORPOR, 1f), Misc.getHighlightColor(), "Advanced Chronostabilizers");
			text.addPara("Ignores all penalties to firerate and dissipation from the effects of low time mult.", 2, Misc.getHighlightColor(),"firerate", "dissipation", "low time mult");
			tooltip.addImageWithText(pad);
		}
		//Perturba : Weapon bonuses... This thing isn't gonna fit in the screen, is it?
		else if (family == VassUtils.VASS_FAMILY.PERTURBA) {
			TooltipMakerAPI text = tooltip.beginImageWithText("graphics/vass/hullmods/perturba_hullmod.png", 36);
			text.addPara("Perturba - Exotic weapon specialists", 0, VassUtils.getFamilyColor(VassUtils.VASS_FAMILY.PERTURBA, 1f), Misc.getHighlightColor(), "Exotic weapon specialists");
			text.addPara("Yawarakai-Te: +150 SU before damage falloff, +20%% damage", 2, Misc.getHighlightColor(),"Yawarakai-Te", "+150", "+20%");
			text.addPara("Dyrnwyn/Cyllel Farchog: Firerate bonus applies at half the normal time variation", 2, Misc.getHighlightColor(), "Dyrnwyn", "Cyllel Farchog", "half");
			text.addPara("Perturba Missile Weapons: Regenerates ammo at 20%% of reload rate", 2, Misc.getHighlightColor(), "Perturba Missile Weapons", "Regenerates", "20%");
			tooltip.addImageWithText(pad);
		}
		//Perturba : Overload avoidance and flux bonuses at low flux. Longer overload
		else if (family == VassUtils.VASS_FAMILY.MULTA) {
			TooltipMakerAPI text = tooltip.beginImageWithText("graphics/vass/hullmods/perturba_hullmod.png", 36);
			text.addPara("Multa - Isochronal Reactor", 0, VassUtils.getFamilyColor(VassUtils.VASS_FAMILY.MULTA, 1f), Misc.getHighlightColor(), "Isochronal Reactor");
			text.addPara("While below "+(int)(MULTA_LOW_FLUX_AMOUNT*100f)+"%% flux, the ship gets "+(int)(MULTA_DISSIPATION_BONUS)+"%% higher flux dissipation " +
					"and can dissipate hardflux with shields up at "+(int)(MULTA_HARDFLUX_DISSIPATION)+"%% of normal dissipation. " +
					"The ship also has a "+(int)(MULTA_OVERLOAD_AVOID_CHANCE*100f)+"%% chance to cancel an overload shortly after it occurs, but " +
					"overloads last "+(int)MULTA_OVERLOAD_TIME_INCREASE_PERCENT+"%% longer",
					2, Misc.getHighlightColor(),(int)(MULTA_LOW_FLUX_AMOUNT*100f)+"%", (int)(MULTA_DISSIPATION_BONUS)+"%",
					(int)(MULTA_HARDFLUX_DISSIPATION)+"%", (int)(MULTA_OVERLOAD_AVOID_CHANCE*100f)+"%", (int)MULTA_OVERLOAD_TIME_INCREASE_PERCENT+"%");
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
				0f,
				0f,
				0f,
				0f,
				0f,
				0.01f,
				0f,
				0.35f,
				layer); //TODO : Confirm that this does not mess up things (specifically, flicker and jitter)
	}


	// --- FAMILY SPECIAL ADVANCE SCRIPTS! --- //
	//Accel : Periodic Mastery
	//Gets even higher time mult than normal, but loses some once CR starts ticking. Also better periodic breaker
	private void advanceAccel(ShipAPI ship, float amount) {
		//Only activate plating if allowed
		if (platingCanBeActive(ship)) {
			float currentTimeMult = ACCEL_TIME_MULT_HIGH;
			//Checks differently if a fleet member is not available
			if (ship.getFleetMember() != null) {
				if (ship.getCurrentCR() < ship.getFleetMember().getRepairTracker().getMaxCR()) {
					currentTimeMult = ACCEL_TIME_MULT_LOW;
				}
			} else {
				if (ship.getCurrentCR() < ship.getCRAtDeployment()) {
					currentTimeMult = ACCEL_TIME_MULT_LOW;
				}
			}
			if (ship == Global.getCombatEngine().getPlayerShip()) {
				ship.getMutableStats().getTimeMult().modifyMult(ID, currentTimeMult);
				Global.getCombatEngine().getTimeMult().modifyMult(ID, 1f / currentTimeMult);
			} else {
				ship.getMutableStats().getTimeMult().modifyMult(ID, currentTimeMult);
				Global.getCombatEngine().getTimeMult().unmodify(ID);
			}

			ship.getMutableStats().getDynamic().getStat("VassAfterimageTracker").modifyFlat("VassAfterimageTrackerNullerID",-1);
			ship.getMutableStats().getDynamic().getStat("VassAfterimageTracker").modifyFlat("VassAfterimageTrackerID",ship.getMutableStats().getDynamic().getStat("VassAfterimageTracker").getModifiedValue()+amount);
			if (ship.getMutableStats().getDynamic().getStat("VassAfterimageTracker").getModifiedValue() > AFTERIMAGE_THRESHHOLD) {
				renderAfterimage(ship, familyMembership.get(ship));
				ship.getMutableStats().getDynamic().getStat("VassAfterimageTracker").modifyFlat("VassAfterimageTrackerID",ship.getMutableStats().getDynamic().getStat("VassAfterimageTracker").getModifiedValue()-AFTERIMAGE_THRESHHOLD);
			}
		} else {
			ship.getMutableStats().getTimeMult().unmodify(ID);
			if (ship == Global.getCombatEngine().getPlayerShip()) {
				Global.getCombatEngine().getTimeMult().unmodify(ID);
			}
		}

		//Addional bonus: periodic breaker gives speed bonus when active
		if (ship.getSystem().getId().equals("vass_periodic_breaker") && ship.getSystem().getEffectLevel() > 0f) {
			ship.getMutableStats().getMaxSpeed().modifyFlat(ID, ACCEL_SYSTEM_SPEED_BOOST*ship.getSystem().getEffectLevel());
		} else {
			ship.getMutableStats().getMaxSpeed().unmodify(ID);
		}
	}

	//Torpor : Advanced Chronostabilizers
	//Ignores any penalties to firerate and dissipation caused by negative time mult.
	//TODO: maybe add more here?
	private void advanceTorpor(ShipAPI ship, float amount) {
		//Only activate plating if allowed
		if (platingCanBeActive(ship)) {
			if (ship == Global.getCombatEngine().getPlayerShip()) {
				ship.getMutableStats().getTimeMult().modifyMult(ID, TIME_MULT);
				Global.getCombatEngine().getTimeMult().modifyMult(ID, 1f/TIME_MULT);
			} else {
				ship.getMutableStats().getTimeMult().modifyMult(ID, TIME_MULT);
				Global.getCombatEngine().getTimeMult().unmodify(ID);
			}

			ship.getMutableStats().getDynamic().getStat("VassAfterimageTracker").modifyFlat("VassAfterimageTrackerNullerID",-1);
			ship.getMutableStats().getDynamic().getStat("VassAfterimageTracker").modifyFlat("VassAfterimageTrackerID",ship.getMutableStats().getDynamic().getStat("VassAfterimageTracker").getModifiedValue()+amount);
			if (ship.getMutableStats().getDynamic().getStat("VassAfterimageTracker").getModifiedValue() > AFTERIMAGE_THRESHHOLD) {
				renderAfterimage(ship, familyMembership.get(ship));
				ship.getMutableStats().getDynamic().getStat("VassAfterimageTracker").modifyFlat("VassAfterimageTrackerID",ship.getMutableStats().getDynamic().getStat("VassAfterimageTracker").getModifiedValue()-AFTERIMAGE_THRESHHOLD);
			}
		} else {
			ship.getMutableStats().getTimeMult().unmodify(ID);
			if (ship == Global.getCombatEngine().getPlayerShip()) {
				Global.getCombatEngine().getTimeMult().unmodify(ID);
			}
		}

		//Secondary effect always active
		//Gets the current time mult affecting the ship, and checks if it's lower than 1. Has no effect on positive time mult
		float currentTimeMult = ship.getMutableStats().getTimeMult().getModifiedValue();
		if (currentTimeMult < 1f) {
			//Calculates and applies the counter-reactive firerate bonus.
			//	Does not work when time mult is 0, but that should never happen for engine reasons: it'll crash anyhow
			float counterBonus = 1f/currentTimeMult;
			ship.getMutableStats().getBallisticRoFMult().modifyMult(ID, counterBonus);
			ship.getMutableStats().getMissileRoFMult().modifyMult(ID, counterBonus);
			ship.getMutableStats().getEnergyRoFMult().modifyMult(ID, counterBonus);
			ship.getMutableStats().getFluxDissipation().modifyMult(ID, counterBonus);
		} else {
			ship.getMutableStats().getBallisticRoFMult().unmodify(ID);
			ship.getMutableStats().getMissileRoFMult().unmodify(ID);
			ship.getMutableStats().getEnergyRoFMult().unmodify(ID);
			ship.getMutableStats().getFluxDissipation().unmodify(ID);
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
				ship.getMutableStats().getTimeMult().modifyMult(ID, TIME_MULT);
				Global.getCombatEngine().getTimeMult().modifyMult(ID, 1f / TIME_MULT);
			} else {
				ship.getMutableStats().getTimeMult().modifyMult(ID, TIME_MULT);
				Global.getCombatEngine().getTimeMult().unmodify(ID);
			}

			ship.getMutableStats().getDynamic().getStat("VassAfterimageTracker").modifyFlat("VassAfterimageTrackerNullerID",-1);
			ship.getMutableStats().getDynamic().getStat("VassAfterimageTracker").modifyFlat("VassAfterimageTrackerID",ship.getMutableStats().getDynamic().getStat("VassAfterimageTracker").getModifiedValue()+amount);
			if (ship.getMutableStats().getDynamic().getStat("VassAfterimageTracker").getModifiedValue() > AFTERIMAGE_THRESHHOLD) {
				renderAfterimage(ship, familyMembership.get(ship));
				ship.getMutableStats().getDynamic().getStat("VassAfterimageTracker").modifyFlat("VassAfterimageTrackerID",ship.getMutableStats().getDynamic().getStat("VassAfterimageTracker").getModifiedValue()-AFTERIMAGE_THRESHHOLD);
			}
		} else {
			ship.getMutableStats().getTimeMult().unmodify(ID);
			if (ship == Global.getCombatEngine().getPlayerShip()) {
				Global.getCombatEngine().getTimeMult().unmodify(ID);
			}
		}
	}

	//Recipro : TODO
	private void advanceRecipro(ShipAPI ship, float amount) {
		//Only activate plating if allowed
		if (platingCanBeActive(ship)) {
			if (ship == Global.getCombatEngine().getPlayerShip()) {
				ship.getMutableStats().getTimeMult().modifyMult(ID, TIME_MULT);
				Global.getCombatEngine().getTimeMult().modifyMult(ID, 1f / TIME_MULT);
			} else {
				ship.getMutableStats().getTimeMult().modifyMult(ID, TIME_MULT);
				Global.getCombatEngine().getTimeMult().unmodify(ID);
			}

			ship.getMutableStats().getDynamic().getStat("VassAfterimageTracker").modifyFlat("VassAfterimageTrackerNullerID",-1);
			ship.getMutableStats().getDynamic().getStat("VassAfterimageTracker").modifyFlat("VassAfterimageTrackerID",ship.getMutableStats().getDynamic().getStat("VassAfterimageTracker").getModifiedValue()+amount);
			if (ship.getMutableStats().getDynamic().getStat("VassAfterimageTracker").getModifiedValue() > AFTERIMAGE_THRESHHOLD) {
				renderAfterimage(ship, familyMembership.get(ship));
				ship.getMutableStats().getDynamic().getStat("VassAfterimageTracker").modifyFlat("VassAfterimageTrackerID",ship.getMutableStats().getDynamic().getStat("VassAfterimageTracker").getModifiedValue()-AFTERIMAGE_THRESHHOLD);
			}
		} else {
			ship.getMutableStats().getTimeMult().unmodify(ID);
			if (ship == Global.getCombatEngine().getPlayerShip()) {
				Global.getCombatEngine().getTimeMult().unmodify(ID);
			}
		}
	}

	//Multa : Isochronal Reactor
	//Chance to ignore oveloads, flux bonuses at very low flux. Overloads longer when actually overloaded
	//TODO: Rework, maybe? Not entirely convinced of this one
	private void advanceMulta(ShipAPI ship, float amount) {
		//Overloads longer when overloaded, always applies
		ship.getMutableStats().getOverloadTimeMod().modifyPercent(ID, MULTA_OVERLOAD_TIME_INCREASE_PERCENT);

		//Only activate plating if allowed
		if (platingCanBeActive(ship)) {
			if (ship == Global.getCombatEngine().getPlayerShip()) {
				ship.getMutableStats().getTimeMult().modifyMult(ID, TIME_MULT);
				Global.getCombatEngine().getTimeMult().modifyMult(ID, 1f / TIME_MULT);
			} else {
				ship.getMutableStats().getTimeMult().modifyMult(ID, TIME_MULT);
				Global.getCombatEngine().getTimeMult().unmodify(ID);
			}

			ship.getMutableStats().getDynamic().getStat("VassAfterimageTracker").modifyFlat("VassAfterimageTrackerNullerID",-1);
			ship.getMutableStats().getDynamic().getStat("VassAfterimageTracker").modifyFlat("VassAfterimageTrackerID",ship.getMutableStats().getDynamic().getStat("VassAfterimageTracker").getModifiedValue()+amount);
			if (ship.getMutableStats().getDynamic().getStat("VassAfterimageTracker").getModifiedValue() > AFTERIMAGE_THRESHHOLD) {
				renderAfterimage(ship, familyMembership.get(ship));
				ship.getMutableStats().getDynamic().getStat("VassAfterimageTracker").modifyFlat("VassAfterimageTrackerID",ship.getMutableStats().getDynamic().getStat("VassAfterimageTracker").getModifiedValue()-AFTERIMAGE_THRESHHOLD);
			}

			//If we're at low flux, we get other bonuses
			if (ship.getFluxLevel() < MULTA_LOW_FLUX_AMOUNT) {
				ship.getMutableStats().getHardFluxDissipationFraction().modifyFlat(ID, MULTA_HARDFLUX_DISSIPATION*0.01f);
				ship.getMutableStats().getFluxDissipation().modifyPercent(ID, MULTA_DISSIPATION_BONUS);
			} else {
				ship.getMutableStats().getHardFluxDissipationFraction().unmodify(ID);
				ship.getMutableStats().getFluxDissipation().unmodify(ID);
			}
		} else {
			ship.getMutableStats().getTimeMult().unmodify(ID);
			if (ship == Global.getCombatEngine().getPlayerShip()) {
				Global.getCombatEngine().getTimeMult().unmodify(ID);
			}
			ship.getMutableStats().getHardFluxDissipationFraction().unmodify(ID);
			ship.getMutableStats().getFluxDissipation().unmodify(ID);
		}

		//Overload protection, out of necessity, needs to be outside normal plate activation
		MultaOverloadTracker tracker = null;
		Object obj = Global.getCombatEngine().getCustomData().get("VassMultaPeriodicPlatingOverloadTracker" + ship.getId());
		if (!(obj instanceof MultaOverloadTracker)) {
			tracker = new MultaOverloadTracker();
			Global.getCombatEngine().getCustomData().put("VassMultaPeriodicPlatingOverloadTracker" + ship.getId(), tracker);
		} else {
			tracker = (MultaOverloadTracker)obj;
		}

		if (ship.getFluxTracker().isOverloaded()) {
			tracker.overloadTime += amount;
			if (!tracker.hasTriggered && tracker.overloadTime >= 0.75f) {
				tracker.hasTriggered = true;
				//Only have a chance to avoid overload
				if (Math.random() <= MULTA_OVERLOAD_AVOID_CHANCE) {
					ship.getFluxTracker().stopOverload();
					//If we avoid overload, refund the last 5% of flux, so we don't insta-overload again or bug out
					if (ship.getFluxLevel() > 0.95f) {
						ship.getFluxTracker().decreaseFlux((ship.getFluxLevel()-0.95f)*ship.getFluxTracker().getMaxFlux());
					}
					//Also, play a sound! Sound cues are important. This needs to scale with the hullsize or it'll get nasty
					MULTA_OVERLOAD_AVOID_SFX.get(ship.getHullSize()).playSound(ship.getLocation(), Misc.ZERO);
					//ALSO also, render som effects! Visual cues are also important
					renderMultaMultiverseImages(ship);
				}
			}
		} else {
			tracker.hasTriggered = false;
			tracker.overloadTime = 0f;
		}
	}

	private static class MultaOverloadTracker {
		float overloadTime = 0f;
		boolean hasTriggered = false;
	}

	//Renders a bunch of "afterimages" due to some Multa nonsense
	private void renderMultaMultiverseImages(ShipAPI ship) {
		int amountToSpawn = MathUtils.getRandomNumberInRange(10, 18);
		for (int i = 0; i < amountToSpawn; i++) {
			// Sprite offset fuckery - Don't you love trigonometry?
			SpriteAPI sprite = ship.getSpriteAPI();
			float offsetX = sprite.getWidth()/2 - sprite.getCenterX();
			float offsetY = sprite.getHeight()/2 - sprite.getCenterY();

			float trueOffsetX = (float)FastTrig.cos(Math.toRadians(ship.getFacing()-90f))*offsetX - (float)FastTrig.sin(Math.toRadians(ship.getFacing()-90f))*offsetY;
			float trueOffsetY = (float)FastTrig.sin(Math.toRadians(ship.getFacing()-90f))*offsetX + (float)FastTrig.cos(Math.toRadians(ship.getFacing()-90f))*offsetY;

			Vector2f spotToSpawnOn = MathUtils.getRandomPointInCircle(new Vector2f(ship.getLocation().getX()+trueOffsetX,ship.getLocation().getY()+trueOffsetY), ship.getCollisionRadius()*1.5f);

			//Determines a layer to render on: fighters render above ships but below fighters, while everything else render below ships
			CombatEngineLayers layer = CombatEngineLayers.BELOW_SHIPS_LAYER;
			if (ship.getHullSize().equals(HullSize.FIGHTER)) {
				layer = CombatEngineLayers.CONTRAILS_LAYER;
			}

			//Gets a color for the afterimage
			Color colorToUse = VassUtils.getFamilyColor(VassUtils.VASS_FAMILY.MULTA, (float)Math.random()*0.4f);

			MagicRender.battlespace(
					Global.getSettings().getSprite(ship.getHullSpec().getSpriteName()),
					spotToSpawnOn,
					new Vector2f(0, 0),
					new Vector2f(ship.getSpriteAPI().getWidth(), ship.getSpriteAPI().getHeight()),
					new Vector2f(0, 0),
					ship.getFacing()-90f+MathUtils.getRandomNumberInRange(-15f, 15f),
					0f,
					colorToUse,
					true,
					0f,
					0f,
					0f,
					0f,
					0f,
					MathUtils.getRandomNumberInRange(0.01f, 0.25f),
					0f,
					MathUtils.getRandomNumberInRange(0.25f, 0.45f),
					layer);
		}
	}


	//Class for managing, and playing, a sound data
	private static class SoundData {
		float volume;
		float pitch;
		String sound;

		SoundData(String sound, float volume, float pitch) {
			this.sound = sound;
			this.volume = volume;
			this.pitch = pitch;
		}

		void playSound(Vector2f loc, Vector2f vel) {
			Global.getSoundPlayer().playSound(sound, pitch, volume, loc, vel);
		}
	}
}
