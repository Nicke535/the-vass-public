package data.scripts.campaign.barEvents;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BarEventManager;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import data.scripts.campaign.VassFamilyTrackerPlugin;
import data.scripts.utils.VassPerturbaRandomPrototypeManager;
import data.scripts.utils.VassUtils;
import org.apache.log4j.Logger;
import org.lazywizard.lazylib.MathUtils;

import java.awt.*;

/**
 * Creates repeatable event where Perturba asks the player to test some weapon prototypes for them
 * @author Nicke535
 */
public class VassPerturbaWeaponTestingEvent extends VassPerturbaBaseEvent {
    public static final Logger LOGGER = Global.getLogger(VassPerturbaWeaponTestingEvent.class);

    public static final boolean DEBUG_MODE = false;

    public enum OptionId {
        INIT,
        CONTINUE_1,
        CONTINUE_2,
        LEAVE_DIALOG,
        LEAVE,
    }

    public static final String COLLATERAL_MEM_KEY = "$vass_perturba_current_collateral_paid";
    private static final float COLLATERAL_PERCENTAGE = 0.2f;
    private static final int COLLATERAL_MAX = 100000;
    private static final int COLLATERAL_MIN = 2000;
    public static final float REWARD_PERCENTAGE_MAX = 1.8f;
    public static final float REWARD_PERCENTAGE_MIN = 1.2f;
    private static final float RELATIONS_NEEDED = 20f;
    public static final float RELATIONS_BOOST_VASS = 0.09f;
    public static final float RELATIONS_BOOST_PERTURBA = 8f;
    public static final float RELATIONS_MAX_PERTURBA = 65f;
    public static final float FAMILY_POWER_BOOST_PERTURBA = 1f;

    public String currentPrototypeWeaponID = "";

    public VassPerturbaWeaponTestingEvent() {
        super();
    }

    // Checks if the event is allowed to appear
    public boolean shouldShowAtMarket(MarketAPI market) {
        if (!super.shouldShowAtMarket(market)) return false;

        if (DEBUG_MODE) { return true; }

        if (Global.getSector().getMemoryWithoutUpdate().get(VassPerturbaWeaponTestingIntel.MEM_KEY_PROTOTYPE_DATA) != null) {
            LOGGER.info("Threw away Perturba weapon test event due to already doing the event");
            return false;
        }
        if (VassFamilyTrackerPlugin.getRelationToFamily(VassUtils.VASS_FAMILY.PERTURBA) < RELATIONS_NEEDED) {
            LOGGER.info("Threw away Perturba weapon test event due to bad relations with Perturba");
            return false;
        }
        if (!Global.getSector().getPlayerFaction().getRelationshipLevel("vass").isAtWorst(RepLevel.INHOSPITABLE)) {
            LOGGER.info("Threw away Perturba weapon test event due to bad relations with Vass");
            return false;
        }
        if (!hasMetContact()) {
            LOGGER.info("Threw away Perturba weapon test event due to not having met their contact");
            return false;
        }

        //If we have told the sector that there's a non-repeatable event that should happen instead, we can't appear
        //Unused for now
        if (Global.getSector().getMemoryWithoutUpdate().contains(VassPerturbaBaseEvent.CURRENT_EVENT_ALLOWED_KEY)) {
            return false;
        }

        return true;
    }

    // Add the event to the intel screen; doesn't do much for now, but it's good to keep it there for future reference
    private void addIntel() {
        TextPanelAPI text = dialog.getTextPanel();
        VassPerturbaWeaponTestingIntel intel = new VassPerturbaWeaponTestingIntel(this);
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
        int collateral = calculateCollateral();
        switch (option) {
            case INIT:
                text.addPara("The Perturba agent smiles as you approach them.");
                text.addPara("'Hello again. Everything going nicely for you? Won't you join me for a drink?'");
                text.addPara("You grab a drink and tell them to get to the point: they didn't just drop by to say hi, did they?");
                text.addPara("'Straight to the point, eh? Well, frankly, this IS a nice bar, but as you've guessed I'm not here just to enjoy the scenery. Perturba actually has a contract offer for you, if you're interested.'");

                options.addOption("Go on...", OptionId.CONTINUE_1);
                options.addOption("Tell them that you're unfortunately preoccupied at the moment.", OptionId.LEAVE);
                break;
            case CONTINUE_1:
                text.addPara("'Well you see, we're in need of some fresh combat data for some of our prototype " +
                        "weapons. We'd do it ourselves but we thought we'd get more reliable data from someone unaffiliated " +
                        "with the labs themselves. So here's the deal: we hand you the prototypes and you participate in " +
                        "3 battles with them equipped on a ship. Once you're done, just bring them back here.'", h, "3 battles");
                text.addPara("'We're going to have to take some collateral from you during the mission, though. " +
                        "A fair sum should be... well, around "+collateral+" credits.'", h, ""+collateral);
                text.addPara("'Don't worry, once you've completed the mission we'll return it in full, together with " +
                        "payment for the mission itself. It's just standard procedure so we have an insurance in case " +
                        "you run off with the goods.'");


                options.addOption("Agree to take on the contract", OptionId.CONTINUE_2);
                if (Global.getSector().getPlayerFleet().getCargo().getCredits().get() < collateral) {
                    options.setEnabled(OptionId.CONTINUE_2, false);
                    options.setTooltip(OptionId.CONTINUE_2, "You don't have enough credits for the mission collateral.");
                }
                options.addOption("Tell them that you're unfortunately preoccupied at the moment.", OptionId.LEAVE);
                break;
            case CONTINUE_2:
                text.addPara("'Great, I'll inform my team to deliver the goods to your fleet as soon as possible.'");
                text.addPara("'Oh, and also; we'd like to get all those goods back, but honestly the data is more " +
                        "important than the prototypes: once you terminate the contract either by fulfilling it or by " +
                        "cancelling it, we'll trigger a self-destruct mechanism on any prototypes we didn't get back. " +
                        "I'd recommend staying at least a good couple of meters away from them.'");
                text.addPara("'Though if you come back with none of the prototypes, we can't really pay you: those " +
                        "things are quite costly. We'll give back the collateral though, since you did provide the " +
                        "data you were asked for.'");

                //Payment
                text.setFontSmallInsignia();
                text.addPara("Lost " + collateral + " credits", n, h, "" + collateral);
                Global.getSector().getPlayerFleet().getCargo().getCredits().add(-1 * collateral);
                Global.getSector().getMemoryWithoutUpdate().set(COLLATERAL_MEM_KEY, (Integer)collateral);

                //Prototype handout
                int prototypesHandedOut = MathUtils.getRandomNumberInRange(2, 4);
                currentPrototypeWeaponID = VassPerturbaRandomPrototypeManager.PROTOTYPE_WEAPON_IDS.pick();
                text.addPara("Gained " + prototypesHandedOut + " prototypes", Misc.getPositiveHighlightColor(), h, "" + prototypesHandedOut);
                Global.getSector().getPlayerFleet().getCargo().addWeapons(currentPrototypeWeaponID, prototypesHandedOut);
                text.setFontInsignia();

                text.addPara("'Good luck with the testing.'");
                BarEventManager.getInstance().notifyWasInteractedWith(this);
                addIntel();

                options.addOption("Leave", OptionId.LEAVE);
                break;
            case LEAVE_DIALOG:
                text.addPara("'Is that so? Oh well, can't be helped then. We'll find someone else to do it. I'll drop by again if more opportunities come up.'");

                options.addOption("Leave", OptionId.LEAVE);
                break;
            case LEAVE:
                noContinue = true;
                done = true;
                break;
        }
    }

    //The description is slightly different if you're at high relations with Perturba
    private String pickDescription() {
        WeightedRandomPicker<String> post = new WeightedRandomPicker<String>();
        post.add("The Perturba contact is hanging around in the back of the room, but looks at you expectantly when you enter; seems like Perturba has an offer for you.");
        post.add("You spot Perturba's contact person having a drink at one of the tables. They give a small wave when they spot you, obviously trying to grab your attention.");
        if (getContact() != null && VassFamilyTrackerPlugin.getRelationToFamily(VassUtils.VASS_FAMILY.PERTURBA) >= 60) {
            post.add("It seems like " + getContact().getName().getFirst() + " is here again; Perturba probably has business with you.");
        }
        return post.pick();
    }

    private String pickPrompt() {
        WeightedRandomPicker<String> post = new WeightedRandomPicker<String>();
        post.add("Walk up to the Perturba agent.");
        post.add("See what Perturba wants with you today.");
        if (getContact() != null && VassFamilyTrackerPlugin.getRelationToFamily(VassUtils.VASS_FAMILY.PERTURBA) >= 60) {
            post.add("Old pal " + getContact().getName().getFirst() + "! Sit down for a drink and see what they have to say.");
        }
        return post.pick();
    }

    private int calculateCollateral() {
        return Math.round(Math.max(COLLATERAL_MIN, Math.min(COLLATERAL_MAX, Global.getSector().getPlayerFleet().getCargo().getCredits().get()*COLLATERAL_PERCENTAGE)));
    }
}
