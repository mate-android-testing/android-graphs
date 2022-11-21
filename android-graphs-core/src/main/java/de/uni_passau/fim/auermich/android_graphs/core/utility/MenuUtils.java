package de.uni_passau.fim.auermich.android_graphs.core.utility;

import de.uni_passau.fim.auermich.android_graphs.core.app.APK;
import de.uni_passau.fim.auermich.android_graphs.core.app.xml.LayoutFile;
import org.jf.dexlib2.builder.BuilderInstruction;
import org.jf.dexlib2.iface.DexFile;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jf.dexlib2.iface.instruction.formats.Instruction35c;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

@SuppressWarnings("unused")
public class MenuUtils {

    public static Map<String, String> ITEM_SELECT_METHOD_TO_ON_CREATE_MENU = Map.of(
            "onContextItemSelected(Landroid/view/MenuItem;)Z",
            "onCreateContextMenu(Landroid/view/ContextMenu;Landroid/view/View;Landroid/view/ContextMenu$ContextMenuInfo;)V",

            "onOptionsItemSelected(Landroid/view/MenuItem;)Z",
            "onCreateOptionsMenu(Landroid/view/Menu;)Z"
    );

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

        return Optional.ofNullable(ITEM_SELECT_METHOD_TO_ON_CREATE_MENU.get(MethodUtils.getMethodName(method.toString())))
                .map(onCreateMethod -> MethodUtils.getClassName(method.toString()) + "->" + onCreateMethod)
                .flatMap(fullOnCreateMethod -> MethodUtils.searchForTargetMethod(methodDexFile, fullOnCreateMethod))
                .flatMap(onCreateMenuMethod -> InstructionUtils.getSwitchCaseKey(builderInstruction, method, methodDexFile)
                        .map(id -> ResourceUtils.lookupIdName(dexFiles, id).orElse(String.valueOf(id))));
    }

    public static boolean isOnCreateMenu(String method) {
        return Stream.of(
                "onCreateContextMenu(Landroid/view/ContextMenu;Landroid/view/View;Landroid/view/ContextMenu$ContextMenuInfo;)V",
                "onCreateOptionsMenu(Landroid/view/Menu;)Z"
        ).anyMatch(method::endsWith);
    }

    public static Stream<TranslatedMenuItem> getDefinedMenuItems(APK apk, DexFile dexFile, Method onCreateMenuMethod) {
        if (onCreateMenuMethod.getImplementation() == null) {
            return Stream.empty();
        }

        return MethodUtils.getAnalyzedInstructions(dexFile, onCreateMenuMethod).stream()
                .flatMap(analyzedInstruction -> {
                    Instruction instruction = analyzedInstruction.getInstruction();
                    if (instruction instanceof Instruction35c
                            && ((Instruction35c) instruction).getReference().toString().equals("Landroid/view/ContextMenu;->add(IIII)Landroid/view/MenuItem;")) {
                        // Menu item is explicitly defined in code
                        int menuItemIdRegister = ((Instruction35c) instruction).getRegisterE();
                        int menuItemStringRegister = ((Instruction35c) instruction).getRegisterG();

                        Optional<Long> menuItemId = InstructionUtils.getLastWriteToRegister(analyzedInstruction, menuItemIdRegister);
                        Optional<Long> menuItemTitleId = InstructionUtils.getLastWriteToRegister(analyzedInstruction, menuItemStringRegister);

                        if (menuItemId.isPresent() && menuItemTitleId.isPresent()) {
                            return Stream.of(buildMenuItem(apk, (int) (long) menuItemId.get(), menuItemTitleId.get()));
                        }
                    } else if (instruction instanceof Instruction35c
                            && ((Instruction35c) instruction).getReference().toString().equals("Landroid/view/MenuInflater;->inflate(ILandroid/view/Menu;)V")) {
                        int menuLayoutIdRegister = ((Instruction35c) instruction).getRegisterD();

                        Optional<Long> menuLayoutId = InstructionUtils.getLastWriteToRegister(analyzedInstruction, menuLayoutIdRegister);

                        return menuLayoutId.stream().flatMap(id -> parseMenuItems(apk, id));
                    }

                    return Stream.empty();
                })
                .map(item -> translate(apk, item));
    }

    private static TranslatedMenuItem translate(APK apk, MenuItem item) {
        return new TranslatedMenuItem(item.getId(), item.getTitleId(), apk.getResourceStrings().get(item.getTitleId()));
    }

    private static MenuItem buildMenuItem(APK apk, int menuItemId, Long menuItemTitleId) {
        String titleId = ResourceUtils.lookupStringIdName(apk.getDexFiles(), menuItemTitleId).orElseThrow();
        String id = ResourceUtils.lookupIdName(apk.getDexFiles(), menuItemId)
                .orElse(String.valueOf(menuItemId));

        return new MenuItem(id, titleId);
    }

    private static Stream<MenuItem> parseMenuItems(APK apk, long menuLayoutId) {
        LayoutFile layoutFile = LayoutFile.findMenuFile(apk.getDecodingOutputPath(), "0x" + Integer.toHexString((int)menuLayoutId));
        return Objects.requireNonNull(layoutFile).parseMenuItems();
    }
}
