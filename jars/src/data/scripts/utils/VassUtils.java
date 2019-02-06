package data.scripts.utils;

import org.lazywizard.lazylib.MathUtils;

import java.awt.*;

public class VassUtils {
    //The families's energy colors (basic is used )
    private static final float[] COLORS_RECIPRO = { 1f, 1f, 1f};
    private static final float[] COLORS_ACCEL = { 1f, 0f, 0f};
    private static final float[] COLORS_TORPOR = { 0.1f, 0.4f, 1f};
    private static final float[] COLORS_PERTURBA = { 0.8f, 1f, 0f};
    //Multa has its own color system, as it's randomized each frame
    private static final float[] COLORS_MULTA_MAX = { 1f, 1f, 1f};
    private static final float[] COLORS_MULTA_MIN = { 0.4f, 0.4f, 0.4f};

    public static enum VASS_FAMILY {
        ACCEL,
        RECIPRO,
        TORPOR,
        PERTURBA,
        MULTA
    }

    //Function for getting a faction's color, with a certain opacity
    public static Color getFamilyColor (VASS_FAMILY family, float opacity) {
        switch (family) {
            case ACCEL:
                return new Color(COLORS_ACCEL[0], COLORS_ACCEL[1], COLORS_ACCEL[2], opacity);
            case RECIPRO:
                return new Color(COLORS_RECIPRO[0], COLORS_RECIPRO[1], COLORS_RECIPRO[2], opacity);
            case TORPOR:
                return new Color(COLORS_TORPOR[0], COLORS_TORPOR[1], COLORS_TORPOR[2], opacity);
            case PERTURBA:
                return new Color(COLORS_PERTURBA[0], COLORS_PERTURBA[1], COLORS_PERTURBA[2], opacity);
            case MULTA:
                return new Color(MathUtils.getRandomNumberInRange(COLORS_MULTA_MIN[0], COLORS_MULTA_MAX[0]),
                        MathUtils.getRandomNumberInRange(COLORS_MULTA_MIN[1], COLORS_MULTA_MAX[1]),
                        MathUtils.getRandomNumberInRange(COLORS_MULTA_MIN[2], COLORS_MULTA_MAX[2]), opacity);
        }

        //In case of something going... wrong, Accel is the default since it's easy to see against normal Vass colors
        return new Color(COLORS_ACCEL[0], COLORS_ACCEL[1], COLORS_ACCEL[2], opacity);
    }
}
