package data.scripts.campaign;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.BaseCampaignEventListener;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.PlayerMarketTransaction;
import com.fs.starfarer.api.campaign.PlayerMarketTransaction.ShipSaleInfo;
import com.fs.starfarer.api.impl.campaign.ids.Submarkets;
import com.fs.starfarer.api.loading.FighterWingSpecAPI;
import com.fs.starfarer.api.util.Misc;

public class VassPlayerSoldVassShipsListener extends BaseCampaignEventListener implements EveryFrameScript {
    public boolean hasSoldMinor = false;
    public boolean hasSoldMajor = false;

    private static final float MINOR_SELL_TIMEOUT_DAYS = 50;
    private static final int MINOR_SELLS_PER_MAJOR = 5;
    private int minorSellingsDone = 0;
    private float minorSellingTimer = 0f;

    public VassPlayerSoldVassShipsListener(boolean permaRegister) {
        super(permaRegister);
        Global.getSector().addScript(this);
    }

    @Override
    public void reportPlayerMarketTransaction(PlayerMarketTransaction transaction) {
        //Ignore transactions to storage
        if (transaction.getSubmarket().getSpec().getId().equals(Submarkets.SUBMARKET_STORAGE)) {
            return;
        }

        //Also ignores transaction to any Vass-owned markets; there probably won't be a lot of them, but they are
        //one of the only places selling Vass ships is allowed
        if (transaction.getSubmarket().getFaction().getId().startsWith("vass")) {
            return;
        }

        //Check if the transaction involved ships or fighters
        if (transaction.getShipsSold().size() > 0 || transaction.getSold().getFighters().size() > 0) {
            //Check all sold ships for Vass ID or Vass fighters
            for (ShipSaleInfo info: transaction.getShipsSold()) {
                if (info.getMember().getHullId().startsWith("vass_")) {
                    registerSelling(info);
                }
                for (String wing : info.getMember().getVariant().getWings()) {
                    if (wing.startsWith("vass_")) {
                        registerSelling(wing);
                    }
                }
            }

            //Check if any "loose" fighters were sold
            for (CargoAPI.CargoItemQuantity<String> fighter : transaction.getSold().getFighters()) {
                if (fighter.getCount() > 0 && fighter.getItem().startsWith("vass_")) {
                    for (int i = 0; i < fighter.getCount(); i++) {
                        registerSelling(fighter.getItem());
                    }
                }
            }
        }
    }

    //Registers a sell, either as a minor or major one depending on price
    private void registerSelling(String fighterWing) {
        FighterWingSpecAPI spec = Global.getSettings().getFighterWingSpec(fighterWing);
        if (spec.getBaseValue() > 60000) {
            hasSoldMajor = true;
            hasSoldMinor = true;
        } else {
            hasSoldMinor = true;
            minorSellingsDone++;
            if (minorSellingsDone > MINOR_SELLS_PER_MAJOR) {
                hasSoldMajor = true;
            }
        }
    }
    private void registerSelling(ShipSaleInfo info) {
        if (info.getPrice() > 60000) {
            hasSoldMajor = true;
            hasSoldMinor = true;
        } else {
            hasSoldMinor = true;
            minorSellingsDone++;
            minorSellingTimer = 0f;
            if (minorSellingsDone > MINOR_SELLS_PER_MAJOR) {
                hasSoldMajor = true;
            }
        }
    }

    // Ticks down our timer, so that far-apart minor resellings don't add up to major ones
    @Override
    public void advance(float amount) {
        minorSellingTimer += Misc.getDays(amount);
        if (minorSellingTimer >= MINOR_SELL_TIMEOUT_DAYS) {
            minorSellingTimer = 0f;
            minorSellingsDone = 0;
        }
    }

    /**
     * Resets the progress of the listener
     */
    public void reset() {
        hasSoldMinor = false;
        hasSoldMajor = false;
    }

    @Override
    public boolean isDone() {
        return false;
    }

    @Override
    public boolean runWhilePaused() {
        return false;
    }
}
