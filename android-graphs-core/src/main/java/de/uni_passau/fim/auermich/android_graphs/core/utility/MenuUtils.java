package de.uni_passau.fim.auermich.android_graphs.core.utility;

import de.uni_passau.fim.auermich.android_graphs.core.app.APK;
import de.uni_passau.fim.auermich.android_graphs.core.app.xml.LayoutFile;
import org.jf.dexlib2.analysis.AnalyzedInstruction;
import org.jf.dexlib2.builder.BuilderInstruction;
import org.jf.dexlib2.builder.MutableMethodImplementation;
import org.jf.dexlib2.builder.instruction.BuilderSwitchElement;
import org.jf.dexlib2.iface.DexFile;
import org.jf.dexlib2.iface.Method;
import org.jf.dexlib2.iface.instruction.Instruction;
import org.jf.dexlib2.iface.instruction.OneRegisterInstruction;
import org.jf.dexlib2.iface.instruction.SwitchElement;
import org.jf.dexlib2.iface.instruction.WideLiteralInstruction;
import org.jf.dexlib2.iface.instruction.formats.Instruction35c;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A utility class for menus, see https://developer.android.com/reference/android/view/Menu.
 */
public class MenuUtils {

    /**
     * Defines a mapping from an 'onItemSelected' method to its 'onCreateMenu' method.
     */
    public static Map<String, String> ITEM_SELECT_METHOD_TO_ON_CREATE_MENU = Map.of(
            "onContextItemSelected(Landroid/view/MenuItem;)Z",
            "onCreateContextMenu(Landroid/view/ContextMenu;Landroid/view/View;Landroid/view/ContextMenu$ContextMenuInfo;)V",
            "onOptionsItemSelected(Landroid/view/MenuItem;)Z",
            "onCreateOptionsMenu(Landroid/view/Menu;)Z"
    );

    /**
     * Retrieves the menu item string id from the given instruction.
     *
     * @param builderInstruction The target instruction.
     * @param method The method containing the target instruction.
     * @param dexFiles The list of dex files.
     * @return Returns the string resource id of the menu item leading to the instruction if it exists,
     *         otherwise an empty optional.
     */
    @SuppressWarnings("unused")
    public static Optional<String> getMenuItemStringId(BuilderInstruction builderInstruction, Method method,
                                                       List<DexFile> dexFiles) {

        DexFile methodDexFile = dexFiles.stream()
                .filter(dexFile -> MethodUtils.searchForTargetMethod(dexFile, method.toString()).isPresent())
                .findAny().orElseThrow();

        return Optional.ofNullable(ITEM_SELECT_METHOD_TO_ON_CREATE_MENU.get(MethodUtils.getMethodName(method)))
                .map(onCreateMethod -> MethodUtils.getClassName(method) + "->" + onCreateMethod)
                .flatMap(fullOnCreateMethod -> MethodUtils.searchForTargetMethod(methodDexFile, fullOnCreateMethod))
                .flatMap(onCreateMenuMethod -> getSwitchCaseKey(builderInstruction, method, methodDexFile)
                        .map(id -> ResourceUtils.lookupIdName(dexFiles, id).orElse(String.valueOf(id))));
    }

    /**
     * Checks whether the given method represents an 'onCreateMenu' method.
     *
     * @param method The method to be checked.
     * @return Returns {@code true} if this method refers to an 'onCreateMenu' method,
     *         otherwise {@code false} is returned.
     */
    public static boolean isOnCreateMenu(String method) {
        return Stream.of(
                "onCreateContextMenu(Landroid/view/ContextMenu;Landroid/view/View;Landroid/view/ContextMenu$ContextMenuInfo;)V",
                "onCreateOptionsMenu(Landroid/view/Menu;)Z"
        ).anyMatch(method::endsWith);
    }

    /**
     * Retrieves the defined menu items in the given onCreate method.
     *
     * @param apk The APK file.
     * @param dexFile The dex files.
     * @param onCreateMenuMethod The menu onCreate method.
     * @return Returns the defined menu items.
     */
    public static Stream<MenuItemWithResolvedTitle> getDefinedMenuItems(APK apk, DexFile dexFile, Method onCreateMenuMethod) {

        if (onCreateMenuMethod.getImplementation() == null) {
            return Stream.empty();
        }

        return MethodUtils.getAnalyzedInstructions(dexFile, onCreateMenuMethod).stream()
                .flatMap(analyzedInstruction -> {
                    Instruction instruction = analyzedInstruction.getInstruction();
                    if (instruction instanceof Instruction35c
                            && ((Instruction35c) instruction).getReference().toString()
                            .equals("Landroid/view/ContextMenu;->add(IIII)Landroid/view/MenuItem;")) {
                        // Menu item is explicitly defined in code
                        int menuItemIdRegister = ((Instruction35c) instruction).getRegisterE();
                        int menuItemStringRegister = ((Instruction35c) instruction).getRegisterG();

                        Optional<Long> menuItemId = getLastWriteToRegister(analyzedInstruction, menuItemIdRegister);
                        Optional<Long> menuItemTitleId = getLastWriteToRegister(analyzedInstruction, menuItemStringRegister);

                        if (menuItemId.isPresent() && menuItemTitleId.isPresent()) {
                            return Stream.of(buildMenuItem(apk, (int) (long) menuItemId.get(), menuItemTitleId.get()));
                        }
                    } else if (instruction instanceof Instruction35c
                            && ((Instruction35c) instruction).getReference().toString()
                            .equals("Landroid/view/MenuInflater;->inflate(ILandroid/view/Menu;)V")) {
                        int menuLayoutIdRegister = ((Instruction35c) instruction).getRegisterD();
                        Optional<Long> menuLayoutId = getLastWriteToRegister(analyzedInstruction, menuLayoutIdRegister);
                        return menuLayoutId.stream().flatMap(id -> parseMenuItems(apk, id));
                    }

                    return Stream.empty();
                })
                .map(item -> resolveMenuItemTitle(apk, item));
    }

    /**
     * Constructs a linear predecessor path starting with the given instruction.
     *
     * @param builderInstruction The given instruction from which a path of predecessors is constructed.
     * @param method The method containing the instruction.
     * @param dexFile The dex file containing the method.
     * @return Returns a (deterministic) predecessor path starting from the given instruction.
     */
    private static List<BuilderInstruction> getPredecessorPath(BuilderInstruction builderInstruction,
                                                               Method method, DexFile dexFile) {

        // TODO: Iterate over all possible predecessor paths!

        // TODO: Avoid mapping between builder and analyzed instructions -> use analyzed instructions solely!

        Objects.requireNonNull(method.getImplementation(), "No implementation for method: " + method);

        final List<AnalyzedInstruction> analyzedInstructions = MethodUtils.getAnalyzedInstructions(dexFile, method);
        final MutableMethodImplementation methodImplementation = new MutableMethodImplementation(method.getImplementation());

        // map builder instruction to analyzed instruction
        AnalyzedInstruction targetInstruction = null;
        for (AnalyzedInstruction analyzedInstruction : analyzedInstructions) {
            if (analyzedInstruction.getInstructionIndex() == builderInstruction.getLocation().getIndex()) {
                targetInstruction = analyzedInstruction;
            }
        }

        Objects.requireNonNull(targetInstruction);

        final List<AnalyzedInstruction> linearPredecessorPath = new LinkedList<>();

        while (true) {
            linearPredecessorPath.add(targetInstruction);

            // no further predecessor
            if (targetInstruction.getPredecessorCount() == 0) {
                return linearPredecessorPath.stream()
                        // map analyzed instructions back to builder instructions
                        .map(a -> methodImplementation.getInstructions().stream()
                            .filter(i -> i.getLocation().getIndex() == a.getInstructionIndex()).findFirst())
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .collect(Collectors.toList());
            }

            targetInstruction = targetInstruction.getPredecessors().first();
        }
    }

    /**
     * Returns the switch case key that the instruction belongs to.
     *
     * @param builderInstruction The target instruction.
     * @param method The method containing the target instruction.
     * @param dexFile The dex file containing the method.
     * @return Returns the switch case key that the instruction belongs to.
     */
    public static Optional<Integer> getSwitchCaseKey(BuilderInstruction builderInstruction, Method method, DexFile dexFile) {
        return getSwitchElementOfInstruction(builderInstruction, method, dexFile).map(SwitchElement::getKey);
    }

    /**
     * Retrieves the switch element belonging to the given instruction.
     *
     * @param instruction The given instruction.
     * @param method The method containing the given instruction.
     * @param dexFile The dex file containing the method.
     * @return Returns the switch element belonging to the given instruction or an empty optional.
     */
    private static Optional<SwitchElement> getSwitchElementOfInstruction(BuilderInstruction instruction,
                                                                         Method method, DexFile dexFile) {

        List<BuilderInstruction> predecessorPath = getPredecessorPath(instruction, method, dexFile);
        BuilderInstruction prev = null;

        // move backward over predecessors starting from the given instruction
        for (BuilderInstruction predecessor : predecessorPath) {

            if (InstructionUtils.isSwitchInstruction(predecessor)) {

                var switchElements = InstructionUtils.getSwitchElements(predecessor);

                for (BuilderSwitchElement switchElement : switchElements) {
                    if (switchElement.getTarget().getLocation().getIndex() == prev.getLocation().getIndex()) {
                        return Optional.of(switchElement);
                    }
                }

                throw new IllegalStateException("Was not able to find switch target in method: " + method);
            }

            prev = predecessor;
        }

        return Optional.empty();
    }

    /**
     * Gets the last long value that was written to the target register.
     *
     * @param analyzedInstruction The instruction from which we start searching backwards (exclusive).
     * @param register The target register to look out for.
     * @return Returns the last long value written to the target register.
     */
    public static Optional<Long> getLastWriteToRegister(AnalyzedInstruction analyzedInstruction, int register) {
        return Stream.iterate(analyzedInstruction, a -> a.getPredecessorCount() == 1, a -> a.getPredecessors().first())
                .skip(1) // start search from first predecessor
                .map(a -> isWriteToRegister(a.getInstruction(), register))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }

    /**
     * Checks whether the given instruction writes to the given register and returns the written value in such a case.
     *
     * @param instruction The given instruction.
     * @param register The given register.
     * @return Returns the written literal value or an empty optional.
     */
    private static Optional<Long> isWriteToRegister(Instruction instruction, int register) {

        if (instruction instanceof WideLiteralInstruction
                && instruction instanceof OneRegisterInstruction
                && ((OneRegisterInstruction) instruction).getRegisterA() == register) {
            return Optional.of(((WideLiteralInstruction) instruction).getWideLiteral());
        }

        return Optional.empty();
    }

    /**
     * Translates the given menu item by resolving the menu item title id to the actual title text.
     *
     * @param apk The APK file.
     * @param item The given menu item.
     * @return Returns the translated menu item.
     */
    private static MenuItemWithResolvedTitle resolveMenuItemTitle(APK apk, MenuItem item) {
        return new MenuItemWithResolvedTitle(item.getId(), item.getTitleId(), apk.getResourceStrings().get(item.getTitleId()));
    }

    /**
     * Builds a menu item from the menu item and menu item title id.
     *
     * @param apk The APK file.
     * @param menuItemId The menu item id.
     * @param menuItemTitleId The menu item title id.
     * @return Returns a menu item.
     */
    private static MenuItem buildMenuItem(APK apk, int menuItemId, Long menuItemTitleId) {
        String titleId = ResourceUtils.lookupStringIdName(apk.getDexFiles(), menuItemTitleId).orElseThrow();
        String id = ResourceUtils.lookupIdName(apk.getDexFiles(), menuItemId).orElse(String.valueOf(menuItemId));
        return new MenuItem(id, titleId);
    }

    /**
     * Parses the menu items from the specified layout file.
     *
     * @param apk The APK file.
     * @param menuLayoutId The menu layout file id.
     * @return Returns the parsed menu items from the specified layout file.
     */
    private static Stream<MenuItem> parseMenuItems(APK apk, long menuLayoutId) {
        LayoutFile layoutFile = LayoutFile.findMenuFile(apk.getDecodingOutputPath(),
                "0x" + Integer.toHexString((int) menuLayoutId));
        return Objects.requireNonNull(layoutFile).parseMenuItems();
    }
}
