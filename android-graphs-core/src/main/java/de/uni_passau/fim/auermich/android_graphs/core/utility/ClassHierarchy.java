package de.uni_passau.fim.auermich.android_graphs.core.utility;

import org.jf.dexlib2.iface.ClassDef;

import java.util.*;

public class ClassHierarchy {

    private Map<String, Class> classHierarchy;

    public class Class {

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
            builder.append( "}");
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

    @Override
    public String toString() {
        return classHierarchy.values().toString();
    }
}
