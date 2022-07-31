package de.uni_passau.fim.auermich.android_graphs.core.utility;

import org.jf.dexlib2.analysis.AnalyzedInstruction;
import org.jf.dexlib2.builder.BuilderInstruction;
import org.jf.dexlib2.iface.DexFile;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.instruction.formats.Instruction35c;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@SuppressWarnings("unused")
public class MenuUtils {

    /**
     * @param builderInstruction The target instruction
     * @param method The method containing the target instruction
     * @param dexFiles The dex files
     * @return the string resource id of the menu item leading to the instruction if it exists otherwise an empty optional
     */
    @SuppressWarnings("unused")
    public static Optional<String> getMenuItemStringId(BuilderInstruction builderInstruction, Method method, List<DexFile> dexFiles) {
        DexFile methodDexFile = dexFiles.stream()
                .filter(dexFile -> MethodUtils.searchForTargetMethod(dexFile, method.toString()).isPresent())
                .findAny().orElseThrow();
        Map<String, String> methodMap = Map.of(
                "onContextItemSelected(Landroid/view/MenuItem;)Z",
                "onCreateContextMenu(Landroid/view/ContextMenu;Landroid/view/View;Landroid/view/ContextMenu$ContextMenuInfo;)V",

                "onOptionsItemSelected(Landroid/view/MenuItem;)Z",
                "onCreateOptionsMenu(Landroid/view/Menu;)Z"
        );

        return Optional.ofNullable(methodMap.get(MethodUtils.getMethodName(method.toString())))
                .map(onCreateMethod -> MethodUtils.getClassName(method.toString()) + "->" + onCreateMethod)
                .flatMap(fullOnCreateMethod -> MethodUtils.searchForTargetMethod(methodDexFile, fullOnCreateMethod))
                .flatMap(onCreateMenuMethod -> InstructionUtils.getSwitchCaseKey(builderInstruction, method, methodDexFile).flatMap(menuId -> getMenuItemStringId(MethodUtils.getAnalyzedInstructions(methodDexFile, onCreateMenuMethod), menuId)))
                .flatMap(id -> ResourceUtils.lookupResourceStringId(id, dexFiles));

    }

    private static Optional<Long> getMenuItemStringId(List<AnalyzedInstruction> instructions, int targetMenuItemId) {
        return instructions.stream()
                .filter(analyzedInstruction -> analyzedInstruction.getInstruction() instanceof Instruction35c
                        && ((Instruction35c) analyzedInstruction.getInstruction()).getReference().toString().equals("Landroid/view/ContextMenu;->add(IIII)Landroid/view/MenuItem;"))
                .map(analyzedInstruction -> {
                    int menuItemIdRegister = ((Instruction35c) analyzedInstruction.getInstruction()).getRegisterE();
                    int menuItemStringRegister = ((Instruction35c) analyzedInstruction.getInstruction()).getRegisterG();

                    Optional<Long> menuItemId = InstructionUtils.getLastWriteToRegister(analyzedInstruction, menuItemIdRegister);
                    Optional<Long> menuItemString = InstructionUtils.getLastWriteToRegister(analyzedInstruction, menuItemStringRegister);

                    if (menuItemId.map(id -> id == targetMenuItemId).orElse(false)) {
                        return menuItemString;
                    } else {
                        return Optional.<Long>empty();
                    }
                })
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findAny();
    }
}
