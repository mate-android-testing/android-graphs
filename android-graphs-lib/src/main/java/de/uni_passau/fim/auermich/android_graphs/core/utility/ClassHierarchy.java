package de.uni_passau.fim.auermich.android_graphs.core.utility;

import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.Method;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nonnull;
import java.util.*;

/**
 * Represents the class hierarchy between the application classes.
 */
public class ClassHierarchy {

    private static final Logger LOGGER = LogManager.getLogger(ClassHierarchy.class);

    /**
     * A mapping of a class name to its class.
     */
    private final Map<String, Class> classHierarchy;

    /**
     * The internal representation of a class.
     */
    private class Class {

        private final ClassDef clazz;
        private final String className;

        private ClassDef superClass;
        private final String superClassName;

        private final Set<ClassDef> interfaces;
        private final Set<String> interfaceNames;

        private final Set<ClassDef> subClasses;

        private final Set<ClassDef> innerClasses;

        /**
         * Constructs a new class instance.
         *
         * @param clazz The underlying class.
         */
        public Class(ClassDef clazz) {
            this.clazz = clazz;
            this.className = clazz.toString();
            this.superClass = null;
            this.superClassName = clazz.getSuperclass();
            this.subClasses = new HashSet<>();
            this.interfaces = new HashSet<>();
            this.interfaceNames = new HashSet<>(clazz.getInterfaces());
            this.innerClasses = new HashSet<>();
        }

        /**
         * Constructs a new class instance. Only used
         * for classes that are not contained in the dex files.
         *
         * @param clazz The underlying class name.
         */
        public Class(String clazz) {
            this.clazz = null;
            this.className = clazz;
            this.superClass = null;
            this.superClassName = null;
            this.subClasses = new HashSet<>();
            this.interfaces = new HashSet<>();
            this.interfaceNames = new HashSet<>();
            this.innerClasses = new HashSet<>();
        }

        public void addInterfaces(Set<ClassDef> interfaces) {
            this.interfaces.addAll(interfaces);
        }

        public void addSubClass(ClassDef classDef) {
            subClasses.add(classDef);
        }

        public void addInnerClasses(Set<ClassDef> innerClasses) {
            this.innerClasses.addAll(innerClasses);
        }

        public void setSuperClass(ClassDef classDef) {
            this.superClass = classDef;
        }

        public String getName() {
            return clazz.toString();
        }

        public ClassDef getClazz() {
            return clazz;
        }

        public ClassDef getSuperClass() {
            return superClass;
        }

        public Set<ClassDef> getInterfaces() {
            return Collections.unmodifiableSet(interfaces);
        }

        public Set<ClassDef> getSubClasses() {
            return Collections.unmodifiableSet(subClasses);
        }

        public Set<ClassDef> getInnerClasses() { return Collections.unmodifiableSet(innerClasses); }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append(className);
            builder.append(" {super class: ");
            builder.append(superClassName);
            builder.append(" | ");
            builder.append("interfaces: ");
            builder.append(interfaceNames);
            builder.append(" | ");
            builder.append("sub classes: ");
            builder.append(subClasses);
            builder.append(" | ");
            builder.append("inner classes: ");
            builder.append(innerClasses);
            builder.append("}");
            return builder.toString();
        }

    }

    public ClassHierarchy() {
        classHierarchy = new HashMap<>();
    }

    private Class getClassByName(String className) {
        return classHierarchy.get(className);
    }

    private Class getClass(ClassDef classDef) {
        return classDef == null ? null : classHierarchy.get(classDef.toString());
    }

    /**
     * Returns the class corresponding to the given class name.
     *
     * @param className The class name.
     * @return Returns the class or {@code null} if the class is not present.
     */
    public ClassDef getClass(String className) {
        Class clazz = classHierarchy.get(className);
        return clazz != null ? clazz.getClazz() : null;
    }

    /**
     * Returns the inner classes of the given class.
     *
     * @param className The class name for which the inner classes should be looked up.
     * @return Returns the inner classes of the given class.
     */
    public Set<ClassDef> getInnerClasses(String className) {
        Class clazz = classHierarchy.get(className);
        return clazz != null ? clazz.getInnerClasses() : Collections.emptySet();
    }

    /**
     * Returns the inner classes of the given class.
     *
     * @param classDef The class for which the inner classes should be looked up.
     * @return Returns the inner classes of the given class.
     */
    public Set<ClassDef> getInnerClasses(ClassDef classDef) {
        return classDef != null ? getInnerClasses(classDef.toString()) : Collections.emptySet();
    }

    /**
     * Returns the sub classes of the given class.
     *
     * @param classDef The class for which the sub classes should be looked up.
     * @return Returns the sub classes of the given class.
     */
    public Set<ClassDef> getSubClasses(ClassDef classDef) {
        return classDef != null ? getSubClasses(classDef.toString()) : Collections.emptySet();
    }

    /**
     * Returns the sub classes of the given class.
     *
     * @param className The class for which the sub classes should be looked up.
     * @return Returns the sub classes of the given class.
     */
    public Set<ClassDef> getSubClasses(String className) {
        final Class clazz = getClassByName(className);
        return clazz != null ? clazz.getSubClasses() : Collections.emptySet();
    }

    public void addClass(ClassDef classDef) {
        Class clazz = new Class(classDef);
        classHierarchy.put(clazz.getName(), clazz);
        update(clazz, null, null);
    }

    public void addClass(ClassDef classDef, ClassDef superClass) {
        Class clazz = new Class(classDef);
        clazz.setSuperClass(superClass);
        classHierarchy.put(clazz.getName(), clazz);
        update(clazz, superClass, null);
    }

    public void addClass(ClassDef classDef, ClassDef superClass, Set<ClassDef> interfaces) {
        Class clazz = new Class(classDef);
        clazz.setSuperClass(superClass);
        clazz.addInterfaces(interfaces);
        classHierarchy.put(clazz.getName(), clazz);
        update(clazz, superClass, interfaces);
    }

    public void addClass(ClassDef classDef, ClassDef superClass, Set<ClassDef> interfaces, Set<ClassDef> innerClasses) {
        Class clazz = new Class(classDef);
        clazz.setSuperClass(superClass);
        clazz.addInterfaces(interfaces);
        clazz.addInnerClasses(innerClasses);
        classHierarchy.put(clazz.getName(), clazz);
        update(clazz, superClass, interfaces);
    }

    /**
     * Internally updates the class hierarchy by introducing back-references
     * from the super class and interfaces.
     *
     * @param clazz      The given class.
     * @param superClass The super class of the given class.
     * @param interfaces The interfaces of the given class.
     */
    private void update(Class clazz, ClassDef superClass, Set<ClassDef> interfaces) {

        if (superClass != null) {
            classHierarchy.putIfAbsent(superClass.toString(), new Class(superClass));
            Class superClazz = classHierarchy.get(superClass.toString());
            superClazz.addSubClass(clazz.getClazz());
        } else {
            // the super class is not contained in the dex file, e.g. Ljava/lang/Object;
            String superClassName = clazz.superClassName;
            if (superClassName != null) {
                classHierarchy.putIfAbsent(superClassName, new Class(superClassName));
                Class superClazz = classHierarchy.get(superClassName);
                superClazz.addSubClass(clazz.getClazz());
            }
        }

        if (interfaces != null) {

            for (ClassDef interfaceClass : interfaces) {
                classHierarchy.putIfAbsent(interfaceClass.toString(), new Class(interfaceClass));
                Class interfaceClazz = classHierarchy.get(interfaceClass.toString());
                interfaceClazz.addSubClass(clazz.getClazz());
            }

            // add a dummy class for interfaces that are not contained in the dex file
            for (String interfaceName : clazz.getClazz().getInterfaces()) {
                classHierarchy.putIfAbsent(interfaceName, new Class(interfaceName));
                Class interfaceClazz = classHierarchy.get(interfaceName);
                interfaceClazz.addSubClass(clazz.getClazz());
            }
        }
    }

    /**
     * Checks each subclass that potentially overwrites the given method. Returns
     * the methods that actually overwrite the given method.
     *
     * @param method The given method.
     * @return Returns the methods that overwrite the given method.
     */
    @SuppressWarnings("unused")
    public Set<String> getOverriddenMethods(final String method) {

        Set<String> overriddenMethods = new HashSet<>();

        String methodName = MethodUtils.getMethodName(method);
        String className = MethodUtils.getClassName(method);
        Class clazz = getClassByName(className);

        if (clazz != null) {
            if (!MethodUtils.isConstructorCall(method)) {

                // check each subclass that could potentially overwrite the method
                for (ClassDef subClass : clazz.getSubClasses()) {
                    Class subClazz = getClass(subClass);
                    if (subClazz != null) {
                        overriddenMethods.addAll(getOverriddenMethodsRecursive(subClazz, methodName));
                    } else {
                        LOGGER.warn("No entry for sub class: " + subClass);
                    }
                }
            }
        }

        LOGGER.debug("Overridden methods for method " + method + ": " + overriddenMethods);
        return overriddenMethods;
    }

    /**
     * Traverses the class hierarchy to get potential invocations of the given method in the current class or
     * any super or sub class. First, we check whether the method is defined in the current class.
     * If this is not the case, any super class is inspected. If any of those define the method,
     * the method signature of the respective super class is returned. If the method is defined in the current class,
     * we also need to check sub classes that could potentially overwrite the method. We return all overridden
     * methods including the method of the current class itself, but only if the current class is available.
     *
     * @param callingClass The class in which the given method is called.
     * @param method The given method.
     * @param packageName The application package name.
     * @param properties The global properties.
     * @return Returns the overridden method(s) in the super or sub classes.
     */
    public Set<String> getOverriddenMethods(final String callingClass, final String method,
                                            final String packageName, final String mainActivityPackage,
                                            final Properties properties) {

        Set<String> overriddenMethods = new HashSet<>();

        String methodName = MethodUtils.getMethodName(method);
        String className = MethodUtils.getClassName(method);
        Class clazz = getClassByName(className);

        if (clazz != null && !ThreadUtils.isThreadMethod(this, method)) {

            /*
            * First, check whether the current class defines the method, otherwise look up
            * in the class hierarchy for any super class defining the method. If this is also
            * not the case, we need to look downwards in the class hierarchy for a sub class
            * overriding the method.
             */
            boolean currentClassDefinesMethod = false;

            if (clazz.getClazz() != null) {
                for (Method m : clazz.getClazz().getMethods()) {
                    if (m.toString().equals(method)) {
                        currentClassDefinesMethod = true;
                        break;
                    }
                }
            }

            if (!currentClassDefinesMethod && clazz.getClazz() != null) {
                // check super classes
                String superMethod = invokesSuperMethod(getClass(clazz.superClass), methodName);

                if (superMethod != null) {

                    if (properties.resolveOnlyAUTClasses) {
                        final String dottedClassName = ClassUtils.dottedClassName(MethodUtils.getClassName(superMethod));
                        if (!ClassUtils.isApplicationClass(packageName, dottedClassName)
                                && (mainActivityPackage == null || !dottedClassName.startsWith(mainActivityPackage))) {
                            LOGGER.debug("Super class method " + superMethod + " that shouldn't be resolved!");
                            /*
                            * This can be a super class method that is actually contained in the dex file,
                            * but should not be resolved according to the configuration. We fall back to
                            * the original method.
                             */
                            superMethod = method;
                        }
                    }

                    overriddenMethods.add(superMethod);
                } else {
                    LOGGER.debug("Method " + method + " not defined in any super class nor current class!");
                    /*
                    * The method is not defined by any accessible super class nor in the current class.
                    * This can be for instance an ART method like 'bindService()', where the super class
                    * isn't contained in the APK. We need to pass the method unchanged to the next processing step.
                     */
                    overriddenMethods.add(method);
                }
            } else if (currentClassDefinesMethod && clazz.getClazz() != null) {

                /*
                * The method is not defined by any super class but at least in the current class.
                * Since we need to over-approximate method invocations, we need to check every
                * sub class as well for potentially overriding the given method. However, we
                * ignore the constructor chain downwards in the class hierarchy.
                 */
                if (!MethodUtils.isConstructorCall(method)) {
                    overriddenMethods.addAll(getOverriddenMethodsRecursive(clazz, methodName));
                } else {
                    overriddenMethods.add(method);
                }
            } else {
                LOGGER.debug("Method " + method + " not defined in any super class nor current class!");
                /*
                 * The method is not defined by any accessible super class nor in the current class.
                 * This can be for instance an ART method like 'sendBroadcast()', where the current class
                 * isn't contained in the APK. We need to pass the method unchanged to the next processing step.
                 */
                overriddenMethods.add(method);
            }
        } else {
            // class not defined in class hierarchy or run method of Thread/Runnable class
            if (ThreadUtils.isThreadMethod(this, method)) {
                /*
                 * Backtracking to the specific run method is rather complex, thus we stick to the following heuristic:
                 * We assume that the class invoking the run method implements itself the run method or any inner class
                 * of it. The procedure may find multiple run methods. If no run method could be found, we pass the
                 * method unchanged to the next processing step.
                 */
                Set<ClassDef> classes = new HashSet<>();
                classes.add(getClass(callingClass));
                classes.addAll(getInnerClasses(callingClass));

                for (ClassDef classDef : classes) {
                    for (Method m : classDef.getMethods()) {
                        if (MethodUtils.getMethodName(m).equals("run()V")) {
                            overriddenMethods.add(MethodUtils.deriveMethodSignature(m));
                        }
                    }
                }

                if (overriddenMethods.isEmpty()) {
                    LOGGER.debug("Couldn't find any run() method in class " + callingClass + "!");
                    // fall back to start()/run() method of Thread/Runnable class
                    overriddenMethods.add(method);
                }
            } else {
                LOGGER.warn("No entry for class: " + className);
                /*
                 * This can be a call of 'Ljava/lang/Class;->newInstance()Ljava/lang/Object;' for instance.
                 * Since we always try to resolve reflection calls, this method comes up here, but the
                 * class defining the method is not contained in the APK. We need to pass the method
                 * unchanged to the next processing step.
                 */
                overriddenMethods.add(method);
            }
        }

        if (overriddenMethods.isEmpty()) {
            LOGGER.warn("Couldn't derive any overridden method for: " + method);
        }

        if (overriddenMethods.size() > 1)
            LOGGER.debug("We have for the method " + method + " the following overridden methods: " + overriddenMethods);

        return overriddenMethods;
    }

    /**
     * Looks up the super classes if any of those defines the given method.
     *
     * @param superClazz The super class from which the search is started.
     * @param methodName The method which should be looked up in the super classes.
     * @return Returns the super method if any, otherwise {@code null} is returned.
     */
    private String invokesSuperMethod(Class superClazz, String methodName) {

        while (superClazz != null && superClazz.getClazz() != null) {

            for (Method method : superClazz.getClazz().getMethods()) {
                if (MethodUtils.getMethodName(method.toString()).equals(methodName)) {
                    LOGGER.debug("(Super) class " + superClazz.getClazz() + " defines the method " + methodName);
                    return method.toString();
                }
            }

            superClazz = getClass(superClazz.superClass);
        }
        return null;
    }

    /**
     * Checks whether the given method is invoked by the current class or any super class.
     *
     * @param methodSignature The method to be checked against.
     * @return Returns the method signature of the defining class or {@code null} if no such class exists.
     */
    public String invokedByCurrentClassOrAnySuperClass(String methodSignature) {
        String methodName = MethodUtils.getMethodName(methodSignature);
        String className = MethodUtils.getClassName(methodSignature);
        Class clazz = getClassByName(className);
        return invokesSuperMethod(clazz, methodName);
    }

    /**
     * Recursively check whether in the given class a method overrides the given method.
     *
     * @param clazz The given class.
     * @param methodName The method name of the base method.
     * @return Returns any method that overrides the given method, including the method itself.
     */
    private Set<String> getOverriddenMethodsRecursive(Class clazz, String methodName) {

        Set<String> subClassMethods = new HashSet<>();

        // check whether the current class overrides the given method
        if (clazz.getClazz() != null) {
            for (Method method : clazz.getClazz().getMethods()) {
                if (MethodUtils.getMethodName(method.toString()).equals(methodName)) {
                    subClassMethods.add(method.toString());
                }
            }
        }

        // recursively step down in the class hierarchy checking whether any sub class overrides the given method
        for (ClassDef subClass : clazz.getSubClasses()) {
            Class subClazz = getClass(subClass);
            if (subClazz != null) {
                subClassMethods.addAll(getOverriddenMethodsRecursive(subClazz, methodName));
            } else {
                LOGGER.warn("No entry for sub class: " + subClass);
            }
        }
        return subClassMethods;
    }

    /**
     * Returns the super classes of the given class.
     *
     * @param className The class for which its super classes should be looked up.
     * @return Returns the super classes of the given class.
     */
    public List<String> getSuperClasses(final String className) {
        ClassDef classDef = getClass(className);
        if (classDef == null) {
            LOGGER.warn("Class " + className + " not present in dex files!");
            return new ArrayList<>();
        } else {
            return getSuperClasses(classDef);
        }
    }

    /**
     * Returns the super classes of the given class.
     *
     * @param classDef The class for which its super classes should be looked up.
     * @return Returns the super classes of the given class.
     */
    public List<String> getSuperClasses(@Nonnull final ClassDef classDef) {

        List<String> superClasses = new ArrayList<>();
        Class clazz = getClass(classDef);

        while (clazz != null) {
            superClasses.add(clazz.superClassName);
            clazz = getClass(clazz.superClass);
        }

        return superClasses;
    }

    @Override
    public String toString() {
        return classHierarchy.values().toString();
    }
}
