package data.scripts.campaign.barEvents;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

import java.awt.*;
import java.util.Set;

import static data.scripts.campaign.barEvents.VassPerturbaGetShipSubmarketEvent.SUBMARKET_ID;
import static data.scripts.campaign.barEvents.VassPerturbaGetShipSubmarketEvent.VASS_PERTURBA_SHIP_SUBMARKET_CONTRACT_KEY;

public class VassPerturbaGetShipSubmarketIntel extends BaseIntelPlugin {
    protected VassPerturbaGetShipSubmarketEvent event;

    public VassPerturbaGetShipSubmarketIntel(VassPerturbaGetShipSubmarketEvent event) {
        this.event = event;
    }

    @Override
    public void advance(float amount) {
        super.advance(amount);

        //If, for some reason, we no longer have our contract, remove the submarket and delete ourselves
        Object read = Global.getSector().getMemoryWithoutUpdate().get(VASS_PERTURBA_SHIP_SUBMARKET_CONTRACT_KEY);
        if (!(read instanceof Boolean) || !(boolean) read) {
            event.getMarket().removeSubmarket(SUBMARKET_ID);
            endImmediately();
        }
    }

    @Override
    protected void notifyEnded() {
        super.notifyEnded();
        Global.getSector().removeScript(this);
    }

    @Override
    public SectorEntityToken getMapLocation(SectorMapAPI map) {
        return this.event.getMarket().getPrimaryEntity();
    }

    @Override
    public void endAfterDelay() {
        super.endAfterDelay();
    }

    @Override
    protected void notifyEnding() {
        super.notifyEnding();
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
        info.addPara("A minor Perturba base has been set up " + event.getMarket().getOnOrAt() + " " + event.getMarket().getName() + ", allowing you to purchase Vass ships there.", opad);
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

        info.addPara("Perturba's submarket will sell you small amounts of worn-out Vass ships. While you're free to repair them, remember that reselling them to a third party can draw their ire unless your relations are good enough.", pad);

        addBulletPoints(info, ListInfoMode.IN_DESC);
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
        return IntelSortTier.TIER_3;
    }

    public String getSortString() {
        return "Perturba";
    }

    // what it's called, with a different name once completed
    public String getName() {
        return "Vass Shipyard Reseller";
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
}