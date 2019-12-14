package data.scripts.campaign.barEvents;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.*;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.BaseMissionIntel;
import com.fs.starfarer.api.ui.ButtonAPI;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;
import data.scripts.campaign.VassFamilyTrackerPlugin;
import data.scripts.utils.VassUtils;

import java.awt.*;
import java.util.Set;

public class VassPerturbaWeaponContractIntel extends BaseIntelPlugin {
    public static final String BUTTON_END_DEAL = "END_DEAL";
    public static final float DEAL_END_RELATIONS_PENALTY = -20f;

    protected VassPerturbaWeaponContractEvent event;

    public VassPerturbaWeaponContractIntel(VassPerturbaWeaponContractEvent event) {
        this.event = event;
    }

    @Override
    protected void notifyEnded() {
        super.notifyEnded();
        Global.getSector().removeScript(this);
    }

    @Override
    public void endAfterDelay() {
        super.endAfterDelay();
    }

    @Override
    protected void notifyEnding() {
        super.notifyEnding();
    }

    //Handles the bullet-points on the intel screen, when details are viewed
    protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode) {
        Color h = Misc.getHighlightColor();
        Color g = Misc.getGrayColor();
        float pad = 3f;
        float opad = 10f;

        float initPad = pad;
        if (mode == ListInfoMode.IN_DESC) initPad = opad;

        bullet(info);

        info.addPara("As long as the deal stands, you can order Perturba's weapon offerings similarly to how you would normally custom-order weapons, and your faction's fleets can utilize them as well.", initPad);

        unindent(info);
    }

    // Handles setting up the intel screen
    @Override
    public void createIntelInfo(TooltipMakerAPI info, ListInfoMode mode) {
        Color c = getTitleColor(mode);
        info.setParaSmallInsignia();
        info.addPara(getName(), c, 0f);
        info.setParaFontDefault();
        addBulletPoints(info, mode);
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

        ButtonAPI button = info.addButton("End deal", BUTTON_END_DEAL,
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
        return getSoundMajorPosting();
    }

    //Handles all button interactions in the intel
    @Override
    public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
        if (buttonId == BUTTON_END_DEAL) {
            endDeal();
            ui.recreateIntelUI();
        }
        super.buttonPressConfirmed(buttonId, ui);
    }


    @Override
    public void createConfirmationPrompt(Object buttonId, TooltipMakerAPI prompt) {
        FactionAPI faction = getFactionForUIColors();
        if (buttonId == BUTTON_END_DEAL) {
            prompt.addPara("Breaking the deal with Perturba might be useful if you need to keep your hands clean, " +
                            "but they are sure to dislike ending such a profitable affair.", 0f,
                    Misc.getTextColor(), faction.getBaseUIColor(), faction.getDisplayNameWithArticleWithoutArticle());
        }
    }

    @Override
    public boolean doesButtonHaveConfirmDialog(Object buttonId) {
        if (buttonId == BUTTON_END_DEAL) {
            return true;
        }
        return true;
    }

    private void endDeal() {
        for (String weapon : VassPerturbaWeaponContractEvent.UNLOCKED_WEAPONS) {
            Global.getSector().getPlayerFaction().removeKnownWeapon(weapon);
        }
        VassFamilyTrackerPlugin.modifyRelationToFamily(VassUtils.VASS_FAMILY.PERTURBA, DEAL_END_RELATIONS_PENALTY);
        endImmediately();
    }
}