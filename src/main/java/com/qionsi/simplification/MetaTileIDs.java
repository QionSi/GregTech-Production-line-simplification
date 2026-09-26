package com.qionsi.simplification;

/**
 * Central registry for the MetaTileEntity ids reserved by this mod.
 * <p>
 * GregTech keeps a single global array ({@code GregTechAPI.METATILEENTITIES}) indexed by the id handed to the machine's
 * constructor, and the array is filled by machine constructors during GregTech's preload phase. Ids are therefore
 * global: if another mod already uses one of these numbers the game will fail to start with an "id is already occupied"
 * error. Keep every id used by this mod in this file so they are easy to relocate.
 */
public final class MetaTileIDs {

    /**
     * 石油化工综合体 / Petrochemical Complex controller.
     */
    public static final int PETROCHEMICAL_COMPLEX_CONTROLLER = 32700;

    private MetaTileIDs() {}
}
