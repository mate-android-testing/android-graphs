package de.uni_passau.fim.auermich.android_graphs.core.utility;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jf.dexlib2.iface.ClassDef;
import org.jf.dexlib2.iface.Method;

import java.util.*;

public class ClassHierarchy {

    private static final Logger LOGGER = LogManager.getLogger(ClassHierarchy.class);
    private Map<String, Class> classHierarchy;

    private class Class {

        private final ClassDef clazz;
        private ClassDef superClass;
        private Set<ClassDef> interfaces;
        private Set<ClassDef> subClasses;

        public Class(ClassDef clazz) {
            this.clazz = clazz;
            this.subClasses = new HashSet<>();
            this.interfaces = new HashSet<>();
        }

        public void addInterfaces(Set<ClassDef> interfaces) {
            interfaces.addAll(interfaces);
        }

        public void addSubClass(ClassDef classDef) {
            subClasses.add(classDef);
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

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append(clazz);
            builder.append(" {super class: ");
            builder.append(superClass);
            builder.append(" | ");
            builder.append("interfaces: ");
            builder.append(interfaces);
            builder.append(" | ");
            builder.append("sub classes: ");
            builder.append(subClasses);
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
        return classHierarchy.get(classDef.toString());
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
        }

        if (interfaces != null) {
            for (ClassDef interfaceClass : interfaces) {
                classHierarchy.putIfAbsent(interfaceClass.toString(), new Class(interfaceClass));
                Class interfaceClazz = classHierarchy.get(interfaceClass.toString());
                interfaceClazz.addSubClass(clazz.getClazz());
            }
        }
    }

    /**
     * Looks up the class hierarchy downwards for methods that override the given method.
     *
     * @param method The given method.
     * @return Returns any method that overrides the given method, including the method itself.
     */
    public Set<String> getOverriddenMethods(String method) {

        Set<String> overriddenMethods = new HashSet<>();

        /*
        * The method itself is always part of the result set.
        *
        * NOTE: This method might be inherited, e.g. notifyObservers(), from a class
        * that is not contained in the dex files, thus we always add it manually.
         */
        overriddenMethods.add(method);

        String methodName = MethodUtils.getMethodName(method);
        String className = MethodUtils.getClassName(method);
        Class clazz = getClassByName(className);

        if (clazz != null) {
            overriddenMethods.addAll(getOverriddenMethodsRecursive(clazz, methodName));
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

        if (overriddenMethods.size() > 1)
            LOGGER.debug("We have for the method " + method + " the following overridden methods: " + overriddenMethods);
        return overriddenMethods;
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
        for (Method method : clazz.getClazz().getMethods()) {
            if (MethodUtils.getMethodName(method.toString()).equals(methodName)) {
                subClassMethods.add(method.toString());
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

    @Override
    public String toString() {
        return classHierarchy.values().toString();
    }
}
