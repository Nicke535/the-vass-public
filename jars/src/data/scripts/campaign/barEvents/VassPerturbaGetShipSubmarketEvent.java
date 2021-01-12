package data.scripts.campaign.barEvents;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.Industry;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BarEventManager;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import data.scripts.campaign.VassFamilyTrackerPlugin;
import data.scripts.utils.VassUtils;
import org.apache.log4j.Logger;

import java.awt.*;
import java.util.HashSet;
import java.util.Set;

/**
 * Creates an event where the player can hire a contract for Perturba weapons to their faction
 * @author Nicke535
 */
public class VassPerturbaGetShipSubmarketEvent extends VassPerturbaBaseEvent {
    public static final Logger LOGGER = Global.getLogger(VassPerturbaGetShipSubmarketEvent.class);

    public static final boolean DEBUG_MODE = false;

    //Some memory keys used by the event
    public static final String VASS_PERTURBA_SHIP_SUBMARKET_CONTRACT_KEY = "$vass_perturba_ship_contract";

    public static final String SUBMARKET_ID = "vass_perturba_shipseller_submarket";

    public enum OptionId {
        INIT,
        CONTINUE,
        CONTINUE_BUY_SERVICE,
        LEAVE_DIALOGUE,
        LEAVE,
    }

    private static final int PURCHASE_COST = 130000;

    private static final float PERTURBA_DEAL_RELATION_BONUS = 10f;

    public VassPerturbaGetShipSubmarketEvent() {
        super();
    }

    // Checks if the event is allowed to appear
    public boolean shouldShowAtMarket(MarketAPI market) {
        if (!super.shouldShowAtMarket(market)) return false;

        if (DEBUG_MODE) { return true; }

        //Don't appear if Perturba are enemies to the player
        if (VassFamilyTrackerPlugin.getRelationToFamily(VassUtils.VASS_FAMILY.PERTURBA) < 0f) {
            LOGGER.info("Threw away Perturba event due to bad relations with Perturba");
            return false;
        }
        if (!Global.getSector().getPlayerFaction().getRelationshipLevel("vass").isAtWorst(RepLevel.INHOSPITABLE)) {
            LOGGER.info("Threw away Perturba event due to bad relations with Vass");
            return false;
        }

        //Don't appear if the player has already setup this ship submarket
        Object hasContract = Global.getSector().getMemoryWithoutUpdate().get(VASS_PERTURBA_SHIP_SUBMARKET_CONTRACT_KEY);
        if (hasContract instanceof Boolean) {
            if ((Boolean) hasContract) {
                LOGGER.info("Threw away Perturba event due to prior contract");
                return false;
            }
        }

        //We also have to be registered as the next non-repeatable encounter to trigger
        if (!("get_ship_submarket".equals(Global.getSector().getMemoryWithoutUpdate().get(VassPerturbaBaseEvent.CURRENT_EVENT_ALLOWED_KEY)))) {
            LOGGER.info("Threw away Perturba event due to not having the correct event flag set");
            return false;
        }

        return true;
    }

    // Add the event to the intel screen; doesn't do much for now, but it's good to keep it there for future reference
    private void addIntel() {
        TextPanelAPI text = dialog.getTextPanel();
        VassPerturbaGetShipSubmarketIntel intel = new VassPerturbaGetShipSubmarketIntel(this);
        Global.getSector().getIntelManager().addIntel(intel, false, text);
    }

    // Creates the actual prompt and description when entering the bar. Picks randomly from a list, with some variations based on circumstance
    @Override
    public void addPromptAndOption(InteractionDialogAPI dialog) {
        super.addPromptAndOption(dialog);

        regen(dialog.getInteractionTarget().getMarket());

        TextPanelAPI text = dialog.getTextPanel();
        text.addPara(pickDescription());

        Color c = Misc.getBasePlayerColor();
        //Color c = Misc.getHighlightColor();

        dialog.getOptionPanel().addOption(pickPrompt(), this,
                c, null);
    }

    @Override
    public void init(InteractionDialogAPI dialog) {
        super.init(dialog);

        done = false;

        dialog.getVisualPanel().showPersonInfo(person, true);

        optionSelected(null, OptionId.INIT);
    }

    // Handles all dialogue alternatives and outcomes
    @Override
    public void optionSelected(String optionText, Object optionData) {
        if (!(optionData instanceof OptionId)) {
            return;
        }
        OptionId option = (OptionId) optionData;

        Color t = Misc.getTextColor();
        Color h = Misc.getHighlightColor();
        Color n = Misc.getNegativeHighlightColor();
        float pad = 3f;

        OptionPanelAPI options = dialog.getOptionPanel();
        TextPanelAPI text = dialog.getTextPanel();
        options.clearOptions();

        handleOptionSelected(text, option, options, t, h, n);
    }

    //For followup encounters
    private void handleOptionSelected(TextPanelAPI text, OptionId option, OptionPanelAPI options, Color t, Color h, Color n) {
        switch (option) {
            case INIT:
                text.addPara("The Perturba agent smiles as you approach them, though the other individual att the table doesn't seem to react.");
                text.addPara("'"+Global.getSector().getPlayerPerson().getName().getFirst()+"! Have you been well? Ah, them?' Perturba's agent points to the other individual at the table. 'They're the reason I'm here today.'");

                options.addOption("Interesting. Grab a drink and tell them to elaborate.", OptionId.CONTINUE);
                options.addOption("Tell them you're unfortunately busy at the moment.", OptionId.LEAVE);
                break;
            case CONTINUE:
                text.addPara("'Well, without going too much into detail, this here is our contact from the Vass Shipyards. We've discussed things among us for a bit, and we've concluded that there is a way for us to supply you with Vass ships without causing a breach in their cartel agreement.'");
                text.addPara("That does sound enticing. But what's the catch?");
                text.addPara("'Well, due to how the contract is written, we can't really supply you with factory-new models. Instead, we will sell you some of the ships normally deemed as defect or otherwise unfit for use. The plan is to set up a small Perturba reseller on "+market.getName()+" which would manage selling these to you. There won't be that many for sale at a time, but it's better than nothing, no?'");
                text.addPara("'Of course, we would need some financial compensation for setting up the base: military-grade ship storage and stealth facilities don't exactly grow on trees. After discounting our own investments, it should take approximately "+PURCHASE_COST+" credits.'", h, "normal administrative functions");
                options.addOption("You have yourself a deal.", OptionId.CONTINUE_BUY_SERVICE);
                if (Global.getSector().getPlayerFleet().getCargo().getCredits().get() < PURCHASE_COST) {
                    options.setEnabled(OptionId.CONTINUE_BUY_SERVICE, false);
                    options.setTooltip(OptionId.CONTINUE_BUY_SERVICE, "You don't have enough credits.");
                }
                options.addOption("Inform the agent you're not interested in procuring Vass ships at the moment.", OptionId.LEAVE_DIALOGUE);
                break;
            case CONTINUE_BUY_SERVICE:
                text.addPara(getContact()+" looks overjoyed, though the Vass representative seems to keep the same, disinterested look.");
                text.addPara("'Ah, perfect! I'll start with the preparations immediately: truth be told, we already set up most of the necessary infrastructure beforehand, but it might still take a month or two to get everything running smoothly.'");

                text.setFontSmallInsignia();
                text.addPara("Lost " + PURCHASE_COST + " credits", n, h, "" + PURCHASE_COST);
                Global.getSector().getPlayerFleet().getCargo().getCredits().add(-1 * PURCHASE_COST);

                float currentPerturbaRelations = VassFamilyTrackerPlugin.getRelationToFamily(VassUtils.VASS_FAMILY.PERTURBA);
                if (currentPerturbaRelations < 100f) {
                    float toModify = Math.min(PERTURBA_DEAL_RELATION_BONUS, 100f-currentPerturbaRelations);
                    VassFamilyTrackerPlugin.modifyRelationToFamily(VassUtils.VASS_FAMILY.PERTURBA, toModify);
                    text.addPara("Relations with Perturba improved to "+Math.round(currentPerturbaRelations+toModify), h, "Perturba", ""+Math.round(currentPerturbaRelations+toModify));
                }
                //This shouldn't run, but I might as well add it as a fallback
                if (!Global.getSector().getPlayerFaction().getRelationshipLevel("vass").isAtWorst(RepLevel.FRIENDLY)) {
                    text.addPara("Relations with the Vass Families improved to Friendly", h, "Vass Families", "Friendly");
                    Global.getSector().getPlayerFaction().setRelationship("vass", RepLevel.FRIENDLY);
                }
                text.setFontInsignia();

                text.addPara("'Perturba and I both hope our relationship will remain as profitable as it has been in the past!'");

                market.addSubmarket(SUBMARKET_ID);
                BarEventManager.getInstance().notifyWasInteractedWith(this);
                Global.getSector().getMemoryWithoutUpdate().set(VASS_PERTURBA_SHIP_SUBMARKET_CONTRACT_KEY, true);

                addIntel();
                options.addOption("Leave", OptionId.LEAVE);
                break;
            case LEAVE_DIALOGUE:
                text.addPara("The Perturba agent looks disappointed. 'Is that so? Well, I'll try to check in with you later then; our offer still stands.'");

                options.addOption("Leave", OptionId.LEAVE);
                break;
            case LEAVE:
                Global.getSector().addScript(new ResetPerturbaEventFlagWithDelay());
                noContinue = true;
                done = true;
                break;
        }
    }

    //The description is slightly different if you're at high relations with Perturba
    private String pickDescription() {
        WeightedRandomPicker<String> post = new WeightedRandomPicker<String>();
        if (getContact() != null && VassFamilyTrackerPlugin.getRelationToFamily(VassUtils.VASS_FAMILY.PERTURBA) >= 60) {
            post.add(getContact().getName().getFirst() + " is here again, sitting next to someone you don't know. Maybe Perturba has something slightly out of the ordinary on offer?");
        }
        post.add("The Perturba contact is sitting at one of the tables, but they seem to have brought company. When you enter, they try to catch your attention; Perturba most likely has an offer for you.");
        return post.pick();
    }

    private String pickPrompt() {
        WeightedRandomPicker<String> post = new WeightedRandomPicker<String>();
        if (getContact() != null && VassFamilyTrackerPlugin.getRelationToFamily(VassUtils.VASS_FAMILY.PERTURBA) >= 60) {
            post.add("Huh, " + getContact().getName().getFirst() + " has brought an acquaintance? Can't hurt to hear what they want.");
        }
        post.add("It's rare for Perturba to send multiple contacts; see what they want with you.");
        return post.pick();
    }

    private class ResetPerturbaEventFlagWithDelay implements EveryFrameScript {
        private boolean hasRun = false;
        private float timer = 0f;

        @Override
        public void advance(float amount) {
            timer += Misc.getDays(amount);
            if (timer > 2f) {
                VassPerturbaBaseEvent.checkAndSetAllowedEvent();
                hasRun = true;
            }
        }

        @Override
        public boolean runWhilePaused() {
            return false;
        }

        @Override
        public boolean isDone() {
            return hasRun;
        }
    }
}
