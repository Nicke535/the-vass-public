package data.scripts.campaign.barEvents;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.scripts.campaign.VassFamilyTrackerPlugin;
import data.scripts.utils.VassUtils;
import data.scripts.weapons.VassRandomPrototypeScript.PrototypeWeaponData;

import java.awt.*;
import java.util.Set;

//TODO: make this work in the first place
public class VassPerturbaWeaponTestingIntel extends BaseIntelPlugin {
    public static final String MEM_KEY_PROTOTYPE_DATA = "$vass_perturba_prototype_weapon_data_key";
    public static final String MEM_KEY_PROTOTYPE_WAS_IN_COMBAT = "$vass_perturba_prototype_weapon_was_in_combat_key";
    public static final String BUTTON_END_CONTRACT = "END_CONTRACT";
    public static final float CONTRACT_END_RELATIONS_PENALTY = -5f;

    protected VassPerturbaWeaponTestingEvent event;
    protected WeaponTestingListener listener;

    private int battlesCompleted;


    public VassPerturbaWeaponTestingIntel(VassPerturbaWeaponTestingEvent event) {
        this.event = event;
        listener = new WeaponTestingListener();
        battlesCompleted = 0;

        //TODO: actually randomize this
        Global.getSector().getMemoryWithoutUpdate().set(MEM_KEY_PROTOTYPE_DATA,
                new PrototypeWeaponData(1f, 1f, 0f, 0f,
                        "vass_caladbolg", false, null, null));
    }

    @Override
    protected void notifyEnded() {
        super.notifyEnded();
        Global.getSector().removeScript(this);
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
        info.addPara(".", initPad);
        unindent(info);
    }

    // Handles setting up the intel screen
    @Override
    public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
        Color c = getTitleColor(mode);
        info.setParaSmallInsignia();
        info.addPara(getName(), c, 0f);
        info.setParaFontDefault();

        bullet(info);
        info.addPara("As long as the deal stands, you can order Perturba's weapon offerings similarly to how you would normally custom-order weapons, and your faction's fleets can utilize them as well.", 3f);
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

        info.addPara("You have an ongoing contract with Perturba, allowing you and your faction to use their services.", opad);

        addBulletPoints(info, ListInfoMode.IN_DESC);

        ButtonAPI button = info.addButton("End deal", BUTTON_END_CONTRACT,
                getFactionForUIColors().getBaseUIColor(), getFactionForUIColors().getDarkUIColor(),
                width, 20f, opad*3f);
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
        return IntelSortTier.TIER_3;
    }

    public String getSortString() {
        return "Perturba";
    }

    // what it's called, with a different name once completed
    public String getName() {
        return "A Profitable Partnership";
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
            prompt.addPara("You can break the contract, but Perturba will not be particularly happy with the situation" +
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

    private void endDeal() {
        VassFamilyTrackerPlugin.modifyRelationToFamily(VassUtils.VASS_FAMILY.PERTURBA, CONTRACT_END_RELATIONS_PENALTY);
        endImmediately();
    }

    //Listener for important events related to this intel
    private class WeaponTestingListener extends BaseCampaignEventListener {
        WeaponTestingListener() {
            super(true);
        }

        @Override
        public void reportBattleOccurred(CampaignFleetAPI primaryWinner, BattleAPI battle) {
            //Only care about the player's battles
            if (battle.isPlayerInvolved()) {
                //Check if the prototype was in combat
                Object obj = Global.getSector().getMemoryWithoutUpdate().get(MEM_KEY_PROTOTYPE_WAS_IN_COMBAT);
                if (obj instanceof Boolean && (Boolean)obj) {
                    //We were in combat: register that to our list of completed battles and clear the memory key
                    battlesCompleted++;
                    Global.getSector().getMemoryWithoutUpdate().unset(MEM_KEY_PROTOTYPE_WAS_IN_COMBAT);
                }
            }
        }

        @Override
        public void reportPlayerDumpedCargo(CargoAPI cargo) {
            cargo.removeWeapons("", cargo.getNumWeapons(""));//TODO: change to actually remove the right weapons
        }

        @Override
        public void reportPlayerMarketTransaction(PlayerMarketTransaction transaction) {
            //TODO: register the target market so it can be completely cleaned out once the event ends and self-destruct mechanisms trigger
        }
    }
}