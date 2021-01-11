package data.scripts.campaign.barEvents;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.econ.SubmarketAPI;
import com.fs.starfarer.api.combat.ShipVariantAPI;
import com.fs.starfarer.api.combat.WeaponAPI;
import com.fs.starfarer.api.fleet.FleetMemberAPI;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.loading.VariantSource;
import com.fs.starfarer.api.loading.WeaponSpecAPI;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.scripts.campaign.VassFamilyTrackerPlugin;
import data.scripts.utils.VassPerturbaRandomPrototypeManager;
import data.scripts.utils.VassUtils;
import org.apache.log4j.Logger;

import java.awt.*;
import java.util.*;


public class VassPerturbaWeaponTestingIntel extends BaseIntelPlugin {
    public static final Logger LOGGER = Global.getLogger(VassPerturbaWeaponTestingIntel.class);
    public static final boolean DEBUG_MODE = true; //TODO: turn off before 1.0; leave for now

    //Memory keys and reputation stats
    public static final String MEM_KEY_PROTOTYPE_DATA = "$vass_perturba_prototype_weapon_data_key";
    public static final String MEM_KEY_PROTOTYPE_IN_COMBAT_SCORE_HANDLER = "$vass_perturba_prototype_weapon_was_in_combat_key";
    public static final String BUTTON_END_CONTRACT = "END_CONTRACT";
    public static final float CONTRACT_END_RELATIONS_PENALTY = -5f;

    protected VassPerturbaWeaponTestingEvent event;
    protected WeaponTestingListener listener;
    protected HashSet<MarketAPI> marketsToClean;

    //Keeps track of completed battles
    public float questFinishProgress;


    public VassPerturbaWeaponTestingIntel(VassPerturbaWeaponTestingEvent event) {
        this.event = event;
        listener = new WeaponTestingListener(this);
        questFinishProgress = 0f;
        marketsToClean = new HashSet<>();

        //Generates a random weapon and puts it in memory
        WeaponSpecAPI baseSpec = Global.getSettings().getWeaponSpec(event.currentPrototypeWeaponID);
        Global.getSector().getMemoryWithoutUpdate().set(MEM_KEY_PROTOTYPE_DATA,
                VassPerturbaRandomPrototypeManager.generateRandomPrototypeStats(baseSpec.getType().equals(WeaponAPI.WeaponType.ENERGY),
                                                                                baseSpec.getSize().equals(WeaponAPI.WeaponSize.MEDIUM)));
    }

    @Override
    protected void notifyEnded() {
        super.notifyEnded();
        Global.getSector().removeScript(this);
        Global.getSector().getMemory().unset(VassPerturbaWeaponTestingEvent.COLLATERAL_MEM_KEY);
        Global.getSector().getMemory().unset(VassPerturbaWeaponTestingIntel.MEM_KEY_PROTOTYPE_DATA);
        Global.getSector().getMemory().unset(MEM_KEY_PROTOTYPE_IN_COMBAT_SCORE_HANDLER);
    }

    //Handles the bullet-points on the intel screen
    protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode) {
        Color h = Misc.getHighlightColor();
        Color g = Misc.getGrayColor();
        float pad = 3f;
        float opad = 10f;

        float initPad = pad;
        if (mode == ListInfoMode.IN_DESC) initPad = opad;

        bullet(info);
        if (questFinishProgress >= 100f) {
            info.addPara("Weapon testing completed: return to " + event.getMarket().getName(), pad);
        } else {
            info.addPara((int)Math.floor(questFinishProgress) + "% of required weapon test data collected.", pad);
        }
        unindent(info);
    }

    // Summary info
    @Override
    public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
        Color c = getTitleColor(mode);
        info.setParaSmallInsignia();
        info.addPara(getName(), c, 0f);
        info.setParaFontDefault();

        addBulletPoints(info, mode);
    }

    // The description shown on the intel screen
    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        Color h = Misc.getHighlightColor();
        Color g = Misc.getGrayColor();
        Color tc = Misc.getTextColor();
        float pad = 3f;
        float opad = 10f;

        info.addPara("Any battles you participate in with a ship equipped with a Perturba Prototype counts towards mission completion.", opad);
        info.addPara("Data collected varies, but is significantly improved by using the weapon in live combat situations and stressing conditions.", opad);

        addBulletPoints(info, ListInfoMode.IN_DESC);

        ButtonAPI button = info.addButton("End mission", BUTTON_END_CONTRACT,
                getFactionForUIColors().getBaseUIColor(), getFactionForUIColors().getDarkUIColor(),
                width, 20f, opad*3f);
    }

    public MarketAPI getMarket() {
        return event.getMarket();
    }

    @Override
    public SectorEntityToken getMapLocation(SectorMapAPI map) {
        return event.getMarket().getPrimaryEntity();
    }

    //Gets the icon to display in the intel screen
    @Override
    public String getIcon() {
        return Global.getSettings().getSpriteName("intel", "vass_perturba_deal");
    }

    //Tags in the intel screen
    @Override
    public Set<String> getIntelTags(SectorMapAPI map) {
        Set<String> tags = super.getIntelTags(map);
        tags.add("vass");
        tags.add("Perturba");
        return tags;
    }

    @Override
    public IntelSortTier getSortTier() {
        return IntelSortTier.TIER_4;
    }

    public String getSortString() {
        return "Perturba";
    }

    // what it's called, with a different name once completed
    public String getName() {
        return "Weapon Prototype Testing";
    }

    @Override
    public FactionAPI getFactionForUIColors() {
        return Global.getSector().getFaction("vass_perturba");
    }

    public String getSmallDescriptionTitle() {
        return getName();
    }

    @Override
    public boolean shouldRemoveIntel() {
        return super.shouldRemoveIntel();
    }

    //The noise to play when a new message shows up
    @Override
    public String getCommMessageSound() {
        return getSoundMinorMessage();
    }

    //Handles all button interactions in the intel
    @Override
    public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
        if (buttonId == BUTTON_END_CONTRACT) {
            endDeal();
            ui.recreateIntelUI();
        }
        super.buttonPressConfirmed(buttonId, ui);
    }


    @Override
    public void createConfirmationPrompt(Object buttonId, TooltipMakerAPI prompt) {
        FactionAPI faction = getFactionForUIColors();
        if (buttonId == BUTTON_END_CONTRACT) {
            prompt.addPara("You can break the contract, but Perturba will not be particularly happy with the situation " +
                            "and the collateral you deposited will, of course, not be returned.", 0f,
                    Misc.getTextColor(), faction.getBaseUIColor(), faction.getDisplayNameWithArticleWithoutArticle());
        }
    }

    @Override
    public boolean doesButtonHaveConfirmDialog(Object buttonId) {
        if (buttonId == BUTTON_END_CONTRACT) {
            return true;
        }
        return true;
    }

    //Self-destruct all prototypes, and modify relations
    private void endDeal() {
        VassFamilyTrackerPlugin.modifyRelationToFamily(VassUtils.VASS_FAMILY.PERTURBA, CONTRACT_END_RELATIONS_PENALTY);

        removeAllPrototypes();

        //Unlocks so new Perturba events may spawn again
        Global.getSector().getMemoryWithoutUpdate().set(VassPerturbaBaseEvent.PERTURBA_EVENTS_BLOCKED_KEY, false);

        endImmediately();
    }

    //Removes all prototypes that needs removing
    public void removeAllPrototypes() {
        LinkedList<FleetMemberAPI> fullMemberList = new LinkedList<>(Global.getSector().getPlayerFleet().getFleetData().getMembersListCopy());

        //Weapons - loose
        for (MarketAPI market : marketsToClean) {
            for (SubmarketAPI submarket : market.getSubmarketsCopy()) {
                submarket.getCargo().removeWeapons(event.currentPrototypeWeaponID, submarket.getCargo().getNumWeapons(event.currentPrototypeWeaponID));
                fullMemberList.addAll(submarket.getCargo().getMothballedShips().getMembersListCopy());
            }
        }

        //Ships WITH weapons
        for (FleetMemberAPI member : fullMemberList) {
            LinkedList<String> slotsToClear = new LinkedList<>();
            for (String slot : member.getVariant().getFittedWeaponSlots()) {
                if (member.getVariant().getWeaponId(slot).equals(event.currentPrototypeWeaponID)) {
                    slotsToClear.add(slot);
                }
            }
            if (!slotsToClear.isEmpty()) {
                ShipVariantAPI newVariant = member.getVariant().clone();
                for (String slot : slotsToClear) {
                    newVariant.clearSlot(slot);
                }
                newVariant.setSource(VariantSource.REFIT);
                member.setVariant(newVariant, false, true);
            }
        }

        //Weapons - loose, but in player fleet
        Global.getSector().getPlayerFleet().getCargo().removeWeapons(event.currentPrototypeWeaponID, Global.getSector().getPlayerFleet().getCargo().getNumWeapons(event.currentPrototypeWeaponID));
    }

    //Listener for important events related to this intel
    private class WeaponTestingListener extends BaseCampaignEventListener {
        VassPerturbaWeaponTestingIntel intel;
        WeaponTestingListener(VassPerturbaWeaponTestingIntel intel) {
            super(true);
            this.intel = intel;
        }

        @Override
        public void reportBattleOccurred(CampaignFleetAPI primaryWinner, BattleAPI battle) {
            //If we have completed enough battles already, don't do anything
            if (questFinishProgress >= 100f) {
                return;
            }

            //Only care about the player's battles
            if (battle.isPlayerInvolved()) {
                //Check if the prototype was in combat
                Object obj = Global.getSector().getMemoryWithoutUpdate().get(MEM_KEY_PROTOTYPE_IN_COMBAT_SCORE_HANDLER);
                if (obj instanceof TestingCombatScoreHandler) {
                    //We were in combat: register our quest progression and clear the memory key
                    questFinishProgress += ((TestingCombatScoreHandler) obj).scoreThisCombat;
                    Global.getSector().getMemoryWithoutUpdate().unset(MEM_KEY_PROTOTYPE_IN_COMBAT_SCORE_HANDLER);
                    Global.getSector().getIntelManager().addIntel(this.intel, false);
                    if (DEBUG_MODE) {
                        LOGGER.info("Progressed weapon testing quest by " + ((TestingCombatScoreHandler) obj).scoreThisCombat);
                    }
                }
            }
        }

        //When the player dumps cargo, the self-destruct switches are triggered (because we can't track them script-side anymore)
        @Override
        public void reportPlayerDumpedCargo(CargoAPI cargo) {
            cargo.removeWeapons(event.currentPrototypeWeaponID, cargo.getNumWeapons(event.currentPrototypeWeaponID));
        }

        //Once the weapons are sold (or transferred to cargo) we need to keep track of that market so no rogue weapons can appear there
        @Override
        public void reportPlayerMarketTransaction(PlayerMarketTransaction transaction) {
            //Check if the transaction contained a prototype
            for (String id : VassPerturbaRandomPrototypeManager.PROTOTYPE_WEAPON_IDS.getItems()) {
                if (transaction.getBought().getNumWeapons(id) > 0 || transaction.getSold().getNumWeapons(id) > 0) {
                    marketsToClean.add(transaction.getMarket());
                    return;
                }
            }
            //Check if the transaction contained SHIP WITH a prototype
            for (PlayerMarketTransaction.ShipSaleInfo shipSaleInfo : transaction.getShipsSold()) {
                for (String id : VassPerturbaRandomPrototypeManager.PROTOTYPE_WEAPON_IDS.getItems()) {
                    for (String slot : shipSaleInfo.getMember().getVariant().getFittedWeaponSlots()) {
                        if (shipSaleInfo.getMember().getVariant().getWeaponId(slot).equals(id)) {
                            marketsToClean.add(transaction.getMarket());
                            return;
                        }
                    }
                }
            }
        }
    }

    public static class TestingCombatScoreHandler {
        float scoreThisCombat;
        boolean hasGottenFromDisabling;
        boolean hasGottenFromOverload;
        float remainingScorePossibleForCombatTime;
        float remainingScorePossibleForLowCR;
        Set<WeaponAPI> weapons = new HashSet<>(); //Only one weapon counts at a time

        public TestingCombatScoreHandler() {
            scoreThisCombat = 15f;
            hasGottenFromDisabling = false;
            hasGottenFromOverload = false;
            remainingScorePossibleForCombatTime = 25f;
            remainingScorePossibleForLowCR = 10f;
        }

        public void handleScoreThisFrame(WeaponAPI weapon, float amount) {
            //Add the weapon to our list (it's a set, so no duplicates happen)
            weapons.add(weapon);

            if (weapon.isDisabled() && !hasGottenFromDisabling) {
                scoreThisCombat += 7f;
                hasGottenFromDisabling = true;
            }
            if (weapon.getShip().getFluxTracker().isOverloaded() && !hasGottenFromOverload) {
                scoreThisCombat += 7f;
                hasGottenFromOverload = true;
            }

            if (weapon.getShip().areAnyEnemiesInRange()) {
                float scoreToAdd = Math.min(remainingScorePossibleForCombatTime, amount/(8f*weapons.size()));
                remainingScorePossibleForCombatTime -= scoreToAdd;
                scoreThisCombat += scoreToAdd;
            }

            if (weapon.getShip().getCurrentCR() < 40f) {
                float scoreToAdd = Math.min(remainingScorePossibleForLowCR, amount/(3f*weapons.size()));
                remainingScorePossibleForLowCR -= scoreToAdd;
                scoreThisCombat += scoreToAdd;
            }
        }
    }
}