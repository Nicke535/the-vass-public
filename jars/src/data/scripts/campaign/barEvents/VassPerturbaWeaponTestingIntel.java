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
import com.fs.starfarer.api.util.WeightedRandomPicker;
import data.scripts.campaign.VassFamilyTrackerPlugin;
import data.scripts.utils.VassUtils;
import data.scripts.weapons.VassDyrnwynScript;
import data.scripts.weapons.VassRandomPrototypeChronoFlakScript;
import data.scripts.weapons.VassRandomPrototypeHyperDyrnwynScript;
import data.scripts.weapons.VassRandomPrototypeScript;
import data.scripts.weapons.VassRandomPrototypeScript.PrototypeWeaponData;
import org.lazywizard.lazylib.MathUtils;

import java.awt.*;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;


public class VassPerturbaWeaponTestingIntel extends BaseIntelPlugin {
    //Memory keys and reputation stats
    public static final String MEM_KEY_PROTOTYPE_DATA = "$vass_perturba_prototype_weapon_data_key";
    public static final String MEM_KEY_PROTOTYPE_WAS_IN_COMBAT = "$vass_perturba_prototype_weapon_was_in_combat_key";
    public static final String BUTTON_END_CONTRACT = "END_CONTRACT";
    public static final float CONTRACT_END_RELATIONS_PENALTY = -5f;

    //Stats for prototype weapon randomization
    public static final float RANDOM_LASER_ARCHETYPE_WEIGHT = 5f;
    public static final float RANDOM_CANNON_ARCHETYPE_WEIGHT = 5f;
    public static final float DYRNWYN_ARCHETYPE_WEIGHT = 3f;
    public static final float CHRONOFLAK_ARCHETYPE_WEIGHT = 2f;

    protected VassPerturbaWeaponTestingEvent event;
    protected WeaponTestingListener listener;
    protected HashSet<MarketAPI> marketsToClean;

    //Keeps track of completed battles
    public int battlesCompleted;

    //Generates random prototype weapon data for the quest
    private PrototypeWeaponData generateRandomPrototypeStats(boolean energy, boolean medium) {
        PrototypeWeaponData data = null;

        WeightedRandomPicker<String> archetypePicker = new WeightedRandomPicker<>();
        if (energy) {
            archetypePicker.add("RANDOM_LASER", RANDOM_LASER_ARCHETYPE_WEIGHT);
            archetypePicker.add("DYRNWYN", DYRNWYN_ARCHETYPE_WEIGHT);
        } else {
            archetypePicker.add("RANDOM_CANNON", RANDOM_CANNON_ARCHETYPE_WEIGHT);
        }
        archetypePicker.add("CHRONOFLAK", CHRONOFLAK_ARCHETYPE_WEIGHT);

        String archetype = archetypePicker.pick();
        if (archetype.equals("RANDOM_LASER")) {
            data = new PrototypeWeaponData(
                    MathUtils.getRandomNumberInRange(0.2f, 2f), //Damage
                    MathUtils.getRandomNumberInRange(0.1f, 1f), //Firerate
                    MathUtils.getRandomNumberInRange(0f, 12f), //Inaccuracy
                    MathUtils.getRandomNumberInRange(0f, 0.3f), //Speed variation
                    medium ? "pulselaser" : "irpulse", //Projectile weapon ID
                    Math.random() < 0.2f, //PD
                    null, //Guidance
                    null, //Script
                    medium ? "pulse_laser_fire" : "ir_pulse_laser_fire"); //Fire sound
        } else if (archetype.equals("RANDOM_CANNON")) {
            data = new PrototypeWeaponData(
                    MathUtils.getRandomNumberInRange(0.5f, 2f), //Damage
                    MathUtils.getRandomNumberInRange(0.2f, 1f), //Firerate
                    MathUtils.getRandomNumberInRange(0f, 12f), //Inaccuracy
                    MathUtils.getRandomNumberInRange(0f, 0.3f), //Speed variation
                    medium ? "heavyac" : "lightac", //Projectile weapon ID
                    Math.random() < 0.2f, //PD
                    null, //Guidance
                    null, //Script
                    medium ? "autocannon_fire" : "light_autocannon_fire"); //Fire sound
        } else if (archetype.equals("DYRNWYN")) {
            data = new PrototypeWeaponData(
                    MathUtils.getRandomNumberInRange(0.7f, 1.3f), //Damage
                    1f, //Firerate
                    medium ? MathUtils.getRandomNumberInRange(4f, 12f) : MathUtils.getRandomNumberInRange(0f, 6f), //Inaccuracy
                    medium ? MathUtils.getRandomNumberInRange(0f, 0.1f) : 0f, //Speed variation
                    "vass_dyrnwyn", //Projectile weapon ID
                    false, //PD
                    null, //Guidance
                    medium ? new VassRandomPrototypeHyperDyrnwynScript() : new VassDyrnwynScript(), //Script
                    "vass_dyrnwyn_fire"); //Fire sound
        } else if (archetype.equals("CHRONOFLAK")) {
            data = new PrototypeWeaponData(
                    medium ? MathUtils.getRandomNumberInRange(0.8f, 1.5f) : MathUtils.getRandomNumberInRange(0.3f, 0.6f), //Damage
                    medium ? MathUtils.getRandomNumberInRange(0.3f, 0.8f) : MathUtils.getRandomNumberInRange(0.6f, 1f), //Firerate
                    MathUtils.getRandomNumberInRange(2f, 8f), //Inaccuracy
                    MathUtils.getRandomNumberInRange(0f, 0.1f), //Speed variation
                    "flak", //Projectile weapon ID
                    true, //PD
                    Math.random() < 0.2f ? new VassRandomPrototypeChronoFlakScript.Guidance() : null, //Guidance
                    new VassRandomPrototypeChronoFlakScript(), //Script
                    "flak_fire"); //Fire sound
        }

        return data;
    }


    public VassPerturbaWeaponTestingIntel(VassPerturbaWeaponTestingEvent event) {
        this.event = event;
        listener = new WeaponTestingListener(this);
        battlesCompleted = 0;
        marketsToClean = new HashSet<>();

        WeaponSpecAPI baseSpec = Global.getSettings().getWeaponSpec(event.currentPrototypeWeaponID);
        Global.getSector().getMemoryWithoutUpdate().set(MEM_KEY_PROTOTYPE_DATA,
                generateRandomPrototypeStats(baseSpec.getType().equals(WeaponAPI.WeaponType.ENERGY), baseSpec.getSize().equals(WeaponAPI.WeaponSize.MEDIUM)));
    }

    @Override
    protected void notifyEnded() {
        super.notifyEnded();
        Global.getSector().removeScript(this);
        Global.getSector().getMemoryWithoutUpdate().unset(VassPerturbaWeaponTestingEvent.COLLATERAL_MEM_KEY);
        Global.getSector().getMemoryWithoutUpdate().unset(VassPerturbaWeaponTestingIntel.MEM_KEY_PROTOTYPE_DATA);
    }

    // Handles setting up the intel screen
    @Override
    public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
        Color c = getTitleColor(mode);
        info.setParaSmallInsignia();
        info.addPara(getName(), c, 0f);
        info.setParaFontDefault();

        bullet(info);
        info.addPara("Any battles you participate in with a ship equipped with a Perturba Prototype counts towards mission completion.", 3f);
        unindent(info);
    }

    // The description shown on the intel screen summary
    @Override
    public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
        Color h = Misc.getHighlightColor();
        Color g = Misc.getGrayColor();
        Color tc = Misc.getTextColor();
        float pad = 3f;
        float opad = 10f;

        if (battlesCompleted >= 3) {
            info.addPara("All weapon tests completed: return to " + event.getMarket(), opad);
        } else {
            info.addPara(battlesCompleted + "/3 weapon tests completed.", opad);
        }

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
        tags.add("Vass");
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
            if (battlesCompleted >= 3) {
                return;
            }

            //Only care about the player's battles
            if (battle.isPlayerInvolved()) {
                //Check if the prototype was in combat
                Object obj = Global.getSector().getMemoryWithoutUpdate().get(MEM_KEY_PROTOTYPE_WAS_IN_COMBAT);
                if (obj instanceof Boolean && (Boolean)obj) {
                    //We were in combat: register that to our list of completed battles and clear the memory key
                    battlesCompleted++;
                    Global.getSector().getMemoryWithoutUpdate().unset(MEM_KEY_PROTOTYPE_WAS_IN_COMBAT);
                    Global.getSector().getIntelManager().addIntel(this.intel, false);
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
            for (String id : VassPerturbaWeaponTestingEvent.TESTABLE_WEAPON_IDS.getItems()) {
                if (transaction.getBought().getNumWeapons(id) > 0 || transaction.getSold().getNumWeapons(id) > 0) {
                    marketsToClean.add(transaction.getMarket());
                    return;
                }
            }
            //Check if the transaction contained SHIP WITH a prototype
            for (PlayerMarketTransaction.ShipSaleInfo shipSaleInfo : transaction.getShipsSold()) {
                for (String id : VassPerturbaWeaponTestingEvent.TESTABLE_WEAPON_IDS.getItems()) {
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
}