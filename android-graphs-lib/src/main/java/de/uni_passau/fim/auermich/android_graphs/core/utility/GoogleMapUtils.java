package de.uni_passau.fim.auermich.android_graphs.core.utility;

import com.android.tools.smali.dexlib2.Opcode;
import com.android.tools.smali.dexlib2.analysis.AnalyzedInstruction;
import com.android.tools.smali.dexlib2.iface.ClassDef;
import com.android.tools.smali.dexlib2.iface.instruction.Instruction;
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction22c;
import com.android.tools.smali.dexlib2.iface.instruction.formats.Instruction35c;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;

public final class GoogleMapUtils {

    private static final Logger LOGGER = LogManager.getLogger(GoogleMapUtils.class);

    // TODO: Add additional registration methods and listeners!

    private static final Map<String, String> REGISTRATION_METHOD_TO_LISTENER_MAPPING = new HashMap<>(){{
        put("Lcom/google/android/gms/maps/GoogleMap;->setOnMapLongClickListener(Lcom/google/android/gms/maps/GoogleMap$OnMapLongClickListener;)V",
                "Lcom/google/android/gms/maps/GoogleMap$OnMapLongClickListener;");
        put("Lcom/google/android/gms/maps/GoogleMap;->setOnInfoWindowClickListener(Lcom/google/android/gms/maps/GoogleMap$OnInfoWindowClickListener;)V",
                "Lcom/google/android/gms/maps/GoogleMap$OnInfoWindowClickListener;");
        put("Lcom/google/android/gms/maps/GoogleMap;->setOnMarkerClickListener(Lcom/google/android/gms/maps/GoogleMap$OnMarkerClickListener;)V",
                "Lcom/google/android/gms/maps/GoogleMap$OnMarkerClickListener;");
        put("Lcom/google/android/gms/maps/GoogleMap;->setOnCameraMoveListener(Lcom/google/android/gms/maps/GoogleMap$OnCameraMoveListener;)V",
                "Lcom/google/android/gms/maps/GoogleMap$OnCameraMoveListener;");
    }};

    private static final Map<String, String> LISTENER_TO_CALLBACK_MAPPING = new HashMap<>(){{
        put("Lcom/google/android/gms/maps/GoogleMap$OnMapLongClickListener;", "onMapLongClick(Lcom/google/android/gms/maps/model/LatLng;)V");
        put("Lcom/google/android/gms/maps/GoogleMap$OnInfoWindowClickListener;", "onInfoWindowClick(Lcom/google/android/gms/maps/model/Marker;)V");
        put("Lcom/google/android/gms/maps/GoogleMap$OnMarkerClickListener;", "onMarkerClick(Lcom/google/android/gms/maps/model/Marker;)Z");
        put("Lcom/google/android/gms/maps/GoogleMap$OnCameraMoveListener;", "onCameraMove()V");
    }};

    private GoogleMapUtils() {
        throw new UnsupportedOperationException("Utility class!");
    }

    // https://developers.google.com/maps/documentation/android-sdk/reference/com/google/android/libraries/maps/GoogleMap#nested-class-summary
    public static boolean isGoogleMapListenerInvocation(String methodSignature) {
        return REGISTRATION_METHOD_TO_LISTENER_MAPPING.containsKey(methodSignature);
    }

    /**
     * Retrieves the callback method associated with the given registration method if possible.
     *
     * @param registrationMethod The registration method of the listener, e.g., setOnInfoWindowClickListener().
     * @param invokeInstruction The invoke instruction referring to the listener registration.
     * @param classHierarchy A mapping from a class name to its class instance.
     * @return Returns the callback associated with the given registration method if possible, otherwise {@code null}.
     */
    public static String getListenerCallback(final String registrationMethod, final AnalyzedInstruction invokeInstruction,
                                             final ClassHierarchy classHierarchy) {

        if (invokeInstruction.getPredecessors().isEmpty()) {
            // couldn't backtrack invocation
            return null;
        }

        // Example:
        // iget-object v4, p0, Lnet/exclaimindustries/geohashdroid/activities/KnownLocationsPicker;
        //                     ->mMap:Lcom/google/android/gms/maps/GoogleMap;
        // invoke-virtual {v4, p0}, Lcom/google/android/gms/maps/GoogleMap;
        //                     ->setOnMapLongClickListener(Lcom/google/android/gms/maps/GoogleMap$OnMapLongClickListener;)V

        final String listener = REGISTRATION_METHOD_TO_LISTENER_MAPPING.get(registrationMethod);
        final String callback = LISTENER_TO_CALLBACK_MAPPING.get(listener);

        if (invokeInstruction.getInstruction() instanceof Instruction35c) {

            // The listener class is stored in the second register passed to the invoke instruction.
            final Instruction35c invoke = (Instruction35c) invokeInstruction.getInstruction();
            final int targetRegister = invoke.getRegisterD();

            AnalyzedInstruction pred = invokeInstruction.getPredecessors().first();

            // backtrack until we discover last write to register D (p0 above)
            while (pred.getInstructionIndex() != -1) {

                final Instruction predecessor = pred.getInstruction();

                if (pred.setsRegister(targetRegister) && predecessor.getOpcode() == Opcode.NEW_INSTANCE) {
                    final String className = ((ReferenceInstruction) predecessor).getReference().toString();
                    final ClassDef classDef = classHierarchy.getClass(className);
                    if (classDef != null && classDef.getInterfaces().contains(listener)) {
                        LOGGER.debug("Found callback: " + className + "->" + callback);
                        return className + "->" + callback;
                    }
                } else if (predecessor.getOpcode() == Opcode.IGET_OBJECT
                        && ((Instruction22c) predecessor).getRegisterB() == targetRegister) {
                    // iget-object v4, p0, Lnet/exclaimindustries/geohashdroid/activities/KnownLocationsPicker;
                    //                     ->mMap:Lcom/google/android/gms/maps/GoogleMap;
                    final String reference = ((ReferenceInstruction) predecessor).getReference().toString();
                    final String className = MethodUtils.getClassName(reference);
                    final ClassDef classDef = classHierarchy.getClass(className);
                    if (classDef != null && classDef.getInterfaces().contains(listener)) {
                        LOGGER.debug("Found callback: " + className + "->" + callback);
                        return className + "->" + callback;
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
