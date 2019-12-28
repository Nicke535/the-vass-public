package data.scripts.campaign.barEvents;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.OptionPanelAPI;
import com.fs.starfarer.api.campaign.RepLevel;
import com.fs.starfarer.api.campaign.TextPanelAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.characters.FullName.Gender;
import com.fs.starfarer.api.impl.campaign.ids.Ranks;
import com.fs.starfarer.api.impl.campaign.intel.bar.events.BarEventManager;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;
import data.scripts.campaign.VassFamilyTrackerPlugin;
import data.scripts.utils.VassUtils;
import org.apache.log4j.Logger;
import org.lazywizard.lazylib.MathUtils;

import java.awt.*;
import java.util.HashSet;
import java.util.Set;

/**
 * Creates an event where the player can hire a contract for Perturba weapons to their faction
 * @author Nicke535
 */
public class VassPerturbaWeaponTestingEvent extends VassPerturbaBaseEvent {
    public static final Logger LOGGER = Global.getLogger(VassPerturbaWeaponTestingEvent.class);

    public static final boolean DEBUG_MODE = false;

    //All the weapon IDs used for testing weapons
    public static final Set<String> TESTABLE_WEAPON_IDS = new HashSet<>();
    static {
        //None, as of yet
        //TESTABLE_WEAPON_IDS.add("vass_perturba_testweapon1");
    }

    public enum OptionId {
        INIT,
        CONTINUE_1,
        CONTINUE_2,
        CONTINUE_3,
        LEAVE_NONHOSTILE,
        LEAVE_DIALOG,
        LEAVE,
    }

    private static float COLLATERAL_PERCENTAGE = 0.1f;
    private static int COLLATERAL_MAX = 100000;
    private static int COLLATERAL_MIN = 2000;
    private static float RELATIONS_NEEDED = 20f;
    private static float RELATIONS_BOOST_PERTURBA = 5f;;
    private static float RELATIONS_MAX_PERTURBA = 65f;

    public VassPerturbaWeaponTestingEvent() {
        super();
    }

    // Checks if the event is allowed to appear
    public boolean shouldShowAtMarket(MarketAPI market) {
        if (!super.shouldShowAtMarket(market)) return false;

        if (DEBUG_MODE) { return true; }

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
        float collateral = calculateCollateral();
        switch (option) {
            case INIT:
                text.addPara("The Perturba agent smiles as you approach them.");
                text.addPara("'Hello again. Everything going nicely for you? Won't you join me for a drink?'");
                text.addPara("You grab a drink and tell them to get to the point: they didn't just drop by to say hi, did they?");
                text.addPara("Straight to the point, eh? Well, frankly, this IS a nice bar, but as you've guessed I'm not here just to enjoy the scenery. Perturba actually has a contract offer for you, if you're interested.");

                options.addOption("Go on...", OptionId.CONTINUE_1);
                options.addOption("Tell them that you're unfortunately preoccupied at the moment.", OptionId.LEAVE);
                break;
            case CONTINUE_1:
                text.addPara("Well you see, we're in need of some fresh combat data for some of our prototype " +
                        "weapons. We'd do it ourselves but we thought we'd get more reliable data from someone unaffiliated " +
                        "with the labs themselves. So here's the deal: we hand you the prototypes and you participate in " +
                        "3 battles with them equipped on a ship. Once you're done, just bring them back here.", h, "3 battles");
                text.addPara("We're going to have to take some collateral from you during the mission, though. " +
                        "A fair sum should be... well, around "+collateral+" credits.", h, ""+collateral);
                text.addPara("Don't worry, once you've completed the mission we'll return it in full, together with " +
                        "payment for the mission itself. It's just standard procedure so we have an insurance in case " +
                        "you run off with the goods.");


                options.addOption("Agree to take on the contract", OptionId.CONTINUE_2);
                if (Global.getSector().getPlayerFleet().getCargo().getCredits().get() < collateral) {
                    options.setEnabled(OptionId.CONTINUE_2, false);
                    options.setTooltip(OptionId.CONTINUE_2, "You don't have enough credits.");
                }
                options.addOption("Tell them that you're unfortunately preoccupied at the moment.", OptionId.LEAVE);
                break;
            case CONTINUE_2:
                text.addPara("We're going to have to take some collateral from you during the mission, though. " +
                        "A fair sum should be... well, around "+collateral+" credits.", h, ""+collateral);
                text.addPara("Oh, and also; we'd like to get all those goods back, but honestly the data is more " +
                        "important than the prototypes: once you terminate the contract either by fulfilling it or by " +
                        "cancelling it, we'll trigger a self-destruct mechanism on the prototypes. I'd recommend staying " +
                        "at least a good couple of meters away from them.");
                text.addPara("Though if you come back with none of the prototypes, we can't really pay you: those " +
                        "prototypes are quite costly. We'll give back the collateral though, since you did give us the " +
                        "data you were asked for.");

                text.setFontSmallInsignia();
                text.addPara("Lost " + collateral + " credits", n, h, "" + collateral);
                Global.getSector().getPlayerFleet().getCargo().getCredits().add(-1 * collateral);

                //TODO: actually give out the prototypes

                float currentPerturbaRelations = VassFamilyTrackerPlugin.getRelationToFamily(VassUtils.VASS_FAMILY.PERTURBA);
                if (VassFamilyTrackerPlugin.getRelationToFamily(VassUtils.VASS_FAMILY.PERTURBA) < RELATIONS_MAX_PERTURBA) {
                    float boost = Math.min(RELATIONS_BOOST_PERTURBA, RELATIONS_MAX_PERTURBA-currentPerturbaRelations);
                    VassFamilyTrackerPlugin.modifyRelationToFamily(VassUtils.VASS_FAMILY.PERTURBA, boost);
                    text.addPara("Relations with Perturba improved by "+Math.round(boost), h, "Perturba", ""+Math.round(boost));
                }
                if (Global.getSector().getPlayerFaction().getRelationshipLevel("vass").isAtBest(RepLevel.NEUTRAL)) {
                    text.addPara("Relations with the Vass Families improved by 3", h, "Vass Families", "3");
                    Global.getSector().getPlayerFaction().adjustRelationship("vass", 3);
                }
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

    //The description is slightly different if you've already met the Perturba contact before
    private String pickDescription() {
        WeightedRandomPicker<String> post = new WeightedRandomPicker<String>();
        Object hasMetContact = Global.getSector().getMemoryWithoutUpdate().get(VASS_PERTURBA_HAS_MET_CONTACT_KEY);
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

    private float calculateCollateral() {
        return Math.max(COLLATERAL_MIN, Math.min(COLLATERAL_MAX, Global.getSector().getPlayerFleet().getCargo().getCredits().get()*COLLATERAL_PERCENTAGE));
    }
}
