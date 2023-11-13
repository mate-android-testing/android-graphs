package de.uni_passau.fim.auermich.android_graphs.core.utility;

import com.android.tools.smali.dexlib2.AccessFlags;
import com.android.tools.smali.dexlib2.analysis.AnalyzedInstruction;
import com.android.tools.smali.dexlib2.analysis.ClassPath;
import com.android.tools.smali.dexlib2.analysis.DexClassProvider;
import com.android.tools.smali.dexlib2.analysis.MethodAnalyzer;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.DexFile;
import com.android.tools.smali.dexlib2.iface.Method;
import com.android.tools.smali.dexlib2.iface.MethodParameter;
import com.android.tools.smali.dexlib2.util.MethodUtil;
import com.google.common.collect.Lists;
import de.uni_passau.fim.auermich.android_graphs.core.app.APK;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MethodUtils {

    private static final Logger LOGGER = LogManager.getLogger(MethodUtils.class);

    public static final Map<String, String> ANDROID_CALLBACK_TO_PARENT = new HashMap<>() {{
        // https://developer.android.com/reference/android/app/Activity#onContextItemSelected(android.view.MenuItem)
        put("onContextItemSelected(Landroid/view/MenuItem;)Z", "onCreateContextMenu(Landroid/view/ContextMenu;Landroid/view/View;Landroid/view/ContextMenu$ContextMenuInfo;)V");
        // https://developer.android.com/guide/topics/ui/menus#options-menu
        put("onPrepareOptionsMenu(Landroid/view/Menu;)Z", "onCreateOptionsMenu(Landroid/view/Menu;)Z");
        // https://developer.android.com/reference/android/app/Activity#onMenuOpened(int,%20android.view.Menu)
        put("onMenuOpened(ILandroid/view/Menu;)Z", "onPrepareOptionsMenu(Landroid/view/Menu;)Z");
        put("onOptionsItemSelected(Landroid/view/MenuItem;)Z", "onMenuOpened(ILandroid/view/Menu;)Z");
    }};

    private static final Set<String> ANDROID_CALLBACKS = new HashSet<>() {{
        add("onClick(Landroid/view/View;)V");
        add("onLongClick(Landroid/view/View;)Z");
        add("onFocusChange(Landroid/view/View;Z)V");
        add("onKey(Landroid/view/View;ILandroid/view/KeyEvent;)Z");
        add("onTouch(Landroid/view/View;Landroid/view/MotionEvent;)Z");
        add("onEditorAction(Landroid/widget/TextView;ILandroid/view/KeyEvent;)Z");
        add("onCreateContextMenu(Landroid/view/ContextMenu;Landroid/view/View;Landroid/view/ContextMenu$ContextMenuInfo;)V");

        // interface Landroid/text/TextWatcher;
        add("afterTextChanged(Landroid/text/Editable;)V");
        add("beforeTextChanged(Ljava/lang/CharSequence;III)V");
        add("onTextChanged(Ljava/lang/CharSequence;III)V");

        // interface Landroid/content/DialogInterface$OnClickListener;
        add("onClick(Landroid/content/DialogInterface;I)V");

        // https://developer.android.com/guide/topics/ui/layout/recyclerview#implement-adapter
        // Landroid/support/v7/widget/RecyclerView$Adapter;
        add("onCreateViewHolder(Landroid/view/ViewGroup;I)Landroid/support/v7/widget/RecyclerView$ViewHolder;");
        add("onBindViewHolder(Landroid/support/v7/widget/RecyclerView$ViewHolder;I)V");
        // seems to be called by the application code itself
        // add("getItemCount()I");

        // Landroid/support/v7/widget/helper/ItemTouchHelper$Callback;
        add("getMovementFlags(Landroid/support/v7/widget/RecyclerView;Landroid/support/v7/widget/RecyclerView$ViewHolder;)I");
        add("isItemViewSwipeEnabled()Z");
        add("isLongPressDragEnabled()Z");
        add("onChildDraw(Landroid/graphics/Canvas;Landroid/support/v7/widget/RecyclerView;" +
                "Landroid/support/v7/widget/RecyclerView$ViewHolder;FFIZ)V");
        add("onSwiped(Landroid/support/v7/widget/RecyclerView$ViewHolder;I)V");
        add("onMove(Landroid/support/v7/widget/RecyclerView;Landroid/support/v7/widget/RecyclerView$ViewHolder;" +
                "Landroid/support/v7/widget/RecyclerView$ViewHolder;)Z");

        // Landroid/support/v7/preference/Preference$OnPreferenceClickListener;
        add("onPreferenceClick(Landroid/support/v7/preference/Preference;)Z");
        add("onPreferenceClick(Landroid/preference/Preference;)Z");
        add("onPreferenceClick(Landroidx/preference/Preference;)Z");

        // Landroid/content/SharedPreferences$OnSharedPreferenceChangeListener;
        add("onSharedPreferenceChanged(Landroid/content/SharedPreferences;Ljava/lang/String;)V");

        // https://developer.android.com/reference/android/app/Activity#onCreateOptionsMenu(android.view.Menu)
        add("onCreateOptionsMenu(Landroid/view/Menu;)Z");

        // https://developer.android.com/reference/android/app/Activity#onPrepareOptionsMenu(android.view.Menu)
        add("onPrepareOptionsMenu(Landroid/view/Menu;)Z");

        add("onDraw(Landroid/graphics/Canvas;)V");
        add("onSizeChanged(IIII)V");
        add("onPreferenceChange(Landroid/preference/Preference;Ljava/lang/Object;)Z");

        // https://developer.android.com/reference/android/widget/AdapterView.OnItemClickListener
        add("onItemClick(Landroid/widget/AdapterView;Landroid/view/View;IJ)V");

        // https://developer.android.com/reference/android/app/Activity#onOptionsItemSelected(android.view.MenuItem)
        add("onOptionsItemSelected(Landroid/view/MenuItem;)Z");

        // https://developer.android.com/reference/android/app/Activity#onBackPressed()
        add("onBackPressed()V");

        // https://developer.android.com/reference/android/app/Activity#dispatchTouchEvent(android.view.MotionEvent)
        add("dispatchTouchEvent(Landroid/view/MotionEvent;)Z");

        // https://developer.android.com/reference/android/app/Activity#dispatchKeyEvent(android.view.KeyEvent)
        add("dispatchKeyEvent(Landroid/view/KeyEvent;)Z");

        // https://developer.android.com/reference/android/app/DatePickerDialog.OnDateSetListener#onDateSet(android.widget.DatePicker,%20int,%20int,%20int)
        add("onDateSet(Landroid/widget/DatePicker;III)V");

        // https://developer.android.com/reference/android/widget/Adapter#getView(int,%20android.view.View,%20android.view.ViewGroup)
        add("getView(ILandroid/view/View;Landroid/view/ViewGroup;)Landroid/view/View;");

        // https://developer.android.com/reference/android/app/Activity#onActivityResult(int,%20int,%20android.content.Intent)
        add("onActivityResult(IILandroid/content/Intent;)V");

        // https://developer.android.com/reference/android/view/GestureDetector.OnGestureListener#onFling(android.view.MotionEvent,%20android.view.MotionEvent,%20float,%20float)
        add("onFling(Landroid/view/MotionEvent;Landroid/view/MotionEvent;FF)Z");

        // https://developer.android.com/reference/android/app/Activity#onConfigurationChanged(android.content.res.Configuration)
        add("onConfigurationChanged(Landroid/content/res/Configuration;)V");

        // https://developer.android.com/reference/android/app/Activity#onTitleChanged(java.lang.CharSequence,%20int)
        add("onTitleChanged(Ljava/lang/CharSequence;I)V");

        // https://developer.android.com/reference/android/webkit/WebViewClient#shouldOverrideUrlLoading(android.webkit.WebView,%20android.webkit.WebResourceRequest)
        add("shouldOverrideUrlLoading(Landroid/webkit/WebView;Ljava/lang/String;)Z");

        // https://developer.android.com/reference/android/webkit/WebViewClient#onPageFinished(android.webkit.WebView,%20java.lang.String)
        add("onPageFinished(Landroid/webkit/WebView;Ljava/lang/String;)V");

        // https://developer.android.com/reference/android/app/Activity#onContextItemSelected(android.view.MenuItem)
        add("onContextItemSelected(Landroid/view/MenuItem;)Z");

        // https://developer.android.com/reference/com/google/android/material/navigation/NavigationView.OnNavigationItemSelectedListener#onNavigationItemSelected(android.view.MenuItem)
        add("onNavigationItemSelected(Landroid/view/MenuItem;)Z");

        // https://developer.android.com/reference/androidx/viewpager/widget/ViewPager.OnPageChangeListener
        add("onPageScrollStateChanged(I)V");
        add("onPageScrolled(IFI)V");
        add("onPageSelected(I)V");

        // https://developer.android.com/reference/android/view/animation/Animation.AnimationListener
        add("onAnimationEnd(Landroid/animation/Animator;)V");
        add("onAnimationRepeat(Landroid/animation/Animator;)V");
        add("onAnimationStart(Landroid/animation/Animator;)V");

        // https://developer.android.com/reference/android/os/FileObserver
        add("onEvent(ILjava/lang/String;)V");

        // https://developer.android.com/reference/android/view/GestureDetector.SimpleOnGestureListener
        add("onContextClick(Landroid/view/MotionEvent;)Z");
        add("onDoubleTap(Landroid/view/MotionEvent;)Z");
        add("onDoubleTapEvent(Landroid/view/MotionEvent;)Z");
        add("onDown(Landroid/view/MotionEvent;)Z");
        add("onFlying(Landroid/view/MotionEvent;Landroid/view/MotionEvent;FF)Z");
        add("onLongPress(Landroid/view/MotionEvent;)V");
        add("onScroll(Landroid/view/MotionEvent;Landroid/view/MotionEvent;FF)Z");
        add("onShowPress(Landroid/view/MotionEvent;)Z");
        add("onSingleTapConfirmed(Landroid/view/MotionEvent;)Z");
        add("onSingleTapUp(Landroid/view/MotionEvent;)Z");

        // https://developer.android.com/reference/android/widget/SeekBar.OnSeekBarChangeListener#onProgressChanged(android.widget.SeekBar,%20int,%20boolean)
        add("onProgressChanged(Landroid/widget/SeekBar;IZ)V");

        // https://developer.android.com/reference/android/app/ListActivity#onListItemClick(android.widget.ListView,%20android.view.View,%20int,%20long)
        add("onListItemClick(Landroid/widget/ListView;Landroid/view/View;IJ)V");

        add("handleMessage(Landroid/os/Message;)V");

        // https://developer.android.com/reference/android/app/Activity#onNewIntent(android.content.Intent)
        add("onNewIntent(Landroid/content/Intent;)V");

        ANDROID_CALLBACK_TO_PARENT.forEach((child, parent) -> {
            add(child);
            add(parent);
        });
    }};

    /**
     * The recognized ART methods excluding component invocation methods, e.g. startActivity().
     */
    private static final Set<String> ART_METHODS = new HashSet<>() {{
        add("findViewById(I)Landroid/view/View;");
        add("setContentView(I)V");
        add("setContentView(Landroid/view/View;)V");
        add("setContentView(Landroid/view/View;Landroid/view/ViewGroup$LayoutParams;)V");
        add("getSupportFragmentManager()Landroid/support/v4/app/FragmentManager;");
        add("addContentView(Landroid/view/View;Landroid/view/ViewGroup$LayoutParams;)V");
        add("getMenuInflater()Landroid/view/MenuInflater;");
        add("invalidateOptionsMenu()V");
        add("writeToParcel(Landroid/os/Parcel;I)V");
        add("getApplicationContext()Landroid/content/Context;");
        add("sendBroadcastAsUser(Landroid/content/Intent;Landroid/os/UserHandle;)V");
        add("sendBroadcastAsUser(Landroid/content/Intent;Landroid/os/UserHandle;Ljava/lang/String;)V");
        add("registerReceiver(Landroid/content/BroadcastReceiver;Landroid/content/IntentFilter;)Landroid/content/Intent;");
        add("registerReceiver(Landroid/content/BroadcastReceiver;Landroid/content/IntentFilter;I)Landroid/content/Intent;");
        add("registerReceiver(Landroid/content/BroadcastReceiver;Landroid/content/IntentFilter;" +
                "Ljava/lang/String;Landroid/os/Handler;)Landroid/content/Intent;");
        add("registerReceiver(Landroid/content/BroadcastReceiver;Landroid/content/IntentFilter;" +
                "Ljava/lang/String;Landroid/os/Handler;I)Landroid/content/Intent;");
        add("getPackageName()Ljava/lang/String;");
        add("getSupportFragmentManager()Landroidx/fragment/app/FragmentManager;");
    }};

    /**
     * The methods contained in the java.lang.Object class.
     */
    private static final Set<String> JAVA_OBJECT_METHODS = new HashSet<>() {{
        add("hashCode()I");
        add("equals(Ljava/lang/Object;)Z");
        add("getClass()Ljava/lang/Class;");
        add("clone()Ljava/lang/Object;");
        add("toString()Ljava/lang/String;");
        add("notify()V");
        add("notifyAll()V");
        add("wait(J)V");
        add("wait(JI)V");
        add("wait()V");
        add("finalize()V");
    }};

    private MethodUtils() {
        throw new UnsupportedOperationException("utility class");
    }

    /**
     * Checks whether the given method represents an android callback method.
     *
     * @param methodSignature The method to be checked.
     * @return Returns {@code true} if the method is an android callback,
     *         otherwise {@code false} is returned.
     */
    public static boolean isCallback(final String methodSignature) {
        return ANDROID_CALLBACKS.contains(getMethodName(methodSignature));
    }

    /**
     * Checks whether the given method represents a constructor call of a lambda class.
     *
     * @param methodSignature The given method.
     * @return Returns {@code true} if the method is a constructor call of a lambda class,
     *         otherwise {@code false} is returned.
     */
    public static boolean isLambdaClassConstructorCall(final String methodSignature) {
        String className = MethodUtils.getClassName(methodSignature);
        return className.contains("$Lambda$") && isConstructorCall(methodSignature);
    }

    /**
     * Checks whether the given method represents a constructor call.
     *
     * @param methodSignature The given method.
     * @return Returns {@code true} if the method is a constructor call,
     *         otherwise {@code false} is returned.
     */
    public static boolean isConstructorCall(final String methodSignature) {
        String method = MethodUtils.getMethodName(methodSignature);
        return method.startsWith("<init>(") && method.endsWith(")V");
    }

    /**
     * Checks whether the given method represents a private constructor.
     *
     * @param method The method to be checked.
     * @return Returns {@code true} if the method is a private constructor,
     *         otherwise {@code false} is returned.
     */
    public static boolean isPrivateConstructor(final Method method) {
        int accessFlags = method.getAccessFlags();
        AccessFlags[] flags = AccessFlags.getAccessFlagsForMethod(accessFlags);
        return Arrays.stream(flags).anyMatch(flag -> flag == AccessFlags.CONSTRUCTOR)
                && Arrays.stream(flags).anyMatch(flag -> flag == AccessFlags.PRIVATE);
    }

    /**
     * Returns the return type of the given method signature.
     *
     * @param fullyQualifiedMethodName The given method signature.
     * @return Returns the return type of the given method.
     */
    public static String getReturnType(final String fullyQualifiedMethodName) {
        return fullyQualifiedMethodName.split("\\)")[1];
    }

    /**
     * Returns solely the method name from a fully qualified method name.
     *
     * @param fullyQualifiedMethodName The fully qualified method name.
     * @return Returns the method name from the fully qualified method name.
     */
    public static String getMethodName(final String fullyQualifiedMethodName) {
        return fullyQualifiedMethodName.split("->")[1];
    }

    /**
     * Returns solely the method name from a fully qualified method name.
     *
     * @param method The fully qualified method name.
     * @return Returns the method name from the fully qualified method name.
     */
    public static String getMethodName(final Method method) {
        return method.toString().split("->")[1];
    }

    /**
     * Checks whether the given class refers to a static initializer.
     *
     * @param method The method to be checked.
     * @return Returns {@code true} if the method represents a static initializer,
     *         otherwise {@code false} is returned.
     */
    public static boolean isStaticInitializer(String method) {
        return method.endsWith("<clinit>()V");
    }

    /**
     * Derives a unique method signature in order to avoid
     * name clashes originating from overloaded/inherited methods
     * or methods in different classes.
     *
     * @param method The method to derive its method signature.
     * @return Returns the method signature of the given {@param method}.
     */
    public static String deriveMethodSignature(final Method method) {

        String className = method.getDefiningClass();
        String methodName = method.getName();
        List<? extends MethodParameter> parameters = method.getParameters();
        String returnType = method.getReturnType();

        StringBuilder builder = new StringBuilder();
        builder.append(className);
        builder.append("->");
        builder.append(methodName);
        builder.append("(");

        for (MethodParameter param : parameters) {
            builder.append(param.getType());
        }

        builder.append(")");
        builder.append(returnType);
        return builder.toString();
    }

    /**
     * Checks whether the given method is contained in the dex files.
     *
     * @param dexFiles The dex files.
     * @param methodSignature The method to be looked up.
     * @return Returns the dex file containing the method, if possible.
     */
    public static Optional<Tuple<DexFile, Method>> containsTargetMethod(final List<DexFile> dexFiles,
                                                                        final String methodSignature) {

        final String className = methodSignature.split("->")[0];

        for (DexFile dexFile : dexFiles) {
            for (ClassDef classDef : dexFile.getClasses()) {
                if (classDef.toString().equals(className)) {
                    for (Method method : classDef.getMethods()) {
                        if (deriveMethodSignature(method).equals(methodSignature)) {
                            return Optional.of(new Tuple<>(dexFile, method));
                        }
                    }
                    // speed up
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Checks whether the given method is contained in the dex files.
     *
     * @param dexFiles The dex files.
     * @param methodSignature The method to be looked up.
     * @return Returns the class and method definition of the given method, if possible.
     */
    public static Optional<Tuple<ClassDef, Method>> searchForTargetMethod(final List<DexFile> dexFiles,
                                                                   final String methodSignature) {

        final String className = methodSignature.split("->")[0];

        for (DexFile dexFile : dexFiles) {
            for (ClassDef classDef : dexFile.getClasses()) {
                if (classDef.toString().equals(className)) {
                    for (Method method : classDef.getMethods()) {
                        if (deriveMethodSignature(method).equals(methodSignature)) {
                            return Optional.of(new Tuple<>(classDef, method));
                        }
                    }
                    // speed up
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Convenient function to get the list of {@code AnalyzedInstruction} of a certain target method.
     *
     * @param dexFile The dex file containing the target method.
     * @param method The target method.
     * @return Returns a list of {@code AnalyzedInstruction} included in the target method.
     */
    public static List<AnalyzedInstruction> getAnalyzedInstructions(final DexFile dexFile, final Method method) {

        MethodAnalyzer analyzer = new MethodAnalyzer(new ClassPath(Lists.newArrayList(new DexClassProvider(dexFile)),
                true, ClassPath.NOT_ART), method,
                null, false);

        return analyzer.getAnalyzedInstructions();
    }

    /**
     * Searches for a target method in the given APK.
     *
     * @param apk The APK file.
     * @param methodSignature The signature of the target method.
     * @return Returns an optional containing either the target method or not.
     */
    public static Optional<Method> searchForTargetMethod(final APK apk, final String methodSignature) {

        String className = getClassName(methodSignature);

        for (DexFile dexFile : apk.getDexFiles()) {
            for (ClassDef classDef : dexFile.getClasses()) {
                if (classDef.toString().equals(className)) {
                    for (Method method : classDef.getMethods()) {
                        if (deriveMethodSignature(method).equals(methodSignature)) {
                            return Optional.of(method);
                        }
                    }
                    // speed up
                    return Optional.empty();
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Searches for a target method in the given {@code dexFile}.
     *
     * @param dexFile The dexFile to search in.
     * @param methodSignature The signature of the target method.
     * @return Returns an optional containing either the target method or not.
     */
    public static Optional<Method> searchForTargetMethod(final DexFile dexFile, final String methodSignature) {

        String className = getClassName(methodSignature);

        Set<? extends ClassDef> classes = dexFile.getClasses();

        // search for target method
        for (ClassDef classDef : classes) {
            if (classDef.toString().equals(className)) {
                for (Method method : classDef.getMethods()) {
                    if (deriveMethodSignature(method).equals(methodSignature)) {
                        return Optional.of(method);
                    }
                }
                // speed up
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    @SuppressWarnings("unused")
    public static MethodAnalyzer getAnalyzer(final DexFile dexFile, final Method targetMethod) {

        MethodAnalyzer analyzer = new MethodAnalyzer(new ClassPath(Lists.newArrayList(new DexClassProvider(dexFile)),
                true, ClassPath.NOT_ART), targetMethod,
                null, false);
        return analyzer;
    }

    /**
     * Checks whether the given method is an (inherited) ART method, e.g. setContentView().
     *
     * @param fullyQualifiedMethodName The method signature.
     * @return Returns {@code true} if the given method is an ART method,
     *         otherwise {@code false}.
     */
    public static boolean isARTMethod(final String fullyQualifiedMethodName) {
        String method = getMethodName(fullyQualifiedMethodName);
        return ART_METHODS.contains(method);
    }

    /**
     * Checks whether the given method is an inherited method from the java.lang.Object class.
     *
     * @param fullyQualifiedMethodName The method signature.
     * @return Returns {@code true} if the given method is a java.lang.Object method,
     *         otherwise {@code false}.
     */
    public static boolean isJavaObjectMethod(final String fullyQualifiedMethodName) {
        String method = getMethodName(fullyQualifiedMethodName);
        return JAVA_OBJECT_METHODS.contains(method);
    }

    /**
     * Returns the number of local registers for a given method.
     *
     * @param method The given method.
     * @return Returns the number of local registers.
     */
    public static int getLocalRegisterCount(Method method) {
        assert method.getImplementation() != null;
        return method.getImplementation().getRegisterCount() - getParamRegisterCount(method);
    }

    /**
     * Returns the number of param registers for a given method.
     *
     * @param method The given method.
     * @return Returns the number of param registers.
     */
    public static int getParamRegisterCount(Method method) {
        return MethodUtil.getParameterRegisterCount(method);
    }

    /**
     * Retrieves the class name of the method's defining class.
     *
     * @param methodSignature The given method signature.
     * @return Returns the class name.
     */
    public static String getClassName(final String methodSignature) {
        return methodSignature.split("->")[0];
    }

    /**
     * Retrieves the class name of the method's defining class.
     *
     * @param method The given method.
     * @return Returns the class name.
     */
    public static String getClassName(final Method method) {
        return getClassName(method.toString());
    }

    /**
     * Checks whether the given method represents a reflection call.
     *
     * @param methodSignature The method to be checked.
     * @return Returns {@code true} if the method refers to a reflection call,
     *         otherwise {@code false} is returned.
     */
    public static boolean isReflectionCall(String methodSignature) {
        return methodSignature.equals("Ljava/lang/Class;->newInstance()Ljava/lang/Object;");
    }

    /**
     * Retrieves the object parameters for the given method.
     *
     * @param fullyQualifiedMethod The fully-qualified method name.
     * @return Returns the set of object parameters.
     */
    @SuppressWarnings("unused")
    public static Set<String> getObjectParameters(String fullyQualifiedMethod) {
        return getParameters(fullyQualifiedMethod)
                .filter(p -> p.startsWith("L") && p.endsWith(";"))
                .collect(Collectors.toSet());
    }

    /**
     * Retrieves the parameters for the given method.
     *
     * @param fullyQualifiedMethod The fully-qualified method name.
     * @return Returns the parameters for the given method.
     */
    public static Stream<String> getParameters(String fullyQualifiedMethod) {

        // the params are enclosed in parentheses
        String parameterString = fullyQualifiedMethod.split("\\(")[1].split("\\)")[0];

        if (parameterString.isBlank()) {
            // no params
            return Stream.empty();
        }

        Queue<Character> parameters = new LinkedList<>();
        for (Character character : parameterString.toCharArray()) {
            parameters.add(character);
        }

        return Stream.iterate(nextParameter(parameters), // initial parameter
                parameter -> !parameters.isEmpty(), // until there are remaining parameters
                parameter -> nextParameter(parameters)); // retrieve next parameter
    }

    /**
     * Returns the next parameter (type) from the parameter list.
     *
     * @param parameters The parameter list.
     * @return Returns the next parameter from the parameter list.
     */
    private static String nextParameter(Queue<Character> parameters) {

        Character parameterPrefix = Objects.requireNonNull(parameters.poll());

        if (Set.of('Z', 'B', 'S', 'C', 'I', 'J', 'F', 'D').contains(parameterPrefix)) {
            // primitive parameter
            return String.valueOf(parameterPrefix);
        } else if (parameterPrefix.equals('L')) {
            // an object param is prefixed with a 'L' and ends with a ';'
            final StringBuilder builder = new StringBuilder();
            builder.append(parameterPrefix);

            do {
                parameterPrefix = Objects.requireNonNull(parameters.poll());
                builder.append(parameterPrefix);
            } while (!parameterPrefix.equals(';'));

            return builder.toString();
        } else if (parameterPrefix.equals('[')) {
            // array parameter
            return "[" + nextParameter(parameters);
        } else {
            throw new IllegalArgumentException("Unknown type prefix: " + parameterPrefix);
        }
    }
}
