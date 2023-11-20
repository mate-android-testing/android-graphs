package de.uni_passau.fim.auermich.android_graphs.core.utility;

import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.analysis.AnalyzedInstruction;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class PopupMenuUtils {

    private static final Logger LOGGER = LogManager.getLogger(PopupMenuUtils.class);

    /**
     * The listener that registers the callback.
     */
    private static final String POPUP_MENU_LISTENER = "Landroid/widget/PopupMenu$OnMenuItemClickListener;";

    /**
     * The callback triggered upon clicking on the popup menu.
     */
    private static final String POPUP_MENU_CALLBACK = "onMenuItemClick(Landroid/view/MenuItem;)Z";

    private PopupMenuUtils() {
        throw new UnsupportedOperationException("Utility class!");
    }

    /**
     * Checks whether the given method refers to the creation of a popup menu.
     *
     * @param methodSignature The method to be checked.
     * @return Returns {@code true} if method refers to a popup menu creation.
     */
    public static boolean isPopupMenuCreation(final String methodSignature) {
        // Actually upon a call of show() but we assume that setOnMenuItemClickListener() precedes immediately before.
        return methodSignature.equals("Landroid/widget/PopupMenu;->" +
                "setOnMenuItemClickListener(Landroid/widget/PopupMenu$OnMenuItemClickListener;)V");
    }

    /**
     * Retrieves the callback associated with the pop menu if possible.
     *
     * @param invokeInstruction The invoke instruction referring to the creation of the popup menu.
     * @param classHierarchy A mapping from a class name to its class instance.
     * @return Returns the callback associated with the given registration method if possible, otherwise {@code null}.
     */
    public static String getPopupMenuCallback(final AnalyzedInstruction invokeInstruction,
                                              final ClassHierarchy classHierarchy) {

        if (invokeInstruction.getPredecessors().isEmpty()) {
            // couldn't backtrack invocation
            return null;
        }

        // Example:
        //    invoke-direct {v0, v1, v2}, Landroid/widget/PopupMenu;-><init>(Landroid/content/Context;Landroid/view/View;)V
        //    const v1, 0x7f0e0001
        //    invoke-virtual {v0, v1}, Landroid/widget/PopupMenu;->inflate(I)V
        //    new-instance v1, Lorg/y20k/transistor/helpers/StationContextMenu$1;
        //    invoke-direct {v1, p0}, Lorg/y20k/transistor/helpers/StationContextMenu$1;-><init>(Lorg/y20k/transistor/helpers/StationContextMenu;)V
        //    invoke-virtual {v0, v1}, Landroid/widget/PopupMenu;->setOnMenuItemClickListener(Landroid/widget/PopupMenu$OnMenuItemClickListener;)V
        //    invoke-virtual {v0}, Landroid/widget/PopupMenu;->show()V

        // TODO: Backtrack menu id and extract menu items as we do for other types of menus!

        if (invokeInstruction.getInstruction() instanceof Instruction35c) {

            // The listener class is stored in the second register (v1 above) passed to the invoke instruction.
            final Instruction35c invoke = (Instruction35c) invokeInstruction.getInstruction();
            final int targetRegister = invoke.getRegisterD();

            AnalyzedInstruction pred = invokeInstruction.getPredecessors().first();

            // backtrack until we discover last write to register D (v1 above)
            while (pred.getInstructionIndex() != -1) {

                final Instruction predecessor = pred.getInstruction();

                if (pred.setsRegister(targetRegister) && predecessor.getOpcode() == Opcode.NEW_INSTANCE) {

                    final String className = ((ReferenceInstruction) predecessor).getReference().toString();
                    final ClassDef classDef = classHierarchy.getClass(className);
                    if (classDef != null && classDef.getInterfaces().contains(POPUP_MENU_LISTENER)) {
                        LOGGER.debug("Found callback: " + className + "->" + POPUP_MENU_CALLBACK);
                        return className + "->" + POPUP_MENU_CALLBACK;
                    }
                }

                // consider next predecessor if available
                if (!pred.getPredecessors().isEmpty()) {
                    pred = pred.getPredecessors().first();
                } else {
                    break;
                }
            }
        }
        return null; // couldn't resolve invocation
    }
}
