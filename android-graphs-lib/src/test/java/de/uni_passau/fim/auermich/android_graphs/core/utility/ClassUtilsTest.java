package de.uni_passau.fim.auermich.android_graphs.core.utility;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class ClassUtilsTest {

    @DisplayName("Testing simple outer class name.")
    @Test
    public void testOuterClassNameSimple() {
        final String className = "Lcom/google/samples/apps/sunflower/adapters/GardenPlantingAdapter$ViewHolder;";
        Assertions.assertEquals("Lcom/google/samples/apps/sunflower/adapters/GardenPlantingAdapter;",
                ClassUtils.getOuterClass(className));
    }

    @DisplayName("Testing nested outer class name.")
    @Test
    public void testOuterClassNameNested() {
        final String className = "Lorg/dmfs/tasks/groupings/BySearch$2$1;";
        Assertions.assertEquals("Lorg/dmfs/tasks/groupings/BySearch$2;", ClassUtils.getOuterClass(className));
    }

    @DisplayName("Testing lambda outer class name.")
    @Test
    public void testOuterClassNameLambda() {
        final String className = "Lit/feio/android/omninotes/DetailFragment$$Lambda$3;";
        Assertions.assertEquals("Lit/feio/android/omninotes/DetailFragment;", ClassUtils.getOuterClass(className));
    }

    @DisplayName("Testing complex lambda outer class name.")
    @Test
    public void testOuterClassNameLambdaComplex() {
        final String className = "Luk/co/bbc/smpan/avmonitoring/-$$Lambda$HeartbeatBuilder$W6lNE_aiLNgIQnJvZjjH1NceFt0;";
        Assertions.assertEquals("Luk/co/bbc/smpan/avmonitoring/HeartbeatBuilder;", ClassUtils.getOuterClass(className));
    }

    @DisplayName("Testing complex outer class name.")
    @Test
    public void testOuterClassNameComplex() {
        final String className = "Lcom/google/samples/apps/sunflower/adapters/PlantAdapter$createOnClickListener$1;";
        Assertions.assertEquals("Lcom/google/samples/apps/sunflower/adapters/PlantAdapter;", ClassUtils.getOuterClass(className));
    }

    @DisplayName("Testing complex nested outer class name.")
    @Test
    public void testOuterClassNameComplexNested() {
        final String className = "Landroidx/core/animation/AnimatorKt$doOnCancel$$inlined$addListener$1;";
        Assertions.assertEquals("Landroidx/core/animation/AnimatorKt;", ClassUtils.getOuterClass(className));
    }
}
